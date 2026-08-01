# Project Structure

Quick reference for what each part of the project does.

## Documentation map (July 2026)

**Start here for streaming:** [streaming-setup-guide.md](streaming-setup-guide.md) — full index: [README.md](README.md)

| If you need… | Read |
|--------------|------|
| Viewer command list | [COMMANDS.md](../COMMANDS.md) |
| Copy for panels / YouTube | [user-facing-summary.md](user-facing-summary.md), [twitch-panel.md](twitch-panel.md), [youtube-description.md](youtube-description.md) |
| Build Streamer.bot (R1–R10) | [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md) + C# in `Lastest UI/streamerbot/phase2/` |
| Import maintainer bot | `Lastest UI/streamerbot/shatter-the-streamer-export-0.2.0` |
| `!kesha`, `!seed`, `!mimic`, `!challenge` (not R1) | [stream-info-commands.md](stream-info-commands.md) |
| Economy rules (cap, bank, 4× donations) | [Chat Command Economy v1.md](Chat%20Command%20Economy%20v1.md) |
| Implementation history / phases | [streaming-system-rework-plan.md](streaming-system-rework-plan.md) |
| Test without chat | `Lastest UI/test_chat_command_api.ps1`, `Lastest UI/phase3_rapid_test.ps1` |
| Old ~40-action Streamer.bot | [archive/README.md](archive/README.md) |

## Root

| Item | Purpose |
|------|---------|
| `core/` | Game source code (Java) |
| `desktop/` | Desktop launcher, streaming server, command handler |
| `docs/` | Documentation |
| `Lastest UI/` | Overlay server, points scripts, Streamer.bot exports, OBS assets |
| `spd-comp3/` | **Godot 4 companion** (summon marches, Bestiary HUD, alerts) — open in Godot; see `spd-comp3/AGENTS.md` |
| `scripts/` | Repo helper scripts (optional) |
| `watcher.py` | Debug utility — connects to game WebSocket, prints hero updates. Optional. |
| `android/`, `ios/`, `gradle/`, `SPD-classes/`, `services/` | SPD base project (unchanged) |

## Lastest UI (overlay & streaming)

### Required for streaming

| File | Purpose |
|------|---------|
| `server.py` | Main overlay server. HTTP API + game WebSocket relay. Run with `python server.py` or `start.bat`. |
| `spd_parser.py` | Save parser. Used by server for game data. |
| `chat_command.py` | Dispatcher for `POST /api/chat-command` (earn, spend, meta, session) |
| `chat_messages.py` | Server-side chat reply strings |
| `summon_bestiary.py` | Bestiary state (bar / sprint / heat / hall) |
| `bestiary_config.json` | Zone unlocks, XP thresholds, heat window |
| `sim_bestiary_chatters.py` | Local sim for Bestiary HUD / chat testing |
| `presentation_config.py` | OBS source names, sounds, GDI paths for API `presentation` |
| `points_command.py` | Spend/query implementation; CLI fallback for local tests |
| `points_config.json` | Costs config (edit here or via http://localhost:5000/points-config) |
| `index.html` | OBS overlay dashboard |
| `double-points-countdown.html` | OBS 2× points countdown |
| `fonts/` | Overlay fonts |
| `requirements.txt` | Python dependencies |
| `start.bat` | Launch script |
| `config.example.json` | Template for paths; copy to `config.json` and edit `save_directory` if needed |

### Runtime files (auto-created, in .gitignore)

`viewer_points.txt`, `bestiary_state.json`, `heat_leader.txt`, `session_state.json`, `spawn_result.txt`, `game_summary.*`, `double_points_end.txt`, `donation_result.txt`, `spend_disabled.txt`, etc. — created when the system runs. Safe to delete; they regenerate.

### Optional / dev tools

| File | Purpose |
|------|---------|
| `ws-inspect.html` | WebSocket inspector |
| `streamerbot/` | Streamer.bot exports (My-Nice-Script_*.cmd), batch helpers (open-ws-inspect.bat) |

### Can remove if not needed

| Item | Notes |
|------|-------|
| `backup/` | Old overlay backup |
| `backup_20251201/` | Older backup |
| `Shattered Pixel Dungeon/` | Copied save data (if present) |

---

## Preparing for GitHub

Your remote is already set: `origin` → [DaltonGoesFast/shattered-pixel-dungeon-qol](https://github.com/DaltonGoesFast/shattered-pixel-dungeon-qol). Most of the noise in `git status` comes from **local installs and runtime files**, not from missing organization.

### Keep outside the repo (`.gitignore`)

| Path | Why |
|------|-----|
| `Streamerbot POST REWORK/` | Full Streamer.bot install (DLLs, WebView2 cache, `data/`, logs). Install the app elsewhere; commit only exports and C# under `Lastest UI/streamerbot/`. |
| `OBS Exports/` | Machine-specific OBS scene/collection JSON. Document layout in `docs/` instead. |
| `Lastest UI/junk/` | Old one-off Streamer.bot `.cmd` exports. |
| `Lastest UI/viewerpoints/` | CSV point ledgers from live streams. |
| Runtime under `Lastest UI/` | Points, spawn results, session/summon/fard counters, `*_body.json` test payloads, locks — see the “Runtime files” section above. |
| `training_exports/` | Local ML/training dumps. |
| `spd-comp3/.godot/`, `spd-comp3/build/`, `*.exe`/`*.pck` | Godot editor cache and exported binaries (source + assets stay in repo). |
| `build/`, `.gradle/` | Gradle outputs. |

### Stage in logical groups (suggested commits)

1. **Game mod** — `core/`, `desktop/`, assets under `core/src/main/assets/` that you changed for QoL/streaming.
2. **Overlay & economy** — `Lastest UI/*.py`, HTML, `points_config.json`, `bestiary_config.json`, tests / sims.
3. **Godot companion** — `spd-comp3/` (project, scripts, assets; not `build/` or `.godot/`).
4. **Streamer.bot integration** — `Lastest UI/streamerbot/phase2/*.cs`, `shatter-the-streamer-export-0.2.0`.
5. **Docs & viewer copy** — `COMMANDS.md`, `README.md`, `docs/*.md` (except gitignored notes).

Use a feature branch before pushing (`CONTRIBUTING.md`): e.g. `git checkout -b dalton/bestiary-companion`.

### Quick sanity check before push

```powershell
cd "path\to\shattered-pixel-dungeon-qol"
git status --short
git diff --stat
```

You should **not** see hundreds of `??` lines under `Streamerbot POST REWORK/` or `OBS Exports/` after updating `.gitignore`.

### Optional cleanup (not required for GitHub)

| Item | Suggestion |
|------|------------|
| Folder name `Lastest UI/` | Typo for “Latest”; renaming is a large path churn — fine to keep until a dedicated refactor. |
| `free_until.json` | Runtime (Stream Deck free promos). Currently tracked; consider `git rm --cached "Lastest UI/free_until.json"` and adding it to `.gitignore`, keeping `{}` as the default when the file is missing. |
| `Lastest UI/streamerbot/README-backup.md` | Local notes; delete or gitignore if not needed publicly. |
| Root `watcher.py` | Optional WebSocket debug tool; commit if you use it, otherwise ignore. |

### What collaborators need

- Clone repo, build desktop per `docs/getting-started-desktop.md`.
- Copy `Lastest UI/config.example.json` → `config.json` and set `save_directory`.
- Import Streamer.bot from `Lastest UI/streamerbot/shatter-the-streamer-export-0.2.0` (or follow `docs/streaming-setup-guide.md`).
- Open **`spd-comp3/`** in Godot 4 for the companion overlay (optional).
- Install **Streamer.bot separately** — do not copy `Streamerbot POST REWORK/` from your machine.
