# Points System (From Scratch)

A simple file-based points system for Streamer.bot—no extensions. Viewers earn points by chatting and spend them to spawn monsters. Works with **Twitch and YouTube**.

---

## Prerequisites

Before implementing, ensure:
1. **Overlay server** running (`python server.py` in `Lastest UI`)
2. **Game** running with streaming enabled (WebSocket on port 5001, the default in Settings)
3. **Streamer.bot** connected to Twitch and/or YouTube
4. **Python** installed (for `points_command.py`)

---

## Implementation Order

1. Action 1 (Earn on message) — gets users into the file
2. Action 1c (First Words) — optional, add to your existing action
3. Action 2 (!points) — viewers can check balance
4. Action 2b (!toppoints) — show top 3 point holders
5. Action 3 (!spawn) — spend points to spawn
6. Action 3b (!gold) — spend points to drop gold near hero
7. Action 3c (!curse) — spend points to curse equipped item
8. Action 3d (!gas) — spend points to spawn random gas
9. Action 3e (!scroll) — spend points to use a random scroll (like +10 Unstable Spellbook)
10. Action 3e1 (!buff) — spend points to apply a random buff (Haste, Healing, Barrier, etc.)
11. Action 3e2 (!debuff) — spend points to apply a random debuff (Blindness, Slow, Roots, etc.)
12. Action 3f (!wand) — spend points to trigger a random cursed wand effect (cost varies by rarity)
11. Action 1b (Passive earn) — optional, for viewers already in file
12. Action 4 (!doublepoints) — optional, 2x points for N minutes
13. Action 4a (Spend OFF / Spend ON) — optional, Stream Deck switch to disable/enable spend commands
14. Action 4b (Super Chat / Cheer) — optional, 1 pt per $0.01
15. Action 5 (Reset) — optional, clear points each stream

---

## YouTube Support

- **Commands (!spawn, !gold, !curse, !gas, !scroll, !buff, !debuff, !wand, !points, !toppoints):** When creating the command, enable **both Twitch and YouTube** as sources so one action handles both platforms.
- **Earn Points (message):** Add **Message Received** from YouTube → Triggers to the same action, or create a duplicate action with the YouTube trigger.
- **Earn Points (passive):** Add **Present Viewers** from YouTube → Triggers (YouTube uses chat-activity threshold; no live viewer list).
- **Response messages:** Use the **commandSource pattern** below so a single action sends to the correct chat.

The `userName` variable works for both platforms.

---

## Response Messages: commandSource Pattern (Twitch + YouTube)

Use **one action per command** that works for both Twitch and YouTube. After checking the result (e.g. `%spawnResult%`), nest **platform checks** so the message goes to the correct chat:

**Structure for each spend command:**
```
1. Run a Program (points_command.py ...)
2. Execute C# Code (reads spawn_result.txt, sets %spawnResult% and any extra vars)
3. Conditional: if ("%spawnResult%" Equals "ok")
   - True branch (success):
     - if ("%commandSource%" Equals (Ignore Case) "youtube") → True: YouTube Message (success text)
     - if ("%commandSource%" Equals (Ignore Case) "twitch")  → True: Twitch Message (success text)
     - Leave False Result EMPTY for both platform checks
   - False branch (error):
     - if ("%commandSource%" Equals (Ignore Case) "youtube") → True: YouTube Message (%spawnResult%)
     - if ("%commandSource%" Equals (Ignore Case) "twitch")  → True: Twitch Message (%spawnResult%)
     - Leave False Result EMPTY for both platform checks
```

**Important:** The **False Result** of each `commandSource` conditional must have **no sub-actions**. Only the **True Result** sends a message.

**Quick reference — success messages by command:**

| Command | Success Message |
|---------|-----------------|
| !spawn | `%userName% spawned a %rawInput%!` |
| !gold | `%userName% dropped %goldAmount% gold!` |
| !curse | `%userName% cursed your %curseItemName%!` |
| !gas | `%userName% spewed %gasName%!` |
| !scroll | `%userName% used a random scroll: %scrollName%!` |
| !buff | `%userName% gave you %buffName%!` |
| !debuff | `%userName% afflicted you with %debuffName%!` |
| !wand | `%userName% triggered a cursed wand effect: %wandEffectName%!` |

---

## File Location

**Path quoting:** If your project path contains spaces (e.g. `My Games`), the script path in Run a Program **Arguments** must be in quotes: `"C:\...\points_command.py"`. Otherwise the command will fail.

Points are stored in (update the path in all C# code if your project is elsewhere):
```
C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt
```
Format: one line per user: `username|points|lastEarnTimestamp`
The file is created automatically when the first action runs.

**Double points:** Stored in `double_points_end.txt` (Unix timestamp when 2x ends; `0` = off). Created when you first use `!doublepoints`.

---

## Configuration (edit in the C# code)

- **Points per message:** `1` (change `POINTS_PER_MESSAGE` in Action 1)
- **Points per passive tick:** `1` (change `POINTS_PER_TICK` in Action 1b)
- **Bot exclusion:** `daltongoesslow` never earns points (change `BOT_USER` in Action 1 and 1b)
- **Chat cooldown:** `30` seconds (change `COOLDOWN_SEC` in Action 1; `0` = no cooldown)
- **Passive cooldown:** `60` seconds (change `COOLDOWN_SEC` in Action 1b; shares `lastEarn` with chat)
- **Points costs:** Edit `points_config.json` or open **http://localhost:5000** in your browser (main control page; overlay server must be running)
- **Donation rate:** 1 point per $0.01 (Super Chat uses Frankfurter API for conversion; not in points config)
- **Top farder 2x:** Set `TOP_FARDER_FILE` to the path of the text file (default: `OBS files\textread\leader.txt`). Expected format: `Top Farder: USERNAME - 45`. That user always earns 2x points.

---

## Action 1: Earn Points (on chat message)

**Trigger:** Message Received (Twitch → Triggers → Message Received)

**Sub-Action:** Execute C# Code (Inline)

Uses plain-text format (no JSON) to avoid System.Core dependency. Uses a lock file (`viewer_points.txt.lock`) so Earn Points and Python (superchat, spawn, etc.) don't overwrite each other.

```csharp
using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";
    const string LOCK_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt.lock";
    const string DOUBLE_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\double_points_end.txt";
    const string TOP_FARDER_FILE = @"C:\Users\dalto\Documents\OBS files\textread\leader.txt";  // Format: "Top Farder: USERNAME - 45"
    const int POINTS_PER_MESSAGE = 1;
    const int COOLDOWN_SEC = 30;  // 0 = no cooldown

    const string BOT_USER = "daltongoesslow";  // Bot never earns points (case-insensitive)

    public bool Execute()
    {
        string user = CPH.TryGetArg("userName", out string u) ? u : null;
        if (string.IsNullOrEmpty(user)) return false;
        if (user.Equals(BOT_USER, StringComparison.OrdinalIgnoreCase)) return false;

        try
        {
            if (!AcquirePointsLock()) return false;

            try
            {
                string key = user.ToLowerInvariant();
                long unixNow = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;

                var data = ReadAll();
            int pts = 0;
            long lastEarn = 0;
            if (data.ContainsKey(key))
            {
                pts = data[key].Item1;
                lastEarn = data[key].Item2;
            }

            if (COOLDOWN_SEC > 0 && lastEarn > 0 && unixNow - lastEarn < COOLDOWN_SEC)
                return false;

            int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopFarder(key) ? 2 : 1);
            int toAdd = POINTS_PER_MESSAGE * mult;
            pts += toAdd;
                data[key] = new Tuple<int, long>(pts, unixNow);
                WriteAll(data);
                return true;
            }
            finally { ReleasePointsLock(); }
        }
        catch (Exception ex) { CPH.LogInfo("Earn points: " + ex.Message); return false; }
    }

    bool AcquirePointsLock()
    {
        for (int i = 0; i < 200; i++)  // 10 sec at 50ms
        {
            try
            {
                using (var fs = new FileStream(LOCK_FILE, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                    fs.WriteByte(0);
                return true;
            }
            catch (IOException) { Thread.Sleep(50); }
        }
        return false;
    }

    void ReleasePointsLock()
    {
        try { if (File.Exists(LOCK_FILE)) File.Delete(LOCK_FILE); } catch { }
    }

    Dictionary<string, Tuple<int, long>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                        result[k] = new Tuple<int, long>(p, l);
                }
            }
        }
        catch { }
        return result;
    }

    void WriteAll(Dictionary<string, Tuple<int, long>> data)
    {
        var lines = new List<string>();
        foreach (var kv in data)
            lines.Add(kv.Key + "|" + kv.Value.Item1 + "|" + kv.Value.Item2);
        File.WriteAllLines(FILE, lines.ToArray());
    }

    bool IsDoublePointsActive(long unixNow)
    {
        try
        {
            if (!File.Exists(DOUBLE_FILE)) return false;
            string s = File.ReadAllText(DOUBLE_FILE).Trim();
            if (string.IsNullOrEmpty(s) || !long.TryParse(s, out long endTime) || endTime <= 0) return false;
            return unixNow < endTime;
        }
        catch { return false; }
    }

    bool IsTopFarder(string userKey)
    {
        try
        {
            if (!File.Exists(TOP_FARDER_FILE)) return false;
            string content = File.ReadAllText(TOP_FARDER_FILE).Trim();
            const string prefix = "Top Farder: ";
            const string suffix = " - ";
            int p = content.IndexOf(prefix, StringComparison.OrdinalIgnoreCase);
            if (p < 0) return false;
            int start = p + prefix.Length;
            int s = content.IndexOf(suffix, start, StringComparison.Ordinal);
            if (s < 0) return false;
            string username = content.Substring(start, s - start).Trim();
            return !string.IsNullOrEmpty(username) && userKey.Equals(username, StringComparison.OrdinalIgnoreCase);
        }
        catch { return false; }
    }
}
```

---

## Action 1b: Earn Points (passive – present viewers)

**Trigger:** Present Viewers (Twitch → Triggers → Present Viewers)

Adds points over time to viewers who are already in the file (have chatted at least once). Enable **Live Update** under Platform → Twitch → Settings and set the interval (e.g. 5 minutes).

**Note:** The C# tries both `userName` and `presentUserName`. If Present Viewers uses a list, add **Add Present User** (index 0, 1, 2, …) in a loop before the C# so each viewer gets processed.

**Sub-Action:** Execute C# Code (Inline)

```csharp
using System;
using System.Collections.Generic;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";
    const string DOUBLE_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\double_points_end.txt";
    const string TOP_FARDER_FILE = @"C:\Users\dalto\Documents\OBS files\textread\leader.txt";  // Format: "Top Farder: USERNAME - 45"
    const int POINTS_PER_TICK = 1;
    const int COOLDOWN_SEC = 60;  // 0 = no cooldown (shares lastEarn with message earn)
    const string BOT_USER = "daltongoesslow";  // Bot never earns points (case-insensitive)

    public bool Execute()
    {
        string user = CPH.TryGetArg("userName", out string u) ? u : null;
        if (string.IsNullOrEmpty(user)) CPH.TryGetArg("presentUserName", out user);
        if (string.IsNullOrEmpty(user)) return false;
        if (user.Equals(BOT_USER, StringComparison.OrdinalIgnoreCase)) return false;

        try
        {
            var data = ReadAll();
            string key = user.ToLowerInvariant();
            if (!data.ContainsKey(key)) return false;  // Only add to users already in file

            int pts = data[key].Item1;
            long lastEarn = data[key].Item2;
            long unixNow = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;

            if (COOLDOWN_SEC > 0 && lastEarn > 0 && unixNow - lastEarn < COOLDOWN_SEC)
                return false;

            int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopFarder(key) ? 2 : 1);
            int toAdd = POINTS_PER_TICK * mult;
            pts += toAdd;
            data[key] = new Tuple<int, long>(pts, unixNow);
            WriteAll(data);
            return true;
        }
        catch (Exception ex) { CPH.LogInfo("Passive earn: " + ex.Message); return false; }
    }

    Dictionary<string, Tuple<int, long>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                        result[k] = new Tuple<int, long>(p, l);
                }
            }
        }
        catch { }
        return result;
    }

    void WriteAll(Dictionary<string, Tuple<int, long>> data)
    {
        var lines = new List<string>();
        foreach (var kv in data)
            lines.Add(kv.Key + "|" + kv.Value.Item1 + "|" + kv.Value.Item2);
        File.WriteAllLines(FILE, lines.ToArray());
    }

    bool IsDoublePointsActive(long unixNow)
    {
        try
        {
            if (!File.Exists(DOUBLE_FILE)) return false;
            string s = File.ReadAllText(DOUBLE_FILE).Trim();
            if (string.IsNullOrEmpty(s) || !long.TryParse(s, out long endTime) || endTime <= 0) return false;
            return unixNow < endTime;
        }
        catch { return false; }
    }

    bool IsTopFarder(string userKey)
    {
        try
        {
            if (!File.Exists(TOP_FARDER_FILE)) return false;
            string content = File.ReadAllText(TOP_FARDER_FILE).Trim();
            const string prefix = "Top Farder: ";
            const string suffix = " - ";
            int p = content.IndexOf(prefix, StringComparison.OrdinalIgnoreCase);
            if (p < 0) return false;
            int start = p + prefix.Length;
            int s = content.IndexOf(suffix, start, StringComparison.Ordinal);
            if (s < 0) return false;
            string username = content.Substring(start, s - start).Trim();
            return !string.IsNullOrEmpty(username) && userKey.Equals(username, StringComparison.OrdinalIgnoreCase);
        }
        catch { return false; }
    }
}
```

---

## Action 1c: First Words Bonus (add to your existing First Words action)

Add this as a **sub-action** to your existing First Words action (before or after the shout out). Gives 5 points the first time a user chats.

**Sub-Action:** Execute C# Code (Inline)

```csharp
using System;
using System.Collections.Generic;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";
    const string DOUBLE_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\double_points_end.txt";
    const string TOP_FARDER_FILE = @"C:\Users\dalto\Documents\OBS files\textread\leader.txt";  // Format: "Top Farder: USERNAME - 45"
    const int FIRST_WORDS_BONUS = 5;

    public bool Execute()
    {
        string user = CPH.TryGetArg("userName", out string u) ? u : null;
        if (string.IsNullOrEmpty(user)) return false;

        try
        {
            long unixNow = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;
            var data = ReadAll();
            string key = user.ToLowerInvariant();
            int pts = data.ContainsKey(key) ? data[key].Item1 : 0;
            int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopFarder(key) ? 2 : 1);
            int toAdd = FIRST_WORDS_BONUS * mult;
            pts += toAdd;
            data[key] = new Tuple<int, long>(pts, unixNow);
            WriteAll(data);
            return true;
        }
        catch (Exception ex) { CPH.LogInfo("First words bonus: " + ex.Message); return false; }
    }

    Dictionary<string, Tuple<int, long>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                        result[k] = new Tuple<int, long>(p, l);
                }
            }
        }
        catch { }
        return result;
    }

    void WriteAll(Dictionary<string, Tuple<int, long>> data)
    {
        var lines = new List<string>();
        foreach (var kv in data)
            lines.Add(kv.Key + "|" + kv.Value.Item1 + "|" + kv.Value.Item2);
        File.WriteAllLines(FILE, lines.ToArray());
    }

    bool IsDoublePointsActive(long unixNow)
    {
        try
        {
            if (!File.Exists(DOUBLE_FILE)) return false;
            string s = File.ReadAllText(DOUBLE_FILE).Trim();
            if (string.IsNullOrEmpty(s) || !long.TryParse(s, out long endTime) || endTime <= 0) return false;
            return unixNow < endTime;
        }
        catch { return false; }
    }

    bool IsTopFarder(string userKey)
    {
        try
        {
            if (!File.Exists(TOP_FARDER_FILE)) return false;
            string content = File.ReadAllText(TOP_FARDER_FILE).Trim();
            const string prefix = "Top Farder: ";
            const string suffix = " - ";
            int p = content.IndexOf(prefix, StringComparison.OrdinalIgnoreCase);
            if (p < 0) return false;
            int start = p + prefix.Length;
            int s = content.IndexOf(suffix, start, StringComparison.Ordinal);
            if (s < 0) return false;
            string username = content.Substring(start, s - start).Trim();
            return !string.IsNullOrEmpty(username) && userKey.Equals(username, StringComparison.OrdinalIgnoreCase);
        }
        catch { return false; }
    }
}
```

Edit `FIRST_WORDS_BONUS` to change the amount (default 5).

---

## Action 2: Check Points (!points)

**Trigger:** Command Triggered → create command `!points` (enable **both Twitch and YouTube** as sources)

**Sub-Actions (in order):**

1. **Execute C# Code** (Inline):

```csharp
using System;
using System.Collections.Generic;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";

    public bool Execute()
    {
        string user = CPH.TryGetArg("userName", out string u) ? u : null;
        if (string.IsNullOrEmpty(user)) { CPH.SetArgument("userPoints", "0"); return true; }

        try
        {
            var data = ReadAll();
            int pts = data.ContainsKey(user.ToLowerInvariant()) ? data[user.ToLowerInvariant()].Item1 : 0;
            CPH.SetArgument("userPoints", pts.ToString());
            return true;
        }
        catch { CPH.SetArgument("userPoints", "0"); return true; }
    }

    Dictionary<string, Tuple<int, long>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                        result[k] = new Tuple<int, long>(p, l);
                }
            }
        }
        catch { }
        return result;
    }
}
```

2. **Response message:** Use commandSource pattern:
   - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName%, you have %userPoints% points. Spawn costs vary by monster (5–80).`
   - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: same text
   - Leave **False Result** empty for both.

---

## Action 2b: Top Points (!toppoints)

**Trigger:** Command Triggered → create command `!toppoints` (or `!leaderboard`) (enable **both Twitch and YouTube** as sources)

**Sub-Actions (in order):**

1. **Execute C# Code** (Inline):

```csharp
using System;
using System.Collections.Generic;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";

    public bool Execute()
    {
        try
        {
            var list = new List<Tuple<string, int>>();
            if (File.Exists(FILE))
            {
                foreach (string line in File.ReadAllLines(FILE))
                {
                    string[] parts = line.Split('|');
                    if (parts.Length >= 2)
                    {
                        string name = parts[0].Trim();
                        int p;
                        if (int.TryParse(parts[1].Trim(), out p) && !string.IsNullOrEmpty(name))
                            list.Add(new Tuple<string, int>(name, p));
                    }
                }
            }
            list.Sort((a, b) => b.Item2.CompareTo(a.Item2));
            string msg;
            if (list.Count == 0)
                msg = "Nobody has points yet. Chat to earn!";
            else
            {
                var parts = new List<string>();
                int take = Math.Min(3, list.Count);
                for (int i = 0; i < take; i++)
                    parts.Add((i + 1) + ". " + list[i].Item1 + " (" + list[i].Item2 + " pts)");
                msg = string.Join(" | ", parts);
            }
            CPH.SetArgument("topPointsResult", msg);
            return true;
        }
        catch (Exception ex) { CPH.SetArgument("topPointsResult", "Error: " + ex.Message); return true; }
    }
}
```

2. **Response message:** Use commandSource pattern:
   - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%topPointsResult%`
   - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%topPointsResult%`
   - Leave **False Result** empty for both.

**Example output:** `1. ViewerA (450 pts) | 2. ViewerB (320 pts) | 3. ViewerC (180 pts)`

---

## Action 3: Spawn Monster (with points)

**Trigger:** Command Triggered → `!spawn` (enable **both Twitch and YouTube** as sources)

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python` (or full path to `python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" spawn %rawInput% %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds
   - **Note:** Use `%rawInput%` for the monster name (text after `!spawn`). If that's empty, try `%input1%` depending on your Streamer.bot version.

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "Spawn failed (no result file - is overlay server running?)";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% spawned a %rawInput%!`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% spawned a %rawInput%!`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

The `points_command.py` script checks points, attempts the spawn, and **only deducts points if the spawn succeeds**. If there's no free space (hero surrounded), points are not wasted.

**Edit costs:** Open http://localhost:5000/points-config or edit `points_config.json`. Example: `"rat": 25, "eye": 200`. Any monster not listed uses `DEFAULT_COST` (100).

**Half price out-of-biome:** Monsters cost **half** when spawned beyond their native area (e.g. sewer mobs in prison+). The script fetches current depth from the overlay server; if the server is unavailable, full price is charged.

**Troubleshooting (!spawn does nothing):**
- **Monster name not passed:** Change `%input1%` to `%rawInput%` in the Arguments (Streamer.bot versions differ).
- **No points:** Chat to earn points.
- **Overlay server not running:** Start `python server.py` in `Lastest UI`.
- **Game not connected:** Game must be running with streaming enabled (port 5001). Server console shows "Game WebSocket: waiting for game..." when disconnected.
- **Test manually:** Run `python "Lastest UI\points_command.py" spawn rat YourUsername` in a terminal—should write `ok` to `spawn_result.txt` or an error.

---

## Action 3b: Drop Gold (with points)

**Trigger:** Command Triggered → `!gold` (enable **both Twitch and YouTube** as sources)

**Usage:** `!gold <amount>` (e.g. `!gold 10`). Amount 1–100 required. Invalid amounts (0, negative, >100) are rejected.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python` (or full path to `python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" gold %rawInput% %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds
   - **Note:** `%rawInput%` = amount (required). `%userName%` = who ran the command.

2. **Execute C# Code** (Inline) — reads `spawn_result.txt`, sets `%spawnResult%` and `%goldAmount%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "Gold failed (no result file - is overlay server running?)";
        string goldAmount = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                if (string.IsNullOrEmpty(result)) result = "Gold failed (empty response)";
                int pipe = result.IndexOf('|');
                if (pipe >= 0)
                {
                    goldAmount = result.Substring(pipe + 1).Trim();
                    result = result.Substring(0, pipe).Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message ?? "Gold failed"; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("goldAmount", goldAmount);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% dropped %goldAmount% gold!`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% dropped %goldAmount% gold!`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 5 points per gold by default (edit via points config). Amount 1–100; invalid amounts are rejected with a clear error.

**Troubleshooting (504 timeout / "unknown error"):**
- **Use the gold C# above** — it reads `spawn_result.txt`, sets `%spawnResult%` and `%goldAmount%`, and uses fallbacks so you never get empty/unknown errors.
- **Set Working Directory** — the Run a Program step must have Working Directory = `Lastest UI` so the script finds its files.
- **504 timeout:** The game didn't respond in time. Ensure the game is running, in an active run (not title screen), and streaming is enabled. Does `!spawn` work? If spawn works, gold should too.

---

## Action 3c: Curse Item (with points)

**Trigger:** Command Triggered → `!curse` (enable **both Twitch and YouTube** as sources)

**Usage:** `!curse` — curses a **random** equipped item (weapon, armor, ring, artifact, or misc). No slot needed.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" curse %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds
   - **Note:** No slot argument — the script picks a random equipped slot (weapon, armor, ring, artifact, misc).

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%` and `%curseItemName%` (item name on success):

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string itemName = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                int pipe = result.IndexOf('|');
                if (pipe >= 0)
                {
                    itemName = result.Substring(pipe + 1).Trim();
                    result = result.Substring(0, pipe).Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("curseItemName", itemName);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% cursed your %curseItemName%!`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% cursed your %curseItemName%!`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 200 points per curse (edit via points config).

**Add to the same blocking queue** as spawn, gold, and earn actions.

**Fails when:** No curseable item in any slot — the script tries each slot (random order) and retries if a slot is empty or already cursed. Only fails if all 5 slots are empty or already cursed.

---

## Action 3d: Spawn Random Gas (with points)

**Trigger:** Command Triggered → `!gas` (enable **both Twitch and YouTube** as sources)

**Usage:** `!gas` — spawns random gas (Chaotic Censer at level +3). Toxic, confusion, regrowth, storm clouds, smoke, stench, inferno, blizzard, or corrosive gas.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" gas %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%` and `%gasName%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string itemName = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                int pipe = result.IndexOf('|');
                if (pipe >= 0)
                {
                    itemName = result.Substring(pipe + 1).Trim();
                    result = result.Substring(0, pipe).Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("gasName", itemName);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% spewed %gasName%!`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% spewed %gasName%!`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 75 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, and earn actions.

**Fails when:** No valid cell 2–6 tiles from hero in field of view.

---

## Action 3e: Random Scroll (with points)

**Trigger:** Command Triggered → `!scroll` (enable **both Twitch and YouTube** as sources)

**Usage:** `!scroll` — uses a random scroll like activating a +10 Unstable Spellbook. Picks from the full scroll pool (excluding transmutation), 50% chance for exotic version. Identify, Remove Curse, and Magic Mapping are half as likely.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" scroll %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds *(required — otherwise C# runs before the script writes the result file)*

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%` and `%scrollName%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string itemName = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                int pipe = result.IndexOf('|');
                if (pipe >= 0)
                {
                    itemName = result.Substring(pipe + 1).Trim();
                    result = result.Substring(0, pipe).Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("scrollName", itemName);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% used a random scroll: %scrollName%!`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% used a random scroll: %scrollName%!`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 100 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, and earn actions.

**Fails when:** Not in an active run, hero dead, magic immune, or blinded.

---

## Action 3e1: Random Buff (with points)

**Trigger:** Command Triggered → `!buff` (enable **both Twitch and YouTube** as sources)

**Usage:** `!buff` — applies a random buff to the hero. Picks from: Haste, Adrenaline, Invisibility, Levitation, Barrier (10% HP shield), Healing (10% HP over 10 turns), Recharging, MindVision. Excludes Paralysis, Burning, Poison, Awareness.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" buff %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%` and `%buffName%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string itemName = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                int pipe = result.IndexOf('|');
                if (pipe >= 0)
                {
                    itemName = result.Substring(pipe + 1).Trim();
                    result = result.Substring(0, pipe).Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("buffName", itemName);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% gave you %buffName%!`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% gave you %buffName%!`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 75 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, and earn actions.

---

## Action 3e2: Random Debuff (with points)

**Trigger:** Command Triggered → `!debuff` (enable **both Twitch and YouTube** as sources)

**Usage:** `!debuff` — applies a random debuff to the hero. Picks from: Blindness, Weakness, Slow, Cripple, Roots, Daze, Vulnerable. Excludes Paralysis, Burning, Poison. Roots is skipped if the hero is flying.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" debuff %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%` and `%debuffName%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string itemName = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                int pipe = result.IndexOf('|');
                if (pipe >= 0)
                {
                    itemName = result.Substring(pipe + 1).Trim();
                    result = result.Substring(0, pipe).Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("debuffName", itemName);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% afflicted you with %debuffName%!`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% afflicted you with %debuffName%!`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 50 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, buff, and earn actions.

---

**Troubleshooting ("No result file - is overlay server running?"):**
- **Quotations:** The script path in Arguments **must be in quotes** (e.g. `"C:\...\points_command.py"`) — paths with spaces (like `My Games`) break without them.
- **Add Wait maximum:** The Run a Program step must have **Wait maximum: 10 seconds**. Without it, Streamer.bot runs the C# step before Python finishes writing `spawn_result.txt`.
- **Overlay running:** Ensure `python server.py` is running in `Lastest UI`.
- **Game connected:** The game must be running with streaming enabled (port 5001). Overlay console shows "Game WebSocket: waiting for game..." when disconnected.
- **Test manually:** From project root: `python "Lastest UI\points_command.py" scroll YourUsername`. From `Lastest UI` folder: `python points_command.py scroll YourUsername`. Should write `ok|ScrollName` or an error to `spawn_result.txt`.

---

## Action 3f: Cursed Wand Effect (with points)

**Trigger:** Command Triggered → `!wand` (enable **both Twitch and YouTube** as sources)

**Usage:** `!wand <tier>` — tier is required. Triggers a cursed wand effect from that rarity.
- `!wand common` — common only (50 pts)
- `!wand uncommon` — uncommon only (100 pts)
- `!wand rare` — rare only (200 pts)
- `!wand veryrare` — very rare only (400 pts)

If they type just `!wand`, they get: "Specify a tier: !wand common, !wand uncommon, !wand rare, or !wand veryrare"

Excludes: AbortRetryFail, Explosion, FireBall, ForestFire.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python` (or full path to `python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" wand %rawInput% %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds *(required — otherwise C# runs before the script writes the result file)*

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%` and `%wandEffectName%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string itemName = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                int pipe = result.IndexOf('|');
                if (pipe >= 0)
                {
                    itemName = result.Substring(pipe + 1).Trim();
                    result = result.Substring(0, pipe).Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("wandEffectName", itemName);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% triggered a cursed wand effect: %wandEffectName%!`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% triggered a cursed wand effect: %wandEffectName%!`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 50 (common) / 100 (uncommon) / 200 (rare) / 400 (very rare). Edit via points config.

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, and earn actions.

**Fails when:** Not in an active run, hero dead, or no valid target cell (need visible tiles 2–6 from hero).

**Troubleshooting:** Same as scroll — quotations on script path, Wait maximum 10 seconds, overlay running, game connected.

**Test manually:** `python points_command.py wand common YourUsername` (tier required).

---

## Action 3 (alternative): File Bridge (when %output1% is empty)

Use this if **Run a Program** does not capture `%output1%`. The Python script writes its result to a file; C# reads it and sets `%spawnResult%`. No HTTP in C# (avoids assembly errors).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `C:\Users\dalto\AppData\Local\Programs\Python\Python310\python.exe` (or your `python.exe`)
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" spawn %rawInput% %userName%`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** (Inline) — reads the result file and sets `spawnResult`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "Spawn failed (no result file - is overlay server running?)";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")` — use the commandSource pattern (see above):
   - **True:** `if (commandSource == youtube)` → YouTube Message; `if (commandSource == twitch)` → Twitch Message: `%userName% spawned a %rawInput%!`
   - **False:** `if (commandSource == youtube)` → YouTube Message; `if (commandSource == twitch)` → Twitch Message: `%spawnResult%`

**Note:** The `points_command.py` script writes to `spawn_result.txt` (or `donation_result.txt` for Super Chat/Cheer).

---

## Action 4: Double Points (streamer command, timed)

**Trigger:** Command Triggered → `!doublepoints` (or `!2x`)

**Permission:** Restrict to **Streamer** (or Mods) so only you can use it.

**Usage:** `!doublepoints <duration>` — duration in minutes. Examples: `!doublepoints 5` (5 min), `!doublepoints 15` (15 min)

**Sub-Action:** Execute C# Code (Inline)

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\double_points_end.txt";

    public bool Execute()
    {
        string input = CPH.TryGetArg("rawInput", out string r) ? r : null;
        if (string.IsNullOrEmpty(input)) input = CPH.TryGetArg("input1", out string i) ? i : null;
        if (string.IsNullOrEmpty(input) || !int.TryParse(input.Trim(), out int minutes) || minutes < 1 || minutes > 120)
        {
            CPH.SetArgument("doublePointsResult", "Usage: !doublepoints <minutes> (e.g. !doublepoints 5 for 5 minutes, max 120)");
            return false;
        }

        try
        {
            long unixNow = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;
            long endTime = unixNow + (minutes * 60);
            File.WriteAllText(FILE, endTime.ToString());
            CPH.SetArgument("doublePointsResult", $"Double points active for {minutes} minutes! Chat to earn 2x points.");
            CPH.SetArgument("doublePointsMinutes", minutes.ToString());
            return true;
        }
        catch (Exception ex)
        {
            CPH.SetArgument("doublePointsResult", "Error: " + ex.Message);
            return false;
        }
    }
}
```

2. **Response message** (required for chat to show anything): Add a sub-action *after* the C# code:
   - **Twitch Message** (if using Twitch): Message = `%doublePointsResult%`
   - **YouTube Message** (if using YouTube): Message = `%doublePointsResult%`
   - Or use a conditional on `%commandSource%` to branch: if `twitch` → Twitch Message; if `youtube` → YouTube Message.

**Troubleshooting (no response in chat):** The C# only sets the variable—you must add a **Twitch Message** or **YouTube Message** sub-action with `%doublePointsResult%` as the message. Without it, nothing is sent to chat.

**Note for !doublepoints:** Ensure the command passes the duration. In Streamer.bot command settings, enable "Arguments" so `!doublepoints 5` passes `5` as `input1` or `rawInput`. If `input1` is empty, the code falls back to `rawInput`.

**OBS countdown timer:** Add a **Browser source** → URL = `http://127.0.0.1:5000/double-points-countdown`. Shows gold "2x points: M:SS" when active, hides when inactive. Requires the overlay server running on port 5000.

**If it doesn't update after restarting OBS:** The page now uses no-cache headers and auto-reloads after 5 failed fetches (e.g. if the server wasn't ready). Also enable **"Refresh browser when scene becomes active"** in the Browser Source properties — it refreshes when you switch to that scene.

---

## Action 4a: Spend OFF / Spend ON (Stream Deck switch)

**Purpose:** Two separate actions for Stream Deck "action switches" — when the switch is ON, trigger Spend ON (enable spending); when OFF, trigger Spend OFF (disable spending). Users can still earn points; they just can't spend them when disabled.

### Spend OFF action

**Trigger:** Hotkey (Stream Deck switch → OFF state)

**Sub-Action:** Execute C# Code (Inline) — creates the disable flag file. Keep spend actions enabled so they still run and return the "Spending is currently disabled" message to chat.

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spend_disabled.txt";

    public bool Execute()
    {
        File.WriteAllText(FILE, "1");
        return true;
    }
}
```

### Spend ON action

**Trigger:** Hotkey (Stream Deck switch → ON state)

**Sub-Action:** Execute C# Code (Inline) — removes the disable flag.

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spend_disabled.txt";

    public bool Execute()
    {
        if (File.Exists(FILE))
            File.Delete(FILE);
        return true;
    }
}
```

**Stream Deck setup:** Create an "Action Switch" or similar. Assign Spend ON to the ON state and Spend OFF to the OFF state. When the switch is ON, spending is enabled; when OFF, spending is disabled.

---

## Action 4b: Earn Points (Super Chat / Cheer)

**Rate:** 1 point per $0.01 USD. Super Chats use the [Frankfurter API](https://www.frankfurter.app/) for currency conversion (no API key). Twitch bits: 100 bits = $1 = 100 points.

**Add both actions to your points queue** (same blocking queue as earn/spend) to avoid race conditions.

### YouTube Super Chat (Setup from Scratch)

1. **Create a new action** (e.g. "Super Chat Points").
2. **Add trigger:** YouTube → Triggers → **Super Chat**.
3. **Add sub-action:** Run Program
   - **Program:** `python` (or full path to `python.exe`, e.g. `C:\Python313\python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" superchat %microAmount% %currencyCode% %userName%`
   - **Working directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
4. **Add this action to your points queue** (same queue as earn/spend).

**Streamer.bot Super Chat variables:** `%microAmount%` (e.g. 1000000 = $1), `%currencyCode%` (e.g. USD), `%userName%` (login) or `%user%` (display name). If `%userName%` is empty for real Super Chats, try `%user%` instead in the Arguments.

**Optional:** Add a C# step to read `donation_result.txt` (format: `ok|150` = 150 points earned). Split by `|`, use second part for a thank-you message: `Thanks for the super chat! You earned %points% points!`

#### Troubleshooting: Test Trigger works, real Super Chats don't

- **YouTube platform:** Ensure YouTube is connected in Streamer.bot (Settings → Platforms → YouTube). Reconnect if needed.
- **Variable names:** If `%userName%` is empty for real events, change the last argument to `%user%` (display name).
- **Debug logging:** Create an empty file `superchat_debug.txt` in `Lastest UI`. The next Super Chat will append a log line to `superchat_debug.log` with the exact args received. Remove the file when done debugging.

### Twitch Cheer

**Trigger:** Twitch → Triggers → Cheer

**Sub-Action:** Run Program
- **Program:** `python`
- **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" cheer %bits% %userName%`
- **Working directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`

**Note:** Anonymous cheers are skipped (no username to credit).

---

## Action 5: Reset Points (every stream)

**Trigger:** Stream Started (Twitch → Triggers → Stream Started). For YouTube, use the equivalent "Stream Online" or "Stream Started" trigger.

**Sub-Action:** Execute C# Code (Inline)

Clears all points when you go live so everyone starts fresh each stream.

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";

    public bool Execute()
    {
        try
        {
            if (File.Exists(FILE))
                File.Delete(FILE);
            return true;
        }
        catch (Exception ex) { CPH.LogInfo("Reset points: " + ex.Message); return false; }
    }
}
```

---

## Commands Quick Reference

| Command | Usage | Cost | Description |
|---------|-------|------|-------------|
| **!points** | `!points` | Free | Check your point balance. |
| **!toppoints** | `!toppoints` | Free | Show top 3 point holders. |
| **!spawn** | `!spawn <monster>` | Varies by monster (5–80 pts) | Spawn a monster near the hero. Half price when spawned beyond its native biome. Valid monsters: rat, albino, snake, gnoll, crab, slime, swarm, thief, skeleton, bat, brute, shaman, spinner, dm100, guard, necromancer, ghoul, elemental, warlock, monk, golem, succubus, eye, scorpio. |
| **!gold** | `!gold <amount>` | 2 pts per gold | Drop gold near the hero. Amount 1–100 required (e.g. `!gold 10` = 20 pts). |
| **!curse** | `!curse` | 200 pts | Curse a **random** equipped item (weapon, armor, ring, artifact, or misc). |
| **!gas** | `!gas` | 75 pts | Spawn random gas (Chaotic Censer +3). Toxic, confusion, regrowth, storm clouds, smoke, stench, inferno, blizzard, or corrosive gas. |
| **!scroll** | `!scroll` | 100 pts | Use a random scroll (like +10 Unstable Spellbook). 50% chance for exotic version. |
| **!buff** | `!buff` | 75 pts | Apply a random buff (Haste, Healing, Barrier, Invisibility, etc.). Healing = 10% HP over 10 turns; Barrier = 10% HP shield. |
| **!debuff** | `!debuff` | 50 pts | Apply a random debuff (Blindness, Slow, Roots, Daze, etc.). Excludes Paralysis, Burning, Poison. |
| **!wand** | `!wand common` (tier required) | 50–400 pts | Trigger a cursed wand effect. Tier required: common, uncommon, rare, or veryrare. |
| **!doublepoints** | `!doublepoints <minutes>` | — | **Streamer only.** 2× points for N minutes (max 120). `!doublepoints 5` for 5 min. |

**Spawn costs (base):** rat 5, albino/snake/gnoll 10, crab/slime/swarm 15, thief/skeleton/dm100 20, guard/necromancer/spinner 25, bat/brute 30, shaman 35, ghoul/elemental 40, warlock 45, monk/golem 50, succubus 60, eye 70, scorpio 80. Unknown monsters default to 100. Edit `points_config.json` or use the config UI to change.

---

## Summary

| Action       | Trigger           | Purpose                                      |
|-------------|-------------------|----------------------------------------------|
| Earn Points | Message Received  | +1 per message (30s cooldown; 2x double points; 2x if top farder) |
| Earn Points (passive) | Present Viewers | +1 per tick (60s cooldown; 2x double points; 2x if top farder) |
| First Words Bonus     | (add to your First Words action) | +5 on first chat (2x double points; 2x if top farder) |
| Check Points| !points           | Show viewer their balance                    |
| Top Points  | !toppoints        | Show top 3 point holders in chat              |
| Spawn Monster| !spawn           | Deduct points (cost varies by monster)       |
| Drop Gold    | !gold <amount>  | Spend points to drop gold (2 pts/gold, amount required) |
| Curse Item   | !curse         | Spend points to curse a random equipped item (200 pts) |
| Spawn Gas    | !gas           | Spend points to spawn random gas (Chaotic Censer +3, 75 pts) |
| Random Scroll | !scroll        | Spend points to use a random scroll (like +10 Unstable Spellbook, 100 pts) |
| Random Buff   | !buff          | Spend points to apply a random buff (75 pts, e.g. Haste, Healing, Barrier) |
| Random Debuff | !debuff        | Spend points to apply a random debuff (50 pts, e.g. Blindness, Slow, Roots) |
| Cursed Wand   | !wand          | Spend points to trigger a random cursed wand effect (50–400 pts by rarity) |
| Super Chat Points | YouTube Super Chat | 1 pt per $0.01 (currency converted via Frankfurter API) |
| Cheer Points | Twitch Cheer | 1 pt per bit (100 bits = $1 = 100 pts) |
| Double Points | !doublepoints (streamer only) | 2x points for N minutes: `!doublepoints 5` |
| Reset Points| Stream Started    | Clear all points when you go live            |

---

## Notes

- **Reset Points** runs when your stream goes live, so points reset at the start of each stream.
- **Passive earn** only adds to users already in the file—they must send at least one message first. Enable **Live Update** under Platform → Twitch → Settings for Present Viewers to work.
- **Message Received** fires on every chat message. Ensure the Earn action does not also fire on bot messages or your own messages if you want to exclude them (add a conditional if needed).
- For `!spawn`, the Command Triggered must pass `input1` (the text after the command). Use `%rawInput%` or `%input1%` depending on your Streamer.bot version.
- Edit `POINTS_PER_MESSAGE`, `POINTS_PER_TICK`, and `COOLDOWN_SEC` (in Action 1 for chat, Action 1b for passive) in the C# code. Edit points costs via http://localhost:5000/points-config or `points_config.json`.
- **Double points** persists until the duration ends. To clear it when the stream starts, add `File.WriteAllText(DOUBLE_FILE, "0");` to the Reset Points action.
- **Top farder 2x:** The earn actions read from `TOP_FARDER_FILE` (default: `OBS files\textread\leader.txt`). Expected format: `Top Farder: USERNAME - 45`. Change the path if your fard counter writes to a different file.
- **Super Chat / Cheer:** Uses `points_command.py` (superchat/cheer subcommands). Currency conversion via Frankfurter (free, no key). Add these actions to the same blocking queue as earn/spend. Anonymous cheers are skipped.

---

## User-Facing Summary

- **[youtube-description.md](youtube-description.md)** — Full YouTube description (channel assets, stream commands, Discord, chat commands)
- **[user-facing-summary.md](user-facing-summary.md)** — Chat commands only (copy-paste block)
- **[twitch-panel.md](twitch-panel.md)** — Formatted version for Twitch panels
