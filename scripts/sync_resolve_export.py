"""
sync_resolve_export.py — Remap markers + captions onto an edited Resolve FCP7 XML.

Usage:
  python scripts/sync_resolve_export.py <session_folder> [edited.xml]

Defaults to <session_folder>/mycut.xml.
Writes:
  - captions.srt          (edit-timeline clock matching the XML)
  - cuts_edited.json      (source windows from the XML)
  - <stem>_marked.xml     (same timeline + markers remapped to current cuts)
"""

from __future__ import annotations

import json
import os
import sys
from copy import deepcopy
from xml.etree import ElementTree as ET
from xml.dom import minidom

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

from generate_resolve_xml import generate_cuts_from_story  # noqa: E402
from process_stream import write_timeline_captions_srt  # noqa: E402


def _fps_of(seq: ET.Element) -> int:
    tb = seq.findtext("rate/timebase")
    return int(tb) if tb else 60


def parse_video_cuts(xml_path: str) -> tuple[list[dict], int, ET.ElementTree]:
    tree = ET.parse(xml_path)
    root = tree.getroot()
    seq = root.find("sequence")
    if seq is None:
        raise RuntimeError("No <sequence> in XML")
    fps = _fps_of(seq)
    track = seq.find("media/video/track")
    if track is None:
        raise RuntimeError("No video track in XML")

    cuts = []
    for c in track.findall("clipitem"):
        if (c.findtext("enabled") or "TRUE").upper() == "FALSE":
            continue
        start = int(float(c.findtext("start") or -1))
        end = int(float(c.findtext("end") or -1))
        inn = int(float(c.findtext("in") or -1))
        out = int(float(c.findtext("out") or -1))
        if start < 0 or end <= start or inn < 0 or out <= inn:
            continue
        cuts.append({
            "clip_index": len(cuts) + 1,
            "start_time_sec": round(inn / fps, 3),
            "end_time_sec": round(out / fps, 3),
            "duration_sec": round((out - inn) / fps, 3),
            "timeline_start_sec": round(start / fps, 3),
            "timeline_end_sec": round(end / fps, 3),
            "title": f"Edit clip {len(cuts) + 1}",
            "pacing": "normal",
        })
    if not cuts:
        raise RuntimeError("No video clipitems found")
    return cuts, fps, tree


def source_to_timeline(cuts: list[dict], source_sec: float) -> float | None:
    for c in cuts:
        s = c["start_time_sec"]
        e = c["end_time_sec"]
        if s <= source_sec <= e:
            return c["timeline_start_sec"] + (source_sec - s)
        # tiny edge tolerance
        if abs(source_sec - s) < 0.05:
            return c["timeline_start_sec"]
        if abs(source_sec - e) < 0.05:
            return c["timeline_end_sec"]
    return None


def build_markers(story: dict, cuts: list[dict], fps: int) -> list[dict]:
    _, raw_markers = generate_cuts_from_story(story)
    out = []
    for m in raw_markers:
        src = float(m.get("source_time_sec") or 0.0)
        tl = source_to_timeline(cuts, src)
        if tl is None:
            continue
        out.append({
            "name": m.get("name") or "Event",
            "comment": m.get("comment") or "",
            "timeline_sec": round(tl, 3),
            "source_time_sec": round(src, 3),
            "in_frame": int(round(tl * fps)),
        })
    return out


def write_marked_xml(tree: ET.ElementTree, markers: list[dict], out_path: str) -> None:
    root = tree.getroot()
    seq = root.find("sequence")
    # Remove existing sequence-level markers (stale absolute positions)
    for m in list(seq.findall("marker")):
        seq.remove(m)
    for m in markers:
        el = ET.SubElement(seq, "marker")
        ET.SubElement(el, "name").text = m["name"]
        ET.SubElement(el, "comment").text = m.get("comment") or ""
        fr = str(m["in_frame"])
        ET.SubElement(el, "in").text = fr
        ET.SubElement(el, "out").text = fr

    xml_bytes = ET.tostring(root, encoding="utf-8")
    pretty = minidom.parseString(xml_bytes).toprettyxml(indent="    ")
    # minidom adds extra XML declaration; Resolve is fine with one
    lines = pretty.splitlines()
    if lines and lines[0].startswith("<?xml"):
        body = "\n".join(lines[1:]).strip() + "\n"
        text = '<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE xmeml>\n' + body
    else:
        text = pretty
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(text)


def main():
    if len(sys.argv) < 2:
        print("Usage: python sync_resolve_export.py <session_folder> [edited.xml]")
        sys.exit(2)

    session = os.path.abspath(sys.argv[1])
    xml_path = os.path.abspath(sys.argv[2]) if len(sys.argv) >= 3 else os.path.join(session, "mycut.xml")
    story_path = os.path.join(session, "session_story.json")
    if not os.path.exists(xml_path):
        raise SystemExit(f"Missing XML: {xml_path}")
    if not os.path.exists(story_path):
        raise SystemExit(f"Missing session_story.json: {story_path}")

    with open(story_path, encoding="utf-8") as f:
        story = json.load(f)

    cuts, fps, tree = parse_video_cuts(xml_path)
    markers = build_markers(story, cuts, fps)

    caps_path = os.path.join(session, "captions.srt")
    n_caps = write_timeline_captions_srt(story.get("transcript") or [], cuts, caps_path)

    cuts_path = os.path.join(session, "cuts_edited.json")
    payload = {
        "project_title": "Edited Resolve cut",
        "source_xml": os.path.basename(xml_path),
        "target_duration_sec": round(sum(c["duration_sec"] for c in cuts), 2),
        "fps": fps,
        "summary": f"Synced from {os.path.basename(xml_path)} ({len(cuts)} clips).",
        "cuts": cuts,
        "markers": [
            {"name": m["name"], "source_time_sec": m["source_time_sec"], "comment": m.get("comment", "")}
            for m in markers
        ],
        "model_used": "resolve-export-sync",
    }
    with open(cuts_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    stem, _ = os.path.splitext(xml_path)
    marked_path = stem + "_marked.xml"
    # Work on a deep copy so we don't mutate parse state oddly
    write_marked_xml(deepcopy(tree), markers, marked_path)

    dur = sum(c["duration_sec"] for c in cuts)
    print("=" * 60)
    print("  RESOLVE EXPORT SYNCED")
    print("=" * 60)
    print(f"  XML in:     {xml_path}")
    print(f"  Clips:      {len(cuts)}")
    print(f"  Duration:   {dur/60:.1f} min")
    print(f"  Markers:    {len(markers)} remapped (kept only if source still on timeline)")
    print(f"  Captions:   {caps_path} ({n_caps} cues)")
    print(f"  Cuts JSON:  {cuts_path}")
    print(f"  Marked XML: {marked_path}")
    print("=" * 60)
    print("  Re-import the *_marked.xml timeline (or replace markers),")
    print("  and import captions.srt on the Subtitle track.")
    print("=" * 60)


if __name__ == "__main__":
    main()
