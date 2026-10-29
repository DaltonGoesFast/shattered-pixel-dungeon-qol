"""
process_stream.py - Post-Stream Processing Pipeline.

Runs when you hit your Stream Deck button after ending a stream:
  1. Finds the latest stream video in ~/Videos.
  2. Finds the corresponding telemetry_*.jsonl log.
  3. Finds all Backtrack/Vertical highlight clips created during the run.
  4. Extracts Audio Track 2 (clean mic) with FFmpeg.
  5. Transcribes speech with Faster-Whisper (GPU with CPU fallback).
  6. Weaves everything into a single master story file:
     ~/Videos/processed_sessions/session_story_YYYY-MM-DD_HH-MM-SS.json
"""

import os
import sys
import json
import wave
import subprocess
import time
from datetime import datetime
import numpy as np

VIDEOS_DIR = os.path.expanduser("~/Videos")
PROCESSED_DIR = os.path.join(VIDEOS_DIR, "processed_sessions")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TELEMETRY_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "Lastest UI", "telemetry_logs"))


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
                # Allow a small buffer before and after recording window
                if (start_mtime - 10) <= mtime <= (end_mtime + 60):
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
    print(f"[1/4] Extracting Track 2 (Clean Mic) from: {os.path.basename(video_path)}...")
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
    print("[2/4] Running Faster-Whisper Speech-to-Text...")

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
            segments, info = model.transcribe(audio_data, word_timestamps=True, vad_filter=True)
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
        segments, info = model.transcribe(audio_data, word_timestamps=True, vad_filter=True)
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
        print("Telemetry:    None found (using audio only)")

    # Recording time bounds
    video_mtime = os.path.getmtime(video_path)
    video_size_mb = os.path.getsize(video_path) / (1024 * 1024)

    # 1. Extract audio
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
    print("[3/4] Parsing gameplay telemetry events...")
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
    print("[4/4] Building unified story timeline for AI editing...")
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

    # 5. Save master dossier
    os.makedirs(PROCESSED_DIR, exist_ok=True)
    base_name = os.path.splitext(os.path.basename(video_path))[0].replace(" ", "_")
    output_json_path = os.path.join(PROCESSED_DIR, f"session_story_{base_name}.json")

    master_dossier = {
        "metadata": {
            "video_file": os.path.basename(video_path),
            "video_path": video_path,
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
    print("   PROCESSING COMPLETE! MASTER STORY DOSSIER CREATED")
    print("=" * 65)
    print(f"   Dossier:  {output_json_path}")
    print(f"   Size:     {os.path.getsize(output_json_path) / 1024:.1f} KB")
    print(f"   Speech:   {len(transcript)} spoken segments")
    print(f"   Game:     {len(telemetry_events)} telemetry events")
    print(f"   Shorts:   {len(backtrack_clips)} manual backtrack clips")
    print("=" * 65)
    print("   Ready for AI Video Editing Agent!")
    print("=" * 65 + "\n")


if __name__ == "__main__":
    main()
