# Chat Commands — Shatter the Streamer

**Shatter the Streamer** — Chat plays along with a live *Shattered Pixel Dungeon* run. Earn points by chatting and spend them on spawns, curses, scrolls, and more that hit the hero in real time. Free commands light up the stream overlay; paid commands change the dungeon.

**Bestiary** — Free `!summon` marches monsters across the companion overlay and fills a **shared XP bar**. Unlock zones together (Sewers → Prison → Caves → City → Halls). Race the **Sprint** for each level (Hall of Fame + **100/200/300/400 donor pts** by zone; **one crown per viewer per stream**). Hold **Heat** (last 15 min of summon XP) for **personal 2×** point gains. Check `!bestiary`, `!heat`, `!sprint`, `!topsummoner`, `!summonhall`. Details: [docs/bestiary-summon-system.md](docs/bestiary-summon-system.md).

**Economy v1.1:** Earn **2 pts per message** (20s cooldown). Chat cap / bank % come from live `points_config.json` (`chat_point_cap` — currently often **300**); use **`!bank`** to save **10%** into **donor points** (permanent). Chat resets when the stream ends; donor points never expire. Chat blurb: **`!economy`** / **`!reminder`** (or bare **`!help`**) always uses the live cap.

**2× bonuses on chat earn (stack — up to 8×):** global !doublepoints + !fard → **Bestiary heat** leader (`!heat`) → sub/member. **Donations** (Super Chat, bits, gifts) use the same bonus sources but cap at **4×** total.

---

## Free commands (no points)

| Command | Description |
|---------|-------------|
| **!fard** | Once per stream per viewer: OBS flash + sound; extends **global 2×** for everyone (+3 min, +6 min for subs/members). Already used? Bot replies that you used your fard this stream. |
| **!summon** | Monster march on the **companion overlay** (not in-game). **60s** cooldown. Starts with **Sewers** mobs; fill the co-op XP bar to unlock more. `!summon rat` or random from the unlocked pool |
| **!bestiary** / **!summonlevel** | Co-op bar (level / zone / XP) + unlocked list + **your** sprint XP and heat XP |
| **!topsummoner** | **Sprint** leader this Bestiary level among viewers who haven’t crowned yet this stream (resets on level-up; not the 2× heat crown) |
| **!sprint** | Sprint leader **plus your rank/XP** this Bestiary level (same race as `!topsummoner`) |
| **!heat** / **!hot** | Rolling **15 min** heat leader — that viewer earns **personal 2×** on point gains |
| **!summonhall** | Hall of Fame — sprint winners for completed zones this stream |
| **!mysummons** | Your summon count this stream + sprint XP + heat XP |
| **!economy** / **!reminder** | Live chat-cap / earn / bank blurb from current `points_config.json` (use this for timed Streamer.bot reminders) |
| **!help** / **!commands** | Same live economy line + link to this doc |

*Technical setup:* [docs/fard-system.md](docs/fard-system.md), [docs/bestiary-summon-system.md](docs/bestiary-summon-system.md), [docs/summon-march-system.md](docs/summon-march-system.md).

### Stream info & fun (separate Streamer.bot commands — not R1)

| Command | Description |
|---------|-------------|
| **!kesha** | OBS overlay flash + sound (~2s); 60s global / 10 min per-user cooldown |
| **!mimic** / **!tooth** | Mimic sound if Mimic Tooth trinket is in the run |
| **!challenge** / **!challenges** | Reply with active challenges |
| **!seed** | Reply with current dungeon seed |

Setup: [docs/stream-info-commands.md](docs/stream-info-commands.md). C#: `Lastest UI/streamerbot/phase2/CheckMimicTooth.cs`, `ReadActiveChallenges.cs`, `ReadGameSeed.cs`.

---

## Extra chat spend commands

| Command | Cost | Description |
|---------|------|-------------|
| **!heal** | 25 | Heal hero ~15% HP. |
| **!cleanse** | 25 | Remove one random debuff. |
| **!dew** | 5 | Drop a dewdrop near the hero. |
| **!corruptally** | 40 | Summon a corrupted ally from the current biome. |
| **!hex** | 75 | Apply Hex debuff. |
| **!degrade** | 75 | Apply Degrade debuff. |
| **!sabotage** | 75 | Remove one random positive buff (visible icon). |

*Costs match `Lastest UI/points_config.json` / overlay; the streamer can change them.*

---

## Commands

| Command | Cost | Description |
|---------|------|-------------|
| **!points** | Free | **Chat points**, **donor points**, and **Bestiary** sprint/heat XP (e.g. `Chat: 120/500 \| Donor: 340 \| Bestiary: sprint 8 XP, heat 14 XP`) |
| **!bank** | Free | Preview bank conversion (chat → donor at **10%**) |
| **!bank all** | Free | Convert all current chat points to donor points |
| **!bank** \<amount\> | Free | Convert exactly \<amount\> chat points to donor points |
| **!toppoints** / **!leaderboard** | Free | Top 3 by **donor points** (all-time saved balance) |
| **!givepoints** \<amount\> \<target\> | Free | Transfer points to another viewer. Works on YouTube without `@`: `!givepoints 50 bob` (Twitch also accepts `@bob`). Spends chat points first, then donor points if needed. |
| **!spawn** \<monster\> | Varies | Spawn a monster. Uses the table **base** for that mob, then compares current **depth** to the mob’s natural **first** floor (`NATIVE_DEPTH` in `Lastest UI/points_command.py`). **Deeper** than that floor → **½** base (int divide, min 1). **Shallower** → `_dungeon_region` / `_early_spawn_multiplier` assign one of three factors from **chapter** alignment (5-floor blocks: sewers→…→halls; plus a same-chapter cave edge case): **100% / 120% / 140%** of base via **base × (1 + 0.20 × (tier − 1))**, tier ∈ {1,2,3}, rounded, min 1. Examples: `!spawn rat`, `!spawn bat`, `!spawn scorpio` |
| **!champion** \<monster\> | 2× spawn | Spawn a **champion** version (**2×** the same zone-adjusted cost as `!spawn` would be here—not 2× the raw table base). Random type: Blazing, Projecting, Antimagic, Giant, Blessed, Growing. Examples: `!champion rat`, `!champion eye` |
| **!gold** \<amount\> | 5 / gold | Drop gold near the hero (**5** pts per gold, 1–100). Example: `!gold 25` |
| **!curse** | 100 | Curse a **random** equipped item. No slot needed — picks weapon, armor, ring, artifact, or misc at random. |
| **!gas** | 75 | Spawn random gas. Toxic, confusion, storm clouds, inferno, blizzard, and more! |
| **!scroll** | 100 | Use a random scroll. Like +10 Unstable Spellbook — 50% chance for exotic version! |
| **!row** | 100 | Drop **Ring of Wealth**–style bonus loot near the hero (simulated kill from a random current-biome mob). Virtual ring tier scales by chapter (+1 sewers … +10 halls). **Always at least one item.** |
| **!trap** | 50 | Place a random visible trap 1–4 tiles from the hero. Pool of 27 traps (instant-death/high-damage blacklisted). |
| **!plant** | 30 | Plant a random seed near the hero. Fails if the **Barren Land** challenge is enabled. |
| **!bomb** | 75 | Drop a weighted random lit bomb 1–4 tiles from the hero. |
| **!transmute** | 150 | Transmute a random transmutable item from bag or equipped. Same rules as Scroll of Transmutation. |
| **!bee** | 40 | Summon an allied bee next to the hero for **150** turns. Fights for you like Elixir of Honeyed Healing. |
| **!ward** | 9 | Summon a ward near the hero. Level scales with depth: +0 sewers, +3 prison, +5 caves, +7 city, +8 halls. Upgrades existing ward if same tile. |
| **!buff** | 75 | Apply a random buff. Haste, Healing, Barrier, Invisibility, Levitation, and more! |
| **!debuff** | 50 | Apply a random debuff. Blindness, Slow, Roots, Daze, Weakness, and more! |
| **!wand** | 75 | Trigger a **random** cursed wand effect (rarity weighted like vanilla: mostly common/uncommon, rare/very rare possible). Burn, freeze, teleport, gas, sheep, and more! |
| **!doublepoints** \<minutes\> | — | **Streamer only.** 2× points for N minutes (max 120). Example: `!doublepoints 5` |

---

## Monster costs (base)

These are **before** half-off when deeper or chapter-gap surcharge (+20% per step when shallower, max +40%).

| Cost | Monsters |
|------|----------|
| 5 | rat |
| 10 | albino, snake, gnoll |
| 15 | crab, slime, swarm |
| 20 | thief, skeleton, dm100 |
| 25 | guard, necromancer, spinner |
| 30 | bat, brute |
| 35 | shaman |
| 40 | ghoul, elemental |
| 45 | warlock |
| 50 | monk, golem |
| 60 | succubus |
| 70 | eye |
| 80 | scorpio |

---

**Champion:** 2× whatever zone-adjusted `!spawn` would charge at the current depth (e.g. a discounted deep spawn stays discounted before the ×2).

*Costs are configurable by the streamer (points config / overlay).*

---

## Streamer.bot (HTTP gateway)

Live wiring uses **R1–R10** ([streamerbot-http-gateway-apply.md](docs/streamerbot-http-gateway-apply.md)), not per-command Run Program actions.

### Chat and spends

**R1** posts every points-related message to `POST /api/chat-command`. The server runs earn, spend, query, and meta (`!fard`, `!summon`, `!bestiary`, `!heat`, `!bank`, etc.) and returns the chat line as JSON.

### Donations (R4–R6)

Streamer.bot calls the overlay server (preferred):

- `POST http://127.0.0.1:5000/api/donation/cheer`
- `POST http://127.0.0.1:5000/api/donation/superchat`
- Gift membership / gift sub routes — see apply guide **Step 4–6**

`points_command.py` still implements the same math if you invoke CLI directly; the live bot should use HTTP so multipliers and v1.1 caps stay in one place.

Donation multipliers: global **!doublepoints** / **!fard** 2×, **Bestiary heat leader** 2×, **subscriber / member** 2× — **capped at 4× total** on donations (v1.1). Chat earn may still reach **8×**.

### Stream info (not R1)

`!kesha`, `!seed`, `!mimic`, `!challenge` — separate Command actions: [stream-info-commands.md](docs/stream-info-commands.md).

### Session end (R10)

**Stream Offline** → `POST /api/session/end` (debounced chat wipe + auto-bank). Details: [economy-v11-apply.md](docs/economy-v11-apply.md).
