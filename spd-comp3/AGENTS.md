# SPD Companion 3 — agent context

## Purpose

Godot **desktop** companion for **Shattered Pixel Dungeon QoL** streaming: **read-only** WebSocket to the game plus a **localhost UDP** channel for **Streamer.bot** hints. **Optional:** **obs-websocket v5** (OBS 30+) can toggle the **title backdrop** vs **identification overlay** from the **program** scene name. Primary on-screen affordances should reflect **game-confirmed** events (e.g. spawn success).

## Export (Windows)

- **`export_presets.cfg`** should keep **`export_filter=all_resources`**. This project loads many textures via runtime paths (`load()`, string-built paths); **Export selected scenes only** omits them and the build looks broken. Opening **Project → Export** can rewrite the preset — if exports regress, confirm the Resources tab is **Export all resources**.
- Preset uses **embed PCK** in the `.exe` so you do not need a separate `.pck` next to the binary.
- If you see a **blank or all-white window** in a release build, turn **Transparent window** off in **Settings → OBS**, save, and re-export (per-pixel transparency can misbehave on some setups until you tune it for OBS).

### GPU / frame pacing

- **Settings → Connection → Max FPS cap**: **`0`** = uncapped (highest GPU). **`60`** (default) or **`30`** usually looks fine on stream and cuts compositing/shader cost; tweak vs OBS/scene complexity.

## OBS WebSocket (optional scene-driven layout)

- **Enable** in OBS: **Tools → WebSocket Server Settings** (plugin **obs-websocket** 5.x). Default URL `ws://127.0.0.1:4455`; set a password if you use one.
- **Companion:** Settings → OBS → enable sync; set **LIVE - PAUSE** and **LIVE - MAIN** substrings (defaults match those names). Per-element show/hide for pause/main/other on Horizontal and Vertical is under **Scene gates**.
- **Behavior:** When the **current program scene** matches that name → **TitleBackdrop** visible, **CanvasLayerID** (ID strip) hidden. Any **other** program scene → title hidden, ID layer shown (inner visibility still follows `id_overlay_enabled` + snapshot when the layer is on).
- **Protocol:** Subscribes to **Scenes** events; uses `GetCurrentProgramScene` on connect and `CurrentProgramSceneChanged` afterward.

## Game WebSocket (authoritative live stream)

- **URL:** `ws://<game_ws_host>:<game_ws_port>` — defaults `127.0.0.1:5001`. Port matches the game **Settings → streaming** value (user-configurable).
- **Outbound from Godot:** none (read-only).
- **Inbound:**
  - **Full snapshots:** large JSON objects without a routing `type` field (same idea as `GameStateSnapshot` / inspector payload). Emitted roughly once per second; new clients may get a snapshot on connect.
  - **Typed messages:** JSON object with top-level string **`type`** (and sometimes **`source`**). Examples referenced in the game repo: **`spawn_result`**, **`ping_result`**, events like **`hero_died`**, **`boss_slain`** — **verify exact strings and fields in Java.**

### Java reference (parent mod repo)

This Godot project lives at **`spd-comp3/`** inside the Shattered Pixel Dungeon QoL mod checkout (`shattered-pixel-dungeon-qol`). Keep Godot resources under `spd-comp3/` so they are **not** imported/exported by the game Gradle project. When coding against Java, use the **parent** tree (one level up):

- `../core/src/main/java/.../GameStateSnapshot.java` — snapshot field list / builder.
- `../desktop/src/main/java/.../StreamingServer.java` — `onMessage`, result `type` values, `broadcastEvent`.
- `../desktop/src/main/java/.../StreamingBootstrapper.java` — when the server starts.
- `../watcher.py` (mod repo root) — optional reference client parsing inbound JSON.

### Commands understood by the mod (`StreamingServer.java` → `onMessage`)

Inbound **`command`** values (game WebSocket; Godot is **read-only** and only **observes** results):

| `command` | Notes |
|-----------|--------|
| `ping` | |
| `spawn` | requires `monster` |
| `champion` | requires `monster` |
| `gold` | optional `amount` (1–100) |
| `gas` | |
| `curse` | requires `slot` |
| `scroll` | optional `args` first element = rune stem (e.g. upgrade HUD art when UDP pending); game `scroll_result` should include `scroll_name` and/or `rune` / `scroll_rune` for sprite lookup |
| `wand` | optional `tier` 0–3 |
| `buff` | |
| `debuff` | |
| `trap` | |
| `bomb` | |
| `transmute` | |
| `summon_bee` | |
| `ward` | |
| `heal` | |
| `cleanse` | |
| `dew` | |
| `corrupt_ally` | |
| `hex` | |
| `degrade` | |
| `sabotage` | |
| `ring_of_wealth` | |

Each response (when `request_id` was provided on the command) is broadcast with a top-level **`type`** ending in **`_result`**, e.g. `spawn_result`, `champion_result`, `gold_result`, `gas_result`, `curse_result`, `scroll_result`, `wand_result`, `buff_result`, `debuff_result`, `trap_result`, `bomb_result`, `transmute_result`, `summon_bee_result`, `ward_result`, `heal_result`, `cleanse_result`, `dew_result`, `corrupt_ally_result`, `hex_result`, `degrade_result`, `sabotage_result`, `ring_of_wealth_result`, `ping_result`. Payloads include **`success`**, optional **`error`**, and optional detail fields such as **`monster`**, **`gas_name`**, **`item_name`**, etc.—see Java for exact keys.

### Correlation (UDP + WebSocket)

- Streamer.bot may send JSON with **`request_id`** (string or int) and **`command`** / **`args`**.
- If `spawn_result` (or similar) in Java includes **`request_id`**, prefer matching pending UDP rows to confirmed results.
- If the mod does **not** echo `request_id`, the companion falls back to **dropping the oldest pending** alert on any successful `spawn_result` (weak correlation).

### Optional HTTP fallback

When enabled in `user://companion_settings.cfg`, if the WebSocket is down the client may **`GET`** `http://127.0.0.1:5000/api/game-data` (Flask **`Lastest UI/server.py`**) for the **last** snapshot — **not** real-time; polling interval is configurable.


## Streamer.bot JSON (UDP)

Target port: **`<streamerbot_udp_port>`** (default **5100**). Companion binds **`*`** (all interfaces) so Streamer.bot **Core → Network → UDP Broadcast** works. Send **one JSON object per datagram**, **UTF-8**.

### Game commands (alerts)

| Field | Required | Description |
|-------|----------|-------------|
| `source` | no | e.g. `"streamerbot"` (for your logging only). |
| `command` | recommended | Mod command name (see table above), e.g. `spawn`, `gold`, `buff`. |
| `args` | no | For `spawn` / `champion`, first element is the monster hint (or set `monster` instead). |
| `username` | no | Shown in pending subtitle and matched to confirmations when possible. |
| `request_id` | no | If the game echoes this on `spawn_result`, the HUD can pair pending → confirm; otherwise FIFO fallback. |
| `text` | no | Fallback headline when `args` is missing. |

**Example (adapt variable tokens to Streamer.bot):**

```json
{"source":"streamerbot","command":"spawn","args":["rat"],"username":"viewer42","request_id":"%uuid%"}
```

### Paid / highlight notices (companion overlay only)

If the JSON includes a non-empty **`ui`** field, it is **not** treated as a game command. Handled by `PaidNoticeOverlay` (Settings → **Paid notices**).

**Apply guide (triggers, Send UDP, Speaker.bot, migration from OBS):** [`docs/streamerbot-paid-notices-companion-apply.md`](../docs/streamerbot-paid-notices-companion-apply.md).

Streamer.bot group **Companion Commands**: `C01 - Superchat`, `C02 - New Sub/Member`, `C03 - Gifted`.

| `ui` (or `kind`) | Platform | Streamer.bot action / trigger |
|------------------|----------|-------------------------------|
| `superchat` | YouTube | **C01** · Super Chat |
| `membership` | YouTube | **C02** · New Sponsor (C# maps from `companionUi=sub`) |
| `sub` | Twitch | **C02** · Subscription / Resubscription |
| `gifted_membership` | YouTube | **C03** · Membership Gift |
| `gifted_subs` | Twitch | **C03** · Gift Subscription (C# maps from `companionUi=gifted_membership`) |
| `highlight` | Twitch | **None** (no SB action — leave disabled) |

Optional fields: `username`, `message` / `text`, `amount`, `tier`, `months`, `count` / `gift_count`, `ttl_sec` (default from settings, often `6`).

**Testing without Streamer.bot:** from PowerShell you can send a datagram (replace path/tools as needed):

```powershell
$udp = New-Object System.Net.Sockets.UdpClient
$bytes = [Text.Encoding]::UTF8.GetBytes('{"command":"spawn","args":["rat"],"username":"test"}')
$udp.Send($bytes, $bytes.Length, "127.0.0.1", 5100) | Out-Null
$udp.Close()
```

### Streamer.bot `spawn_result.txt` (existing C# inline)

If the overlay writes **`spawn_result.txt`** and your Streamer.bot C# reads it (same pipe format as `spawnResult|scrollName|userPointsRemaining`), you do **not** need to change that C# for the companion:

- The companion treats this file as **status text** (headline + optional points). **Scroll icons** normally come from the game **WebSocket** (`scroll_result` and rune/scroll fields) or **Streamer.bot UDP** `scroll` with `args`.
- Optional **`spawn_result_file_icons`** (default **false**): if your overlay puts a real scroll name / rune in the **middle** field, set this **true** in Settings → Network or in cfg under `[network]`.
- Set **`spawn_result_file_path`** in Settings → Network to the **same absolute path** as `RESULT_FILE` in your inline.
- **`spawn_result_file_poll_sec`** (default **0.15**): how often Godot checks the file. The bridge **never deletes** the file; your C# can still read and delete it.
- **Race:** If C# runs and deletes the file **before** the next poll, Godot may miss one event—use a short poll (e.g. **0.05–0.1**), or add a short **Delay** before the C# action so the companion usually sees the file first.
- Alert **icons** from this file are **off** by default (`spawn_result_file_icons=false`). Turn **on** only if the middle segment reliably carries a scroll/rune or `ScrollOf*` name.

## Placeable chrome boxes (SPD window frames + optional text)

- **Settings → UI panels → Add chrome box** creates a chrome panel on the canvas.
- Edit boxes from **Settings → UI panels**: pick **Layout** (Main or Vertical), then a box, then set **Chrome style**, optional **header / body** text (empty = blank frame), font sizes/colors, align, padding, pixel zone (`x/y/w/h`, height `0` = auto), chrome scale, **Show on Live** / **Show on Pause**.
- Style ids + labels live in `SpdUiArt.CHROME_STYLE_*`; margins match `Chrome.java`. Assets under `assets/ui_spd/chrome/`.
- Max **16** boxes per layout. Persisted as `[ui] chrome_boxes` (main) and `[ui_vertical] chrome_boxes` in `user://companion_settings.cfg`.
- Runtime: `CanvasLayerChromeBoxes` / `scripts/placeable_chrome_boxes.gd` (layer 2) inside each `stream_canvas` instance. When OBS sync is off or disconnected, a box shows if either scene checkbox is on.

## Vertical companion window (1080×1920)

- Second OS window titled **SPD Companion — Vertical** for a dedicated OBS Window Capture (`CAP - COMP V`). Same live feeds as main; own layout profile.
- Project: `display/window/subwindows/embed_subwindows=false` (required — embedding draws the 1080×1920 vertical canvas on top of Main and hides F2).
- Vertical Window sets `content_scale_size=1080×1920` + `CONTENT_SCALE_MODE_CANVAS_ITEMS` so it does **not** inherit the project's 1920×1080 stretch base (wrong base caused overflow / sliced title when moved).
- Opens beside the main window (not centered on top of it).
- Shared canvas scene: [`scenes/stream_canvas.tscn`](scenes/stream_canvas.tscn) instanced under Main and under [`scenes/vertical_companion_window.tscn`](scenes/vertical_companion_window.tscn).
- **F4** toggles the vertical window. Settings → **Vertical**: enable window, per-element show toggles, zones, live-water L, corner pins.
- Config section **`[ui_vertical]`**: same zone keys as main layout plus `show_*` element flags and `vertical_window_enabled`.
- Pause: title+torches centered for portrait; summon march stays visible. Vertical march units force `layout: "vertical"`.
- Free-promos panel on vertical **reads** `FreePromosState` (main window owns the HTTP poll).

## Summon march + Bestiary (Lastest UI HTTP)

- **March queue:** `GET http://127.0.0.1:5000/api/summon-march?since=<id>` — autoload `SummonPollService`.
- **Bestiary HUD:** `GET http://127.0.0.1:5000/api/bestiary` — autoload `BestiaryPollService`; scene `BestiaryHud` (SPD `status_pane` exp bar + chrome).
- **Remote settings:** `GET/POST http://127.0.0.1:5000/api/companion-settings` — autoload `CompanionSettingsPollService`. Enable in Settings → **Remote** (poll on, leave **push on save** off for HTML-as-source-of-truth). Seed once with **Upload current to Flask**, then edit from Lastest UI `/points-config` → **Companion** tabs (F2-like: scene gates, alerts, paid, bestiary HUD, ID, corners, live water, chrome, vertical, summon, advanced JSON). Heartbeat: `POST/GET /api/companion-settings/heartbeat`. Presets + undo also on that API. Does **not** sync OBS password, hosts, or absolute file paths.
- Full rules: [`../docs/bestiary-summon-system.md`](../docs/bestiary-summon-system.md).
- Settings → **Bestiary**: enable, URL, poll, zone layout, exp bar width, fonts, banner duration.
- **Asset sync:** copy `../core/src/main/assets/interfaces/status_pane.png` → `assets/ui_spd/status_pane/status_pane.png` (atlas rects in `scripts/spd_ui_art.gd` match `StatusPane.java`).
- Chat: `!summon`, `!bestiary` / `!summonlevel` (bar + your XP), `!topsummoner` (sprint), `!heat` / `!hot` (2×), `!summonhall`, `!mysummons`; `!points` includes sprint/heat XP.

## Local settings file (`user://companion_settings.cfg`)

Created on first run. You can edit in a text editor, or use the in-app **Settings** window (**F2** or the HUD button), then **Apply & save**. That writes this file and reconnects WebSocket / UDP where needed. **F5** still reloads from disk without opening the window (and reconnects WS/UDP via the game client autoload). **F3** toggles the top-left connection/snapshot status panel (hidden state is saved without reconnecting listeners). **F4** toggles the vertical companion window.

### Export defaults (overwrite user:// on revision bump)

- Shipped file: [`res://defaults/companion_settings.cfg`](defaults/companion_settings.cfg) with `[meta] defaults_revision`.
- On load: if `user://` is missing **or** its `defaults_revision` ≠ shipped revision → **overwrite** `user://` from the shipped file (then continue normally).
- In the **editor** Settings footer: **Save as export defaults** applies current UI, bumps revision, writes `user://`, and copies into `res://defaults/`. Re-export after that so the new revision ships.
- Runtime tweaks still save to `user://` until the next export with a higher revision overwrites them.

**`[network]`**

- `game_ws_host` (default `127.0.0.1`)
- `game_ws_port` (default `5001`)
- `streamerbot_udp_port` (default `5100`)
- `ws_reconnect_sec` (default `2.0`)
- `http_fallback_enabled` (default `false`)
- `http_fallback_url` (default `http://127.0.0.1:5000/api/game-data`)
- `http_poll_interval_sec` (default `2.0`)
- `spawn_result_file_path` (default `""`) — absolute path to `spawn_result.txt`; empty disables
- `spawn_result_file_poll_sec` (default `0.15`) — poll interval for that file
- `spawn_result_file_icons` (default `false`) — use middle segment for alert scroll icon

**`[ui]`**

- `hud_status_panel_visible` (default `true`) — WebSocket / UDP / snapshot status panel; **F3** toggles
- `show_pending_udp_alerts` (default `true`)
- `alert_queue_max` (default `5`)
- `alert_hold_sec` (default `3.0`)
- `alert_fade_in_sec` (default `0.5`)
- `alert_fade_out_sec` (default `0.75`)
- `show_ping_alerts` (default `false`) — show `ping_result` alerts
- `show_failed_command_alerts` (default `false`) — show failed `*_result` with `error` text
- **Alert zone** (viewport pixels from top-left):
  - `alert_zone_x_px`, `alert_zone_y_px`, `alert_zone_width_px`
  - `alert_zone_height_px` (default `0` = auto to bottom minus margin)
  - `alert_zone_bottom_margin_px`
- **ID overlay zone** (separate rectangle; same height/margin rules):
  - `id_zone_x_px`, `id_zone_y_px`, `id_zone_width_px`, `id_zone_height_px`, `id_zone_bottom_margin_px`
  - If `id_zone_x_px` is missing from an older cfg, values are copied from the alert zone once.
- `alert_text_align` (default `left`) — `left` or `center`
- `id_overlay_enabled` (default `true`)
- `id_cell_width_px`, `id_cell_height_px`, `id_cell_padding_px`, `id_known_icon_fraction`, `id_flow_h_separation_px`, `id_block_separation_px`
- `spend_indicator_corner` / `spend_indicator_margin_x` / `spend_indicator_margin_y` (also mirrored under `[network]` for older tooling)

**`[ui_vertical]`**

- `vertical_window_enabled` (default `true`)
- Zone / live-water / corner keys matching the main layout (alert, id, bestiary, paid, live_water_*, spend_*, free_promos_*)
- `chrome_boxes` — separate list from `[ui]`
- Per-element toggles: `show_live_water`, `show_title`, `show_chrome_boxes`, `show_id_overlay`, `show_alerts`, `show_paid_notices`, `show_bestiary`, `show_summon_march`, `show_spend_indicator`, `show_free_promos`

Legacy configs without `alert_zone_x_px` are migrated once from old `alert_layout_margin_px`, `alert_slot_width_fraction`, and `id_overlay_top_offset_px` (then ID zone is initialized equal to the alert zone from that migration).

Snapshot parsing: prefers `identification.potions` / `identification.scrolls` (arrays). Also tries top-level `potions` / `scrolls` and alternate keys (`*_list`, `identified_*`). Each item may use `rune_name`/`rune`/`color`, `class_name`/`type`, and `is_known`/`known`/etc. Textures: `res://assets/potions/*.png`, `res://assets/scrolls/*.png`, `res://assets/itemicons/*.png`.
