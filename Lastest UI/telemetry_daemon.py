"""
telemetry_daemon.py - Background Telemetry Recorder for Live Stream Video Editing.

Monitors:
  1. OBS Studio WebSocket (listens for RecordStateChanged, ReplayBufferSaved, Aitum Vertical vendor events)
  2. Shattered Pixel Dungeon WebSocket (:5001) (listens for game_log, hero_died, boss_slain, snapshot diffs)
  3. Video directory (detects Vertical Backtrack clip creations)

Produces:
  A second-by-second .jsonl timeline synchronized with your OBS recording file.
"""

import os
import sys
import json
import time
import glob
import threading
from datetime import datetime

try:
    import websocket
except ImportError:
    websocket = None


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Config defaults
DEFAULT_GAME_WS = "ws://127.0.0.1:5001"
DEFAULT_OBS_WS_PORTS = [4456, 4455]
TELEMETRY_DIR = os.path.join(SCRIPT_DIR, "telemetry_logs")


class TelemetrySession:
    """Manages an active recording session and writes to .jsonl."""

    def __init__(self, video_path: str, start_epoch_ms: int):
        self.video_path = video_path or ""
        self.start_epoch_ms = start_epoch_ms
        self.active = True
        self.lock = threading.Lock()
        self.event_count = 0

        os.makedirs(TELEMETRY_DIR, exist_ok=True)
        timestamp_str = datetime.fromtimestamp(start_epoch_ms / 1000.0).strftime("%Y-%m-%d_%H-%M-%S")
        self.log_path = os.path.join(TELEMETRY_DIR, f"telemetry_{timestamp_str}.jsonl")

        self.file = open(self.log_path, "a", encoding="utf-8")
        self.log_event("session_start", {
            "video_path": self.video_path,
            "start_time_iso": datetime.fromtimestamp(start_epoch_ms / 1000.0).isoformat(),
        })
        print(f"[Telemetry] Session recording started -> {self.log_path}")

    def video_time_sec(self, epoch_ms: int) -> float:
        return max(0.0, round((epoch_ms - self.start_epoch_ms) / 1000.0, 3))

    def log_event(self, event_type: str, data: dict = None, epoch_ms: int = None):
        if not self.active:
            return
        if epoch_ms is None:
            epoch_ms = int(time.time() * 1000)

        entry = {
            "epoch_ms": epoch_ms,
            "video_time_sec": self.video_time_sec(epoch_ms),
            "type": event_type,
        }
        if data:
            entry.update(data)

        with self.lock:
            try:
                self.file.write(json.dumps(entry) + "\n")
                self.file.flush()
                self.event_count += 1
            except Exception as e:
                print(f"[Telemetry] Write error: {e}")

    def close(self, end_epoch_ms: int = None):
        if not self.active:
            return
        if end_epoch_ms is None:
            end_epoch_ms = int(time.time() * 1000)

        duration_sec = self.video_time_sec(end_epoch_ms)
        self.log_event("session_end", {
            "duration_sec": duration_sec,
            "total_events": self.event_count + 1,
        }, epoch_ms=end_epoch_ms)

        with self.lock:
            self.active = False
            try:
                self.file.close()
            except Exception:
                pass

        print(f"[Telemetry] Session closed. Duration: {duration_sec:.1f}s | Events: {self.event_count}")


class TelemetryDaemon:
    """Background service coordinating OBS, Game WS, and file logging."""

    def __init__(self):
        self.current_session: TelemetrySession = None
        self.session_lock = threading.Lock()
        self.last_game_state = {}
        self.seen_backtrack_files = set()
        self.running = True

    def get_obs_ws_url(self) -> str:
        """Read OBS URL from obs_ui_actions.json or check default ports."""
        config_path = os.path.join(SCRIPT_DIR, "obs_ui_actions.json")
        if os.path.exists(config_path):
            try:
                with open(config_path, encoding="utf-8") as f:
                    data = json.load(f)
                    url = data.get("obs_ws_url")
                    if url:
                        return url
            except Exception:
                pass
        return f"ws://127.0.0.1:{DEFAULT_OBS_WS_PORTS[0]}"

    def start_session(self, video_path: str):
        with self.session_lock:
            if self.current_session and self.current_session.active:
                self.current_session.close()
            now_ms = int(time.time() * 1000)
            self.current_session = TelemetrySession(video_path, now_ms)

    def stop_session(self):
        with self.session_lock:
            if self.current_session and self.current_session.active:
                self.current_session.close()
                self.current_session = None

    def log(self, event_type: str, data: dict = None):
        with self.session_lock:
            if self.current_session and self.current_session.active:
                self.current_session.log_event(event_type, data)

    # -------------------------------------------------------------
    # OBS WebSocket Listener (obs-websocket v5 protocol)
    # -------------------------------------------------------------
    def obs_ws_loop(self):
        if not websocket:
            print("[Telemetry] websocket-client not installed; OBS tracking disabled.")
            return

        urls_to_try = [self.get_obs_ws_url()]
        for p in DEFAULT_OBS_WS_PORTS:
            u = f"ws://127.0.0.1:{p}"
            if u not in urls_to_try:
                urls_to_try.append(u)

        url_idx = 0
        while self.running:
            url = urls_to_try[url_idx % len(urls_to_try)]
            try:
                ws = websocket.create_connection(url, timeout=3)
                # op 0 Hello
                msg = json.loads(ws.recv())
                if msg.get("op") != 0:
                    ws.close()
                    time.sleep(5)
                    continue

                # op 1 Identify - subscribe to all standard events + vendor events (2047 | 512)
                ws.send(json.dumps({
                    "op": 1,
                    "d": {
                        "rpcVersion": 1,
                        "eventSubscriptions": 2047
                    }
                }))

                # op 2 Identified
                ident = json.loads(ws.recv())
                if ident.get("op") != 2:
                    ws.close()
                    time.sleep(5)
                    continue

                # Set socket to blocking (no timeout) so we never disconnect on silence
                ws.settimeout(None)
                print(f"[Telemetry] Connected to OBS WebSocket at {url}")

                while self.running:
                    raw = ws.recv()
                    if not raw:
                        break
                    event_msg = json.loads(raw)
                    if event_msg.get("op") == 5:  # Event
                        d = event_msg.get("d", {})
                        event_type = d.get("eventType")
                        event_data = d.get("eventData", {})

                        if event_type == "RecordStateChanged":
                            state = event_data.get("outputState")
                            active = event_data.get("outputActive", False)
                            path = event_data.get("outputPath", "")
                            
                            if state == "OBS_WEBSOCKET_OUTPUT_STARTED" or (active and path):
                                self.start_session(path)
                            elif state == "OBS_WEBSOCKET_OUTPUT_STOPPED" or (not active and state != "OBS_WEBSOCKET_OUTPUT_STARTING"):
                                self.stop_session()

                        elif event_type == "ReplayBufferSaved":
                            replay_path = event_data.get("savedReplayPath", "")
                            self.log("vertical_clip_saved", {"clip_path": replay_path})
                            print(f"[Telemetry] Highlight clip recorded -> {replay_path}")

                        elif event_type == "VendorEvent":
                            vendor = event_data.get("vendorName")
                            if vendor == "aitum-vertical-canvas":
                                sub_event = event_data.get("eventType")
                                self.log("aitum_vertical_event", {"event": sub_event})

            except Exception as e:
                url_idx += 1
                time.sleep(5)

    # -------------------------------------------------------------
    # Game WebSocket Listener (:5001)
    # -------------------------------------------------------------
    def game_ws_loop(self):
        if not websocket:
            return

        while self.running:
            try:
                ws = websocket.create_connection(DEFAULT_GAME_WS, timeout=3)
                ws.settimeout(None)
                print(f"[Telemetry] Connected to Game WebSocket at {DEFAULT_GAME_WS}")
                while self.running:
                    raw = ws.recv()
                    if not raw:
                        break
                    try:
                        data = json.loads(raw)
                    except Exception:
                        continue

                    msg_type = data.get("type")

                    if msg_type == "game_log":
                        # In-game combat, trap, item, or status log message
                        self.log("game_log", {"message": data.get("message")})

                    elif msg_type == "hero_died":
                        self.log("hero_died", {
                            "challenge_count": data.get("challenge_count", -1)
                        })
                        print("[Telemetry] Logged: Hero Died!")

                    elif msg_type == "boss_slain":
                        self.log("boss_slain", {"depth": data.get("depth")})
                        print(f"[Telemetry] Logged: Boss Slain (depth {data.get('depth')})!")

                    elif "hero" in data and "stats" in data:
                        # Full GameStateSnapshot: detect meaningful state changes
                        self._process_snapshot(data)

            except Exception:
                time.sleep(5)

    def _process_snapshot(self, snap: dict):
        hero = snap.get("hero", {})
        stats = snap.get("stats", {})
        equipped = snap.get("equipped", {})

        depth = stats.get("depth", 0)
        hp = hero.get("hp", 0)
        ht = hero.get("ht", 1)
        lvl = hero.get("lvl", 1)

        prev_depth = self.last_game_state.get("depth")
        prev_lvl = self.last_game_state.get("lvl")
        prev_weapon = self.last_game_state.get("weapon")

        # 1. Floor change
        if prev_depth is not None and depth != prev_depth and depth > 0:
            self.log("floor_change", {"from_depth": prev_depth, "to_depth": depth})

        # 2. Level up
        if prev_lvl is not None and lvl > prev_lvl:
            self.log("level_up", {"new_level": lvl})

        # 3. Equipped weapon change or upgrade
        current_weapon = equipped.get("weapon", {})
        curr_weapon_name = current_weapon.get("displayName") if current_weapon else None
        if prev_weapon is not None and curr_weapon_name != prev_weapon:
            self.log("weapon_changed", {"new_weapon": curr_weapon_name})

        # 4. Low HP drama (under 25%)
        if ht > 0 and (hp / float(ht)) <= 0.25 and hp > 0:
            if not self.last_game_state.get("in_danger", False):
                self.log("danger_low_hp", {"hp": hp, "max_hp": ht})
                self.last_game_state["in_danger"] = True
        elif ht > 0 and (hp / float(ht)) > 0.35:
            self.last_game_state["in_danger"] = False

        self.last_game_state["depth"] = depth
        self.last_game_state["lvl"] = lvl
        self.last_game_state["weapon"] = curr_weapon_name

    # -------------------------------------------------------------
    # Backtrack Video Folder Poller (catches Aitum Vertical clips)
    # -------------------------------------------------------------
    def backtrack_folder_loop(self):
        """Scans Videos folder for newly saved 'Backtrack *.mp4' clips."""
        videos_dir = os.path.expanduser("~/Videos")
        pattern = os.path.join(videos_dir, "Backtrack *.mp4")

        # Seed existing files so we don't retroactively flag old clips
        for f in glob.glob(pattern):
            self.seen_backtrack_files.add(f)

        while self.running:
            time.sleep(2)
            try:
                for f in glob.glob(pattern):
                    if f not in self.seen_backtrack_files:
                        self.seen_backtrack_files.add(f)
                        self.log("vertical_backtrack_saved", {
                            "clip_file": os.path.basename(f),
                            "full_path": f
                        })
                        print(f"[Telemetry] Detected new Vertical Backtrack clip: {os.path.basename(f)}")
            except Exception:
                pass


# Global singleton instance
_daemon_instance = None


def start_in_background():
    """Starts the telemetry daemon in background threads."""
    global _daemon_instance
    if _daemon_instance is not None:
        return _daemon_instance

    daemon = TelemetryDaemon()
    _daemon_instance = daemon

    t_obs = threading.Thread(target=daemon.obs_ws_loop, daemon=True, name="Telemetry-OBS")
    t_game = threading.Thread(target=daemon.game_ws_loop, daemon=True, name="Telemetry-Game")
    t_backtrack = threading.Thread(target=daemon.backtrack_folder_loop, daemon=True, name="Telemetry-Backtrack")

    t_obs.start()
    t_game.start()
    t_backtrack.start()

    print("[Telemetry] Telemetry Daemon started in background.")
    return daemon


if __name__ == "__main__":
    print("[Telemetry] Starting daemon directly (standalone mode)...")
    d = start_in_background()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("[Telemetry] Exiting.")
