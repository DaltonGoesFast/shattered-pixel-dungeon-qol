# Bestiary Summon System

Hybrid progression for free `!summon` marches on the Godot companion overlay.

**Related:** [summon-march-system.md](summon-march-system.md), [Chat Command Economy v1.md](Chat%20Command%20Economy%20v1.md), [`spd-comp3/AGENTS.md`](../spd-comp3/AGENTS.md).

---

## Overview

| System | Behavior | Reward |
|--------|----------|--------|
| **Co-op Bestiary bar** | All summons add XP; bar fills → unlock next zone | Shared unlock pool |
| **Level sprint** | XP since current level started; resets on level-up. **One crown per user per stream** (session reset clears) | Hall of Fame + donor pts **100 / 200 / 300 / 400** by zone |
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

Default bar thresholds to leave each level: **4000 → 7000 → 10000 → 14000** (~35k XP to Halls; tuned so a hot 30–50 chatter chat takes **>~2 hours**; level 5 has no further bar).

---

## XP formula

From [`points_config.json`](../Lastest%20UI/points_config.json) `cost_per_monster`:

```text
xp = max(2, cost // 5)   # rat(5)→2, scorpio(80)→16
```

Implemented in [`summon_bestiary.monster_xp()`](../Lastest%20UI/summon_bestiary.py).

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
- Sprint winner → `grant_sprint_donor_reward` scaled by completed level: **100 + 100×(level−1)** (Sewers 100 → City 400). Flat donor pts, no earn multipliers.
- After crowning, that user is in `sprint_winners` and cannot win another sprint until `POST /api/session/reset` / Stream Started. `!topsummoner` / HUD show the next eligible leader.
- Crown holders’ `!summon` marches are stamped `crowned: true` on `/api/summon-march`; the Godot companion draws them **larger**, with a **Blessed-champion Flare** (yellow `0xFFFF00`, 6 rays, additive light mode, 90°/s — matching `ChampionEnemy.Blessed` + `effects.Flare`), plus the **Dwarf King crown**.

---

## Smoke test

```powershell
cd "Lastest UI"
python -c "from summon_bestiary import reset_bestiary_state, apply_summon, get_state_payload; reset_bestiary_state(); print(apply_summon('Test','rat')); print(get_state_payload()['level'])"
curl http://127.0.0.1:5000/api/bestiary
```
