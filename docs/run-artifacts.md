# Run Artifacts (Modifiers)

**Status:** Design pass in progress — **not implemented**. Landed artifacts are ready to build; paused/skipped ones need a later pass.

RoR2-style run toggles for Shattered Pixel Dungeon: enable or disable any amount at hero select, like challenges.

Call them **artifacts** in the UI if you want the RoR2 flavor. Internally use **Modifiers**. Vanilla already uses “artifact” for Cloak / Chalice / Rose / Holy Tome.

---

## Architecture

The challenge checkbox pattern is the right **UX**. It is the wrong **data model**.

Vanilla challenges are a 9-bit mask (`Dungeon.challenges`) locked in at hero select, saved with the run, shown on rankings, and scored at `1.25^n`. Champion badges key off that count. This list is mixed polarity: Honor/Glass/Death are extra difficulty; Command/Devotion/Rebirth are power or chaos. Putting them in that mask would inflate challenge count, pollute badges/score, and fight upstream if a 10th challenge is added.

**Parallel layer, same UI:**

- New bitmask or string set: `Dungeon.modifiers` / `SPDSettings.modifiers()`, copied at `Dungeon.init()` exactly like challenges.
- New window (or a second tab on the challenge window) on hero select. **Cannot enable or disable artifacts after a run has started** (same lock as challenges).
- Rankings / in-progress / victory screens list them, but they **do not** feed `Challenges.activeChallenges()`.
- Score: `1.0x` always, or a separate table later (Command −score, chaos slight +, hard mods +). Daily/custom-seed runs should forbid Command and Rebirth so seeds stay comparable.
- Query: `Dungeon.isModified(HONOR)` next to `Dungeon.isChallenged(...)`. Keep effects in a few hooks, not sprinkled like older challenges.
- **Mix-and-match:** any artifact combo is allowed unless a later note marks a hard exclusive. Balance is not a gating concern.

**Existing pipes worth reusing:**

| System | Where | Useful for |
|--------|--------|------------|
| Challenge checkboxes | `WndChallenges`, `HeroSelectScene` | UI shell |
| Transmute + replace | `ScrollOfTransmutation` | Metamorphosis, Enigma (in-class) |
| Category generator | `Generator.Category` | Command, Sacrifice equipment |
| Champion roll | `ChampionEnemy.rollForChampion()` | Honor |
| Mob rotation | `MobSpawner.getMobRotation()` | Kin, Dissonance |
| Chat spawn scaler | `SpawnScaled`, `SpawnScaleConfig` | Dissonance |
| Fly split | `Swarm.defenseProc()` | Swarms |
| Fall landing | `Chasm.heroLand()` | Frailty |
| Ankh revive | `Hero.die()`, shop stock | Death |
| Corruption DoT | `Corruption.act()` | Devotion |
| Forced RoW drops | `RingOfWealth.tryForBonusDrop(..., forcedWealthBonus)` | Prestige, Sacrifice |
| Remains file | `Bones` / `bones.dat` | Rebirth |
| 1 HP delayed spawn | `Wraith` | Soul |
| Tome choice window | `WndChooseSubclass` | Command |

---

## Easy (mostly one hook)

**Honor — all enemies are champions** ✅ landed  
- Eligible: regular floor mobs, respawns, chat summons. Skip bosses, pylons, quest NPCs, shopkeepers.
- Passive things (mimics-as-chests, sleeping statues, etc.) stay normal until they attack / aggro, then they get a champion type.
- Literal from floor 1. No vanilla early-floor crab/thief/guard/bat veto.
- One random champion type each.
- Hostile Champions challenge still rolls. If it would have made that mob a champion, it gets a **second** type on top of Honor’s. Honor alone = everyone 1x; Honor + challenge = everyone 1x, ~1/8 are 2x.

**Glass — RoR2 package** ✅ landed  
- Applies to the **hero and all allies** (Rose ghost, corrupted, bees, Mirror Images, wards, etc.). Enemies are not glassed.
- Hero HT = **1 per hero level** (lvl 1 → 1 HP, lvl 30 → 30 HP). Allies: cap HT at hero.lvl; if they were already lower, leave them.
- Outgoing damage is huge for hero and allies. Working number: **5×**. No extra incoming-damage multiplier — tiny HT is the fragility.
- Healing and regen shrink with HT. Vanilla PoH is `0.8*HT + 14`, which still full-heals at 30 HT — Glass must drop the flat +14 (and keep the heal a sip, not a reset).
- DoTs and chip (burn, bleed, starve, gas) stay at vanilla damage, so they are basically fatal.

**Frailty — jumping/falling is always fatal** ✅ landed  
- Any hero fall is lethal: chasm jump, knockback into a pit, pitfall trap, weak-floor room.
- Elixir of Feather Fall (and similar flying/levitation that already cancels a fall) still saves. That’s the expensive out.
- Keep the existing jump confirm dialog as-is (a later pass may change that UI).
- Mobs unchanged; they already die to chasms.

**Death — extra lives are gone** ✅ landed  
- Ankhs still generate and shops still sell them. They do not revive. Blessing one is a waste of dew/resources.
- Skip `Hero.die()` ankh revive and `WndResurrect` entirely while Death is on.
- Any other extra life is also gone, **except Berserker’s 0-HP berserk shield** (`Berserk`). That subclass mechanic stays.

**Devotion — allies don’t rot, and they stay put** ✅ landed  
- Every ally except **Mirror Images** (those stay vanilla). Includes corrupted mobs, honeyed-healing converts, Rose ghost, bees, wards, clones, hawks, etc.
- Combat damage still kills them normally. Background rot (Corruption’s `HT/100` per turn, and any similar ally decay) is off.
- Corrupting still full-heals them when they flip.
- Allies **do not follow between floors**. They remain on the floor they were created or converted on.

**Spite — everyone drops a bomb** ✅ landed  
- Every death, including bosses and chat summons. Nothing excluded.
- Normal enemies drop a **regular Bomb** item. Bosses drop a **random crafted bomb**.
- Normal loot still drops. The bomb is a real item you can pick up. Vanilla bomb fuse/timing applies (lit drop is fine — extra chaos). Explosion may destroy loot; that’s OK.
- Chasm deaths still drop: spawn the bomb on the **next floor** (same cell/column if possible), since the corpse never arrives.

---

## Medium (new rules, existing pipes)

**Kin — one type per floor** ✅ landed  
- Types stay in their **home region**. Boss floors (5, 10, 15, 20, 25) excluded. **Floor 26: no regular mobs** (no Kin type).
- Quest mobs, statues, mimics, piranhas stay as their normal extras.
- Each Kin floor has a **10%** chance to spawn only a rare/alt. Use that type’s alt if it has one; otherwise a rare from the **same region** (not halls rares in sewers).
- Kin + Dissonance: ignore the curated list. Each floor is still one species, but it can be **any** mob, STS-scaled to current depth.

| Floor | Region | Type | Exotic floor |
|------|--------|------|----------------|
| 1 | Sewers | Rat | Albino |
| 2 | Sewers | Gnoll | Gnoll Exile |
| 3 | Sewers | Crab | Hermit Crab |
| 4 | Sewers | Slime | Caustic Slime |
| 6 | Prison | Skeleton | region rare |
| 7 | Prison | Thief | Bandit |
| 8 | Prison | DM-100 | region rare |
| 9 | Prison | Necromancer | Spectral Necromancer |
| 11 | Caves | Bat | region rare |
| 12 | Caves | Brute | Armored Brute |
| 13 | Caves | Shaman | region rare |
| 14 | Caves | Spinner | region rare |
| 16 | City | Ghoul | region rare |
| 17 | City | Elemental | Chaos Elemental |
| 18 | City | Warlock | region rare |
| 19 | City | Monk | Senior |
| 21 | Halls | Succubus | region rare |
| 22 | Halls | Eye | region rare |
| 23 | Halls | Scorpio | Acidic |
| 24 | Halls | Scorpio | Acidic |

Unused on purpose: Snake, Swarm, Guard, DM-200, Golem (region already full). Floor 24 repeats Scorpio because halls only has three regulars.

**Dissonance — mixed roster, STS-scaled** ✅ landed  
- Natural **levelgen + respawns** pull from any regular mob. Mixed on the same floor (not one type — that’s Kin, or Kin+Dissonance).
- Late mobs on early floors scale **down** via existing `SpawnScaled` / `SpawnScaleConfig`. Early mobs on late floors stay weak (rats on 24 are fodder).
- Blacklist: chapter bosses, Yog/fists, quest uniques, Rat King. Piranhas only in water. Large mobs still need open space.
- **Chat summons are unchanged** — they already have their own scaler. Dissonance does not double-apply.

**Soul — wraith on every kill** ✅ landed  
- On death of any hostile (regulars, quest mobs, pylons, bosses, chat summons). **Not** allies.
- Spawn a real **Wraith** with vanilla depth scaling (`Wraith.adjustStats`). They sting if ignored.
- Wraiths **stay on that floor** (no follow downstairs).
- Splitting enemies still spawn wraiths, but stop after a few splits: no wraith if `generation >= 3` (original + first two clone generations drop; deeper clones don’t). Caps Swarm and the Swarms artifact.

**Evolution — global slow Growing** ✅ landed  
- Not per-floor stacks. Every enemy is a **scaled-down Growing**: the run itself grows on **turn count**, from turn 1.
- Hero gets a visible buff (same idea as Swarm Intelligence’s tracker) showing current evolution % / multiplier.
- Growing’s three factors all apply: melee damage, damage taken (`1/mult`), accuracy/evasion. **Plus HT** scaled with the same multiplier (reasonable extra HP).
- Regulars, respawns, and **chat summons** all read the current global multiplier. Bosses too (every enemy).
- Working numbers (tunable): multiplier starts at **1.00**, caps at **1.50** (+50% damage, ~33% less damage taken, +50% HT) at a **slow full-run** turn count (~50,000 hero turns). Playing fast stays well under the cap. Tune the curve in play.

**Prestige — regional mountain shrines** 🚧 paused, needs rework  
- Landed so far: optional, hero-only, shrine every floor, **per-region** (sewers shrines only juice Goo, etc.). One `!row`-style drop per shrine taken, paid on that boss, chapter wealth tiers.
- First shrine was meant to turn on Badder Bosses for that boss (or start stacking numbers if BB is already on). **BB is a boolean in code**, so extra stacks need a real design (replay BB deltas vs a multiplier vs loot-only after shrine 1). Also unresolved: cap per region, shrine on boss floors, N separate `!row`s vs one pile, shrine persistence.
- **Do not implement until this is reworked.**

**Metamorphosis — transmute equipped on first descent** ✅ landed  
- Fires when **depth increases for the first time** (`Statistics.deepestFloor`). Not ascent, not revisits, not same-depth branches.
- **All equipment:** weapon, armor, ring, artifact, misc, second weapon, trinket.
- **Uniques untouched** (Spirit Bow, Cloak, Holy Tome, staff body, class armor, pickaxe, etc.).
- Keep upgrade level, curse, and identify status. **Armor glyph and augmentation are rerolled** (weapons keep their enchant/augment unless we say otherwise later).
- Name stays Metamorphosis.

**Sacrifice — chests gone, kills pay the equipment budget** ✅ landed  
- Remove regular chests and **gold (locked) chests**. **Keep a gold chest if it holds the trinket catalyst.** Crystal chests stay. Shops stay. Sacrificial Fire rooms left alone.
- **No mimics except crystal mimics.** Regular and golden mimics do not spawn.
- Every hostile kill drops from its pool at 100% (junk if it has no pool). **No `LimitedDrops` caps.** Vanilla `maxLvl+2` overlevel gate is **off** so overleveled kills still pay.
- **Floor equipment is gone** (weapons/armor/missiles/wands/rings/artifacts no longer generate as ground loot). That budget is paid through kills instead: same kind of generated gear as floor loot, up to **+2/+3**, chance **high on the first few drops then decaying**. Consumable/gold piles can still exist.
- Equipment is “regularly generated” loot, not a separate 5–15% sprinkle on top of a full floor of gear.

---

## Hard (kit / persistence / every-mob)

**Command — pick any in category on pickup** ✅ landed  
- Window opens **after the item already exists** (pickup / open / buy), Tome-of-Mastery style. Levelgen still rolls a real item.
- Scope: **almost everything except gold and quest items**. **Shops count.**
- Lists are **not depth-gated**: sewer leather can become plate; any weapon (any tier) can become any other weapon; any potion; any scroll.
- **SoU / Potion of Strength:** only the **vanilla guaranteed count** can be chosen (chapter quotas). After that they’re gone from the lists. Command does not print extra SoUs/SoStr.
- Uniques/quest stay un-Commandable.
- Shop **price can stay on the rolled item** (cloth gold for plate is intended; Command is supposed to be OP). Chosen item **keeps the rolled upgrade / curse / identify**.

**Swarms — split like flies** ✅ landed  
- Hostiles split on hit like `Swarm` (need space, leftover HP, clone at half, `EXP = 0`).
- **Skip** bosses, large/immovable, mimics, ghouls (already two-phase), necro skeletons.
- Cap: **original + 2 clone generations** (no split at `generation >= 3`). Matches Soul’s wraith cap.
- Clones inherit champion buffs, `SpawnScaled`, Honor types, etc.
- **Allies do not split.**
- Clone deaths still drop Spite bombs (chains are intended).

**Enigma — class unique / on-use scramble** 🚧 skipped for now  
- Original pitch: reroll the hero unique on descent. Cross-class fights talent trees; in-class vs RoR2-style on-use scramble never got picked.
- **Do not implement until revisited.** Metamorphosis already leaves uniques untouched, so this slot is still available later.

**Rebirth — forced carry on amulet, consolation on death** ✅ landed  
- **Dailies / custom seeds: cannot enable.**
- On **Amulet pickup** you **must** sacrifice one **non-unique** (full upgrades/enchants OK). That item starts in the **next** run’s inventory. Only **one** carry — a new sacrifice replaces any previous queue, no snowball.
- Amulet itself and uniques cannot be the sacrifice.
- If you **die before sacrificing** (including dying while holding last run’s carry): the next Rebirth run starts with **one random loot item** instead (`Generator.random` — potion, dirk, anything floor loot can be). Not the lost carry.

**Vengeance — hostile allies / doppelganger** 🚧 skipped for now  
- Original pitch: all allies hostile, or a hero clone. Never picked.
- **Do not implement until revisited.**

---

## Skipped / parked

**Chaos** 🚧 skipped  
Vanilla friendly fire is already mostly on. On-use scramble overlaps Enigma. **Do not implement until revisited.**

**Delusion** 🚧 skipped  
Darkness already exists as a challenge. No SPD-shaped loop design. **Do not implement until revisited.**

---

## Conflicts to encode in the UI

| Pair | Why |
|------|-----|
| Kin + Dissonance | Not exclusive: one random species per floor, STS-scaled |
| Honor + Champion challenge | Honor = all 1x; challenge adds a 2nd type on ~1/8 |
| Swarms + Spite + Soul | Actor/explosion flood (intended chaos, cap at gen 3) |
| Glass + Frailty | Falls are just death |
| Command + Sacrifice | Menu every kill (intended; Command is OP) |
| Metamorphosis + Command | Transmute stays random; Command is pickup choice |

---

## Polarity (for score / badges later)

| Kind | Artifacts |
|------|-----------|
| Hard | Honor, Glass, Kin, Frailty, Death, Evolution, Dissonance, Swarms, Soul, Spite |
| Chaos | Metamorphosis |
| Mixed / player-positive | Command, Sacrifice, Devotion, Rebirth |
| Paused, needs rework | Prestige |
| Skipped for now | Enigma, Vengeance, Chaos, Delusion |

Treat these as **fun-run flags**, not Champion progress, unless you later add hard-only scoring.

---

## Build order (when implementing)

1. **Shell only** — modifier mask, hero-select UI, save/load, rankings text, `isModified()`. No gameplay.
2. Death, Frailty, Honor, Devotion, Glass
3. Kin, Dissonance (`SpawnScaled`), Soul, Spite, Evolution
4. Sacrifice, Metamorphosis
5. Swarms, Command, Rebirth
6. Prestige / Enigma / Vengeance / Chaos / Delusion only after a later design pass

Honor is the smallest combat slice. Command is the largest (UI, shops, quotas). Rebirth is the only cross-run state.
