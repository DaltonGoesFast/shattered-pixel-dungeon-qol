from flask import Flask, jsonify, request, send_from_directory
from flask_cors import CORS
from spd_parser import SPDSaveParser
import os
import sys
import json
import logging
import queue
import shutil
import uuid
import threading
import time
from datetime import datetime

# Glance console by default; SPD_LOG_VERBOSE=1 or --verbose restores HTTP + game transport detail
LOG_VERBOSE = (
    os.environ.get("SPD_LOG_VERBOSE", "").strip().lower() in ("1", "true", "yes", "on")
    or "--verbose" in sys.argv
)


def _vprint(*args, **kwargs):
    """Print only in verbose mode (game send/result/OK transport chatter)."""
    if LOG_VERBOSE:
        print(*args, **kwargs)

if os.name == "nt":
    import msvcrt
else:
    import fcntl

try:
    import websocket
except ImportError:
    websocket = None

app = Flask(__name__, static_folder='.')
CORS(app)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
GAME_ASSETS_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, '..', 'core', 'src', 'main', 'assets'))

def _default_save_directory():
    """Platform-aware default save directory."""
    home = os.path.expanduser("~")
    if os.name == "nt":
        return os.path.join(home, "AppData", "Roaming", ".shatteredpixel", "Shattered Pixel Dungeon QoL")
    return os.path.join(home, ".shatteredpixel", "Shattered Pixel Dungeon QoL")

def load_config():
    """Load config.json. Returns {} if missing or invalid."""
    path = os.path.join(SCRIPT_DIR, "config.json")
    try:
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                return json.load(f)
    except (json.JSONDecodeError, OSError) as e:
        print(f"Warning: could not load config.json: {e}")
    return {}

_config = load_config()
SAVE_DIRECTORY = _config.get("save_directory", _default_save_directory())

# Configuration
UPDATE_INTERVAL = 1.0  # Check for updates every second
DOUBLE_POINTS_END_FILE = os.path.join(SCRIPT_DIR, "double_points_end.txt")
GAME_SUMMARY_TXT = os.path.join(SCRIPT_DIR, "game_summary.txt")
GAME_SUMMARY_JSON = os.path.join(SCRIPT_DIR, "game_summary.json")
POINTS_CONFIG_FILE = os.path.join(SCRIPT_DIR, "points_config.json")

# Default spawn scaling (matches Java SpawnScaleConfig.applyDefault)
DEFAULT_SPAWN_SCALE = {
    "overall_power": 1.0,
    "early_hp_min": 0.25,
    "late_hp_min": 0.15,
    "scorpio_hp_min": 0.15,
    "early_sewers_dmg_mult": 1.0,
    "late_sewers_dmg_mult": 0.35,
    "early_sewers_dmg_floor": 0.05,
    "late_sewers_dmg_floor": 0.08,
    "prison_plus_dmg_floor": 0.15,
    "early_dr_mult": 0.70,
    "late_dr_mult": 0.40,
    "dr_floor": 0.15,
    "min_one_damage_vs_hero": True,
    "sewers_one_damage_cap": False,
    "eye_gaze_hero_ht_frac": 0.75,
    "paralysis_enabled": True,
    "paralysis_turns_gap_1": 1,
    "paralysis_turns_gap_2": 2,
    "paralysis_turns_gap_3": 3,
    "paralysis_turns_gap_4": 4,
    "xp_sewers": 2,
    "xp_prison": 5,
    "xp_caves": 8,
    "xp_city": 11,
    "xp_halls": 12,
}


def _clamp_float(v, lo, hi, default):
    try:
        return max(lo, min(hi, float(v)))
    except (TypeError, ValueError):
        return default


def _clamp_int(v, lo, hi, default):
    try:
        return max(lo, min(hi, int(v)))
    except (TypeError, ValueError):
        return default


def sanitize_spawn_scale(raw):
    """Merge and clamp spawn_scale knobs; always returns a full dict."""
    src = raw if isinstance(raw, dict) else {}
    d = dict(DEFAULT_SPAWN_SCALE)
    d.update({k: src[k] for k in DEFAULT_SPAWN_SCALE if k in src})
    out = {
        "overall_power": _clamp_float(d["overall_power"], 0.25, 3.0, 1.0),
        "early_hp_min": _clamp_float(d["early_hp_min"], 0.05, 1.0, 0.25),
        "late_hp_min": _clamp_float(d["late_hp_min"], 0.05, 1.0, 0.15),
        "scorpio_hp_min": _clamp_float(d["scorpio_hp_min"], 0.05, 1.0, 0.15),
        "early_sewers_dmg_mult": _clamp_float(d["early_sewers_dmg_mult"], 0.05, 3.0, 1.0),
        "late_sewers_dmg_mult": _clamp_float(d["late_sewers_dmg_mult"], 0.05, 3.0, 0.35),
        "early_sewers_dmg_floor": _clamp_float(d["early_sewers_dmg_floor"], 0.01, 1.0, 0.05),
        "late_sewers_dmg_floor": _clamp_float(d["late_sewers_dmg_floor"], 0.01, 1.0, 0.08),
        "prison_plus_dmg_floor": _clamp_float(d["prison_plus_dmg_floor"], 0.01, 1.0, 0.15),
        "early_dr_mult": _clamp_float(d["early_dr_mult"], 0.05, 2.0, 0.70),
        "late_dr_mult": _clamp_float(d["late_dr_mult"], 0.05, 2.0, 0.40),
        "dr_floor": _clamp_float(d["dr_floor"], 0.01, 1.0, 0.15),
        "min_one_damage_vs_hero": bool(d.get("min_one_damage_vs_hero", True)),
        "sewers_one_damage_cap": bool(d.get("sewers_one_damage_cap", False)),
        "eye_gaze_hero_ht_frac": _clamp_float(d["eye_gaze_hero_ht_frac"], 0.1, 2.0, 0.75),
        "paralysis_enabled": bool(d.get("paralysis_enabled", True)),
        "paralysis_turns_gap_1": _clamp_int(d.get("paralysis_turns_gap_1", d.get("paralysis_turns_prison", 1)), 0, 6, 1),
        "paralysis_turns_gap_2": _clamp_int(d.get("paralysis_turns_gap_2", d.get("paralysis_turns_caves", 2)), 0, 6, 2),
        "paralysis_turns_gap_3": _clamp_int(d.get("paralysis_turns_gap_3", d.get("paralysis_turns_city", 3)), 0, 6, 3),
        "paralysis_turns_gap_4": _clamp_int(d.get("paralysis_turns_gap_4", d.get("paralysis_turns_halls", 4)), 0, 6, 4),
        "xp_sewers": _clamp_int(d["xp_sewers"], 0, 30, 2),
        "xp_prison": _clamp_int(d["xp_prison"], 0, 30, 5),
        "xp_caves": _clamp_int(d["xp_caves"], 0, 30, 8),
        "xp_city": _clamp_int(d["xp_city"], 0, 30, 11),
        "xp_halls": _clamp_int(d["xp_halls"], 0, 30, 12),
    }
    return out


def load_spawn_scale_from_disk():
    try:
        if os.path.exists(POINTS_CONFIG_FILE):
            with open(POINTS_CONFIG_FILE, encoding="utf-8") as f:
                data = json.load(f) or {}
            return sanitize_spawn_scale(data.get("spawn_scale"))
    except Exception:
        pass
    return sanitize_spawn_scale(None)


def push_spawn_scale_to_game(spawn_scale=None):
    """Push spawn_scale knobs to the game over WebSocket. Returns True if sent."""
    if spawn_scale is None:
        spawn_scale = load_spawn_scale_from_disk()
    if not game_ws_app:
        return False
    try:
        payload = {"command": "spawn_scale_config", "spawn_scale": spawn_scale}
        _send_to_game(payload)
        return True
    except Exception as e:
        print(f"push_spawn_scale_to_game: {e}")
        return False

FREE_UNTIL_FILE = os.path.join(SCRIPT_DIR, "free_until.json")
SPEND_DISABLED_FILE = os.path.join(SCRIPT_DIR, "spend_disabled.txt")
VIEWER_POINTS_FILE = os.path.join(SCRIPT_DIR, "viewer_points.txt")
VIEWER_POINTS_LOCK_FILE = VIEWER_POINTS_FILE + ".lock"
VIEWER_POINTS_UNDO_FILE = os.path.join(SCRIPT_DIR, "viewer_points_undo.txt")
VIEWER_POINTS_UNDO_META_FILE = os.path.join(SCRIPT_DIR, "viewer_points_undo_meta.json")
DOUBLE_POINTS_COUNTDOWN_FILE = os.path.join(SCRIPT_DIR, "double_points_countdown.txt")
STREAMER_CHAT_SCORE_FILE = os.path.join(SCRIPT_DIR, "streamer_chat_score.json")
STREAMER_CHAT_SCORE_TXT = os.path.join(SCRIPT_DIR, "streamer_chat_score.txt")
TOP_SUMMONER_FILE = os.path.join(SCRIPT_DIR, "top_summoner.txt")
SUMMON_MARCH_QUEUE_FILE = os.path.join(SCRIPT_DIR, "summon_march_queue.jsonl")
COMPANION_SETTINGS_FILE = os.path.join(SCRIPT_DIR, "companion_settings_remote.json")
COMPANION_SETTINGS_PREV_FILE = os.path.join(SCRIPT_DIR, "companion_settings_previous.json")
COMPANION_HEARTBEAT_FILE = os.path.join(SCRIPT_DIR, "companion_settings_heartbeat.json")
COMPANION_PRESETS_DIR = os.path.join(SCRIPT_DIR, "companion_settings_presets")
SUMMON_MARCH_QUEUE_MAX = 500

# Game WebSocket: receive live stream from game and serve via HTTP /api/game-data and game_summary.json
GAME_WS_URL = "ws://127.0.0.1:5001"   # Game streaming port (default in game Settings; change if you set a different port)
USE_GAME_WEBSOCKET = True             # If True, connect to game WS for live data (same shape as inspector)
GAME_WS_RECONNECT_INTERVAL = 10       # Seconds between reconnect attempts when game isn't running

# OBS inventory crop: direct SetSourceFilterSettings from game item_info geometry
OBS_INV_LAYOUT_FILE = os.path.join(SCRIPT_DIR, "obs_inv_layout.json")
OBS_INV_LAYOUT_EXAMPLE = os.path.join(SCRIPT_DIR, "obs_inv_layout.example.json")

_DEFAULT_OBS_INV_LAYOUT = {
    "enabled": True,
    "obs_ws_url": "ws://127.0.0.1:4455",
    "source_group": "V - INV HUD GROUP",
    "filter_crop": "Crop/Pad",
    "source_hud": "V - INV HUD",
    "filter_mask": "Image Mask/Blend",
    "crop_top_closed": 683,
    "crop_top_min": 390,
    "margin_px": 12,
    "ui_to_obs_scale": 1.0,
    "crop_top_boost": 0,
    "game_top_at_crop_min": 70,
    "game_top_at_crop_closed": 215,
    "full_expand_top_max": 85,
    "full_expand_height_min": 130,
    "short_box_height_max": 85,
    "short_box_crop_add": 250,
    "no_expand_max_height": 100,
}


def load_obs_inv_layout():
    """Load OBS inv crop settings from obs_inv_layout.json or the example file."""
    cfg = dict(_DEFAULT_OBS_INV_LAYOUT)
    for path in (OBS_INV_LAYOUT_FILE, OBS_INV_LAYOUT_EXAMPLE):
        try:
            if os.path.exists(path):
                with open(path, encoding="utf-8") as f:
                    user = json.load(f)
                if isinstance(user, dict):
                    cfg.update(user)
                break
        except (json.JSONDecodeError, OSError) as e:
            print(f"Warning: could not load {path}: {e}")
    return cfg


obs_inv_config = load_obs_inv_layout()

@app.after_request
def add_headers(response):
    """Add headers for CORS and Private Network Access"""
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS'
    response.headers['Access-Control-Allow-Headers'] = 'Content-Type, Authorization, X-Requested-With'
    response.headers['Access-Control-Allow-Private-Network'] = 'true'
    return response

# Global state
current_game_data = {}
parser = SPDSaveParser(SAVE_DIRECTORY)
data_lock = threading.Lock()

last_ws_update_time = 0.0   # when we last got data from game WS; parser skips overwrite if recent
FREE_CLOCK_STALE_SEC = 3.0


def _free_clock_unavailable_reason():
    """Why chat commands cannot run right now, or None if the dungeon is accepting them."""
    if os.path.exists(SPEND_DISABLED_FILE):
        return "spend off"
    now = time.time()
    if last_ws_update_time <= 0 or (now - last_ws_update_time) > FREE_CLOCK_STALE_SEC:
        return "no live game"
    with data_lock:
        data = current_game_data or {}
    ui = data.get("ui") if isinstance(data.get("ui"), dict) else {}
    scene = str(ui.get("scene") or "unknown").strip().lower() or "unknown"
    if scene != "game":
        return f"scene={scene}"
    hero = data.get("hero")
    if not isinstance(hero, dict):
        return "no hero"
    try:
        hp = int(hero.get("hp", 0) or 0)
    except (TypeError, ValueError):
        hp = 0
    if hp <= 0:
        return "hero dead"
    return None

# OBS inventory crop relay (direct filter control)
obs_layout_queue = queue.Queue()
obs_layout_wakeup = threading.Event()
snapshot_write_queue = queue.Queue(maxsize=1)
last_obs_crop_top = None
last_obs_mask_enabled = None
last_open_best_crop = None
last_open_layout_key = None
last_item_info_ignore_open_until = 0.0
ITEM_INFO_IGNORE_OPEN_AFTER_CLOSE_SEC = 0.5
game_ws_received_count = 0
last_ignored_source_log_time = 0.0
IGNORED_SOURCE_LOG_INTERVAL = 60.0

# Chat spawn: reference to game WebSocket for sending commands (set when connected)
game_ws_app = None
pending_spawns = {}  # request_id -> {"event": Event, "success": bool}
spawn_lock = threading.Lock()
game_ws_send_lock = threading.Lock()


def _send_to_game(payload):
    """Thread-safe send on the single game WebSocket (library send is not thread-safe)."""
    with game_ws_send_lock:
        game_ws_app.send(json.dumps(payload))

# Activity feed: recent command events for overlay (max 100, each: time, username, command, detail, success)
recent_command_events = []
COMMAND_EVENTS_MAX = 100
command_events_lock = threading.Lock()


def _record_command_event(username, command, detail, success, spend_id=None, cost=None):
    """Append a command event for the activity feed (overlay polls /api/activity-commands)."""
    with command_events_lock:
        event = {
            "time": int(time.time() * 1000),
            "username": username or "",
            "command": command,
            "detail": detail or "",
            "success": bool(success),
        }
        if spend_id:
            event["spend_id"] = spend_id
        if cost is not None:
            event["cost"] = cost
        recent_command_events.append(event)
        while len(recent_command_events) > COMMAND_EVENTS_MAX:
            recent_command_events.pop(0)


def _spend_event_kwargs(data):
    """Optional spend_id/cost from points_command payload for Activity refund buttons."""
    if not isinstance(data, dict):
        return {}
    out = {}
    sid = (data.get("spend_id") or "").strip()
    if sid:
        out["spend_id"] = sid
    cost = data.get("cost")
    if cost is not None and str(cost).strip() != "":
        try:
            out["cost"] = int(cost)
        except (TypeError, ValueError):
            pass
    return out


SPAWN_RESULT_TIMEOUT = 9.0  # must match GAME_COMMAND_TIMEOUT in points_command.py (Streamer.bot waits 10s)
SPAWN_WHITELIST = frozenset([
    'rat', 'albino', 'snake', 'gnoll', 'crab', 'slime', 'swarm', 'thief',
    'skeleton', 'bat', 'brute', 'shaman', 'spinner', 'dm100', 'guard',
    'necromancer', 'ghoul', 'elemental', 'warlock', 'monk', 'golem',
    'succubus', 'eye', 'scorpio'
])
SPAWN_COOLDOWN_SEC = 0  # 0 = disabled; handle cooldown in Streamer.bot
last_spawn_time = 0.0

# Summon march queue (Godot companion app polls GET /api/summon-march)
summon_march_events = []
summon_march_lock = threading.Lock()


def _parse_top_summoner_file():
    """Parse top_summoner.txt → (display_name, count) or (None, 0)."""
    try:
        if not os.path.exists(TOP_SUMMONER_FILE):
            return None, 0, ""
        with open(TOP_SUMMONER_FILE, encoding="utf-8") as f:
            line = f.readline().strip()
        if not line:
            return None, 0, ""
        prefix = "Top Summoner: "
        if not line.startswith(prefix):
            return None, 0, line
        rest = line[len(prefix):]
        dash = rest.rfind(" - ")
        if dash < 0:
            return rest.strip() or None, 0, line
        name = rest[:dash].strip()
        count = 0
        try:
            count = int(rest[dash + 3:].strip())
        except ValueError:
            pass
        return name or None, count, line
    except OSError:
        return None, 0, ""


def _write_summon_march_queue_to_disk(events):
    """Replace jsonl with the given event list (caller holds summon_march_lock if needed)."""
    tmp = SUMMON_MARCH_QUEUE_FILE + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            for ev in events:
                f.write(json.dumps(ev, separators=(",", ":")) + "\n")
        os.replace(tmp, SUMMON_MARCH_QUEUE_FILE)
    except OSError as e:
        print(f"Warning: could not write summon march queue: {e}")
        try:
            if os.path.exists(tmp):
                os.remove(tmp)
        except OSError:
            pass


def _load_summon_march_queue_from_disk():
    """Load recent summon march events from jsonl on startup."""
    events = []
    try:
        if not os.path.exists(SUMMON_MARCH_QUEUE_FILE):
            return events
        with open(SUMMON_MARCH_QUEUE_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    ev = json.loads(line)
                    if isinstance(ev, dict) and ev.get("id"):
                        events.append(ev)
                except json.JSONDecodeError:
                    continue
        if len(events) > SUMMON_MARCH_QUEUE_MAX:
            events = events[-SUMMON_MARCH_QUEUE_MAX:]
            _write_summon_march_queue_to_disk(events)
    except OSError as e:
        print(f"Warning: could not load summon march queue: {e}")
    return events


def _append_summon_march_event(event):
    """Append event to in-memory queue, trim, and persist to jsonl."""
    with summon_march_lock:
        summon_march_events.append(event)
        trimmed = False
        while len(summon_march_events) > SUMMON_MARCH_QUEUE_MAX:
            summon_march_events.pop(0)
            trimmed = True
        try:
            if trimmed:
                _write_summon_march_queue_to_disk(summon_march_events)
            else:
                with open(SUMMON_MARCH_QUEUE_FILE, "a", encoding="utf-8") as f:
                    f.write(json.dumps(event, separators=(",", ":")) + "\n")
        except OSError as e:
            print(f"Warning: could not append summon march event: {e}")


def update_game_data():
    """Background thread to continuously update game data (from save files). Skipped when game WS is active."""
    global current_game_data
    
    while True:
        try:
            # If we got game WebSocket data in the last 3s, don't overwrite with parser
            if time.time() - last_ws_update_time < 3.0:
                time.sleep(UPDATE_INTERVAL)
                continue
            latest_save = parser.find_latest_save()
            if latest_save and parser.has_save_updated(latest_save):
                game_info = parser.get_current_game_info()
                if game_info:
                    with data_lock:
                        # Preserve ui from last game WebSocket snapshot so file keeps scene/open_windows
                        if "ui" in current_game_data:
                            game_info["ui"] = current_game_data["ui"]
                        current_game_data = game_info
                        
                        # Export text and JSON summaries
                        try:
                            # Text summary
                            summary_text = parser.generate_summary_text(game_info)
                            with open(GAME_SUMMARY_TXT, "w", encoding='utf-8') as f:
                                f.write(summary_text)
                                f.flush()
                                os.fsync(f.fileno())
                            
                            # JSON summary
                            with open(GAME_SUMMARY_JSON, "w", encoding='utf-8') as f:
                                json.dump(game_info, f, indent=4)
                                f.flush()
                                os.fsync(f.fileno())
                        except Exception as export_error:
                            print(f"Error exporting summaries: {export_error}")
        except Exception as e:
            print(f"Error updating game data: {e}")
        
        time.sleep(UPDATE_INTERVAL)


def _map_game_top_to_crop(item_top, closed, expand_most, top_for_min, top_for_closed, boost):
    """Map game popup top (Y px) to OBS Crop/Pad top between expand_most and closed."""
    if top_for_closed <= top_for_min:
        top_for_closed = top_for_min + 1
    if item_top <= top_for_min:
        crop = expand_most
    elif item_top >= top_for_closed:
        crop = closed
    else:
        t = (item_top - top_for_min) / (top_for_closed - top_for_min)
        crop = int(round(expand_most + t * (closed - expand_most)))
    crop = max(expand_most, min(closed, crop + boost))
    return crop


def _compute_obs_crop(item_info):
    """Return (crop_top, mask_enabled) for OBS Crop/Pad and mask from game item_info bounds.

    crop_top_closed / crop_top_min: OBS Crop/Pad top values (683 closed, 390 largest box).
    game_top_at_crop_min: game item_info.top when crop should be crop_top_min.
    game_top_at_crop_closed: game item_info.top when crop should be crop_top_closed.
    crop_top_boost: added to OBS crop top (positive = less upward expansion).
    """
    scale = float(obs_inv_config.get("ui_to_obs_scale", 1.0))
    closed = int(obs_inv_config.get("crop_top_closed", 683))
    expand_most = int(obs_inv_config.get("crop_top_min", 390))
    boost = int(obs_inv_config.get("crop_top_boost", 0))
    top_for_min = float(obs_inv_config.get("game_top_at_crop_min", 70))
    top_for_closed = float(obs_inv_config.get("game_top_at_crop_closed", 215))
    full_expand_top = float(obs_inv_config.get("full_expand_top_max", 85))
    full_expand_height = float(obs_inv_config.get("full_expand_height_min", 130))
    short_height = float(obs_inv_config.get("short_box_height_max", 85))
    short_crop_add = int(obs_inv_config.get("short_box_crop_add", 250))

    if not item_info or not item_info.get("open"):
        return closed, True

    item_top = float(item_info.get("top", 0)) * scale
    height = float(item_info.get("height", 0)) * scale
    inv_top = float(item_info.get("inv_top", 0)) * scale
    no_expand_max_height = float(obs_inv_config.get("no_expand_max_height", 100))

    # Popups this tall or shorter (e.g. Cursed Metal Shard) need no crop/mask change
    if height > 0 and height <= no_expand_max_height:
        return closed, True

    if obs_inv_config.get("skip_expand_above_inv", False) and inv_top > 0:
        item_bottom = float(item_info.get("bottom", 0)) * scale
        if item_bottom <= inv_top:
            return closed, False

    crop_top = _map_game_top_to_crop(
        item_top, closed, expand_most, top_for_min, top_for_closed, boost
    )
    # Kinetic-staff class: high on screen + tall -> full expansion (390)
    if item_top <= full_expand_top and height >= full_expand_height:
        crop_top = min(crop_top, expand_most)
    # Short popups (e.g. top~213 height~61): stay near closed
    elif height <= short_height:
        crop_top = max(crop_top, expand_most + short_crop_add)
    return crop_top, False


def _queue_obs_layout(update):
    """Keep only the latest pending OBS layout update (drop stale queued items)."""
    try:
        while True:
            obs_layout_queue.get_nowait()
    except queue.Empty:
        pass
    try:
        obs_layout_queue.put_nowait(update)
    except queue.Full:
        pass
    obs_layout_wakeup.set()


def _enqueue_snapshot_write(data):
    """Write game_summary off the WS thread so ui_layout is never blocked by fsync."""
    try:
        while True:
            snapshot_write_queue.get_nowait()
    except queue.Empty:
        pass
    try:
        snapshot_write_queue.put_nowait(data)
    except queue.Full:
        pass


def snapshot_writer_thread():
    """Background writer for game_summary.json / .txt from 1 Hz snapshots."""
    while True:
        data = snapshot_write_queue.get()
        try:
            with open(GAME_SUMMARY_JSON, "w", encoding='utf-8') as f:
                json.dump(data, f, indent=4)
                f.flush()
                os.fsync(f.fileno())
            summary_text = parser.generate_summary_text(data)
            with open(GAME_SUMMARY_TXT, "w", encoding='utf-8') as f:
                f.write(summary_text)
                f.flush()
                os.fsync(f.fileno())
        except Exception as e:
            print(f"Error writing game_summary from WS: {e}")


def _apply_obs_inv_layout(item_info, force=False, *, immediate=False):
    """Queue OBS filter updates when crop or mask state changes."""
    global last_obs_crop_top, last_obs_mask_enabled, last_open_best_crop, last_open_layout_key
    global last_item_info_ignore_open_until
    if not obs_inv_config.get("enabled", True) or not websocket:
        return
    now = time.time()
    is_open = bool(item_info and item_info.get("open"))
    if is_open and now < last_item_info_ignore_open_until:
        return
    if not is_open:
        last_item_info_ignore_open_until = now + ITEM_INFO_IGNORE_OPEN_AFTER_CLOSE_SEC
        last_open_best_crop = None
        last_open_layout_key = None
    crop_top, mask_enabled = _compute_obs_crop(item_info)
    computed = crop_top
    if is_open:
        layout_key = (
            int(round(float(item_info.get("top", 0)))),
            int(round(float(item_info.get("height", 0)))),
        )
        if layout_key != last_open_layout_key:
            last_open_layout_key = layout_key
            last_open_best_crop = None
        if last_open_best_crop is None:
            last_open_best_crop = crop_top
        else:
            last_open_best_crop = min(last_open_best_crop, crop_top)
        crop_top = last_open_best_crop
    if not force and not immediate and crop_top == last_obs_crop_top and mask_enabled == last_obs_mask_enabled:
        return
    state = "open" if is_open else "closed"
    extra = f" computed={computed}" if is_open and computed != crop_top else ""
    print(
        f"OBS inv crop: {state} top={crop_top} mask={mask_enabled}{extra} "
        f"(game top={item_info.get('top') if item_info else None} "
        f"height={item_info.get('height') if item_info else None})"
    )
    last_obs_crop_top = crop_top
    last_obs_mask_enabled = mask_enabled
    _queue_obs_layout({"crop_top": crop_top, "mask_enabled": mask_enabled})


def _load_score_data():
    """Load streamer vs chat score. Returns dict with streamer, chat, session_start, streamer_label, chat_label."""
    try:
        if os.path.exists(STREAMER_CHAT_SCORE_FILE):
            with open(STREAMER_CHAT_SCORE_FILE, encoding='utf-8') as f:
                d = json.load(f)
                d.setdefault('streamer', 0)
                d.setdefault('chat', 0)
                d.setdefault('session_start', datetime.now().isoformat())
                d.setdefault('streamer_label', 'Streamer')
                d.setdefault('chat_label', 'Chat')
                return d
    except (json.JSONDecodeError, OSError):
        pass
    return {'streamer': 0, 'chat': 0, 'session_start': datetime.now().isoformat(), 'streamer_label': 'Streamer', 'chat_label': 'Chat'}


def _save_score_data(data):
    """Save score JSON and write TXT for OBS."""
    try:
        with open(STREAMER_CHAT_SCORE_FILE, "w", encoding='utf-8') as f:
            json.dump(data, f, indent=2)
            f.flush()
            os.fsync(f.fileno())
        sl = str(data.get('streamer_label', 'Streamer') or 'Streamer')
        cl = str(data.get('chat_label', 'Chat') or 'Chat')
        txt = f"{sl}: {data.get('streamer', 0)} | {cl}: {data.get('chat', 0)}\n"
        with open(STREAMER_CHAT_SCORE_TXT, "w", encoding='utf-8') as f:
            f.write(txt)
            f.flush()
            os.fsync(f.fileno())
    except OSError as e:
        print(f"Error saving streamer_chat_score: {e}")


def _handle_score_event(data):
    """Handle hero_died or boss_slain from game WebSocket."""
    score_data = _load_score_data()
    if data.get('type') == 'hero_died':
        score_data['chat'] = score_data.get('chat', 0) + 1
        try:
            from points_command import on_hero_death_cost_inflation, get_death_cost_multiplier
            deaths = on_hero_death_cost_inflation()
            mult = get_death_cost_multiplier()
            print(f"Death cost inflation: {deaths} death(s) -> {mult:.2f}x harmful command costs")
        except Exception as e:
            print(f"Death cost inflation update failed: {e}")
    elif data.get('type') == 'boss_slain':
        score_data['streamer'] = score_data.get('streamer', 0) + 1
        try:
            from points_command import on_boss_slain_cost_inflation_reset
            on_boss_slain_cost_inflation_reset()
            print("Death cost inflation reset (boss slain)")
        except Exception as e:
            print(f"Death cost inflation reset failed: {e}")
    _save_score_data(score_data)
    print(f"Score event {data.get('type')}: streamer={score_data['streamer']} chat={score_data['chat']}")


def _game_ws_on_message(ws, message):
    """Handle message from game WebSocket: update live data (same JSON shape as inspector)."""
    global current_game_data, last_ws_update_time, last_obs_crop_top, game_ws_received_count
    try:
        data = json.loads(message)
        # Handle spawn/gold result (game reports success/failure)
        if data.get('type') in ('ping_result', 'spawn_result', 'champion_result', 'gold_result', 'curse_result', 'gas_result', 'scroll_result', 'wand_result', 'buff_result', 'debuff_result', 'trap_result', 'plant_result', 'bomb_result', 'transmute_result', 'summon_bee_result', 'ward_result', 'heal_result', 'cleanse_result', 'dew_result', 'corrupt_ally_result', 'hex_result', 'degrade_result', 'sabotage_result', 'ring_of_wealth_result', 'streamer_debug_result'):
            rid = data.get('request_id')
            ok = data.get('success', False)
            if rid:
                with spawn_lock:
                    if rid in pending_spawns:
                        pending_spawns[rid]['success'] = ok
                        if data.get('type') == 'ping_result' and data.get('version'):
                            pending_spawns[rid]['version'] = data.get('version')
                        if data.get('type') in ('spawn_result', 'gold_result') and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'champion_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'champion_result' and data.get('monster'):
                            pending_spawns[rid]['monster'] = data.get('monster')
                        if data.get('type') == 'curse_result' and data.get('item_name'):
                            pending_spawns[rid]['item_name'] = data.get('item_name')
                        if data.get('type') == 'curse_result' and data.get('temporary'):
                            pending_spawns[rid]['temporary'] = True
                            pending_spawns[rid]['duration_turns'] = data.get('duration_turns')
                        if data.get('type') == 'curse_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'gas_result' and data.get('gas_name'):
                            pending_spawns[rid]['gas_name'] = data.get('gas_name')
                        if data.get('type') == 'gas_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'scroll_result' and data.get('scroll_name'):
                            pending_spawns[rid]['scroll_name'] = data.get('scroll_name')
                        if data.get('type') == 'scroll_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'wand_result' and data.get('effect_name'):
                            pending_spawns[rid]['effect_name'] = data.get('effect_name')
                        if data.get('type') == 'wand_result' and data.get('rarity') is not None:
                            pending_spawns[rid]['rarity'] = data.get('rarity')
                        if data.get('type') == 'wand_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'buff_result' and data.get('buff_name'):
                            pending_spawns[rid]['buff_name'] = data.get('buff_name')
                        if data.get('type') == 'buff_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'debuff_result' and data.get('debuff_name'):
                            pending_spawns[rid]['debuff_name'] = data.get('debuff_name')
                        if data.get('type') == 'debuff_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'trap_result' and data.get('trap_name'):
                            pending_spawns[rid]['trap_name'] = data.get('trap_name')
                        if data.get('type') == 'trap_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'plant_result' and data.get('plant_name'):
                            pending_spawns[rid]['plant_name'] = data.get('plant_name')
                        if data.get('type') == 'plant_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'bomb_result' and data.get('bomb_name'):
                            pending_spawns[rid]['bomb_name'] = data.get('bomb_name')
                        if data.get('type') == 'bomb_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'transmute_result' and data.get('item_name'):
                            pending_spawns[rid]['item_name'] = data.get('item_name')
                        if data.get('type') == 'transmute_result' and data.get('original_item_name'):
                            pending_spawns[rid]['original_item_name'] = data.get('original_item_name')
                        if data.get('type') == 'transmute_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'summon_bee_result' and data.get('ally_name'):
                            pending_spawns[rid]['ally_name'] = data.get('ally_name')
                        if data.get('type') == 'summon_bee_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'ward_result' and data.get('ward_name'):
                            pending_spawns[rid]['ward_name'] = data.get('ward_name')
                        if data.get('type') == 'ward_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'heal_result' and data.get('buff_name'):
                            pending_spawns[rid]['buff_name'] = data.get('buff_name')
                        if data.get('type') == 'heal_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'cleanse_result' and data.get('buff_name'):
                            pending_spawns[rid]['buff_name'] = data.get('buff_name')
                        if data.get('type') == 'cleanse_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'dew_result' and data.get('item_name'):
                            pending_spawns[rid]['item_name'] = data.get('item_name')
                        if data.get('type') == 'dew_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'corrupt_ally_result' and data.get('mob_name'):
                            pending_spawns[rid]['mob_name'] = data.get('mob_name')
                        if data.get('type') == 'corrupt_ally_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'hex_result' and data.get('debuff_name'):
                            pending_spawns[rid]['debuff_name'] = data.get('debuff_name')
                        if data.get('type') == 'hex_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'degrade_result' and data.get('debuff_name'):
                            pending_spawns[rid]['debuff_name'] = data.get('debuff_name')
                        if data.get('type') == 'degrade_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'sabotage_result' and data.get('buff_name'):
                            pending_spawns[rid]['buff_name'] = data.get('buff_name')
                        if data.get('type') == 'sabotage_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'ring_of_wealth_result' and data.get('detail'):
                            pending_spawns[rid]['detail'] = data.get('detail')
                        if data.get('type') == 'ring_of_wealth_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        if data.get('type') == 'streamer_debug_result' and data.get('detail'):
                            pending_spawns[rid]['detail'] = data.get('detail')
                        if data.get('type') == 'streamer_debug_result' and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
                        pending_spawns[rid]['event'].set()
            _vprint(f"Game {data.get('type')}: request_id={rid} success={ok}")
            return
        if data.get('type') in ('hero_died', 'boss_slain') and data.get('source') == 'shattered-pixel-dungeon':
            _handle_score_event(data)
            return
        if data.get('type') == 'ui_layout' and data.get('source') == 'shattered-pixel-dungeon':
            _apply_obs_inv_layout(data.get('item_info'), force=True, immediate=True)
            return
        if data.get('source') != 'shattered-pixel-dungeon':
            global last_ignored_source_log_time
            now = time.time()
            if now - last_ignored_source_log_time >= IGNORED_SOURCE_LOG_INTERVAL:
                last_ignored_source_log_time = now
                keys = list(data.keys()) if isinstance(data, dict) else []
                print(f"Game message ignored (missing or wrong source); keys: {keys}")
            return
        game_ws_received_count += 1
        last_ws_update_time = time.time()
        with data_lock:
            current_game_data = data
        _enqueue_snapshot_write(data)
    except Exception as e:
        print(f"Game WS message error: {e}")


def _double_points_display_minutes(secs):
    """Minutes-only countdown; round up; supports triple digits."""
    if secs <= 0:
        return ""
    return f"{(secs + 59) // 60} min"


def double_points_countdown_thread():
    """Write 2x points countdown to file every second for OBS Text source."""
    while True:
        try:
            display = ""
            if os.path.exists(DOUBLE_POINTS_END_FILE):
                try:
                    with open(DOUBLE_POINTS_END_FILE, "r", encoding="utf-8") as f:
                        raw = f.read().strip()
                    end_ts = int(raw) if raw else 0
                except (ValueError, OSError):
                    end_ts = 0
            else:
                end_ts = 0
            now = int(time.time())
            if end_ts > now:
                secs = end_ts - now
                display = f"2x points: {_double_points_display_minutes(secs)}"
            with open(DOUBLE_POINTS_COUNTDOWN_FILE, "w", encoding="utf-8") as f:
                f.write(display)
                f.flush()
                os.fsync(f.fileno())
        except Exception as e:
            print(f"Double points countdown error: {e}")
        time.sleep(1.0)


def free_until_freeze_thread():
    """Hold Shatter / Stream Deck free windows still while the dungeon cannot accept commands."""
    from points_command import tick_free_until_clock
    last_reason = None
    while True:
        try:
            reason = _free_clock_unavailable_reason()
            paused = reason is not None
            tick_free_until_clock(paused)
            if reason != last_reason:
                if paused:
                    print(f"Free-until clock paused ({reason})")
                else:
                    print("Free-until clock running")
                last_reason = reason
        except Exception as e:
            print(f"Free-until freeze error: {e}")
        time.sleep(0.25)


def _obs_ws_send(ws, request_type, request_data, wait_response=False):
    """Send one OBS WebSocket v5 request. Layout updates skip waiting for faster swaps."""
    req_id = f'spd-{uuid.uuid4()}'
    ws.send(json.dumps({
        'op': 6,
        'd': {
            'requestType': request_type,
            'requestId': req_id,
            'requestData': request_data,
        }
    }))
    if not wait_response:
        return None
    try:
        ws.settimeout(2.0)
        while True:
            raw = ws.recv()
            msg = json.loads(raw)
            if msg.get('op') == 7 and msg.get('d', {}).get('requestId') == req_id:
                return msg.get('d', {})
    except Exception:
        return None
    finally:
        try:
            ws.settimeout(None)
        except Exception:
            pass
    return None


def obs_relay_thread():
    """Connect to OBS WebSocket and apply inventory Crop/Pad + mask from the layout queue."""
    last_obs_error_print = 0.0
    OBS_ERROR_THROTTLE = 60.0  # seconds
    obs_url = obs_inv_config.get('obs_ws_url', 'ws://127.0.0.1:4455')
    while obs_inv_config.get('enabled', True) and websocket:
        try:
            ws = websocket.create_connection(obs_url)
            last_obs_error_print = 0.0
            msg = json.loads(ws.recv())
            if msg.get('op') != 0:
                ws.close()
                time.sleep(5)
                continue
            ws.send(json.dumps({
                'op': 1,
                'd': {'rpcVersion': 1, 'eventSubscriptions': 0}
            }))
            msg = json.loads(ws.recv())
            if msg.get('op') != 2:
                ws.close()
                time.sleep(5)
                continue
            while True:
                obs_layout_wakeup.wait(timeout=1.0)
                obs_layout_wakeup.clear()
                update = None
                try:
                    update = obs_layout_queue.get_nowait()
                    while True:
                        update = obs_layout_queue.get_nowait()
                except queue.Empty:
                    pass
                if update is None:
                    continue
                crop_top = update['crop_top']
                mask_enabled = update['mask_enabled']
                _obs_ws_send(ws, 'SetSourceFilterSettings', {
                    'sourceName': obs_inv_config['source_group'],
                    'filterName': obs_inv_config['filter_crop'],
                    'filterSettings': {'top': crop_top},
                    'overlay': True,
                }, wait_response=False)
                _obs_ws_send(ws, 'SetSourceFilterEnabled', {
                    'sourceName': obs_inv_config['source_hud'],
                    'filterName': obs_inv_config['filter_mask'],
                    'filterEnabled': mask_enabled,
                }, wait_response=False)
                try:
                    ws.settimeout(0)
                    while True:
                        ws.recv()
                except Exception:
                    pass
                finally:
                    try:
                        ws.settimeout(None)
                    except Exception:
                        pass
        except Exception as e:
            now = time.time()
            is_refused = (getattr(e, 'errno', None) == 10061 or 'refused' in str(e).lower())
            if is_refused and (now - last_obs_error_print) < OBS_ERROR_THROTTLE:
                pass
            else:
                if is_refused:
                    last_obs_error_print = now
                    print(
                        f"OBS inv layout: connection refused at {obs_url} "
                        f"(start OBS + WebSocket server, or disable in obs_inv_layout.json). "
                        f"Retrying quietly for {int(OBS_ERROR_THROTTLE)}s."
                    )
                else:
                    print(f"OBS inv layout error: {e}")
        time.sleep(5)


def game_ws_thread():
    """Connect to game WebSocket and keep receiving; reconnect on disconnect."""
    global last_obs_crop_top, last_obs_mask_enabled, game_ws_received_count, game_ws_app
    last_error_print = -999.0  # So first failure prints immediately
    while USE_GAME_WEBSOCKET and websocket:
        try:
            def on_open(conn):
                # Push latest spawn_scale so a restarted game picks up saved knobs
                try:
                    push_spawn_scale_to_game()
                except Exception as e:
                    print(f"Game WS on_open spawn_scale push: {e}")
            def on_close(conn, code, reason):
                global last_obs_crop_top, last_obs_mask_enabled, last_open_best_crop, last_open_layout_key
                global last_item_info_ignore_open_until, game_ws_received_count, game_ws_app
                last_obs_crop_top = None
                last_obs_mask_enabled = None
                last_open_best_crop = None
                last_open_layout_key = None
                last_item_info_ignore_open_until = 0.0
                game_ws_received_count = 0
                game_ws_app = None
            def on_error(ws, err):
                nonlocal last_error_print
                now = time.time()
                if now - last_error_print >= 30:  # Throttle: print at most once per 30s
                    last_error_print = now
                    print(f"Game WebSocket: waiting for game... (retry every {GAME_WS_RECONNECT_INTERVAL}s)")
            ws = websocket.WebSocketApp(
                GAME_WS_URL,
                on_open=on_open,
                on_message=_game_ws_on_message,
                on_error=on_error,
                on_close=on_close
            )
            game_ws_app = ws
            ws.run_forever()
            game_ws_app = None
        except Exception as e:
            print(f"Game WS exception: {e}")
        time.sleep(GAME_WS_RECONNECT_INTERVAL)


@app.route('/')
def index():
    """Serve the main control page (config, viewer points, WebSocket inspector)"""
    return send_from_directory('.', 'points-config.html')


@app.route('/favicon.ico')
def favicon():
    """Avoid 404 in browser tab; no favicon file required."""
    return '', 204


@app.route('/overlay')
def overlay():
    """Serve the OBS overlay page (game summary text)"""
    return send_from_directory('.', 'index.html')


@app.route('/overlay-vertical')
def overlay_vertical():
    """Serve vertical 1080×1920 HUD overlay for OBS (top/bottom bars, transparent middle)"""
    resp = send_from_directory('.', 'overlay-vertical.html')
    resp.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    resp.headers['Pragma'] = 'no-cache'
    resp.headers['Expires'] = '0'
    return resp


@app.route('/double-points-countdown')
def double_points_countdown_page():
    """Serve 2x points countdown for OBS Browser Source (avoids CORS when using file://)"""
    resp = send_from_directory('.', 'double-points-countdown.html')
    resp.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    resp.headers['Pragma'] = 'no-cache'
    resp.headers['Expires'] = '0'
    return resp


@app.route('/points-config')
def points_config_page():
    """Alias for main page"""
    return send_from_directory('.', 'points-config.html')


@app.route('/companion_settings_panel.js')
def companion_settings_panel_js():
    """Companion remote-settings tab UI (loaded by points-config.html)."""
    return send_from_directory(SCRIPT_DIR, 'companion_settings_panel.js', mimetype='application/javascript')


@app.route('/ws-inspect')
def ws_inspect_page():
    """WebSocket JSON inspector (game data + cost config + viewer points)"""
    return send_from_directory('.', 'ws-inspect.html')


@app.route('/api/points-config', methods=['GET', 'POST', 'OPTIONS'])
def points_config_api():
    """Get or save points config (costs for spawn, gold, curse, gas)."""
    if request.method == 'OPTIONS':
        return '', 204
    if request.method == 'GET':
        try:
            if os.path.exists(POINTS_CONFIG_FILE):
                with open(POINTS_CONFIG_FILE, encoding='utf-8') as f:
                    data = json.load(f)
                data.setdefault("cost_per_heal", 100)
                data.setdefault("cost_per_cleanse", 150)
                data.setdefault("cost_per_dew", 30)
                data.setdefault("cost_per_plant", 30)
                data.setdefault("cost_per_hex", 75)
                data.setdefault("cost_per_degrade", 100)
                data.setdefault("cost_per_sabotage", 75)
                data.setdefault("cost_per_corrupt_ally", 100)
                data.setdefault("cost_per_ring_of_wealth", 100)
                data.setdefault("command_allowed_roles", {})
                data.setdefault("cost_per_wand", 75)
                data.setdefault("cost_per_bomb", 75)
                data.setdefault("points_per_message", 2)
                data.setdefault("chat_cooldown_sec", 20)
                data.setdefault("passive_cooldown_sec", 60)
                data.setdefault("first_words_bonus", 5)
                data.setdefault("chat_point_cap", 500)
                data.setdefault("bank_ratio_manual", 0.10)
                data.setdefault("bank_ratio_auto", 0.05)
                data.setdefault("bank_ratio_auto_member", 0.10)
                data.setdefault("donation_multiplier_cap", 4)
                data.setdefault("reset_debounce_hours", 4)
                data.setdefault("curse_class_kit_duration_turns", 100)
                data.setdefault("cooldown_bypass_users", ["DaltonGoesFast"])
                data.setdefault("death_cost_inflation_enabled", True)
                data["spawn_scale"] = sanitize_spawn_scale(data.get("spawn_scale"))
            else:
                data = {
                    "cost_per_gold": 5,
                    "cost_per_curse": 200,
                    "cost_per_gas": 75,
                    "cost_per_scroll": 100,
                    "cost_per_trap": 50,
                    "cost_per_bomb": 75,
                    "cost_per_transmute": 150,
                    "cost_per_ally_bee": 75,
                    "cost_per_ward": 30,
                    "cost_per_buff": 75,
                    "cost_per_debuff": 50,
                    "cost_per_wand": 75,
                    "default_monster_cost": 100,
                    "cost_per_monster": {
                        "rat": 5, "albino": 10, "snake": 10, "gnoll": 10, "crab": 15,
                        "slime": 15, "swarm": 15, "thief": 20, "skeleton": 20, "bat": 30,
                        "brute": 30, "shaman": 35, "spinner": 25, "dm100": 20, "guard": 25,
                        "necromancer": 25, "ghoul": 40, "elemental": 40, "warlock": 45,
                        "monk": 50, "golem": 50, "succubus": 60, "eye": 70, "scorpio": 80,
                    },
                    "cost_per_heal": 100,
                    "cost_per_cleanse": 150,
                    "cost_per_dew": 30,
                    "cost_per_plant": 30,
                    "cost_per_hex": 75,
                    "cost_per_degrade": 100,
                    "cost_per_sabotage": 75,
                    "cost_per_corrupt_ally": 100,
                    "cost_per_ring_of_wealth": 100,
                    "command_allowed_roles": {},
                    "points_per_message": 2,
                    "chat_cooldown_sec": 20,
                    "passive_cooldown_sec": 60,
                    "cooldown_bypass_users": ["DaltonGoesFast"],
                    "first_words_bonus": 5,
                    "chat_point_cap": 500,
                    "bank_ratio_manual": 0.10,
                    "bank_ratio_auto": 0.05,
                    "bank_ratio_auto_member": 0.10,
                    "donation_multiplier_cap": 4,
                    "reset_debounce_hours": 4,
                    "curse_class_kit_duration_turns": 100,
                    "death_cost_inflation_enabled": True,
                    "spawn_scale": sanitize_spawn_scale(None),
                }
            try:
                from points_command import load_free_until
                free_until = load_free_until()
            except Exception:
                free_until = {}
            data["free_until"] = free_until
            data["free_until_paused"] = bool(_free_clock_unavailable_reason())
            if "spawn_scale" not in data:
                data["spawn_scale"] = sanitize_spawn_scale(None)
            return jsonify(data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500
    # POST - save
    try:
        data = request.get_json(force=True, silent=True) or {}
        existing = {}
        if os.path.exists(POINTS_CONFIG_FILE):
            try:
                with open(POINTS_CONFIG_FILE, encoding="utf-8") as f:
                    existing = json.load(f) or {}
            except Exception:
                existing = {}

        if "cooldown_bypass_users" in data:
            raw_bypass = data.get("cooldown_bypass_users")
            if isinstance(raw_bypass, str):
                bypass_users = [u.strip() for u in raw_bypass.split(",") if u.strip()]
            elif isinstance(raw_bypass, list):
                bypass_users = [str(u).strip() for u in raw_bypass if str(u).strip()]
            else:
                bypass_users = []
        else:
            bypass_users = [
                str(u).strip()
                for u in (existing.get("cooldown_bypass_users") or ["DaltonGoesFast"])
                if str(u).strip()
            ]

        if "command_allowed_roles" in data:
            allowed_roles = data.get("command_allowed_roles") or {}
        else:
            allowed_roles = existing.get("command_allowed_roles") or {}

        if "death_cost_inflation_enabled" in data:
            death_cost_enabled = bool(data.get("death_cost_inflation_enabled"))
        else:
            death_cost_enabled = bool(existing.get("death_cost_inflation_enabled", True))

        # Validate and sanitize
        cfg = {
            "cost_per_gold": max(1, int(data.get("cost_per_gold", 5))),
            "cost_per_curse": max(1, int(data.get("cost_per_curse", 200))),
            "cost_per_gas": max(1, int(data.get("cost_per_gas", 75))),
            "cost_per_scroll": max(1, int(data.get("cost_per_scroll", 100))),
            "cost_per_trap": max(1, int(data.get("cost_per_trap", 50))),
            "cost_per_bomb": max(1, int(data.get("cost_per_bomb", 75))),
            "cost_per_transmute": max(1, int(data.get("cost_per_transmute", 150))),
            "cost_per_ally_bee": max(1, int(data.get("cost_per_ally_bee", 75))),
            "cost_per_ward": max(1, int(data.get("cost_per_ward", 30))),
            "cost_per_buff": max(1, int(data.get("cost_per_buff", 75))),
            "cost_per_debuff": max(1, int(data.get("cost_per_debuff", 50))),
            "cost_per_wand": max(1, int(data.get("cost_per_wand", 75))),
            "default_monster_cost": max(1, int(data.get("default_monster_cost", 100))),
            "cost_per_monster": {},
            "cost_per_heal": max(1, int(data.get("cost_per_heal", 100))),
            "cost_per_cleanse": max(1, int(data.get("cost_per_cleanse", 150))),
            "cost_per_dew": max(1, int(data.get("cost_per_dew", 30))),
            "cost_per_plant": max(1, int(data.get("cost_per_plant", 30))),
            "cost_per_hex": max(1, int(data.get("cost_per_hex", 75))),
            "cost_per_degrade": max(1, int(data.get("cost_per_degrade", 100))),
            "cost_per_sabotage": max(1, int(data.get("cost_per_sabotage", 75))),
            "cost_per_corrupt_ally": max(1, int(data.get("cost_per_corrupt_ally", 100))),
            "cost_per_ring_of_wealth": max(1, int(data.get("cost_per_ring_of_wealth", 100))),
            "command_allowed_roles": allowed_roles,
            "points_per_message": max(1, int(data.get("points_per_message", 2))),
            "chat_cooldown_sec": max(0, int(data.get("chat_cooldown_sec", 20))),
            "passive_cooldown_sec": max(0, int(data.get("passive_cooldown_sec", 60))),
            "cooldown_bypass_users": bypass_users,
            "first_words_bonus": max(0, int(data.get("first_words_bonus", 5))),
            "chat_point_cap": max(1, int(data.get("chat_point_cap", 500))),
            "bank_ratio_manual": max(0, min(1, float(data.get("bank_ratio_manual", 0.10)))),
            "bank_ratio_auto": max(0, min(1, float(data.get("bank_ratio_auto", 0.05)))),
            "bank_ratio_auto_member": max(0, min(1, float(data.get("bank_ratio_auto_member", 0.10)))),
            "donation_multiplier_cap": max(1, int(data.get("donation_multiplier_cap", 4))),
            "reset_debounce_hours": max(0, float(data.get("reset_debounce_hours", 4))),
            "curse_class_kit_duration_turns": max(1, int(data.get("curse_class_kit_duration_turns", 100))),
            "death_cost_inflation_enabled": death_cost_enabled,
            "spawn_scale": sanitize_spawn_scale(
                data["spawn_scale"] if "spawn_scale" in data else existing.get("spawn_scale")
            ),
        }
        for k, v in (data.get("cost_per_monster") or {}).items():
            try:
                cfg["cost_per_monster"][str(k)] = max(0, int(v))
            except (ValueError, TypeError):
                pass
        with open(POINTS_CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
        pushed = push_spawn_scale_to_game(cfg["spawn_scale"])
        try:
            from points_command import refresh_death_cost_display_file
            refresh_death_cost_display_file()
        except Exception:
            pass
        return jsonify({"ok": True, "spawn_scale_pushed": pushed})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/death-cost/reset', methods=['POST', 'OPTIONS'])
def death_cost_reset_api():
    """Reset death-based harmful cost inflation to 1.0x (same as boss slain)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from points_command import on_boss_slain_cost_inflation_reset, get_death_cost_deaths, get_death_cost_multiplier
        on_boss_slain_cost_inflation_reset()
        return jsonify({
            "ok": True,
            "deaths": get_death_cost_deaths(),
            "multiplier": get_death_cost_multiplier(),
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/death-cost', methods=['GET', 'OPTIONS'])
def death_cost_status_api():
    """Live death-cost inflation status for the manager header."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from points_command import (
            is_death_cost_inflation_enabled,
            get_death_cost_deaths,
            get_death_cost_multiplier,
            format_death_cost_display,
        )
        enabled = is_death_cost_inflation_enabled()
        deaths = get_death_cost_deaths()
        mult = get_death_cost_multiplier()
        return jsonify({
            "enabled": enabled,
            "deaths": deaths,
            "multiplier": mult,
            "display": format_death_cost_display(),
        })
    except Exception as e:
        return jsonify({"error": str(e), "enabled": True, "deaths": 0, "multiplier": 1.0, "display": ""}), 500


@app.route('/api/spend-disabled', methods=['GET', 'POST', 'OPTIONS'])
def spend_disabled_api():
    """Read/toggle spend kill switch (same spend_disabled.txt as Stream Deck)."""
    if request.method == 'OPTIONS':
        return '', 204
    if request.method == 'GET':
        return jsonify({"disabled": os.path.exists(SPEND_DISABLED_FILE)})
    try:
        body = request.get_json(force=True, silent=True) or {}
        if "disabled" not in body:
            return jsonify({"error": "disabled boolean required"}), 400
        disabled = bool(body.get("disabled"))
        if disabled:
            with open(SPEND_DISABLED_FILE, "w", encoding="utf-8") as f:
                f.write("1\n")
        elif os.path.exists(SPEND_DISABLED_FILE):
            os.remove(SPEND_DISABLED_FILE)
        return jsonify({"ok": True, "disabled": os.path.exists(SPEND_DISABLED_FILE)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/cost-free', methods=['GET', 'POST', 'DELETE', 'OPTIONS'])
def cost_free_api():
    """Set a cost as free for N minutes, or cancel. costKey: e.g. cost_per_gold, cost_per_monster.rat.
    Cancel all: DELETE with no costKey, or POST { cancelAll: true }."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from points_command import load_free_until, save_free_until
        if request.method == 'GET':
            reason = _free_clock_unavailable_reason()
            return jsonify({
                "free_until": load_free_until(),
                "paused": bool(reason),
                "reason": reason or "",
            })
        body = request.get_json(force=True, silent=True) if request.method == 'POST' else {}
        body = body or {}
        cost_key = ''
        if request.method == 'DELETE':
            cost_key = (request.args.get('costKey') or request.args.get('cost_key') or '').strip()
        else:
            cost_key = (body.get('costKey') or body.get('cost_key') or '').strip()

        cancel_all = bool(body.get('cancelAll') or body.get('cancel_all')) or (
            request.method == 'DELETE' and not cost_key
        )
        if cancel_all:
            save_free_until({})
            return jsonify({"ok": True, "cancelledAll": True})

        if not cost_key:
            return jsonify({"error": "costKey required"}), 400
        free_until = load_free_until()
        if request.method == 'DELETE' or body.get('cancel'):
            free_until.pop(cost_key, None)
            save_free_until(free_until)
            return jsonify({"ok": True, "costKey": cost_key, "cancelled": True})
        minutes = max(0, min(1440, int(body.get('minutes', body.get('mins', 5)))))
        end_ts = int(time.time()) + minutes * 60
        free_until[cost_key] = end_ts
        save_free_until(free_until)
        return jsonify({"ok": True, "costKey": cost_key, "freeUntil": end_ts})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


_viewer_points_lock_local = threading.local()


def _acquire_viewer_points_lock():
    """Acquire OS-level lock on viewer_points lock file. Returns True if acquired.

    Byte-range locks (msvcrt/fcntl) are released by the OS if the process dies,
    so a crash can never leave a stale lock. Same lock file as points_command.py.
    The fd is stored per-thread; release with _release_viewer_points_lock()."""
    fd = os.open(VIEWER_POINTS_LOCK_FILE, os.O_CREAT | os.O_RDWR)
    start = time.monotonic()
    while (time.monotonic() - start) < 10:
        try:
            if os.name == "nt":
                os.lseek(fd, 0, os.SEEK_SET)
                msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
            else:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            _viewer_points_lock_local.fd = fd
            return True
        except OSError:
            time.sleep(0.05)
    os.close(fd)
    return False


def _release_viewer_points_lock():
    fd = getattr(_viewer_points_lock_local, "fd", None)
    if fd is None:
        return
    _viewer_points_lock_local.fd = None
    try:
        if os.name == "nt":
            os.lseek(fd, 0, os.SEEK_SET)
            msvcrt.locking(fd, msvcrt.LK_UNLCK, 1)
        else:
            fcntl.flock(fd, fcntl.LOCK_UN)
    except OSError:
        pass
    try:
        os.close(fd)
    except OSError:
        pass


@app.route('/api/viewer-points', methods=['GET', 'POST', 'OPTIONS'])
def viewer_points_api():
    """Get or update viewer points (username -> {points, last}). Uses same lock file as points_command.py."""
    if request.method == 'OPTIONS':
        return '', 204
    if request.method == 'GET':
        if not _acquire_viewer_points_lock():
            return jsonify({"error": "Points file busy"}), 503
        try:
            raw = _read_viewer_points_raw()
            data = {}
            for k, v in raw.items():
                role = v[3] if len(v) >= 4 else ''
                data[k] = {
                    'points': v[0],
                    'last': v[1],
                    'donationPts': v[2],
                    'role': role or '',
                }
            return jsonify(data)
        finally:
            _release_viewer_points_lock()
    # POST - add or update a user's points
    try:
        body = request.get_json(force=True, silent=True) or {}
        username = (body.get('username') or '').strip()
        points = int(body.get('points', 0))
        if 'donationPts' in body or 'donation_pts' in body:
            donation_pts = max(0, int(body.get('donationPts') or body.get('donation_pts') or 0))
        else:
            donation_pts = None
        role_raw = (body.get('role') or '').strip().lower()
        role = role_raw if role_raw in ('helper', 'hurter') else ''
        if not username:
            return jsonify({"error": "username required"}), 400
        if not _acquire_viewer_points_lock():
            return jsonify({"error": "Points file busy, try again"}), 503
        try:
            data = _read_viewer_points_raw()
            existing = data.get(username.lower(), (0, 0, 0, ''))
            pts, last, dp, existing_role = existing[0], existing[1], existing[2], (existing[3] if len(existing) >= 4 else '')
            if donation_pts is None:
                donation_pts = dp
            donation_pts = min(donation_pts, max(0, points))
            new_pts = max(max(0, points), donation_pts)
            new_role = role if 'role' in body else existing_role
            data[username.lower()] = (new_pts, last, donation_pts, new_role)
            _write_viewer_points_raw(data)
            return jsonify({"ok": True, "username": username, "points": new_pts})
        finally:
            _release_viewer_points_lock()
    except ValueError as e:
        return jsonify({"error": "Invalid points: " + str(e)}), 400
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def _read_viewer_points_raw():
    """Read viewer_points file as dict[key] = (pts, last, donation_pts, role). Returns {} if not exists."""
    data = {}
    if not os.path.exists(VIEWER_POINTS_FILE):
        return data
    with open(VIEWER_POINTS_FILE, encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('|')
            if len(parts) >= 3:
                try:
                    donation_pts = int(parts[3]) if len(parts) >= 4 else 0
                    role = (parts[4].strip() or '') if len(parts) >= 5 else ''
                    if role not in ('helper', 'hurter'):
                        role = ''
                    data[parts[0].lower()] = (int(parts[1]), int(parts[2]), donation_pts, role)
                except (ValueError, IndexError):
                    pass
    return data


def _write_viewer_points_raw(data):
    def _row(k, v):
        role = (v[3] or '') if len(v) >= 4 else ''
        return f"{k}|{v[0]}|{v[1]}|{v[2]}|{role}"
    lines = [_row(k, v) for k, v in data.items()]
    with open(VIEWER_POINTS_FILE, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))


def _viewer_points_undo_meta():
    if not os.path.exists(VIEWER_POINTS_UNDO_META_FILE):
        return None
    try:
        with open(VIEWER_POINTS_UNDO_META_FILE, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def _snapshot_viewer_points_for_undo(action: str) -> None:
    """Copy current viewer_points.txt into the one-level undo slot (call while locked)."""
    if os.path.exists(VIEWER_POINTS_FILE):
        shutil.copy2(VIEWER_POINTS_FILE, VIEWER_POINTS_UNDO_FILE)
    else:
        with open(VIEWER_POINTS_UNDO_FILE, "w", encoding="utf-8") as f:
            f.write("")
    meta = {
        "action": action or "edit",
        "at": int(time.time()),
        "at_iso": datetime.now().isoformat(timespec="seconds"),
    }
    with open(VIEWER_POINTS_UNDO_META_FILE, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)


def _restore_viewer_points_undo() -> dict:
    """Swap current file with undo snapshot (second undo = redo). Call while locked."""
    if not os.path.exists(VIEWER_POINTS_UNDO_FILE):
        raise FileNotFoundError("No viewer points undo snapshot available")
    meta = _viewer_points_undo_meta() or {}
    if os.path.exists(VIEWER_POINTS_FILE):
        current_tmp = VIEWER_POINTS_UNDO_FILE + ".swap"
        shutil.copy2(VIEWER_POINTS_FILE, current_tmp)
        shutil.copy2(VIEWER_POINTS_UNDO_FILE, VIEWER_POINTS_FILE)
        shutil.move(current_tmp, VIEWER_POINTS_UNDO_FILE)
    else:
        shutil.copy2(VIEWER_POINTS_UNDO_FILE, VIEWER_POINTS_FILE)
    new_meta = {
        "action": "undo:" + str(meta.get("action") or "edit"),
        "at": int(time.time()),
        "at_iso": datetime.now().isoformat(timespec="seconds"),
        "restored_action": meta.get("action"),
        "restored_at": meta.get("at"),
    }
    with open(VIEWER_POINTS_UNDO_META_FILE, "w", encoding="utf-8") as f:
        json.dump(new_meta, f, indent=2)
    return new_meta


@app.route('/api/viewer-points/undo', methods=['GET', 'POST', 'OPTIONS'])
def viewer_points_undo_api():
    """GET: undo availability. POST: restore last snapshot (swap = redo)."""
    if request.method == 'OPTIONS':
        return '', 204
    if request.method == 'GET':
        available = os.path.exists(VIEWER_POINTS_UNDO_FILE)
        meta = _viewer_points_undo_meta() if available else None
        return jsonify({
            "available": available,
            "action": (meta or {}).get("action"),
            "at": (meta or {}).get("at"),
            "at_iso": (meta or {}).get("at_iso"),
        })
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        meta = _restore_viewer_points_undo()
        return jsonify({"ok": True, **meta})
    except FileNotFoundError as e:
        return jsonify({"error": str(e)}), 404
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/refund-action', methods=['POST', 'OPTIONS'])
def viewer_points_refund_action_api():
    """Refund a confirmed chat spend by spend ledger id. Body: { id }."""
    if request.method == 'OPTIONS':
        return '', 204
    body = request.get_json(force=True, silent=True) or {}
    spend_id = (body.get('id') or body.get('spend_id') or '').strip()
    if not spend_id:
        return jsonify({"ok": False, "error": "Missing spend id"}), 400
    try:
        import points_command
        result = points_command.refund_spend_action(spend_id)
        if not result.get('ok'):
            return jsonify(result), 400
        return jsonify(result)
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route('/api/viewer-points/import', methods=['POST', 'OPTIONS'])
def viewer_points_import():
    """Bulk import viewer points from JSON. Body: { users: [{username, points, donationPts?, last?, role?}, ...], merge: bool }."""
    if request.method == 'OPTIONS':
        return '', 204
    body = request.get_json(force=True, silent=True) or {}
    users = body.get('users')
    if not isinstance(users, list):
        return jsonify({"error": "users array required"}), 400
    merge = body.get('merge', True)
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        _snapshot_viewer_points_for_undo("import-merge" if merge else "import-replace")
        data = {} if not merge else _read_viewer_points_raw()
        count = 0
        for u in users:
            if not isinstance(u, dict):
                continue
            username = (u.get('username') or '').strip()
            if not username:
                continue
            pts = max(0, int(u.get('points', 0)))
            donor = max(0, int(u.get('donationPts') or u.get('donation_pts', 0)))
            donor = min(donor, pts)
            last = int(u.get('last', 0))
            role_raw = (u.get('role') or '').strip().lower()
            role = role_raw if role_raw in ('helper', 'hurter') else ''
            data[username.lower()] = (pts, last, donor, role)
            count += 1
        _write_viewer_points_raw(data)
        return jsonify({"ok": True, "count": count})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/prune', methods=['POST', 'OPTIONS'])
def viewer_points_prune():
    """Remove users inactive N+ days with donor below minDonor; optionally also max chat-only pts (total − donor).
    Body: { days: 2, minDonor: 101, maxChatPts?: number, preview?: bool } — omit maxChatPts to ignore chat (legacy behavior).
    preview=true returns {ok, count, users} without snapshot or write."""
    if request.method == 'OPTIONS':
        return '', 204
    body = request.get_json(force=True, silent=True) or {}
    try:
        days = max(1, int(body.get('days', 2)))
    except (TypeError, ValueError):
        return jsonify({"error": "days must be an integer ≥ 1"}), 400
    try:
        min_donor = max(0, int(body.get('minDonor', body.get('min_donor', 101))))
    except (TypeError, ValueError):
        return jsonify({"error": "minDonor must be an integer ≥ 0"}), 400
    max_chat_raw = body.get('maxChatPts', body.get('max_chat_pts'))
    max_chat_pts = None
    if max_chat_raw is not None and max_chat_raw != '':
        try:
            max_chat_pts = max(0, int(max_chat_raw))
        except (TypeError, ValueError):
            return jsonify({"error": "maxChatPts must be a non-negative integer"}), 400
    cutoff = int(time.time()) - days * 24 * 60 * 60
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        data = _read_viewer_points_raw()
        to_remove = []
        for k, v in data.items():
            pts, last, donation_pts, role = v[0], v[1], v[2], (v[3] if len(v) >= 4 else '')
            if last >= cutoff:
                continue
            if donation_pts >= min_donor:
                continue
            if max_chat_pts is not None:
                chat_only = max(0, int(pts) - int(donation_pts))
                if chat_only > max_chat_pts:
                    continue
            to_remove.append(k)
        to_remove.sort()
        if body.get("preview"):
            return jsonify({"ok": True, "count": len(to_remove), "users": to_remove})
        _snapshot_viewer_points_for_undo("prune")
        for k in to_remove:
            del data[k]
        _write_viewer_points_raw(data)
        return jsonify({"ok": True, "count": len(to_remove)})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/clear-non-donor', methods=['POST', 'OPTIONS'])
def viewer_points_clear_non_donor():
    """Set each user's points = donationPts. Donors keep their amount; non-donors go to 0."""
    if request.method == 'OPTIONS':
        return '', 204
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        _snapshot_viewer_points_for_undo("clear-non-donor")
        data = _read_viewer_points_raw()
        for k in data:
            pts, last, donation_pts, role = data[k][0], data[k][1], data[k][2], (data[k][3] if len(data[k]) >= 4 else '')
            data[k] = (donation_pts, 0, donation_pts, role)
        _write_viewer_points_raw(data)
        return jsonify({"ok": True})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/bulk/set', methods=['POST', 'OPTIONS'])
def viewer_points_bulk_set():
    """Set points and donationPts for specified users (replace, not add). Body: { points, donationPts?, users }."""
    if request.method == 'OPTIONS':
        return '', 204
    body = request.get_json(force=True, silent=True) or {}
    pts = max(0, int(body.get('points', 0)))
    donor = max(0, int(body.get('donationPts') or body.get('donation_pts', 0)))
    donor = min(donor, pts)
    users_filter = body.get('users')
    if not users_filter or not isinstance(users_filter, list):
        return jsonify({"error": "users array required"}), 400
    users_filter = [str(u).strip().lower() for u in users_filter if u and str(u).strip()]
    if not users_filter:
        return jsonify({"error": "at least one user required"}), 400
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        _snapshot_viewer_points_for_undo("bulk-set")
        data = _read_viewer_points_raw()
        for k in users_filter:
            v = data.get(k, (0, 0, 0, ''))
            last, role = v[1], (v[3] if len(v) >= 4 else '')
            data[k] = (pts, last, donor, role)
        _write_viewer_points_raw(data)
        return jsonify({"ok": True, "count": len(users_filter)})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/bulk/add', methods=['POST', 'OPTIONS'])
def viewer_points_bulk_add():
    """Add points and donationPts to every user."""
    if request.method == 'OPTIONS':
        return '', 204
    body = request.get_json(force=True, silent=True) or {}
    chat_add = max(0, int(body.get('points') or body.get('chat') or 0))
    donor_add = max(0, int(body.get('donationPts') or body.get('donor') or 0))
    if chat_add == 0 and donor_add == 0:
        return jsonify({"ok": True, "count": 0})
    users_filter = body.get('users')
    if users_filter:
        users_filter = [str(u).strip().lower() for u in users_filter if u and str(u).strip()]
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        _snapshot_viewer_points_for_undo("bulk-add")
        data = _read_viewer_points_raw()
        keys = users_filter if users_filter else list(data.keys())
        for k in keys:
            v = data.get(k, (0, 0, 0, ''))
            pts, last, donation_pts = v[0], v[1], v[2]
            role = v[3] if len(v) >= 4 else ''
            data[k] = (pts + chat_add, last, donation_pts + donor_add, role)
        _write_viewer_points_raw(data)
        return jsonify({"ok": True, "count": len(keys)})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/bulk/chat-to-donor', methods=['POST', 'OPTIONS'])
def viewer_points_chat_to_donor():
    """Move a percentage of chat-only points into donation_pts (default 100%)."""
    if request.method == 'OPTIONS':
        return '', 204
    body = request.get_json(force=True, silent=True) or {}
    users_filter = body.get('users')
    if users_filter:
        users_filter = [str(u).strip().lower() for u in users_filter if u and str(u).strip()]
    if not users_filter:
        return jsonify({"error": "users required"}), 400
    pct_raw = body.get('percent', 100)
    try:
        percent = int(pct_raw)
    except (TypeError, ValueError):
        return jsonify({"error": "percent must be an integer"}), 400
    if percent < 0 or percent > 100:
        return jsonify({"error": "percent must be 0–100"}), 400
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        _snapshot_viewer_points_for_undo("chat-to-donor")
        data = _read_viewer_points_raw()
        count = 0
        for k in users_filter:
            if k not in data:
                continue
            v = data[k]
            pts, last, donation_pts, role = v[0], v[1], v[2], (v[3] if len(v) >= 4 else '')
            chat_pts = max(0, pts - donation_pts)
            move = (chat_pts * percent) // 100
            if move > 0:
                data[k] = (pts, last, donation_pts + move, role)
                count += 1
        _write_viewer_points_raw(data)
        return jsonify({"ok": True, "count": count})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/bulk/clear-donor', methods=['POST', 'OPTIONS'])
def viewer_points_clear_donor_only():
    """Remove donor points only: set donationPts = 0, keep chat points."""
    if request.method == 'OPTIONS':
        return '', 204
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        _snapshot_viewer_points_for_undo("clear-donor")
        data = _read_viewer_points_raw()
        for k in data:
            v = data[k]
            pts, last, donation_pts, role = v[0], v[1], v[2], (v[3] if len(v) >= 4 else '')
            chat_only = max(0, pts - donation_pts)
            data[k] = (chat_only, last, 0, role)
        _write_viewer_points_raw(data)
        return jsonify({"ok": True})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/clear-all', methods=['POST', 'OPTIONS'])
def viewer_points_clear_all():
    """Full wipe: set each user's points = 0, donationPts = 0."""
    if request.method == 'OPTIONS':
        return '', 204
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        _snapshot_viewer_points_for_undo("clear-all")
        data = _read_viewer_points_raw()
        for k in data:
            data[k] = (0, 0, 0, '')
        _write_viewer_points_raw(data)
        return jsonify({"ok": True})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/balance/<username>', methods=['GET', 'OPTIONS'])
def viewer_points_balance(username):
    """Fast balance lookup for !points (Streamer.bot HTTP). Same lock as other viewer_points access."""
    if request.method == 'OPTIONS':
        return '', 204
    username = (username or '').strip()
    if not username:
        return jsonify({"error": "username required", "points": 0}), 400
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy", "points": 0}), 503
    try:
        data = _read_viewer_points_raw()
        key = username.lower()
        if key not in data:
            return jsonify({"points": 0})
        p, _, d, _ = data[key]
        pts = (p + d) if (d > 0 and p < d) else p
        return jsonify({"points": pts})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/<username>', methods=['DELETE', 'OPTIONS'])
def viewer_points_delete(username):
    """Remove a viewer from the points file."""
    if request.method == 'OPTIONS':
        return '', 204
    username = (username or '').strip()
    if not username:
        return jsonify({"error": "username required"}), 400
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        data = _read_viewer_points_raw()
        if username.lower() in data:
            del data[username.lower()]
            _write_viewer_points_raw(data)
        return jsonify({"ok": True})
    finally:
        _release_viewer_points_lock()


@app.route('/fonts/<path:filename>')
def serve_font(filename):
    """Serve font files for OBS countdown (pixel font from game)"""
    return send_from_directory('fonts', filename)


@app.route('/game-assets/<path:filename>')
def game_assets(filename):
    """Serve game sprite sheets for overlay HUD (items.png, large_buffs.png, etc.)"""
    resp = send_from_directory(GAME_ASSETS_DIR, filename)
    resp.headers['Cache-Control'] = 'public, max-age=3600'
    return resp


@app.route('/game_summary.txt')
def serve_summary():
    """Serve the text summary. Prefer generating from game_summary.json so overlay matches the JSON file."""
    try:
        if os.path.exists(GAME_SUMMARY_JSON):
            with open(GAME_SUMMARY_JSON, 'r', encoding='utf-8') as f:
                data = json.load(f)
            if data:
                content = parser.generate_summary_text(data)
                return content, 200, {
                    'Content-Type': 'text/plain; charset=utf-8',
                    'Cache-Control': 'no-cache, no-store, must-revalidate',
                    'Pragma': 'no-cache',
                    'Expires': '0'
                }
        if os.path.exists(GAME_SUMMARY_TXT):
            with open(GAME_SUMMARY_TXT, 'r', encoding='utf-8') as f:
                content = f.read()
            return content, 200, {
                'Content-Type': 'text/plain; charset=utf-8',
                'Cache-Control': 'no-cache, no-store, must-revalidate',
                'Pragma': 'no-cache',
                'Expires': '0'
            }
        return "No game data available.", 404
    except Exception as e:
        print(f"Error serving game_summary.txt: {e}")
        return f"Internal Server Error: {str(e)}", 500

@app.route('/game_summary.json')
def serve_json_summary():
    """Serve the JSON summary file"""
    try:
        if not os.path.exists(GAME_SUMMARY_JSON):
            return jsonify({"error": "File not found"}), 404
            
        with open(GAME_SUMMARY_JSON, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return jsonify(data)
    except Exception as e:
        print(f"Error serving game_summary.json: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/game-data')
def get_game_data():
    """API endpoint to get current game data. Prefer game_summary.json file so HTML/clients see same data as the file."""
    try:
        if os.path.exists(GAME_SUMMARY_JSON):
            with open(GAME_SUMMARY_JSON, 'r', encoding='utf-8') as f:
                data = json.load(f)
            if data:
                return jsonify(data)
    except Exception as e:
        print(f"Error reading game_summary.json for /api/game-data: {e}")
    with data_lock:
        if current_game_data:
            return jsonify(current_game_data)
    return jsonify({'error': 'No game data available'}), 404

@app.route('/api/game-ping')
def game_ping():
    """Verify connection to game. Returns version if connected to QoL mod; 504 if no response."""
    if not game_ws_app:
        return jsonify({'ok': False, 'error': 'Game not connected'}), 503
    request_id = str(uuid.uuid4())
    ev = threading.Event()
    with spawn_lock:
        pending_spawns[request_id] = {'event': ev, 'success': False}
    try:
        _send_to_game({'command': 'ping', 'request_id': request_id})
    except Exception as e:
        with spawn_lock:
            pending_spawns.pop(request_id, None)
        return jsonify({'ok': False, 'error': str(e)}), 503
    if ev.wait(timeout=5):
        with spawn_lock:
            popped = pending_spawns.pop(request_id, {})
        version = popped.get('version', 'unknown')
        return jsonify({'ok': True, 'version': version})
    with spawn_lock:
        pending_spawns.pop(request_id, None)
    return jsonify({
        'ok': False,
        'error': 'No ping response. Ensure game is the latest build (desktop-3.3.7.jar) with streaming enabled.'
    }), 504

@app.route('/api/streamer-chat-score', methods=['GET', 'POST', 'OPTIONS'])
def streamer_chat_score():
    """Get or update streamer vs chat score. POST body: {streamer?, chat?, streamer_label?, chat_label?}."""
    if request.method == 'OPTIONS':
        return '', 204
    if request.method == 'GET':
        return jsonify(_load_score_data())
    # POST - merge and save
    try:
        data = _load_score_data()
        body = request.get_json(force=True, silent=True) or {}
        if 'streamer' in body:
            data['streamer'] = max(0, int(body['streamer']))
        if 'chat' in body:
            data['chat'] = max(0, int(body['chat']))
        if 'streamer_label' in body:
            data['streamer_label'] = str(body['streamer_label']).strip() or 'Streamer'
        if 'chat_label' in body:
            data['chat_label'] = str(body['chat_label']).strip() or 'Chat'
        _save_score_data(data)
        return jsonify(data)
    except (ValueError, TypeError) as e:
        return jsonify({'error': str(e)}), 400

@app.route('/api/streamer-chat-score/reset', methods=['POST', 'OPTIONS'])
def streamer_chat_score_reset():
    """Reset both scores to 0, update session_start. Labels unchanged."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = _load_score_data()
        data['streamer'] = 0
        data['chat'] = 0
        data['session_start'] = datetime.now().isoformat()
        _save_score_data(data)
        return jsonify(data)
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/spawn-command', methods=['POST', 'OPTIONS'])
def spawn_command():
    """Receive spawn command from Streamer.bot; forward to game via WebSocket."""
    global last_spawn_time
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        monster = (data.get('monster') or '').strip().lower()
        username = (data.get('username') or '').strip() or None
        if not monster:
            err = 'Missing monster'
            print(f"Spawn 400: {err} (received: {data})")
            return jsonify({'ok': False, 'error': err}), 400
        if monster not in SPAWN_WHITELIST:
            err = f'Unknown monster: {monster}'
            print(f"Spawn 400: {err} (received: {data})")
            return jsonify({'ok': False, 'error': err}), 400
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        elapsed = time.time() - last_spawn_time
        if elapsed < SPAWN_COOLDOWN_SEC:
            return jsonify({
                'ok': False, 'error': 'Cooldown active',
                'retry_after': int(SPAWN_COOLDOWN_SEC - elapsed)
            }), 429
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'spawn', 'monster': monster, 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Spawn send to game: {monster} request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                popped = pending_spawns.pop(request_id, {})
                success = popped.get('success', False)
                spawn_error = popped.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            err = ('Spawn timed out. Ensure game is the latest build (desktop-3.3.7.jar), '
                   'streaming is enabled in Settings, and you are in an active run (not title screen).')
            print(f"Spawn 504: {err}")
            return jsonify({'ok': False, 'error': err}), 504
        last_spawn_time = time.time()
        if success:
            _vprint(f"Spawn OK: {monster} for {username}")
            _record_command_event(username, 'spawn', monster, True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'monster': monster})
        err = spawn_error or 'No space to spawn (hero surrounded or no valid tiles)'
        if err.startswith('Timeout or error:'):
            err = ('Game did not process spawn in time. Rebuild the game from this project (e.g. gradlew desktop:run), '
                   'ensure you are in an active run (not title screen), and streaming is enabled in Settings.')
        print(f"Spawn FAIL: {err} ({monster} for {username})")
        _record_command_event(username, 'spawn', monster, False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Spawn 400 exception: {e} (data: {request.get_data(as_text=True)[:200]})")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/champion-command', methods=['POST', 'OPTIONS'])
def champion_command():
    """Receive champion spawn command from Streamer.bot; forward to game via WebSocket. Cost is 2× zone-adjusted spawn cost (same rules as !spawn); overlay handles cost."""
    global last_spawn_time
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        monster = (data.get('monster') or '').strip().lower()
        username = (data.get('username') or '').strip() or None
        if not monster:
            return jsonify({'ok': False, 'error': 'Missing monster'}), 400
        if monster not in SPAWN_WHITELIST:
            return jsonify({'ok': False, 'error': f'Unknown monster: {monster}'}), 400
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        elapsed = time.time() - last_spawn_time
        if elapsed < SPAWN_COOLDOWN_SEC:
            return jsonify({
                'ok': False, 'error': 'Cooldown active',
                'retry_after': int(SPAWN_COOLDOWN_SEC - elapsed)
            }), 429
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'champion', 'monster': monster, 'request_id': request_id}
            if username:
                payload['username'] = username
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                popped = pending_spawns.pop(request_id, {})
                success = popped.get('success', False)
                champion_error = popped.get('error')
                champion_monster = popped.get('monster', monster)
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Champion spawn timed out'}), 504
        last_spawn_time = time.time()
        if success:
            _vprint(f"Champion OK: {champion_monster} for {username}")
            _record_command_event(username, 'champion', champion_monster, True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'monster': champion_monster})
        err = champion_error or 'No space to spawn (hero surrounded or no valid tiles)'
        _record_command_event(username, 'champion', monster, False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/gold-command', methods=['POST', 'OPTIONS'])
def gold_command():
    """Receive gold drop command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        amount = data.get('amount', 5)
        try:
            amount = int(amount) if amount is not None else 5
        except (TypeError, ValueError):
            return jsonify({'ok': False, 'error': 'Amount must be 1-100'}), 200
        if amount < 1 or amount > 100:
            return jsonify({'ok': False, 'error': 'Amount must be 1-100'}), 200
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'gold', 'amount': amount, 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Gold send to game: amount={amount} request_id={request_id} (waiting up to {SPAWN_RESULT_TIMEOUT}s for response)")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                popped = pending_spawns.pop(request_id, {})
                success = popped.get('success', False)
                gold_error = popped.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Gold drop timed out'}), 504
        if success:
            _vprint(f"Gold OK: {amount} for {username}")
            _record_command_event(username, 'gold', str(amount), True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'amount': amount})
        err = gold_error or 'No space to drop gold (hero surrounded)'
        _record_command_event(username, 'gold', str(amount), False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Gold 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/gas-command', methods=['POST', 'OPTIONS'])
def gas_command():
    """Receive gas command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'gas', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Gas send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                gas_name = pending.get('gas_name', '')
                gas_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Gas command timed out'}), 504
        if success:
            _vprint(f"Gas OK: {gas_name} for {username}")
            _record_command_event(username, 'gas', gas_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'gas_name': gas_name})
        err = gas_error or 'No valid cell to spawn gas (need visible tiles 2-6 from hero)'
        _record_command_event(username, 'gas', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Gas 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/curse-command', methods=['POST', 'OPTIONS'])
def curse_command():
    """Receive curse command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        slot = (data.get('slot') or '').strip().lower()
        username = (data.get('username') or '').strip() or None
        class_kit_duration = 100
        try:
            if data.get('class_kit_curse_duration_turns') is not None:
                class_kit_duration = max(1, int(data.get('class_kit_curse_duration_turns')))
            elif os.path.exists(POINTS_CONFIG_FILE):
                with open(POINTS_CONFIG_FILE, encoding='utf-8') as f:
                    cfg = json.load(f)
                class_kit_duration = max(1, int(cfg.get('curse_class_kit_duration_turns', 100)))
        except (TypeError, ValueError, json.JSONDecodeError, OSError):
            class_kit_duration = 100
        valid_slots = {'weapon', 'armor', 'ring', 'artifact', 'misc'}
        if slot and slot not in valid_slots:
            return jsonify({'ok': False, 'error': f'Invalid slot. Options: weapon, armor, ring, artifact, misc (middle slot)'}), 400
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'curse', 'request_id': request_id, 'class_kit_curse_duration_turns': class_kit_duration}
            if slot:
                payload['slot'] = slot
            if username:
                payload['username'] = username
            _vprint(f"Curse send to game: slot={slot or 'random'} request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                item_name = pending.get('item_name', '')
                curse_error = pending.get('error')
                curse_temporary = pending.get('temporary', False)
                curse_duration = pending.get('duration_turns')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Curse timed out'}), 504
        if success:
            _vprint(f"Curse OK: {slot or 'random'} ({item_name}) for {username}")
            _record_command_event(username, 'curse', slot or 'random', True, **_spend_event_kwargs(data))
            resp = {'ok': True, 'slot': slot or None, 'item_name': item_name}
            if curse_temporary:
                resp['temporary'] = True
                resp['duration_turns'] = curse_duration
            return jsonify(resp)
        err = curse_error or 'No curseable equipped item (all slots empty or already cursed)'
        _record_command_event(username, 'curse', slot or 'random', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Curse 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/scroll-command', methods=['POST', 'OPTIONS'])
def scroll_command():
    """Receive scroll command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'scroll', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Scroll send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                scroll_name = pending.get('scroll_name', '')
                scroll_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Scroll command timed out'}), 504
        if success:
            _vprint(f"Scroll OK: {scroll_name} for {username}")
            _record_command_event(username, 'scroll', scroll_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'scroll_name': scroll_name})
        err = scroll_error or 'Could not use random scroll'
        _record_command_event(username, 'scroll', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Scroll 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/ring-of-wealth-command', methods=['POST', 'OPTIONS'])
def ring_of_wealth_command():
    """Receive Ring of Wealth loot command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'ring_of_wealth', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Ring of wealth send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                detail = pending.get('detail', '')
                row_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Ring of wealth command timed out'}), 504
        if success:
            _vprint(f"Ring of wealth OK: {detail!r} for {username}")
            _record_command_event(username, 'ring_of_wealth', detail or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'detail': detail})
        err = row_error or 'Ring of wealth command failed'
        _record_command_event(username, 'ring_of_wealth', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Ring of wealth 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/trap-command', methods=['POST', 'OPTIONS'])
def trap_command():
    """Receive trap command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'trap', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Trap send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                trap_name = pending.get('trap_name', '')
                trap_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Trap command timed out'}), 504
        if success:
            _vprint(f"Trap OK: {trap_name} for {username}")
            _record_command_event(username, 'trap', trap_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'trap_name': trap_name})
        err = trap_error or 'No space to place trap'
        _record_command_event(username, 'trap', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Trap 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/bomb-command', methods=['POST', 'OPTIONS'])
def bomb_command():
    """Receive bomb command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'bomb', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Bomb send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                bomb_name = pending.get('bomb_name', '')
                bomb_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Bomb command timed out'}), 504
        if success:
            _vprint(f"Bomb OK: {bomb_name} for {username}")
            _record_command_event(username, 'bomb', bomb_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'bomb_name': bomb_name})
        err = bomb_error or 'No space to drop bomb'
        _record_command_event(username, 'bomb', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Bomb 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/transmute-command', methods=['POST', 'OPTIONS'])
def transmute_command():
    """Receive transmute command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'transmute', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Transmute send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                item_name = pending.get('item_name', '')
                original_item_name = pending.get('original_item_name', '')
                transmute_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Transmute command timed out'}), 504
        if success:
            _vprint(f"Transmute OK: {original_item_name!r} -> {item_name!r} for {username}")
            _record_command_event(username, 'transmute', item_name or '', True, **_spend_event_kwargs(data))
            return jsonify({
                'ok': True,
                'item_name': item_name,
                'original_item_name': original_item_name,
            })
        err = transmute_error or 'No transmutable item'
        _record_command_event(username, 'transmute', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Transmute 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/ward-command', methods=['POST', 'OPTIONS'])
def ward_command():
    """Receive ward command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'ward', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Ward send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                ward_name = pending.get('ward_name', '')
                ward_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Ward command timed out'}), 504
        if success:
            _vprint(f"Ward OK: {ward_name} for {username}")
            _record_command_event(username, 'ward', ward_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'ward_name': ward_name})
        err = ward_error or 'No space for ward'
        _record_command_event(username, 'ward', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Ward 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/summon-bee-command', methods=['POST', 'OPTIONS'])
def summon_bee_command():
    """Receive summon bee command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'summon_bee', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Summon bee send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                ally_name = pending.get('ally_name', '')
                summon_bee_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Summon bee command timed out'}), 504
        if success:
            _vprint(f"Summon bee OK: {ally_name} for {username}")
            _record_command_event(username, 'summon_bee', ally_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'ally_name': ally_name})
        err = summon_bee_error or 'No space for bee'
        _record_command_event(username, 'summon_bee', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Summon bee 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/buff-command', methods=['POST', 'OPTIONS'])
def buff_command():
    """Receive buff command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'buff', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Buff send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                buff_name = pending.get('buff_name', '')
                buff_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Buff command timed out'}), 504
        if success:
            _vprint(f"Buff OK: {buff_name} for {username}")
            _record_command_event(username, 'buff', buff_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'buff_name': buff_name})
        err = buff_error or 'Could not apply random buff'
        _record_command_event(username, 'buff', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Buff 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/debuff-command', methods=['POST', 'OPTIONS'])
def debuff_command():
    """Receive debuff command from Streamer.bot; forward to game via WebSocket."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'debuff', 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"Debuff send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                debuff_name = pending.get('debuff_name', '')
                debuff_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Debuff command timed out'}), 504
        if success:
            _vprint(f"Debuff OK: {debuff_name} for {username}")
            _record_command_event(username, 'debuff', debuff_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'debuff_name': debuff_name})
        err = debuff_error or 'Could not apply random debuff'
        _record_command_event(username, 'debuff', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Debuff 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


def _forward_chat_command(cmd, result_key, default_err):
    """Forward a chat spend command to game. result_key: buff_name, debuff_name, or item_name."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': cmd, 'request_id': request_id}
            if username:
                payload['username'] = username
            _vprint(f"{cmd} send to game: request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                result_val = pending.get(result_key, '')
                err_val = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': f'{cmd} command timed out'}), 504
        if success:
            _vprint(f"{cmd} OK: {result_val} for {username}")
            _record_command_event(username, cmd, result_val or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, result_key: result_val})
        err = err_val or default_err
        _record_command_event(username, cmd, '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"{cmd} 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


def _forward_streamer_debug(ws_cmd, default_err='Command failed', extra_payload=None):
    """Forward a streamer-only debug command to the game (no points)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': ws_cmd, 'request_id': request_id}
            if extra_payload:
                payload.update(extra_payload)
            _vprint(f"streamer debug send to game: {ws_cmd} request_id={request_id}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                detail = pending.get('detail', '')
                err_val = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': f'{ws_cmd} timed out'}), 504
        if success:
            _vprint(f"streamer debug OK: {detail}")
            return jsonify({'ok': True, 'detail': detail})
        err = err_val or default_err
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"streamer debug exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/streamer-debug/heal-all', methods=['POST', 'OPTIONS'])
def streamer_debug_heal_all():
    """Streamer debug: full heal, all debuffs removed, all curses cleansed."""
    return _forward_streamer_debug('streamer_heal_all', 'Heal-all failed')


@app.route('/api/streamer-debug/identify-all', methods=['POST', 'OPTIONS'])
def streamer_debug_identify_all():
    """Streamer debug: identify all inventory and equipped items."""
    return _forward_streamer_debug('streamer_identify_all', 'Identify-all failed')


@app.route('/api/streamer-debug/reveal-map', methods=['POST', 'OPTIONS'])
def streamer_debug_reveal_map():
    """Streamer debug: magic mapping for current floor."""
    return _forward_streamer_debug('streamer_reveal_map', 'Reveal-map failed')


@app.route('/api/streamer-debug/goto-stairs-down', methods=['POST', 'OPTIONS'])
def streamer_debug_goto_stairs_down():
    """Streamer debug: teleport to floor exit (stairs down)."""
    return _forward_streamer_debug('streamer_goto_stairs_down', 'Goto stairs down failed')


@app.route('/api/streamer-debug/goto-stairs-up', methods=['POST', 'OPTIONS'])
def streamer_debug_goto_stairs_up():
    """Streamer debug: teleport to floor entrance (stairs up)."""
    return _forward_streamer_debug('streamer_goto_stairs_up', 'Goto stairs up failed')


@app.route('/api/streamer-debug/search', methods=['POST', 'OPTIONS'])
def streamer_debug_search():
    """Streamer debug: search item names. Body: {query, limit?}. Works without an active run."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        query = (data.get('query') or '').strip()
        if not query:
            return jsonify({'ok': False, 'error': 'Missing query'}), 400
        limit = max(1, min(25, int(data.get('limit', 12) or 12)))
        return _forward_streamer_debug(
            'streamer_search_items',
            'Search failed',
            extra_payload={'query': query, 'limit': limit},
        )
    except (TypeError, ValueError) as e:
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/streamer-debug/buff', methods=['POST', 'OPTIONS'])
def streamer_debug_buff():
    """Streamer debug: apply named buff. Body: {buff, duration?} (duration in turns, 0 = default)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        buff = (data.get('buff') or '').strip()
        if not buff:
            return jsonify({'ok': False, 'error': 'Missing buff'}), 400
        duration = max(0.0, min(9999.0, float(data.get('duration', 0) or 0)))
        return _forward_streamer_debug(
            'streamer_apply_buff',
            'Apply buff failed',
            extra_payload={'buff': buff, 'duration': duration},
        )
    except (TypeError, ValueError) as e:
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/streamer-debug/debuff', methods=['POST', 'OPTIONS'])
def streamer_debug_debuff():
    """Streamer debug: apply named debuff. Body: {debuff, duration?}."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        debuff = (data.get('debuff') or '').strip()
        if not debuff:
            return jsonify({'ok': False, 'error': 'Missing debuff'}), 400
        duration = max(0.0, min(9999.0, float(data.get('duration', 0) or 0)))
        return _forward_streamer_debug(
            'streamer_apply_debuff',
            'Apply debuff failed',
            extra_payload={'debuff': debuff, 'duration': duration},
        )
    except (TypeError, ValueError) as e:
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/streamer-debug/give', methods=['POST', 'OPTIONS'])
def streamer_debug_give():
    """Streamer debug: give item. Body: {item, quantity?, level?}."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        item = (data.get('item') or '').strip()
        if not item:
            return jsonify({'ok': False, 'error': 'Missing item'}), 400
        quantity = max(1, min(999, int(data.get('quantity', 1) or 1)))
        level = max(0, min(99, int(data.get('level', 0) or 0)))
        return _forward_streamer_debug(
            'streamer_give_item',
            'Give item failed',
            extra_payload={'item': item, 'quantity': quantity, 'level': level},
        )
    except (TypeError, ValueError) as e:
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/heal-command', methods=['POST', 'OPTIONS'])
def heal_command():
    """Chat command: heal hero ~15% HP."""
    return _forward_chat_command('heal', 'buff_name', 'Heal failed')


@app.route('/api/cleanse-command', methods=['POST', 'OPTIONS'])
def cleanse_command():
    """Chat command: remove one random negative buff."""
    return _forward_chat_command('cleanse', 'buff_name', 'No debuff to remove')


@app.route('/api/dew-command', methods=['POST', 'OPTIONS'])
def dew_command():
    """Chat command: drop dewdrop near hero."""
    return _forward_chat_command('dew', 'item_name', 'No space for dewdrop')


@app.route('/api/plant-command', methods=['POST', 'OPTIONS'])
def plant_command():
    """Chat command: plant a random seed near hero (fails if Barren Land enabled)."""
    return _forward_chat_command('plant', 'plant_name', 'No space to plant')


@app.route('/api/corrupt-ally-command', methods=['POST', 'OPTIONS'])
def corrupt_ally_command():
    """Chat command: summon corrupted ally from current biome."""
    return _forward_chat_command('corrupt_ally', 'mob_name', 'Corrupt ally failed')


@app.route('/api/hex-command', methods=['POST', 'OPTIONS'])
def hex_command():
    """Chat command: apply Hex debuff."""
    return _forward_chat_command('hex', 'debuff_name', 'Hex failed')


@app.route('/api/degrade-command', methods=['POST', 'OPTIONS'])
def degrade_command():
    """Chat command: apply Degrade debuff."""
    return _forward_chat_command('degrade', 'debuff_name', 'Degrade failed')


@app.route('/api/sabotage-command', methods=['POST', 'OPTIONS'])
def sabotage_command():
    """Chat command: remove one random positive buff."""
    return _forward_chat_command('sabotage', 'buff_name', 'No buff to remove')


@app.route('/api/wand-command', methods=['POST', 'OPTIONS'])
def wand_command():
    """Receive cursed wand command from Streamer.bot; forward to game via WebSocket. Fixed cost_per_wand; game rolls rarity (no tier in body => weighted common..very_rare)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        data = request.get_json(force=True, silent=True) or {}
        if not data and request.form:
            data = request.form.to_dict()
        username = (data.get('username') or '').strip() or None
        tier = data.get('tier')  # None = random, 0=common, 1=uncommon, 2=rare, 3=very_rare
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'wand', 'request_id': request_id}
            if username:
                payload['username'] = username
            payload['tier'] = int(tier) if tier is not None else -1
            _vprint(f"Wand send to game: request_id={request_id} tier={tier}")
            _send_to_game(payload)
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                effect_name = pending.get('effect_name', '')
                rarity = pending.get('rarity', 0)
                wand_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Wand command timed out'}), 504
        if success:
            _vprint(f"Wand OK: {effect_name} (rarity={rarity}) for {username}")
            _record_command_event(username, 'wand', effect_name or '', True, **_spend_event_kwargs(data))
            return jsonify({'ok': True, 'effect_name': effect_name, 'rarity': rarity})
        err = wand_error or 'Could not trigger cursed wand effect'
        _record_command_event(username, 'wand', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Wand 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


def _donation_json_response(result_msg, kind="donation", username=""):
    """Parse points_command donation result (ok|N, skip|0, invalid|0) into JSON."""
    parts = (result_msg or "").split("|", 1)
    status = parts[0].strip().lower() if parts else "error"
    added = 0
    if len(parts) > 1:
        try:
            added = int(parts[1].strip())
        except ValueError:
            added = 0
    if status == "ok":
        user = (username or "?").strip() or "?"
        print(f"Donate {kind}: {user} +{added} pts")
        return jsonify({"ok": True, "pointsAdded": added, "result": result_msg})
    if status == "skip":
        return jsonify({"ok": False, "skipped": True, "reason": "anonymous or empty username", "result": result_msg})
    if status == "invalid":
        return jsonify({"ok": False, "error": "invalid arguments", "result": result_msg}), 400
    return jsonify({"ok": False, "error": result_msg, "result": result_msg}), 503


@app.route('/api/donation/superchat', methods=['POST', 'OPTIONS'])
def donation_superchat_api():
    """Award Super Chat points (same logic as points_command.py superchat)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from points_command import cmd_superchat
        body = request.get_json(force=True, silent=True) or {}
        username = str(body.get('username') or body.get('userName') or '')
        args = [
            str(body.get('microAmount') or body.get('micro_amount') or ''),
            str(body.get('currencyCode') or body.get('currency') or 'USD'),
            username,
        ]
        for key in ('isSubscribed', 'is_subscribed', 'userIsSponsor', 'user_is_sponsor', 'topFarder', 'top_farder'):
            if key in body:
                args.append(str(body[key]))
        _, msg = cmd_superchat(args)
        return _donation_json_response(msg, kind="superchat", username=username)
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route('/api/donation/cheer', methods=['POST', 'OPTIONS'])
def donation_cheer_api():
    """Award Twitch cheer/bits points."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from points_command import cmd_cheer
        body = request.get_json(force=True, silent=True) or {}
        username = str(body.get('username') or body.get('userName') or '')
        args = [
            str(body.get('bits') or body.get('amount') or 0),
            username,
        ]
        for key in ('isSubscribed', 'is_subscribed', 'userIsSponsor', 'user_is_sponsor', 'topFarder', 'top_farder'):
            if key in body:
                args.append(str(body[key]))
        _, msg = cmd_cheer(args)
        return _donation_json_response(msg, kind="cheer", username=username)
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route('/api/donation/gift-membership', methods=['POST', 'OPTIONS'])
def donation_gift_membership_api():
    """Award points for gifted sub / gift membership."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from points_command import cmd_giftmembership
        body = request.get_json(force=True, silent=True) or {}
        username = str(body.get('username') or body.get('userName') or '')
        args = [username]
        if body.get('tier'):
            args.append(str(body['tier']))
        for key in ('isSubscribed', 'is_subscribed', 'userIsSponsor', 'user_is_sponsor', 'topFarder', 'top_farder'):
            if key in body:
                args.append(str(body[key]))
        _, msg = cmd_giftmembership(args)
        return _donation_json_response(msg, kind="gift-membership", username=username)
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route('/api/channel-points/convert', methods=['POST', 'OPTIONS'])
def channel_points_convert_api():
    """Convert Twitch Channel Points redeem into flat donor points (no donation multipliers)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from points_command import grant_flat_donor_points
        import chat_messages
        body = request.get_json(force=True, silent=True) or {}
        username = str(body.get('username') or body.get('userName') or '').strip()
        if not username or username.lower() == 'anonymous':
            return jsonify({"ok": False, "skipped": True, "message": "Missing username", "error": "username required"}), 400
        try:
            channel_points = int(body.get('channelPoints') or body.get('channel_points') or 0)
        except (TypeError, ValueError):
            channel_points = 0
        try:
            donor_points = int(body.get('donorPoints') or body.get('donor_points') or 0)
        except (TypeError, ValueError):
            donor_points = 0
        if donor_points <= 0 and channel_points > 0:
            donor_points = channel_points // 20
        if donor_points <= 0:
            return jsonify({"ok": False, "message": "donorPoints required", "error": "donorPoints required"}), 400
        if channel_points <= 0:
            channel_points = donor_points * 20
        ok, donor_total, added = grant_flat_donor_points(username, donor_points)
        if not ok:
            return jsonify({
                "ok": False,
                "message": "Could not grant donor points. Try again in a moment.",
                "error": "grant failed",
            }), 503
        message = chat_messages.channel_points_convert(username, channel_points, added, donor_total)
        return jsonify({
            "ok": True,
            "message": message,
            "pts": donor_total,
            "donorPoints": added,
            "channelPoints": channel_points,
            "donorTotal": donor_total,
        })
    except Exception as e:
        return jsonify({"ok": False, "message": str(e), "error": str(e)}), 500


@app.route('/api/double-points-remaining')
def double_points_remaining():
    """Return 2x points countdown for OBS Browser Source."""
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        end_ts = 0
        if os.path.exists(DOUBLE_POINTS_END_FILE):
            try:
                with open(DOUBLE_POINTS_END_FILE, "r", encoding="utf-8") as f:
                    raw = f.read().strip()
                end_ts = int(raw) if raw else 0
            except (ValueError, OSError):
                pass
        now = int(time.time())
        if end_ts <= now:
            return jsonify({"active": False, "seconds_left": 0, "display": ""}), 200, resp_headers
        secs = end_ts - now
        display = _double_points_display_minutes(secs)
        return jsonify({"active": True, "seconds_left": secs, "display": display}), 200, resp_headers
    except Exception as e:
        return jsonify({"active": False, "error": str(e)}), 500, resp_headers


@app.route('/api/double-points-start', methods=['POST', 'OPTIONS'])
def double_points_start():
    """Start 2x points for N minutes. Body: { minutes: 5 } (1–1440)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        body = request.get_json(force=True, silent=True) or {}
        minutes = body.get('minutes', body.get('mins', 5))
        minutes = max(1, min(1440, int(minutes) if minutes is not None else 5))
        end_ts = int(time.time()) + minutes * 60
        with open(DOUBLE_POINTS_END_FILE, 'w', encoding='utf-8') as f:
            f.write(str(end_ts))
            f.flush()
            os.fsync(f.fileno())
        return jsonify({"ok": True, "minutes": minutes, "end_ts": end_ts})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/double-points-stop', methods=['POST', 'OPTIONS'])
def double_points_stop():
    """Clear active 2x points period."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        with open(DOUBLE_POINTS_END_FILE, 'w', encoding='utf-8') as f:
            f.write('0')
            f.flush()
            os.fsync(f.fileno())
        return jsonify({"ok": True, "active": False})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/double-points-extend', methods=['POST', 'OPTIONS'])
def double_points_extend():
    """Extend (or start) 2x by N minutes. Body: { minutes: 5 } (1–1440)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        body = request.get_json(force=True, silent=True) or {}
        minutes = body.get('minutes', body.get('mins', 5))
        minutes = max(1, min(1440, int(minutes) if minutes is not None else 5))
        from chat_command import _extend_double_points
        _extend_double_points(minutes * 60)
        end_ts = 0
        try:
            with open(DOUBLE_POINTS_END_FILE, encoding='utf-8') as f:
                raw = f.read().strip()
            end_ts = int(raw) if raw else 0
        except (ValueError, OSError):
            pass
        return jsonify({"ok": True, "minutes": minutes, "end_ts": end_ts})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/spawn-costs', methods=['GET', 'OPTIONS'])
def spawn_costs_api():
    """Zone-adjusted spawn costs at a depth (query ?depth=N or live game depth)."""
    if request.method == 'OPTIONS':
        return '', 204
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        from points_command import get_config, compute_spawn_cost, get_current_depth, NATIVE_DEPTH
        depth_arg = request.args.get('depth')
        if depth_arg is not None and str(depth_arg).strip() != '':
            depth = int(depth_arg)
        else:
            depth = get_current_depth()
        cfg = get_config()
        monsters = sorted(set(list((cfg.get('cost_per_monster') or {}).keys()) + list(NATIVE_DEPTH.keys())))
        base = {}
        costs = {}
        champions = {}
        for m in monsters:
            b = int((cfg.get('cost_per_monster') or {}).get(m, cfg.get('default_monster_cost', 100)))
            adj = compute_spawn_cost(m, depth)
            base[m] = b
            costs[m] = adj
            champions[m] = 2 * adj
        return jsonify({
            "depth": depth,
            "base": base,
            "costs": costs,
            "champion": champions,
        }), 200, resp_headers
    except Exception as e:
        return jsonify({"error": str(e), "depth": None, "base": {}, "costs": {}}), 500, resp_headers


@app.route('/api/summon-march', methods=['GET', 'POST', 'OPTIONS'])
def summon_march():
    """Queue summon-march events for Godot companion; Streamer.bot POSTs new summons."""
    if request.method == 'OPTIONS':
        return '', 204
    if request.method == 'POST':
        try:
            data = request.get_json(force=True, silent=True) or {}
            if not data and request.form:
                data = request.form.to_dict()
            username = (data.get('username') or data.get('userName') or '').strip()
            monster = (data.get('monster') or '').strip().lower()
            layout = (data.get('layout') or 'horizontal').strip().lower()
            if layout not in ('horizontal', 'vertical'):
                layout = 'horizontal'
            if not monster or monster not in SPAWN_WHITELIST:
                return jsonify({'ok': False, 'error': 'Invalid or missing monster'}), 400
            # Bestiary unlocked-pool gate (chat_command already gates; reject stale direct POSTs)
            try:
                from summon_bestiary import (
                    is_monster_unlocked,
                    monster_xp,
                    get_state_payload,
                    get_march_leader_status,
                )
                if not is_monster_unlocked(monster):
                    return jsonify({
                        'ok': False,
                        'error': f'Monster not unlocked in current Bestiary level: {monster}',
                    }), 400
                xp = int(data.get('xp') or monster_xp(monster) or 0)
                bestiary_level = int(data.get('bestiary_level') or get_state_payload().get('level') or 1)
                leader = get_march_leader_status(username)
            except Exception:
                xp = int(data.get('xp') or 0)
                bestiary_level = int(data.get('bestiary_level') or 1)
                leader = {
                    'heat_leader': False,
                    'sprint_rank': 0,
                    'badge': '',
                    'crowned': bool(data.get('crowned')),
                }
            event_id = str(uuid.uuid4())
            ts = int(time.time())
            badge = str(leader.get('badge') or '').strip().lower()
            if data.get('badge'):
                badge = str(data.get('badge')).strip().lower()
            sprint_rank = int(leader.get('sprint_rank') or 0)
            if data.get('sprint_rank') not in (None, ''):
                try:
                    sprint_rank = int(data.get('sprint_rank'))
                except (TypeError, ValueError):
                    pass
            heat_leader = bool(leader.get('heat_leader')) or bool(data.get('heat_leader'))
            event = {
                'id': event_id,
                'ts': ts,
                'username': username,
                'monster': monster,
                'layout': layout,
                'xp': xp,
                'bestiary_level': bestiary_level,
                'badge': badge,
                'heat_leader': heat_leader,
                'sprint_rank': sprint_rank,
                # Legacy: true for active heat/sprint top-3 badges only (not past winners).
                'crowned': bool(badge) or bool(data.get('crowned')),
            }
            _append_summon_march_event(event)
            _record_command_event(username, 'summon_march', monster, True, **_spend_event_kwargs(data))
            tag = f" [{badge}]" if badge else ""
            print(f"Summon march queued: {monster} for {username or '?'} ({event_id}){tag}")
            return jsonify({'ok': True, 'id': event_id, 'event': event})
        except Exception as e:
            print(f"Summon march POST error: {e}")
            return jsonify({'ok': False, 'error': str(e)}), 500
    # GET — poll events since id or unix timestamp
    try:
        since = request.args.get('since', '').strip()
        with summon_march_lock:
            events = list(summon_march_events)
        if not since:
            out = events
        elif since.isdigit():
            since_ts = int(since)
            out = [e for e in events if e.get('ts', 0) > since_ts]
        else:
            found = False
            out = []
            for e in events:
                if found:
                    out.append(e)
                elif e.get('id') == since:
                    found = True
        return jsonify({'events': out, 'count': len(out)})
    except Exception as e:
        return jsonify({'error': str(e), 'events': []}), 500


@app.route('/api/bestiary')
def bestiary_api():
    """Bestiary HUD payload: bar, sprint, heat, hall of fame, unlocked monsters."""
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        from summon_bestiary import get_state_payload
        return jsonify(get_state_payload()), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e), 'level': 1, 'bar_xp': 0, 'bar_threshold': 60}), 500, resp_headers


@app.route('/api/bestiary-config', methods=['GET', 'POST', 'OPTIONS'])
def bestiary_config_api():
    """Get or save bestiary_config.json (zone XP thresholds, heat, soft floor)."""
    if request.method == 'OPTIONS':
        return '', 204
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        from summon_bestiary import load_config, save_config
        if request.method == 'GET':
            return jsonify(load_config(force=True)), 200, resp_headers
        raw = request.get_json(silent=True)
        if not isinstance(raw, dict):
            return jsonify({'error': 'JSON object required'}), 400, resp_headers
        saved = save_config(raw)
        return jsonify({'ok': True, 'config': saved}), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e)}), 500, resp_headers


def _default_companion_settings_doc():
    return {
        'revision': 0,
        'updated_at': 0,
        'source': '',
        'settings': {},
    }


def _load_companion_settings_doc():
    doc = _default_companion_settings_doc()
    if not os.path.exists(COMPANION_SETTINGS_FILE):
        return doc
    try:
        with open(COMPANION_SETTINGS_FILE, encoding='utf-8') as f:
            raw = json.load(f)
        if isinstance(raw, dict):
            doc['revision'] = int(raw.get('revision', 0) or 0)
            doc['updated_at'] = float(raw.get('updated_at', 0) or 0)
            doc['source'] = str(raw.get('source', '') or '')
            settings = raw.get('settings')
            doc['settings'] = settings if isinstance(settings, dict) else {}
    except (json.JSONDecodeError, OSError, TypeError, ValueError):
        pass
    return doc


def _save_companion_settings_doc(doc):
    out = {
        'revision': int(doc.get('revision', 0) or 0),
        'updated_at': float(doc.get('updated_at', 0) or 0),
        'source': str(doc.get('source', '') or ''),
        'settings': doc.get('settings') if isinstance(doc.get('settings'), dict) else {},
    }
    # Keep previous for one-step undo (before overwrite).
    try:
        if os.path.exists(COMPANION_SETTINGS_FILE):
            with open(COMPANION_SETTINGS_FILE, encoding='utf-8') as f:
                prev_raw = json.load(f)
            if isinstance(prev_raw, dict) and prev_raw.get('settings'):
                with open(COMPANION_SETTINGS_PREV_FILE, 'w', encoding='utf-8') as f:
                    json.dump(prev_raw, f, indent=2)
                    f.write('\n')
    except (json.JSONDecodeError, OSError, TypeError, ValueError):
        pass
    with open(COMPANION_SETTINGS_FILE, 'w', encoding='utf-8') as f:
        json.dump(out, f, indent=2)
        f.write('\n')
    return out


@app.route('/api/companion-settings', methods=['GET', 'POST', 'OPTIONS'])
def companion_settings_api():
    """Remote SPD Companion layout/UI settings (revisioned). Godot polls; HTML edits."""
    if request.method == 'OPTIONS':
        return '', 204
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        if request.method == 'GET':
            return jsonify(_load_companion_settings_doc()), 200, resp_headers
        raw = request.get_json(silent=True)
        if not isinstance(raw, dict):
            return jsonify({'error': 'JSON object required'}), 400, resp_headers
        # Accept either {settings: {...}, source?} or a bare settings object with ui/network keys.
        if isinstance(raw.get('settings'), dict):
            settings = raw['settings']
            source = str(raw.get('source', 'html') or 'html')
        elif any(k in raw for k in ('ui', 'ui_vertical', 'network')):
            settings = {k: raw[k] for k in ('ui', 'ui_vertical', 'network') if k in raw}
            source = str(raw.get('source', 'html') or 'html')
        else:
            return jsonify({'error': 'settings object required'}), 400, resp_headers
        doc = _load_companion_settings_doc()
        doc['revision'] = int(doc.get('revision', 0) or 0) + 1
        doc['updated_at'] = time.time()
        doc['source'] = source
        doc['settings'] = settings
        saved = _save_companion_settings_doc(doc)
        return jsonify({'ok': True, **saved}), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e)}), 500, resp_headers


@app.route('/api/companion-settings/undo', methods=['POST', 'OPTIONS'])
def companion_settings_undo_api():
    """Restore previous companion settings blob (bumps revision)."""
    if request.method == 'OPTIONS':
        return '', 204
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        if not os.path.exists(COMPANION_SETTINGS_PREV_FILE):
            return jsonify({'error': 'No previous revision'}), 404, resp_headers
        with open(COMPANION_SETTINGS_PREV_FILE, encoding='utf-8') as f:
            prev = json.load(f)
        if not isinstance(prev, dict) or not isinstance(prev.get('settings'), dict):
            return jsonify({'error': 'Invalid previous revision'}), 400, resp_headers
        doc = _load_companion_settings_doc()
        doc['revision'] = int(doc.get('revision', 0) or 0) + 1
        doc['updated_at'] = time.time()
        doc['source'] = 'undo'
        doc['settings'] = prev['settings']
        saved = _save_companion_settings_doc(doc)
        return jsonify({'ok': True, **saved}), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e)}), 500, resp_headers


@app.route('/api/companion-settings/heartbeat', methods=['GET', 'POST', 'OPTIONS'])
def companion_settings_heartbeat_api():
    """Godot POSTs applied revision; HTML GETs last-seen status."""
    if request.method == 'OPTIONS':
        return '', 204
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        if request.method == 'GET':
            hb = {'last_seen': 0, 'applied_revision': 0, 'server_revision': 0}
            if os.path.exists(COMPANION_HEARTBEAT_FILE):
                try:
                    with open(COMPANION_HEARTBEAT_FILE, encoding='utf-8') as f:
                        raw = json.load(f)
                    if isinstance(raw, dict):
                        hb.update(raw)
                except (json.JSONDecodeError, OSError):
                    pass
            doc = _load_companion_settings_doc()
            hb['server_revision'] = int(doc.get('revision', 0) or 0)
            return jsonify(hb), 200, resp_headers
        raw = request.get_json(silent=True) or {}
        if not isinstance(raw, dict):
            raw = {}
        doc = _load_companion_settings_doc()
        hb = {
            'last_seen': time.time(),
            'applied_revision': int(raw.get('applied_revision', 0) or 0),
            'server_revision': int(doc.get('revision', 0) or 0),
            'poll_ok': bool(raw.get('poll_ok', True)),
        }
        with open(COMPANION_HEARTBEAT_FILE, 'w', encoding='utf-8') as f:
            json.dump(hb, f, indent=2)
            f.write('\n')
        return jsonify({'ok': True, **hb}), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e)}), 500, resp_headers


def _safe_preset_name(name: str) -> str:
    s = ''.join(c for c in str(name or '').strip() if c.isalnum() or c in ('-', '_', ' ')).strip()
    return s[:64]


@app.route('/api/companion-settings/presets', methods=['GET', 'POST', 'OPTIONS'])
def companion_settings_presets_api():
    if request.method == 'OPTIONS':
        return '', 204
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        os.makedirs(COMPANION_PRESETS_DIR, exist_ok=True)
        if request.method == 'GET':
            names = []
            for fn in sorted(os.listdir(COMPANION_PRESETS_DIR)):
                if fn.endswith('.json'):
                    names.append(fn[:-5])
            return jsonify({'presets': names}), 200, resp_headers
        raw = request.get_json(silent=True)
        if not isinstance(raw, dict):
            return jsonify({'error': 'JSON object required'}), 400, resp_headers
        name = _safe_preset_name(raw.get('name', ''))
        settings = raw.get('settings')
        if not name:
            return jsonify({'error': 'name required'}), 400, resp_headers
        if not isinstance(settings, dict):
            return jsonify({'error': 'settings object required'}), 400, resp_headers
        path = os.path.join(COMPANION_PRESETS_DIR, name + '.json')
        with open(path, 'w', encoding='utf-8') as f:
            json.dump({'name': name, 'settings': settings, 'saved_at': time.time()}, f, indent=2)
            f.write('\n')
        return jsonify({'ok': True, 'name': name}), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e)}), 500, resp_headers


@app.route('/api/companion-settings/presets/<name>', methods=['DELETE', 'OPTIONS'])
def companion_settings_preset_delete_api(name):
    if request.method == 'OPTIONS':
        return '', 204
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        safe = _safe_preset_name(name)
        path = os.path.join(COMPANION_PRESETS_DIR, safe + '.json')
        if not safe or not os.path.exists(path):
            return jsonify({'error': 'Preset not found'}), 404, resp_headers
        os.remove(path)
        return jsonify({'ok': True}), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e)}), 500, resp_headers


@app.route('/api/companion-settings/presets/<name>/apply', methods=['POST', 'OPTIONS'])
def companion_settings_preset_apply_api(name):
    if request.method == 'OPTIONS':
        return '', 204
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        safe = _safe_preset_name(name)
        path = os.path.join(COMPANION_PRESETS_DIR, safe + '.json')
        if not safe or not os.path.exists(path):
            return jsonify({'error': 'Preset not found'}), 404, resp_headers
        with open(path, encoding='utf-8') as f:
            raw = json.load(f)
        settings = raw.get('settings') if isinstance(raw, dict) else None
        if not isinstance(settings, dict):
            return jsonify({'error': 'Invalid preset'}), 400, resp_headers
        doc = _load_companion_settings_doc()
        doc['revision'] = int(doc.get('revision', 0) or 0) + 1
        doc['updated_at'] = time.time()
        doc['source'] = 'preset:' + safe
        doc['settings'] = settings
        saved = _save_companion_settings_doc(doc)
        return jsonify({'ok': True, **saved}), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e)}), 500, resp_headers


@app.route('/api/top-summoner')
def top_summoner_api():
    """Sprint leader for OBS / legacy sources (Bestiary level sprint, not session count)."""
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        from summon_bestiary import get_sprint_leader
        username, count, gap = get_sprint_leader()
        display = f'Top Summoner: {username} - {count}' if username else ''
        return jsonify({
            'username': username or '',
            'count': count,
            'gap': gap,
            'display': display,
            'has_leader': bool(username),
            'kind': 'sprint',
        }), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e), 'username': '', 'count': 0, 'display': '', 'has_leader': False}), 500, resp_headers


@app.route('/api/heat-leader')
def heat_leader_api():
    """Rolling heat leader (personal 2×)."""
    resp_headers = {'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0'}
    try:
        from summon_bestiary import get_heat_leader, load_config
        username, xp = get_heat_leader()
        window = int(load_config().get('heat_window_sec', 900))
        return jsonify({
            'username': username or '',
            'xp': xp,
            'window_sec': window,
            'has_leader': bool(username),
            'display': f'Heat: {username} - {xp}' if username else '',
        }), 200, resp_headers
    except Exception as e:
        return jsonify({'error': str(e), 'username': '', 'xp': 0, 'has_leader': False}), 500, resp_headers


@app.route('/api/activity-commands')
def activity_commands():
    """Return command events for the overlay activity feed. Query param: since=ms (return events with time > since)."""
    try:
        since = request.args.get('since', type=int) or 0
        with command_events_lock:
            out = [e for e in recent_command_events if e['time'] > since]
        return jsonify({'events': out})
    except Exception as e:
        return jsonify({'error': str(e), 'events': []}), 500


@app.route('/api/chat-command', methods=['POST', 'OPTIONS'])
def chat_command_api():
    """Unified chat router: earn, spend, query, meta. See docs/streaming-system-rework-plan.md."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from chat_command import dispatch_chat_command
        body = request.get_json(force=True, silent=True) or {}
        result = dispatch_chat_command(body)
        raw = (body.get("rawMessage") or body.get("message") or "").strip()
        req_type = (body.get("type") or "").strip()
        # Bang-commands and failures: one glanceable line (HTTP access muted in glance mode)
        if raw.startswith("!") or (not result.ok and result.message):
            user = body.get("username") or body.get("userName") or "?"
            status = "OK" if result.ok else "FAIL"
            tokens = raw.split() if raw.startswith("!") else []
            cmd_part = " ".join(tokens[:2]) if tokens else ""
            reply = (result.message or raw or req_type or "?").strip()
            if len(reply) > 100:
                reply = reply[:100]
            if cmd_part and reply not in (raw, cmd_part):
                detail = f"{cmd_part} — {reply}"
            elif cmd_part:
                detail = cmd_part
            else:
                detail = reply
            print(f"Chat [{status}] {user}: {detail}")
        return jsonify(result.to_api_dict())
    except Exception as e:
        print(f"chat-command error: {e}")
        return jsonify({"ok": False, "message": str(e), "pts": None, "earned": 0}), 500


@app.route('/api/session', methods=['GET', 'OPTIONS'])
def session_status_api():
    """Live session status for the manager (started / pending stream-end debounce)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from chat_command import _load_session_state, get_config
        state = _load_session_state()
        debounce_hours = float(get_config().get("reset_debounce_hours", 4))
        debounce_sec = int(debounce_hours * 3600)
        offline_at = state.get("stream_offline_at")
        pending_end = bool(offline_at)
        retry_after_sec = None
        if pending_end:
            elapsed = int(time.time()) - int(offline_at)
            retry_after_sec = max(0, debounce_sec - elapsed)
        return jsonify({
            "stream_started_at": state.get("stream_started_at"),
            "stream_offline_at": offline_at,
            "debounce_hours": debounce_hours,
            "retry_after_sec": retry_after_sec,
            "pending_end": pending_end,
            "fard_used_count": len(state.get("fard_used") or {}),
            "first_words_count": len(state.get("first_words") or {}),
            "fard_used": sorted((state.get("fard_used") or {}).keys()),
            "first_words": sorted((state.get("first_words") or {}).keys()),
        })
    except Exception as e:
        return jsonify({"error": str(e), "pending_end": False}), 500


@app.route('/api/session/clear-user', methods=['POST', 'OPTIONS'])
def session_clear_user_api():
    """Remove one user from fard_used and/or first_words maps. Body: { username, clear: ["fard","first_words"] }."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from chat_command import _load_session_state, _save_session_state
        body = request.get_json(force=True, silent=True) or {}
        username = (body.get('username') or body.get('userName') or '').strip().lower()
        if not username:
            return jsonify({"error": "username required"}), 400
        clear = body.get('clear') or ['fard', 'first_words']
        if isinstance(clear, str):
            clear = [clear]
        clear = [str(c).strip().lower() for c in clear if str(c).strip()]
        state = _load_session_state()
        removed = []
        if 'fard' in clear or 'fard_used' in clear:
            used = state.setdefault('fard_used', {})
            if used.pop(username, None) is not None:
                removed.append('fard')
            state['fard_used'] = used
        if 'first_words' in clear or 'firstwords' in clear:
            fw = state.setdefault('first_words', {})
            if fw.pop(username, None) is not None:
                removed.append('first_words')
            state['first_words'] = fw
        _save_session_state(state)
        return jsonify({
            "ok": True,
            "username": username,
            "removed": removed,
            "fard_used_count": len(state.get('fard_used') or {}),
            "first_words_count": len(state.get('first_words') or {}),
        })
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route('/api/session/reset', methods=['POST', 'OPTIONS'])
def session_reset_api():
    """Reset per-stream session state (Stream Started). Does not wipe viewer points."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from chat_command import reset_session_state
        reset_session_state()
        return jsonify({"ok": True, "message": "Session reset."})
    except Exception as e:
        print(f"session reset error: {e}")
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route('/api/session/end', methods=['POST', 'OPTIONS'])
def session_end_api():
    """Stream Offline: schedule or execute chat wipe + auto-bank (4h debounce unless force)."""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        from chat_command import try_execute_stream_end
        body = request.get_json(force=True, silent=True) or {}
        force = bool(body.get("force") or request.args.get("force") in ("1", "true", "yes"))
        result = try_execute_stream_end(force=force)
        ok = result.get("ok", True) if not result.get("executed") else result.get("ok", True)
        status = 200 if ok else 500
        return jsonify({"ok": ok, **result}), status
    except Exception as e:
        print(f"session end error: {e}")
        return jsonify({"ok": False, "error": str(e)}), 500


def stream_end_debounce_thread():
    """Background: run pending stream-end reset after debounce elapses."""
    while True:
        time.sleep(60)
        try:
            from chat_command import try_execute_pending_stream_end
            result = try_execute_pending_stream_end()
            if result.get("executed"):
                print(f"stream-end auto reset: {result}")
        except Exception as e:
            print(f"stream-end debounce error: {e}")


@app.route('/api/status')
def get_status():
    with data_lock:
        return jsonify({
            'running': True,
            'has_data': bool(current_game_data),
            'save_directory': SAVE_DIRECTORY
        })

if __name__ == '__main__':
    # Ensure we are in the correct directory
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    
    # Start background update thread (save-file parser fallback)
    update_thread = threading.Thread(target=update_game_data, daemon=True)
    update_thread.start()
    
    # Start game WebSocket relay when enabled (transmit data to HTTP /api/game-data and game_summary.json)
    if USE_GAME_WEBSOCKET and websocket:
        threading.Thread(target=snapshot_writer_thread, daemon=True).start()
        threading.Thread(target=game_ws_thread, daemon=True).start()
    # OBS inventory crop relay (direct filter control from game geometry)
    if obs_inv_config.get('enabled', True) and websocket:
        threading.Thread(target=obs_relay_thread, daemon=True).start()
    # Double points countdown for OBS (writes to double_points_countdown.txt every second)
    threading.Thread(target=double_points_countdown_thread, daemon=True).start()
    threading.Thread(target=free_until_freeze_thread, daemon=True).start()
    threading.Thread(target=stream_end_debounce_thread, daemon=True).start()
    
    # Load initial data
    game_info = parser.get_current_game_info()
    if game_info:
        with data_lock:
            current_game_data = game_info

    # Ensure streamer_chat_score.txt exists for OBS Read from file
    _save_score_data(_load_score_data())
    try:
        from points_command import refresh_death_cost_display_file
        refresh_death_cost_display_file()
    except Exception as e:
        print(f"Death cost display init failed: {e}")

    # Load summon march queue from disk for Godot catch-up
    summon_march_events[:] = _load_summon_march_queue_from_disk()

    print("\n" + "="*50)
    print("SPD Overlay Server Starting...")
    print("="*50)
    print(f"Save Directory: {SAVE_DIRECTORY}")
    print(f"Server URL: http://localhost:5000")
    print("Overlays: /overlay  |  /overlay-vertical (1080x1920)")
    if USE_GAME_WEBSOCKET and websocket:
        print(f"Game WebSocket: {GAME_WS_URL}")
    if obs_inv_config.get('enabled', True) and websocket:
        print(f"OBS Inv Layout: {obs_inv_config.get('obs_ws_url')}")
    print(f"Summon march queue: {len(summon_march_events)} events")
    print(f"Streamer vs Chat: {STREAMER_CHAT_SCORE_TXT}")
    if LOG_VERBOSE:
        print("Console: verbose (HTTP access + game transport detail)")
    else:
        print("Console: glance (set SPD_LOG_VERBOSE=1 or --verbose for HTTP + game transport detail)")
    print("="*50 + "\n")

    # Glance: mute successful HTTP access lines. Verbose: keep today's poll-path mute only.
    _QUIET_PATHS = (
        '/game_summary',
        '/api/double-points-remaining',
        '/api/game-data',
        '/api/bestiary',
        '/api/bestiary-config',
        '/api/companion-settings',
        '/api/companion-settings/heartbeat',
        '/api/summon-march',
        '/api/points-config',
        '/api/activity-commands',
        '/api/top-summoner',
        '/api/heat-leader',
        '/api/spend-disabled',
        '/api/death-cost',
        '/api/session',
        '/api/spawn-costs',
    )

    class _QuietAccessFilter(logging.Filter):
        def filter(self, record):
            msg = record.getMessage()
            # Keep non-200 / errors visible
            if ' 200 ' not in msg and ' 204 ' not in msg:
                return True
            if LOG_VERBOSE:
                return not any(p in msg for p in _QUIET_PATHS)
            return False

    logging.getLogger('werkzeug').addFilter(_QuietAccessFilter())
    if LOG_VERBOSE:
        print(
            "Request log: polling GETs muted (bestiary/summon-march/points-config/…). "
            "Chat bang-commands print as: Chat [OK|FAIL] user: …\n"
        )
    else:
        print(
            "Request log: successful HTTP muted. "
            "Chat bang-commands print as: Chat [OK|FAIL] user: !cmd — …\n"
        )

    # Run Flask server
    app.run(host='127.0.0.1', port=5000, debug=False, threaded=True)
