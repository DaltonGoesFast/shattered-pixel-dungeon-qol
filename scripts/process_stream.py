"""
process_stream.py - Post-Stream Processing & File Organization Pipeline.

Triggered via your Stream Deck button after ending a stream:
  1. Finds the latest stream recording in ~/Videos.
  2. Finds the corresponding telemetry_*.jsonl log.
  3. Finds all Backtrack/Vertical highlight clips created during the run.
  4. Extracts Audio Track 2 (clean mic) with FFmpeg.
  5. Transcribes speech with Faster-Whisper using game_vocabulary.txt priming.
  6. Builds unified story timeline dossier (session_story.json).
  7. Organizes everything into a clean, dedicated Session folder:
     ~/Videos/Stream_Sessions/YYYY-MM-DD_Stream_HH-MM-SS/
       ├── raw_recording.mp4
       ├── session_story.json
       ├── telemetry.jsonl
       └── shorts/
           └── Backtrack_*.mp4
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
VOCABULARY_FILE = os.path.join(SCRIPT_DIR, "game_vocabulary.txt")


def load_game_vocabulary():
    """Loads game vocabulary to prime Whisper's initial_prompt."""
    if not os.path.exists(VOCABULARY_FILE):
        return None
    terms = []
    try:
        with open(VOCABULARY_FILE, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#"):
                    terms.append(line)
        if terms:
            return "Shattered Pixel Dungeon commentary. Terminology: " + ", ".join(terms)
    except Exception:
        pass
    return None


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


def find_matching_telemetry(video_path):
    base = os.path.splitext(os.path.basename(video_path))[0]
    date_part = base.replace(" ", "_")
    target_name = f"telemetry_{date_part}.jsonl"
    target_path = os.path.join(TELEMETRY_DIR, target_name)
    if os.path.exists(target_path):
        return target_path

    # Fallback to newest telemetry log
    if os.path.exists(TELEMETRY_DIR):
        logs = [os.path.join(TELEMETRY_DIR, f) for f in os.listdir(TELEMETRY_DIR) if f.endswith(".jsonl")]
        if logs:
            logs.sort(key=os.path.getmtime, reverse=True)
            return logs[0]
    return None


def find_backtrack_clips_in_window(start_mtime, end_mtime):
    """Finds all Backtrack clips created within the stream window."""
    clips = []
    for f in os.listdir(VIDEOS_DIR):
        if f.startswith("Backtrack") and f.endswith(".mp4"):
            full = os.path.join(VIDEOS_DIR, f)
            try:
                mtime = os.path.getmtime(full)
                if (start_mtime - 15) <= mtime <= (end_mtime + 60):
                    clips.append({
                        "file": f,
                        "path": full,
                        "created_iso": datetime.fromtimestamp(mtime).isoformat(),
                        "offset_from_start_sec": max(0.0, round(mtime - start_mtime, 2))
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
        "-map", "0:a:1",        # Audio stream 1 = Track 2
        "-ar", "16000",         # 16kHz for Whisper
        "-ac", "1",             # Mono
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


def transcribe_audio(wav_path):
    from faster_whisper import WhisperModel

    audio_data = load_audio_wav(wav_path)
    prompt = load_game_vocabulary()
    print("[2/5] Running Faster-Whisper Speech-to-Text (with SPD vocabulary priming)...")

    use_gpu = True
    try:
        model = WhisperModel("small.en", device="cuda", compute_type="float16")
    except Exception:
        use_gpu = False

    t0 = time.time()
    results = []

    if use_gpu:
        try:
            print("      Engine: NVIDIA CUDA (GPU)")
            segments, info = model.transcribe(audio_data, initial_prompt=prompt, word_timestamps=True, vad_filter=True)
            for s in segments:
                words = [{"word": w.word.strip(), "start": round(w.start, 2), "end": round(w.end, 2)} for w in (s.words or [])]
                results.append({
                    "start": round(s.start, 2),
                    "end": round(s.end, 2),
                    "text": s.text.strip(),
                    "words": words
                })
            print(f"      GPU Speech-to-Text completed in {time.time() - t0:.2f}s!")
            return results
        except Exception as e:
            print(f"      [Notice] CUDA DLL missing ({e}). Switching to CPU...")
            use_gpu = False

    if not use_gpu:
        print("      Engine: CPU (int8)")
        model = WhisperModel("small.en", device="cpu", compute_type="int8")
        t0 = time.time()
        segments, info = model.transcribe(audio_data, initial_prompt=prompt, word_timestamps=True, vad_filter=True)
        for s in segments:
            words = [{"word": w.word.strip(), "start": round(w.start, 2), "end": round(w.end, 2)} for w in (s.words or [])]
            results.append({
                "start": round(s.start, 2),
                "end": round(s.end, 2),
                "text": s.text.strip(),
                "words": words
            })
        print(f"      CPU Speech-to-Text completed in {time.time() - t0:.2f}s!")
        return results


def main():
    print("=" * 65)
    print("   SHATTERED PIXEL DUNGEON — POST-STREAM PROCESSOR")
    print("=" * 65)

    video_path = find_latest_session_video()
    if not video_path:
        print("Error: No stream video files found in ~/Videos.")
        return

    print(f"Target Video: {os.path.basename(video_path)}")
    telem_file = find_matching_telemetry(video_path)
    if telem_file:
        print(f"Telemetry:    {os.path.basename(telem_file)}")
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

    # 2. Parse telemetry
    print("[3/5] Parsing gameplay telemetry events...")
    telemetry_events = []
    session_start_ms = None
    session_end_ms = None

    if telem_file and os.path.exists(telem_file):
        with open(telem_file, encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    try:
                        ev = json.loads(line)
                        telemetry_events.append(ev)
                        if ev.get("type") == "session_start" and not session_start_ms:
                            session_start_ms = ev.get("epoch_ms")
                        if ev.get("type") == "session_end":
                            session_end_ms = ev.get("epoch_ms")
                    except Exception:
                        pass

    start_time_sec = (session_start_ms / 1000.0) if session_start_ms else (video_mtime - 180)
    end_time_sec = (session_end_ms / 1000.0) if session_end_ms else video_mtime

    # 3. Find backtrack clips
    backtrack_clips = find_backtrack_clips_in_window(start_time_sec, end_time_sec)
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
    base_stem = os.path.splitext(video_basename)[0]
    session_slug = base_stem.replace(" ", "_Stream_")
    session_dir = os.path.join(SESSIONS_ROOT, session_slug)
    shorts_dir = os.path.join(session_dir, "shorts")

    os.makedirs(session_dir, exist_ok=True)
    if backtrack_clips:
        os.makedirs(shorts_dir, exist_ok=True)

    # Move master video
    new_video_path = os.path.join(session_dir, video_basename)
    try:
        shutil.move(video_path, new_video_path)
        print(f"      Moved video -> {os.path.basename(session_dir)}/{video_basename}")
    except Exception as e:
        new_video_path = video_path
        print(f"      Note: Could not move video (in use?): {e}")

    # Move telemetry file
    new_telem_path = None
    if telem_file and os.path.exists(telem_file):
        new_telem_path = os.path.join(session_dir, "telemetry.jsonl")
        try:
            shutil.move(telem_file, new_telem_path)
            print(f"      Moved telemetry -> telemetry.jsonl")
        except Exception:
            try:
                shutil.copy2(telem_file, new_telem_path)
            except Exception:
                new_telem_path = telem_file

    # Move backtrack clips into shorts/
    for clip in backtrack_clips:
        orig_clip_path = clip["path"]
        if os.path.exists(orig_clip_path):
            target_clip_path = os.path.join(shorts_dir, clip["file"])
            try:
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
        },
        "highlight_clips": backtrack_clips,
        "transcript": transcript,
        "telemetry_events": telemetry_events,
        "unified_story_timeline": combined_timeline
    }

    with open(output_json_path, "w", encoding="utf-8") as f:
        json.dump(master_dossier, f, indent=2)

    print("\n" + "=" * 65)
    print("   PROCESSING COMPLETE! SESSION PACKAGED & READY")
    print("=" * 65)
    print(f"   Session Folder: {session_dir}")
    print(f"   Dossier:        session_story.json ({os.path.getsize(output_json_path)/1024:.1f} KB)")
    print(f"   Master Video:   {os.path.basename(new_video_path)} ({video_size_mb:.1f} MB)")
    print(f"   Speech Lines:   {len(transcript)}")
    print(f"   Game Events:    {len(telemetry_events)}")
    print(f"   Shorts Saved:   {len(backtrack_clips)} in /shorts/")
    print("=" * 65)
    print("   Root ~/Videos is clean. Archive is ready for AI Editing Agent!")
    print("=" * 65 + "\n")


if __name__ == "__main__":
    main()
