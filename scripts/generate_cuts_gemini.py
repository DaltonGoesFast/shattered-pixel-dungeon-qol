"""
generate_cuts_gemini.py - Ask Gemini for an Edit Decision List from session_story.json.

Fail-soft: returns None / exits non-zero on missing key or API errors so callers
can fall back to heuristic cuts.
"""

import os
import sys
import json
import re
import urllib.parse
import urllib.request
import urllib.error

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROMPT_PATH = os.path.join(SCRIPT_DIR, "editor_agent_prompt.md")
SECRETS_CANDIDATES = [
    os.path.normpath(os.path.join(SCRIPT_DIR, "..", "Lastest UI", "secrets.local.json")),
    os.path.join(SCRIPT_DIR, "secrets.local.json"),
]
# Prefer models that currently answer for this API key; skip hang-prone 3.8 first.
DEFAULT_MODEL = "gemini-3.6-flash"
MODEL_FALLBACKS = [
    "gemini-3.6-flash",
    "gemini-3.7-flash",
    "gemini-3.5-flash",
    "gemini-flash-latest",
    "gemini-2.5-flash",
    "gemini-3.8-flash",
]
# Post-Whisper Process Stream can leave the machine busy; 30s was too tight (~22s OK idle).
REQUEST_TIMEOUT_SEC = 90
API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"


def _key_from_windows_user_env(name: str) -> str:
    """Read User-scoped env vars from the registry (visible before shell restart)."""
    if os.name != "nt":
        return ""
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Environment") as k:
            val, _ = winreg.QueryValueEx(k, name)
            return (val or "").strip()
    except Exception:
        return ""


def load_api_key() -> str:
    key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or ""
    if key.strip():
        return key.strip()
    key = _key_from_windows_user_env("GEMINI_API_KEY") or _key_from_windows_user_env("GOOGLE_API_KEY")
    if key:
        return key
    for path in SECRETS_CANDIDATES:
        if os.path.exists(path):
            try:
                with open(path, encoding="utf-8") as f:
                    data = json.load(f)
                key = (data.get("GEMINI_API_KEY") or data.get("GOOGLE_API_KEY") or "").strip()
                if key:
                    return key
            except Exception:
                pass
    return ""


def load_system_prompt() -> str:
    if not os.path.exists(PROMPT_PATH):
        return "You are an expert YouTube gaming video editor. Output ONLY valid JSON EDL."
    with open(PROMPT_PATH, encoding="utf-8") as f:
        text = f.read()
    # Prefer the fenced markdown block inside the prompt file if present
    m = re.search(r"```markdown\s*(.*?)```", text, re.DOTALL)
    if m:
        return m.group(1).strip()
    return text.strip()


# Dropped from Gemini payloads (noise / unused for EDL). Live OBS still uses ui_state via server.py.
_COMPACT_DROP_TYPES = frozenset({
    "ui_state",
    "menu_scene",
    "interlevel_start",
    "interlevel_end",
    "session_start",
    "session_sync",
    "session_end",
})
_COMPACT_DROP_KEYS = frozenset({
    "data",
    "epoch_ms",
    "video_path",
    "start_time_iso",
    "start_epoch_ms",
    "video_time_offset_applied_sec",
})
_GAME_LOG_KEEP_PREFIXES = (
    "--",   # NPC / boss speech
    "@@",   # floor banners
    "++",   # level-up / talent
    "**",   # notable status / altar / boss mechanics
)
_GAME_LOG_KEEP_SUBSTR = (
    "You defeated Goo",
    "You defeated Tengu",
    "You defeated DM-300",
    "You defeated Dwarf King",
    "You defeated Yog",
    "King of Dwarves",
    "sacrific",
    "Amulet",
    "ascend",
    "Scroll of Upgrade",
    "upgraded",
    "equipped",
    "artifact",
)


def _keep_game_log_message(msg: str) -> bool:
    if not msg:
        return False
    if msg.startswith("You picked up:"):
        return False
    if any(msg.startswith(p) for p in _GAME_LOG_KEEP_PREFIXES):
        return True
    lower = msg.lower()
    if any(s.lower() in lower for s in _GAME_LOG_KEEP_SUBSTR):
        return True
    # Routine "You defeated rat" noise — bosses/minibosses are covered by typed events + -- speech.
    if msg.startswith("You defeated "):
        return False
    return False


def compact_story_for_api(story: dict) -> dict:
    """
    Compact session_story for the API while keeping editing power:
    - transcript: sentence start/end/text only (word stamps are huge; snap happens locally)
    - telemetry: drop UI/menu/interlevel/session noise; thin game_log; drop Barrier buffs
    - drops unified_story_timeline duplicate blob
    """
    transcript = []
    for seg in story.get("transcript") or []:
        transcript.append({
            "start": seg.get("start"),
            "end": seg.get("end"),
            "text": seg.get("text"),
        })

    telemetry = []
    for ev in story.get("telemetry_events") or []:
        etype = ev.get("type")
        if etype in _COMPACT_DROP_TYPES:
            continue
        if etype == "buff_changed" and (ev.get("buff") or "") == "Barrier":
            continue
        if etype == "game_log" and not _keep_game_log_message(str(ev.get("message") or "")):
            continue
        entry = {k: v for k, v in ev.items() if k not in _COMPACT_DROP_KEYS}
        telemetry.append(entry)

    meta = dict(story.get("metadata") or {})
    # Drop bulky path strings the model doesn't need
    meta.pop("session_dir", None)
    meta.pop("video_path", None)

    return {
        "metadata": meta,
        "highlight_clips": story.get("highlight_clips", []),
        "transcript": transcript,
        "telemetry_events": telemetry,
    }


def _extract_json(text: str) -> dict:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        # Find outermost object
        start = text.find("{")
        end = text.rfind("}")
        if start >= 0 and end > start:
            return json.loads(text[start:end + 1])
        raise


def validate_cuts_payload(data: dict) -> dict:
    cuts = data.get("cuts")
    if not isinstance(cuts, list) or not cuts:
        raise ValueError("EDL missing non-empty cuts[]")
    cleaned = []
    for idx, c in enumerate(cuts, start=1):
        s = float(c["start_time_sec"])
        e = float(c["end_time_sec"])
        if e <= s:
            raise ValueError(f"cut {idx} has end <= start")
        cleaned.append({
            "clip_index": idx,
            "start_time_sec": round(s, 2),
            "end_time_sec": round(e, 2),
            "duration_sec": round(e - s, 2),
            "title": c.get("title") or f"Clip {idx}",
            "story_reason": c.get("story_reason", ""),
            "pacing": c.get("pacing", "normal"),
            "transition_to_next": c.get("transition_to_next", "cut"),
        })
    out = dict(data)
    out["cuts"] = cleaned
    if "markers" not in out:
        out["markers"] = []
    return out


def _call_gemini(body: dict, key: str, model: str) -> dict:
    url = API_URL.format(model=model, key=urllib.parse.quote(key))
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT_SEC) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Gemini HTTP {e.code} ({model}): {err_body[:500]}") from e
    except (TimeoutError, urllib.error.URLError, OSError) as e:
        raise RuntimeError(f"Gemini network error ({model}): {e}") from e
    except Exception as e:
        # Anything else transport-ish — still retryable at the model loop
        raise RuntimeError(f"Gemini network error ({model}): {e}") from e


def load_session_edit_notes(session_folder: str) -> str:
    """Optional per-session notes (edit_notes.md) that override/augment global style for one VOD."""
    if not session_folder:
        return ""
    path = os.path.join(session_folder, "edit_notes.md")
    if not os.path.exists(path):
        return ""
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read().strip()
        return text
    except Exception:
        return ""


def generate_cuts(story: dict, model: str = None, session_folder: str = None) -> dict:
    key = load_api_key()
    if not key:
        raise RuntimeError("GEMINI_API_KEY not set (env or secrets.local.json)")

    system_prompt = load_system_prompt()
    compact = compact_story_for_api(story)
    notes = ""
    if session_folder:
        notes = load_session_edit_notes(session_folder)
    elif isinstance(story.get("metadata"), dict):
        notes = load_session_edit_notes(story["metadata"].get("session_dir") or "")

    notes_block = ""
    if notes:
        notes_block = (
            "\n\n## SESSION-SPECIFIC EDIT NOTES (highest priority for this VOD)\n"
            "Follow these over the generic defaults when they conflict:\n\n"
            + notes
            + "\n\n"
        )

    user_content = (
        "Produce the Edit Decision List JSON for this session_story.\n"
        "Output ONLY valid JSON matching the schema in your instructions.\n"
        + notes_block
        + "\n"
        + json.dumps(compact, ensure_ascii=False)
    )

    body = {
        "contents": [
            {
                "role": "user",
                "parts": [{"text": system_prompt + "\n\n" + user_content}],
            }
        ],
        "generationConfig": {
            "temperature": 0.4,
            "responseMimeType": "application/json",
        },
    }

    models = []
    if model:
        models.append(model)
    for m in MODEL_FALLBACKS:
        if m not in models:
            models.append(m)

    last_err = None
    raw = None
    used_model = None
    for m in models:
        try:
            print(f"      Trying Gemini model: {m} (timeout {REQUEST_TIMEOUT_SEC}s)...")
            raw = _call_gemini(body, key, m)
            used_model = m
            break
        except KeyboardInterrupt:
            raise
        except RuntimeError as e:
            last_err = e
            msg = str(e)
            # Retire/overload/hang — try next model name
            retryable = (
                "HTTP 404" in msg
                or "HTTP 503" in msg
                or "HTTP 429" in msg
                or "timed out" in msg.lower()
                or "timeout" in msg.lower()
                or "network error" in msg.lower()
            )
            if retryable:
                brief = msg.replace("\n", " ")[:120]
                print(f"      {m} unavailable — {brief}")
                print("      trying next...")
                continue
            raise
    if raw is None:
        raise last_err or RuntimeError("Gemini request failed")

    candidates = raw.get("candidates") or []
    if not candidates:
        raise RuntimeError(f"Gemini returned no candidates: {str(raw)[:400]}")
    parts = candidates[0].get("content", {}).get("parts") or []
    text = "".join(p.get("text", "") for p in parts)
    if not text.strip():
        raise RuntimeError("Gemini returned empty text")

    parsed = _extract_json(text)
    validated = validate_cuts_payload(parsed)
    validated["model_used"] = used_model
    return validated


def write_cuts_for_session(session_folder: str, story: dict = None, model: str = None) -> str:
    story_path = os.path.join(session_folder, "session_story.json")
    if story is None:
        with open(story_path, encoding="utf-8") as f:
            story = json.load(f)
    cuts = generate_cuts(story, model=model, session_folder=session_folder)
    out_path = os.path.join(session_folder, "cuts.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(cuts, f, indent=2)
    return out_path


def main():
    if len(sys.argv) < 2:
        print("Usage: python generate_cuts_gemini.py <session_folder_or_session_story.json>")
        sys.exit(2)
    target = sys.argv[1]
    if os.path.isdir(target):
        session_folder = target
    else:
        session_folder = os.path.dirname(os.path.abspath(target))
    try:
        path = write_cuts_for_session(session_folder)
        print(f"Wrote {path}")
    except Exception as e:
        print(f"ERROR: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
