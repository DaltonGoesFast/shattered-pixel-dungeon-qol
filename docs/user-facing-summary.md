# User-Facing Summary

Copy the block below for your YouTube description, Twitch panels, or channel About section.

- **YouTube (full):** See [youtube-description.md](youtube-description.md) for a complete YouTube description including channel assets, stream commands (!fard, !kesha, etc.), Discord, and chat commands.
- **Twitch panels:** See [twitch-panel.md](twitch-panel.md) for a formatted version with line breaks.

---

```
CHAT COMMANDS — Spend points to mess with the run!

Earn points by chatting (1 per message, 30s cooldown). Super Chats & bits also give points!

COMMANDS (points):
- !points / !toppoints / !leaderboard — Free
- !spawn (monster) — Base cost from table, then depth/chapter rules vs. that mob’s natural first floor: half off (rounded down, min 1) if the hero is deeper than that floor. If shallower (still before that floor), price goes up—up to 3× base for late mobs in early chapters; often 2× when one chapter early or same-Caves-earlier-floor (tiers in Lastest UI/points_command.py).
- !champion (monster) — 2× the adjusted !spawn price here (not 2× the raw table)
- !gold (amount) — 5 pts per gold (1–100). Example: !gold 25 → 125 pts
- !curse — 100
- !gas — 75
- !scroll — 100
- !row — 100 (Ring of Wealth–style loot; scales by chapter; always ≥1 item)
- !trap — 50
- !bomb — 75
- !transmute — 150
- !bee — 40 (allied bee, 150 turns)
- !ward — 9
- !buff — 75
- !debuff — 50
- !wand — 75
- !heal — 25
- !cleanse — 25
- !dew — 5
- !corruptally — 40
- !hex / !degrade / !sabotage — 75 each
- !doublepoints (streamer only) — no point cost; enables 2× earning for N minutes

Monster costs (base, before depth/chapter multiply or half): rat 5 | albino/snake/gnoll 10 | crab/slime/swarm 15 | thief/skeleton/dm100 20 | guard/necromancer/spinner 25 | bat/brute 30 | shaman 35 | ghoul/elemental 40 | warlock 45 | monk/golem 50 | succubus 60 | eye 70 | scorpio 80

Champion: 2× whatever !spawn would charge after those rules (not 2× raw table base). Overlay / points_config.json can change any price anytime.
```
