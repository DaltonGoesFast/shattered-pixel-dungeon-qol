# Chat Commands — Spend points to mess with the run!

Earn points by chatting (1 per message, 30s cooldown). Super Chats & bits also give points!

---

## Helpers vs Hurters

When enabled, chatters are assigned alternating roles on first chat: **helper** or **hurter**. Helpers earn bonus points when you kill a boss; hurters earn when you die. Each side gets **50% off** certain commands: helpers on bee, ward, buff; hurters on curse, gas, trap, debuff. **!myside** shows your current side. **!switch** costs 150 pts and has a long cooldown — choose wisely!

| Command | Description |
|---------|-------------|
| **!myside** | Remind yourself which side (helper/hurter) you're on. No cost. |
| **!switch** | Switch from helper to hurter or vice versa. 150 pts, long cooldown. |

**Helper-exclusive commands:**

| Command | Description |
|---------|-------------|
| **!heal** | Heal hero ~15% HP (50 pts). |
| **!cleanse** | Remove one random debuff (50 pts). |
| **!dew** | Drop a dewdrop near the hero (10 pts). |

**Hurter-exclusive commands:**

| Command | Description |
|---------|-------------|
| **!hex** | Apply Hex debuff (75 pts). |
| **!degrade** | Apply Degrade debuff (75 pts). |
| **!sabotage** | Remove one random buff (75 pts). |

*Costs and role exclusivity are configurable in the points config overlay.*

---

## Commands

| Command | Description |
|---------|-------------|
| **!points** | Check your balance |
| **!toppoints** / **!leaderboard** | Top 3 point holders |
| **!spawn** \<monster\> | Spawn a monster (cost varies). Half price when spawned beyond its native area (e.g. sewer mobs in prison+). Examples: `!spawn rat`, `!spawn bat`, `!spawn scorpio` |
| **!champion** \<monster\> | Spawn a **champion** version of that monster (2× base cost, **no zone discount**). Random type: Blazing, Projecting, Antimagic, Giant, Blessed, Growing. Examples: `!champion rat`, `!champion eye` |
| **!gold** \<amount\> | Drop gold near the hero (5 pts per gold, 1–100). Example: `!gold 25` |
| **!curse** | Curse a **random** equipped item (200 pts). No slot needed — picks weapon, armor, ring, artifact, or misc at random. |
| **!gas** | Spawn random gas (75 pts). Toxic, confusion, storm clouds, inferno, blizzard, and more! |
| **!scroll** | Use a random scroll (100 pts). Like +10 Unstable Spellbook — 50% chance for exotic version! |
| **!trap** | Place a random visible trap 1–4 tiles from the hero (50 pts). Pool of 27 traps (instant-death/high-damage blacklisted). |
| **!transmute** | Transmute a random transmutable item from bag or equipped (150 pts). Same rules as Scroll of Transmutation. |
| **!bee** | Summon an allied bee next to the hero for 50 turns (75 pts). Fights for you like Elixir of Honeyed Healing. |
| **!ward** | Summon a ward near the hero (9 pts). Level scales with depth: +0 sewers, +3 prison, +5 caves, +7 city, +8 halls. Upgrades existing ward if same tile. |
| **!buff** | Apply a random buff (75 pts). Haste, Healing, Barrier, Invisibility, Levitation, and more! |
| **!debuff** | Apply a random debuff (50 pts). Blindness, Slow, Roots, Daze, Weakness, and more! |
| **!wand** \<tier\> | Trigger a cursed wand effect. **Tier required:** common (50 pts), uncommon (100 pts), rare (200 pts), or veryrare (400 pts). Burn, freeze, teleport, gas, sheep, and more! |
| **!doublepoints** \<minutes\> | **Streamer only.** 2× points for N minutes (max 120). Example: `!doublepoints 5` |

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

**Champion:** 2× base cost (no zone discount). e.g. !champion rat = 10 pts, !champion eye = 140 pts.

*Costs are configurable by the streamer (points config / overlay).*
