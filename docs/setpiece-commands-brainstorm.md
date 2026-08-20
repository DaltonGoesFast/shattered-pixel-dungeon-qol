# Setpiece Commands Brainstorm

**Status:** Brainstorm / outline only — **not implemented**. Refine here before coding.

**Related:** [COMMANDS.md](../COMMANDS.md), [Chat Command Economy v1.md](Chat%20Command%20Economy%20v1.md), [archive/streamerbot-points-from-scratch.md](archive/streamerbot-points-from-scratch.md), [stream-info-commands.md](stream-info-commands.md)

These are higher-cost **Shatter the Streamer** spends that change the map, arm delayed taxes, or open side content — above near-hero effects like `!spawn` / `!trap` / `!bomb`.

---

## Shared contract

All setpiece commands should follow the same fail-closed rules:

1. Active run required; hero alive
2. Respect spend kill-switch (`spend_disabled` / R8)
3. Fail → full refund; no half-applied terrain or flags
4. Default: block on boss floors / ascending / awkward special depths (unless a command explicitly allows them)
5. Prefer closest valid placement to the hero
6. Preserve path to stairs / exits; never overwrite entrance, exit, shop, or unique critical terrain
7. Spectacle: readable on stream (map ping, notice, companion alert)
8. Harmful commands should participate in death-cost inflation unless marked optional-risk

**Suggested cost ladder (relative, not final numbers):**

| Command | Role | Relative cost |
|---------|------|----------------|
| `!ambush` | Delayed tax on next pickup | Mid-high |
| `!mimic` | Convert existing loot into a fight | 40 / 60 / 120 by subtype |
| `!greed` | Optional greed island | High |
| `!pedestal` | Visible temptation with random upside | High–very high |
| `!trial` | Full setpiece floor | Highest (`annex` / `puzzle` > `gauntlet`) |

**Suggested build order (when implementing):**

1. `!ambush` — flag + one effect, least map gen
2. `!mimic` — entity swap on existing heaps
3. `!greed` — local terrain edit + guards
4. `!pedestal` — spawn + claim interaction
5. `!trial` — side level lifecycle

---

## 1. Mimic Vault — `!mimic <chest|gold|ebony>`

### Intent

Turn safe loot into a fight using chests already on the floor (or place an ebony mimic in fog).

### Locked decisions

- **Chat-picked subtype** with scaling costs:
  | Arg | Cost |
  |-----|------|
  | `chest` (regular) | 40 |
  | `gold` | 60 |
  | `ebony` | 120 |
- **No `crystal`** — crystal chests are too rare; the subtype would rarely succeed
- **Old Streamer.bot info `!mimic` will be removed** — this spend owns the name
- **No boss floors** — deny + refund
- **Ebony placement:** anywhere a normal ebony mimic can appear (doors, chests, items, stairs), but **only in an unexplored area**. If nothing qualifies on the current floor, trying the **next floor** is OK
- **Ebony spend timing:** charge **only if spawn succeeds** (current or deferred next-floor place). No charge / full refund on failure
- **Conversion rules = vanilla:** only targets the game already allows to become that mimic type. No extra softlock allowlist
- **Locked chests OK:** if a locked chest becomes a mimic, using the key lets it attack; killing the mimic drops the chest again so the key can open it (vanilla flow — not a softlock)

### Behavior

- `chest` / `gold`: find a matching chest on the current level → replace with that mimic type (vanilla-eligible only)
- Prefer closest valid target to the hero
- `ebony`: place over a normal ebony-valid host in unexplored fog (current floor first; else defer attempt to next floor)
- Refund / no charge if no chest is transformed / ebony never places

### Safety

- Follow vanilla mimic eligibility (including locked chests where vanilla allows)
- Skip already-opened / empty chests
- Ebony: unexplored-only; host types match vanilla ebony rules

### Open questions

- [ ] Next-floor ebony UX: tell chat “armed for next floor” while holding points until place/fail?

### Fail → refund

- No matching chest
- Ebony never finds a valid host (current or next floor)
- Boss floor
- Not in run / dead / spend disabled

---

## 2. Cursed Pedestal — `!pedestal <scroll|potion>`

### Intent

Spawn a pedestal with a fully random scroll or potion (including high-value ones like SoU / potion of strength). Claiming it always has a downside.

### Locked decisions

- **Identified on pedestal** — chat and hero see exactly what it is
- **Better item → harder claim cost** — downside severity scales with loot value (SoU / SoStr at the top end)
- **Up to 3 pedestals at a time** — reject / refund if 3 already exist on the floor
- **No boss floors** — deny + refund
- **Despawn on floor leave** — if the hero leaves the floor without claiming, pedestals on that floor are gone
- **Claim downsides** — one downside on claim; severity scales with depth / zone / reward rarity (see table)

### Behavior

- Arg selects pool: all scrolls **or** all potions
- Contents chosen randomly from that pool (rarity weighting TBD)
- Place pedestal on closest safe open tile near hero
- Item shown identified for stream spectacle
- Claiming applies **one** downside from the table below (tier weighted by reward rarity)

### Claim downsides

**Scaling knobs (shared)**

| Knob | Rule |
|------|------|
| Blood chalice prick | Depth-based prick level; **lower** in Sewers; **+1 or +2 levels** from reward rarity |
| Energize cost | Depth-based charge/resource cost; **extra** from reward rarity |

**Low**

| Downside | Rule |
|----------|------|
| Gold tax | Depth-based gold cost + extra for rarity |
| Chalice prick | Uses scaling knob above |
| Energize | Uses scaling knob above |

**Medium**

| Downside | Rule |
|----------|------|
| Guardian spawn | **1 guardian per zone** (chapter), **+1 more** from rarity |
| Bees | Spawn **2 or 3** bees (hostile pressure on claim) |

**High** (restricted)

| Downside | Rule |
|----------|------|
| Cursed gear | **Only** for potion of strength or upgrade scroll. Starts at **2** cursed slots; slot count also scales with depth |
| Darkness floor | Next **unthemed** floor becomes a darkness floor |

### Safety

- Placement: passable; not on traps / heaps / stairs; path preserved
- Guardian / bee spawn needs space or claim fails cleanly
- No persist across floors; max 3 live pedestals
- High downsides never roll for junk/common rewards; cursed gear only on SoStr / SoU

### Open questions

- [ ] Exact rarity tiers (what counts for +1 vs +2, guardian +1, bees 2 vs 3)
- [ ] Exact gold / energize / chalice formulas
- [ ] Downside roll weights per rarity band
- [ ] “Unthemed floor” definition for darkness
- [ ] Cost of the `!pedestal` spend itself (scroll vs potion same?)

### Fail → refund

- No safe placement tile
- Already 3 pedestals on the floor
- Boss floor
- Not in run / dead / spend disabled

---

## 3. Challenge Annex — `!trial <annex|gauntlet|puzzle>`

### Intent

Extra locked floor that does **not** cost food, with a challenge that must be completed to leave. Return to prior depth + cell.

### Shared trial rules

- Store return depth + cell
- Hunger paused (other timers TBD)
- Exit sealed until win condition
- Fail gen → refund; never strand without an exit plan
- One active trial at a time (proposed)
- Death in trial = normal run death (proposed — confirm)
- Optional later: streamer failsafe (`!trialbail` admin-only)

### `annex` — combat focused

- Small extra floor
- Something like a hidden summon-trap room
- Generally combat pressure
- Win: clear room / waves / marked elite (TBD)
- Reward: modest chest or biome loot

### `gauntlet` — cheaper

- Small extra floor with sequential rooms
- Fight each monster of the biome (or a curated subset)
- All are random champions
- Each drop keys to the next room / exit
- Length scales lightly with depth (e.g. 3–5 rooms)

### `puzzle` — premium

- Often chasm- or timed-movement-based
- Grants a reward of **double crystal door** (define package precisely later)
- Has an alchemy pot to allow complex solutions
- Prefer puzzles solvable with movement + pot alone (no softlock if missing rare items)

### Safety

- Ban on boss floors / shop floors if return is messy
- Gen failure must not leave hero mid-transition
- Clarify whether chat spends (`!spawn`, etc.) work inside trials

### Open questions

- [ ] Exact win conditions per subtype
- [ ] Exact crystal-door reward package for puzzle
- [ ] Hunger pause only, or also freeze other turn-based pressures?
- [ ] Summons / spends allowed inside?
- [ ] Relative costs: `gauntlet` < `annex` < `puzzle`?

### Fail → refund

- Trial gen fails
- Already in a trial
- Not in run / dead / spend disabled / blocked floor

---

## 4. Ambush Cache — `!ambush`

### Intent

Arm a one-shot trap: the next item the hero picks up triggers an Alarm cursed-wand effect (or equivalent swarm).

### Behavior

- Set a hero flag when spent
- Next heap / item pickup triggers Alarm effect, then clears the flag
- Announce clearly (“Ambush armed…”) for chat readability

### Safety / edge cases

- Define what counts as a pickup (gold, dew, keys, quest items?)
- Stacking: reject if already armed, or refresh
- If hero dies / descends / leaves floor before pickup: expire + refund vs persist (TBD)

### Open questions

- [ ] Duration / floor expiry?
- [ ] Gold-only heaps trigger?
- [ ] Exact effect = cursed wand Alarm, or custom spawn table?

### Fail → refund

- Already armed (if reject policy)
- Not in run / dead / spend disabled / blocked floor

---

## 5. Chasm Chest — `!greed`

### Intent

Optional greed setpiece: a chest surrounded by void / on an island in the void. Costs levitation (or equivalent) to reach. Always spectacle-marked.

### Behavior

- Spawn chest on a solid tile surrounded by chasm/void, closest safe site to hero
- Always magic-mapped
- Brief mind vision on the chest (a few turns)
- Reaching it is a player resource tax (levitation / ethereal chains / etc.), not an auto-death

### Safety (critical)

- Never sever path to exit / entrance
- Never chasm over stairs, locked doors, shops, unique terrain
- Prefer unused open space; if no site → refund
- Island must be reachable with standard levitation-class tools, not only rare wands
- No partial carve on failure

### Open questions

- [ ] Chest tier: depth-scaled? guaranteed good?
- [ ] Mimic chance, or always real (greed is already the tax)?
- [ ] Persist forever if unclaimed?
- [ ] Optional-risk → exempt from death-cost inflation?

### Fail → refund

- No safe carve / island site
- Not in run / dead / spend disabled / blocked floor

---

## Cross-cutting decisions (lock before coding)

| Topic | Notes |
|-------|--------|
| Optional vs forced | `!greed` / `!pedestal` are skippable; `!mimic` / `!ambush` / `!trial` are not. Cost + death inflation should reflect that. |
| Chat agency | Args (`chest`, `scroll`, `gauntlet`) vs pure random for drama |
| Boss / special floors | Default deny + refund (`!mimic`, `!pedestal` locked: no boss floors) |
| Spectacle | Map ping + paid notice / companion alert per command |
| Refund matrix | Exact fail cases per command (listed above; refine as specs harden) |
| Naming | Spend `!mimic` owns the name; remove old Streamer.bot info `!mimic` |

---

## Revision log

| Date | Note |
|------|------|
| 2026-08-02 | Initial brainstorm from setpiece ideas session (`!mimic`, `!pedestal`, `!trial`, `!ambush`, `!greed`) |
| 2026-08-02 | `!mimic`: chat-picked subtypes + cost ladder; remove old info cmd; no boss floors; ebony unexplored-only (may defer next floor) |
| 2026-08-02 | `!mimic`: costs 40/60/120; drop crystal; ebony charges only on successful spawn |
| 2026-08-02 | `!mimic`: vanilla eligibility only; locked chest → key wakes mimic → kill returns chest |
| 2026-08-02 | `!pedestal`: identified; downside scales with item value; max 3; no boss floors; despawn on floor leave |
| 2026-08-02 | `!pedestal`: claim downside tiers (low gold/chalice/energize; med guardian/bees; high curse gear / darkness) |
