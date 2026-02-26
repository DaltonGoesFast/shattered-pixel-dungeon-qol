# Project Structure

Quick reference for what each part of the project does.

## Root

| Item | Purpose |
|------|---------|
| `core/` | Game source code (Java) |
| `desktop/` | Desktop launcher, streaming server, command handler |
| `docs/` | Documentation |
| `Lastest UI/` | Overlay server, points scripts, OBS assets |
| `watcher.py` | Debug utility — connects to game WebSocket, prints hero updates. Optional. |
| `android/`, `ios/`, `gradle/`, `SPD-classes/`, `services/` | SPD base project (unchanged) |

## Lastest UI (overlay & streaming)

### Required for streaming

| File | Purpose |
|------|---------|
| `server.py` | Main overlay server. HTTP API + game WebSocket relay. Run with `python server.py` or `start.bat`. |
| `spd_parser.py` | Save parser. Used by server for game data. |
| `points_command.py` | Unified script: spawn, gold, curse, gas, scroll, wand, superchat, cheer |
| `points_config.json` | Costs config (edit here or via http://localhost:5000/points-config) |
| `index.html` | OBS overlay dashboard |
| `double-points-countdown.html` | OBS 2× points countdown |
| `fonts/` | Overlay fonts |
| `requirements.txt` | Python dependencies |
| `start.bat` | Launch script |
| `config.txt` | Save path, etc. |

### Runtime files (auto-created, in .gitignore)

`viewer_points.txt`, `spawn_result.txt`, `game_summary.txt`, `game_summary.json`, `double_points_end.txt`, `donation_result.txt`, etc. — created when the system runs. Safe to delete; they regenerate.

### Optional / dev tools

| File | Purpose |
|------|---------|
| `inspect_hero.py` | CLI: inspect hero from save |
| `inspect_depth.py` | CLI: inspect depth from save |
| `inspect_save.py` | CLI: inspect save keys |
| `spawn_post.py` | CLI: spawn without points (testing) |
| `get_game_seed.py` | CLI: get current game seed |
| `ws-inspect.html` | WebSocket inspector |
| `obs-scene-switcher.html` | OBS scene switcher |

### Can remove if not needed

| Item | Notes |
|------|-------|
| `backup/` | Old overlay backup |
| `backup_20251201/` | Older backup |
| `Shattered Pixel Dungeon/` | Copied save data (if present) |
