# Run Pacts (Modifiers)

**This doc is the design spec, not a ship log.** Per-pact badges answer “are the rules decided?” They do **not** mean the pact is in the game.


| Badge               | Means                                               | Does not mean        |
| ------------------- | --------------------------------------------------- | -------------------- |
| ✅ design locked     | Open questions are answered; implement to this spec | The pact is playable |
| 🚧 paused / skipped | Do not implement until a later design pass          | —                    |


**In-game status** lives only in section headers and **Build order** at the bottom. Easy pacts are coded. Medium/hard pacts that are design-locked may or may not be coded yet — check Build order, then the code.

Optional run-wide rule changes for Shattered Pixel Dungeon: enable or disable any amount at hero select, like challenges.

**UI:** Pacts. **Code:** Modifiers (`Dungeon.modifiers`, `SPDSettings.modifiers()`, `Dungeon.isModified(...)`). Vanilla already uses “artifact” for Cloak / Chalice / Rose / Holy Tome — never use that word here.

This is a **single-player** feature. Design notes and player text never mention chat summons or stream hooks.

**Player copy:** Short and a little vague. State the bargain, not the edge cases (chests, challenge interactions, exemptions).

Window intro: *Pacts change the rules of this run. Some make the dungeon harder, some weirder, some more generous. They are not challenges.*

Rankings: `Pacts: Honor, Glass`.

---



## Architecture

The challenge checkbox pattern is the right **UX**. It is the wrong **data model**.

Vanilla challenges are a 9-bit mask (`Dungeon.challenges`) locked in at hero select, saved with the run, shown on rankings, and scored at `1.25^n`. Champion badges key off that count. This list is mixed polarity: Honor/Glass/Death are extra difficulty; Command/Devotion/Rebirth are power or chaos. Putting them in that mask would inflate challenge count, pollute badges/score, and fight upstream if a 10th challenge is added.

**Parallel layer, same UI:**

- New bitmask or string set: `Dungeon.modifiers` / `SPDSettings.modifiers()`, copied at `Dungeon.init()` exactly like challenges.
- New window (or a second tab on the challenge window) on hero select. **Cannot enable or disable pacts after a run has started** (same lock as challenges).
- Rankings / in-progress / victory screens list them, but they **do not** feed `Challenges.activeChallenges()`.
- Score: `1.0x` always, or a separate table later (Command −score, chaos slight +, hard mods +). Daily/custom-seed runs should forbid Command and Rebirth so seeds stay comparable.
- Query: `Dungeon.isModified(HONOR)` next to `Dungeon.isChallenged(...)`. Keep effects in a few hooks, not sprinkled like older challenges.
- **Mix-and-match:** any pact combo is allowed unless a later note marks a hard exclusive. Balance is not a gating concern.

**Existing pipes worth reusing:**


| System               | Where                                                  | Useful for                                     |
| -------------------- | ------------------------------------------------------ | ---------------------------------------------- |
| Challenge checkboxes | `WndChallenges`, `HeroSelectScene`                     | UI shell                                       |
| Transmute + replace  | `ScrollOfTransmutation` / `Recycle`                    | Metamorphosis (equipped), Enigma (consumables) |
| Category generator   | `Generator.Category`                                   | Command, Sacrifice equipment                   |
| Champion roll        | `ChampionEnemy.rollForChampion()`                      | Honor                                          |
| Mob rotation         | `MobSpawner.getMobRotation()`                          | Kin, Dissonance                                |
| Chat spawn scaler    | `SpawnScaled`, `SpawnScaleConfig`                      | Dissonance                                     |
| Fly split            | `Swarm.defenseProc()`                                  | Swarms                                         |
| Fall landing         | `Chasm.heroLand()`                                     | Frailty                                        |
| Ankh revive          | `Hero.die()`, shop stock                               | Death                                          |
| Corruption DoT       | `Corruption.act()`                                     | Devotion                                       |
| Forced RoW drops     | `RingOfWealth.tryForBonusDrop(..., forcedWealthBonus)` | Prestige, Sacrifice                            |
| Remains file         | `Bones` / `bones.dat`                                  | Rebirth                                        |
| 1 HP delayed spawn   | `Wraith`                                               | Soul                                           |
| Tome choice window   | `WndChooseSubclass`                                    | Command                                        |


---



## Easy (mostly one hook) — implemented

**Honor — all enemies are champions** ✅ design locked  

- **Player:** You refuse easy fights. Every enemy is a champion.
- Eligible: regular floor mobs and respawns. Skip bosses, pylons, quest NPCs, shopkeepers.
- Passive things stay normal until they attack / aggro, then they get a champion type.
- Literal from floor 1. No vanilla early-floor crab/thief/guard/bat veto.
- One random champion type each.
- Hostile Champions challenge still rolls. If it would have made that mob a champion, it gets a **second** type on top of Honor’s. Honor alone = everyone 1x; Honor + challenge = everyone 1x, ~1/8 are 2x.

**Glass — frail, heavy hits** ✅ design locked  

- **Player:** You and yours are glass. Little health, devastating blows.
- Applies to the **hero and all allies**. Enemies are not glassed.
- Hero HT = **1 per hero level** (lvl 1 → 1 HP, lvl 30 → 30 HP). Allies: cap HT at hero.lvl; if they were already lower, leave them.
- Outgoing damage is huge for hero and allies. Working number: **5×**. No extra incoming-damage multiplier — tiny HT is the fragility.
- Healing and regen shrink with HT. Vanilla PoH is `0.8*HT + 14`, which still full-heals at 30 HT — Glass must drop the flat +14 (and keep the heal a sip, not a reset).
- DoTs and chip (burn, bleed, starve, gas) stay at vanilla damage, so they are basically fatal.

**Frailty — jumping/falling is always fatal** ✅ design locked  

- **Player:** The pit does not forgive. Every fall is death.
- Any hero fall is lethal: chasm jump, knockback into a pit, pitfall trap, weak-floor room.
- Elixir of Feather Fall (and similar flying/levitation that already cancels a fall) still saves. That’s the expensive out.
- Keep the existing jump confirm dialog as-is (a later pass may change that UI).
- Mobs unchanged; they already die to chasms.

**Death — extra lives are gone** ✅ design locked  

- **Player:** You have one life. There is no coming back.
- Ankhs still generate and shops still sell them. They do not revive. Blessing one is a waste of dew/resources.
- Skip `Hero.die()` ankh revive and `WndResurrect` entirely while Death is on.
- Any other extra life is also gone, **except Berserker’s 0-HP berserk shield** (`Berserk`). That subclass mechanic stays.

**Devotion — allies don’t rot, and they stay put** ✅ design locked  

- **Player:** Your allies endure, but they stay where you leave them.
- Every ally except **Mirror Images** (those stay vanilla). Includes corrupted mobs, honeyed-healing converts, Rose ghost, bees, wards, clones, hawks, etc.
- Combat damage still kills them normally. Background rot (Corruption’s `HT/100` per turn, and any similar ally decay) is off.
- Corrupting still full-heals them when they flip.
- Allies **do not follow between floors**. They remain on the floor they were created or converted on.

**Spite — everyone drops a bomb** ✅ design locked  

- **Player:** Every death leaves a bomb behind.
- Every death, including bosses. Nothing excluded.
- Normal enemies drop a **regular Bomb** item. Bosses drop a **random crafted bomb**.
- Normal loot still drops. The bomb is a real item you can pick up. Vanilla bomb fuse/timing applies (lit drop is fine — extra chaos). Explosion may destroy loot; that’s OK.
- Chasm deaths still drop: spawn the bomb on the **next floor** (same cell/column if possible), since the corpse never arrives.

**Soul — a wraith from every eligible foe** ✅ design locked  

- **Player:** Every soul escapes into the dungeon.
- On death of a hostile that is not a wraith. Allies do not count.
- Spawn a regular `Wraith` via `Wraith.spawnAt` (vanilla `Dungeon.scalingDepth()`).
- Wraiths do not spawn wraiths.

---



## Medium (new rules, existing pipes)

✅ here is design-locked only. Whether a pact is playable is in **Build order**, not this badge.

**Kin — one type per floor** ✅ design locked  

- Types stay in their **home region**. Boss floors (5, 10, 15, 20, 25) excluded. **Floor 26: no regular mobs** (no Kin type).
- Quest mobs, statues, mimics, piranhas stay as their normal extras.
- Each Kin floor has a **10%** chance to spawn only a rare/alt. Use that type’s alt if it has one; otherwise a rare from the **same region** (not halls rares in sewers).
- Kin + Dissonance: ignore the curated list. Each floor is still one species, but it can be **any** mob, STS-scaled to current depth.


| Floor | Region | Type        | Exotic floor         |
| ----- | ------ | ----------- | -------------------- |
| 1     | Sewers | Rat         | Albino               |
| 2     | Sewers | Gnoll       | Gnoll Exile          |
| 3     | Sewers | Crab        | Hermit Crab          |
| 4     | Sewers | Slime       | Caustic Slime        |
| 6     | Prison | Skeleton    | region rare          |
| 7     | Prison | Thief       | Bandit               |
| 8     | Prison | DM-100      | region rare          |
| 9     | Prison | Necromancer | Spectral Necromancer |
| 11    | Caves  | Bat         | region rare          |
| 12    | Caves  | Brute       | Armored Brute        |
| 13    | Caves  | Shaman      | region rare          |
| 14    | Caves  | Spinner     | region rare          |
| 16    | City   | Ghoul       | region rare          |
| 17    | City   | Elemental   | Chaos Elemental      |
| 18    | City   | Warlock     | region rare          |
| 19    | City   | Monk        | Senior               |
| 21    | Halls  | Succubus    | region rare          |
| 22    | Halls  | Eye         | region rare          |
| 23    | Halls  | Scorpio     | Acidic               |
| 24    | Halls  | Scorpio     | Acidic               |


Unused on purpose: Snake, Swarm, Guard, DM-200, Golem (region already full). Floor 24 repeats Scorpio because halls only has three regulars.

**Dissonance — mixed roster, STS-scaled** ✅ design locked  

- Natural **levelgen + respawns** pull from any regular mob. Mixed on the same floor (not one type — that’s Kin, or Kin+Dissonance).
- Late mobs on early floors scale **down** via frozen Default `SpawnScaled` numbers (not live stream presets). No spawn-in paralysis. Early mobs on late floors stay weak in combat (rats on 24 are fodder) but pay that **chapter’s kill EXP** and keep an open loot/`maxLvl` gate so the floor still levels and can roll wealth.
- Blacklist: chapter bosses, Yog/fists, quest uniques, Rat King. Piranhas only in water. Large mobs still need open space.
- **Chat summons are unchanged** — they already have their own scaler. Dissonance does not double-apply.

**Evolution — global growth on descent** ✅ design locked  

- **Player:** The dungeon itself grows stronger as you descend. Every enemy shares that growth.
- Not turn-based. Steps when **depth increases for the first time** (`Statistics.deepestFloor`), same clock as Metamorphosis / Enigma. Quest branches and ascent do not step.
- `multiplier = 1 + min(0.50, 0.02 * (deepestFloor - 1))`. Floor 1 = 1.00, Goo (5) = 1.08, Yog (25) = 1.48, floor 26 = 1.50.
- One global value. The next hit anywhere (including mobs left upstairs) uses the new step. Nothing stored on the mob.
- Eligible: every `alignment == ENEMY` (bosses, quest hostiles, mimics, statues, wraiths). Hero / allies / neutrals unchanged.
- Four stats at the same multiplier: outgoing damage (melee **and** zaps/beams/boss specials), accuracy, evasion, and **effective HP** (damage taken = `1/mult`, matching Growing’s champion blurb). Health bars do not grow.
- Blob/buff DoTs whose source is not the enemy stay vanilla damage. Hits into an evolved enemy are still reduced.
- Stacks by multiplying with a real Growing champion and with `SpawnScaled`. Incoming reduction is ceiled like champions.
- Hero gets a visible tracker buff (percent) like Swarm Intelligence’s; combat does not depend on it.

**Prestige — regional mountain shrines** 🚧 paused, needs rework  

- Decided so far (still not a complete spec): optional, hero-only, shrine every floor, **per-region** (sewers shrines only juice Goo, etc.). One `!row`-style drop per shrine taken, paid on that boss, chapter wealth tiers.
- First shrine was meant to turn on Badder Bosses for that boss (or start stacking numbers if BB is already on). **BB is a boolean in code**, so extra stacks need a real design (replay BB deltas vs a multiplier vs loot-only after shrine 1). Also unresolved: cap per region, shrine on boss floors, N separate `!row`s vs one pile, shrine persistence.
- **Do not implement until this is reworked.**

**Metamorphosis — transmute equipped on first descent** ✅ design locked  

- Fires when **depth increases for the first time** (`Statistics.deepestFloor`), including **run start** (0→1 remakes starting kit). Not ascent, not revisits, not same-depth branches.
- **All equipment:** weapon, armor, ring, artifact, misc, second weapon, trinket.
- **Uniques untouched** (Spirit Bow, Cloak, Holy Tome, class armor, pickaxe, etc.). Trinkets remake despite `unique`. **Mage's Staff** remakes its imbued wand (SoT-style); the staff body stays.
- Keep upgrade level, curse, and identify status. **Armor glyph and augment are rerolled**; armor becomes any other regular tier (cloth↔plate). **Weapon enchant and augment are also rerolled** (cursed weapons stay in the curse-enchant family).
- Name stays Metamorphosis.

**Sacrifice — loose treasure is carried by the monsters** ✅ design locked  

- **Player:** The dungeon's loose treasure is carried by its monsters. Kill them to claim it. Locked, secret, and puzzle rewards stay where they lie.
- Main dungeon only (`Dungeon.branch == 0`, regular floors). Boss floors, the vault, and the mines are untouched.
- Ungated loot is rolled normally, then taken off the ground and dealt to enemies on that floor: one item per carrier, round robin, wrapping when the hoard outnumbers the roster. Carriers are sorted by cell, then shuffled, so a seeded floor deals the same way each time. Gold is carried and is not Command loot; every other cargo item is. Killing the carrier drops that cargo beside its own loot. A chasm death sends the cargo down the pit. If nobody can carry, the hoard is put back as ordinary heaps.
- A carrier is a hostile enemy in a standard or connection room. Mimics, bosses, and minibosses do not carry. Rooms under `.rooms.quest.` are not open, even when they extend `StandardRoom`.
- Collected from those open rooms: heaps, regular chests, and skeletons, plus the items inside a suspicious-chest mimic. An emptied heap is removed. An emptied mimic is removed. The floor's usual 3/4/5 generated drops (plus 2 on a large floor) never land; they go straight into the hoard. Carriable prizes still waiting in `itemsToSpawn` do too.
- Left where they are: keys, torches, ceremonial candles, the guidebook, journal pages, dried rose petals, and the trinket catalyst. Locked chests, crystal chests, tombs, remains, and shop stock are never collected. Special rooms, secret rooms, quest rooms, and shops keep what they hold. Hidden spyglass piles, bones, darkness torches, and cached rations are placed after the sweep and stay on the floor.
- The trinket catalyst stays where it spawned. A room that already claimed it keeps that placement. If it is still unplaced, it drops as a locked chest with its golden key, the same as a run without this pact.
- Native kill loot is not rewritten. `Mob.rollToDropLoot` still uses vanilla `lootChance`, `LimitedDrops`, and the `maxLvl+2` gate, and clones still drop nothing there. A few exotic bonus drops skip their own clone and overlevel early-return while Sacrifice is on (gnoll exile's extra items, hermit crab's armor, caustic slime's blob, the DM-201 shard, the spectral necromancer's scroll, and the monk's Imp token check). The shared roll they call underneath is still gated.

**Crimson — heal by striking, the flask is a weapon** ✅ design locked  

- **Player:** Striking restores you. A thrown healing potion bursts for damage and poisons.
- Hero only. A hero attack (melee, thrown, spirit bow) that removes hit points heals `max(1, basis/4)`, capped at missing HP. Basis = min(damage just before Glass’s 5×, HP actually removed). Shields and overkill don’t count. **With Glass on, every such hit heals a flat 1.**
- DoT ticks, wand zaps, bombs, ally hits, and separate enchant procs don’t heal. Vampiric, soul mark, regen, Chalice, Salt Cube, level-up HP, ankhs, and food buffs stay.
- No hero HP from: PoH drink (still cures), honeyed/aquatic elixirs, dew + waterskin (Shielding Dew barrier stays), sungrass, Water of Health (cure/uncurse/hunger stay), healing darts, Hearty Meal. Ally heals and barriers stay.
- Thrown PoH on an **enemy**: `4 + depth` impact through armor (no Glass, no enchants), heal quarter of HP removed (min 1), plus Venomous-style poison: 3-turn delay if fresh, extend `3 + depth/5`. Anyone else / empty tile: splash. No beneficial-throw confirm.
- Pharmacophobia: throw is splash only. Drink keeps the challenge’s cure + `pharmacophobiaProc`.

**Guild — each shop keeps one trade** ✅ design locked  

- **Player:** Each shop keeps one trade, and pays more for it. Two of a kind may hide which is finer.
- Spoils pact. Floor loot is unchanged. Buy price stays `Shopkeeper.sellPrice` (value × 5 × (depth/5 + 1)). The keeper does not gouge.
- **Crafts:** floor 6 Arms, floor 11 Mail, floor 16 Arcana, imp (depth 20/21) Stores.
- **Staples on every counter** (known, never part of the gamble): the next `ChooseBag` bag, one Potion of Healing, one Small Ration, hourglass sandbags (same rules), ankhs only through `Legacy.shopStocksAnkh()`, and three Torches on the imp only.
- **Shelves:**
  - Arms: 4 T2 melee, 5 T3 (50% chance one T3 becomes T4 at 3/4 or T5 at 1/4), 5 T2 missiles, 2 `TippedDart.randomTipped(2)`, 1 Stylus, 2 Remove Curse, 1 Stone of Augmentation.
  - Mail: 5 Mail, 1 Leather, 1 Scale, 3 Plate, 1 Remove Curse, 1 Stone of Augmentation.
  - Arcana: 9 wands, 5 rings, 1 artifact from the artifact deck (never a duplicate), 2 Identify.
  - Stores: 1 Magic Mapping, Alchemize ×2–3, 6 potions, 6 scrolls, two rolls of bomb / double bomb / honeypot (1/4, 1/2, 1/4 each).
- Gear rolls use `Generator.randomUsingDefaults` so the deck does not eat duplicates. Two of the same piece can share a shelf.
- **Hidden roll** (melee, missiles, armor, wands, rings, the artifact): the class’s own `random()` sets the curse at floor odds (30%). Level is then rerolled at shelf odds: +0 45%, +1 35%, +2 15%, +3 5% (wands refill to the new max charges). The artifact stays +0. Weapon and armor `random()` already applies Parchment Scrap’s curse and enchant multipliers; a good enchant or glyph is held off the item on the shelf (`shelfEnchant` / `shelfGlyph`) and applied when the item is collected into the pack, so it adds no name, glow, or price tell. Curse enchants and glyphs stay on the item (hidden until `cursedKnown`). Level and curse stay, `levelKnown` and `cursedKnown` are false, and `identify()` is never called. Title, glow, examine (+0 stats), and price match across copies. After purchase, vanilla identify rules apply. Fireblast and Regrowth show the 1-charge blurb while the level is unknown.
- **Sell premium:** at each counter, its own trade sells for 130% of `value()` (Arms: weapons and thrown; Mail: armor except class armor; Arcana: wands, rings, artifacts; Stores: potions, scrolls, bombs, honeypots, Alchemize, torches). Buyback charges the same amount. Everything else sells at `value()`.
- **Room size:** same as vanilla, sized to the stock so the shelf has no empty stretch. The imp shop stays 9×9.
- Not seed-banned. Command, Sacrifice, Metamorphosis, Enigma, Hex, and Crimson are unchanged; Command’s shop swap keeps the hidden level and curse.

---



## Hard (kit / persistence / every-mob)

✅ here is design-locked only. Whether a pact is playable is in **Build order**, not this badge.

**Command — pick any in category on pickup** ✅ design locked  

- Window opens **after the item already exists** (pickup / open / buy), Tome-of-Mastery style. Levelgen still rolls a real item.
- Scope: **almost everything except gold and quest items**. **Shops count.**
- Lists are **not depth-gated**: sewer leather can become plate; any weapon (any tier) can become any other weapon; any potion; any scroll.
- **SoU / Potion of Strength:** only the **vanilla guaranteed count** can be chosen (chapter quotas). After that they’re gone from the lists. Command does not print extra SoUs/SoStr.
- Uniques/quest stay un-Commandable.
- Shop **price can stay on the rolled item** (cloth gold for plate is intended; Command is supposed to be OP). Chosen item **keeps the rolled upgrade / curse / identify**.

**Swarms — split like flies** ✅ design locked  

- Hostiles split on hit like `Swarm` (need space, leftover HP, clone at half current HP, `EXP = 0`).
- **Also halve max HP** so total health does not grow (e.g. 40 HP / 10 dmg → two at 15/20). Parent keeps the odd HT/HP point.
- **Skip** bosses, large/immovable, mimics, ghouls (already two-phase), necro skeletons, **wraiths**.
- Cap: **original + 2 clone generations** (no split at `generation >= 3`).
- Clones inherit champion buffs, `SpawnScaled`, Honor types, etc.
- **Clones drop nothing** (no loot, no scripted/quest rewards). Spite bombs still drop. Soul still spawns from non-wraith deaths.
- **Allies do not split.**
- Real flies keep unlimited full-HP splits when Swarms is off; with the pact on they use this path once (no double split).

**Enigma — recycle consumables + remake spare gear on first descent** ✅ design locked  

- **Player:** The things you carry will not be the things you brought down.
- Same trigger as Metamorphosis: fires when **depth increases for the first time** (`Statistics.deepestFloor`), including run start. Not ascent, not revisits, not same-depth branches.
- **Consumables — Recycle**, not regular↔exotic. Same-category reroll (`Recycle` / `Generator.randomUsingDefaults`).
- **In scope (consumables):** potions (not brews/elixirs), scrolls, seeds, runestones, tipped darts, **food**. Recurse into pouches / bandolier / scroll holder / holster.
- **In scope (spare gear):** any **unequipped** weapon, armor, ring, artifact, wand, missile, or trinket in the bag. Same remake rules as Metamorphosis (any-tier armor, enchant/glyph reroll, staff imbued wand). Worn slots are Metamorphosis’s job.
- **Out of scope:** worn equipment, gold, bombs, keys, bags, waterskin, dew vial, quest items, ankhs, uniques (Spirit Bow, Cloak, etc.).
- **Upgrade currency is untouched:** Scroll of Upgrade, Potion of Strength, and their exotics (Enchantment, Mastery). Do not recycle those stacks; other items must not be allowed to become them either (reroll if the generator lands on one).
- **Stack as a unit:** 5 Healing → 5 of one other potion, not five independent rolls.
- Name stays Enigma. Old unique-reroll / on-use-scramble pitch is retired.

**Rebirth — forced carry on amulet, consolation on death** ✅ design locked  

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
Vanilla friendly fire is already mostly on. On-use scramble is retired with the old Enigma pitch. **Do not implement until revisited.**

**Delusion** 🚧 skipped  
Darkness already exists as a challenge. No SPD-shaped loop design. **Do not implement until revisited.**

---



## Conflicts to encode in the UI


| Pair                       | Why                                                                                           |
| -------------------------- | --------------------------------------------------------------------------------------------- |
| Kin + Dissonance           | Not exclusive: one random species per floor, STS-scaled                                       |
| Honor + Champion challenge | Honor = all 1x; challenge adds a 2nd type on ~1/8                                             |
| Swarms + Spite + Soul      | Actor/explosion flood (intended chaos). Soul and Swarms both skip wraiths; Swarms caps clones at gen 3 |
| Glass + Frailty            | Falls are just death                                                                          |
| Command + Sacrifice        | Menu every kill (intended; Command is OP)                                                     |
| Metamorphosis + Command    | Transmute stays random; Command is pickup choice                                              |
| Enigma + Metamorphosis     | Complementary: Meta remakes worn (+trinkets); Enigma remakes bag consumables + spare gear |
| Enigma + Command           | Command is pickup choice; stairs still recycle the bag                                        |


---



## Polarity (for score / badges later)


| Kind                    | Artifacts                                                                     |
| ----------------------- | ----------------------------------------------------------------------------- |
| Hard                    | Honor, Glass, Kin, Frailty, Death, Evolution, Dissonance, Swarms, Soul, Spite |
| Chaos                   | Metamorphosis, Enigma                                                         |
| Mixed / player-positive | Command, Sacrifice, Devotion, Rebirth                                         |
| Paused, needs rework    | Prestige                                                                      |
| Skipped for now         | Vengeance, Chaos, Delusion                                                    |


Treat these as **fun-run flags**, not Champion progress, unless you later add hard-only scoring.

---



## Build order (what is actually in the game)

1. **Shell only** — modifier mask, hero-select UI, save/load, rankings text, `isModified()`. No gameplay.
2. Death, Frailty, Honor, Devotion, Glass, Spite, Soul ✅ done
3. Kin, Dissonance (`SpawnScaled`) ✅ done; Evolution ✅ done
4. Sacrifice ✅ done; Metamorphosis, Enigma ✅ done
5. Swarms ✅ done; Command ✅ done; Rebirth
6. Crimson ✅ done
7. Guild ✅ done
8. Prestige / Vengeance / Chaos / Delusion only after a later design pass

Honor is the smallest combat slice. Command is the largest (UI, shops, quotas). Rebirth is the only cross-run state.