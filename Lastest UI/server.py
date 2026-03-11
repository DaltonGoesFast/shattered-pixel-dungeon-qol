from flask import Flask, jsonify, request, send_from_directory
from flask_cors import CORS
from spd_parser import SPDSaveParser
import os
import json
import logging
import queue
import uuid
import threading
import time
from datetime import datetime

try:
    import websocket
except ImportError:
    websocket = None

app = Flask(__name__, static_folder='.')
CORS(app)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

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
FREE_UNTIL_FILE = os.path.join(SCRIPT_DIR, "free_until.json")
VIEWER_POINTS_FILE = os.path.join(SCRIPT_DIR, "viewer_points.txt")
VIEWER_POINTS_LOCK_FILE = VIEWER_POINTS_FILE + ".lock"
DOUBLE_POINTS_COUNTDOWN_FILE = os.path.join(SCRIPT_DIR, "double_points_countdown.txt")
STREAMER_CHAT_SCORE_FILE = os.path.join(SCRIPT_DIR, "streamer_chat_score.json")
STREAMER_CHAT_SCORE_TXT = os.path.join(SCRIPT_DIR, "streamer_chat_score.txt")

# Game WebSocket: receive live stream from game and serve via HTTP /api/game-data and game_summary.json
GAME_WS_URL = "ws://127.0.0.1:5001"   # Game streaming port (default in game Settings; change if you set a different port)
USE_GAME_WEBSOCKET = True             # If True, connect to game WS for live data (same shape as inspector)
GAME_WS_RECONNECT_INTERVAL = 10       # Seconds between reconnect attempts when game isn't running

# OBS Advanced Scene Switcher: send item_info_open / item_info_closed when ui.open_windows changes
OBS_WS_URL = "ws://127.0.0.1:4455"    # OBS WebSocket (Tools → WebSocket Server Settings)
USE_OBS_ITEM_INFO_RELAY = True       # If True, send messages to Advanced Scene Switcher when item_info window opens/closes

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

# OBS Advanced Scene Switcher relay: track item_info state, queue messages to send
last_item_info_open = None
obs_message_queue = queue.Queue()
game_ws_received_count = 0

# Chat spawn: reference to game WebSocket for sending commands (set when connected)
game_ws_app = None
pending_spawns = {}  # request_id -> {"event": Event, "success": bool}
spawn_lock = threading.Lock()
# Activity feed: recent command events for overlay (max 100, each: time, username, command, detail, success)
recent_command_events = []
COMMAND_EVENTS_MAX = 100
command_events_lock = threading.Lock()


def _record_command_event(username, command, detail, success):
    """Append a command event for the activity feed (overlay polls /api/activity-commands)."""
    with command_events_lock:
        recent_command_events.append({
            "time": int(time.time() * 1000),
            "username": username or "",
            "command": command,
            "detail": detail or "",
            "success": bool(success),
        })
        while len(recent_command_events) > COMMAND_EVENTS_MAX:
            recent_command_events.pop(0)


SPAWN_RESULT_TIMEOUT = 18.0  # seconds to wait for game to report spawn/gold result (game may be slow if not in run or main thread busy)
SPAWN_WHITELIST = frozenset([
    'rat', 'albino', 'snake', 'gnoll', 'crab', 'slime', 'swarm', 'thief',
    'skeleton', 'bat', 'brute', 'shaman', 'spinner', 'dm100', 'guard',
    'necromancer', 'ghoul', 'elemental', 'warlock', 'monk', 'golem',
    'succubus', 'eye', 'scorpio'
])
SPAWN_COOLDOWN_SEC = 0  # 0 = disabled; handle cooldown in Streamer.bot
last_spawn_time = 0.0

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


def _send_obs_message(msg):
    """Queue a message for the OBS relay thread (e.g. item_info_open, item_info_closed)."""
    if USE_OBS_ITEM_INFO_RELAY and websocket:
        try:
            obs_message_queue.put_nowait(msg)
        except queue.Full:
            pass


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
    elif data.get('type') == 'boss_slain':
        score_data['streamer'] = score_data.get('streamer', 0) + 1
    _save_score_data(score_data)
    print(f"Score event {data.get('type')}: streamer={score_data['streamer']} chat={score_data['chat']}")


def _game_ws_on_message(ws, message):
    """Handle message from game WebSocket: update live data (same JSON shape as inspector)."""
    global current_game_data, last_ws_update_time, last_item_info_open, game_ws_received_count
    try:
        data = json.loads(message)
        # Handle spawn/gold result (game reports success/failure)
        if data.get('type') in ('ping_result', 'spawn_result', 'champion_result', 'gold_result', 'curse_result', 'gas_result', 'scroll_result', 'wand_result', 'buff_result', 'debuff_result', 'trap_result', 'transmute_result', 'summon_bee_result', 'ward_result'):
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
                        if data.get('type') == 'transmute_result' and data.get('item_name'):
                            pending_spawns[rid]['item_name'] = data.get('item_name')
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
                        pending_spawns[rid]['event'].set()
            print(f"Game {data.get('type')}: request_id={rid} success={ok}")
            return
        if data.get('type') in ('hero_died', 'boss_slain') and data.get('source') == 'shattered-pixel-dungeon':
            _handle_score_event(data)
            return
        if data.get('source') != 'shattered-pixel-dungeon':
            return
        game_ws_received_count += 1
        last_ws_update_time = time.time()
        with data_lock:
            current_game_data = data
        try:
            with open(GAME_SUMMARY_JSON, "w", encoding='utf-8') as f:
                json.dump(data, f, indent=4)
                f.flush()
                os.fsync(f.fileno())
            # Generate text summary (parser expects same shape as game_summary.json)
            summary_text = parser.generate_summary_text(data)
            with open(GAME_SUMMARY_TXT, "w", encoding='utf-8') as f:
                f.write(summary_text)
                f.flush()
                os.fsync(f.fileno())
        except Exception as e:
            print(f"Error writing game_summary from WS: {e}")
        # OBS Advanced Scene Switcher: send item_info_open / item_info_closed when state changes
        if USE_OBS_ITEM_INFO_RELAY:
            ui = data.get('ui') or {}
            open_windows = ui.get('open_windows') or []
            item_info_open = 'item_info' in open_windows
            if last_item_info_open is not None and item_info_open != last_item_info_open:
                msg = 'item_info_open' if item_info_open else 'item_info_closed'
                _send_obs_message(msg)
            last_item_info_open = item_info_open
    except Exception as e:
        print(f"Game WS message error: {e}")


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
                mins, secs = divmod(secs, 60)
                display = f"2x points: {mins}:{secs:02d}"
            with open(DOUBLE_POINTS_COUNTDOWN_FILE, "w", encoding="utf-8") as f:
                f.write(display)
                f.flush()
                os.fsync(f.fileno())
        except Exception as e:
            print(f"Double points countdown error: {e}")
        time.sleep(1.0)


def obs_relay_thread():
    """Connect to OBS WebSocket and send Advanced Scene Switcher messages from the queue."""
    last_obs_error_print = 0.0
    OBS_ERROR_THROTTLE = 60.0  # seconds
    while USE_OBS_ITEM_INFO_RELAY and websocket:
        try:
            ws = websocket.create_connection(OBS_WS_URL)
            last_obs_error_print = 0.0  # reset once connected
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
                try:
                    message = obs_message_queue.get(timeout=0.5)
                except queue.Empty:
                    continue
                req = {
                    'op': 6,
                    'd': {
                        'requestType': 'CallVendorRequest',
                        'requestId': f'spd-{uuid.uuid4()}',
                        'requestData': {
                            'vendorName': 'AdvancedSceneSwitcher',
                            'requestType': 'AdvancedSceneSwitcherMessage',
                            'requestData': {'message': message}
                        }
                    }
                }
                ws.send(json.dumps(req))
        except Exception as e:
            now = time.time()
            is_refused = (getattr(e, 'errno', None) == 10061 or 'refused' in str(e).lower())
            if is_refused and (now - last_obs_error_print) < OBS_ERROR_THROTTLE:
                pass  # skip log when OBS not running
            else:
                if is_refused:
                    last_obs_error_print = now
                print(f"OBS relay error: {e}")
        time.sleep(5)


def game_ws_thread():
    """Connect to game WebSocket and keep receiving; reconnect on disconnect."""
    global last_item_info_open, game_ws_received_count, game_ws_app
    last_error_print = -999.0  # So first failure prints immediately
    while USE_GAME_WEBSOCKET and websocket:
        try:
            def on_open(conn):
                pass
            def on_close(conn, code, reason):
                global last_item_info_open, game_ws_received_count, game_ws_app
                last_item_info_open = None
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
            else:
                data = {
                    "cost_per_gold": 5,
                    "cost_per_curse": 200,
                    "cost_per_gas": 75,
                    "cost_per_scroll": 100,
                    "cost_per_trap": 50,
                    "cost_per_transmute": 150,
                    "cost_per_ally_bee": 75,
                    "cost_per_ward": 30,
                    "cost_per_buff": 75,
                    "cost_per_debuff": 50,
                    "cost_per_wand_common": 50,
                    "cost_per_wand_uncommon": 100,
                    "cost_per_wand_rare": 200,
                    "cost_per_wand_veryrare": 400,
                    "default_monster_cost": 100,
                    "cost_per_monster": {
                        "rat": 5, "albino": 10, "snake": 10, "gnoll": 10, "crab": 15,
                        "slime": 15, "swarm": 15, "thief": 20, "skeleton": 20, "bat": 30,
                        "brute": 30, "shaman": 35, "spinner": 25, "dm100": 20, "guard": 25,
                        "necromancer": 25, "ghoul": 40, "elemental": 40, "warlock": 45,
                        "monk": 50, "golem": 50, "succubus": 60, "eye": 70, "scorpio": 80,
                    },
                }
            free_until = {}
            if os.path.exists(FREE_UNTIL_FILE):
                try:
                    with open(FREE_UNTIL_FILE, encoding='utf-8') as f:
                        free_until = json.load(f)
                except Exception:
                    pass
            data["free_until"] = free_until
            return jsonify(data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500
    # POST - save
    try:
        data = request.get_json(force=True, silent=True) or {}
        # Validate and sanitize
        cfg = {
            "cost_per_gold": max(1, int(data.get("cost_per_gold", 5))),
            "cost_per_curse": max(1, int(data.get("cost_per_curse", 200))),
            "cost_per_gas": max(1, int(data.get("cost_per_gas", 75))),
            "cost_per_scroll": max(1, int(data.get("cost_per_scroll", 100))),
            "cost_per_trap": max(1, int(data.get("cost_per_trap", 50))),
            "cost_per_transmute": max(1, int(data.get("cost_per_transmute", 150))),
            "cost_per_ally_bee": max(1, int(data.get("cost_per_ally_bee", 75))),
            "cost_per_ward": max(1, int(data.get("cost_per_ward", 30))),
            "cost_per_buff": max(1, int(data.get("cost_per_buff", 75))),
            "cost_per_debuff": max(1, int(data.get("cost_per_debuff", 50))),
            "cost_per_wand_common": max(1, int(data.get("cost_per_wand_common", 50))),
            "cost_per_wand_uncommon": max(1, int(data.get("cost_per_wand_uncommon", 100))),
            "cost_per_wand_rare": max(1, int(data.get("cost_per_wand_rare", 200))),
            "cost_per_wand_veryrare": max(1, int(data.get("cost_per_wand_veryrare", 400))),
            "default_monster_cost": max(1, int(data.get("default_monster_cost", 100))),
            "cost_per_monster": {},
        }
        for k, v in (data.get("cost_per_monster") or {}).items():
            try:
                cfg["cost_per_monster"][str(k)] = max(0, int(v))
            except (ValueError, TypeError):
                pass
        with open(POINTS_CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/api/cost-free', methods=['POST', 'DELETE', 'OPTIONS'])
def cost_free_api():
    """Set a cost as free for N minutes, or cancel. costKey: e.g. cost_per_gold, cost_per_monster.rat"""
    if request.method == 'OPTIONS':
        return '', 204
    try:
        body = request.get_json(force=True, silent=True) if request.method == 'POST' else {}
        body = body or {}
        cost_key = ''
        if request.method == 'DELETE':
            cost_key = (request.args.get('costKey') or request.args.get('cost_key') or '').strip()
        else:
            cost_key = (body.get('costKey') or body.get('cost_key') or '').strip()
        if not cost_key:
            return jsonify({"error": "costKey required"}), 400
        free_until = {}
        if os.path.exists(FREE_UNTIL_FILE):
            try:
                with open(FREE_UNTIL_FILE, encoding='utf-8') as f:
                    free_until = json.load(f)
            except Exception:
                pass
        if request.method == 'DELETE' or body.get('cancel'):
            free_until.pop(cost_key, None)
            with open(FREE_UNTIL_FILE, 'w', encoding='utf-8') as f:
                json.dump(free_until, f, indent=2)
            return jsonify({"ok": True, "costKey": cost_key, "cancelled": True})
        minutes = max(0, min(1440, int(body.get('minutes', body.get('mins', 5)))))
        end_ts = int(time.time()) + minutes * 60
        free_until[cost_key] = end_ts
        with open(FREE_UNTIL_FILE, 'w', encoding='utf-8') as f:
            json.dump(free_until, f, indent=2)
        return jsonify({"ok": True, "costKey": cost_key, "freeUntil": end_ts})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def _acquire_viewer_points_lock():
    """Acquire lock on viewer_points file. Returns True if acquired."""
    import time
    start = time.monotonic()
    while (time.monotonic() - start) < 10:
        try:
            fd = os.open(VIEWER_POINTS_LOCK_FILE, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, str(os.getpid()).encode())
            os.close(fd)
            return True
        except FileExistsError:
            time.sleep(0.05)
    return False


def _release_viewer_points_lock():
    try:
        os.remove(VIEWER_POINTS_LOCK_FILE)
    except OSError:
        pass


@app.route('/api/viewer-points', methods=['GET', 'POST', 'OPTIONS'])
def viewer_points_api():
    """Get or update viewer points (username -> {points, last}). Uses same lock file as C# and Python."""
    if request.method == 'OPTIONS':
        return '', 204
    if request.method == 'GET':
        if not _acquire_viewer_points_lock():
            return jsonify({"error": "Points file busy"}), 503
        try:
            data = {}
            if os.path.exists(VIEWER_POINTS_FILE):
                try:
                    with open(VIEWER_POINTS_FILE, encoding='utf-8') as f:
                        for line in f:
                            parts = line.strip().split('|')
                            if len(parts) >= 3:
                                try:
                                    donation_pts = int(parts[3]) if len(parts) >= 4 else 0
                                    data[parts[0].lower()] = {
                                        'points': int(parts[1]),
                                        'last': int(parts[2]),
                                        'donationPts': donation_pts,
                                    }
                                except ValueError:
                                    pass
                except Exception as e:
                    return jsonify({"error": str(e)}), 500
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
        if not username:
            return jsonify({"error": "username required"}), 400
        if not _acquire_viewer_points_lock():
            return jsonify({"error": "Points file busy, try again"}), 503
        try:
            data = {}
            if os.path.exists(VIEWER_POINTS_FILE):
                with open(VIEWER_POINTS_FILE, encoding='utf-8') as f:
                    for line in f:
                        parts = line.strip().split('|')
                        if len(parts) >= 3:
                            try:
                                dp = int(parts[3]) if len(parts) >= 4 else 0
                                data[parts[0].lower()] = (int(parts[1]), int(parts[2]), dp)
                            except ValueError:
                                pass
            existing = data.get(username.lower(), (0, 0, 0))
            if donation_pts is None:
                donation_pts = existing[2]
            donation_pts = min(donation_pts, max(0, points))
            new_pts = max(max(0, points), donation_pts)
            data[username.lower()] = (new_pts, existing[1], donation_pts)
            lines = [f"{k}|{v[0]}|{v[1]}|{v[2]}" for k, v in data.items()]
            with open(VIEWER_POINTS_FILE, 'w', encoding='utf-8') as f:
                f.write('\n'.join(lines))
            return jsonify({"ok": True, "username": username, "points": data[username.lower()][0]})
        finally:
            _release_viewer_points_lock()
    except ValueError as e:
        return jsonify({"error": "Invalid points: " + str(e)}), 400
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def _read_viewer_points_raw():
    """Read viewer_points file as dict[key] = (pts, last, donation_pts). Returns {} if not exists."""
    data = {}
    if not os.path.exists(VIEWER_POINTS_FILE):
        return data
    with open(VIEWER_POINTS_FILE, encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('|')
            if len(parts) >= 3:
                try:
                    donation_pts = int(parts[3]) if len(parts) >= 4 else 0
                    data[parts[0].lower()] = (int(parts[1]), int(parts[2]), donation_pts)
                except ValueError:
                    pass
    return data


def _write_viewer_points_raw(data):
    lines = [f"{k}|{v[0]}|{v[1]}|{v[2]}" for k, v in data.items()]
    with open(VIEWER_POINTS_FILE, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))


@app.route('/api/viewer-points/clear-non-donor', methods=['POST', 'OPTIONS'])
def viewer_points_clear_non_donor():
    """Set each user's points = donationPts. Donors keep their amount; non-donors go to 0."""
    if request.method == 'OPTIONS':
        return '', 204
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        data = _read_viewer_points_raw()
        for k in data:
            pts, last, donation_pts = data[k]
            data[k] = (donation_pts, 0, donation_pts)
        _write_viewer_points_raw(data)
        return jsonify({"ok": True})
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
        data = _read_viewer_points_raw()
        keys = users_filter if users_filter else list(data.keys())
        for k in keys:
            pts, last, donation_pts = data.get(k, (0, 0, 0))
            data[k] = (pts + chat_add, last, donation_pts + donor_add)
        _write_viewer_points_raw(data)
        return jsonify({"ok": True, "count": len(keys)})
    finally:
        _release_viewer_points_lock()


@app.route('/api/viewer-points/bulk/chat-to-donor', methods=['POST', 'OPTIONS'])
def viewer_points_chat_to_donor():
    """Convert chat points to donor points for specified users. pts stays same, donation_pts = pts."""
    if request.method == 'OPTIONS':
        return '', 204
    body = request.get_json(force=True, silent=True) or {}
    users_filter = body.get('users')
    if users_filter:
        users_filter = [str(u).strip().lower() for u in users_filter if u and str(u).strip()]
    if not users_filter:
        return jsonify({"error": "users required"}), 400
    if not _acquire_viewer_points_lock():
        return jsonify({"error": "Points file busy, try again"}), 503
    try:
        data = _read_viewer_points_raw()
        count = 0
        for k in users_filter:
            if k not in data:
                continue
            pts, last, donation_pts = data[k]
            chat_pts = max(0, pts - donation_pts)
            if chat_pts > 0:
                data[k] = (pts, last, donation_pts + chat_pts)
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
        data = _read_viewer_points_raw()
        for k in data:
            pts, last, donation_pts = data[k]
            chat_only = max(0, pts - donation_pts)
            data[k] = (chat_only, last, 0)
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
        data = _read_viewer_points_raw()
        for k in data:
            data[k] = (0, 0, 0)
        _write_viewer_points_raw(data)
        return jsonify({"ok": True})
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
        data = {}
        if os.path.exists(VIEWER_POINTS_FILE):
            with open(VIEWER_POINTS_FILE, encoding='utf-8') as f:
                for line in f:
                    parts = line.strip().split('|')
                    if len(parts) >= 3 and parts[0].lower() != username.lower():
                        try:
                            donation_pts = int(parts[3]) if len(parts) >= 4 else 0
                            data[parts[0].lower()] = (int(parts[1]), int(parts[2]), donation_pts)
                        except ValueError:
                            pass
        lines = [f"{k}|{v[0]}|{v[1]}|{v[2]}" for k, v in data.items()]
        with open(VIEWER_POINTS_FILE, 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines))
        return jsonify({"ok": True})
    finally:
        _release_viewer_points_lock()


@app.route('/fonts/<path:filename>')
def serve_font(filename):
    """Serve font files for OBS countdown (pixel font from game)"""
    return send_from_directory('fonts', filename)

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
        game_ws_app.send(json.dumps({'command': 'ping', 'request_id': request_id}))
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
            print(f"Spawn send to game: {monster} request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Spawn OK: {monster} for {username}")
            _record_command_event(username, 'spawn', monster, True)
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
    """Receive champion spawn command from Streamer.bot; forward to game via WebSocket. Cost is 2× base (no zone discount); overlay handles cost."""
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
            game_ws_app.send(json.dumps(payload))
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
            print(f"Champion OK: {champion_monster} for {username}")
            _record_command_event(username, 'champion', champion_monster, True)
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
            amount = 5
        amount = max(1, min(100, amount))
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
            print(f"Gold send to game: amount={amount} request_id={request_id} (waiting up to {SPAWN_RESULT_TIMEOUT}s for response)")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Gold OK: {amount} for {username}")
            _record_command_event(username, 'gold', str(amount), True)
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
            print(f"Gas send to game: request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Gas OK: {gas_name} for {username}")
            _record_command_event(username, 'gas', gas_name or '', True)
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
        if not slot:
            return jsonify({'ok': False, 'error': 'Missing slot'}), 400
        valid_slots = {'weapon', 'armor', 'ring', 'artifact', 'misc'}
        if slot not in valid_slots:
            return jsonify({'ok': False, 'error': f'Invalid slot. Options: weapon, armor, ring, artifact, misc (middle slot)'}), 400
        if not game_ws_app:
            return jsonify({'ok': False, 'error': 'Game not connected'}), 503
        request_id = str(uuid.uuid4())
        ev = threading.Event()
        with spawn_lock:
            pending_spawns[request_id] = {'event': ev, 'success': False}
        try:
            payload = {'command': 'curse', 'slot': slot, 'request_id': request_id}
            if username:
                payload['username'] = username
            print(f"Curse send to game: slot={slot} request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Curse timed out'}), 504
        if success:
            print(f"Curse OK: {slot} ({item_name}) for {username}")
            _record_command_event(username, 'curse', slot, True)
            return jsonify({'ok': True, 'slot': slot, 'item_name': item_name})
        err = curse_error or f'No item in {slot} slot or already cursed'
        _record_command_event(username, 'curse', slot, False)
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
            print(f"Scroll send to game: request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Scroll OK: {scroll_name} for {username}")
            _record_command_event(username, 'scroll', scroll_name or '', True)
            return jsonify({'ok': True, 'scroll_name': scroll_name})
        err = scroll_error or 'Could not use random scroll'
        _record_command_event(username, 'scroll', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Scroll 400 exception: {e}")
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
            print(f"Trap send to game: request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Trap OK: {trap_name} for {username}")
            _record_command_event(username, 'trap', trap_name or '', True)
            return jsonify({'ok': True, 'trap_name': trap_name})
        err = trap_error or 'No space to place trap'
        _record_command_event(username, 'trap', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Trap 400 exception: {e}")
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
            print(f"Transmute send to game: request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
        except Exception as e:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': str(e)}), 503
        if ev.wait(timeout=SPAWN_RESULT_TIMEOUT):
            with spawn_lock:
                pending = pending_spawns.pop(request_id, {})
                success = pending.get('success', False)
                item_name = pending.get('item_name', '')
                transmute_error = pending.get('error')
        else:
            with spawn_lock:
                pending_spawns.pop(request_id, None)
            return jsonify({'ok': False, 'error': 'Transmute command timed out'}), 504
        if success:
            print(f"Transmute OK: {item_name} for {username}")
            _record_command_event(username, 'transmute', item_name or '', True)
            return jsonify({'ok': True, 'item_name': item_name})
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
            print(f"Ward send to game: request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Ward OK: {ward_name} for {username}")
            _record_command_event(username, 'ward', ward_name or '', True)
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
            print(f"Summon bee send to game: request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Summon bee OK: {ally_name} for {username}")
            _record_command_event(username, 'summon_bee', ally_name or '', True)
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
            print(f"Buff send to game: request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Buff OK: {buff_name} for {username}")
            _record_command_event(username, 'buff', buff_name or '', True)
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
            print(f"Debuff send to game: request_id={request_id}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Debuff OK: {debuff_name} for {username}")
            _record_command_event(username, 'debuff', debuff_name or '', True)
            return jsonify({'ok': True, 'debuff_name': debuff_name})
        err = debuff_error or 'Could not apply random debuff'
        _record_command_event(username, 'debuff', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Debuff 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/wand-command', methods=['POST', 'OPTIONS'])
def wand_command():
    """Receive cursed wand command from Streamer.bot; forward to game via WebSocket. Cost varies by rarity (0=common, 1=uncommon, 2=rare, 3=very_rare)."""
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
            print(f"Wand send to game: request_id={request_id} tier={tier}")
            game_ws_app.send(json.dumps(payload))
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
            print(f"Wand OK: {effect_name} (rarity={rarity}) for {username}")
            _record_command_event(username, 'wand', effect_name or '', True)
            return jsonify({'ok': True, 'effect_name': effect_name, 'rarity': rarity})
        err = wand_error or 'Could not trigger cursed wand effect'
        _record_command_event(username, 'wand', '', False)
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Wand 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


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
        mins, secs = divmod(secs, 60)
        display = f"{mins}:{secs:02d}"
        return jsonify({"active": True, "seconds_left": end_ts - now, "display": display}), 200, resp_headers
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
        threading.Thread(target=game_ws_thread, daemon=True).start()
    # Start OBS relay when enabled (sends item_info_open / item_info_closed to Advanced Scene Switcher)
    if USE_OBS_ITEM_INFO_RELAY and websocket:
        threading.Thread(target=obs_relay_thread, daemon=True).start()
    # Double points countdown for OBS (writes to double_points_countdown.txt every second)
    threading.Thread(target=double_points_countdown_thread, daemon=True).start()
    
    # Load initial data
    game_info = parser.get_current_game_info()
    if game_info:
        with data_lock:
            current_game_data = game_info

    # Ensure streamer_chat_score.txt exists for OBS Read from file
    _save_score_data(_load_score_data())

    print("\n" + "="*50)
    print("SPD Overlay Server Starting...")
    print("="*50)
    print(f"Save Directory: {SAVE_DIRECTORY}")
    print(f"Server URL: http://localhost:5000")
    print(f"Add this URL as a Browser Source in OBS")
    if USE_GAME_WEBSOCKET and websocket:
        print(f"Game WebSocket: {GAME_WS_URL} (live data → /api/game-data, game_summary.txt/json)")
    if USE_OBS_ITEM_INFO_RELAY and websocket:
        print(f"OBS Item Info Relay: {OBS_WS_URL} (item_info_open / item_info_closed → Advanced Scene Switcher)")
    print(f"Chat spawn: POST /api/spawn-command {{\"monster\": \"rat\"}} (cooldown: {SPAWN_COOLDOWN_SEC}s)")
    print(f"Connection test: GET /api/game-ping (returns version if game connected)")
    print(f"Streamer vs Chat: {STREAMER_CHAT_SCORE_TXT} (OBS Read from file)")
    print("="*50 + "\n")

    # Suppress request logs for high-frequency polling endpoints (easier to see commands)
    class _QuietPollFilter(logging.Filter):
        def filter(self, record):
            msg = record.getMessage()
            return ('/game_summary' not in msg and
                    '/api/double-points-remaining' not in msg and
                    '/api/game-data' not in msg)
    logging.getLogger('werkzeug').addFilter(_QuietPollFilter())

    # Run Flask server
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
