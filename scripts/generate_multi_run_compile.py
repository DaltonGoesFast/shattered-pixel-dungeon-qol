"""
generate_multi_run_compile.py — Multi-session Gemini EDL + multi-source Resolve XML.

Example:
  python generate_multi_run_compile.py ^
    --out "C:/Users/dalto/Videos/Stream_Sessions/2026-11-02_9Challenge_Triple" ^
    --prompt "C:/Users/dalto/Videos/Stream_Sessions/2026-11-02_9Challenge_Triple/editor_prompt_9ch_triple.md" ^
    --sessions ^
      "C:/Users/dalto/Videos/Stream_Sessions/2026-11-02_Stream_12-36-44" ^
      "C:/Users/dalto/Videos/Stream_Sessions/2026-11-02_Stream_11-29-31" ^
      "C:/Users/dalto/Videos/Stream_Sessions/2026-11-02_Stream_14-32-41"
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.parse
from xml.etree import ElementTree as ET
from xml.dom import minidom

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

from generate_cuts_gemini import (  # noqa: E402
    MODEL_FALLBACKS,
    compact_story_for_api,
    load_api_key,
    load_system_prompt,
    _call_gemini,
    _extract_json,
)
from generate_resolve_xml import (  # noqa: E402
    DEFAULT_FPS,
    get_video_duration_frames,
    sec_to_frames,
    snap_cuts_to_speech,
)

REQUEST_TIMEOUT_SEC = 180
IMPORTANT_TELEM = frozenset({
    "boss_slain",
    "hero_died",
    "danger_low_hp",
    "quest_complete",
    "mob_slain",
    "sacrificial_reward",
    "lock_opened",
    "alchemy_crafted",
    "item_identified",
    "shop_buy",
    "level_up",
    "amulet_obtained",
    "floor_change",
})


def _session_name(path: str) -> str:
    return os.path.basename(os.path.normpath(path))


def _find_master_mp4(session_folder: str, story: dict) -> str:
    meta = story.get("metadata") or {}
    vp = meta.get("video_path")
    if vp and os.path.exists(vp):
        return vp
    for name in os.listdir(session_folder):
        if name.lower().endswith(".mp4"):
            return os.path.join(session_folder, name)
    raise FileNotFoundError(f"No master mp4 in {session_folder}")


def _load_reference_cuts(session_folder: str) -> dict:
    path = os.path.join(session_folder, "cuts.json")
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    cuts = []
    for c in data.get("cuts") or []:
        cuts.append({
            "title": c.get("title"),
            "start_time_sec": c.get("start_time_sec"),
            "end_time_sec": c.get("end_time_sec"),
            "duration_sec": c.get("duration_sec"),
            "pacing": c.get("pacing"),
            "story_reason": c.get("story_reason"),
        })
    return {
        "project_title": data.get("project_title"),
        "summary": data.get("summary"),
        "cuts": cuts,
        "youtube_chapters": data.get("youtube_chapters") or [],
    }


def _windows_from_reference(ref: dict, pad: float = 50.0) -> list:
    wins = []
    for c in ref.get("cuts") or []:
        try:
            s = float(c["start_time_sec"]) - pad
            e = float(c["end_time_sec"]) + pad
            wins.append((max(0.0, s), e))
        except Exception:
            pass
    return wins


def _in_windows(t: float, wins: list) -> bool:
    for s, e in wins:
        if s <= t <= e:
            return True
    return False


def thin_compact_for_compile(compact: dict, ref: dict) -> dict:
    """Keep transcript/telemetry near reference cuts + critical events (payload control)."""
    wins = _windows_from_reference(ref, pad=55.0)
    telem = compact.get("telemetry_events") or []
    for ev in telem:
        if ev.get("type") in IMPORTANT_TELEM:
            try:
                t = float(ev.get("video_time_sec"))
                wins.append((max(0.0, t - 40.0), t + 40.0))
            except Exception:
                pass

    thin_transcript = []
    for seg in compact.get("transcript") or []:
        try:
            s = float(seg.get("start", 0))
            e = float(seg.get("end", s))
        except Exception:
            continue
        if _in_windows(s, wins) or _in_windows(e, wins) or _in_windows((s + e) / 2.0, wins):
            thin_transcript.append(seg)

    thin_telem = []
    for ev in telem:
        et = ev.get("type")
        if et in IMPORTANT_TELEM:
            thin_telem.append(ev)
            continue
        try:
            t = float(ev.get("video_time_sec"))
        except Exception:
            continue
        if _in_windows(t, wins):
            thin_telem.append(ev)

    out = dict(compact)
    out["transcript"] = thin_transcript
    out["telemetry_events"] = thin_telem
    out["reference_cuts"] = ref
    return out


def validate_multi_cuts(data: dict, allowed_sessions: set) -> dict:
    cuts = data.get("cuts")
    if not isinstance(cuts, list) or not cuts:
        raise ValueError("EDL missing non-empty cuts[]")
    cleaned = []
    for idx, c in enumerate(cuts, start=1):
        src = (c.get("source_session") or "").strip()
        if src not in allowed_sessions:
            raise ValueError(f"cut {idx} bad source_session={src!r}; allowed={sorted(allowed_sessions)}")
        s = float(c["start_time_sec"])
        e = float(c["end_time_sec"])
        if e <= s:
            raise ValueError(f"cut {idx} has end <= start")
        cleaned.append({
            "clip_index": idx,
            "source_session": src,
            "start_time_sec": round(s, 2),
            "end_time_sec": round(e, 2),
            "duration_sec": round(e - s, 2),
            "title": c.get("title") or f"Clip {idx}",
            "story_reason": c.get("story_reason", ""),
            "act": c.get("act", ""),
            "pacing": c.get("pacing", "normal"),
            "transition_to_next": c.get("transition_to_next", "cut"),
            "boss": bool(c.get("boss")),
            "death": bool(c.get("death")),
        })
    out = dict(data)
    out["cuts"] = cleaned
    out.setdefault("markers", [])
    out.setdefault("youtube_chapters", [])
    return out


def create_fcp_xml_multi(cuts: list, session_videos: dict, markers: list, sequence_name: str, fps: int = DEFAULT_FPS) -> str:
    """Multi-master FCP7 XML. session_videos: source_session -> abs mp4 path."""
    file_meta = {}
    for sid, path in session_videos.items():
        norm = os.path.abspath(path).replace("\\", "/")
        file_meta[sid] = {
            "id": f"file_{sid}",
            "name": os.path.basename(path),
            "uri": "file://localhost/" + urllib.parse.quote(norm, safe=":/"),
            "frames": get_video_duration_frames(path, fps),
        }

    total_timeline_frames = sum(sec_to_frames(c["duration_sec"], fps) for c in cuts)
    xmeml = ET.Element("xmeml", version="5")
    sequence = ET.SubElement(xmeml, "sequence")
    ET.SubElement(sequence, "name").text = sequence_name
    ET.SubElement(sequence, "duration").text = str(total_timeline_frames)
    rate = ET.SubElement(sequence, "rate")
    ET.SubElement(rate, "timebase").text = str(fps)
    ET.SubElement(rate, "ntsc").text = "FALSE"

    media = ET.SubElement(sequence, "media")
    video = ET.SubElement(media, "video")
    v_format = ET.SubElement(video, "format")
    sample_char = ET.SubElement(v_format, "samplecharacteristics")
    ET.SubElement(sample_char, "width").text = "1920"
    ET.SubElement(sample_char, "height").text = "1080"
    ET.SubElement(sample_char, "pixelaspectratio").text = "square"
    v_track = ET.SubElement(video, "track")

    declared_files = set()

    def attach_file(parent, sid: str):
        meta = file_meta[sid]
        file_el = ET.SubElement(parent, "file", id=meta["id"])
        if meta["id"] not in declared_files:
            declared_files.add(meta["id"])
            ET.SubElement(file_el, "name").text = meta["name"]
            ET.SubElement(file_el, "pathurl").text = meta["uri"]
            f_rate = ET.SubElement(file_el, "rate")
            ET.SubElement(f_rate, "timebase").text = str(fps)
            ET.SubElement(file_el, "duration").text = str(meta["frames"])
            f_media = ET.SubElement(file_el, "media")
            f_video = ET.SubElement(f_media, "video")
            ET.SubElement(f_video, "duration").text = str(meta["frames"])

    curr = 0
    for idx, cut in enumerate(cuts, start=1):
        sid = cut["source_session"]
        in_f = sec_to_frames(cut["start_time_sec"], fps)
        out_f = sec_to_frames(cut["end_time_sec"], fps)
        dur_f = out_f - in_f
        clipitem = ET.SubElement(v_track, "clipitem", id=f"v_clip_{idx}")
        ET.SubElement(clipitem, "name").text = cut.get("title", f"Clip {idx}")
        ET.SubElement(clipitem, "duration").text = str(dur_f)
        c_rate = ET.SubElement(clipitem, "rate")
        ET.SubElement(c_rate, "timebase").text = str(fps)
        ET.SubElement(c_rate, "ntsc").text = "FALSE"
        ET.SubElement(clipitem, "in").text = str(in_f)
        ET.SubElement(clipitem, "out").text = str(out_f)
        ET.SubElement(clipitem, "start").text = str(curr)
        ET.SubElement(clipitem, "end").text = str(curr + dur_f)
        attach_file(clipitem, sid)
        curr += dur_f

    audio = ET.SubElement(media, "audio")
    for track_idx in range(1, 5):
        a_track = ET.SubElement(audio, "track")
        curr_a = 0
        declared_files.clear()  # re-declare on first audio use per FCP quirks with multi-file
        for idx, cut in enumerate(cuts, start=1):
            sid = cut["source_session"]
            in_f = sec_to_frames(cut["start_time_sec"], fps)
            out_f = sec_to_frames(cut["end_time_sec"], fps)
            dur_f = out_f - in_f
            aclip = ET.SubElement(a_track, "clipitem", id=f"a{track_idx}_clip_{idx}")
            ET.SubElement(aclip, "name").text = f"{cut.get('title', f'Clip {idx}')} (A{track_idx})"
            ET.SubElement(aclip, "duration").text = str(dur_f)
            arate = ET.SubElement(aclip, "rate")
            ET.SubElement(arate, "timebase").text = str(fps)
            ET.SubElement(aclip, "in").text = str(in_f)
            ET.SubElement(aclip, "out").text = str(out_f)
            ET.SubElement(aclip, "start").text = str(curr_a)
            ET.SubElement(aclip, "end").text = str(curr_a + dur_f)
            attach_file(aclip, sid)
            sourcetrack = ET.SubElement(aclip, "sourcetrack")
            ET.SubElement(sourcetrack, "mediatype").text = "audio"
            ET.SubElement(sourcetrack, "trackindex").text = str(track_idx)
            curr_a += dur_f

    # Resolve / FCP7 marker colors (RGB 0-255)
    color_rgb = {
        "green": (0, 255, 0),
        "red": (255, 0, 0),
        "blue": (0, 0, 255),
        "yellow": (255, 255, 0),
        "cyan": (0, 255, 255),
        "purple": (128, 0, 255),
    }

    for m in markers or []:
        timeline_pos = None
        timeline_out = None
        if m.get("timeline_time_sec") is not None:
            try:
                t0 = float(m["timeline_time_sec"])
                timeline_pos = sec_to_frames(t0, fps)
                dur = float(m.get("duration_sec") or 0.0)
                timeline_out = sec_to_frames(t0 + dur, fps) if dur > 0 else timeline_pos
            except Exception:
                timeline_pos = None
        else:
            sid = m.get("source_session")
            try:
                source_time = float(m.get("source_time_sec", 0.0))
            except Exception:
                continue
            acc = 0
            for cut in cuts:
                if cut.get("source_session") != sid:
                    acc += sec_to_frames(cut["duration_sec"], fps)
                    continue
                if cut["start_time_sec"] <= source_time <= cut["end_time_sec"]:
                    offset = source_time - cut["start_time_sec"]
                    timeline_pos = acc + sec_to_frames(offset, fps)
                    timeline_out = timeline_pos
                    break
                acc += sec_to_frames(cut["duration_sec"], fps)
        if timeline_pos is None:
            continue
        if timeline_out is None:
            timeline_out = timeline_pos
        marker_el = ET.SubElement(sequence, "marker")
        ET.SubElement(marker_el, "name").text = m.get("name", "Event")
        ET.SubElement(marker_el, "comment").text = m.get("comment", "")
        cname = (m.get("color") or "").lower()
        if cname in color_rgb:
            r, g, b = color_rgb[cname]
            col = ET.SubElement(marker_el, "color")
            ET.SubElement(col, "alpha").text = "0"
            ET.SubElement(col, "red").text = str(r)
            ET.SubElement(col, "green").text = str(g)
            ET.SubElement(col, "blue").text = str(b)
        ET.SubElement(marker_el, "in").text = str(timeline_pos)
        ET.SubElement(marker_el, "out").text = str(timeline_out)

    xml_str = ET.tostring(xmeml, encoding="utf-8")
    return minidom.parseString(xml_str).toprettyxml(indent="  ")


def snap_multi_cuts(cuts: list, stories_by_session: dict) -> list:
    grouped = {}
    for i, c in enumerate(cuts):
        grouped.setdefault(c["source_session"], []).append((i, c))

    out = list(cuts)
    for sid, items in grouped.items():
        story = stories_by_session[sid]
        transcript = story.get("transcript") or []
        meta = story.get("metadata") or {}
        video_path = meta.get("video_path")
        if not video_path or not os.path.exists(str(video_path)):
            session_dir = meta.get("session_dir")
            if session_dir:
                video_path = _find_master_mp4(session_dir, story)
        try:
            video_dur = (
                get_video_duration_frames(video_path, DEFAULT_FPS) / float(DEFAULT_FPS)
                if video_path else None
            )
        except Exception:
            video_dur = None
        subset = [dict(c) for _, c in items]
        snapped = snap_cuts_to_speech(
            subset, transcript, video_duration_sec=video_dur, max_duration_sec=900.0
        )
        for (orig_i, orig_c), sc in zip(items, snapped):
            sc["source_session"] = sid
            sc["act"] = orig_c.get("act", "")
            sc["story_reason"] = orig_c.get("story_reason", "")
            sc["transition_to_next"] = orig_c.get("transition_to_next", "cut")
            sc["boss"] = bool(orig_c.get("boss"))
            sc["death"] = bool(orig_c.get("death"))
            sc["duration_sec"] = round(float(sc["end_time_sec"]) - float(sc["start_time_sec"]), 2)
            out[orig_i] = sc
    for idx, c in enumerate(out, start=1):
        c["clip_index"] = idx
    return out


def _clip(
    session: str,
    start: float,
    end: float,
    title: str,
    act: str,
    pacing: str,
    reason: str,
    *,
    boss: bool = False,
    death: bool = False,
) -> dict:
    return {
        "source_session": session,
        "start_time_sec": round(start, 2),
        "end_time_sec": round(end, 2),
        "duration_sec": round(end - start, 2),
        "title": title,
        "story_reason": reason,
        "act": act,
        "pacing": pacing,
        "transition_to_next": "cut",
        "boss": boss,
        "death": death,
    }


def markers_from_cuts(cuts: list) -> list:
    """Green BOSS IN/OUT + red death markers on the compiled timeline."""
    markers = []
    t = 0.0
    for c in cuts:
        dur = float(c["duration_sec"])
        title = c.get("title") or "Clip"
        if c.get("boss"):
            markers.append({
                "name": f"BOSS IN — {title}",
                "comment": "Keep continuous; edit around, not through",
                "timeline_time_sec": round(t, 3),
                "duration_sec": 0.0,
                "color": "green",
            })
            markers.append({
                "name": f"BOSS OUT — {title}",
                "comment": "Boss package ends",
                "timeline_time_sec": round(t + dur, 3),
                "duration_sec": 0.0,
                "color": "green",
            })
        if c.get("death") or (c.get("act") or "").endswith("_death") or str(title).startswith("Death "):
            markers.append({
                "name": f"DEATH — {title}",
                "comment": "Hero picked off",
                "timeline_time_sec": round(t, 3),
                "duration_sec": 0.0,
                "color": "red",
            })
        t += dur
    return markers


def assemble_reference_interleave(sessions_by_alias: dict) -> dict:
    """
    Depth-layered interleave: all sewers → pickoffs → prisons → caves.
    Within a tier, die-first order (Cleric → Rogue → Duelist) so survivors keep descending.
    """
    D = sessions_by_alias["duelist"]
    R = sessions_by_alias["rogue"]
    C = sessions_by_alias["cleric"]

    cuts = [
        # Cold open
        _clip(R, 3110.0, 3128.0, "Cold Open: Ankh Reset?!?", "cold_open", "snappy",
              "Tease the funniest death before the real open"),
        # Soft open
        _clip(D, 0.0, 48.0, "Today: 9-Challenge (Three Attempts)", "sewers", "snappy",
              "Thesis — nine challenge is hard; multiple attempts"),
        # --- SEWERS: everyone (die-first order) ---
        _clip(C, 0.0, 14.5, "Cleric: Sewers Attempt", "sewers", "snappy",
              "First hero into the sewers"),
        _clip(C, 179.5, 208.5, "Cleric: Unstable Spellbook", "sewers", "snappy",
              "Build identity"),
        _clip(C, 555.0, 582.5, "Cleric: Gnoll Trickster 1-HP Energy", "sewers", "normal",
              "9ch early-game scare"),
        _clip(C, 872.0, 903.0, "Cleric: Aqua Brew Solve", "sewers", "snappy",
              "Prep package into Goo"),
        _clip(C, 965.0, 1233.0, "Cleric vs Goo", "sewers", "long",
              "Sewers boss — Cleric", boss=True),
        _clip(C, 1266.0, 1287.0, "Cleric: Post-Goo Shop", "sewers", "snappy",
              "Short aftermath"),
        _clip(C, 1331.0, 1367.0, "Cleric: Sacrificial Altar", "sewers", "normal",
              "Reward package"),
        _clip(C, 2208.5, 2256.0, "Meet Joan of Fard", "sewers", "normal",
              "Naming payoff before she gets picked off"),
        _clip(R, 0.0, 42.0, "Rogue: Sewers Attempt", "sewers", "snappy",
              "Second hero still in sewers"),
        _clip(R, 80.0, 96.0, "Rogue: Master Thieves' Armband", "sewers", "snappy",
              "Signature rogue build piece"),
        _clip(R, 615.0, 636.0, "Rogue: Natty +2 Shocking Dirk", "sewers", "snappy",
              "Greedy payoff beat"),
        _clip(R, 875.0, 902.0, "Rogue: Ghost Quest Reward", "sewers", "snappy",
              "Armor inscription before Goo"),
        _clip(R, 980.0, 1075.0, "Rogue vs Goo", "sewers", "long",
              "Sewers boss — Rogue", boss=True),
        _clip(R, 1105.0, 1140.0, "Rogue: Post-Goo Ankh Shop", "sewers", "snappy",
              "Ankh buy plants the later punchline"),
        _clip(D, 222.0, 260.0, "Duelist: Ring of Wealth Early", "sewers", "snappy",
              "Third hero sewers build toy"),
        _clip(D, 402.0, 415.0, "Duelist: Trickster & Ghost", "sewers", "snappy",
              "Quick quest payoff before Goo"),
        _clip(D, 1370.0, 1530.0, "Duelist vs Goo", "sewers", "long",
              "Sewers boss — Duelist", boss=True),
        _clip(D, 1555.0, 1570.0, "Duelist: Post-Goo Shop", "sewers", "snappy",
              "Sewers tier closes — everyone cleared Goo"),
        # Joan picked off; others keep descending
        _clip(C, 3070.0, 3140.0, "Death 1: Joan vs Crazy Thief", "cleric_death", "normal",
              "First pickoff — Cleric out, Rogue+Duelist continue", death=True),
        _clip(C, 2240.0, 2256.0, "Callback: Joan of Fard", "callback", "snappy",
              "Funny callback as survivors push prison"),
        # --- PRISON: survivors only ---
        _clip(R, 1488.0, 1545.0, "Rogue: First Ankh Burn", "prison", "normal",
              "Prison tier — setup for ankh punchline"),
        _clip(R, 1795.0, 1860.0, "Rogue: Wand of Fireblast", "prison", "normal",
              "Wandmaker payoff"),
        _clip(R, 2680.0, 2790.0, "Rogue vs Tengu", "prison", "long",
              "Prison boss — Rogue", boss=True),
        _clip(R, 2915.0, 2930.0, "Rogue: Blast Wave Steal", "prison", "snappy",
              "Snappy bridge"),
        _clip(D, 2135.0, 2195.0, "Duelist: Sacrificial Altar", "prison", "normal",
              "Prison tier — still alive"),
        _clip(D, 2380.0, 2435.0, "Duelist: Wand of Corrosion", "prison", "normal",
              "Rotberry quest into Tengu"),
        _clip(D, 3180.0, 3270.0, "Duelist vs Tengu → Monk", "prison", "long",
              "Prison boss — Duelist", boss=True),
        _clip(D, 3740.0, 3765.0, "Duelist: Ring of Force", "prison", "snappy",
              "Monk kit coming online"),
        _clip(R, 3075.0, 3240.0, "Death 2: Ankh Reset Without Ankh", "rogue_death", "long",
              "Second pickoff — only Duelist descends further", death=True),
        # --- CAVES: last hero standing ---
        _clip(D, 4830.0, 4905.0, "Duelist: Geomancer Quest", "caves", "normal",
              "Last run standing"),
        _clip(D, 5100.0, 5210.0, "Duelist vs DM-300", "caves", "long",
              "Caves boss — only run that gets here", boss=True),
        _clip(D, 5460.0, 5515.0, "Duelist: Corrosion Misplay Ankh", "caves", "normal",
              "Near-wipe before the real end"),
        _clip(D, 6445.0, 6535.0, "Death 3: Metropolis Ends the Streak", "caves", "normal",
              "Final pickoff — deepest 9ch fail", death=True),
    ]
    for i, c in enumerate(cuts, start=1):
        c["clip_index"] = i

    chapters = []
    t = 0.0
    chapter_acts = [
        ("cold_open", "Cold Open"),
        ("sewers", "Sewers: All Three vs Goo"),
        ("cleric_death", "Death 1: Joan Picked Off"),
        ("prison", "Prison: Survivors vs Tengu"),
        ("rogue_death", "Death 2: Ankh-less"),
        ("caves", "Caves: Last Hero Standing"),
    ]
    seen = set()
    for c in cuts:
        act = c["act"]
        for key, title in chapter_acts:
            if act == key and key not in seen:
                chapters.append({"timestamp_sec": round(t, 1), "title": title})
                seen.add(key)
        t += c["duration_sec"]

    total = sum(c["duration_sec"] for c in cuts)
    return {
        "project_title": "9 Challenge is Actually Hard | 3 Classes, 3 Deaths, 0 Wins",
        "target_duration_sec": round(total, 1),
        "summary": (
            "Sewers first (Cleric→Rogue→Duelist through Goo), Joan picked off, "
            "prison survivors (Rogue→Duelist through Tengu), Rogue ankh death, "
            "Duelist alone through DM-300 / Metropolis finale."
        ),
        "cuts": cuts,
        "youtube_chapters": chapters,
        "markers": [],  # filled after speech-snap from final cut times
        "model_used": "reference_interleave_v2_depth_layers",
    }


def call_gemini_multi(brief: str, payload: dict, model: str | None = None) -> dict:
    key = load_api_key()
    if not key:
        raise RuntimeError("GEMINI_API_KEY not set (env or secrets.local.json)")
    base = load_system_prompt()
    user_content = (
        "MULTI-RUN COMPILE JOB — follow the brief below over generic single-VOD defaults.\n\n"
        + brief
        + "\n\nProduce the Edit Decision List JSON for these three sessions.\n"
        "Output ONLY valid JSON matching the schema in the brief.\n\n"
        + json.dumps(payload, ensure_ascii=False)
    )
    body = {
        "contents": [{"role": "user", "parts": [{"text": base + "\n\n" + user_content}]}],
        "generationConfig": {"temperature": 0.35, "responseMimeType": "application/json"},
    }
    models = []
    if model:
        models.append(model)
    for m in MODEL_FALLBACKS:
        if m not in models:
            models.append(m)

    # Temporarily widen timeout for fat multi payloads
    import generate_cuts_gemini as gcg
    old_timeout = gcg.REQUEST_TIMEOUT_SEC
    gcg.REQUEST_TIMEOUT_SEC = REQUEST_TIMEOUT_SEC
    last_err = None
    raw = None
    used = None
    try:
        for m in models:
            try:
                print(f"      Trying Gemini model: {m} (timeout {REQUEST_TIMEOUT_SEC}s)...")
                raw = _call_gemini(body, key, m)
                used = m
                break
            except KeyboardInterrupt:
                raise
            except RuntimeError as e:
                last_err = e
                msg = str(e)
                retryable = any(x in msg for x in ("HTTP 404", "HTTP 503", "HTTP 429", "timed out", "timeout", "network error"))
                if retryable or "timeout" in msg.lower():
                    print(f"      {m} unavailable — {msg.replace(chr(10), ' ')[:120]}")
                    print("      trying next...")
                    continue
                raise
    finally:
        gcg.REQUEST_TIMEOUT_SEC = old_timeout

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
    parsed["model_used"] = used
    return parsed


def main():
    ap = argparse.ArgumentParser(description="Multi-run Gemini compile → Resolve XML")
    ap.add_argument("--out", required=True, help="Output folder for cuts.json + timeline_resolve.xml")
    ap.add_argument("--prompt", required=True, help="Unique multi-run brief markdown")
    ap.add_argument("--sessions", nargs="+", required=True, help="Session folders (3+)")
    ap.add_argument("--model", default=None)
    ap.add_argument("--skip-gemini", action="store_true", help="Rebuild XML from existing out/cuts.json only")
    ap.add_argument(
        "--from-reference",
        action="store_true",
        help="Skip Gemini; assemble interleaved EDL from solo reference cuts (draft fallback)",
    )
    args = ap.parse_args()

    out_dir = os.path.abspath(args.out)
    os.makedirs(out_dir, exist_ok=True)
    cuts_path = os.path.join(out_dir, "cuts.json")

    stories = {}
    videos = {}
    allowed = set()
    payload_runs = []

    for folder in args.sessions:
        folder = os.path.abspath(folder)
        name = _session_name(folder)
        allowed.add(name)
        story_path = os.path.join(folder, "session_story.json")
        with open(story_path, encoding="utf-8") as f:
            story = json.load(f)
        # Ensure session_dir present for later snap/xml
        meta = dict(story.get("metadata") or {})
        meta["session_dir"] = folder
        meta["session_name"] = name
        if not meta.get("video_path") or not os.path.exists(meta.get("video_path", "")):
            meta["video_path"] = _find_master_mp4(folder, story)
        story["metadata"] = meta
        stories[name] = story
        videos[name] = meta["video_path"]
        compact = compact_story_for_api(story)
        # restore session identity for the model
        compact["metadata"] = dict(compact.get("metadata") or {})
        compact["metadata"]["session_name"] = name
        compact["metadata"]["session_dir_name"] = name
        ref = _load_reference_cuts(folder)
        thinned = thin_compact_for_compile(compact, ref)
        payload_runs.append({
            "source_session": name,
            "story": thinned,
        })
        print(
            f"   Packed {name}: transcript {len(thinned['transcript'])}, "
            f"telem {len(thinned['telemetry_events'])}, ref_cuts {len(ref.get('cuts') or [])}",
            flush=True,
        )

    if args.skip_gemini:
        if not os.path.exists(cuts_path):
            raise SystemExit(f"--skip-gemini but missing {cuts_path}")
        with open(cuts_path, encoding="utf-8") as f:
            validated = json.load(f)
        validated = validate_multi_cuts(validated, allowed)
    elif args.from_reference:
        # Expect session order: duelist (longest), rogue, cleric — or detect by folder name.
        by_alias = {}
        for name in allowed:
            if name.endswith("12-36-44"):
                by_alias["duelist"] = name
            elif name.endswith("11-29-31"):
                by_alias["rogue"] = name
            elif name.endswith("14-32-41"):
                by_alias["cleric"] = name
        if len(by_alias) != 3:
            raise SystemExit(f"--from-reference needs the three 2026-11-02 sessions; got {sorted(allowed)}")
        print("   Assembling reference interleave (Gemini skipped)...", flush=True)
        raw_edl = assemble_reference_interleave(by_alias)
        validated = validate_multi_cuts(raw_edl, allowed)
        validated["model_used"] = raw_edl.get("model_used")
        validated["project_title"] = raw_edl.get("project_title")
        validated["summary"] = raw_edl.get("summary")
        validated["youtube_chapters"] = raw_edl.get("youtube_chapters") or []
        validated["markers"] = raw_edl.get("markers") or []
        validated["target_duration_sec"] = raw_edl.get("target_duration_sec")
    else:
        with open(args.prompt, encoding="utf-8") as f:
            brief = f.read()
        payload = {
            "job": "9_challenge_triple_interleave",
            "runs": payload_runs,
        }
        chars = len(json.dumps(payload))
        print(f"   Payload chars: {chars} ({chars/1e6:.2f}MB)", flush=True)
        payload_path = os.path.join(out_dir, "gemini_payload.json")
        with open(payload_path, "w", encoding="utf-8") as f:
            json.dump(payload, f)
        print(f"   Wrote payload preview: {payload_path}", flush=True)
        print("   Calling Gemini...", flush=True)
        raw_edl = call_gemini_multi(brief, payload, model=args.model)
        validated = validate_multi_cuts(raw_edl, allowed)
        validated["model_used"] = raw_edl.get("model_used")

    print(f"   Snapping {len(validated['cuts'])} cuts to speech per source...")
    validated["cuts"] = snap_multi_cuts(validated["cuts"], stories)
    # recompute durations after snap
    for c in validated["cuts"]:
        c["duration_sec"] = round(float(c["end_time_sec"]) - float(c["start_time_sec"]), 2)
    # Boss IN/OUT (green) + death (red) from final timeline times
    validated["markers"] = markers_from_cuts(validated["cuts"])
    print(
        f"   Markers: {len(validated['markers'])} "
        f"(green BOSS IN/OUT, red deaths)",
        flush=True,
    )

    with open(cuts_path, "w", encoding="utf-8") as f:
        json.dump(validated, f, indent=2)
    print(f"   Wrote {cuts_path}")

    title = validated.get("project_title") or "9 Challenge Triple"
    xml = create_fcp_xml_multi(
        validated["cuts"],
        videos,
        validated.get("markers") or [],
        sequence_name=f"Agent Cut - {title}"[:120],
    )
    xml_path = os.path.join(out_dir, "timeline_resolve.xml")
    with open(xml_path, "w", encoding="utf-8") as f:
        f.write(xml)

    total = sum(c["duration_sec"] for c in validated["cuts"])
    print("\n" + "=" * 65)
    print("   MULTI-RUN RESOLVE TIMELINE READY")
    print("=" * 65)
    print(f"   XML:          {xml_path}")
    print(f"   Cuts:         {cuts_path}")
    print(f"   Clips:        {len(validated['cuts'])}")
    print(f"   Duration:     {total:.1f}s ({total/60:.1f} min)")
    print(f"   Model:        {validated.get('model_used', 'n/a')}")
    print("=" * 65)
    print("   IMPORT: DaVinci Resolve → File → Import → Timeline... → select the XML")
    print("=" * 65 + "\n")


if __name__ == "__main__":
    main()
