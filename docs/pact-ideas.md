# Pact ideas

**Pitch list, not the design spec.** Promoted pacts move into [run-artifacts.md](run-artifacts.md) and follow that doc’s rules (hero-select lock, mix-and-match, no score, no stream hooks). Nothing in this file is in the game.

| Badge | Means | Does not mean |
| ----- | ----- | ------------- |
| 🧪 shaping | The mechanic is chosen. Numbers are working numbers. | Design-locked, or playable |
| 💡 pitch | The bargain is worth keeping. Rules are not settled. | Ready to implement |
| 🚫 parked | Looked at and set aside. | — |


---



## Grudge — death rage 🧪 shaping

**Player:** Nothing dies quietly. A killing blow throws them into a rage.

Modeled on the gnoll brute, not on a parting swing. A brute at 0 HP does not die. `Brute.isAlive()` calls `triggerEnrage()` once: shield of `HT/2 + 4`, berserk flash, `spend(TICK)` so the rage turn is lost, then `BruteRage` burns 4 shield per turn (ascension-scaled) while HP stays 0. Hits also chew the shield, because it is a `ShieldBuff`. Empty shield calls `die(null)`. Healing above 0 detaches the rage and the brute lives, and it does not get a second rage. A chasm calls `die(Chasm.class)` directly and sets `hasRaged`, so a pit skips the rage. While raging, the brute’s melee roll swaps from 5–25 to 15–40.

Grudge gives that sequence to other hostiles. Reuse the regular brute numbers (`BruteRage`), not armored brute (`HT/2 + 1`, 1 shield lost per turn, ~60 turns).

- **Who:** hostile mobs. The hero and allies are unchanged.
- **Once:** one rage per mob. A heal that cancels it spends the rage; the next drop to 0 is a real death.
- **Shield:** `HT/2 + 4`. Shown as a shielding number plus the berserk sprite, same as a brute.
- **Lost turn:** `spend(TICK)` inside the rage trigger. The killing blow does not get an immediate counterattack. The next turn they act if you are still there.
- **Decay:** 4 shield per turn while HP is 0, times the ascension stat modifier, same as `BruteRage.act()`.
- **Hits:** player damage, DoTs, and bombs all hit the shield. Bursting it is the way through. Waiting the tick out is the other.
- **Damage:** while the Grudge shield is up, that mob’s `damageRoll()` is doubled. A brute’s own enraged average is 27.5 against a normal 15, a bit under 2×. 2× is the stand-in for mobs that have no second roll table. DoTs they have already applied stay at their old damage.
- **Real death:** loot, XP, Spite’s bomb, and Soul’s wraith happen in `die()`, which runs when the shield hits 0. The rage starting does not pay any of those.
- **Pits:** chasm and the brute’s existing pit skip. No rage, no second body.
- **Grim and other huge hits:** they go through `Char` damage → `isAlive()`, so they start the rage. They do not skip it.

**Already has a second life — skip, so it does not stack:**

- `Brute` and `ArmoredBrute` (this mechanic, with their own roll swap). No second shield, no extra 2× on top of the enraged roll.
- `Ghoul` (crumple and life-link).
- Phase bosses and quest setpieces that already keep themselves alive: Tengu, DM-300, Dwarf King, Yog and fists, Crystal Guardian, Gnoll Geomancer. Goo too, even though its enrage is a low-HP phase rather than an `isAlive` override.
- Pylons, shopkeepers, quest NPCs, Rat King.
- Wraiths. Soul still spawns one wraith on the real death. That wraith does not rage.

Everyone else rages, including mimics, statues, quest hostiles (fetid rat, trickster, great crab), and Swarm clones. A clone raging does not split again.

**How long the shield lasts if you only wait** (4 per turn, before ascension):

| Mob | HT | Shield | Turns of ticking |
| --- | -- | ------ | ---------------- |
| Rat | 8 | 8 | 2 |
| Crab | 15 | 11 | 3 |
| Bat | 30 | 19 | 5 |
| Brute (own rage, not Grudge) | 40 | 24 | 6 |
| Monk / warlock | 70 | 39 | 10 |
| Succubus | 80 | 44 | 11 |
| Eye | 100 | 54 | 14 |
| Scorpio | 110 | 59 | 15 |

Sewers are a short spike. Demon Halls are a long one if you walk away. That is the brute formula applied raw. If Halls rages feel endless, a later pass changes the decay, and the shape stays.

**Combos:**

| Pair | Result |
| ---- | ------ |
| Grudge + Spite / Soul | Bomb and wraith on the real death only |
| Grudge + Swarms | Clones can rage. Rage does not split them |
| Grudge + Honor | Champion buffs stay up during the rage |
| Grudge + Glass | The free turn is the only safe beat. The next hit is doubled into tiny HP |
| Grudge + Frailty | Pits still skip the rage, then Frailty handles the hero |

**Pipe:** `Brute.isAlive`, `Brute.triggerEnrage`, `Brute.BruteRage`. One Grudge buff sibling is enough. Damage doubling sits next to the brute’s `damageRoll` check, gated on that buff.


---



## Pitches 💡

Rules below are the bargain to test, not a spec. Do not implement from this section.

**Polarity.** The dungeon strikes in extremes, and you can see which one is coming.

- One shared pole, shown as a buff. It flips when the hero acts. Until the hero acts again, every hit in the dungeon uses that pole: maximum damage, or minimum damage.
- Average stays the normal roll’s average. The decision is whether to trade on a maximum turn.
- Hidden per-hit 50/50 is parked below. A result you learn after the swing does not give you a turn to play.

**Threshold.** A surprise blow hits for maximum. An aware blow hits for minimum.

- The door is the usual way to earn a surprise, and the check is surprise itself. SPD opens the door as you step in, so a “door tile is still closed” check would miss the swing.
- Applies to enemies too. They surprise you, they hit for max.
- Open: whether wands, bows, and thrown weapons use the same rule, or only melee.

**Hex.** Nothing you wear can be cleansed. The curse is the enchantment.

- Generated equipment is cursed and stays cursed, with a curse enchant or glyph.
- Remove Curse rerolls that curse. It does not uncurse, and it does not unequip.
- Upgrades still apply. Uniques stay out of it (Spirit Bow, Cloak, class armor, and the rest of Metamorphosis’s unique list).
- This is the loop. “Loot spawns cursed, and Remove Curse still cleans it” is parked below.

**Mantle.** What you kill, you become, until the next kill.

- Killing a champion gives the hero that champion buff until they kill a different champion, or leave the floor. One buff. The new one replaces the old.
- With Honor, every champion kill swaps the buff.
- Open: several champion buffs assume a mob (on-attack blaze, projectile reach). Each one needs a pass before this is a spec.

**Feast.** Hunger never leaves. A meal is a weapon.

- The hero does not climb out of hunger by eating. Food instead grants a short combat buff.
- Inverse of the On Diet challenge, which only makes food scarce.
- Open: which buff, how many turns, and whether meat, pasty, and frozen carpaccio are the same button.

**Contagion.** Pain is shared by kin.

- Damage dealt to one enemy also hits other enemies of that same class.
- Floor-wide plus Kin deletes the floor in one hit. A real pass has to pick a limit (same room, or within a few tiles) before this is a spec.


---



## Parked 🚫

**Hidden polarity.** Every attack independently rolls minimum or maximum, 50/50, with no tell. Expected damage equals a normal roll, `(min + max) / 2`, so the pact only removes the middle. It stacks on accuracy as a second coin flip, and early weapons with a minimum of 1 spend half their swings doing nothing. The Polarized curse is a different coin flip (50% chance of 1.5× damage, 50% chance of 0) and has the same problem if it is applied to every hit in the dungeon. See Polarity above for the version with a tell.

**Cursed loot as a tax.** Everything generates cursed, and Scroll of Remove Curse still cleans it. By the prison that is one scroll per gear swap. See Hex above for the version where the curse is the build.
