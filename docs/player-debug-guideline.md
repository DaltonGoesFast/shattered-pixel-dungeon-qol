# Player debug mode

**Status:** Guideline for a planning pass. Not implemented. Not a spec.

This describes opt-in debug play for desktop and Android. A later agent should turn it into a plan, then an implementation. Where this doc is silent, follow existing Shattered Pixel Dungeon patterns. Do not invent extra commands, confirm dialogs, or a separate debug window.

Streamer debug (`Lastest UI/streamer_debug.py`, overlay server, `StreamingCommandHandler`) stays as it is. This mode does not depend on it. Reuse those game actions where that stays simple.

## Player experience

Debug is off by default.

Settings gains a Debug tab, on desktop and Android, from the title screen and from the pause menu. The tab always shows the on/off switch. Turning the switch on or off does nothing until the game is restarted, and the tab says that. The session already running is unchanged, so a player cannot flip the switch and quietly lose a legitimate run.

After a restart with debug on, the actions listed below appear on that same tab. With debug off, those actions stay hidden.

Actions that need a hero do nothing useful on the title screen. Show them when a debug run is loaded.

One tap runs an action. No confirm dialog.

## What counts as a debug run

A run is stamped when it is **started**, from the debug state that is already active after a restart. The stamp is part of the save and never changes.

- Debug on at new game → debug run.
- Debug off at new game → normal run.
- Flipping the switch does not convert an existing save.
- In-run progress still saves. Talents, items, quests, and the rest of play behave normally.

While debug is on, a normal save cannot be continued. While debug is off, a debug save cannot be continued. The save stays in the list, can still be deleted, and tells the player why it is blocked. Starting a new run is always allowed, and that new run picks up the current stamp.

A daily or custom seed started with debug on is a debug run. It can be played. It does not submit a score.

## What a debug run must not record

Nothing from a debug run updates the profile:

- Badges, including the moment one would unlock
- Rankings
- Daily scores
- Lifetime stats
- Challenge and custom-seed records

Blocking only the end-of-run submit is not enough. Unlock checks during the run have to miss as well.

## Actions

All of these live on the Debug tab once debug is on after a restart:

- Heal: full HP, clear debuffs, cleanse curses (same idea as a healing well)
- Identify carried and equipped items
- Give any missing bags
- Reveal the current floor
- Go to the stairs up, or the stairs down
- Give an item from a searchable list, then quantity and upgrade, then one tap
- Set hero level, in the existing 1–30 range
- Go to a floor, in the existing 1–26 range

The item list has to be searchable. Dumping the whole catalog into the settings tab will not fit.

Leave out buffs, debuffs, and a separate search command.

## How the player sees it

During a debug run the hero has a permanent status icon in the buff tray. It is a reminder, not a combat effect, and the player cannot remove it. The description says this run will not count for badges, dailies, or rankings. Use the first glyph from `debugrun.png`. No separate buff sprite.

That same sheet replaces the small floor icon for a debug-stamped run, in the in-game depth display and on the save list, so a debug run is recognizable before it is opened. Keep the level feeling (plain, chasm, water, grass, dark, large, traps, secrets). The red mark on each glyph is the debug cue.

## Asset

`core/src/main/assets/interfaces/debugrun.png`

64×8, one row, eight glyphs. Same pitch as the depth icons in `interfaces/icons.png`: a 6px glyph, then 7px glyphs on an 8px step, in `Level.Feeling` order (plain, chasm, water, grass, dark, large, traps, secrets).

## Out of scope

- Requiring this toggle for streamer debug, or changing the overlay commands
- Buff and debuff commands
- A typed command line
- Confirm prompts
- Applying the switch without a restart
- Letting a normal run and a debug run be continued in the same debug state

## Where to look

Orientation only. The plan chooses the actual edits.

- Settings tabs: `WndSettings`
- Setting storage: `SPDSettings`
- Small floor icons and run-type variants: `Icons` (`DEPTH` and `runTypeOfs`)
- In-game depth display: `MenuPane`
- Save slots: `GamesInProgress`
- End-of-run records: `Rankings.submit`
- Badge unlocks: `Badges`
- Existing debug actions to reuse: `StreamingCommandHandler` (heal, identify, bags, map, stairs, give, level, floor)
