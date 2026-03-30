# Streaming Setup Guide (for Collaborators)

This guide helps you emulate the full streaming setup so you can test and verify changes to the overlay, points system, and chat spawn features.

---

## Prerequisites

| Component | Purpose |
|-----------|---------|
| **Python 3.8+** | Runs the overlay server and `points_command.py` |
| **Shattered Pixel Dungeon QoL** | The game (this mod) with streaming enabled |
| **Streamer.bot** | Connects chat to actions (optional for basic testing) |
| **OBS Studio** | Displays the overlay; optional for API testing |

---

## 1. Overlay Server

The Flask server exposes **`/api/game-data`**, **`/api/points-config`** (GET/POST), viewer-points APIs (including bulk ops and **`/api/viewer-points/rebalance-roles`**), **`/api/streamer-chat-score`**, double-points endpoints, **`/api/activity-commands`**, and many forwarded chat routes such as **`/api/spawn-command`**, **`/api/champion-command`**, **`/api/gold-command`**, **`/api/trap-command`**, **`/api/transmute-command`**, **`/api/summon-bee-command`**, **`/api/ward-command`**, **`/api/corrupt-ally-command`**, helper/hurter commands, **`/api/wand-command`**, etc. See `Lastest UI/server.py` for the full list, or open **`http://localhost:5000/ws-inspect`** for a combined inspector.

```bash
cd "Lastest UI"
pip install -r requirements.txt
python server.py
```

**Configure paths in `config.json`:**
- Copy `config.example.json` to `config.json` (gitignored). Set `save_directory` to where SPD saves are stored (default in example: `C:\Users\dalto\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon QoL`).
- On your machine, find your SPD save folder (e.g. under `%APPDATA%` or `~/.shatteredpixel/`)

Server runs at `http://localhost:5000`. You should see:
```
SPD Overlay Server Starting...
Server URL: http://localhost:5000
Game WebSocket: ws://127.0.0.1:5001
```

**Useful URLs (same server):**

| URL | Purpose |
|-----|---------|
| `/overlay` | OBS browser source (game summary overlay) |
| `/points-config` | Costs, helpers/hurters (incl. per-floor helper points), viewer points table, streamer vs chat score |
| `/double-points-countdown` | Optional OBS text source for 2× countdown |
| `/ws-inspect` | Live game JSON + config snapshot |

---

## 2. Game (Streaming Enabled)

1. Build and run the desktop game from this repo.
2. In-game: **Settings** → enable **Streaming** (or similar).
3. The game opens a WebSocket server (default port **5001**; check **Settings → Streaming** if you changed it). `server.py` connects to it as a client and mirrors live state into `game_summary.json` / `/api/game-data`.

**Without the game running:** The server falls back to parsing save files. `/api/game-data` may return 404 until you have a save, or it uses the parser thread to read `game.dat` and `depthX.dat`.

**To test spawn:** The game must be running and in an active run (not title screen). Spawn commands are sent over the WebSocket.

---

## 3. Testing Without Streamer.bot

You can test most functionality without Streamer.bot:

### Overlay / game data
```bash
curl http://localhost:5000/api/game-data
curl http://localhost:5000/game_summary.json
```

### Spawn / Scroll (via Python)
```bash
cd "Lastest UI"
python points_command.py spawn rat YourUsername
python points_command.py scroll YourUsername
```
- Requires: overlay server running, game running with streaming, `viewer_points.txt` with enough points.
- Creates `viewer_points.txt` if missing. Each line is: `username|totalPoints|lastActivityUnix|donationPts|role` (role: `helper`, `hurter`, or empty). Example chat-only user: `yourusername|100|0|0|`

### Spawn (via HTTP)
```bash
curl -X POST http://localhost:5000/api/spawn-command -H "Content-Type: application/json" -d "{\"monster\": \"rat\", \"username\": \"test\"}"
```
- This bypasses the points check (points are enforced in `points_command.py`, not the server). Use for testing spawn delivery only.

---

## 4. Full Setup (Streamer.bot + OBS)

For full chat integration you need Streamer.bot talking to the overlay server and the game, plus OBS if you stream the overlay.

### 4.1 Streamer.bot: import export (optional shortcut)

A bundled Streamer.bot export is included for parity with the maintainer’s setup:

- **File:** `Lastest UI/streamerbot/shatter-the-streamer-export-0.1.0.txt` (single-line base64 export string; paste into Streamer.bot import or select when it asks for the export).
- Import it through Streamer.bot’s normal **Import** flow for actions/triggers.

**After importing, do not assume it will work unchanged on your machine.**

1. **Reconcile with the canonical walkthrough** — Step through [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md) and compare: commands, **Execute Python** / **Execute C#** steps, URLs like `http://127.0.0.1:5000/...`, and especially **absolute paths** to `points_command.py`, `Lastest UI`, and any files the actions read/write. Update every path to your clone location.
2. **WebSocket connections** — Imports do not reliably carry working connections to **your** game or OBS. In Streamer.bot, open the **WebSocket Client** (or related) plugins and ensure you have a connection to the **game** stream, typically **`ws://127.0.0.1:5001`** (or whatever port **Settings → Streaming** shows). If you use the OBS Advanced Scene Switcher relay from `server.py`, that targets **`ws://127.0.0.1:4455`** by default — only enable if your OBS WebSocket matches. Recreate or fix these entries after import.
3. **Overlay server** — `python server.py` must be running on port **5000** (or change Streamer.bot HTTP calls to match).

### 4.2 Streamer.bot: build from scratch

If you prefer not to use the export, follow [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md) end-to-end to create actions, dedupe, and donation hooks.

### 4.3 OBS

- Add a **Browser Source**: `http://localhost:5000/overlay`
- Optional: `http://localhost:5000/double-points-countdown` for 2× points text, or read `streamer_chat_score.txt` / use `/api/streamer-chat-score` per your layout.

**Paths to update** (any machine-specific Streamer.bot step):
- All `FILE`, `DOUBLE_FILE`, `TOP_FARDER_FILE`, and Python invocations pointing at `Lastest UI`
- `points_command.py` resolves `viewer_points.txt` and config next to the script (`SCRIPT_DIR`)

---

## 5. Minimal Test Checklist

| Test | What to do | Expected |
|------|------------|----------|
| Server starts | `python server.py` | No errors, "Server URL" printed |
| Points config UI | Open `http://localhost:5000/points-config` | Page loads; Save writes `points_config.json` |
| Game data | `curl http://localhost:5000/api/game-data` | JSON with `stats` (includes `depth`), `hero`, etc. when game is in a run and WS connected |
| Spawn script | Ensure `viewer_points.txt` has `testuser|50|0|0|` (or use UI), run `python points_command.py spawn rat testuser` | `ok` in `spawn_result.txt` if game is running and has space |
| Half-price | Be deeper than the mob’s native chapter, spawn a cheap sewer mob | Spawn cost reflects overlay rules (see `points_command.py` / config) |
| Streamer.bot import | After import, trigger one command + check game | No path errors; WebSocket shows connected if your actions require it |

---

## Troubleshooting

- **"No game data available"** — Game not running, or save directory wrong. Check `save_directory` in `config.json`.
- **"Game not connected"** on spawn — Game must be running with streaming enabled; port in game Settings must match `GAME_WS_URL` in `server.py` (default 5001).
- **"Not enough points"** — Raise balance in **points-config** or add a line to `viewer_points.txt` (`username|total|0|0|`).
- **Paths in Streamer.bot** — Python and C# actions often use absolute paths from the export author. Update to your `Lastest UI` folder.
- **Imported Streamer.bot profile: commands fire but game ignores** — Recreate **WebSocket Client** connection to `ws://127.0.0.1:<game port>`; confirm the game’s streaming toggle is on.
- **Helper floor points not awarding** — Requires helpers/hurters system **on** (no `helpers_hurters_disabled.txt` lockout), `points_per_helper_on_new_floor` greater than `0` in `points_config.json`, and **descending** to a deeper floor (ascending does not grant).
