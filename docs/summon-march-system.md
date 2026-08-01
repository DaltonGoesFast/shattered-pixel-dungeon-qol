# Summon March System

Free chat command: viewers use **`!summon`** (**60 s** per-user cooldown) to queue a monster march on the **Godot companion** overlay.

**Bestiary progression (current):** zone unlocks, sprint Hall of Fame, and rolling **heat** for personal 2× — see **[bestiary-summon-system.md](bestiary-summon-system.md)**. Session-long summon **count** leaders are **deprecated**.

**Live wiring:** **R1** + `chat_command.py` (no separate Streamer.bot summon action). **Spec below.** **Stream-info / OBS:** [streaming-system-rework-plan.md](streaming-system-rework-plan.md) § Streamer.bot meta commands. **Related:** [fard-system.md](fard-system.md), [streaming-setup-guide.md](streaming-setup-guide.md).

**Legacy apply steps:** [streamerbot-summon-march-apply.md](streamerbot-summon-march-apply.md) (archived section only).

---

## Overview

| Property | Value |
|----------|-------|
| **Platform** | Streamer.bot + overlay server + Godot companion (visual) |
| **Commands** | `!summon`, `!topsummoner`, `!mysummons`, `!bestiary`, `!heat`, `!summonhall` |
| **Cost** | Free |
| **Cooldown** | 60 s per user |
| **Monster** | Optional arg; random from **unlocked Bestiary pool** if omitted |
| **Sprint / Heat** | See [bestiary-summon-system.md](bestiary-summon-system.md) |
| **Sound** | `presentation_config.py` → `SUMMON_SOUND`; R1 `ParseChatResponse.cs` plays on success |
| **Game impact** | None — companion overlay only (not in-game spawn) |

---

## Chat commands

| Command | Aliases | Behavior |
|---------|---------|----------|
| `!summon` | — | Queue monster march; 60 s cooldown; XP to bar/sprint/heat; unlocked pool only |
| `!bestiary` | `!summonlevel` | Co-op bar + unlocked list + **your** sprint/heat XP |
| `!topsummoner` | — | **Sprint** leader this Bestiary level (+ gap) |
| `!heat` | `!hot` | Rolling 15m heat leader (personal 2×) + your heat XP |
| `!summonhall` | — | Hall of Fame (sprint winners per completed zone) |
| `!mysummons` | — | Session summon count + sprint XP + heat XP |
| `!points` | — | Also includes Bestiary sprint/heat XP (with chat/donor balances) |

---

## Architecture

```mermaid
sequenceDiagram
    participant Chat as Viewer
    participant SB as Streamer.bot R1
    participant Srv as server.py
    participant Godot as Godot_companion

    Chat->>SB: !summon rat
    SB->>Srv: POST /api/chat-command
    Srv->>Srv: cooldown, unlocked pool, apply_summon XP
    Srv->>Srv: append summon_march_queue.jsonl; heat_leader.txt
    Srv-->>SB: JSON message + optional presentation (sound)
    Godot->>Srv: GET /api/summon-march?since=id
    Srv-->>Godot: events
    Godot->>Godot: march animation
    Godot->>Srv: GET /api/bestiary
    Srv-->>Godot: bar / sprint / heat / hall
```

---

## Data

### Temp globals (Streamer.bot — session-scoped)

| Variable | Purpose |
|----------|---------|
| `summon_march_last_%userName%` | Unix timestamp of last successful summon |
| `summon_count_%userName%` | Successful summons this stream |
| `top_summon_score_ever` | Highest personal count this stream |
| `totalsummons` | Session total → `totalsummons.txt` |

### Files (`Lastest UI/`)

| File | Role |
|------|------|
| `heat_leader.txt` | Heat leader for personal 2× + OBS (`Top Summoner: name - XP`) |
| `bestiary_state.json` | Co-op bar, sprint, heat events, hall of fame |
| `totalsummons.txt` | `Total Summons: N` — session count for OBS (if written) |
| `summon_march_queue.jsonl` | Durable event log (max 500 in memory) |

### Queue event (JSON)

```json
{"id":"uuid","ts":1710000000,"username":"viewer","monster":"rat","layout":"horizontal"}
```

---

## HTTP API (`server.py`)

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/summon-march` | POST | Streamer.bot enqueues summon `{username, monster, layout?}` |
| `/api/summon-march` | GET | Godot polls `?since=<event_id or unix_ts>` |
| `/api/top-summoner` | GET | Sprint leader (`kind: sprint`) for OBS / companion |
| `/api/heat-leader` | GET | Rolling heat leader + window |
| `/api/bestiary` | GET | Full HUD payload (bar, sprint, heat, hall, unlocked) |

Monster must be in the **unlocked Bestiary pool** (starts Sewers-only; expands on level-up).

---

## Points integration

**Multiplier (chat + passive + first words):**

```text
mult = (doublePoints ? 2 : 1) × (heatLeader ? 2 : 1) × (subOrMember ? 2 : 1)
```

- **Earn Points / donations** — `is_top_summoner()` matches the **rolling heat** leader (see [bestiary-summon-system.md](bestiary-summon-system.md)), not session summon count.
- **Sprint winner** on Bestiary level-up earns a flat **100 donor points** (not the 2× multiplier).

**Deprecated:** Session count → `top_summoner.txt` as the sole 2× source.

**Separate from fard:** `!fard` = once/stream, extends **global** 2× timer. `!summon` = repeatable visual + **heat** personal 2× race + **sprint** titles.

---

## Godot companion

**Project:** [`spd-comp3/`](../spd-comp3/) in this repo (**SPD Companion 3**) — autoloads `SummonPollService`, `BestiaryPollService`, march + Bestiary HUD. Notes: [`spd-comp3/AGENTS.md`](../spd-comp3/AGENTS.md).

**Full spec:** [godot-summon-march-brief.md](godot-summon-march-brief.md), [bestiary-summon-system.md](bestiary-summon-system.md)

1. Poll `GET http://127.0.0.1:5000/api/summon-march?since=<lastEventId>` every 200–500 ms.
2. Dedupe by event `id`.
3. For each event: spawn monster sprite off-screen, tween across, despawn.
4. Map `monster` string → sprite (same names as spawn whitelist; alias `dm100` → “DM-100” for display if desired).
5. Poll `GET /api/bestiary` for the Bestiary HUD (bar / sprint / heat / hall).
6. Optional: `GET /api/heat-leader` or `/api/top-summoner` for crowns.
7. Godot runs as separate OBS source (Window Capture / Game Capture), not inside the HTML overlay.

---

## Known limitations

| Issue | Notes |
|-------|-------|
| Session-scoped counts | Resets when Streamer.bot restarts |
| Cooldown reset on SB restart | Same as fard temp globals |
| Godot offline | Events queue in jsonl; catch up on start (cap 500) |
| Concurrent users | Each user 1/min; many users = busy screen |
| server.py required | POST fails if overlay server not running |
| Multiplier stacking | Heat leader + global 2× + sub = up to 8×/message |

---

## Test checklist

| Test | Expected |
|------|----------|
| `!summon` | Queue entry; Bestiary XP; chat OK |
| `!summon` within 60 s | Cooldown skip |
| `!summon badmob` | Invalid monster rejection |
| `!summon scorpio` at Lv 1 | Locked-zone rejection |
| `!summon` no arg | Random from **unlocked** pool |
| Fill Sewers bar | Level-up; sprint winner + 100 donor; Prison unlocks |
| `!topsummoner` | Sprint leader this level |
| `!heat` | 15m heat leader (2×) |
| `!bestiary` | Bar + your sprint/heat XP |
| `!points` | Includes Bestiary sprint/heat XP |
| Donation as heat leader | 2× base (stacks with other bonuses; donation cap 4×) |
| server.py stopped | Command fails at HTTP step |
| GET `/api/summon-march` | Returns queued events |
| GET `/api/bestiary` | HUD payload |
