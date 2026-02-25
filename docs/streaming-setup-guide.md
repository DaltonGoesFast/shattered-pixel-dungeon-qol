# Streaming Setup Guide (for Collaborators)

This guide helps you emulate the full streaming setup so you can test and verify changes to the overlay, points system, and chat spawn features.

---

## Prerequisites

| Component | Purpose |
|-----------|---------|
| **Python 3.8+** | Runs the overlay server and `spawn_with_points.py` |
| **Shattered Pixel Dungeon QoL** | The game (this mod) with streaming enabled |
| **Streamer.bot** | Connects chat to actions (optional for basic testing) |
| **OBS Studio** | Displays the overlay; optional for API testing |

---

## 1. Overlay Server

The server provides `/api/game-data`, `/api/spawn-command`, and serves the overlay.

```bash
cd "Lastest UI"
pip install -r requirements.txt
python server.py
```

**Configure paths in `server.py`:**
- `SAVE_DIRECTORY` — Where SPD saves are stored. Default: `C:\Users\dalto\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon QoL`
- On your machine, find your SPD save folder (e.g. under `%APPDATA%` or `~/.shatteredpixel/`)

Server runs at `http://localhost:5000`. You should see:
```
SPD Overlay Server Starting...
Server URL: http://localhost:5000
Game WebSocket: ws://127.0.0.1:5001
```

---

## 2. Game (Streaming Enabled)

1. Build and run the desktop game from this repo.
2. In-game: **Settings** → enable **Streaming** (or similar).
3. The game opens a WebSocket on port **5001** and sends live game state.

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

### Spawn (via Python)
```bash
cd "Lastest UI"
python spawn_with_points.py rat YourUsername
```
- Requires: overlay server running, game running with streaming, `viewer_points.txt` with enough points.
- Creates `viewer_points.txt` if missing. Add a line manually: `yourusername|100|0` to give yourself points.

### Spawn (via HTTP)
```bash
curl -X POST http://localhost:5000/api/spawn-command -H "Content-Type: application/json" -d "{\"monster\": \"rat\", \"username\": \"test\"}"
```
- This bypasses the points check (points are enforced in `spawn_with_points.py`, not the server). Use for testing spawn delivery only.

---

## 4. Full Setup (Streamer.bot + OBS)

For full chat integration:

1. **Streamer.bot** — Connect to Twitch or YouTube.
2. **OBS** — Add Browser Source: `http://localhost:5000`
3. **Points system** — Follow [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md) to create the actions.

**Paths to update** (if your project lives elsewhere):
- All `FILE`, `DOUBLE_FILE`, `TOP_FARDER_FILE` in the C# code
- `spawn_with_points.py` uses `SCRIPT_DIR` (same folder as the script) for `viewer_points.txt`

---

## 5. Minimal Test Checklist

| Test | What to do | Expected |
|------|------------|----------|
| Server starts | `python server.py` | No errors, "Server URL" printed |
| Game data | `curl http://localhost:5000/api/game-data` | JSON with `stats`, `hero`, etc. or 404 if no game |
| Spawn script | Create `viewer_points.txt` with `testuser|50|0`, run `python spawn_with_points.py rat testuser` | `ok` in `spawn_result.txt` if game is running and has space |
| Half-price | Be in prison (depth 6+), spawn rat | Cost should be 2–3 (half of 5) |

---

## Troubleshooting

- **"No game data available"** — Game not running, or save directory wrong. Check `SAVE_DIRECTORY` in `server.py`.
- **"Game not connected"** on spawn — Game must be running with streaming enabled on port 5001.
- **"Not enough points"** — Add your username to `viewer_points.txt` with enough points: `username|100|0`
- **Paths in C#** — Streamer.bot C# uses absolute paths. Update them for your machine.
