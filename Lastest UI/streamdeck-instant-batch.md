# Stream Deck — Instant Batch-PowerShell (template)

Use the **Instant Batch-PowerShell** plugin with **Command Line (Batch)** for streamer-only actions that call Python in `Lastest UI` (no Streamer.bot, no points).

**Related:** `streamer_debug.py` (debug commands), `streamdeck-free-commands.md` (PowerShell plugin + free promos), `streamerbot/start-overlay-instant-batch.bat` (start overlay server).

---

## Plugin settings

| Setting | Value |
|--------|--------|
| **Console** | Command Line (Batch) |
| **Execute Path** | `C:\temp\` (or any folder — script `cd`s to the project first) |
| **File Name Prefix** | e.g. `spd-heal-all` (helps find temp scripts) |
| **Code** | See template below |

**Do not** put `pause` in button scripts (blocks until you click). Reserve `pause` for long-running actions like starting `server.py`.

---

## Standard template (copy for every new button)

Replace `YOUR_COMMAND` with a `streamer_debug.py` subcommand (e.g. `heal-all`, `ping`) or another script/args.

```batch
@echo off
set "OVERLAY_DIR=C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI"
cd /d "%OVERLAY_DIR%"
python streamer_debug.py YOUR_COMMAND
exit /b %ERRORLEVEL%
```

**Why this shape**

1. **`OVERLAY_DIR`** — Plugin runs from `C:\temp\`, not the repo; always `cd` into `Lastest UI` first.
2. **`cd /d`** — Switches drive + folder (needed if project is not on `C:`).
3. **`python …`** — Uses PATH; same as terminal testing.
4. **`exit /b %ERRORLEVEL%`** — Propagates success/failure to the plugin (optional but useful).

Optional: hide console output with `pythonw` instead of `python` if `pythonw` is on PATH.

---

## Ready-made buttons (`streamer_debug.py`)

**Full heal** (well of healing: full HP, all debuffs, all curses):

```batch
@echo off
set "OVERLAY_DIR=C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI"
cd /d "%OVERLAY_DIR%"
python streamer_debug.py heal-all
exit /b %ERRORLEVEL%
```

**Identify all** / **Reveal map** / **Stairs down** / **Stairs up** — same template; change the last line:

```batch
python streamer_debug.py identify-all
python streamer_debug.py reveal-map
python streamer_debug.py goto-stairs-down
python streamer_debug.py goto-stairs-up
```

**Ping** (overlay ↔ game connection):

```batch
@echo off
set "OVERLAY_DIR=C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI"
cd /d "%OVERLAY_DIR%"
python streamer_debug.py ping
exit /b %ERRORLEVEL%
```

List commands: `python streamer_debug.py help`  
Last result: `streamer_debug_result.txt` in `Lastest UI`.

---

## Interactive give-item window

**Stream Deck button** — paste into plugin (opens CMD, type items there):

```batch
@echo off
set "OVERLAY_DIR=C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI"
start "SPD Give Item" /D "%OVERLAY_DIR%" cmd /k python streamer_give_prompt.py
```

Use **`START /D`** for the folder (paths with spaces). Do **not** use `cmd /k "cd /d \"%OVERLAY_DIR%\" && ..."` — escaped quotes break when the plugin writes a temp `.cmd` file.

Or double-click `streamdeck_give_item.bat` in `Lastest UI`.

**At the `Give>` prompt:**

| Input | Effect |
|-------|--------|
| `Scroll of Upgrade x10` | 10 scrolls |
| `Battle Axe +99` | +99 battle axe |
| `ScrollOfUpgrade` | class name works |
| `Gold x500` | 500 gold |

One-shot from terminal: `python streamer_debug.py give "Potion of Healing x3"`

**Find item names** (no game required for local search; game connected = fuller list):

```batch
python streamer_debug.py search stylus
```

In the give CMD window you can also type: `search arcane`

Failed gives print **Did you mean:** suggestions (game rebuild required for in-game resolver).

**Buffs / debuffs** at `Give>` (game must be in a run):

```text
buff Haste 30
debuff Blindness
search hex
```

CLI: `python streamer_debug.py buff Haste` / `debuff Hex`

---

## Other Python scripts (same template)

Swap the `python` line; keep `OVERLAY_DIR` + `cd`:

```batch
python points_command.py spawn rat YourUsername
python server.py
```

For **`points_command.py`**, viewer points and spend rules apply. For **`streamer_debug.py`**, no points.

---

## Requirements

- Game running, **in an active run**, **Settings → Streaming** enabled.
- **`python server.py`** running in `Lastest UI` (for anything that hits `http://127.0.0.1:5000`).
- New game debug handlers need a **desktop rebuild** after Java changes.

---

## Path on another machine

Edit only **`OVERLAY_DIR`** in each button (or maintain one shared `.bat` in the repo and `call` it from the plugin).
