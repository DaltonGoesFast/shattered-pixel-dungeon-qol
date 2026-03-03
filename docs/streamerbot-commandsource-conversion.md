# Converting Streamer.bot Actions to Use commandSource

This guide helps you convert actions that have separate Twitch and YouTube versions into a single action that uses `commandSource` to respond on the correct platform.

---

## Step 1: Decode Your Export (Optional)

You can inspect your export structure to see action names and sub-action types:

1. Copy your full Streamer.bot export string (from Export → Export to Clipboard)
2. Paste it into a new file: `Lastest UI/streamerbot_export.txt` (no extra spaces or line breaks in the middle of the string)
3. Run the decoder:

```powershell
cd "C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI"
python convert_to_commandsource.py streamerbot_export.txt
```

4. This creates `streamerbot_export_decoded.json` with the raw action structure (useful for reference).

---

## Step 2: Manual Conversion Pattern

Because Streamer.bot's internal format is complex, **automated conversion isn't reliable**. Use this pattern manually for each spend command (spawn, gold, curse, gas, scroll, buff, debuff, wand):

### Current Structure (Twitch-only or duplicate actions)
```
1. Run a Program (points_command.py ...)
2. Execute C# Code (read spawn_result.txt)
3. if (spawnResult == "ok")
   - True:  Twitch Message (success)
   - False: Twitch Message (error)
```

### Target Structure (commandSource for both platforms)
```
1. Run a Program (points_command.py ...)
2. Execute C# Code (read spawn_result.txt)
3. if (spawnResult == "ok")
   - True:
     - if (commandSource == "youtube") → YouTube Message (success)
     - if (commandSource == "twitch")  → Twitch Message (success)
   - False:
     - if (commandSource == "youtube") → YouTube Message (%spawnResult%)
     - if (commandSource == "twitch")  → Twitch Message (%spawnResult%)
```

### Key Points
- **Trigger:** Use ONE command trigger for both Twitch and YouTube (enable both sources)
- **False Result of platform checks:** Leave EMPTY (0 sub-actions)
- **Variable names:** Use the correct ones per command (e.g. `%scrollName%`, `%buffName%`, `%debuffName%`)

---

## Step 3: Commands to Convert

| Command | Success Message | C# Sets |
|---------|-----------------|---------|
| !spawn | %userName% spawned a %monster%! | spawnResult, (monster from input) |
| !gold | %userName% dropped %goldAmount% gold! | spawnResult, goldAmount |
| !curse | %userName% cursed your %slot%! | spawnResult, item_name |
| !gas | %userName% spewed %gasName%! | spawnResult, gas_name |
| !scroll | %userName% used a random scroll: %scrollName%! | spawnResult, scrollName |
| !buff | %userName% gave you %buffName%! | spawnResult, buffName |
| !debuff | %userName% afflicted you with %debuffName%! | spawnResult, debuffName |
| !wand | %userName% triggered: %effectName%! | spawnResult, wandEffectName |

---

## Step 4: Delete Duplicate Actions

After converting each action to use commandSource:
1. Delete the platform-specific duplicate (e.g. "Scroll TW" and "Scroll YT")
2. Keep the single unified action
3. Ensure the trigger accepts commands from both Twitch and YouTube

---

## Reference: Full Debuff Setup

See the debuff command setup in `streamerbot-points-from-scratch.md` (Action 3e2) for a complete example with commandSource.
