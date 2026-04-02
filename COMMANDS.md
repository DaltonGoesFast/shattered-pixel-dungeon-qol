# Chat Commands — Spend points to mess with the run!

Earn points by chatting (1 per message, 30s cooldown). Super Chats & bits also give points!

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
| **!spawn** \<monster\> | Varies | Spawn a monster (cost varies by monster and **zone**). **Half price** when deeper than the mob’s native depth; **up to 3×** when spawning later-chapter mobs in earlier areas (see `points_command.py`). Examples: `!spawn rat`, `!spawn bat`, `!spawn scorpio` |
| **!champion** \<monster\> | 2× spawn | Spawn a **champion** version of that monster (**2×** the zone-adjusted `!spawn` cost — same early/late rules). Random type: Blazing, Projecting, Antimagic, Giant, Blessed, Growing. Examples: `!champion rat`, `!champion eye` |
| **!gold** \<amount\> | 5 / gold | Drop gold near the hero (**5** pts per gold, 1–100). Example: `!gold 25` |
| **!curse** | 100 | Curse a **random** equipped item. No slot needed — picks weapon, armor, ring, artifact, or misc at random. |
| **!gas** | 75 | Spawn random gas. Toxic, confusion, storm clouds, inferno, blizzard, and more! |
| **!scroll** | 100 | Use a random scroll. Like +10 Unstable Spellbook — 50% chance for exotic version! |
| **!trap** | 50 | Place a random visible trap 1–4 tiles from the hero. Pool of 27 traps (instant-death/high-damage blacklisted). |
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

**Champion:** 2× whatever `!spawn` would charge at the current depth (examples vary with zone; e.g. deep cheap mobs can be less than 2× table base).

*Costs are configurable by the streamer (points config / overlay).*

---

## Streamer.bot (points script)

### Super Chat and Cheer (donation points)

`points_command.py` applies the same stacking multipliers as chat earning: global **!doublepoints** 2×, then **subscriber / member** 2× (`isSubscribed` on Twitch, `userIsSponsor` on YouTube), then optional **top farder** 2×. Pass optional trailing CLI args (default `0`):

- `superchat <microAmount> <currencyCode> <username> [isSubscribed] [userIsSponsor] [topFarder]`
- `cheer <bits> <username> [isSubscribed] [userIsSponsor] [topFarder]`

Example args from Streamer.bot: `%rawInput0%` … then `%isSubscribed%`, `%userIsSponsor%`, and `0` or `1` for top farder if you compute it in C#.
