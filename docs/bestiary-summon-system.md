# Bestiary Summon System

Hybrid progression for free `!summon` marches on the Godot companion overlay.

**Related:** [summon-march-system.md](summon-march-system.md), [Chat Command Economy v1.md](Chat%20Command%20Economy%20v1.md), [`spd-comp3/AGENTS.md`](../spd-comp3/AGENTS.md).

---

## Overview

| System | Behavior | Reward |
|--------|----------|--------|
| **Co-op Bestiary bar** | All summons add XP; bar fills → unlock next zone | Shared unlock pool |
| **Level sprint** | XP since current level started; resets on level-up **and Halls loops**. **One crown per user per stream** (session reset clears) | Hall of Fame + donor pts **100 / 200 / 300 / 400** by zone; Halls loops badge + lockout, **no donor** |
| **Rolling heat** | XP in last **15 minutes** | **Personal 2×** on chat/passive/donations |
| **Soft floor (catch-up)** | After **10 unique summoners**, anyone outside top **3 eligible sprint** gets **×1.25** XP | Same mult on sprint + heat + co-op bar; chat notes `catch-up XP multiplier` |

One summon feeds all three buckets with the same XP value. Soft floor uses `max(base+1, ceil(xp * 1.25))` so even rats always gain at least +1. Excluded: `DaltonGoesFast`, `DaltonGoesSlow`. Config: `bestiary_config.json` → `soft_floor`.

**Breaking change:** Session-long summon **count** no longer drives `is_top_summoner` / personal 2×. Heat leader does.

---

## Zones (5 levels)

Zones follow SPD **native depth / chapter** (`NATIVE_DEPTH` in `points_command.py`).

| Level | Zone | Monsters |
|-------|------|----------|
| 1 | Sewers | rat, albino, snake, gnoll, crab, slime, swarm, thief |
| 2 | Prison | skeleton, dm100, guard, necromancer |
| 3 | Caves | bat, brute, shaman, spinner, ghoul |
| 4 | City | elemental, warlock, monk, golem, succubus |
| 5 | Halls | eye, scorpio |

Config: [`Lastest UI/bestiary_config.json`](../Lastest%20UI/bestiary_config.json).

Default bar thresholds to leave each level: **1000 → 2000 → 3000 → 4000** then Halls **5555** (live `bestiary_config.json`). Filling Halls loops the bar in place: crowns the eligible sprint leader (Halls badge + lockout, no donor pts) and starts a new sprint.

---

## XP formula

From [`points_config.json`](../Lastest%20UI/points_config.json) `cost_per_monster`:

```text
xp = max(2, cost // 5)   # rat(5)→2, scorpio(80)→16
```

Implemented in [`summon_bestiary.monster_xp()`](../Lastest%20UI/summon_bestiary.py).

**Paid in-game commands** (`!spawn`, `!heal`, `!gold`, …) also grant **co-op bar XP only** after the game confirms the spend, using the same formula on the **actual points deducted** (`spend_xp_from_cost` / `apply_bar_xp`). Promo / zero cost → **0 XP** (no floor of 2). Sprint, Heat, session summon counts, and overlay marches stay `!summon`-only. A spend that fills the bar still triggers the normal level-up (crowns the current **summon** sprint leader).

### Shatter Event

Mystery **pips** on the co-op bar (counts scale by zone: 3→6) plus a reward on **zone level-up** and **Halls bar loops**. Each event grants a timed free window via `free_until.json` (default **60s**): always a zone `!spawn` (1% jackpot → 50/50 eye/scorpio), usually plus one cost-weighted sidecar (hostile pool + dew/bee/ward, helpful capped). Overlapping events **extend** existing free timers. Grants are skipped while `spend_disabled.txt` exists (catch-up on the next bar XP grant after spend is re-enabled). Free promo / Shatter cost-0 spends grant **0** bar XP, so free spam cannot chain new events. Config: `bestiary_config.json` → `shatter_event` + per-level `pip_count` (also `/points-config` → Bestiary). Companion HUD draws claimed/unclaimed pip dots from `GET /api/bestiary` → `shatter`.

---

## Chat commands

| Command | Behavior |
|---------|----------|
| `!summon [mob]` | March if unlocked; adds XP |
| `!bestiary` / `!summonlevel` | Level, bar, unlocked list + **your** sprint/heat XP |
| `!topsummoner` | **Current sprint** leader + gap |
| `!heat` / `!hot` | Rolling heat leader (2×) + your heat XP |
| `!summonhall` | Frozen winners per completed level |
| `!mysummons` | Session count + sprint XP + heat XP |
| `!points` | Chat/donor balances **plus** Bestiary sprint/heat XP |

---

## HTTP API (`server.py`)

| Route | Purpose |
|-------|---------|
| `GET /api/bestiary` | Full HUD payload (level, bar, sprint, heat, hall, unlocked) |
| `GET/POST /api/bestiary-config` | Read/write `bestiary_config.json` (thresholds, heat, soft floor). Also editable on `/points-config` → Bestiary |
| `GET/POST /api/companion-settings` | Revisioned companion layout/UI for Godot poll (`CompanionSettingsPollService`). Primary editor: `/points-config` → Companion tabs. Seed once from F2 → Remote → Upload. |
| `GET/POST /api/companion-settings/heartbeat` | Companion last-seen / applied revision (Status tab) |
| `GET/POST/DELETE /api/companion-settings/presets[…]` | Named presets; `…/presets/<name>/apply` bumps revision |
| `POST /api/companion-settings/undo` | Restore previous settings blob (one step) |
| `GET /api/top-summoner` | Sprint leader (`kind: sprint`) |
| `GET /api/heat-leader` | Heat leader + window |
| `POST /api/summon-march` | Rejects monsters not in unlocked pool; events include `xp`, `bestiary_level` |

---

## Files

| File | Role |
|------|------|
| `bestiary_config.json` | Zones, thresholds, heat window, sprint donor reward |
| `summon_bestiary.py` | State machine |
| `bestiary_state.json` | Runtime session state (not committed) |
| `heat_leader.txt` | Heat display (`Top Summoner: name - N` format) |

Session reset (`POST /api/session/reset` / Stream Started) clears bestiary state.

---

## Godot companion

- Autoload `BestiaryPollService` → polls `/api/bestiary`
- `BestiaryHud` uses SPD `status_pane.png` exp slices + chrome (see `spd_ui_art.gd`)
- Settings → **Bestiary** tab (F2)
- Asset sync: copy `core/src/main/assets/interfaces/status_pane.png` → `spd-comp3/assets/ui_spd/status_pane/`; `levelicons.png` (5×16px zone icons) → `spd-comp3/assets/ui_spd/levelicons/`

---

## Points integration

- [`is_top_summoner()`](../Lastest%20UI/points_command.py) → heat leader
- Sprint winner → `grant_sprint_donor_reward` scaled by completed level: **100 + 100×(level−1)** (Sewers 100 → Metro 400). Flat donor pts, no earn multipliers. **Halls loops do not pay donor pts.**
- After crowning, that user is in `sprint_winners` and cannot win another sprint until `POST /api/session/reset` / Stream Started. `!topsummoner` / HUD show the next eligible leader. The level-up / Halls-loop banner / Hall strip names the winner — **no permanent march crown** for past winners. Later Halls loops overwrite the Halls Hall of Fame slot with the new winner.
- **Active competition** marches get crowns via `/api/summon-march` fields `badge` / `sprint_rank` / `heat_leader`:
  - `heat` → `crown_heat.png` (current heat leader)
  - `gold` / `silver` / `bronze` → sprint ranks 1–3 (`crown.png`, `crown_silver.png`, `crown_bronze.png`)
  - Heat wins if both apply. Companion may also resolve badges from Bestiary poll `sprint.top` + `heat.username`.

---

## Smoke test

```powershell
cd "Lastest UI"
python -c "from summon_bestiary import reset_bestiary_state, apply_summon, get_state_payload; reset_bestiary_state(); print(apply_summon('Test','rat')); print(get_state_payload()['level'])"
curl http://127.0.0.1:5000/api/bestiary
```
