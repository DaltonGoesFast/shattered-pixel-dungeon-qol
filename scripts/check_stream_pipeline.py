"""
check_stream_pipeline.py - Preflight checks before a stream.

PASS/FAIL/WARN for OBS WS, game WS, Gemini key, CUDA, ffmpeg, Videos & telemetry dirs.
Hard failures (missing ffmpeg / Videos) exit non-zero. Soft warnings do not.
"""

import os
import sys
import json
import shutil
import socket

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
UI_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "Lastest UI"))
TELEMETRY_DIR = os.path.join(UI_DIR, "telemetry_logs")
VIDEOS_DIR = os.path.expanduser("~/Videos")
DEFAULT_OBS_PORTS = [4456, 4455]
GAME_HOST, GAME_PORT = "127.0.0.1", 5001

hard_fail = 0
warns = 0


def ok(msg):
    print(f"  PASS  {msg}")


def warn(msg):
    global warns
    warns += 1
    print(f"  WARN  {msg}")


def fail(msg, hard=True):
    global hard_fail
    if hard:
        hard_fail += 1
        print(f"  FAIL  {msg}")
    else:
        warn(msg)


def tcp_open(host, port, timeout=1.5) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except Exception:
        return False


def obs_ports_to_check():
    ports = list(DEFAULT_OBS_PORTS)
    cfg = os.path.join(UI_DIR, "obs_ui_actions.json")
    if os.path.exists(cfg):
        try:
            with open(cfg, encoding="utf-8") as f:
                data = json.load(f)
            url = data.get("obs_ws_url") or ""
            # ws://127.0.0.1:4456
            if ":" in url:
                tail = url.rsplit(":", 1)[-1].rstrip("/")
                if tail.isdigit():
                    p = int(tail)
                    if p not in ports:
                        ports.insert(0, p)
        except Exception:
            pass
    return ports


def check_gemini_key():
    sys.path.insert(0, SCRIPT_DIR)
    try:
        from generate_cuts_gemini import load_api_key
        key = load_api_key()
    except Exception:
        key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or ""
    if key.strip():
        ok(f"GEMINI_API_KEY present ({len(key.strip())} chars)")
        return
    warn("GEMINI_API_KEY missing — process_stream will fail-soft to heuristic cuts")


def main():
    print("=" * 65)
    print("   STREAM PIPELINE PREFLIGHT")
    print("=" * 65)

    # ffmpeg / ffprobe
    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if ffmpeg:
        ok(f"ffmpeg on PATH ({ffmpeg})")
    else:
        fail("ffmpeg not found on PATH")
    if ffprobe:
        ok(f"ffprobe on PATH ({ffprobe})")
    else:
        fail("ffprobe not found on PATH")

    # Videos dir
    if os.path.isdir(VIDEOS_DIR):
        test_path = os.path.join(VIDEOS_DIR, ".pipeline_write_test")
        try:
            with open(test_path, "w") as f:
                f.write("ok")
            os.remove(test_path)
            ok(f"~/Videos writable ({VIDEOS_DIR})")
        except Exception as e:
            fail(f"~/Videos not writable: {e}")
    else:
        fail(f"~/Videos missing: {VIDEOS_DIR}")

    # Telemetry dir
    try:
        os.makedirs(TELEMETRY_DIR, exist_ok=True)
        test_path = os.path.join(TELEMETRY_DIR, ".pipeline_write_test")
        with open(test_path, "w") as f:
            f.write("ok")
        os.remove(test_path)
        ok(f"telemetry_logs writable ({TELEMETRY_DIR})")
    except Exception as e:
        fail(f"telemetry_logs not writable: {e}")

    # OBS WebSocket
    obs_ok = False
    for port in obs_ports_to_check():
        if tcp_open("127.0.0.1", port):
            ok(f"OBS WebSocket reachable on 127.0.0.1:{port}")
            obs_ok = True
            break
    if not obs_ok:
        warn("OBS WebSocket not reachable on 4456/4455 — start OBS + enable WebSocket before recording")

    # Game WebSocket
    if tcp_open(GAME_HOST, GAME_PORT):
        ok(f"Game WebSocket reachable on {GAME_HOST}:{GAME_PORT}")
    else:
        warn(f"Game WebSocket not up on {GAME_HOST}:{GAME_PORT} — start the jar when streaming (OK if pre-launch)")

    # Gemini
    check_gemini_key()

    # CUDA
    try:
        sys.path.insert(0, SCRIPT_DIR)
        from whisper_cuda import setup_cuda_dlls, cuda_runtime_available
        setup_cuda_dlls()
        if cuda_runtime_available():
            ok("NVIDIA CUDA Whisper runtime DLLs discoverable")
        else:
            warn("CUDA runtime DLLs not found — Whisper may fall back to CPU")
    except Exception as e:
        warn(f"CUDA check error: {e}")

    print("=" * 65)
    if hard_fail:
        print(f"   RESULT: {hard_fail} hard failure(s), {warns} warning(s)")
        print("=" * 65)
        sys.exit(1)
    print(f"   RESULT: ready ({warns} warning(s))")
    print("=" * 65)
    sys.exit(0)


if __name__ == "__main__":
    main()
