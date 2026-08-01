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
| **Godot companion** (optional) | **`spd-comp3/`** in this repo — polls `/api/summon-march` + `/api/bestiary` |

---

## 1. Overlay Server

The Flask server exposes **`/api/chat-command`** (unified chat router), **`/api/session/reset`** and **`/api/session/end`**, donation routes under **`/api/donation/*`**, **`/api/game-data`**, **`/api/points-config`** (GET/POST), viewer-points APIs, double-points endpoints, **`/api/activity-commands`**, **`/api/summon-march`** (Godot companion queue), **`/api/top-summoner`**, and legacy per-command HTTP routes kept for tools/tests. See `Lastest UI/server.py` or open **`http://localhost:5000/ws-inspect`** for a combined inspector.

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
| `/points-config` | Costs (including heal/cleanse/dew/corrupt ally/hex/degrade/sabotage), viewer points table, streamer vs chat score |
| `/double-points-countdown` | Optional OBS text source for 2× countdown |
| `/api/summon-march` | Godot companion: poll queued monster marches (`GET ?since=id`) |
| `/api/top-summoner` | Bestiary **sprint** leader for OBS |
| `/api/heat-leader` | Rolling heat leader (personal 2×) |
| `/api/bestiary` | Bestiary HUD payload (bar / sprint / heat / hall) |
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

You can test most functionality without Streamer.bot.

### API smoke test (recommended)

```powershell
cd "Lastest UI"
python server.py   # separate terminal
.\test_chat_command_api.ps1
```

Expect **9 passed** (`/api/chat-command`, session reset, donations, earn, spend errors).

### Rapid scenarios (optional)

```powershell
.\phase3_rapid_test.ps1 -Scenario All          # both direct API + Streamer.bot HTTP
.\phase3_rapid_test.ps1 -Scenario SpawnStorm   # parallel !spawn rat stress
```

Requires Streamer.bot **HTTP server** on `127.0.0.1:7474` for `-Mode StreamerBot` steps. See [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md).

### Overlay / game data
```bash
curl http://localhost:5000/api/game-data
curl http://localhost:5000/game_summary.json
```

### Spawn / spend (CLI fallback)

```bash
cd "Lastest UI"
python points_command.py spawn rat YourUsername
```

- Requires overlay server, game running with streaming, and balance in `viewer_points.txt` (or points-config UI).
- **Live stream path:** Streamer.bot **R1** → `POST /api/chat-command` (no `spawn_result.txt`).

Each line in `viewer_points.txt`: `username|chatPts|lastActivityUnix|donorPts|role` (fifth column legacy; unused). Example: `yourusername|100|0|0|`

### Summon march (Godot companion — no game required)
```bash
curl -X POST http://localhost:5000/api/summon-march -H "Content-Type: application/json" -d "{\"username\": \"test\", \"monster\": \"rat\"}"
curl "http://localhost:5000/api/summon-march"
curl http://localhost:5000/api/top-summoner
```
- Requires overlay server only (game can be offline). Events append to `Lastest UI/summon_march_queue.jsonl`.
- Streamer.bot setup: [summon-march-system.md](summon-march-system.md) (`!summon` via R1). Bestiary rules: [bestiary-summon-system.md](bestiary-summon-system.md). Legacy separate action: [streamerbot-summon-march-apply.md](streamerbot-summon-march-apply.md) (archived).
- Viewer-facing copy: [user-facing-summary.md](user-facing-summary.md), [youtube-description.md](youtube-description.md), [twitch-panel.md](twitch-panel.md), [COMMANDS.md](../COMMANDS.md).

---

## 4. Full Setup (Streamer.bot + OBS)

**Status:** Live setup is the **HTTP gateway (R1–R10)** with **Economy v1.1** on the server. Roadmap and phase history: [streaming-system-rework-plan.md](streaming-system-rework-plan.md).

| Action | Role |
|--------|------|
| **R1** | All points chat → `POST /api/chat-command` |
| **R2** | First Words OBS only (points on server) |
| **R3** | Passive earn batch → HTTP |
| **R4–R6** | Donations |
| **R7** | Twitch Stream Started → `/api/session/reset` |
| **R8** | Spend on/off (`spend_disabled.txt`) — two actions for Stream Deck |
| **R9** | Presentation queue (`!fard` OBS; optional summon sound in R1) |
| **R10** | Stream Offline → `/api/session/end` (debounced chat wipe + auto-bank) |

**Separate Command actions (not R1):** `!kesha`, `!seed`, `!mimic`, `!challenge` — [stream-info-commands.md](stream-info-commands.md).

### 4.1 Option A — Import export (fastest)

- **Current:** `Lastest UI/streamerbot/shatter-the-streamer-export-0.2.0`
- **Legacy (~40 actions):** `shatter-the-streamer-export-0.1.0.txt` — do not use for new setups

After import:

1. Fix **absolute paths** in curl steps (see [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md) § R1 `chat_command_body.json`).
2. **OBS** must be connected in Streamer.bot (presentation and GDI steps fail silently otherwise).
3. **Game WebSocket** `ws://127.0.0.1:5001` if any action still talks to the game directly (most spend goes through the overlay server).
4. Run `test_chat_command_api.ps1` and one live `!spawn` / `!fard` smoke test.

### 4.2 Option B — Build from scratch

Follow [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md) and paste C# from `Lastest UI/streamerbot/phase2/`.

Do **not** build new setups from the archived walkthrough in [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md) (kept for reference only).

### 4.3 OBS

- Add a **Browser Source**: `http://localhost:5000/overlay`
- Optional: `http://localhost:5000/double-points-countdown` for 2× points text, or read `streamer_chat_score.txt` / use `/api/streamer-chat-score` per your layout.

#### Vertical layout: dynamic inventory crop (item info popup)

When the overlay server and game streaming are running, `server.py` drives OBS **Crop/Pad** and the inventory **mask** directly over OBS WebSocket (no Advanced Scene Switcher macros required).

1. Copy `Lastest UI/obs_inv_layout.example.json` to `Lastest UI/obs_inv_layout.json` and set source/filter names to match your scene (`V - INV HUD GROUP`, `V - INV HUD`, etc.).
2. Enable **WebSocket Server** in OBS (default `ws://127.0.0.1:4455`, no password).
3. Enable **WebSocket streaming** in game **Settings → Data** and run `python server.py`.
4. **Disable** old ASS macros (`INV EXPAND UP`, `INV EXPAND UP 2`) so they do not fight the relay.

The game sends immediate `ui_layout` events with popup bounds; the server expands the crop only when the item info window overlaps the cropped HUD strip, and only as much as needed.

**Tuning:** Crop/Pad `top` on `V - INV HUD GROUP`: `crop_top_closed` (**683**), `crop_top_min` (**390** for kinetic staff). Popups with `height` ≤ `no_expand_max_height` (**100**, Cursed Metal Shard size) keep closed crop and mask on. Larger popups use the linear `top` map and disable the mask. Log: `OBS inv crop: open top=... (game top=... height=...)`. Copy `obs_inv_layout.example.json` to `obs_inv_layout.json` for your own values. Set `"enabled": false` to turn the relay off.

**Paths to update** on a new machine:

- R1 **curl** path to `chat_command_body.json` and **Working directory** (`Lastest UI`) — [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md)
- `Lastest UI/presentation_config.py` if OBS scene names differ
- `Lastest UI/config.json` → `save_directory`

---

## 5. Minimal Test Checklist

| Test | What to do | Expected |
|------|------------|----------|
| Server starts | `python server.py` | No errors, "Server URL" printed |
| Points config UI | Open `http://localhost:5000/points-config` | Page loads; Save writes `points_config.json` |
| Game data | `curl http://localhost:5000/api/game-data` | JSON with `stats` (includes `depth`), `hero`, etc. when game is in a run and WS connected |
| Spawn script | `python points_command.py spawn rat testuser` (CLI) or `POST /api/chat-command` with `!spawn rat` | Game spawns when WS connected; JSON `ok` from API |
| Spawn zone pricing | Deeper than native depth: half price; shallower: chapter-gap surcharge (+20% per step on base, max +40%; see `compute_spawn_cost` / `_early_spawn_multiplier` in `points_command.py`) | Costs match script when `/api/game-data` reports depth |
| Streamer.bot import | After import, trigger one command + check game | No path errors; OBS connected for presentation |

---

## 6. Pre-stream checklist (go live)

| Check | How |
|-------|-----|
| Overlay server | `python server.py` in `Lastest UI` — port **5000** |
| API smoke | `.\test_chat_command_api.ps1` → 9 passed |
| Game | Desktop build, **Streaming** on, WebSocket **5001** (default) |
| Streamer.bot → OBS | **Connected** (fard/kesha OBS fail silently otherwise) |
| R1–R10 | Enabled; legacy ~40-action commands **disabled** |
| R7 | Twitch **Stream Started** → `/api/session/reset` |
| R10 | **Stream Offline** → curl `/api/session/end` — [economy-v11-apply.md](economy-v11-apply.md) |
| R8 | Stream Deck spend on/off wired |
| Stream info | `!kesha`, `!seed`, `!mimic`, `!challenge` — [stream-info-commands.md](stream-info-commands.md) |
| Godot companion | Polls `/api/summon-march` if used |
| Points backup | CSV export from `/points-config` before wipes |
| After stream | Keep server up for debounced R10 **or** force session end before next stream |

Optional: `.\phase3_rapid_test.ps1 -Scenario Default` (Streamer.bot HTTP on `7474`).

---

## 7. Troubleshooting

- **"No game data available"** — Game not running, or save directory wrong. Check `save_directory` in `config.json`.
- **"Game not connected"** on spawn — Game must be running with streaming enabled; port in game Settings must match `GAME_WS_URL` in `server.py` (default 5001).
- **"Not enough points"** — Raise balance in **points-config** or add a line to `viewer_points.txt` (`username|total|0|0|`).
- **Paths in Streamer.bot** — Update R1 curl path to your clone’s `Lastest UI` folder (see apply guide).
- **Fard / kesha: sound works, no OBS flash** — Streamer.bot was not connected to OBS; connect and retry.
- **Session end did nothing** — `POST /api/session/end` returns `debounce` until the timer fires; keep `server.py` running or use `force: true` (see [economy-v11-apply.md](economy-v11-apply.md)).
- **Chat commands fire but spawns do not appear** — Game **Streaming** must be on; overlay relays to `ws://127.0.0.1:5001`. R1 does not require a separate Streamer.bot game WebSocket.
