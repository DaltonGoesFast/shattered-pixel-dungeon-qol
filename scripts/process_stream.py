"""
process_stream.py - Post-Stream Processing & File Organization Pipeline.

Triggered via your Stream Deck button after ending a stream:
  1. Resolves the correct master video via CLI arg or telemetry session_start.video_path.
  2. Finds matching telemetry_*.jsonl (bound to that video).
  3. Finds Backtrack/Vertical highlight clips created during the run.
  4. Extracts Audio Track 2 (clean mic) with FFmpeg.
  5. Transcribes with Faster-Whisper (short prompt + lexicon cleanup).
  6. Builds session_story.json (raw game logs kept).
  7. Optionally asks Gemini for cuts.json (fail-soft to heuristics).
  8. Generates timeline_resolve.xml (speech-snapped cuts).
  9. Writes captions.srt (edit-timeline clock) + captions_full.srt (master VOD).
 10. Writes Shorts .srt sidecars only (no burned *_captioned.mp4).
"""

import os
import sys
import json
import wave
import shutil
import subprocess
import time
from datetime import datetime
import numpy as np

VIDEOS_DIR = os.path.expanduser("~/Videos")
SESSIONS_ROOT = os.path.join(VIDEOS_DIR, "Stream_Sessions")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TELEMETRY_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "Lastest UI", "telemetry_logs"))

# Ensure sibling imports work when launched as `python scripts/process_stream.py` from Stream Deck.
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from whisper_cuda import setup_cuda_dlls
from game_vocab_utils import whisper_initial_prompt, correct_transcript_with_lexicon

setup_cuda_dlls()


def _read_jsonl_events(telem_path):
    events = []
    if not telem_path or not os.path.exists(telem_path):
        return events
    with open(telem_path, encoding="utf-8") as f:
        for line in f:
            if line.strip():
                try:
                    events.append(json.loads(line))
                except Exception:
                    pass
    return events


def _session_start_from_events(events):
    for ev in events:
        if ev.get("type") == "session_start":
            return ev
    return None


def _session_sync_skew_sec(events) -> float:
    """Return clock_skew_ms/1000 from session_sync if present, else 0."""
    for ev in events:
        if ev.get("type") == "session_sync":
            try:
                return float(ev.get("clock_skew_ms", 0)) / 1000.0
            except Exception:
                return 0.0
    return 0.0


def apply_video_time_offset(events, offset_sec: float):
    """Shift video_time_sec by -offset so events align to file timeline. Keep epoch_ms."""
    if abs(offset_sec) < 0.001:
        return events
    out = []
    for ev in events:
        e = dict(ev)
        if "video_time_sec" in e:
            try:
                e["video_time_sec"] = round(max(0.0, float(e["video_time_sec"]) - offset_sec), 3)
                e["video_time_offset_applied_sec"] = round(offset_sec, 3)
            except Exception:
                pass
        out.append(e)
    return out


def find_latest_session_video():
    candidates = []
    if not os.path.exists(VIDEOS_DIR):
        return None
    for f in os.listdir(VIDEOS_DIR):
        if f.endswith(".mp4") and not f.startswith("Backtrack") and not f.startswith("Vertical"):
            full = os.path.join(VIDEOS_DIR, f)
            try:
                candidates.append((os.path.getmtime(full), full))
            except Exception:
                pass
    if not candidates:
        return None
    candidates.sort(reverse=True)
    return candidates[0][1]


def list_telemetry_logs_newest_first():
    if not os.path.exists(TELEMETRY_DIR):
        return []
    logs = [
        os.path.join(TELEMETRY_DIR, f)
        for f in os.listdir(TELEMETRY_DIR)
        if f.endswith(".jsonl")
    ]
    logs.sort(key=os.path.getmtime, reverse=True)
    return logs


def resolve_session_inputs(cli_arg: str = None):
    """
    Resolution order:
      1. CLI arg: session folder, video path, or telemetry jsonl
      2. Newest telemetry whose session_start.video_path exists on disk
      3. Fallback: latest non-Backtrack mp4 in ~/Videos (loud warning)
    Returns (video_path, telem_path, warning_or_None)
    """
    if cli_arg:
        target = os.path.abspath(os.path.expanduser(cli_arg))
        if os.path.isdir(target):
            # Session folder: prefer existing story video / mp4 / telemetry
            telem = os.path.join(target, "telemetry.jsonl")
            story = os.path.join(target, "session_story.json")
            video = None
            if os.path.exists(story):
                try:
                    with open(story, encoding="utf-8") as f:
                        meta = json.load(f).get("metadata", {})
                    video = meta.get("video_path")
                except Exception:
                    pass
            if not video or not os.path.exists(video):
                for f in os.listdir(target):
                    if f.endswith(".mp4") and not f.startswith("Backtrack"):
                        video = os.path.join(target, f)
                        break
            if video and os.path.exists(video):
                return video, telem if os.path.exists(telem) else None, None
            raise FileNotFoundError(f"No master video found in session folder: {target}")

        if target.endswith(".jsonl"):
            events = _read_jsonl_events(target)
            start = _session_start_from_events(events)
            video = (start or {}).get("video_path") or ""
            if video and os.path.exists(video):
                return video, target, None
            raise FileNotFoundError(f"Telemetry has no usable video_path: {target}")

        if target.endswith(".mp4") and os.path.exists(target):
            # Prefer telemetry that references this exact path
            for log in list_telemetry_logs_newest_first():
                events = _read_jsonl_events(log)
                start = _session_start_from_events(events)
                vp = (start or {}).get("video_path") or ""
                if vp and os.path.normcase(os.path.abspath(vp)) == os.path.normcase(os.path.abspath(target)):
                    return target, log, None
            # Also try filename-based match inside session folder telemetry
            telem = find_matching_telemetry_by_name(target)
            return target, telem, None

        raise FileNotFoundError(f"Unrecognized CLI target: {cli_arg}")

    # 2) Newest telemetry with an existing OBS video_path
    for log in list_telemetry_logs_newest_first():
        events = _read_jsonl_events(log)
        start = _session_start_from_events(events)
        if not start:
            continue
        video = start.get("video_path") or ""
        if video and os.path.exists(video):
            return video, log, None
        # Video may already have been moved into Stream_Sessions
        if video:
            base = os.path.basename(video)
            for root, _dirs, files in os.walk(SESSIONS_ROOT):
                if base in files:
                    return os.path.join(root, base), log, None

    # 3) Loud fallback
    video = find_latest_session_video()
    if not video:
        return None, None, "No stream video files found in ~/Videos."
    telem = find_matching_telemetry_by_name(video)
    warn = (
        "WARNING: Falling back to newest mp4 in ~/Videos — telemetry had no usable "
        "session_start.video_path. Confirm this is the correct recording."
    )
    return video, telem, warn


def find_matching_telemetry_by_name(video_path):
    base = os.path.splitext(os.path.basename(video_path))[0]
    date_part = base.replace(" ", "_")
    target_name = f"telemetry_{date_part}.jsonl"
    target_path = os.path.join(TELEMETRY_DIR, target_name)
    if os.path.exists(target_path):
        return target_path
    return None


def find_backtrack_clips_in_window(start_mtime, end_mtime, skew_sec=0.0):
    """Finds all Backtrack clips created within the stream window."""
    clips = []
    if not os.path.exists(VIDEOS_DIR):
        return clips
    for f in os.listdir(VIDEOS_DIR):
        if f.startswith("Backtrack") and f.endswith(".mp4"):
            full = os.path.join(VIDEOS_DIR, f)
            try:
                mtime = os.path.getmtime(full)
                if (start_mtime - 15) <= mtime <= (end_mtime + 60):
                    # Align offset to video timeline using same skew as telemetry
                    offset = max(0.0, round((mtime - start_mtime) - skew_sec, 2))
                    clips.append({
                        "file": f,
                        "path": full,
                        "created_iso": datetime.fromtimestamp(mtime).isoformat(),
                        "offset_from_start_sec": offset
                    })
            except Exception:
                pass
    clips.sort(key=lambda x: x["offset_from_start_sec"])
    return clips


def extract_track2(video_path, output_wav):
    print(f"[1/5] Extracting Track 2 (Clean Mic) from: {os.path.basename(video_path)}...")
    cmd = [
        "ffmpeg", "-y",
        "-i", video_path,
        "-map", "0:a:1",
        "-ar", "16000",
        "-ac", "1",
        "-c:a", "pcm_s16le",
        output_wav
    ]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if res.returncode != 0:
        print("      Note: Track 2 not found; falling back to Track 1 (Master Mix)...")
        cmd[5] = "0:a:0"
        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    print(f"      Audio extracted ({os.path.getsize(output_wav)/1024:.1f} KB).")


def load_audio_wav(wav_path):
    with wave.open(wav_path, "rb") as wf:
        frames = wf.readframes(wf.getnframes())
        return np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0


def _format_srt_time(sec: float) -> str:
    """HH:MM:SS,mmm for SRT."""
    if sec is None or sec < 0:
        sec = 0.0
    h = int(sec // 3600)
    m = int((sec % 3600) // 60)
    s = int(sec % 60)
    ms = int(round((sec - int(sec)) * 1000))
    if ms >= 1000:
        ms = 999
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def write_captions_srt(transcript, output_path: str) -> int:
    """
    Write SRT timed to the full master VOD (source clock).
    Useful for reviewing the whole recording; not for cut timelines.
    """
    lines = []
    idx = 0
    for seg in transcript or []:
        text = (seg.get("text") or "").strip()
        if not text:
            continue
        start = float(seg.get("start") or 0.0)
        end = float(seg.get("end") or start)
        if end <= start:
            end = start + 0.5
        idx += 1
        lines.append(str(idx))
        lines.append(f"{_format_srt_time(start)} --> {_format_srt_time(end)}")
        lines.append(text)
        lines.append("")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    return idx


def write_timeline_captions_srt(transcript, cuts, output_path: str) -> int:
    """
    Remap transcript cues onto the edited Resolve timeline clock.

    Each cut's source [start_time_sec, end_time_sec] is laid end-to-end on the
    timeline; speech overlapping a cut is clipped and shifted into that window.
    Import this file (not captions_full.srt) as the Subtitle track on timeline_resolve.xml.
    """
    lines = []
    idx = 0
    timeline_t = 0.0

    for cut in cuts or []:
        src_s = float(cut.get("start_time_sec") or 0.0)
        src_e = float(cut.get("end_time_sec") or src_s)
        if src_e <= src_s:
            continue
        cut_dur = src_e - src_s

        for seg in transcript or []:
            text = (seg.get("text") or "").strip()
            if not text:
                continue
            seg_s = float(seg.get("start") or 0.0)
            seg_e = float(seg.get("end") or seg_s)
            if seg_e <= seg_s:
                seg_e = seg_s + 0.05
            # No overlap with this cut's source window
            if seg_e <= src_s or seg_s >= src_e:
                continue
            clip_s = max(seg_s, src_s)
            clip_e = min(seg_e, src_e)
            if clip_e <= clip_s:
                continue
            tl_s = timeline_t + (clip_s - src_s)
            tl_e = timeline_t + (clip_e - src_s)
            if tl_e <= tl_s:
                tl_e = tl_s + 0.05
            idx += 1
            lines.append(str(idx))
            lines.append(f"{_format_srt_time(tl_s)} --> {_format_srt_time(tl_e)}")
            lines.append(text)
            lines.append("")

        timeline_t += cut_dur

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    return idx


def transcribe_audio(wav_path):
    from faster_whisper import WhisperModel

    audio_data = load_audio_wav(wav_path)
    prompt = whisper_initial_prompt()
    print("[2/5] Running Faster-Whisper Speech-to-Text (short SPD prompt + lexicon cleanup)...")

    use_gpu = True
    try:
        model = WhisperModel("small.en", device="cuda", compute_type="float16")
    except Exception:
        use_gpu = False

    t0 = time.time()
    results = []

    def consume(segments):
        out = []
        for s in segments:
            words = [{"word": w.word.strip(), "start": round(w.start, 2), "end": round(w.end, 2)} for w in (s.words or [])]
            out.append({
                "start": round(s.start, 2),
                "end": round(s.end, 2),
                "text": s.text.strip(),
                "words": words
            })
        return out

    if use_gpu:
        try:
            print("      Engine: NVIDIA CUDA (GPU)")
            segments, info = model.transcribe(audio_data, initial_prompt=prompt, word_timestamps=True, vad_filter=True)
            results = consume(segments)
            print(f"      GPU Speech-to-Text completed in {time.time() - t0:.2f}s!")
        except Exception as e:
            print(f"      [Notice] CUDA DLL missing ({e}). Switching to CPU...")
            use_gpu = False

    if not use_gpu:
        print("      Engine: CPU (int8)")
        model = WhisperModel("small.en", device="cpu", compute_type="int8")
        t0 = time.time()
        segments, info = model.transcribe(audio_data, initial_prompt=prompt, word_timestamps=True, vad_filter=True)
        results = consume(segments)
        print(f"      CPU Speech-to-Text completed in {time.time() - t0:.2f}s!")

    results = correct_transcript_with_lexicon(results)
    return results


def main():
    print("=" * 65)
    print("   SHATTERED PIXEL DUNGEON — POST-STREAM PROCESSOR")
    print("=" * 65)

    cli_arg = sys.argv[1] if len(sys.argv) > 1 else None
    try:
        video_path, telem_file, warn = resolve_session_inputs(cli_arg)
    except FileNotFoundError as e:
        print(f"Error: {e}")
        return

    if not video_path:
        print(f"Error: {warn or 'No stream video found.'}")
        return
    if warn:
        print(warn)

    print(f"Target Video: {video_path}")
    if telem_file:
        print(f"Telemetry:    {telem_file}")
    else:
        print("Telemetry:    None found")

    video_mtime = os.path.getmtime(video_path)
    video_size_mb = os.path.getsize(video_path) / (1024 * 1024)

    # 1. Extract and transcribe audio
    temp_wav = os.path.join(SCRIPT_DIR, "temp_stream_mic.wav")
    try:
        extract_track2(video_path, temp_wav)
        transcript = transcribe_audio(temp_wav)
    finally:
        if os.path.exists(temp_wav):
            try:
                os.remove(temp_wav)
            except Exception:
                pass

    # 2. Parse telemetry + apply clock skew offset
    print("[3/5] Parsing gameplay telemetry events...")
    raw_events = _read_jsonl_events(telem_file) if telem_file else []
    skew_sec = _session_sync_skew_sec(raw_events)
    if abs(skew_sec) >= 0.05:
        print(f"      Applying clock skew offset: {skew_sec:+.3f}s (from session_sync)")
    telemetry_events = apply_video_time_offset(raw_events, skew_sec)

    session_start_ms = None
    session_end_ms = None
    for ev in telemetry_events:
        if ev.get("type") == "session_start" and not session_start_ms:
            session_start_ms = ev.get("epoch_ms")
        if ev.get("type") == "session_end":
            session_end_ms = ev.get("epoch_ms")

    start_time_sec = (session_start_ms / 1000.0) if session_start_ms else (video_mtime - 180)
    end_time_sec = (session_end_ms / 1000.0) if session_end_ms else video_mtime

    # 3. Find backtrack clips
    backtrack_clips = find_backtrack_clips_in_window(start_time_sec, end_time_sec, skew_sec=skew_sec)
    print(f"      Found {len(backtrack_clips)} Vertical Backtrack highlight clips.")

    # 4. Synthesize chronological timeline
    print("[4/5] Building unified story timeline for AI editing...")
    combined_timeline = []

    for ev in telemetry_events:
        t_sec = ev.get("video_time_sec", 0.0)
        combined_timeline.append({
            "time_sec": t_sec,
            "category": "game" if ev.get("type") == "game_log" else "system",
            "type": ev.get("type"),
            "data": ev
        })

    for clip in backtrack_clips:
        combined_timeline.append({
            "time_sec": clip["offset_from_start_sec"],
            "category": "highlight_clip",
            "type": "vertical_backtrack",
            "data": clip
        })

    for tr in transcript:
        combined_timeline.append({
            "time_sec": tr["start"],
            "category": "speech",
            "type": "spoken_dialogue",
            "data": {
                "start": tr["start"],
                "end": tr["end"],
                "text": tr["text"],
                "words": tr["words"]
            }
        })

    combined_timeline.sort(key=lambda x: x["time_sec"])

    # 5. Organize into dedicated Session Folder
    print("[5/5] Organizing files into dedicated Stream Session folder...")
    video_basename = os.path.basename(video_path)
    # If already inside Stream_Sessions, reuse that folder
    abs_video = os.path.abspath(video_path)
    if os.path.normcase(SESSIONS_ROOT) in os.path.normcase(abs_video):
        session_dir = os.path.dirname(abs_video)
        session_slug = os.path.basename(session_dir)
        new_video_path = abs_video
        print(f"      Using existing session folder -> {session_slug}")
    else:
        base_stem = os.path.splitext(video_basename)[0]
        session_slug = base_stem.replace(" ", "_Stream_")
        session_dir = os.path.join(SESSIONS_ROOT, session_slug)
        os.makedirs(session_dir, exist_ok=True)
        new_video_path = os.path.join(session_dir, video_basename)
        try:
            if os.path.abspath(video_path) != os.path.abspath(new_video_path):
                shutil.move(video_path, new_video_path)
                print(f"      Moved video -> {os.path.basename(session_dir)}/{video_basename}")
        except Exception as e:
            new_video_path = video_path
            print(f"      Note: Could not move video (in use?): {e}")

    shorts_dir = os.path.join(session_dir, "shorts")
    if backtrack_clips:
        os.makedirs(shorts_dir, exist_ok=True)

    # Move telemetry file
    new_telem_path = None
    if telem_file and os.path.exists(telem_file):
        new_telem_path = os.path.join(session_dir, "telemetry.jsonl")
        if os.path.abspath(telem_file) != os.path.abspath(new_telem_path):
            try:
                shutil.move(telem_file, new_telem_path)
                print("      Moved telemetry -> telemetry.jsonl")
            except Exception:
                try:
                    shutil.copy2(telem_file, new_telem_path)
                except Exception:
                    new_telem_path = telem_file
        else:
            new_telem_path = telem_file

    # Move backtrack clips into shorts/
    for clip in backtrack_clips:
        orig_clip_path = clip["path"]
        if os.path.exists(orig_clip_path):
            target_clip_path = os.path.join(shorts_dir, clip["file"])
            try:
                if os.path.abspath(orig_clip_path) != os.path.abspath(target_clip_path):
                    shutil.move(orig_clip_path, target_clip_path)
                clip["path"] = target_clip_path
            except Exception:
                pass

    # Save session_story.json in the session folder
    output_json_path = os.path.join(session_dir, "session_story.json")
    master_dossier = {
        "metadata": {
            "session_name": session_slug,
            "session_dir": session_dir,
            "video_file": os.path.basename(new_video_path),
            "video_path": new_video_path,
            "video_size_mb": round(video_size_mb, 1),
            "processed_at_iso": datetime.now().isoformat(),
            "speech_segment_count": len(transcript),
            "telemetry_event_count": len(telemetry_events),
            "highlight_clip_count": len(backtrack_clips),
            "clock_skew_sec": round(skew_sec, 3),
        },
        "highlight_clips": backtrack_clips,
        "transcript": transcript,
        "telemetry_events": telemetry_events,
        "unified_story_timeline": combined_timeline
    }

    with open(output_json_path, "w", encoding="utf-8") as f:
        json.dump(master_dossier, f, indent=2)

    # 6. Optional Gemini EDL (fail-soft)
    cuts_path = os.path.join(session_dir, "cuts.json")
    used_gemini = False
    print("[6/8] Requesting Gemini Edit Decision List (fail-soft)...")
    try:
        from generate_cuts_gemini import write_cuts_for_session, load_api_key
        if not load_api_key():
            raise RuntimeError(
                "GEMINI_API_KEY not found (User env / registry / secrets.local.json)"
            )
        write_cuts_for_session(session_dir, story=master_dossier)
        used_gemini = os.path.exists(cuts_path)
        if used_gemini:
            print(f"      Gemini cuts.json written ({os.path.getsize(cuts_path)/1024:.1f} KB)")
        else:
            raise RuntimeError("write_cuts_for_session returned but cuts.json missing")
    except Exception as e:
        print(f"      Gemini unavailable — using speech-snapped heuristics.")
        print(f"      Reason: {e}")
        try:
            err_path = os.path.join(session_dir, "gemini_error.txt")
            with open(err_path, "w", encoding="utf-8") as ef:
                ef.write(str(e))
            print(f"      Wrote {os.path.basename(err_path)} for debugging")
        except Exception:
            pass

    # 7. Auto-generate DaVinci Resolve Timeline XML
    print("[7/8] Generating DaVinci Resolve Timeline XML...")
    rough_cuts = []
    try:
        from generate_resolve_xml import (
            generate_cuts_from_story,
            create_fcp_xml,
            snap_cuts_to_speech,
            get_video_duration_frames,
            DEFAULT_FPS,
        )
        markers = []
        if used_gemini and os.path.exists(cuts_path):
            with open(cuts_path, encoding="utf-8") as f:
                cut_data = json.load(f)
            rough_cuts = cut_data.get("cuts", [])
            markers = cut_data.get("markers") or []
            if not markers:
                _, markers = generate_cuts_from_story(master_dossier)
            try:
                video_dur = get_video_duration_frames(new_video_path, DEFAULT_FPS) / float(DEFAULT_FPS)
            except Exception:
                video_dur = None
            rough_cuts = snap_cuts_to_speech(rough_cuts, transcript, video_duration_sec=video_dur)
            for idx, c in enumerate(rough_cuts, start=1):
                c["clip_index"] = idx
                c["duration_sec"] = round(c["end_time_sec"] - c["start_time_sec"], 2)
        else:
            rough_cuts, markers = generate_cuts_from_story(master_dossier)

        xml_content = create_fcp_xml(
            video_path=new_video_path,
            cuts=rough_cuts,
            markers=markers,
            fps=60
        )
        xml_path = os.path.join(session_dir, "timeline_resolve.xml")
        with open(xml_path, "w", encoding="utf-8") as xf:
            xf.write(xml_content)
        src = "Gemini+speech-snap" if used_gemini else "heuristic+speech-snap"
        print(f"      Created DaVinci Resolve XML ({src}) -> {os.path.basename(xml_path)}")
    except Exception as e:
        print(f"      Warning: Could not auto-generate DaVinci XML: {e}")

    # 8. Captions: timeline-mapped (for Resolve edit) + full-VOD sidecar
    captions_path = os.path.join(session_dir, "captions.srt")
    captions_full_path = os.path.join(session_dir, "captions_full.srt")
    try:
        n_full = write_captions_srt(transcript, captions_full_path)
        print(f"[8/8] Wrote captions_full.srt ({n_full} cues) — full master VOD clock")
        if rough_cuts:
            n_caps = write_timeline_captions_srt(transcript, rough_cuts, captions_path)
            print(
                f"      Wrote captions.srt ({n_caps} cues) — EDIT timeline clock "
                "(import this on timeline_resolve.xml)"
            )
        else:
            # No cuts: fall back to full-clock file as captions.srt
            n_caps = write_captions_srt(transcript, captions_path)
            print(f"      Wrote captions.srt ({n_caps} cues) — no cuts; same as full VOD")
    except Exception as e:
        captions_path = None
        print(f"      Warning: Could not write captions: {e}")

    # 9. Shorts: SRT sidecars only (no burn-in)
    if backtrack_clips:
        print("\n[Bonus] Writing Shorts subtitle sidecars (.srt only, no burn-in)...")
        try:
            from polish_shorts import process_short_video
            for clip in backtrack_clips:
                cpath = clip.get("path")
                if cpath and os.path.exists(cpath):
                    process_short_video(cpath, burn=False)
        except Exception as e:
            print(f"      Warning: Could not polish shorts: {e}")

    print("\n" + "=" * 65)
    print("   PROCESSING COMPLETE! SESSION PACKAGED & READY")
    print("=" * 65)
    print(f"   Session Folder: {session_dir}")
    print(f"   Dossier:        session_story.json ({os.path.getsize(output_json_path)/1024:.1f} KB)")
    print(f"   Master Video:   {os.path.basename(new_video_path)} ({video_size_mb:.1f} MB)")
    print(f"   Captions:       {os.path.basename(captions_path) if captions_path else '(none)'}")
    print(f"   Speech Lines:   {len(transcript)}")
    print(f"   Game Events:    {len(telemetry_events)}")
    print(f"   Cuts Source:    {'Gemini' if used_gemini else 'Heuristic (speech-snapped)'}")
    print(f"   Shorts Saved:   {len(backtrack_clips)} in /shorts/")
    print("=" * 65 + "\n")


if __name__ == "__main__":
    main()
