# WebSocket Streaming Plan – OBS, Streamer.bot, Custom Apps

This document outlines how to add **in-game WebSocket streaming** so the game pushes the same gameplay data your save-file parser exports, in real time, to OBS, Streamer.bot, and your custom program—without polling save files.

---

## 1. Goal

- **Current setup:** Python server watches save files, parses `game.dat` / `depthN.dat`, builds `game_summary.json` (hero, equipped, inventory, stats, challenges, talents, quests, buffs, combat, feeling, etc.), serves it over HTTP. OBS uses a Browser Source; other tools read the JSON.
- **Target:** The **game itself** runs a small WebSocket server and pushes the **same JSON shape** (or a superset) whenever game state changes (or on a short interval). Clients connect once and receive live updates.

Benefits:

- No dependency on save frequency or file watchers.
- Real-time (e.g. HP, depth, gold update as they change).
- One integration point: everything connects to the game’s WebSocket.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Shattered Pixel Dungeon (Desktop)                               │
│  ┌──────────────┐     ┌─────────────────────┐                    │
│  │ Game loop    │────▶│ GameStateSnapshot   │                    │
│  │ (main thread)│     │ (core; builds JSON) │                    │
│  └──────────────┘     └──────────┬──────────┘                    │
│                                  │                                │
│                                  ▼                                │
│  ┌──────────────────────────────────────────────────────────────┐
│  │ WebSocketServer (desktop only; background thread)              │
│  │  - Listens on localhost:PORT                                   │
│  │  - Broadcasts latest JSON to all connected clients             │
│  └──────────────────────────────────────────────────────────────┘
└─────────────────────────────────────────────────────────────────┘
         │
         │ WebSocket (e.g. ws://127.0.0.1:5001)
         ▼
┌─────────────┐  ┌─────────────────┐  ┌────────────────────────────┐
│ OBS         │  │ Streamer.bot    │  │ Your custom program        │
│ Browser     │  │ (WS client)     │  │ (e.g. Python WS client or   │
│ Source      │  │                 │  │  relay to existing UI)     │
│ (HTML+JS)   │  │                 │  │                            │
└─────────────┘  └─────────────────┘  └────────────────────────────┘
```

- **Core (shared):** Builds the “game state” payload (same structure as your parser’s `game_summary.json`).
- **Desktop only:** Runs the WebSocket server and sends that payload to all connected clients.
- **Android:** No WebSocket server; feature disabled or hidden on mobile.

---

## 3. Data Contract – Match Your Parser

Keep the JSON shape **compatible** with what `spd_parser.py` produces so your existing overlay and tools need minimal changes. Example (same as your `game_summary.json`):

- **hero:** class, subclass, hp, ht, exp, lvl, str  
- **equipped:** weapon, armor, artifact, ring, misc (each: name, level, enchantment/glyph if applicable)  
- **inventory:** list of { name, quantity, level }  
- **stats:** depth, max_depth, gold, energy, score, enemies_slain, food_eaten, potions_cooked, ankhs_used  
- **challenges:** list of active challenge names  
- **buffs:** list of buff class names  
- **talents:** tier1..tier4 with names and levels  
- **quests:** e.g. quest name → { completed, … }  
- **combat_stats:** sneak_attacks, thrown_assists, hazard_assists  
- **identification:** potions, scrolls, rings (for overlay)  
- **feeling:** current floor feeling (from level)  
- **seed:** run seed string  
- **duration:** turns played  
- **won / ascended:** booleans  
- **upgrades_used:** if available in-game  

Anything your parser currently exposes can be added so the WebSocket payload is a **superset** of the current JSON.

---

## 3a. UI / Context State (scene and open windows)

So OBS, Streamer.bot, and your custom program can react to **what the player is looking at**, the WebSocket payload should include a small **UI/context** block that is always present (even when there is no active run). This is in addition to the gameplay data from §3.

### Scene (where the player is)

A single field indicating the **current scene** (screen), e.g.:

| Value | Meaning |
|-------|--------|
| `title` | Title screen (main menu) |
| `start` | StartScene (new game / continue) |
| `hero_select` | Choosing hero class |
| `game` | Main gameplay (GameScene; dungeon, moving, fighting) |
| `interlevel` | Between levels (stairs, loading next floor) |
| `alchemy` | Alchemy screen (AlchemyScene; pot, recipes, energy) |
| `journal` | Standalone journal (JournalScene, e.g. from title) |
| `rankings` | Rankings / high scores |
| `surface` | Surface (after winning, before ascend) |
| `amulet` | Amulet / victory flow |
| `news` | News / updates |
| `changes` | Changelog |
| `about` | About screen |
| `supporter` | Supporter scene |
| `welcome` | First-time welcome |

Implementation: from the **main thread**, call `ShatteredPixelDungeon.scene()` and use `instanceof` (or a small map from class to string) to set a `scene` string in the snapshot. No WebSocket or desktop-only code in core—just reading the current scene class.

### Open windows (when applicable)

When the current scene is one where windows can be open (e.g. **game** or **title**), include a list (or object) of **which major windows are currently open**. Clients can use this to switch overlays (e.g. “hide gameplay stats when journal is open”) or trigger actions (e.g. “alchemy screen shown”).

Suggested fields (booleans or a list like `open_windows: ["journal", "inventory"]`):

| Window | How to detect (core) |
|--------|----------------------|
| **journal** | `WndJournal.INSTANCE != null` (and visible / in scene if needed) |
| **inventory** | `WndBag.INSTANCE != null` (covers both full bag and quick bag, which sets `WndBag.INSTANCE`) |
| **alchemy** | Not a window—use `scene == "alchemy"` (AlchemyScene) |
| **settings** | No global INSTANCE; optional: scan scene for a `WndSettings` in the stage, or omit until we add a static ref |
| **hero_info** | `WndHeroInfo` / hero window—optional; add if there is or we add an INSTANCE |
| **game_menu** | WndGame (pause/menu in-game)—optional |

Implementation: in the same snapshot builder (main thread only), if the current scene is `GameScene` or `TitleScene`, set e.g. `open_windows` to a list of strings like `"journal"`, `"inventory"` by checking the corresponding `Wnd*.*.INSTANCE != null`. For windows that use `INSTANCE` and clear it on hide, that check is enough; if needed we can also verify `INSTANCE.visible` or `getParent() != null` so we don’t report a window as open after it’s been closed in the same frame.

### Suggested JSON shape for UI/context

Always include something like:

```json
"ui": {
  "scene": "game",
  "open_windows": ["inventory"]
}
```

When not in a scene where windows are tracked, `open_windows` can be omitted or `[]`. When there is no run (e.g. title screen), the rest of the payload can omit or null out gameplay fields, but `ui.scene` (and optionally `ui.open_windows`) should still be present so clients can always know current screen and key windows.

This allows:

- **OBS:** Switch overlay layout or sources based on `ui.scene` and `ui.open_windows` (e.g. different layout when journal or alchemy is open).
- **Streamer.bot:** Triggers like “scene changed to alchemy”, “inventory opened”, “back to gameplay”.
- **Custom program:** Same logic as above; can mirror or extend your parser’s usage.

---

## 4. Implementation Phases

### Phase 1 – Snapshot in core (no WebSocket yet)

- Add a class in **core** (e.g. `com.shatteredpixel.shatteredpixeldungeon.utils.GameStateSnapshot` or `streaming.StreamingSnapshot`).
- Implement a static method that:
  - **Always** builds the **UI/context** block (§3a): set `scene` from `ShatteredPixelDungeon.scene()` (e.g. title, game, alchemy, hero_select, rankings, interlevel, …) and when on GameScene or TitleScene set `open_windows` from `WndJournal.INSTANCE`, `WndBag.INSTANCE`, etc.
  - Returns `null` (or minimal payload with only `ui`) when `Dungeon.hero` or `Dungeon.level` is null (title screen, loading, etc.).
  - Otherwise builds a map/list structure matching the parser’s `game_info` using:
    - `Dungeon.hero` (HP, HT, exp, lvl, STR, class, subclass, buffs, talents, belongings, inventory).
    - `Dungeon.level` (depth, feeling).
    - `Statistics` (gold, score, depth, enemies slain, etc.).
    - `Challenges` (bitmask → list of names, same as parser).
    - Items: names from class names, levels, enchantment/glyph where applicable.
  - Returns either a `JsonObject`/`JsonValue` (if you add a small JSON lib in core) or a `Map<String, Object>` / list types that **desktop** will serialize to JSON. Prefer a format that’s easy to convert to the exact parser JSON (e.g. same keys and number types).
- **No WebSocket dependency in core.** Core only knows “build snapshot”; desktop will turn it into a string and send it.

### Phase 2 – WebSocket server on desktop only

- Add a **WebSocket dependency** only in the **desktop** module (e.g. [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) or Jetty WebSocket). Example in `desktop/build.gradle`:
  - `implementation 'org.java-websocket:Java-WebSocket:1.5.4'`
- Implement a small **WebSocket server** that:
  - Binds to `127.0.0.1` and a **configurable port** (e.g. 5001 so it doesn’t clash with your Flask 5000).
  - Runs in a **dedicated background thread** (don’t run it on the libGDX main thread).
  - Keeps a **thread-safe “last payload”** (e.g. `volatile String lastJson` or `AtomicReference<String>`).
- **Main thread (game loop):**
  - On a **timer** (e.g. every 1 second when in-game), call the core snapshot API, serialize to JSON (in desktop, e.g. with Gson or the same library you use elsewhere), and set `lastJson`.
- **WebSocket thread:**
  - On a **timer** (e.g. every 1 s) or immediately when someone connects, broadcast `lastJson` to all connected clients.
  - Optionally: send on connect so new clients get the current state right away.
- Start the server when the **game scene** is active (e.g. when entering `GameScene` with a non-null hero); stop or pause when returning to title or closing.
- **No server on Android:** only start this in the desktop launcher / desktop-specific code path.

### Phase 3 – Settings and safety

- **Settings (SPDSettings + WndSettings):**
  - “Streaming / OBS integration” or “WebSocket streaming”:
    - Checkbox: **Enable WebSocket** (default off for safety).
    - Port (e.g. 5001), only used when enabled.
  - Store in preferences; read when starting/stopping the server.
- **Thread safety:**
  - Snapshot is built only on the **main thread** (inside the game update).
  - Only the **result** (JSON string) is handed off to the WebSocket thread; no shared mutable game state on the WS thread.
- **Graceful shutdown:** When the game exits, close the WebSocket server and release the port.

### Phase 4 – Clients (OBS, Streamer.bot, custom)

- **OBS:** New Browser Source that:
  - Connects to `ws://127.0.0.1:PORT` (your configured port).
  - On each message, parse JSON and update the overlay (same layout as now, but data from WebSocket instead of HTTP).
  - You can keep your existing `index.html` styling and logic and only change the data source from “poll `/game_summary.json`” to “on WebSocket message”.
- **Streamer.bot:** Use its WebSocket client to connect to the same URL; trigger actions from parsed fields (e.g. “depth changed”, “HP below 25%”, “boss floor”).
- **Custom program:** Connect to the same WebSocket; receive the same JSON. Optionally keep your Python server as a **relay** (Python connects to the game’s WS and still serves HTTP/WS to OBS if you prefer), or point OBS/Streamer.bot directly at the game.

---

## 5. Where to Hook “Send Snapshot”

- **Option A – Time-based (simplest):** In `GameScene` or the main game update, if `Dungeon.hero != null`, every N seconds call “build snapshot and set lastJson”. N = 1 is a good default (matches your current 1 s poll).
- **Option B – Event-based:** On depth change, HP change, inventory change, etc., call “build snapshot and set lastJson”. Can be more responsive but needs more hooks; can combine with a short throttle (e.g. at most once per 0.5 s).

Recommendation: start with **Option A** (e.g. 1 s) and add **Option B** later if you want instant updates for specific events.

---

## 6. File / Module Layout (suggested)

- **core:**  
  - `.../utils/GameStateSnapshot.java` (or `.../streaming/GameStateSnapshot.java`)  
  - Builds the snapshot; no WebSocket, no desktop-only code.

- **desktop:**  
  - `.../desktop/StreamingServer.java` (or similar)  
  - WebSocket server thread, reads “last JSON” and broadcasts.  
  - Startup: from `DesktopLauncher` or from a desktop-only “game lifecycle” hook when entering GameScene.  
  - Shutdown: when app exits or when returning to title (optional).

- **core (settings):**  
  - Add keys in `SPDSettings` for “WebSocket streaming enabled” and “WebSocket port”.  
  - Add UI in `WndSettings` (e.g. under a “Streaming” or “Advanced” tab) so the user can enable/disable and set the port.

- **Lastest UI (your folder):**  
  - Add a small `overlay_ws.html` (or update `index.html`) that uses WebSocket to `ws://127.0.0.1:PORT` and renders the same layout.  
  - Optional: a tiny Python/Node script that connects to the game’s WebSocket and still writes `game_summary.json` for backward compatibility.

---

## 7. Dependencies Summary

| Component              | Where   | Dependency / note                                      |
|------------------------|--------|--------------------------------------------------------|
| Snapshot building      | core   | None (use only JDK + existing SPD classes).           |
| JSON serialization     | desktop| Gson (if not already there) or minimal JSON in core.  |
| WebSocket server       | desktop| Java-WebSocket or Jetty WebSocket (desktop only).     |
| Android                | —      | No WebSocket; feature not compiled or hidden on Android. |

---

## 8. How to Inspect WebSocket JSON

**Prerequisites:** In-game **Settings**, enable **WebSocket streaming** and note the port (default **5001**). Start or continue a game so the server is running.

- **Browser inspector (easiest):** Open `Lastest UI/ws-inspect.html` in a browser. Set the port if you changed it, click **Connect**. You get the live snapshot as a **Tree** (expand/collapse keys) or **Raw JSON**. Data updates every second.
- **Browser console:** Open DevTools (F12) → Console. Run:
  ```js
  const ws = new WebSocket('ws://127.0.0.1:5001');
  ws.onmessage = e => console.log(JSON.parse(e.data));
  ```
  Replace `5001` with your port. You'll see the full object in the console each time the game pushes an update.
- **Python (one-off print):**
  ```py
  import json, websocket
  ws = websocket.create_connection('ws://127.0.0.1:5001')
  print(json.dumps(json.loads(ws.recv()), indent=2))
  ws.close()
  ```
  Or loop `ws.recv()` to watch updates. Requires `websocket-client`.

The payload is the same structure as `game_summary.json` (hero, equipped, inventory, identification, stats, challenges, won, ascended, seed, duration, upgrades_used, combat_stats, buffs, talents, quests, feeling), plus a top-level **`ui`** object with `scene` and (when applicable) `open_windows`.

---

## 9. Testing Order

1. **Phase 1:** In desktop, from the game loop, call the core snapshot once per second and write it to a file (e.g. `game_summary_live.json`). Compare with your parser’s `game_summary.json` after a save. Align fields until they match.
2. **Phase 2:** Start the WebSocket server; connect with a simple test client (e.g. browser console or a small Python script) and confirm you receive the same JSON.
3. **Phase 3:** Add settings; restart game; confirm server only runs when enabled and on the chosen port.
4. **Phase 4:** Point OBS Browser Source at a new overlay that uses WebSocket; point Streamer.bot and your custom program at the game’s WebSocket and verify behavior.

---

## 10. Optional: Backward Compatibility With Your Parser

- Keep your Flask server and parser as an **optional** path: if the game’s WebSocket is enabled, your Python could connect as a client and still write `game_summary.json` (and serve it over HTTP) so existing OBS setups that poll HTTP keep working without change.
- Or migrate OBS/Streamer.bot/custom app fully to the game’s WebSocket and retire the file-based parser for live streaming once you’re happy.

---

## 11. OBS Scene Switching (title ↔ gameplay)

To switch OBS scenes when the game goes from menu to gameplay (or back):

1. **Enable OBS WebSocket server**  
   In OBS: **Tools → WebSocket Server Settings** (or **Settings → Advanced → WebSocket**). Enable the server; note the port (default **4455**). If you set a password, either disable it for local use or use a script that supports OBS 5 authentication.

2. **Create two scenes in OBS**  
   e.g. **Title** (menu/game capture off or static) and **Gameplay** (game capture).

3. **Run the scene switcher**  
   Open `Lastest UI/obs-scene-switcher.html` in a browser (or add it as an OBS **Browser Source** and check “Refresh browser when scene becomes active” / run it in a separate window). Set:
   - **Game port** to the game’s streaming port (e.g. 5002).
   - **OBS port** to OBS WebSocket port (e.g. 4455).
   - **Scene when in menu** = your menu scene name (e.g. `Title`).
   - **Scene when in gameplay** = your gameplay scene name (e.g. `Gameplay`).

4. **Start the game** with WebSocket streaming enabled. The page connects to both the game and OBS; when `ui.scene` changes between menu (title, start, hero_select, …) and gameplay (game, interlevel, alchemy, …), it sends `SetCurrentProgramScene` to OBS so the correct scene is shown.

**If OBS asks for a password:** turn off “Enable authentication” in OBS WebSocket Server Settings for local use, or the switcher would need to implement OBS 5 auth (password + salt).

---

## 12. Summary

| Step | What |
|------|------|
| 1 | Core: `GameStateSnapshot` that builds parser-compatible JSON (or map) from `Dungeon`, `Hero`, `Statistics`, items, challenges, talents, quests, etc., **and** always includes `ui` with `scene` and (when applicable) `open_windows` (§3a). |
| 2 | Desktop: WebSocket server (background thread), configurable port, broadcasts last snapshot JSON every 1 s (and on connect). |
| 3 | Game loop: Every 1 s (and when in-game), build snapshot in core, serialize in desktop, set “last JSON” for the server. |
| 4 | Settings: Enable/disable WebSocket, set port; only start server on desktop when enabled. |
| 5 | Clients: OBS (Browser Source with WS), Streamer.bot (WS client), your program (WS client); all use the same JSON shape and can react to `ui.scene` / `ui.open_windows`. |
| 6 | OBS scene switching: use `Lastest UI/obs-scene-switcher.html` to switch OBS scenes when `ui.scene` goes from menu to gameplay (§11). |

This gives you real-time, in-game streaming that matches your existing parser output and works with OBS, Streamer.bot, and your custom program over a single WebSocket connection.
