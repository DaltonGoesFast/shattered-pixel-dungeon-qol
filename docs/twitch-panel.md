# Chat Commands (Twitch Panel)

Spend points to mess with the run! Earn points by chatting (1 per message, 30s cooldown). Super Chats & bits also give points.

**2× bonuses (stack — up to 8×):** global !doublepoints + !fard → personal top summoner (!summon leader) → sub/member.

---

## Free commands (no points)

**!fard** — Once per stream per viewer: OBS flash + sound; extends **global 2×** for everyone (+1 min, +5 min for subs/members). Already used? The command silently does nothing (no chat).

**!summon** — Queue a monster march on the **companion overlay** (not an in-game spawn). **60s** cooldown per user. Optional monster: `!summon rat` (random from spawn pool if omitted)

**!topsummoner** — Session summon leader (most successful !summons). Leader earns **personal 2×** on point gains

**!mysummons** — Your summon count this stream

---

## Spend commands (points)

**!points** — Check your balance (free)

**!toppoints** / **!leaderboard** — Top 3 point holders (free)

**!givepoints** (amount) (target) — Give points to another viewer (free). YouTube works without `@`: `!givepoints 50 bob` (Twitch also accepts `@bob`). Spends chat points first, then donor points if needed.

**!spawn** (monster) — Table **base** vs your depth and that mob’s normal **first** floor: **½** off if you’re **deeper** (rounded down, min 1). If you’re **still above** that depth, extra cost follows **chapter gap**: five chapters × five floors (sewers → prison → caves → city → halls)—you pay **base**, **base +20%**, or **base +40%** (rounded). Exact mapping is in `Lastest UI/points_command.py`. Examples: !spawn rat, !spawn bat, !spawn scorpio

**!champion** (monster) — **2×** that adjusted !spawn price (not 2× raw table). Random champion type. Examples: !champion rat, !champion eye

**!gold** (amount) — Drop gold near the hero (**5** pts per gold, 1–100). Example: !gold 25

**!curse** — Curse a random equipped item (**100** base pts; **doubles** for each item already cursed on you — 100 / 200 / 400 / 800 / 1600 / 3200)

**!gas** — Spawn random gas (**75** pts). Toxic, confusion, storm clouds, inferno, and more!

**!scroll** — Use a random scroll (**100** pts). Like +10 Unstable Spellbook — 50% chance for exotic version!

**!row** — Ring of Wealth bonus loot near the hero (**100** pts). Tier scales with chapter; always at least one item.

**!trap** — Place a random visible trap nearby (**50** pts). Shocking, toxic, burning, teleport, summon, and more!

**!plant** — Plant a random seed nearby (**30** pts). Fails if the **Barren Land** challenge is enabled.

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

**Monster costs (table base before depth/chapter adjustment):**
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

**Champion:** 2× the zone-adjusted !spawn price at this depth (same depth rules as !spawn, then double—not 2× raw table base alone).

*These prices match the streamer’s points overlay config. They can be changed anytime.*
