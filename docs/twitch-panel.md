# Chat Commands (Twitch Panel)

Spend points to mess with the run! Earn points by chatting (1 per message, 30s cooldown). Super Chats & bits also give points.

---

**!points** — Check your balance (free)

**!toppoints** / **!leaderboard** — Top 3 point holders (free)

**!spawn** (monster) — Table **base** cost, then vs. that mob’s natural **first** floor: **½** if you’re **deeper**; **1×–3×** if you’re **shallower** (before that floor), by **dungeon chapter** rules—up to **3×** for very early spawns of late-chapter mobs, often **2×** one chapter early or same-Caves-earlier-floor (see `Lastest UI/points_command.py`). Examples: !spawn rat, !spawn bat, !spawn scorpio

**!champion** (monster) — **2×** that adjusted !spawn price (not 2× raw table). Random champion type. Examples: !champion rat, !champion eye

**!gold** (amount) — Drop gold near the hero (**5** pts per gold, 1–100). Example: !gold 25

**!curse** — Curse a random equipped item (**100** pts)

**!gas** — Spawn random gas (**75** pts). Toxic, confusion, storm clouds, inferno, and more!

**!scroll** — Use a random scroll (**100** pts). Like +10 Unstable Spellbook — 50% chance for exotic version!

**!row** — Ring of Wealth bonus loot near the hero (**100** pts). Tier scales with chapter; always at least one item.

**!trap** — Place a random visible trap nearby (**50** pts). Shocking, toxic, burning, teleport, summon, and more!

**!bomb** — Drop a weighted random lit bomb nearby (**75** pts). Regular and alchemy bombs; fuse like a thrown bomb.

**!transmute** — Transmute a random transmutable item from bag or equipped (**150** pts). Same rules as Scroll of Transmutation.

**!bee** — Summon an allied bee for **150** turns (**40** pts). Fights for you like honeyed healing!

**!ward** — Summon a ward (**9** pts). Scales with depth; upgrades existing ward if same tile.

**!buff** — Apply a random buff (**75** pts). Haste, Healing, Barrier, Invisibility, and more!

**!debuff** — Apply a random debuff (**50** pts). Blindness, Slow, Roots, Daze, and more!

**!wand** — Random cursed wand effect (weighted rarities); **75** pts. Burn, freeze, teleport, gas, sheep, and more!

**!heal** — Heal hero ~15% HP (**25** pts)

**!cleanse** — Remove one random debuff (**25** pts)

**!dew** — Drop a dewdrop near the hero (**5** pts)

**!corruptally** — Summon a corrupted ally from the current biome (**40** pts)

**!hex** — Apply Hex debuff (**75** pts)

**!degrade** — Apply Degrade debuff (**75** pts)

**!sabotage** — Remove one random positive buff (**75** pts)

---

**Monster costs (base, before depth/chapter × or ½):**
- 5 pts: rat
- 10 pts: albino, snake, gnoll
- 15 pts: crab, slime, swarm
- 20 pts: thief, skeleton, dm100
- 25 pts: guard, necromancer, spinner
- 30 pts: bat, brute
- 35 pts: shaman
- 40 pts: ghoul, elemental
- 45 pts: warlock
- 50 pts: monk, golem
- 60 pts: succubus
- 70 pts: eye
- 80 pts: scorpio

**Champion:** 2× the spawn price after those depth/chapter rules (same as !spawn at this floor, then double).

*These prices match the streamer’s points overlay config. They can be changed anytime.*
