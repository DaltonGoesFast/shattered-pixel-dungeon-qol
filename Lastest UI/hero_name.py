"""
Hero naming pot + post-Goo poll helpers.

Viewers stash a last !name; after Goo (depth 5) Python draws 4 and asks
Streamer.bot (N01) to open Twitch + YouTube polls. Votes are combined here.
Applying the name to the hero is streamer-driven (Phase 3), never automatic.
"""
from __future__ import annotations

import json
import os
import random
import re
import threading
import time
import urllib.error
import urllib.request
from typing import Any, Optional

from points_command import BOT_USER, SCRIPT_DIR

NAME_MAX_LEN = 12
NAME_CHARSET_RE = re.compile(r"^[A-Za-z0-9 '\-]+$")
POLL_TITLE = "Name the hero"
POLL_DURATION_SEC = 60
POLL_GRACE_SEC = 20
N01_ACTION_NAME = "N01 - Hero Name Poll"
N05_ACTION_NAME = "N05 - Hero Name Winner"
STREAMERBOT_DOACTION_URL = os.environ.get(
    "STREAMERBOT_DOACTION_URL", "http://127.0.0.1:7474/DoAction"
)
GOO_DEPTH = 5

POT_FILE = os.path.join(SCRIPT_DIR, "hero_name_pot.json")
WINNERS_FILE = os.path.join(SCRIPT_DIR, "hero_name_winners.json")
BLOCKLIST_FILE = os.path.join(SCRIPT_DIR, "hero_name_blocklist.txt")
PENDING_FILE = os.path.join(SCRIPT_DIR, "hero_name_pending.json")
WINNER_TXT_FILE = os.path.join(SCRIPT_DIR, "hero_name_winner.txt")
POLL_STATE_FILE = os.path.join(SCRIPT_DIR, "hero_name_poll_state.json")

_lock = threading.RLock()
_finalize_timer: Optional[threading.Timer] = None


def _read_json(path: str, default: Any) -> Any:
    if not os.path.exists(path):
        return default
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return default


def _write_json(path: str, data: Any) -> None:
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    os.replace(tmp, path)


def _load_blocklist() -> list[str]:
    if not os.path.exists(BLOCKLIST_FILE):
        return []
    out: list[str] = []
    try:
        with open(BLOCKLIST_FILE, encoding="utf-8") as f:
            for line in f:
                s = line.strip()
                if not s or s.startswith("#"):
                    continue
                out.append(s.lower())
    except OSError:
        return []
    return out


def normalize_username(username: str) -> str:
    return (username or "").strip().lower()


def validate_name(raw: str) -> tuple[bool, str]:
    """Return (ok, cleaned_name_or_reason)."""
    name = (raw or "").strip()
    name = name.replace("\n", " ").replace("\r", " ").replace("\t", " ")
    name = re.sub(r" +", " ", name)
    if not name:
        return False, "empty"
    if len(name) > NAME_MAX_LEN:
        return False, "too_long"
    if not NAME_CHARSET_RE.match(name):
        return False, "charset"
    lower = name.lower()
    for bad in _load_blocklist():
        if bad and bad in lower:
            return False, "blocklist"
    return True, name


def submit_name(username: str, raw_name: str) -> tuple[bool, str]:
    """Store viewer's last name in the pot. Silent success; fails with reason key."""
    key = normalize_username(username)
    if not key or key == BOT_USER:
        return False, "bot"
    ok, cleaned = validate_name(raw_name)
    if not ok:
        return False, cleaned
    with _lock:
        pot = _read_json(POT_FILE, {})
        if not isinstance(pot, dict):
            pot = {}
        pot[key] = {
            "name": cleaned,
            "username": username.strip(),
            "ts": int(time.time()),
        }
        _write_json(POT_FILE, pot)
    return True, cleaned


def _load_winners() -> set[str]:
    data = _read_json(WINNERS_FILE, {"usernames": []})
    if isinstance(data, list):
        return {normalize_username(u) for u in data}
    usernames = data.get("usernames") if isinstance(data, dict) else []
    if not isinstance(usernames, list):
        return set()
    return {normalize_username(u) for u in usernames if u}


def append_winner(username: str) -> None:
    key = normalize_username(username)
    if not key:
        return
    with _lock:
        data = _read_json(WINNERS_FILE, {"usernames": []})
        if isinstance(data, list):
            data = {"usernames": list(data)}
        usernames = data.setdefault("usernames", [])
        if key not in {normalize_username(u) for u in usernames}:
            usernames.append(key)
        data["updated_at"] = int(time.time())
        _write_json(WINNERS_FILE, data)


def clear_winners() -> None:
    """Clear past poll winners (exclude list). Called on stream session reset."""
    with _lock:
        _write_json(WINNERS_FILE, {"usernames": [], "updated_at": int(time.time())})
        try:
            if os.path.exists(WINNER_TXT_FILE):
                with open(WINNER_TXT_FILE, "w", encoding="utf-8") as f:
                    f.write("")
        except OSError:
            pass


def eligible_entries() -> list[dict[str, str]]:
    """One entry per unique name (case-insensitive); skip prior winners + bot."""
    with _lock:
        pot = _read_json(POT_FILE, {})
    if not isinstance(pot, dict):
        return []
    winners = _load_winners()
    by_name: dict[str, dict[str, str]] = {}
    for key, row in pot.items():
        ukey = normalize_username(key)
        if not ukey or ukey == BOT_USER or ukey in winners:
            continue
        if not isinstance(row, dict):
            continue
        name = (row.get("name") or "").strip()
        if not name:
            continue
        ok, cleaned = validate_name(name)
        if not ok:
            continue
        nkey = cleaned.lower()
        if nkey in by_name:
            continue
        by_name[nkey] = {
            "username": (row.get("username") or key).strip(),
            "username_key": ukey,
            "name": cleaned,
        }
    return list(by_name.values())


def draw_poll_options(count: int = 4) -> Optional[list[dict[str, str]]]:
    pool = eligible_entries()
    if len(pool) < count:
        return None
    return random.sample(pool, count)


def _write_winner_txt(name: str) -> None:
    with open(WINNER_TXT_FILE, "w", encoding="utf-8") as f:
        f.write(name)


def _cancel_finalize_timer() -> None:
    global _finalize_timer
    if _finalize_timer is not None:
        try:
            _finalize_timer.cancel()
        except Exception:
            pass
        _finalize_timer = None


def _schedule_finalize(poll_id: str, delay_sec: float) -> None:
    global _finalize_timer
    _cancel_finalize_timer()

    def _fire() -> None:
        try:
            finalize_poll(poll_id=poll_id, reason="timeout")
        except Exception as e:
            print(f"hero_name finalize timer error: {e}")

    _finalize_timer = threading.Timer(max(1.0, delay_sec), _fire)
    _finalize_timer.daemon = True
    _finalize_timer.start()


def _post_doaction(options: list[dict[str, str]]) -> bool:
    body = {
        "action": {"name": N01_ACTION_NAME},
        "args": {
            "pollTitle": POLL_TITLE,
            "pollDuration": str(POLL_DURATION_SEC),
            "option1": options[0]["name"],
            "option2": options[1]["name"],
            "option3": options[2]["name"],
            "option4": options[3]["name"],
        },
    }
    return _doaction_raw(body)


def _announce_winner(username: str, hero: str) -> bool:
    """Ask Streamer.bot N05 to post the winner line on Twitch + YouTube."""
    try:
        import chat_messages
        msg = chat_messages.hero_named(username, hero)
    except Exception:
        who = (username or "").strip() or "Someone"
        if who and not who.startswith("@"):
            who = "@" + who
        msg = f"{who} named hero {(hero or '').strip() or '?'}"
    body = {
        "action": {"name": N05_ACTION_NAME},
        "args": {
            "userName": (username or "").strip(),
            "heroName": (hero or "").strip(),
            "announceMessage": msg,
        },
    }
    ok = _doaction_raw(body)
    if not ok:
        print(f"hero_name announce DoAction failed (is '{N05_ACTION_NAME}' set up?): {msg}")
    else:
        print(f"hero_name announced: {msg}")
    return ok


def _doaction_raw(body: dict) -> bool:
    raw = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        STREAMERBOT_DOACTION_URL,
        data=raw,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            return 200 <= getattr(resp, "status", 204) < 300
    except urllib.error.HTTPError as e:
        if e.code == 204:
            return True
        try:
            detail = e.read().decode("utf-8", errors="replace")
        except Exception:
            detail = ""
        print(f"hero_name DoAction HTTP {e.code}: {e} {detail[:200]}")
        return False
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"hero_name DoAction failed: {e}")
        return False


def _open_poll_expired(state: dict) -> bool:
    if not isinstance(state, dict) or state.get("status") != "open":
        return False
    try:
        started = int(state.get("started_at") or 0)
        duration = int(state.get("duration_sec") or POLL_DURATION_SEC)
        grace = int(state.get("grace_sec") or POLL_GRACE_SEC)
    except (TypeError, ValueError):
        return True
    if started <= 0:
        return True
    return time.time() >= started + duration + grace


def _clear_stale_open_poll_if_needed() -> Optional[str]:
    """
    If an open poll is past duration+grace (common after server restart),
    finalize it when votes exist, otherwise abandon. Returns blocking poll_id
    if a still-active open poll remains.
    """
    with _lock:
        existing = _read_json(POLL_STATE_FILE, {})
        if not isinstance(existing, dict) or existing.get("status") != "open":
            return None
        if not _open_poll_expired(existing):
            return str(existing.get("poll_id") or "")
        poll_id = existing.get("poll_id")
        reports = existing.get("reports") or {}
        snapshot = dict(existing)

    if reports:
        try:
            finalize_poll(poll_id=str(poll_id) if poll_id else None, reason="stale_timeout")
        except Exception as e:
            print(f"hero_name stale finalize error: {e}")
        return None

    with _lock:
        snapshot["status"] = "abandoned"
        snapshot["closed_at"] = int(time.time())
        snapshot["close_reason"] = "stale_no_reports"
        _write_json(POLL_STATE_FILE, snapshot)
        pending = _read_json(PENDING_FILE, {})
        if isinstance(pending, dict) and pending.get("poll_id") == poll_id:
            pending["status"] = "abandoned"
            _write_json(PENDING_FILE, pending)
    print(f"hero_name abandoned stale open poll {poll_id}")
    return None


def try_start_goo_poll() -> dict[str, Any]:
    """
    Draw 4 names and trigger N01. Returns status dict for logging.
    Caller must enforce once-per-run + depth == 5.
    """
    blocking = _clear_stale_open_poll_if_needed()
    if blocking is not None:
        return {"ok": False, "reason": "poll_already_open", "poll_id": blocking}

    drawn = draw_poll_options(4)
    if not drawn:
        return {"ok": False, "reason": "need_four_names", "eligible": len(eligible_entries())}

    poll_id = f"hero-{int(time.time())}-{random.randint(1000, 9999)}"
    started_at = int(time.time())
    state = {
        "poll_id": poll_id,
        "status": "open",
        "title": POLL_TITLE,
        "started_at": started_at,
        "duration_sec": POLL_DURATION_SEC,
        "grace_sec": POLL_GRACE_SEC,
        "options": [
            {
                "name": e["name"],
                "username": e["username"],
                "username_key": e["username_key"],
                "index": i,
            }
            for i, e in enumerate(drawn)
        ],
        "reports": {},
    }
    pending = {
        "poll_id": poll_id,
        "status": "voting",
        "title": POLL_TITLE,
        "started_at": started_at,
        "options": state["options"],
        "winner": None,
    }
    with _lock:
        _write_json(POLL_STATE_FILE, state)
        _write_json(PENDING_FILE, pending)

    ok = _post_doaction(drawn)
    if not ok:
        with _lock:
            state["status"] = "failed_start"
            _write_json(POLL_STATE_FILE, state)
            pending["status"] = "failed_start"
            _write_json(PENDING_FILE, pending)
        return {"ok": False, "reason": "doaction_failed", "poll_id": poll_id, "options": drawn}

    _schedule_finalize(poll_id, POLL_DURATION_SEC + POLL_GRACE_SEC)
    return {
        "ok": True,
        "poll_id": poll_id,
        "options": [{"name": e["name"], "username": e["username"]} for e in drawn],
    }


def _option_votes_from_body(body: dict) -> dict[str, int]:
    """Map option text (lower) -> votes from either choices or options arrays."""
    out: dict[str, int] = {}
    for key in ("choices", "options"):
        arr = body.get(key)
        if not isinstance(arr, list):
            continue
        for item in arr:
            if not isinstance(item, dict):
                continue
            text = (item.get("text") or item.get("title") or item.get("name") or "").strip()
            if not text:
                continue
            try:
                votes = int(item.get("votes") or item.get("totalVotes") or 0)
            except (TypeError, ValueError):
                votes = 0
            out[text.lower()] = max(0, votes)
    # Flat option1/votes1 style
    for i in range(5):
        text = (
            body.get(f"option{i}")
            or body.get(f"choice{i}")
            or body.get(f"option{i}.text")
            or body.get(f"choice{i}.title")
        )
        if text is None:
            continue
        text = str(text).strip()
        if not text:
            continue
        try:
            votes = int(
                body.get(f"votes{i}")
                or body.get(f"option{i}_votes")
                or body.get(f"choice{i}_votes")
                or body.get(f"option{i}.votes")
                or 0
            )
        except (TypeError, ValueError):
            votes = 0
        out[text.lower()] = max(0, votes)
    return out


def record_poll_result(body: dict) -> dict[str, Any]:
    """Ingest one platform's closed-poll report; finalize when both present (or timer)."""
    title = (body.get("title") or body.get("pollTitle") or "").strip()
    if title and title.lower() != POLL_TITLE.lower():
        return {"ok": False, "reason": "wrong_title", "title": title}

    platform = (body.get("platform") or "").strip().lower()
    if platform not in ("twitch", "youtube"):
        return {"ok": False, "reason": "bad_platform"}

    votes = _option_votes_from_body(body)
    with _lock:
        state = _read_json(POLL_STATE_FILE, {})
        if not isinstance(state, dict) or state.get("status") != "open":
            return {"ok": False, "reason": "no_open_poll"}
        poll_id = state.get("poll_id")
        body_poll_id = body.get("poll_id")
        if body_poll_id and poll_id and str(body_poll_id) != str(poll_id):
            return {"ok": False, "reason": "poll_id_mismatch"}

        reports = state.setdefault("reports", {})
        reports[platform] = {
            "votes": votes,
            "received_at": int(time.time()),
            "raw_keys": sorted(votes.keys()),
        }
        _write_json(POLL_STATE_FILE, state)
        both = "twitch" in reports and "youtube" in reports
        report_keys = list(reports.keys())

    if both:
        return finalize_poll(poll_id=str(poll_id), reason="both_platforms")
    return {"ok": True, "status": "waiting", "platform": platform, "reports": report_keys}


def finalize_poll(poll_id: Optional[str] = None, reason: str = "manual") -> dict[str, Any]:
    """Combine Twitch + YouTube votes; write OBS + pending + winners."""
    with _lock:
        state = _read_json(POLL_STATE_FILE, {})
        if not isinstance(state, dict):
            return {"ok": False, "reason": "no_state"}
        if state.get("status") != "open":
            return {"ok": False, "reason": "not_open", "status": state.get("status")}
        if poll_id and state.get("poll_id") and str(poll_id) != str(state.get("poll_id")):
            return {"ok": False, "reason": "poll_id_mismatch"}

        options = state.get("options") or []
        if len(options) < 4:
            state["status"] = "failed"
            _write_json(POLL_STATE_FILE, state)
            return {"ok": False, "reason": "bad_options"}

        reports = state.get("reports") or {}
        combined: list[dict[str, Any]] = []
        for opt in options:
            name = opt["name"]
            key = name.lower()
            tw = int((reports.get("twitch") or {}).get("votes", {}).get(key, 0) or 0)
            yt = int((reports.get("youtube") or {}).get("votes", {}).get(key, 0) or 0)
            combined.append({
                "name": name,
                "username": opt.get("username"),
                "username_key": opt.get("username_key"),
                "index": opt.get("index", 0),
                "twitch_votes": tw,
                "youtube_votes": yt,
                "total_votes": tw + yt,
            })

        # Tie → first in draw order (lowest index)
        combined.sort(key=lambda r: (-r["total_votes"], r["index"]))
        winner = combined[0]

        state["status"] = "closed"
        state["closed_at"] = int(time.time())
        state["close_reason"] = reason
        state["combined"] = combined
        state["winner"] = winner
        _write_json(POLL_STATE_FILE, state)

        pending = {
            "poll_id": state.get("poll_id"),
            "status": "ready",
            "title": POLL_TITLE,
            "closed_at": state["closed_at"],
            "close_reason": reason,
            "options": options,
            "combined": combined,
            "winner": {
                "name": winner["name"],
                "username": winner.get("username"),
                "username_key": winner.get("username_key"),
                "twitch_votes": winner["twitch_votes"],
                "youtube_votes": winner["youtube_votes"],
                "total_votes": winner["total_votes"],
            },
            "applied": False,
        }
        try:
            import chat_messages
            pending["announce_message"] = chat_messages.hero_named(
                winner.get("username") or "", winner["name"]
            )
        except Exception:
            pending["announce_message"] = None
        _write_json(PENDING_FILE, pending)
        _write_winner_txt(winner["name"])

    _cancel_finalize_timer()
    append_winner(winner.get("username_key") or winner.get("username") or "")
    print(
        f"hero_name winner: {winner['name']} "
        f"(by {winner.get('username')}) tw={winner['twitch_votes']} yt={winner['youtube_votes']} ({reason})"
    )
    try:
        _announce_winner(winner.get("username") or "", winner["name"])
    except Exception as e:
        print(f"hero_name announce error: {e}")
    return {
        "ok": True,
        "winner": pending["winner"],
        "reason": reason,
        "message": pending.get("announce_message"),
        "platforms": sorted((reports or {}).keys()),
    }


def get_pending_winner() -> Optional[dict[str, Any]]:
    pending = _read_json(PENDING_FILE, {})
    if not isinstance(pending, dict):
        return None
    if pending.get("status") != "ready":
        return None
    winner = pending.get("winner")
    if not isinstance(winner, dict) or not winner.get("name"):
        return None
    return pending


def mark_applied(name: str) -> None:
    with _lock:
        pending = _read_json(PENDING_FILE, {})
        if isinstance(pending, dict):
            pending["applied"] = True
            pending["applied_at"] = int(time.time())
            pending["applied_name"] = name
            _write_json(PENDING_FILE, pending)


def clear_run_poll_flag_files() -> None:
    """No-op placeholder — run flag lives in session_state via chat_command helpers."""
    pass
