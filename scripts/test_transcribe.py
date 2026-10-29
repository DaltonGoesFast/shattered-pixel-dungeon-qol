"""
test_transcribe.py - Test script to extract Track 2 (Mic) and transcribe with faster-whisper.
Then displays a combined chronological timeline with the matching telemetry events!
"""

import os
import sys
import json
import wave
import subprocess
import time
import numpy as np

def find_latest_video():
    videos_dir = os.path.expanduser("~/Videos")
    candidates = []
    for f in os.listdir(videos_dir):
        if f.endswith(".mp4") and not f.startswith("Backtrack") and not f.startswith("Vertical"):
            full_path = os.path.join(videos_dir, f)
            candidates.append((os.path.getmtime(full_path), full_path))
    if not candidates:
        return None
    candidates.sort(reverse=True)
    return candidates[0][1]

def find_matching_telemetry(video_path):
    base = os.path.splitext(os.path.basename(video_path))[0]
    date_part = base.replace(" ", "_")
    target_name = f"telemetry_{date_part}.jsonl"

    script_dir = os.path.dirname(os.path.abspath(__file__))
    telemetry_dir = os.path.normpath(os.path.join(script_dir, "..", "Lastest UI", "telemetry_logs"))
    target_path = os.path.join(telemetry_dir, target_name)
    if os.path.exists(target_path):
        return target_path
    
    if os.path.exists(telemetry_dir):
        logs = [os.path.join(telemetry_dir, f) for f in os.listdir(telemetry_dir) if f.endswith(".jsonl")]
        if logs:
            logs.sort(key=os.path.getmtime, reverse=True)
            return logs[0]
    return None

def extract_track2(video_path, output_wav):
    print(f"\n[1/3] Extracting Track 2 (Isolated Mic) from: {os.path.basename(video_path)}...")
    cmd = [
        "ffmpeg", "-y",
        "-i", video_path,
        "-map", "0:a:1",        # Audio stream 1 = Track 2
        "-ar", "16000",         # 16kHz sample rate optimal for Whisper
        "-ac", "1",             # Mono
        "-c:a", "pcm_s16le",
        output_wav
    ]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if res.returncode != 0:
        print("Warning: Track 2 extraction failed. Attempting Track 1 fallback...")
        cmd[5] = "0:a:0"
        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    print(f"      Extracted to: {output_wav} ({os.path.getsize(output_wav)/1024:.1f} KB)")

def load_audio_wav(wav_path):
    with wave.open(wav_path, "rb") as wf:
        frames = wf.readframes(wf.getnframes())
        return np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0

def transcribe(wav_path):
    from faster_whisper import WhisperModel

    audio_data = load_audio_wav(wav_path)
    print("\n[2/3] Transcribing audio with word timestamps...")

    # Attempt CUDA first; fall back to fast CPU (int8) if CUDA runtime DLLs are missing
    use_gpu = True
    try:
        model = WhisperModel("small.en", device="cuda", compute_type="float16")
    except Exception:
        use_gpu = False

    t0 = time.time()
    results = []

    if use_gpu:
        try:
            print("      Running on: NVIDIA CUDA (GPU)")
            segments, info = model.transcribe(audio_data, word_timestamps=True, vad_filter=True)
            for s in segments:
                words = [{"word": w.word.strip(), "start": round(w.start, 2), "end": round(w.end, 2)} for w in (s.words or [])]
                results.append({
                    "start": round(s.start, 2),
                    "end": round(s.end, 2),
                    "text": s.text.strip(),
                    "words": words
                })
            print(f"      GPU transcription finished in {time.time() - t0:.2f}s!")
            return results
        except Exception as e:
            print(f"      [Notice] GPU runtime DLL missing ({e}). Switching to CPU mode...")
            use_gpu = False

    if not use_gpu:
        print("      Running on: CPU (int8 optimized)")
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
        print(f"      CPU transcription finished in {time.time() - t0:.2f}s!")
        return results

def main():
    video_path = find_latest_video()
    if not video_path:
        print("No video files found in Videos folder.")
        return

    temp_wav = os.path.join(os.path.dirname(__file__), "temp_mic.wav")
    try:
        extract_track2(video_path, temp_wav)
        transcript = transcribe(temp_wav)

        print("\n" + "="*60)
        print("   TRANSCRIPTION RESULTS (Word-Level Timing)")
        print("="*60)
        if not transcript:
            print("   (No speech detected - silence or mic muted during clip)")
        for t in transcript:
            print(f"   [{t['start']:05.2f}s - {t['end']:05.2f}s]  \"{t['text']}\"")

        # Load matching telemetry
        telem_file = find_matching_telemetry(video_path)
        if telem_file:
            print("\n" + "="*60)
            print("   COMBINED TIMELINE (What You Said vs What Happened In-Game)")
            print("="*60)
            events = []
            with open(telem_file, encoding="utf-8") as f:
                for line in f:
                    if line.strip():
                        try:
                            events.append(json.loads(line))
                        except Exception:
                            pass

            combined = []
            for ev in events:
                t_sec = ev.get("video_time_sec", 0.0)
                etype = ev.get("type")
                if etype == "game_log":
                    desc = f"GAME: {ev.get('message')}"
                elif etype in ("session_start", "session_end"):
                    desc = f"SYSTEM: {etype}"
                elif etype == "vertical_backtrack_saved":
                    desc = f"HIGHLIGHT CLIP SAVED: {ev.get('clip_file')}"
                else:
                    desc = f"EVENT: {etype}"
                combined.append((t_sec, desc))

            for t in transcript:
                combined.append((t["start"], f"VOICE: \"{t['text']}\""))

            combined.sort(key=lambda x: x[0])
            for t_sec, text in combined:
                print(f"   [{t_sec:05.2f}s] {text}")
            print("="*60)

    finally:
        if os.path.exists(temp_wav):
            try:
                os.remove(temp_wav)
            except Exception:
                pass

if __name__ == "__main__":
    main()
