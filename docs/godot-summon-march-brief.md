# Godot Companion — Summon March Implementation Brief

**Audience:** Godot agent / developer implementing the SPD stream companion overlay.  
**Production project:** [`spd-comp3/`](../spd-comp3/) in this repo (**SPD Companion 3**). See [`spd-comp3/AGENTS.md`](../spd-comp3/AGENTS.md).  
**Backend:** `shattered-pixel-dungeon-qol` → `Lastest UI/server.py` (already implemented).

---

## What this feature is

Viewers type `!summon` in Twitch/YouTube chat. Streamer.bot validates cooldown + monster, then **POSTs** to the overlay server. **Your Godot app** polls that server and plays a short visual: a monster sprite enters from one edge of the window, walks/runs across, and exits.

This is **purely cosmetic**. It does not spawn mobs in Shattered Pixel Dungeon. Chat, points, and leaderboard are handled elsewhere.

---

## Prerequisites

| Requirement | Details |
|-------------|---------|
| Overlay server | `python server.py` in `Lastest UI/` — listens on **`http://127.0.0.1:5000`** |
| Godot window | Transparent or chroma-key friendly; captured in OBS as Window/Game Capture |
| Network | Godot uses `HTTPRequest` (or equivalent) to localhost only |

**Smoke test without chat:**

```bash
curl -X POST http://127.0.0.1:5000/api/summon-march \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"TestViewer\",\"monster\":\"rat\",\"layout\":\"horizontal\"}"

curl "http://127.0.0.1:5000/api/summon-march"
```

---

## API contract

Base URL: `http://127.0.0.1:5000`

### 1. Poll summon queue (required)

```
GET /api/summon-march?since=<cursor>
```

**`since` cursor (pick one strategy and stick to it):**

| Cursor value | Behavior |
|--------------|----------|
| *(empty)* | Returns **all** events currently in memory (up to 500) |
| UUID string | Returns events **after** the event with that `id` (exclusive) |
| Unix timestamp (digits only) | Returns events where `ts > since` |

**Recommended for Godot:** UUID cursor. After processing events, set `last_seen_id` to the **last event's `id`** in the batch. Next poll: `?since=<last_seen_id>`.

**Response 200:**

```json
{
  "events": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "ts": 1710000123,
      "username": "SomeViewer",
      "monster": "rat",
      "layout": "horizontal"
    }
  ],
  "count": 1
}
```

**Fields:**

| Field | Type | Notes |
|-------|------|-------|
| `id` | string (UUID) | Dedupe key — never process the same `id` twice |
| `ts` | int | Unix seconds when queued |
| `username` | string | Chatter display name; may be empty |
| `monster` | string | Lowercase spawn id — see whitelist below |
| `layout` | `"horizontal"` \| `"vertical"` | Hint for march direction / scene; default `horizontal` if omitted on POST |

**Polling interval:** 200–500 ms while the app is running.

**Delivery semantics:** At-least-once. On startup, server reloads recent events from `summon_march_queue.jsonl`. Dedupe by `id` in Godot. **No ack endpoint in v1.**

**Error handling:** If GET fails (connection refused), show offline state and retry. Do not crash.

---

### 2. Top summoner (optional)

```
GET /api/top-summoner
```

**Response 200:**

```json
{
  "username": "Alice",
  "count": 12,
  "display": "Top Summoner: Alice - 12",
  "has_leader": true
}
```

Poll every few seconds if you want a crown/badge on the companion overlay. Leader is session-scoped (resets when Streamer.bot restarts).

---

### 3. Server status (optional)

```
GET /api/status
```

Returns `{ "running": true, ... }` — useful for a “connected” indicator.

---

## Monster whitelist

Every `monster` value is already validated server-side. Map these strings to sprites/animations:

```
rat, albino, snake, gnoll, crab, slime, swarm, thief,
skeleton, bat, brute, shaman, spinner, dm100, guard,
necromancer, ghoul, elemental, warlock, monk, golem,
succubus, eye, scorpio
```

**Display alias:** `dm100` → “DM-100” if you show a label.

**Unknown monster at runtime:** Should not happen if server is authoritative; fallback to a generic silhouette or `rat`.

**Sprite source (optional):** SPD mob art lives under the main game repo `core/src/main/assets/` (spritesheets). You can copy/import assets or use placeholders until art is wired.

---

## Visual spec (v1)

### Window / OBS

- Fixed resolution matching stream layout (e.g. 1920×1080 horizontal, 1080×1920 vertical).
- Transparent background (`Project → Display → Window → Transparent` or per-platform equivalent).
- Layered in OBS **above** or **beside** the game capture — not inside the HTML overlay.

### March animation

For each new event:

1. Instantiate monster at off-screen start (e.g. left edge for horizontal left→right).
2. Optional: brief label with `username` (small text above sprite).
3. Tween/move across the viewport over ~3–5 seconds (configurable).
4. Despawn on exit.

**`layout` field:**

| Value | Suggested behavior |
|-------|-------------------|
| `horizontal` | March left→right or right→left along bottom third of screen |
| `vertical` | March bottom→top or top→bottom along side margin |

**Concurrent events:** Multiple viewers can summon (1/min cooldown each). v1: allow overlapping marches on different Y lanes, or queue visually if too busy.

### Catch-up on late start

If Godot starts mid-stream, first poll may return many events. Options:

- Play all with staggered delays (can flood the screen), or
- Only animate events from the last N seconds (`ts > now - 30`), or
- Skip backlog and only animate events newer than startup time.

**Recommendation:** On first poll after connect, set cursor to latest `id` without animating backlog; only animate **new** events from then on. Optionally make this a settings toggle.

---

## Suggested Godot architecture

```
Main (autoload or root)
├── SummonPollService     # HTTPRequest timer, tracks last_seen_id
├── MarchController       # Spawns march scenes, assigns lanes
├── MonsterRegistry       # monster string → PackedScene / SpriteFrames
└── TopSummonerBadge      # optional, polls /api/top-summoner
```

**SummonPollService pseudocode:**

```gdscript
var base_url := "http://127.0.0.1:5000"
var last_seen_id := ""
var seen_ids := {}  # Dictionary for dedupe safety

func _poll():
    var url := base_url + "/api/summon-march"
    if last_seen_id != "":
        url += "?since=" + last_seen_id.uri_encode()
    # HTTPRequest GET url → on success parse JSON
    # for event in response.events:
    #     if not seen_ids.has(event.id):
    #         seen_ids[event.id] = true
    #         march_controller.spawn(event)
    #         last_seen_id = event.id
```

**March scene:** `CharacterBody2D` or `Sprite2D` + `Tween`; export march duration and lane index.

---

## Out of scope (handled by other systems)

| Concern | Owner |
|---------|--------|
| Chat command `!summon` | Streamer.bot |
| 60 s per-user cooldown | Streamer.bot temp globals |
| Leaderboard / 2× points | Streamer.bot + `top_summoner.txt` + Earn Points C# |
| In-game mob spawn (`!spawn`) | Java game + different API |
| POST to queue | Streamer.bot only — Godot is **read-only** |

---

## Configuration constants (suggested exports)

| Setting | Default | Purpose |
|---------|---------|---------|
| `poll_interval_sec` | `0.3` | HTTP poll rate |
| `march_duration_sec` | `4.0` | Cross-screen tween time |
| `max_concurrent_marches` | `8` | Cap simultaneous sprites |
| `server_base_url` | `http://127.0.0.1:5000` | Override for testing |
| `animate_backlog_on_start` | `false` | Skip old events on connect |

---

## Test plan

1. Start `python server.py` in `Lastest UI`.
2. Run Godot companion; confirm polls without errors.
3. POST a test event via curl (above); monster should march once.
4. POST again with different `monster` values; confirm registry mapping.
5. POST duplicate poll — same `id` must not double-spawn (dedupe).
6. Stop server — app shows disconnected, recovers when server returns.
7. POST 5 events quickly — verify lane stacking or queue behavior.
8. *(Optional)* `GET /api/top-summoner` after manual write to `Lastest UI/top_summoner.txt`:
   `Top Summoner: TestUser - 3`

---

## Related docs (main repo)

- [summon-march-system.md](summon-march-system.md) — full system overview
- [streamerbot-summon-march-apply.md](streamerbot-summon-march-apply.md) — legacy standalone `!summon` (archived); live path is R1 — [summon-march-system.md](summon-march-system.md)
- [streaming-setup-guide.md](streaming-setup-guide.md) — server startup

---

## One-paragraph summary for the agent

Build a transparent Godot overlay that polls `GET http://127.0.0.1:5000/api/summon-march?since=<last_uuid>` every ~300ms, dedupes events by `id`, and for each new event spawns a monster sprite that tweens across the screen. Event payload: `{ id, ts, username, monster, layout }`. Monsters are lowercase ids from the 24-mob whitelist (`rat`, `gnoll`, `bat`, …). Optionally poll `/api/top-summoner` for a session leader badge. Do not POST to the server; Streamer.bot enqueues events when chat uses `!summon`. The SPD overlay server must be running (`python server.py` in `Lastest UI`).
