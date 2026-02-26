from flask import Flask, jsonify, request, send_from_directory
from flask_cors import CORS
from spd_parser import SPDSaveParser
import os
import json
import queue
import uuid
import threading
import time

try:
    import websocket
except ImportError:
    websocket = None

app = Flask(__name__, static_folder='.')
CORS(app)

# Configuration
SAVE_DIRECTORY = r"C:\Users\dalto\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon QoL"
UPDATE_INTERVAL = 1.0  # Check for updates every second
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DOUBLE_POINTS_END_FILE = os.path.join(SCRIPT_DIR, "double_points_end.txt")
POINTS_CONFIG_FILE = os.path.join(SCRIPT_DIR, "points_config.json")
VIEWER_POINTS_FILE = os.path.join(SCRIPT_DIR, "viewer_points.txt")
VIEWER_POINTS_LOCK_FILE = VIEWER_POINTS_FILE + ".lock"
DOUBLE_POINTS_COUNTDOWN_FILE = os.path.join(SCRIPT_DIR, "double_points_countdown.txt")

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
SPAWN_RESULT_TIMEOUT = 10.0  # seconds to wait for game to report spawn/gold result
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
                            with open("game_summary.txt", "w", encoding='utf-8') as f:
                                f.write(summary_text)
                                f.flush()
                                os.fsync(f.fileno())
                            
                            # JSON summary
                            with open("game_summary.json", "w", encoding='utf-8') as f:
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


def _game_ws_on_message(ws, message):
    """Handle message from game WebSocket: update live data (same JSON shape as inspector)."""
    global current_game_data, last_ws_update_time, last_item_info_open, game_ws_received_count
    try:
        data = json.loads(message)
        # Handle spawn/gold result (game reports success/failure)
        if data.get('type') in ('spawn_result', 'gold_result', 'curse_result', 'gas_result', 'scroll_result', 'wand_result'):
            rid = data.get('request_id')
            ok = data.get('success', False)
            if rid:
                with spawn_lock:
                    if rid in pending_spawns:
                        pending_spawns[rid]['success'] = ok
                        if data.get('type') in ('spawn_result', 'gold_result') and data.get('error'):
                            pending_spawns[rid]['error'] = data.get('error')
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
                        pending_spawns[rid]['event'].set()
            print(f"Game {data.get('type')}: request_id={rid} success={ok}")
            return
        if data.get('source') != 'shattered-pixel-dungeon':
            return
        game_ws_received_count += 1
        last_ws_update_time = time.time()
        with data_lock:
            current_game_data = data
        try:
            with open("game_summary.json", "w", encoding='utf-8') as f:
                json.dump(data, f, indent=4)
                f.flush()
                os.fsync(f.fileno())
            # Generate text summary (parser expects same shape as game_summary.json)
            summary_text = parser.generate_summary_text(data)
            with open("game_summary.txt", "w", encoding='utf-8') as f:
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
    while USE_OBS_ITEM_INFO_RELAY and websocket:
        try:
            ws = websocket.create_connection(OBS_WS_URL)
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


@app.route('/overlay')
def overlay():
    """Serve the OBS overlay page (game summary text)"""
    return send_from_directory('.', 'index.html')


@app.route('/double-points-countdown')
def double_points_countdown_page():
    """Serve 2x points countdown for OBS Browser Source (avoids CORS when using file://)"""
    return send_from_directory('.', 'double-points-countdown.html')


@app.route('/points-config')
def points_config_page():
    """Alias for main page"""
    return send_from_directory('.', 'points-config.html')


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
                    "cost_per_gold": 2,
                    "cost_per_curse": 200,
                    "cost_per_gas": 75,
                    "cost_per_scroll": 100,
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
            return jsonify(data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500
    # POST - save
    try:
        data = request.get_json(force=True, silent=True) or {}
        # Validate and sanitize
        cfg = {
            "cost_per_gold": max(1, int(data.get("cost_per_gold", 2))),
            "cost_per_curse": max(1, int(data.get("cost_per_curse", 200))),
            "cost_per_gas": max(1, int(data.get("cost_per_gas", 75))),
            "cost_per_scroll": max(1, int(data.get("cost_per_scroll", 100))),
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
    """Get or update viewer points (username -> {points, last})."""
    if request.method == 'OPTIONS':
        return '', 204
    if request.method == 'GET':
        data = {}
        if os.path.exists(VIEWER_POINTS_FILE):
            try:
                with open(VIEWER_POINTS_FILE, encoding='utf-8') as f:
                    for line in f:
                        parts = line.strip().split('|')
                        if len(parts) >= 3:
                            try:
                                data[parts[0].lower()] = {
                                    'points': int(parts[1]),
                                    'last': int(parts[2]),
                                }
                            except ValueError:
                                pass
            except Exception as e:
                return jsonify({"error": str(e)}), 500
        return jsonify(data)
    # POST - add or update a user's points
    try:
        body = request.get_json(force=True, silent=True) or {}
        username = (body.get('username') or '').strip()
        points = int(body.get('points', 0))
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
                                data[parts[0].lower()] = (int(parts[1]), int(parts[2]))
                            except ValueError:
                                pass
            data[username.lower()] = (max(0, points), data.get(username.lower(), (0, 0))[1])
            lines = [f"{k}|{v[0]}|{v[1]}" for k, v in data.items()]
            with open(VIEWER_POINTS_FILE, 'w', encoding='utf-8') as f:
                f.write('\n'.join(lines))
            return jsonify({"ok": True, "username": username, "points": data[username.lower()][0]})
        finally:
            _release_viewer_points_lock()
    except ValueError as e:
        return jsonify({"error": "Invalid points: " + str(e)}), 400
    except Exception as e:
        return jsonify({"error": str(e)}), 500


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
                            data[parts[0].lower()] = (int(parts[1]), int(parts[2]))
                        except ValueError:
                            pass
        lines = [f"{k}|{v[0]}|{v[1]}" for k, v in data.items()]
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
    """Serve the text summary file manually to avoid framework-specific issues"""
    try:
        if not os.path.exists('game_summary.txt'):
            return "File not found.", 404
            
        with open('game_summary.txt', 'r', encoding='utf-8') as f:
            content = f.read()
        return content, 200, {
            'Content-Type': 'text/plain; charset=utf-8',
            'Cache-Control': 'no-cache, no-store, must-revalidate',
            'Pragma': 'no-cache',
            'Expires': '0'
        }
    except Exception as e:
        print(f"Error serving game_summary.txt: {e}")
        return f"Internal Server Error: {str(e)}", 500

@app.route('/game_summary.json')
def serve_json_summary():
    """Serve the JSON summary file"""
    try:
        if not os.path.exists('game_summary.json'):
            return jsonify({"error": "File not found"}), 404
            
        with open('game_summary.json', 'r', encoding='utf-8') as f:
            data = json.load(f)
        return jsonify(data)
    except Exception as e:
        print(f"Error serving game_summary.json: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/game-data')
def get_game_data():
    """API endpoint to get current game data"""
    with data_lock:
        if current_game_data:
            return jsonify(current_game_data)
        else:
            return jsonify({'error': 'No game data available'}), 404

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
            return jsonify({'ok': False, 'error': 'Spawn timed out'}), 504
        last_spawn_time = time.time()
        if success:
            print(f"Spawn OK: {monster} for {username}")
            return jsonify({'ok': True, 'monster': monster})
        err = spawn_error or 'No space to spawn (hero surrounded or no valid tiles)'
        print(f"Spawn FAIL: {err} ({monster} for {username})")
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Spawn 400 exception: {e} (data: {request.get_data(as_text=True)[:200]})")
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
            return jsonify({'ok': True, 'amount': amount})
        err = gold_error or 'No space to drop gold (hero surrounded)'
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
            return jsonify({'ok': True, 'gas_name': gas_name})
        err = gas_error or 'No valid cell to spawn gas (need visible tiles 2-6 from hero)'
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
            return jsonify({'ok': True, 'slot': slot, 'item_name': item_name})
        err = curse_error or f'No item in {slot} slot or already cursed'
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
            return jsonify({'ok': True, 'scroll_name': scroll_name})
        err = scroll_error or 'Could not use random scroll'
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Scroll 400 exception: {e}")
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
            return jsonify({'ok': True, 'effect_name': effect_name, 'rarity': rarity})
        err = wand_error or 'Could not trigger cursed wand effect'
        return jsonify({'ok': False, 'error': err}), 200
    except Exception as e:
        print(f"Wand 400 exception: {e}")
        return jsonify({'ok': False, 'error': str(e)}), 400


@app.route('/api/double-points-remaining')
def double_points_remaining():
    """Return 2x points countdown for OBS Browser Source."""
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
            return jsonify({"active": False, "seconds_left": 0, "display": ""})
        secs = end_ts - now
        mins, secs = divmod(secs, 60)
        display = f"{mins}:{secs:02d}"
        return jsonify({"active": True, "seconds_left": end_ts - now, "display": display})
    except Exception as e:
        return jsonify({"active": False, "error": str(e)}), 500


@app.route('/api/status')
def get_status():
    """Check if the server is running and has data"""
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
    print("="*50 + "\n")
    
    # Run Flask server
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
