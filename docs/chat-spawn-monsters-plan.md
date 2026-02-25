# Chat-Spawn Monsters – Implementation Plan

Viewers send commands in Twitch chat (e.g. `!spawn rat`) via Streamer.bot. The command is relayed to the game, which spawns the monster near the player.

---

## 1. Architecture Overview

```
Twitch Chat (!spawn rat)
       │
       ▼
Streamer.bot (trigger: Twitch Chat Message)
       │
       ▼
Streamer.bot Action (HTTP POST or Run Command)
       │
       ▼
server.py (POST /api/spawn-command)
       │
       ▼
server.py sends via WebSocket to game (already connected)
       │
       ▼
Game StreamingServer.onMessage (receives command)
       │
       ▼
StreamingCommandHandler (queue for main thread)
       │
       ▼
GameScene / Level (spawn mob near hero)
```

---

## 2. Streamer.bot Setup

### Trigger
- **Event:** Twitch Chat Message
- **Condition:** Message matches `!spawn <monster>` (e.g. `!spawn rat`, `!spawn crab`)
- **Optional:** Extract `%rawInput%` or `%message%` and parse the monster name

### Action
**Option A – HTTP Request (recommended):**
- Streamer.bot has a "Make Request" or similar action
- POST to `http://127.0.0.1:5000/api/spawn-command`
- Body: `{"monster": "rat"}`
- Or: `{"monster": "rat", "username": "%userName%"}` for logging/cooldowns per user

**Option B – Run Command:**
- Execute: `curl -X POST http://127.0.0.1:5000/api/spawn-command -H "Content-Type: application/json" -d "{\"monster\":\"rat\"}"`
- Or a small Python script that POSTs

### Command Parsing
- Use Streamer.bot's "Execute Code" or a sub-action to parse the message
- Extract `rat` from `!spawn rat` → pass as `monster` in the request body

---

## 3. server.py Changes

### New Endpoint
- **POST /api/spawn-command**
- Body: `{"monster": "rat"}` (required)
- Optional: `{"monster": "rat", "username": "viewer123"}` for per-user cooldowns

### Logic
1. Validate `monster` is in the allowed list (whitelist).
2. Optionally enforce a global cooldown (e.g. 30 seconds between spawns).
3. Optionally enforce per-user cooldown (e.g. 60 seconds per viewer).
4. Store the pending command in a queue: `spawn_command_queue.put({"monster": "rat"})`.
5. The game WebSocket thread (or a dedicated sender) reads from the queue and sends to the game via the existing WebSocket connection.

### WebSocket Send
- The game_ws_thread uses `WebSocketApp`; we need a reference to the `ws` object to send.
- Store `game_ws_conn` when `on_open` is called; clear it on `on_close`.
- When a spawn command is queued, call `game_ws_conn.send(json.dumps({"command": "spawn", "monster": "rat"}))`.

### Whitelist
- Define a list of allowed monster names: `rat`, `crab`, `snake`, `gnoll`, `bat`, `skeleton`, etc.
- Reject unknown monsters with 400.

---

## 4. Game Changes

### 4.1 StreamingServer – Handle Incoming Messages

**File:** `desktop/StreamingServer.java`

- Override `onMessage(conn, message)`:
  - Parse JSON: `{"command": "spawn", "monster": "rat"}`.
  - If valid, pass to a handler that queues the command for the main thread.
- Use `Gdx.app.postRunnable()` so the spawn runs on the main/game thread.

### 4.2 StreamingCommandHandler (New)

**File:** `core/.../utils/StreamingCommandHandler.java` (or `desktop/.../StreamingCommandHandler.java`)

- Static method: `void onSpawnCommand(String monsterName)`.
- Called from `Gdx.app.postRunnable()`.
- Map `monsterName` (e.g. `"rat"`) → `Rat.class`.
- Find a valid cell near the hero (reuse logic from `SummoningTrap` or `Level.randomRespawnCell`).
- Create mob: `Mob mob = Reflection.newInstance(Rat.class)`.
- Set `mob.pos`, `mob.state = mob.WANDERING`.
- Add to level: `GameScene.add(mob)`.

### 4.3 Monster Name Mapping

**Map chat names → Mob classes:**

| Chat name | Mob class |
|-----------|-----------|
| rat | Rat |
| albino | Albino |
| snake | Snake |
| gnoll | Gnoll |
| crab | Crab |
| slime | Slime |
| swarm | Swarm |
| thief | Thief |
| skeleton | Skeleton |
| bat | Bat |
| brute | Brute |
| shaman | Shaman (or random subclass) |
| spinner | Spinner |
| dm100 | DM100 |
| guard | Guard |
| necromancer | Necromancer |
| ghoul | Ghoul |
| elemental | Elemental.random() |
| warlock | Warlock |
| monk | Monk |
| golem | Golem |
| succubus | Succubus |
| eye | Eye |
| scorpio | Scorpio |

- Exclude bosses (Goo, Tengu, DM-300, King, Yog) by default.
- Exclude NPCs (Shopkeeper, etc.) unless you want them.

### 4.4 Find Cell Near Hero

**Logic (from SummoningTrap):**
- Start from `Dungeon.hero.pos`.
- Check `PathFinder.NEIGHBOURS8` (adjacent cells).
- If `Actor.findChar(cell) == null` and `(passable[cell] || avoid[cell])`, use it.
- For large mobs, also require `openSpace[cell]`.
- If no adjacent cell, expand outward (e.g. `PathFinder.NEIGHBOURS8` then `PathFinder.NEIGHBOURS16` or similar).

---

## 5. Safety & Rate Limiting

### Cooldowns
- **Global:** 30–60 seconds between spawns (configurable).
- **Per-user:** 60–120 seconds per viewer (optional).

### Whitelist
- Only spawn mobs in the allowed list.
- Avoid bosses and special mobs unless explicitly allowed.

### Game State
- Only process when `Dungeon.hero != null`, `Dungeon.level != null`, and we're in `GameScene`.
- Ignore commands when in menu, loading, or dead.

### Settings
- Add a setting: `Enable chat spawn` (default off).
- Optional: `Chat spawn cooldown (seconds)`.

---

## 6. File / Module Layout

| Component | Location |
|-----------|----------|
| HTTP endpoint | `Lastest UI/server.py` |
| WebSocket send | `server.py` (reuse game WS connection) |
| Command handler | `desktop/StreamingCommandHandler.java` (or `core` if no desktop deps) |
| onMessage | `desktop/StreamingServer.java` |
| Monster name map | `StreamingCommandHandler` or static util |
| Settings | `SPDSettings`, `WndSettings` |

---

## 7. Implementation Phases

### Phase 1 – Minimal (server + game)
1. Add `POST /api/spawn-command` to server.py.
2. Add queue and WebSocket send when game is connected.
3. Add `onMessage` to StreamingServer; parse and queue.
4. Add `StreamingCommandHandler` with hardcoded spawn for `rat` only.
5. Test: `curl -X POST http://127.0.0.1:5000/api/spawn-command -d '{"monster":"rat"}'`.

### Phase 2 – Full monster list
1. Add full monster name → class mapping.
2. Add whitelist.
3. Add global cooldown.

### Phase 3 – Streamer.bot
1. Setup Twitch Chat trigger.
2. Parse `!spawn rat` → extract `rat`.
3. Call HTTP POST.

### Phase 4 – Polish
1. Add settings (enable/disable, cooldown).
2. Add per-user cooldown (optional).
3. Optional: announce in chat when a spawn succeeds.

---

## 8. Example Request/Response

**Request:**
```http
POST /api/spawn-command HTTP/1.1
Host: 127.0.0.1:5000
Content-Type: application/json

{"monster": "rat"}
```

**Response (success):**
```json
{"ok": true, "monster": "rat"}
```

**Response (rejected – unknown):**
```json
{"ok": false, "error": "Unknown monster: dragon"}
```

**Response (rejected – cooldown):**
```json
{"ok": false, "error": "Cooldown active", "retry_after": 25}
```

---

## 9. Dependencies

- `server.py` already has `websocket-client` and connects to the game.
- Game already has `StreamingServer` and `StreamingBootstrapper`.
- No new external dependencies.

---

## 10. Testing Order

1. **Phase 1:** Test with curl; verify rat spawns.
2. **Phase 2:** Test with other monsters.
3. **Phase 3:** Connect Streamer.bot; test from Twitch chat.
4. **Phase 4:** Tune cooldowns and settings.
