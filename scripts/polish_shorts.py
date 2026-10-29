"""
polish_shorts.py - Turn raw Aitum Vertical Backtrack clips into polished Shorts / TikToks.

Features:
  1. Extracts audio from vertical clip.
  2. Runs Faster-Whisper with GPU acceleration and SPD vocabulary primer.
  3. Uses word-level timestamps to generate:
     - Standard SRT subtitles (.srt)
     - Animated ASS subtitles (.ass) with viral "pop/highlight" word effect:
       * Styled for 1080x1920 portrait
       * Bold, high-contrast font with thick black outline
       * Spoken word dynamically highlighted in vivid yellow while sentence stays visible
       * Positioned safely above TikTok / Shorts UI overlays
  4. Automatically burns subtitles onto the video using NVIDIA NVENC hardware acceleration
     producing ready-to-upload `<clip_name>_captioned.mp4`.
"""

import os
import sys
import re
import wave
import subprocess
import tempfile
import argparse
import numpy as np
from typing import List, Dict, Any

# Ensure CUDA DLLs are discoverable and preloaded into memory on Windows
def setup_cuda_dlls():
    import site
    import ctypes
    paths = site.getsitepackages() + [site.getusersitepackages()]
    for base in paths:
        cublas_bin = os.path.join(base, "nvidia", "cublas", "bin")
        cudnn_bin = os.path.join(base, "nvidia", "cudnn", "bin")
        for b in [cublas_bin, cudnn_bin]:
            if os.path.exists(b):
                try:
                    os.add_dll_directory(b)
                except Exception:
                    pass
                os.environ["PATH"] = b + os.pathsep + os.environ.get("PATH", "")
                for dll in ["cublasLt64_12.dll", "cublas64_12.dll", "cudnn64_9.dll"]:
                    p = os.path.join(b, dll)
                    if os.path.exists(p):
                        try:
                            ctypes.CDLL(p)
                        except Exception:
                            pass

setup_cuda_dlls()

from faster_whisper import WhisperModel

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
VOCAB_PATH = os.path.join(SCRIPT_DIR, "game_vocabulary.txt")


def load_vocabulary() -> str:
    """Loads custom SPD and streaming vocabulary for whisper prompt primer."""
    if not os.path.exists(VOCAB_PATH):
        return ""
    with open(VOCAB_PATH, "r", encoding="utf-8") as f:
        words = [line.strip() for line in f if line.strip() and not line.startswith("#")]
    return ", ".join(words)


def format_ass_time(sec: float) -> str:
    """Formats float seconds into ASS timestamp H:MM:SS.cs"""
    h = int(sec // 3600)
    m = int((sec % 3600) // 60)
    s = int(sec % 60)
    cs = int(round((sec - int(sec)) * 100))
    if cs >= 100:
        cs = 99
    return f"{h}:{m:02d}:{s:02d}.{cs:02d}"


def format_srt_time(sec: float) -> str:
    """Formats float seconds into SRT timestamp HH:MM:SS,mmm"""
    h = int(sec // 3600)
    m = int((sec % 3600) // 60)
    s = int(sec % 60)
    ms = int(round((sec - int(sec)) * 1000))
    if ms >= 1000:
        ms = 999
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def extract_audio(video_path: str, output_wav: str):
    """Extracts audio to 16kHz mono WAV using ffmpeg."""
    cmd = [
        "ffmpeg", "-y",
        "-i", video_path,
        "-vn",
        "-ac", "1",
        "-ar", "16000",
        "-c:a", "pcm_s16le",
        output_wav
    ]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)


def load_audio_wav(wav_path: str) -> np.ndarray:
    """Loads 16kHz mono WAV file into float32 numpy array bypassing PyAV."""
    with wave.open(wav_path, "rb") as wf:
        frames = wf.readframes(wf.getnframes())
        return np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0


def transcribe_words(wav_path: str) -> List[Dict[str, Any]]:
    """Transcribes audio using Faster-Whisper with word-level timestamps."""
    vocab_primer = load_vocabulary()
    audio_data = load_audio_wav(wav_path)
    all_words = []

    try:
        print("      Running on: NVIDIA CUDA (GPU)")
        model = WhisperModel("small.en", device="cuda", compute_type="float16")
        segments, _ = model.transcribe(
            audio_data,
            word_timestamps=True,
            initial_prompt=vocab_primer,
            beam_size=5,
            vad_filter=True,
            vad_parameters=dict(min_silence_duration_ms=400)
        )
        for s in segments:
            if not s.words:
                continue
            for w in s.words:
                clean_word = w.word.strip()
                if clean_word:
                    all_words.append({
                        "word": clean_word,
                        "start": w.start,
                        "end": max(w.start + 0.1, w.end),
                        "prob": w.probability
                    })
    except Exception as e:
        print(f"      [Whisper GPU fallback] {e} -> Running on CPU...")
        model = WhisperModel("small.en", device="cpu", compute_type="int8")
        segments, _ = model.transcribe(
            audio_data,
            word_timestamps=True,
            initial_prompt=vocab_primer,
            beam_size=5,
            vad_filter=True,
            vad_parameters=dict(min_silence_duration_ms=400)
        )
        for s in segments:
            if not s.words:
                continue
            for w in s.words:
                clean_word = w.word.strip()
                if clean_word:
                    all_words.append({
                        "word": clean_word,
                        "start": w.start,
                        "end": max(w.start + 0.1, w.end),
                        "prob": w.probability
                    })

    return all_words


def chunk_words(words: List[Dict[str, Any]], max_words: int = 4, max_gap_sec: float = 0.5) -> List[List[Dict[str, Any]]]:
    """Groups words into bite-sized short-form chunks (3-4 words max)."""
    chunks = []
    current_chunk = []

    for w in words:
        if not current_chunk:
            current_chunk.append(w)
        else:
            gap = w["start"] - current_chunk[-1]["end"]
            if len(current_chunk) >= max_words or gap > max_gap_sec:
                chunks.append(current_chunk)
                current_chunk = [w]
            else:
                current_chunk.append(w)

    if current_chunk:
        chunks.append(current_chunk)

    return chunks


def generate_srt(chunks: List[List[Dict[str, Any]]], output_path: str):
    """Generates standard clean SRT subtitle file."""
    lines = []
    for idx, chunk in enumerate(chunks, start=1):
        start_time = chunk[0]["start"]
        end_time = chunk[-1]["end"]
        text = " ".join(w["word"] for w in chunk).upper()

        lines.append(str(idx))
        lines.append(f"{format_srt_time(start_time)} --> {format_srt_time(end_time)}")
        lines.append(text)
        lines.append("")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def generate_ass(chunks: List[List[Dict[str, Any]]], output_path: str):
    """
    Generates dynamic ASS subtitle file with active-word yellow pop/highlight.
    Styled for 1080x1920 portrait mobile feeds.
    """
    # ASS Header
    ass_content = [
        "[Script Info]",
        "Title: SPD Short Captions",
        "ScriptType: v4.00+",
        "WrapStyle: 0",
        "ScaledBorderAndShadow: yes",
        "YCbCr Matrix: None",
        "PlayResX: 1080",
        "PlayResY: 1920",
        "",
        "[V4+ Styles]",
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
        # Alignment 2 = bottom center, MarginV 720 = positioned ~38% from screen bottom (safe from TikTok UI)
        # PrimaryColour: &H00FFFFFF (White), Outline: 5px Black, Shadow: 2px
        "Style: Default,Impact,70,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,1,0,1,5,2,2,40,40,720,1",
        "",
        "[Events]",
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
    ]

    # Vivid Yellow in ASS BGR format: &H0000FFFF or Gold &H0000D7FF (B=00, G=D7, R=FF -> &H0000D7FF&)
    HIGHLIGHT_COLOR = r"{\c&H0024E5FF&}"  # Warm golden-yellow (#FFE524)
    DEFAULT_COLOR = r"{\c&H00FFFFFF&}"

    for chunk in chunks:
        for active_idx, target_word in enumerate(chunk):
            word_start = target_word["start"]
            word_end = target_word["end"]
            
            # Build phrase with only the active word highlighted
            phrase_parts = []
            for idx, w in enumerate(chunk):
                w_text = w["word"].upper()
                if idx == active_idx:
                    phrase_parts.append(f"{HIGHLIGHT_COLOR}{w_text}{DEFAULT_COLOR}")
                else:
                    phrase_parts.append(w_text)
            
            phrase_str = " ".join(phrase_parts)
            dialogue_line = (
                f"Dialogue: 0,{format_ass_time(word_start)},{format_ass_time(word_end)},"
                f"Default,,0,0,0,,{phrase_str}"
            )
            ass_content.append(dialogue_line)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(ass_content))


def burn_subtitles(video_path: str, ass_path: str, output_path: str):
    """Burns ASS subtitles onto video using NVIDIA NVENC hardware acceleration."""
    # libass requires escaping colons and backslashes in Windows file paths for -vf ass=...
    escaped_ass = ass_path.replace("\\", "/").replace(":", r"\:")
    
    # Try NVENC first for super-fast render
    cmd = [
        "ffmpeg", "-y",
        "-i", video_path,
        "-vf", f"ass='{escaped_ass}'",
        "-c:v", "h264_nvenc",
        "-preset", "p5",
        "-cq", "18",
        "-c:a", "copy",
        output_path
    ]
    
    result = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    if result.returncode != 0:
        # Fallback to libx264 software encoder if NVENC is unavailable
        cmd[cmd.index("h264_nvenc")] = "libx264"
        cmd[cmd.index("-cq")] = "-crf"
        cmd.pop(cmd.index("-preset") + 1)
        cmd.remove("-preset")
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)


def process_short_video(video_path: str, burn: bool = True) -> Dict[str, str]:
    """Processes a single vertical video file."""
    print(f"\n[Short Polish] Processing: {os.path.basename(video_path)}")
    base_dir = os.path.dirname(video_path)
    base_name = os.path.splitext(os.path.basename(video_path))[0]
    
    srt_path = os.path.join(base_dir, f"{base_name}.srt")
    ass_path = os.path.join(base_dir, f"{base_name}.ass")
    captioned_video = os.path.join(base_dir, f"{base_name}_captioned.mp4")

    with tempfile.TemporaryDirectory() as tmp_dir:
        tmp_wav = os.path.join(tmp_dir, "temp_audio.wav")
        print("   -> Extracting audio stream...")
        extract_audio(video_path, tmp_wav)

        print("   -> Transcribing word timestamps with Faster-Whisper...")
        words = transcribe_words(tmp_wav)
        print(f"      Recognized {len(words)} spoken words.")

        if not words:
            print("   -> No speech detected in clip.")
            return {}

        chunks = chunk_words(words)
        print(f"   -> Grouped into {len(chunks)} punchy subtitle chunks.")

        print("   -> Generating SRT and animated ASS subtitle tracks...")
        generate_srt(chunks, srt_path)
        generate_ass(chunks, ass_path)
        print(f"      Created: {os.path.basename(srt_path)}")
        print(f"      Created: {os.path.basename(ass_path)}")

        if burn:
            print("   -> Burning animated subtitles to video via GPU NVENC...")
            burn_subtitles(video_path, ass_path, captioned_video)
            print(f"      Exported: {os.path.basename(captioned_video)}")

    return {
        "srt": srt_path,
        "ass": ass_path,
        "captioned_video": captioned_video if burn else None
    }


def main():
    parser = argparse.ArgumentParser(description="Polish vertical Backtrack clips with animated subtitles.")
    parser.add_argument("target", nargs="?", default="", help="Path to video file or session folder.")
    parser.add_argument("--no-burn", action="store_true", help="Generate .ass and .srt subtitle files only without re-encoding video.")
    args = parser.parse_args()

    burn = not args.no_burn

    # If no target specified, look for latest session folder
    target_path = args.target
    if not target_path:
        sessions_root = os.path.expanduser("~/Videos/Stream_Sessions")
        if os.path.exists(sessions_root):
            subdirs = [os.path.join(sessions_root, d) for d in os.listdir(sessions_root) if os.path.isdir(os.path.join(sessions_root, d))]
            if subdirs:
                subdirs.sort(key=os.path.getmtime, reverse=True)
                target_path = subdirs[0]

    if not target_path or not os.path.exists(target_path):
        print(f"Error: Target path '{target_path}' does not exist.")
        return

    # If file, process directly
    if os.path.isfile(target_path):
        process_short_video(target_path, burn=burn)
        return

    # If directory, find shorts/ or *.mp4
    shorts_dir = os.path.join(target_path, "shorts")
    search_dir = shorts_dir if os.path.exists(shorts_dir) else target_path

    mp4_files = [
        os.path.join(search_dir, f) for f in os.listdir(search_dir)
        if f.lower().endswith(".mp4") and not f.endswith("_captioned.mp4")
    ]

    if not mp4_files:
        print(f"No unprocessed .mp4 clips found in {search_dir}.")
        return

    print(f"Found {len(mp4_files)} clip(s) to polish in {search_dir}.")
    for mp4 in mp4_files:
        process_short_video(mp4, burn=burn)

    print("\nAll shorts successfully processed and polished!")


if __name__ == "__main__":
    main()
