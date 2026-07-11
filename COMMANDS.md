# Chat Commands — Spend points to mess with the run!

Earn points by chatting (1 per message, 30s cooldown). Super Chats & bits also give points!

**2× bonuses (stack — up to 8×):** global !doublepoints + !fard → personal top summoner (!summon leader) → sub/member.

---

## Free commands (no points)

| Command | Description |
|---------|-------------|
| **!fard** | Once per stream per viewer: OBS flash + sound; extends **global 2×** for everyone (+1 min, +5 min for subs/members) |
| **!summon** | Monster march on the **companion overlay** (not in-game). **60s** cooldown. `!summon rat` or random monster (same pool as !spawn) |
| **!topsummoner** | Session summon leader — earns **personal 2×** on point gains |
| **!mysummons** | Your summon count this stream |

*Technical setup:* [docs/fard-system.md](docs/fard-system.md), [docs/summon-march-system.md](docs/summon-march-system.md).

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
| **!points** | Free | Check your balance |
| **!toppoints** / **!leaderboard** | Free | Top 3 point holders |
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

## Streamer.bot (points script)

### Super Chat, Cheer, and gift subs (donation points)

These do **not** run by themselves — **Streamer.bot** must call `points_command.py` (or POST to the overlay server) on the matching trigger. See `docs/streamerbot-points-from-scratch.md` **Actions 20, 21, and 40**.

`points_command.py` applies the same stacking multipliers as chat earning: global **!doublepoints** (and **!fard** extensions) 2×, then **top summoner** 2× (`top_summoner.txt` from `!summon`), then **subscriber / member** 2× (`isSubscribed` on Twitch, `userIsSponsor` on YouTube). Pass optional trailing CLI args for sub/member (default off):

- `superchat <microAmount> <currencyCode> <username> [isSubscribed] [userIsSponsor]`
- `cheer <bits> <username> [isSubscribed] [userIsSponsor]`
- `giftmembership <username> [tier] [isSubscribed] [userIsSponsor]` — Twitch: `%recipientUserName%`; YouTube gift membership: `%gifterUserName%` (API has no recipient name)

**HTTP alternative** (overlay server must be running): `POST http://127.0.0.1:5000/api/donation/superchat`, `/api/donation/cheer`, `/api/donation/gift-membership` with JSON body (`username`, `microAmount` / `bits` / `tier`, optional flags).

Example Streamer.bot args: `points_command.py superchat %microAmount% %currencyCode% %userName% %isSubscribed% %userIsSponsor%` — if real Super Chats get 0 points, try `%user%` instead of `%userName%`, or enable debug via empty `Lastest UI/superchat_debug.txt`.
