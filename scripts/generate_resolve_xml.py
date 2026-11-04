"""
generate_resolve_xml.py - Generates DaVinci Resolve FCP-XML Timelines from Agent Cuts.

Converts an AI Edit Decision List (or automated highlights from session_story.json)
into a standard Final Cut Pro XML (.xml) that DaVinci Resolve imports in 1 click.

Features:
  - Video track sliced at exact in/out frames.
  - Multi-track audio preserved:
      Track 1: Master Mix
      Track 2: Clean Voice
      Track 3: Alerts / Streamerbot
      Track 4: Game Audio
  - Color-coded DaVinci Timeline Markers placed for:
      - Boss Fights (Green)
      - Near-Death / Hero Death (Red)
      - Vertical Backtrack Highlights (Cyan)
"""

import os
import sys
import json
import urllib.parse
import subprocess
from xml.etree import ElementTree as ET
from xml.dom import minidom

DEFAULT_FPS = 60


def sec_to_frames(seconds: float, fps: int = DEFAULT_FPS) -> int:
    return int(round(seconds * fps))


def get_video_duration_frames(video_path: str, fps: int = DEFAULT_FPS) -> int:
    try:
        cmd = ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", video_path]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
        dur_sec = float(res.stdout.strip())
        return int(round(dur_sec * fps))
    except Exception:
        return 3600 * fps * 4


def create_fcp_xml(video_path: str, cuts: list, markers: list, fps: int = DEFAULT_FPS) -> str:
    """Builds FCP7 XML string for DaVinci Resolve import."""
    norm_path = os.path.abspath(video_path).replace("\\", "/")
    # Keep ':' and '/' unescaped so Windows drive letters (C:) work in DaVinci Resolve
    file_uri = "file://localhost/" + urllib.parse.quote(norm_path, safe=":/")
    file_name = os.path.basename(video_path)
    total_media_frames = get_video_duration_frames(video_path, fps)

    # Calculate total sequence duration in frames
    total_timeline_frames = sum(sec_to_frames(c["duration_sec"], fps) for c in cuts)

    xmeml = ET.Element("xmeml", version="5")
    sequence = ET.SubElement(xmeml, "sequence")
    
    ET.SubElement(sequence, "name").text = f"Agent Cut - {os.path.splitext(file_name)[0]}"
    ET.SubElement(sequence, "duration").text = str(total_timeline_frames)

    rate = ET.SubElement(sequence, "rate")
    ET.SubElement(rate, "timebase").text = str(fps)
    ET.SubElement(rate, "ntsc").text = "FALSE"

    media = ET.SubElement(sequence, "media")

    # -------------------------------------------------------------
    # Video Track
    # -------------------------------------------------------------
    video = ET.SubElement(media, "video")
    v_format = ET.SubElement(video, "format")
    sample_char = ET.SubElement(v_format, "samplecharacteristics")
    ET.SubElement(sample_char, "width").text = "1920"
    ET.SubElement(sample_char, "height").text = "1080"
    ET.SubElement(sample_char, "pixelaspectratio").text = "square"

    v_track = ET.SubElement(video, "track")

    curr_timeline_frame = 0
    for idx, cut in enumerate(cuts, start=1):
        in_frame = sec_to_frames(cut["start_time_sec"], fps)
        out_frame = sec_to_frames(cut["end_time_sec"], fps)
        dur_frames = out_frame - in_frame

        clipitem = ET.SubElement(v_track, "clipitem", id=f"v_clip_{idx}")
        ET.SubElement(clipitem, "name").text = cut.get("title", f"Clip {idx}")
        ET.SubElement(clipitem, "duration").text = str(dur_frames)

        c_rate = ET.SubElement(clipitem, "rate")
        ET.SubElement(c_rate, "timebase").text = str(fps)
        ET.SubElement(c_rate, "ntsc").text = "FALSE"

        ET.SubElement(clipitem, "in").text = str(in_frame)
        ET.SubElement(clipitem, "out").text = str(out_frame)
        ET.SubElement(clipitem, "start").text = str(curr_timeline_frame)
        ET.SubElement(clipitem, "end").text = str(curr_timeline_frame + dur_frames)

        # File reference
        file_el = ET.SubElement(clipitem, "file", id="file_master")
        ET.SubElement(file_el, "name").text = file_name
        ET.SubElement(file_el, "pathurl").text = file_uri
        f_rate = ET.SubElement(file_el, "rate")
        ET.SubElement(f_rate, "timebase").text = str(fps)
        ET.SubElement(file_el, "duration").text = str(total_media_frames)
        f_media = ET.SubElement(file_el, "media")
        f_video = ET.SubElement(f_media, "video")
        ET.SubElement(f_video, "duration").text = str(total_media_frames)

        curr_timeline_frame += dur_frames

    # -------------------------------------------------------------
    # 4 Audio Tracks (A1: Master, A2: Mic, A3: Alerts, A4: Game)
    # -------------------------------------------------------------
    audio = ET.SubElement(media, "audio")
    for track_idx in range(1, 5):
        a_track = ET.SubElement(audio, "track")
        curr_a_frame = 0
        for idx, cut in enumerate(cuts, start=1):
            in_frame = sec_to_frames(cut["start_time_sec"], fps)
            out_frame = sec_to_frames(cut["end_time_sec"], fps)
            dur_frames = out_frame - in_frame

            aclip = ET.SubElement(a_track, "clipitem", id=f"a{track_idx}_clip_{idx}")
            ET.SubElement(aclip, "name").text = f"{cut.get('title', f'Clip {idx}')} (A{track_idx})"
            ET.SubElement(aclip, "duration").text = str(dur_frames)

            arate = ET.SubElement(aclip, "rate")
            ET.SubElement(arate, "timebase").text = str(fps)

            ET.SubElement(aclip, "in").text = str(in_frame)
            ET.SubElement(aclip, "out").text = str(out_frame)
            ET.SubElement(aclip, "start").text = str(curr_a_frame)
            ET.SubElement(aclip, "end").text = str(curr_a_frame + dur_frames)

            afile = ET.SubElement(aclip, "file", id="file_master")
            ET.SubElement(afile, "name").text = file_name
            ET.SubElement(afile, "pathurl").text = file_uri
            f_rate = ET.SubElement(afile, "rate")
            ET.SubElement(f_rate, "timebase").text = str(fps)
            ET.SubElement(afile, "duration").text = str(total_media_frames)

            sourcetrack = ET.SubElement(aclip, "sourcetrack")
            ET.SubElement(sourcetrack, "mediatype").text = "audio"
            ET.SubElement(sourcetrack, "trackindex").text = str(track_idx)

            curr_a_frame += dur_frames

    # -------------------------------------------------------------
    # Timeline Markers (Chapter / Drama / Highlights)
    # -------------------------------------------------------------
    for m in markers:
        # Check if marker falls into any cut
        source_time = m.get("source_time_sec", 0.0)
        # Find position on timeline
        acc = 0
        timeline_pos = None
        for cut in cuts:
            if cut["start_time_sec"] <= source_time <= cut["end_time_sec"]:
                offset_in_cut = source_time - cut["start_time_sec"]
                timeline_pos = acc + sec_to_frames(offset_in_cut, fps)
                break
            acc += sec_to_frames(cut["duration_sec"], fps)

        if timeline_pos is not None:
            marker_el = ET.SubElement(sequence, "marker")
            ET.SubElement(marker_el, "name").text = m.get("name", "Event")
            ET.SubElement(marker_el, "comment").text = m.get("comment", "")
            ET.SubElement(marker_el, "in").text = str(timeline_pos)
            ET.SubElement(marker_el, "out").text = str(timeline_pos)

    xml_str = ET.tostring(xmeml, encoding="utf-8")
    parsed = minidom.parseString(xml_str)
    return parsed.toprettyxml(indent="  ")


def _collect_word_spans(transcript: list) -> list:
    """Flatten transcript into sorted (start, end) word spans."""
    spans = []
    for seg in transcript or []:
        words = seg.get("words") or []
        if words:
            for w in words:
                try:
                    spans.append((float(w["start"]), float(w["end"])))
                except Exception:
                    pass
        else:
            try:
                spans.append((float(seg["start"]), float(seg["end"])))
            except Exception:
                pass
    spans.sort(key=lambda x: x[0])
    return spans


def _sentence_end_after(spans: list, t: float, max_extend: float = 12.0) -> float:
    """If t lands mid-phrase, extend to the end of the current spoken sentence."""
    if not spans:
        return t
    # Find the word covering / just before t
    idx = None
    for i, (ws, we) in enumerate(spans):
        if ws <= t <= we + 0.35:
            idx = i
            break
        if ws > t:
            idx = max(0, i - 1)
            break
    if idx is None:
        return t
    end = spans[idx][1]
    j = idx
    limit = t + max_extend
    while j + 1 < len(spans) and spans[j + 1][0] <= limit:
        gap = spans[j + 1][0] - spans[j][1]
        # Sentence / phrase break: longer pause
        if gap >= 0.55:
            break
        j += 1
        end = spans[j][1]
        if end >= limit:
            break
    return max(t, end)


def snap_cuts_to_speech(
    cuts: list,
    transcript: list,
    video_duration_sec: float = None,
    pre_roll: float = 0.55,
    post_roll: float = 0.75,
    search_pad: float = 4.0,
    max_duration_sec: float = 40.0,
) -> list:
    """
    Snap cut edges into silence / speech breathing room (snappy bias).
    - start: silence gap / pre-roll before first word in window
    - end: prefer not cutting mid-sentence; finish the current phrase
    - boss/fight/clutch titles: do NOT shorten the authored window (full takes)
    - other clips: may shorten to last speech; clamp very long clips
    """
    spans = _collect_word_spans(transcript)
    if not cuts:
        return cuts

    snapped = []
    for cut in cuts:
        s = float(cut.get("start_time_sec", 0.0))
        e = float(cut.get("end_time_sec", s))
        if e <= s:
            e = s + 1.0

        title = (cut.get("title") or "").lower()
        pacing = (cut.get("pacing") or "").lower()
        allow_long = pacing in ("boss", "long", "normal") or any(
            k in title
            for k in (
                "boss",
                "clutch",
                "near death",
                "hero death",
                "fight",
                "yog",
                "ascension",
                "alchemy",
                "quest",
                "sacrificial",
                "build",
                "artifact",
                "amulet",
                "shop",
            )
        )

        window_words = [sp for sp in spans if sp[1] >= s and sp[0] <= e]

        if window_words:
            first_word_start = window_words[0][0]
            last_word_end = window_words[-1][1]

            if allow_long:
                # Preserve authored window (boss/story packages); only fix mid-sentence edges
                new_s = max(0.0, s)
                # If we open mid-word, roll back to that word's start / prior gap
                for ws, we in spans:
                    if ws <= new_s <= we:
                        prev_ends = [sp[1] for sp in spans if sp[1] <= ws]
                        gap_start = prev_ends[-1] if prev_ends else max(0.0, ws - pre_roll)
                        new_s = max(gap_start, ws - pre_roll)
                        break
                new_e = max(e, last_word_end + post_roll)
                new_e = _sentence_end_after(spans, new_e, max_extend=14.0) + post_roll
            else:
                prev_ends = [sp[1] for sp in spans if sp[1] <= first_word_start]
                gap_start = prev_ends[-1] if prev_ends else max(0.0, first_word_start - pre_roll)
                new_s = max(gap_start, first_word_start - pre_roll)
                new_s = max(0.0, max(s - search_pad, new_s))
                # Snappy end on last speech inside window, but finish the sentence
                new_e = last_word_end + post_roll
                new_e = min(max(new_e, e), e + search_pad)
                new_e = _sentence_end_after(spans, new_e, max_extend=10.0) + post_roll
                new_e = max(new_e, first_word_start + 0.8)
        else:
            new_s = max(0.0, s)
            new_e = e

        if video_duration_sec is not None:
            new_s = min(new_s, max(0.0, video_duration_sec - 0.1))
            new_e = min(new_e, video_duration_sec)

        if (not allow_long) and (new_e - new_s) > max_duration_sec and window_words:
            # Keep the end (payoff/reaction), trim earlier walk-and-talk
            new_s = max(new_s, new_e - max_duration_sec)

        if new_e <= new_s:
            new_e = new_s + 0.5

        out = dict(cut)
        out["start_time_sec"] = round(new_s, 2)
        out["end_time_sec"] = round(new_e, 2)
        out["duration_sec"] = round(new_e - new_s, 2)
        snapped.append(out)

    return snapped


def generate_cuts_from_story(story: dict) -> tuple:
    """Fallback: Generates high-confidence rough cuts if no AI cuts.json provided."""
    meta = story.get("metadata", {})
    highlights = story.get("highlight_clips", [])
    transcript = story.get("transcript", [])

    # Find anchor windows
    anchor_times = []
    markers = []

    # 1. Backtrack clips (tighter windows — snappy bias)
    for h in highlights:
        t = h.get("offset_from_start_sec", 0.0)
        anchor_times.append((max(0.0, t - 18.0), t + 4.0, "Vertical Highlight"))
        markers.append({"name": "Short Highlight", "source_time_sec": t, "comment": h.get("file", "")})

    # 2. Telemetry drama (boss kills, hero death, low HP)
    for ev in story.get("telemetry_events", []):
        t = ev.get("video_time_sec", 0.0)
        etype = ev.get("type")

        if etype == "boss_slain":
            # Prefer long continuous boss takes; speech-snap will not shorten "Boss" titles
            anchor_times.append((max(0.0, t - 90.0), t + 25.0, "Boss Fight"))
            markers.append({"name": f"Boss Slain (Depth {ev.get('depth')})", "source_time_sec": t})
        elif etype == "hero_died":
            anchor_times.append((max(0.0, t - 18.0), t + 6.0, "Hero Death"))
            markers.append({"name": "Hero Died", "source_time_sec": t})
        elif etype == "danger_low_hp":
            anchor_times.append((max(0.0, t - 10.0), t + 8.0, "Near Death"))
            markers.append({"name": "Near Death (HP Drop)", "source_time_sec": t})
        elif etype == "amulet_obtained":
            anchor_times.append((max(0.0, t - 20.0), t + 8.0, "Amulet"))
            markers.append({"name": "Amulet Obtained", "source_time_sec": t})
        elif etype in ("ascended", "surface_reached"):
            anchor_times.append((max(0.0, t - 15.0), t + 10.0, "Ascension"))
            markers.append({"name": "Ascended / Surface", "source_time_sec": t})
        elif etype == "quest_complete":
            quest = ev.get("quest", "quest")
            anchor_times.append((max(0.0, t - 22.0), t + 10.0, "Quest Complete"))
            markers.append({"name": f"Quest Complete ({quest})", "source_time_sec": t})
        elif etype == "sacrificial_reward":
            prize = ev.get("prize_class") or ev.get("item_class") or "reward"
            anchor_times.append((max(0.0, t - 35.0), t + 12.0, "Sacrificial Reward"))
            markers.append({"name": f"Sacrificial Reward ({prize})", "source_time_sec": t})
        elif etype == "item_picked_up" and (ev.get("category") == "artifact" or "Artifact" in str(ev.get("item_class") or "")):
            name = ev.get("item_name") or ev.get("item_class") or "artifact"
            anchor_times.append((max(0.0, t - 8.0), t + 14.0, "Artifact Find"))
            markers.append({"name": f"Artifact ({name})", "source_time_sec": t})
        elif etype == "item_used" and "Upgrade" in str(ev.get("item_class") or ""):
            anchor_times.append((max(0.0, t - 8.0), t + 10.0, "Scroll of Upgrade"))
            markers.append({"name": "Scroll of Upgrade", "source_time_sec": t})
        elif etype == "shop_buy":
            # Prefer post-boss shopping; short window so merge can attach to boss aftermath
            anchor_times.append((max(0.0, t - 4.0), t + 8.0, "Shop"))
        elif etype == "mob_slain":
            mob = ev.get("mob_name") or ev.get("mob_class") or "Miniboss"
            anchor_times.append((max(0.0, t - 22.0), t + 6.0, "Miniboss Slain"))
            markers.append({"name": f"Mob Slain ({mob})", "source_time_sec": t})

    # Always keep a short intro hook
    anchor_times.append((0.0, 25.0, "Intro"))

    # Merge overlapping windows
    anchor_times.sort(key=lambda x: x[0])
    merged = []
    for s, e, label in anchor_times:
        if not merged:
            merged.append([s, e, label])
        else:
            prev_s, prev_e, prev_label = merged[-1]
            if s <= prev_e + 3.0:  # If within 3 seconds, bridge them
                merged[-1][1] = max(prev_e, e)
                merged[-1][2] = f"{prev_label} & {label}"
            else:
                merged.append([s, e, label])

    cuts = []
    for idx, (s, e, label) in enumerate(merged, start=1):
        cuts.append({
            "clip_index": idx,
            "start_time_sec": round(s, 2),
            "end_time_sec": round(e, 2),
            "duration_sec": round(e - s, 2),
            "title": label
        })

    video_dur = None
    video_path = meta.get("video_path")
    if video_path and os.path.exists(video_path):
        try:
            video_dur = get_video_duration_frames(video_path, DEFAULT_FPS) / float(DEFAULT_FPS)
        except Exception:
            video_dur = None

    cuts = snap_cuts_to_speech(cuts, transcript, video_duration_sec=video_dur)
    for idx, c in enumerate(cuts, start=1):
        c["clip_index"] = idx

    return cuts, markers


def main():
    if len(sys.argv) < 2:
        # Search for latest session folder in ~/Videos/Stream_Sessions
        sessions_root = os.path.expanduser("~/Videos/Stream_Sessions")
        if not os.path.exists(sessions_root):
            print("Usage: python generate_resolve_xml.py <path_to_session_story.json or session_folder> [optional_cuts.json]")
            return
        subdirs = [os.path.join(sessions_root, d) for d in os.listdir(sessions_root) if os.path.isdir(os.path.join(sessions_root, d))]
        if not subdirs:
            print("No session folders found in ~/Videos/Stream_Sessions.")
            return
        subdirs.sort(key=os.path.getmtime, reverse=True)
        session_folder = subdirs[0]
        story_path = os.path.join(session_folder, "session_story.json")
    else:
        target = sys.argv[1]
        if os.path.isdir(target):
            session_folder = target
            story_path = os.path.join(session_folder, "session_story.json")
        else:
            story_path = target
            session_folder = os.path.dirname(story_path)

    if not os.path.exists(story_path):
        print(f"Error: session_story.json not found at {story_path}")
        return

    with open(story_path, encoding="utf-8") as f:
        story = json.load(f)

    meta = story.get("metadata", {})
    video_path = meta.get("video_path")
    if not video_path or not os.path.exists(video_path):
        # Fallback to looking inside session folder
        for f in os.listdir(session_folder):
            if f.endswith(".mp4"):
                video_path = os.path.join(session_folder, f)
                break

    if not video_path or not os.path.exists(video_path):
        print(f"Error: Master video file not found in {session_folder}")
        return

    # Check for optional AI-generated cuts.json
    custom_cuts_path = os.path.join(session_folder, "cuts.json")
    if len(sys.argv) >= 3 and os.path.exists(sys.argv[2]):
        custom_cuts_path = sys.argv[2]

    if os.path.exists(custom_cuts_path):
        print(f"Using AI Agent cuts from: {os.path.basename(custom_cuts_path)}")
        with open(custom_cuts_path, encoding="utf-8") as f:
            cut_data = json.load(f)
            cuts = cut_data.get("cuts", cut_data if isinstance(cut_data, list) else [])
            markers = cut_data.get("markers", [])
            # Still derive telemetry markers if AI file omitted them
            if not markers:
                _, markers = generate_cuts_from_story(story)
        transcript = story.get("transcript", [])
        try:
            video_dur = get_video_duration_frames(video_path, DEFAULT_FPS) / float(DEFAULT_FPS)
        except Exception:
            video_dur = None
        # Authored / Gemini EDLs already chose windows — only nudge edges for speech,
        # do not crush long story clips with the heuristic 40s cap.
        cuts = snap_cuts_to_speech(
            cuts, transcript, video_duration_sec=video_dur, max_duration_sec=900.0
        )
        for idx, c in enumerate(cuts, start=1):
            c["clip_index"] = idx
            if "duration_sec" not in c:
                c["duration_sec"] = round(c["end_time_sec"] - c["start_time_sec"], 2)
    else:
        print("Synthesizing rough-cut timeline from telemetry highlights...")
        cuts, markers = generate_cuts_from_story(story)

    xml_content = create_fcp_xml(video_path, cuts, markers, fps=DEFAULT_FPS)

    output_xml_path = os.path.join(session_folder, "timeline_resolve.xml")
    with open(output_xml_path, "w", encoding="utf-8") as f:
        f.write(xml_content)

    print("\n" + "=" * 65)
    print("   DAVINCI RESOLVE TIMELINE GENERATED!")
    print("=" * 65)
    print(f"   XML File:       {output_xml_path}")
    print(f"   Master Video:   {os.path.basename(video_path)}")
    print(f"   Total Slices:   {len(cuts)} clips")
    print(f"   Timeline Dur:   {sum(c['duration_sec'] for c in cuts):.1f} seconds")
    print(f"   Markers Added:  {len(markers)} (Color-coded in Resolve)")
    print("=" * 65)
    print("   HOW TO OPEN IN DAVINCI RESOLVE:")
    print("   1. Open DaVinci Resolve.")
    print("   2. File -> Import -> Timeline... (Ctrl+Shift+I)")
    print(f"   3. Select: {output_xml_path}")
    print("=" * 65 + "\n")


if __name__ == "__main__":
    main()
