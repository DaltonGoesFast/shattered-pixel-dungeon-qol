# User-Facing Summary

Copy the block below for your YouTube description, Twitch panels, or channel About section.

- **YouTube (full):** See [youtube-description.md](youtube-description.md) for a complete YouTube description including channel assets, stream commands (!fard, !summon, !bestiary, !kesha, etc.), Discord, and chat commands.
- **Twitch panels:** See [twitch-panel.md](twitch-panel.md) for a formatted version with line breaks.
- **Bestiary details:** [bestiary-summon-system.md](bestiary-summon-system.md).

---

```
SHATTER THE STREAMER - Chat plays along with a live Shattered Pixel Dungeon run. Earn points by chatting and spend them on spawns, curses, scrolls, and more that hit the hero in real time. Free commands light up the stream overlay; paid commands change the dungeon.

BESTIARY - Free !summon marches monsters across the companion overlay and fills a shared XP bar. Unlock zones together (Sewers -> Prison -> Caves -> City -> Halls). Race the Sprint for each level (Hall of Fame + 100 donor pts on win). Hold Heat (last 15 min of summon XP) for personal 2x point gains. Check !bestiary, !heat, !topsummoner, !summonhall.

CHAT COMMANDS - Spend points to mess with the run!

Earn up to 500 chat points per stream (2 pts per message, 20s cooldown - active chatters ~83 min to cap). Super Chats & bits go to donor points (up to 4x during bonuses). !bank saves 10% of chat pts into donor pts permanently. Chat resets when the stream ends; donor points never expire or cap. Members auto-bank on reset at 10% (same as !bank).

2x BONUSES on chat earn (stack - up to 8x):
- Global 2x: streamer !doublepoints + community !fard (+3 min, +6 min for subs/members)
- Personal 2x: Bestiary heat leader (!heat - most summon XP in the last 15 minutes)
- Sub/member 2x (Twitch sub or YouTube member)

FREE COMMANDS (no points):
- !fard - Once per stream per viewer: OBS flash + sound; extends global 2x for everyone (+3 min, +6 min subs/members)
- !summon - Free monster march (60s cooldown). Starts with Sewers mobs; unlock more by filling the bar. Optional: !summon rat
- !bestiary / !summonlevel - Co-op bar + unlocked list + your sprint/heat XP
- !topsummoner - Sprint leader this Bestiary level (resets on level-up)
- !heat / !hot - Rolling 15m heat leader (personal 2x)
- !summonhall - Sprint winners for completed zones this stream
- !mysummons - Your summon count + sprint XP + heat XP
- !kesha - OBS meme flash + sound (~2s)
- !mimic / !tooth - Mimic sound if Mimic Tooth trinket is in the run
- !challenge / !challenges - Active run challenges (live game data)
- !seed - Current dungeon seed

COMMANDS (points):
- !points - Chat, donor, and Bestiary sprint/heat XP (free)
- !bank / !bank all / !bank (amount) - Convert chat pts to donor pts at 10% (free)
- !toppoints / !leaderboard - Top 3 by donor points (free)
- !givepoints (amount) (target) - Give points to another viewer. YouTube: no @ needed (example: !givepoints 50 bob). Spends chat points first, then donor points if needed.
- !spawn (monster) - Table base vs depth and where that mob normally first appears: half off (rounded down, min 1) if the hero is deeper. If still above that depth, price bumps by dungeon chapter gap (five 5-floor chapters from sewers to halls) - listed price, +20%, or +40% (rounded). Details in Lastest UI/points_command.py.
- !champion (monster) - 2x the adjusted !spawn price here (not 2x the raw table)
- !gold (amount) - 5 pts per gold (1-100). Example: !gold 25 -> 125 pts
- !curse - 100 base; doubles per known curse worn (100 / 200 / 400 / 800 / 1600 / 3200)
- !gas - 75
- !scroll - 100
- !row - 100 (Ring of Wealth-style loot; scales by chapter; always at least 1 item)
- !trap - 50
- !plant - 30 (plants a random seed near the hero; fails if Barren Land is enabled)
- !bomb - 75
- !transmute - 150
- !bee - 40 (allied bee, 150 turns)
- !ward - 9
- !buff - 75
- !debuff - 50
- !wand - 75
- !heal - 25
- !cleanse - 25
- !dew - 5
- !corruptally - 40
- !hex / !degrade / !sabotage - 75 each
- !doublepoints (streamer only) - no point cost; enables 2x earning for N minutes

Monster costs (table base before depth/chapter adjustment): rat 5 | albino/snake/gnoll 10 | crab/slime/swarm 15 | thief/skeleton/dm100 20 | guard/necromancer/spinner 25 | bat/brute 30 | shaman 35 | ghoul/elemental 40 | warlock 45 | monk/golem 50 | succubus 60 | eye 70 | scorpio 80

Champion: 2x whatever zone-adjusted !spawn would charge (not 2x raw table base alone). Overlay / points_config.json can change any price anytime.
```
