# Streamer.bot HTTP Gateway Plan

**Status:** Approved for implementation  
**Last updated:** July 2026  

Thin Streamer.bot + fat `server.py`: one HTTP entry point owns earn, spend, queries, donations, and meta commands. Streamer.bot handles chat I/O, OBS, and sounds only.

**Scope:** Plumbing first — **current economy rules**. [Chat Command Economy v1.1](Chat%20Command%20Economy%20v1.md) is a follow-up project (server/Python only; no Streamer.bot action changes).

**Related:** [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md), [fard-system.md](fard-system.md), [summon-march-system.md](summon-march-system.md), [streaming-setup-guide.md](streaming-setup-guide.md)

---

## Locked decisions

| Decision | Choice |
|----------|--------|
| Scope | Plumbing first; Economy v1.1 later |
| Action reduction | Full collapse — one message router for all `!` commands |
| Chat copy | Server returns final `message` strings; Streamer.bot sends them |
| Migration | Offline cutover — test off-stream, switch in one session |
| `fard_used` | Server session state (not Streamer.bot temp globals) |
| `!` parsing | Server parses `rawMessage` for command + args (e.g. `!spawn rat`) |
| Command registry | Delete per-command Streamer.bot entries; router handles all |
| `spend_disabled.txt` | Keep — Stream Deck writes file; server reads it |
| Auth | Localhost-only for now |

---

## Problem

Today ~40 Streamer.bot actions each run a 3-step pipeline:

1. **Run Program** — `python points_command.py <cmd> …`
2. **Execute C#** — read `spawn_result.txt`, set variables
3. **Platform branch** — `%commandSource%` → Twitch vs YouTube reply

Earn logic lives in Streamer.bot C#; spend/donate costs live in `points_config.json` and Python. Any economy or command change means hand-editing many actions and apply guides.

---

## Target architecture

```
Viewer chat / donations
        ↓
Streamer.bot (~9 actions: triggers + presentation only)
        ↓
POST /api/chat-command  →  server.py  →  points logic + game WS :5001
        ↓
JSON { ok, message, pts, presentation? }
        ↓
Streamer.bot: chat reply + optional OBS/sound (presentation queue)
```

**Removed from live path:** `spawn_result.txt`, `donation_result.txt`, earn C# blocks, per-command C# parsers.

---

## Target Streamer.bot inventory (~9 actions)

| # | Action | Trigger | Responsibility |
|---|--------|---------|----------------|
| 1 | **Chat router** | Message Received (Twitch + YouTube) | All `!` commands + non-command earn via HTTP |
| 2 | **First Words presentation** | First Words (platform trigger) | OBS overlay + sound only — **not** points |
| 3 | **Passive earn** | Present Viewers | `type: earn.passive` |
| 4 | **Cheer** | Twitch Cheer | `type: donation.cheer` |
| 5 | **Super Chat** | YouTube Super Chat | `type: donation.superchat` |
| 6 | **Gift** | Gift sub / gift membership | `type: donation.gift` |
| 7 | **Stream started** | Stream Started | Session reset |
| 8 | **Spend off / on** | Stream Deck hotkeys | Write `spend_disabled.txt` |
| 9 | **Presentation** | Sub-action from router / First Words | OBS flashes, sounds, GDI text (`!fard`, etc.) |

**Delete:** Actions 04–31, 36–37, 40 and all matching **Command** registry entries (`!spawn`, `!heal`, …).

### First Words split

First chat of a session fires **two** things:

| Path | Trigger | Does |
|------|---------|------|
| Points | Chat router (`earn.message`) | Server detects first message, awards +5 with multipliers |
| OBS | **First Words** trigger (Action 2) | OBS overlay + sound on **Default / presentation queue** |

The First Words trigger stays in Streamer.bot because OBS presentation requires it. Points logic moves to the server so First Words action has **no** C# earn block and **no** blocking queue wait.

**Ordering note:** On a user's first message, Message Received (router) and First Words may both fire. That is fine — server awards points; First Words action runs OBS in parallel on the presentation queue.

---

## API contract

### Endpoint

`POST http://127.0.0.1:5000/api/chat-command`

### Request (router — thinnest Streamer.bot body)

```json
{
  "rawMessage": "!spawn rat",
  "username": "viewer1",
  "platform": "twitch",
  "context": {
    "isSubscribed": true,
    "isMember": false,
    "isBroadcaster": false,
    "bits": 0
  }
}
```

Server parses `rawMessage`:

- Starts with `!` → spend, query, or meta command + args
- Otherwise → `earn.message`

Donation actions may use typed requests instead:

```json
{
  "type": "donation.cheer",
  "username": "viewer1",
  "platform": "twitch",
  "context": { "bits": 100, "isSubscribed": true }
}
```

### Response

```json
{
  "ok": true,
  "message": "@viewer1 — Spawned a rat! You have 42 points left.",
  "pts": 42,
  "earned": 0,
  "extra": { "command": "spawn", "monster": "rat" },
  "presentation": null
}
```

Silent earn (no chat spam):

```json
{
  "ok": true,
  "message": null,
  "pts": 106,
  "earned": 2,
  "presentation": null
}
```

Presentation hint (`!fard`):

```json
{
  "ok": true,
  "message": null,
  "pts": 42,
  "presentation": {
    "obs": ["fard_flash_h", "fard_flash_v"],
    "sound": "fart",
    "gdi": { "file": "totalfard.txt", "increment": 1 }
  }
}
```

| Field | Meaning |
|-------|---------|
| `ok` | Success / failure |
| `message` | Chat text to send; `null` = stay silent |
| `pts` | Remaining spendable balance |
| `earned` | Points added this call (earn/donation) |
| `extra` | Command-specific payload for logging/debug |
| `presentation` | Optional OBS/sound/GDI hints for Action 9 |

---

## Server implementation

### New / changed modules

| Piece | Location | Notes |
|-------|----------|-------|
| `/api/chat-command` | `server.py` | Single entry; dispatches by parsed message or `type` |
| Earn logic | `earn.py` or `points_command.py` | Port from Streamer.bot C# Actions 01–03 |
| Session state | `session_state.json` | Per-stream: `fard_used`, `first_words`, summon counts |
| Message catalog | `chat_messages.py` or JSON | All viewer-facing strings (ported from scratch doc) |
| Refactored spend | `points_command.py` | Return structured result; optional legacy file mode during dev |

### Earn rules (current — not v1.1)

| Source | Rate | Notes |
|--------|------|-------|
| Chat message | +1 | 30 s cooldown per user |
| Passive tick | +1 | Every 60 s; shares `lastEarn` with chat |
| First Words | +5 | Once per user per stream; server tracks in `session_state.json` |

**Multipliers (chat, passive, first words):**

```
effective = base × (global2x ? 2 : 1) × (topSummoner ? 2 : 1) × (subOrMember ? 2 : 1)
```

Max 8× on chat earn. Donation multipliers unchanged (existing `points_command.py` logic).

### Session state (`session_state.json`)

Reset on Stream Started (Action 7):

```json
{
  "fard_used": { "viewer1": true },
  "first_words": { "viewer2": true },
  "stream_started_at": 1720000000
}
```

- `!fard` gate reads/writes `fard_used` on server
- First Words bonus reads/writes `first_words` on server
- Summon march session data may merge here or stay in existing files

### Command routing (server-side)

| Parsed command | Handler |
|----------------|---------|
| `spawn`, `champion`, `gold`, … | Existing `cmd_*` spend functions |
| `points` | Balance query |
| `toppoints` | Top-3 query |
| `fard` | Meta: extend `double_points_end.txt`, return `presentation` |
| `summon` | Meta: existing summon-march logic |
| `doublepoints` | Meta: streamer-only gate, extend global 2× |
| Unknown `!foo` | `ok: false`, helpful error message |

Args parsed from remainder of `rawMessage` (e.g. `!spawn golden bee` → `["golden", "bee"]` or monster alias rules per existing spawn logic).

### Meta commands

| Command | Server | Streamer.bot |
|---------|--------|--------------|
| `!fard` | Gate via `fard_used`, extend timer, bump counter | Presentation action: OBS + sound from response |
| `!summon` | `summon_march_post.py` logic inlined or called | None |
| `!doublepoints` | Broadcaster check, extend timer | None |

---

## Streamer.bot implementation

### Action 1: Chat router

**Trigger:** Message Received (Twitch + YouTube)  
**Queue:** Blocking points queue (same as today's spend/earn queue)

**Sub-actions:**

1. **Web Request** — `POST /api/chat-command`
   - Body: `rawMessage`, `username`, `platform`, `context` (sub/member flags)
   - Timeout: **12 s**
2. **Parse JSON** → `%apiOk%`, `%apiMessage%`, `%apiPts%`, `%apiPresentation%`
3. **If presentation** → Run Action 9 on **Default / presentation queue** (non-blocking)
4. **If message not empty:**
   - `platform == youtube` → YouTube Message
   - `platform == twitch` → Twitch Message

No Run Program. No C# file parsing.

### Action 2: First Words presentation

**Trigger:** First Words (Twitch + YouTube)  
**Queue:** **Default / presentation queue** (never blocking spend queue)

**Sub-actions:** OBS overlay + sound only (existing First Words visuals).

Optionally POST `type: earn.firstwords` to server **in parallel** if First Words trigger fires before Message Received on some platforms — server idempotency ensures +5 only once. Prefer: rely on router `earn.message` for points; this action is presentation-only.

### Action 9: Presentation (shared)

Called by router (and optionally First Words for non-fard cues):

- Show/hide OBS sources from `presentation.obs[]`
- Play `presentation.sound`
- Update GDI files from `presentation.gdi`

---

## Migration: offline cutover

### Phase 0 — Prep

- [ ] Map every current action → delete / merge / keep (see [inventory](#action-inventory))
- [ ] Port chat message strings to server catalog
- [ ] Export backup of current Streamer.bot
- [ ] Write test matrix (spend × platform × edge cases)

### Phase 1 — Server (repo)

- [ ] Implement `POST /api/chat-command`
- [ ] Port earn logic from C# (current rates)
- [ ] Add `session_state.json` + Stream Started reset endpoint
- [ ] Refactor `points_command.py` to return JSON-shaped results
- [ ] Keep `LEGACY_FILE_MODE` flag until cutover
- [ ] Unit/manual tests via `curl`

### Phase 2 — Streamer.bot (off-stream)

- [ ] Build Actions 1–9 alongside old set (disabled)
- [ ] Wire presentation queue for Actions 2 and 9
- [ ] Verify no hardcoded author paths in Web Request URLs (`127.0.0.1:5000` only)

### Phase 3 — Off-stream testing

- [ ] Full test matrix passes
- [ ] No reads of `spawn_result.txt` / earn C# in new path
- [ ] First Words OBS fires on presentation queue without waiting on spawns
- [ ] `!fard` OBS + sound still works via `presentation`
- [ ] 12 s timeout returns error message to chat on game hang

### Phase 4 — Cutover night

1. Stop stream
2. Deploy server changes
3. Start overlay + game; verify `/api/status`
4. Disable old actions (01–40 spend/earn/query)
5. Enable new actions (1–9)
6. Delete Command registry entries
7. Smoke test (~20 min)
8. Go live

**Rollback:** Re-import backup export; revert server if needed.

### Phase 5 — Cleanup + replicability

- [ ] Remove `LEGACY_FILE_MODE` and file handoff from production path
- [ ] Rewrite [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md) for 9-action model
- [ ] Merge fard/summon apply guides into this doc's Streamer.bot section
- [ ] Generate `Lastest UI/streamerbot/shatter-the-streamer-export-0.2.0.txt`
- [ ] Add **replication appendix** (below)

---

## Action inventory

| Current action | Disposition |
|----------------|-------------|
| 01 Earn (chat) | **Delete** → Chat router + server `earn.message` |
| 02 Earn (passive) | **Replace** → Action 3 HTTP only |
| 03 First Words (C#) | **Delete C#** → server awards; keep **Action 2** for OBS |
| 04 `!points` | **Delete** → router |
| 05 `!toppoints` | **Delete** → router |
| 06–18, 25–31, 36–37 Spend commands | **Delete** → router |
| 19 `!doublepoints` | **Delete** → router (server gate) |
| 20–21 Cheer / Super Chat | **Replace** → Actions 4–5 HTTP |
| 22 Stream Started | **Replace** → Action 7 + server session reset |
| 23–24 Spend off/on | **Keep** → Action 8 |
| 40 Gift | **Replace** → Action 6 HTTP |
| fard action | **Delete** → router + Action 9 presentation |
| summon action | **Delete** → router |

---

## Test matrix (minimum)

| Category | Cases |
|----------|-------|
| Spend | Each command: ok, insufficient pts, spend disabled, game timeout |
| Args | `!spawn rat`, `!gold 50`, bare `!wand` |
| Query | `!points`, `!toppoints` |
| Earn | +1 message, cooldown skip, passive, first words +5 |
| Multipliers | Global 2×, top summoner, sub/member, stacked |
| Meta | `!fard` once, twice (silent), `!summon`, `!doublepoints` non-streamer blocked |
| Donations | Cheer, Super Chat, gift — multiplier args |
| Platform | Twitch + YouTube for router replies |
| First Words | OBS fires; points awarded once only |
| Concurrency | Rapid double `!spawn` — second serializes cleanly |

---

## Deferred: Economy v1.1

After plumbing is stable, implement [Chat Command Economy v1.1](Chat%20Command%20Economy%20v1.md) in **server/Python only**:

- 2 pt/msg, 20 s cooldown
- 500 chat cap per stream
- `!bank` command
- Stream-offline reset + auto-bank
- Donation 4× cap

No Streamer.bot action changes required — API contract stays the same.

---

## Replication appendix

For streamers who want to replicate the setup without author-specific paths:

### Requirements

- Python 3.x
- Streamer.bot (Twitch + optional YouTube)
- OBS (scenes referenced in `presentation` hints)
- Game + overlay server from this repo

### Path rules

| What | Rule |
|------|------|
| Web Request URL | Always `http://127.0.0.1:5000/api/chat-command` — no file paths |
| Working Directory | **Not used** in new model (no Run Program) |
| `spend_disabled.txt` | Stream Deck toggle writes to `Lastest UI/spend_disabled.txt` |
| OBS source names | Must match `presentation.obs` keys in server responses or config |

### Setup steps (summary)

1. Clone repo; `pip install` overlay deps; run `python server.py` in `Lastest UI`
2. Enable game streaming (WS :5001)
3. Import `shatter-the-streamer-export-0.2.0.txt`
4. Map OBS source names in Action 9 to your scenes
5. Connect Twitch/YouTube in Streamer.bot
6. Off-stream smoke test per [test matrix](#test-matrix-minimum)

### Customization without Streamer.bot edits

| Change | Where |
|--------|-------|
| Command costs | `points_config.json` / `/points-config` UI |
| New spend command | `points_command.py` + server route table |
| Chat messages | Server message catalog |
| Earn rates (post-v1.1) | `points_config.json` + server earn module |
| OBS presentation | `presentation` payload in server meta handlers |

---

## Effort estimate

| Phase | Effort |
|-------|--------|
| 0 Prep | 2–4 hours |
| 1 Server | 2–3 days |
| 2 Streamer.bot build | 4–6 hours |
| 3 Test | 1–2 days |
| 4 Cutover | 1–2 hours |
| 5 Cleanup | 4–6 hours |

---

## Open items

- [ ] Exact OBS source names for Action 9 / `presentation.obs` keys
- [ ] Streamer username(s) for `!doublepoints` gate
- [ ] Whether donation actions call `/api/chat-command` or existing `/api/donation/*` (either works)
- [ ] First Words: confirm platform trigger list (Twitch + YouTube) matches current setup
