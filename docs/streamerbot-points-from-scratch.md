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

**Action numbering matches Streamer.bot.** Keep actions in sync with this doc.

| # | Action | Trigger | Purpose |
|---|--------|---------|---------|
| 01 | Earn Points (on chat) | Message Received | +1 per message (30s cooldown) |
| 02 | Earn Points (passive) | Present Viewers | +1 per tick for users already in file |
| 03 | First Words Bonus | (add to First Words) | +5 on first chat |
| 04 | Check Points | !points | Show viewer their balance |
| 05 | Top Points | !toppoints | Show top 3 point holders |
| 06 | Spawn Monster | !spawn | Spend points to spawn monster |
| 07 | Spawn Champion | !champion | Spend points to spawn champion (2× base) |
| 08 | Drop Gold | !gold | Spend points to drop gold |
| 09 | Curse Item | !curse | Spend points to curse equipped item |
| 10 | Spawn Random Gas | !gas | Spend points to spawn random gas |
| 11 | Random Scroll | !scroll | Spend points to use random scroll |
| 12 | Place Trap | !trap | Spend points to place random trap |
| 13 | Transmute | !transmute | Spend points to transmute item |
| 14 | Bee | !bee | Spend points to summon allied bee |
| 15 | Ward | !ward | Spend points to summon ward |
| 16 | Random Buff | !buff | Spend points to apply random buff |
| 17 | Random Debuff | !debuff | Spend points to apply random debuff |
| 18 | Cursed Wand Effect | !wand | Spend points to trigger cursed wand effect |
| 19 | Double Points | !doublepoints | Streamer only: 2× points for N minutes |
| 20 | Earn Points (Cheer) | Twitch Cheer | 1 pt per bit |
| 21 | Earn Points (Super Chat) | YouTube Super Chat | 1 pt per $0.01 |
| 22 | Reset Points | Stream Started | Clear non-donor points |
| 23 | Spend OFF | Hotkey (Stream Deck OFF) | Disable spend commands |
| 24 | Spend ON | Hotkey (Stream Deck ON) | Enable spend commands |
| 25 | Helpers/Hurters OFF | Hotkey (Stream Deck OFF) | Disable helpers vs hurters |
| 26 | Helpers/Hurters ON | Hotkey (Stream Deck ON) | Enable helpers vs hurters |
| 27 | !myside | !myside | Remind user their side (no cost) |
| 28 | !switch | !switch | Switch helper/hurter side (cost configurable) |
| 29 | !heal | !heal | Helper: heal hero ~15% HP |
| 30 | !cleanse | !cleanse | Helper: remove one random debuff |
| 31 | !dew | !dew | Helper: drop dewdrop near hero |
| 32 | !hex | !hex | Hurter: apply Hex debuff |
| 33 | !degrade | !degrade | Hurter: apply Degrade debuff |
| 34 | !sabotage | !sabotage | Hurter: remove one random buff |

---

## YouTube Support

- **Commands (!spawn, !champion, !gold, !curse, !gas, !scroll, !trap, !transmute, !bee, !ward, !buff, !debuff, !wand, !points, !toppoints, !myside, !switch, !heal, !cleanse, !dew, !hex, !degrade, !sabotage):** When creating the command, enable **both Twitch and YouTube** as sources so one action handles both platforms.
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
2. Execute C# Code (reads spawn_result.txt, sets %spawnResult%, %userPointsRemaining%, and any extra vars)
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

**Result format:** Success results are `ok|extra|pts` (spawn: `ok|pts`). The last value is the user's remaining points. Each C# block parses this and sets `%userPointsRemaining%` so you can tell chatters their balance after use.

**Quick reference — success messages by command:**

| Command | Success Message |
|---------|-----------------|
| !spawn | `%userName% spawned a %rawInput%! You have %userPointsRemaining% points left.` |
| !champion | `%userName% spawned a champion %championMonster%! You have %userPointsRemaining% points left.` |
| !gold | `%userName% dropped %goldAmount% gold! You have %userPointsRemaining% points left.` |
| !curse | `%userName% cursed your %curseItemName%! You have %userPointsRemaining% points left.` |
| !gas | `%userName% spewed %gasName%! You have %userPointsRemaining% points left.` |
| !scroll | `%userName% used a random scroll: %scrollName%! You have %userPointsRemaining% points left.` |
| !trap | `%userName% placed a %trapName% nearby! You have %userPointsRemaining% points left.` |
| !transmute | `%userName% transmuted an item into %transmuteItemName%! You have %userPointsRemaining% points left.` |
| !bee | `%userName% summoned a bee to help you! You have %userPointsRemaining% points left.` |
| !ward | `%userName% summoned a ward to help you! You have %userPointsRemaining% points left.` |
| !buff | `%userName% gave you %buffName%! You have %userPointsRemaining% points left.` |
| !debuff | `%userName% afflicted you with %debuffName%! You have %userPointsRemaining% points left.` |
| !wand | `%userName% triggered a cursed wand effect: %wandEffectName%! You have %userPointsRemaining% points left.` |
| !myside | `%spawnResult%` (result is the full message, e.g. "You're on the helper side!") |
| !switch | `%userName% switched to %newSide%! You have %userPointsRemaining% points left.` (C# sets `newSide` from parts[1]) |
| !heal | `%userName% healed you! You have %userPointsRemaining% points left.` |
| !cleanse | `%userName% cleansed %allyName%! You have %userPointsRemaining% points left.` |
| !dew | `%userName% dropped a dewdrop! You have %userPointsRemaining% points left.` |
| !hex | `%userName% hexed you! You have %userPointsRemaining% points left.` |
| !degrade | `%userName% degraded you! You have %userPointsRemaining% points left.` |
| !sabotage | `%userName% sabotaged %allyName%! You have %userPointsRemaining% points left.` |

---

## File Location

**Path quoting:** If your project path contains spaces (e.g. `My Games`), the script path in Run a Program **Arguments** must be in quotes: `"C:\...\points_command.py"`. Otherwise the command will fail.

Points are stored in (update the path in all C# code if your project is elsewhere):
```
C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt
```
Format: one line per user: `username|points|lastEarnTimestamp|donationPts|role` (5 columns). `role` is `helper` or `hurter` (assigned alternating on first chat); legacy 4-column lines have no role until next earn. 3-column lines treated as donationPts=0.
The file is created automatically when the first action runs.

**Helpers vs Hurters:** `helper_hurter_counter.txt` in the same folder stores an integer for alternating role assignment (0=helper, 1=hurter, 2=helper, …). Reset Points sets it to 0.

**Double points:** Stored in `double_points_end.txt` (Unix timestamp when 2x ends; `0` = off). Created when you first use `!doublepoints`.

---

## Configuration (edit in the C# code)

- **Points per message:** `1` (change `POINTS_PER_MESSAGE` in Earn Points on chat)
- **Points per passive tick:** `1` (change `POINTS_PER_TICK` in Earn Points passive)
- **Bot exclusion:** `daltongoesslow` never earns points (change `BOT_USER` in Earn Points on chat and passive)
- **Chat cooldown:** `30` seconds (change `COOLDOWN_SEC` in Earn Points on chat; `0` = no cooldown)
- **Passive cooldown:** `60` seconds (change `COOLDOWN_SEC` in Earn Points passive; shares `lastEarn` with chat)
- **Points costs:** Edit `points_config.json` or open **http://localhost:5000** in your browser (main control page; overlay server must be running). The overlay also has **Delete all points** (clears non-donor only; donors keep donation) and **Delete all donor points** (full wipe).
- **Donation rate:** 1 point per $0.01 (Super Chat uses Frankfurter API for conversion; not in points config)
- **Top farder 2x:** Set `TOP_FARDER_FILE` to the path of the text file (default: `OBS files\textread\leader.txt`). Expected format: `Top Farder: USERNAME - 45`. That user always earns 2x points.
- **Subscriber / member 2x:** Twitch subscribers and YouTube channel members earn 2x points. Uses Streamer.bot variables `isSubscribed` (Twitch) and `userIsSponsor` (YouTube). Stacks with double points and top farder (e.g. 4x or 8x when multiple apply).

---

## Action 01: Earn Points (on chat message)

**Trigger:** Message Received (Twitch → Triggers → Message Received)

**Sub-Action:** Execute C# Code (Inline)

Uses plain-text format (no JSON) to avoid System.Core dependency. **All code that reads or writes `viewer_points.txt` must use the same lock file** (`viewer_points.txt.lock`) so Earn Points, Passive earn, !points, !toppoints, First Words, Reset, the overlay server, and Python (superchat, spawn, etc.) don't overwrite each other. Earn Points on chat, Earn Points passive, First Words, Check Points, Top Points, and Reset Points all use this lock.

```csharp
using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";
    const string LOCK_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt.lock";
    const string COUNTER_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\helper_hurter_counter.txt";
    const string ASSIGNED_ROLE_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\assigned_role.txt";
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
                int donationPts = 0;
                string role = "";
                if (data.ContainsKey(key))
                {
                    pts = data[key].Item1;
                    lastEarn = data[key].Item2;
                    donationPts = data[key].Item3;
                    role = data[key].Item4 ?? "";
                }

                if (COOLDOWN_SEC > 0 && lastEarn > 0 && unixNow - lastEarn < COOLDOWN_SEC)
                    return false;

                // Helpers vs Hurters: assign role for new users or legacy 4-col (no role)
                bool justAssigned = false;
                if (string.IsNullOrEmpty(role) || (role != "helper" && role != "hurter"))
                {
                    int counter = 0;
                    if (File.Exists(COUNTER_FILE) && int.TryParse(File.ReadAllText(COUNTER_FILE).Trim(), out int c))
                        counter = c;
                    role = (counter % 2 == 0) ? "helper" : "hurter";
                    File.WriteAllText(COUNTER_FILE, (counter + 1).ToString());
                    justAssigned = true;
                }

                int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopFarder(key) ? 2 : 1) * (IsSubscriberOrMember() ? 2 : 1);
                int toAdd = POINTS_PER_MESSAGE * mult;
                pts += toAdd;
                data[key] = Tuple.Create(pts, unixNow, donationPts, role);
                WriteAll(data);
                // Optional: write assigned_role.txt so a follow-up action can send "You're on the helper/hurter side!" to the user
                if (justAssigned) try { File.WriteAllText(ASSIGNED_ROLE_FILE, user + "|" + role); } catch { }
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

    Dictionary<string, Tuple<int, long, int, string>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long, int, string>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l; int d = 0;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                    {
                        if (parts.Length >= 4) int.TryParse(parts[3].Trim(), out d);
                        string role = (parts.Length >= 5 && (parts[4] == "helper" || parts[4] == "hurter")) ? parts[4] : "";
                        result[k] = Tuple.Create(p, l, d, role);
                    }
                }
            }
        }
        catch { }
        return result;
    }

    void WriteAll(Dictionary<string, Tuple<int, long, int, string>> data)
    {
        var lines = new List<string>();
        foreach (var kv in data)
        {
            string role = (kv.Value.Item4 == "helper" || kv.Value.Item4 == "hurter") ? kv.Value.Item4 : "";
            lines.Add(kv.Key + "|" + kv.Value.Item1 + "|" + kv.Value.Item2 + "|" + kv.Value.Item3 + "|" + role);
        }
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

    // Twitch: isSubscribed. YouTube: userIsSponsor (channel member). Stacks with double points & top farder.
    bool IsSubscriberOrMember()
    {
        if (CPH.TryGetArg("isSubscribed", out string tw) && tw.Equals("True", StringComparison.OrdinalIgnoreCase)) return true;
        if (CPH.TryGetArg("userIsSponsor", out string yt) && yt.Equals("True", StringComparison.OrdinalIgnoreCase)) return true;
        return false;
    }
}
```

**Optional – First-message side announcement:** When a user is assigned a helper/hurter role for the first time, the C# writes `assigned_role.txt` with `username|helper` or `username|hurter`. Add the following sub-actions **after** the Earn Points C# (Action 01) or First Words C# (Action 03) to message the user using the commandSource pattern:

**Sub-actions (in order):**

1. **If File Exists** → `Lastest UI/assigned_role.txt` → **True** branch only (leave False empty):
   - **Execute C# Code (Inline)** — read the file, parse `username|role`, set `%assignedRoleUsername%` and `%assignedRoleSide%`, then delete the file:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\assigned_role.txt";

    public bool Execute()
    {
        if (!File.Exists(FILE)) return true;
        try
        {
            string content = File.ReadAllText(FILE).Trim();
            File.Delete(FILE);
            var parts = content.Split('|');
            if (parts.Length >= 2)
            {
                string username = parts[0].Trim();
                string role = parts[1].Trim().ToLowerInvariant();
                CPH.SetArgument("assignedRoleUsername", username);
                CPH.SetArgument("assignedRoleSide", role);
            }
        }
        catch (Exception ex) { CPH.LogInfo("Assigned role: " + ex.Message); }
        return true;
    }
}
```

   - **Conditional:** `if ("%assignedRoleUsername%" Not Equals "")` → **True** branch:
     - **If** `("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%assignedRoleUsername%, you're on the %assignedRoleSide% side!`
     - **If** `("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%assignedRoleUsername%, you're on the %assignedRoleSide% side!`
     - Leave **False Result** empty for both platform checks.

**Note:** `commandSource` is typically set for Command Triggered events. For **Message Received** or **First Words**, Streamer.bot may use `platform` or `broadcastPlatform` instead. If `commandSource` is empty, try `%platform%` or create separate actions per platform (one for Twitch Message Received, one for YouTube Message Received) and send only to that platform.

---

## Action 02: Earn Points (passive – present viewers)

**Trigger:** Present Viewers (Twitch → Triggers → Present Viewers)

Adds points over time to viewers who are already in the file (have chatted at least once). Enable **Live Update** under Platform → Twitch → Settings and set the interval (e.g. 5 minutes).

**Note:** The C# tries both `userName` and `presentUserName`. If Present Viewers uses a list, add **Add Present User** (index 0, 1, 2, …) in a loop before the C# so each viewer gets processed. Subscriber/member 2x applies when Streamer.bot provides `isSubscribed` or `userIsSponsor` for that viewer (Message Received does; Present Viewers may not, depending on your setup).

**Sub-Action:** Execute C# Code (Inline)

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
            if (!AcquirePointsLock()) return false;
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

                int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopFarder(key) ? 2 : 1) * (IsSubscriberOrMember() ? 2 : 1);
                int toAdd = POINTS_PER_TICK * mult;
                pts += toAdd;
                int donationPts = data[key].Item3;
                string role = data[key].Item4 ?? "";
                data[key] = Tuple.Create(pts, unixNow, donationPts, role);
                WriteAll(data);
                return true;
            }
            finally { ReleasePointsLock(); }
        }
        catch (Exception ex) { CPH.LogInfo("Passive earn: " + ex.Message); return false; }
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

    Dictionary<string, Tuple<int, long, int, string>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long, int, string>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l; int d = 0;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                    {
                        if (parts.Length >= 4) int.TryParse(parts[3].Trim(), out d);
                        string role = (parts.Length >= 5 && (parts[4] == "helper" || parts[4] == "hurter")) ? parts[4] : "";
                        result[k] = Tuple.Create(p, l, d, role);
                    }
                }
            }
        }
        catch { }
        return result;
    }

    void WriteAll(Dictionary<string, Tuple<int, long, int, string>> data)
    {
        var lines = new List<string>();
        foreach (var kv in data)
        {
            string role = (kv.Value.Item4 == "helper" || kv.Value.Item4 == "hurter") ? kv.Value.Item4 : "";
            lines.Add(kv.Key + "|" + kv.Value.Item1 + "|" + kv.Value.Item2 + "|" + kv.Value.Item3 + "|" + role);
        }
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

    bool IsSubscriberOrMember()
    {
        if (CPH.TryGetArg("isSubscribed", out string tw) && tw.Equals("True", StringComparison.OrdinalIgnoreCase)) return true;
        if (CPH.TryGetArg("userIsSponsor", out string yt) && yt.Equals("True", StringComparison.OrdinalIgnoreCase)) return true;
        return false;
    }
}
```

---

## Action 03: First Words Bonus (add to your existing First Words action)

Add this as a **sub-action** to your existing First Words action (before or after the shout out). Gives 5 points the first time a user chats.

**Sub-Action:** Execute C# Code (Inline)

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
    const int FIRST_WORDS_BONUS = 5;

    public bool Execute()
    {
        string user = CPH.TryGetArg("userName", out string u) ? u : null;
        if (string.IsNullOrEmpty(user)) return false;

        try
        {
            if (!AcquirePointsLock()) return false;
            try
            {
                long unixNow = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;
                var data = ReadAll();
                string key = user.ToLowerInvariant();
                int pts = data.ContainsKey(key) ? data[key].Item1 : 0;
                int donationPts = data.ContainsKey(key) ? data[key].Item3 : 0;
                string role = data.ContainsKey(key) ? (data[key].Item4 ?? "") : "";
                int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopFarder(key) ? 2 : 1) * (IsSubscriberOrMember() ? 2 : 1);
                int toAdd = FIRST_WORDS_BONUS * mult;
                pts += toAdd;
                data[key] = Tuple.Create(pts, unixNow, donationPts, role);
                WriteAll(data);
                return true;
            }
            finally { ReleasePointsLock(); }
        }
        catch (Exception ex) { CPH.LogInfo("First words bonus: " + ex.Message); return false; }
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

    Dictionary<string, Tuple<int, long, int, string>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long, int, string>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l; int d = 0;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                    {
                        if (parts.Length >= 4) int.TryParse(parts[3].Trim(), out d);
                        string role = (parts.Length >= 5 && (parts[4] == "helper" || parts[4] == "hurter")) ? parts[4] : "";
                        result[k] = Tuple.Create(p, l, d, role);
                    }
                }
            }
        }
        catch { }
        return result;
    }

    void WriteAll(Dictionary<string, Tuple<int, long, int, string>> data)
    {
        var lines = new List<string>();
        foreach (var kv in data)
        {
            string role = (kv.Value.Item4 == "helper" || kv.Value.Item4 == "hurter") ? kv.Value.Item4 : "";
            lines.Add(kv.Key + "|" + kv.Value.Item1 + "|" + kv.Value.Item2 + "|" + kv.Value.Item3 + "|" + role);
        }
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

    bool IsSubscriberOrMember()
    {
        if (CPH.TryGetArg("isSubscribed", out string tw) && tw.Equals("True", StringComparison.OrdinalIgnoreCase)) return true;
        if (CPH.TryGetArg("userIsSponsor", out string yt) && yt.Equals("True", StringComparison.OrdinalIgnoreCase)) return true;
        return false;
    }
}
```

Edit `FIRST_WORDS_BONUS` to change the amount (default 5).

---

## Action 04: Check Points (!points)

**Trigger:** Command Triggered → create command `!points` (enable **both Twitch and YouTube** as sources)

**Sub-Actions (in order):**

1. **Execute C# Code** (Inline):

```csharp
using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";
    const string LOCK_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt.lock";

    public bool Execute()
    {
        string user = CPH.TryGetArg("userName", out string u) ? u : null;
        if (string.IsNullOrEmpty(user)) { CPH.SetArgument("userPoints", "0"); return true; }

        try
        {
            if (!AcquirePointsLock()) { CPH.SetArgument("userPoints", "0"); return true; }
            try
            {
                var data = ReadAll();
                int pts = 0;
                if (data.TryGetValue(user.ToLowerInvariant(), out var t))
                {
                    int p = t.Item1, d = t.Item3;
                    pts = (d > 0 && p < d) ? (p + d) : p;
                }
                CPH.SetArgument("userPoints", pts.ToString());
                return true;
            }
            finally { ReleasePointsLock(); }
        }
        catch { CPH.SetArgument("userPoints", "0"); return true; }
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

    Dictionary<string, Tuple<int, long, int, string>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long, int, string>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l; int d = 0;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                    {
                        if (parts.Length >= 4) int.TryParse(parts[3].Trim(), out d);
                        string role = (parts.Length >= 5 && (parts[4] == "helper" || parts[4] == "hurter")) ? parts[4] : "";
                        result[k] = Tuple.Create(p, l, d, role);
                    }
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

## Action 05: Top Points (!toppoints)

**Trigger:** Command Triggered → create command `!toppoints` (or `!leaderboard`) (enable **both Twitch and YouTube** as sources)

**Sub-Actions (in order):**

1. **Execute C# Code** (Inline):

```csharp
using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";
    const string LOCK_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt.lock";

    public bool Execute()
    {
        try
        {
            if (!AcquirePointsLock()) { CPH.SetArgument("topPointsResult", "Nobody has points yet. Chat to earn!"); return true; }
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
                            int p; int d = 0;
                            if (int.TryParse(parts[1].Trim(), out p) && !string.IsNullOrEmpty(name))
                            {
                                if (parts.Length >= 4) int.TryParse(parts[3].Trim(), out d);
                                int pts = (d > 0 && p < d) ? (p + d) : p;
                                list.Add(new Tuple<string, int>(name, pts));
                            }
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
            finally { ReleasePointsLock(); }
        }
        catch (Exception ex) { CPH.SetArgument("topPointsResult", "Error: " + ex.Message); return true; }
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
}
```

2. **Response message:** Use commandSource pattern:
   - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%topPointsResult%`
   - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%topPointsResult%`
   - Leave **False Result** empty for both.

**Example output:** `1. ViewerA (450 pts) | 2. ViewerB (320 pts) | 3. ViewerC (180 pts)`

---

## Action 06: Spawn Monster (!spawn, with points)

**Trigger:** Command Triggered → `!spawn` (enable **both Twitch and YouTube** as sources)

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python` (or full path to `python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" spawn %rawInput% %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds
   - **Note:** Use `%rawInput%` for the monster name (text after `!spawn`). If that's empty, try `%input1%` depending on your Streamer.bot version.

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%` and `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "Spawn failed (no result file - is overlay server running?)";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 2 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% spawned a %rawInput%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% spawned a %rawInput%! You have %userPointsRemaining% points left.`
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
- **Test manually:** Run `python "Lastest UI\points_command.py" spawn rat YourUsername` in a terminal—should write `ok|{pts}` to `spawn_result.txt` or an error.

---

## Action 07: Spawn Champion (!champion, with points)

**Trigger:** Command Triggered → `!champion` (enable **both Twitch and YouTube** as sources)

**Usage:** `!champion <monster>` (e.g. `!champion rat`, `!champion eye`). Spawns a **champion** version of the specified monster (random type: Blazing, Projecting, Antimagic, Giant, Blessed, or Growing). Same valid monsters as `!spawn`. **Cost:** always **2× the base spawn cost** — no early-zone discount.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" champion %rawInput% %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds
   - **Note:** `%rawInput%` = monster name (e.g. rat, eye). Same as spawn.

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%championMonster%`, and `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "Champion spawn failed (no result file - is overlay server running?)";
        string monsterName = "";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    monsterName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    monsterName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("championMonster", monsterName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% spawned a champion %championMonster%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% spawned a champion %championMonster%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 2× base monster cost (e.g. rat = 10 pts, eye = 140 pts). No half-price discount for spawning in a later zone.

**Add to the same blocking queue** as spawn, gold, and earn actions. Shares spawn cooldown with `!spawn`.

**Fails when:** Same as spawn (not in run, hero dead, no space, unknown monster).

---

## Action 08: Drop Gold (!gold, with points)

**Trigger:** Command Triggered → `!gold` (enable **both Twitch and YouTube** as sources)

**Usage:** `!gold <amount>` (e.g. `!gold 10`). Amount 1–100 required. Invalid amounts (0, negative, >100) are rejected.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python` (or full path to `python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" gold %rawInput% %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds
   - **Note:** `%rawInput%` = amount (required). `%userName%` = who ran the command.

2. **Execute C# Code** (Inline) — reads `spawn_result.txt`, sets `%spawnResult%`, `%goldAmount%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                if (string.IsNullOrEmpty(result)) result = "Gold failed (empty response)";
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    goldAmount = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    goldAmount = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message ?? "Gold failed"; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("goldAmount", goldAmount);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% dropped %goldAmount% gold! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% dropped %goldAmount% gold! You have %userPointsRemaining% points left.`
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

## Action 09: Curse Item (!curse, with points)

**Trigger:** Command Triggered → `!curse` (enable **both Twitch and YouTube** as sources)

**Usage:** `!curse` — curses a **random** equipped item (weapon, armor, ring, artifact, or misc). No slot needed.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" curse %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds
   - **Note:** No slot argument — the script picks a random equipped slot (weapon, armor, ring, artifact, misc).

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%curseItemName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("curseItemName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% cursed your %curseItemName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% cursed your %curseItemName%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 200 points per curse (edit via points config).

**Add to the same blocking queue** as spawn, gold, and earn actions.

**Fails when:** No curseable item in any slot — the script tries each slot (random order) and retries if a slot is empty or already cursed. Only fails if all 5 slots are empty or already cursed.

---

## Action 10: Spawn Random Gas (!gas, with points)

**Trigger:** Command Triggered → `!gas` (enable **both Twitch and YouTube** as sources)

**Usage:** `!gas` — spawns random gas (Chaotic Censer at level +3). Toxic, confusion, regrowth, storm clouds, smoke, stench, inferno, blizzard, or corrosive gas.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" gas %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%gasName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("gasName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% spewed %gasName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% spewed %gasName%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 75 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, and earn actions.

**Fails when:** No valid cell 2–6 tiles from hero in field of view.

---

## Action 11: Random Scroll (!scroll, with points)

**Trigger:** Command Triggered → `!scroll` (enable **both Twitch and YouTube** as sources)

**Usage:** `!scroll` — uses a random scroll like activating a +10 Unstable Spellbook. Picks from the full scroll pool (excluding transmutation), 50% chance for exotic version. Identify, Remove Curse, and Magic Mapping are half as likely.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" scroll %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds *(required — otherwise C# runs before the script writes the result file)*

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%scrollName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("scrollName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% used a random scroll: %scrollName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% used a random scroll: %scrollName%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 100 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, and earn actions.

**Fails when:** Not in an active run, hero dead, magic immune, or blinded.

---

## Action 12: Place Trap (!trap, with points)

**Trigger:** Command Triggered → `!trap` (enable **both Twitch and YouTube** as sources)

**Usage:** `!trap` — places a random **visible** trap 1–4 tiles from the hero (same placement logic as gold). Picks from a pool of 27 traps; instant-death and very high-damage traps (Grim, Disintegration, Pitfall, Explosive, Rockfall, Gnoll Rockfall) are blacklisted by default. Configurable in game code.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" trap %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%trapName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("trapName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% placed a %trapName% nearby! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% placed a %trapName% nearby! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 50 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, and earn actions.

**Fails when:** Not in an active run, hero dead, no empty passable tile 1–4 tiles from hero (e.g. surrounded), or all traps blacklisted.

---

## Action 13: Transmute (!transmute, with points)

**Trigger:** Command Triggered → `!transmute` (enable **both Twitch and YouTube** as sources)

**Usage:** `!transmute` — transmutes a **random** transmutable item from the hero's bag or equipped slots (weapon, armor, ring, artifact, misc, second weapon). Same rules as Scroll of Transmutation: melee/missile weapons (except pickaxe on mining level, plain darts), potions (no brews/elixirs), scrolls, non-unique artifacts, rings, wands, trinkets, seeds, runestones. Cost 150 pts (configurable).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" transmute %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%transmuteItemName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("transmuteItemName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% transmuted an item into %transmuteItemName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% transmuted an item into %transmuteItemName%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 150 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, trap, and earn actions.

**Fails when:** Not in an active run, hero dead, or no transmutable item (need at least one weapon, armor, ring, artifact, potion, scroll, wand, seed, runestone, or trinket in bag or equipped).

---

## Action 14: Bee (!bee, with points)

**Trigger:** Command Triggered → `!bee` (enable **both Twitch and YouTube** as sources)

**Usage:** `!bee` — summons an **allied bee** next to the hero for 50 turns. The bee fights for the player like the one from Elixir of Honeyed Healing. Cost 75 pts (configurable).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" bee %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%allyName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("allyName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% summoned a bee to help you! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% summoned a bee to help you! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 75 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, trap, transmute, and earn actions.

**Fails when:** Not in an active run, hero dead, or no adjacent passable tile (hero surrounded).

---

## Action 15: Ward (!ward, with points)

**Trigger:** Command Triggered → `!ward` (enable **both Twitch and YouTube** as sources)

**Usage:** `!ward` — summons a **ward** (Wand of Warding style) near the hero. Level scales with depth: +0 sewers, +3 prison, +5 caves, +7 city, +8 halls. If the ward lands on the same tile as an existing ward, it upgrades that ward instead. Cost 30 pts (configurable).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" ward %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%allyName%`, and `%userPointsRemaining%`:

Use the same C# code block as Action 14: Bee, which reads the result file and sets `spawnResult`, `allyName`, and `userPointsRemaining`.

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% summoned a ward to help you! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% summoned a ward to help you! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 30 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, trap, transmute, bee, and earn actions.

**Fails when:** Not in an active run, hero dead, or no space to spawn ward (hero surrounded).

---

## Action 16: Random Buff (!buff, with points)

**Trigger:** Command Triggered → `!buff` (enable **both Twitch and YouTube** as sources)

**Usage:** `!buff` — applies a random buff to the hero. Picks from: Haste, Adrenaline, Invisibility, Levitation, Barrier (10% HP shield), Healing (10% HP over 10 turns), Recharging, MindVision. Excludes Paralysis, Burning, Poison, Awareness.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" buff %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%buffName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("buffName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% gave you %buffName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% gave you %buffName%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 75 points (edit via points config).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, and earn actions.

---

## Action 17: Random Debuff (!debuff, with points)

**Trigger:** Command Triggered → `!debuff` (enable **both Twitch and YouTube** as sources)

**Usage:** `!debuff` — applies a random debuff to the hero. Picks from: Blindness, Weakness, Slow, Cripple, Roots, Daze, Vulnerable. Excludes Paralysis, Burning, Poison. Roots is skipped if the hero is flying.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" debuff %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%debuffName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("debuffName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% afflicted you with %debuffName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% afflicted you with %debuffName%! You have %userPointsRemaining% points left.`
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
- **Test manually:** From project root: `python "Lastest UI\points_command.py" scroll YourUsername`. From `Lastest UI` folder: `python points_command.py scroll YourUsername`. Should write `ok|ScrollName|{pts}` or an error to `spawn_result.txt`. For trap: `python points_command.py trap YourUsername` → `ok|TrapName|{pts}` or error.

---

## Action 18: Cursed Wand Effect (!wand, with points)

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

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%wandEffectName%`, and `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("wandEffectName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% triggered a cursed wand effect: %wandEffectName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% triggered a cursed wand effect: %wandEffectName%! You have %userPointsRemaining% points left.`
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

## Action 06a: Spawn Monster – File Bridge (alternative, when %output1% is empty)

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

## Action 19: Double Points (!doublepoints, streamer command, timed)

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

## Action 20: Earn Points (Cheer)

**Rate:** 100 bits = $1 = 100 points. **When !doublepoints is active, Cheer points are doubled.**

**Add this action to your points queue** (same blocking queue as earn/spend) to avoid race conditions.

**Trigger:** Twitch → Triggers → Cheer

**Sub-Action:** Run Program
- **Program:** `python`
- **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" cheer %bits% %userName%`
- **Working directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`

**Note:** Anonymous cheers are skipped (no username to credit).

---

## Action 21: Earn Points (Super Chat)

**Rate:** 1 point per $0.01 USD. Uses the [Frankfurter API](https://www.frankfurter.app/) for currency conversion (no API key). **When !doublepoints is active, Super Chat points are doubled.**

**Add this action to your points queue** (same blocking queue as earn/spend) to avoid race conditions.

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

---

## Action 22: Reset Points (every stream)

**Trigger:** Stream Started (Twitch → Triggers → Stream Started). For YouTube, use the equivalent "Stream Online" or "Stream Started" trigger.

**Sub-Action:** Execute C# Code (Inline)

Clears non-donor points when you go live. Donors (Super Chat / Cheer) keep their donation amount; everyone else starts fresh.

```csharp
using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt";
    const string LOCK_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt.lock";
    const string COUNTER_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\helper_hurter_counter.txt";

    public bool Execute()
    {
        try
        {
            if (!AcquirePointsLock()) return false;
            try
            {
                var data = ReadAll();
                var toWrite = new Dictionary<string, Tuple<int, long, int, string>>(StringComparer.OrdinalIgnoreCase);
                foreach (var kv in data)
                {
                    int donationPts = kv.Value.Item3;
                    string role = kv.Value.Item4 ?? "";
                    if (donationPts > 0)
                        toWrite[kv.Key] = Tuple.Create(donationPts, 0L, donationPts, role);
                }
                WriteAll(toWrite);
                // Reset Helpers vs Hurters counter for new stream
                try { File.WriteAllText(COUNTER_FILE, "0"); } catch { }
                return true;
            }
            finally { ReleasePointsLock(); }
        }
        catch (Exception ex) { CPH.LogInfo("Reset points: " + ex.Message); return false; }
    }

    Dictionary<string, Tuple<int, long, int, string>> ReadAll()
    {
        var result = new Dictionary<string, Tuple<int, long, int, string>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(FILE)) return result;
        try
        {
            foreach (string line in File.ReadAllLines(FILE))
            {
                string[] parts = line.Split('|');
                if (parts.Length >= 3)
                {
                    string k = parts[0].Trim();
                    int p; long l; int d = 0;
                    if (int.TryParse(parts[1].Trim(), out p) && long.TryParse(parts[2].Trim(), out l))
                    {
                        if (parts.Length >= 4) int.TryParse(parts[3].Trim(), out d);
                        string role = (parts.Length >= 5 && (parts[4] == "helper" || parts[4] == "hurter")) ? parts[4] : "";
                        result[k] = Tuple.Create(p, l, d, role);
                    }
                }
            }
        }
        catch { }
        return result;
    }

    void WriteAll(Dictionary<string, Tuple<int, long, int, string>> data)
    {
        var lines = new List<string>();
        foreach (var kv in data)
        {
            string role = (kv.Value.Item4 == "helper" || kv.Value.Item4 == "hurter") ? kv.Value.Item4 : "";
            lines.Add(kv.Key + "|" + kv.Value.Item1 + "|" + kv.Value.Item2 + "|" + kv.Value.Item3 + "|" + role);
        }
        File.WriteAllLines(FILE, lines.ToArray());
    }

    bool AcquirePointsLock()
    {
        for (int i = 0; i < 200; i++)
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
}
```

---

## Action 23: Spend OFF / Action 24: Spend ON (Stream Deck switch)

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

**Coverage:** All spend commands in `points_command.py` (spawn, champion, gold, curse, gas, scroll, trap, transmute, bee, ward, buff, debuff, wand, heal, cleanse, dew, hex, degrade, sabotage, switch) check for `spend_disabled.txt` and return "Spending is currently disabled by the streamer." when the file exists. If you add new spend commands to the script, add the same `is_spend_disabled()` check at the start of the handler.

---

## Action 25: Helpers/Hurters OFF / Action 26: Helpers/Hurters ON (Stream Deck switch)

**Purpose:** Same pattern as Spend OFF/ON. When Helpers/Hurters is OFF, the system treats everyone as "both" (no role-based point earning on boss/death, no discounts, !switch returns "Helpers/Hurters is currently turned off."). When ON, full helpers vs hurters behavior.

### Helpers/Hurters OFF action

**Trigger:** Hotkey (Stream Deck switch → OFF state)

**Sub-Action:** Execute C# Code (Inline)

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\helpers_hurters_disabled.txt";

    public bool Execute()
    {
        File.WriteAllText(FILE, "1");
        return true;
    }
}
```

### Helpers/Hurters ON action

**Trigger:** Hotkey (Stream Deck switch → ON state)

**Sub-Action:** Execute C# Code (Inline)

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\helpers_hurters_disabled.txt";

    public bool Execute()
    {
        if (File.Exists(FILE))
            File.Delete(FILE);
        return true;
    }
}
```

**Stream Deck setup:** Create a second Action Switch. Assign Helpers/Hurters ON to the ON state and Helpers/Hurters OFF to the OFF state.

---

## Action 27: !myside

**Trigger:** Command Triggered → `!myside` (enable **both Twitch and YouTube** as sources)

**Usage:** `!myside` — reminds the user their assigned side (helper or hurter). **No cost.** Requires Helpers vs Hurters to be ON; if OFF, returns "Helpers/Hurters is currently turned off."

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" myside %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
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

3. **Conditional:** Use commandSource pattern to send `%spawnResult%` to chat (no ok/error branch — result is the full message):
   - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
   - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
   - Leave **False Result** empty for both.

**Add to the same blocking queue** as other commands.

---

## Action 28: !switch

**Trigger:** Command Triggered → `!switch`

**Usage:** `!switch` — switch from helper to hurter or vice versa. Cost configurable (default 50 pts). Requires Helpers vs Hurters ON.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" switch %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%newSide%`, `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string newSide = "";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    newSide = parts.Length >= 2 ? parts[1].Trim() : "";
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    newSide = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("newSide", newSide);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% switched to %newSide%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% switched to %newSide%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

---

## Action 29: !heal (Helper only)

**Trigger:** Command Triggered → `!heal`

**Usage:** `!heal` — **Helper only.** Heals hero ~15% HP. Cost configurable (default 100 pts). Helper discount applies.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" heal %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% healed you! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% healed you! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

---

## Action 30: !cleanse (Helper only)

**Trigger:** Command Triggered → `!cleanse`

**Usage:** `!cleanse` — **Helper only.** Removes one random debuff from the hero. Cost configurable (default 150 pts).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" cleanse %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%allyName%`, `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts.Length >= 2 ? parts[1].Trim() : "";
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("allyName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% cleansed %allyName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% cleansed %allyName%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

---

## Action 31: !dew (Helper only)

**Trigger:** Command Triggered → `!dew`

**Usage:** `!dew` — **Helper only.** Drops a dewdrop near the hero. Cost configurable (default 30 pts).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" dew %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% dropped a dewdrop! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% dropped a dewdrop! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

---

## Action 32: !hex (Hurter only)

**Trigger:** Command Triggered → `!hex`

**Usage:** `!hex` — **Hurter only.** Applies Hex debuff to the hero. Cost configurable (default 75 pts).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" hex %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% hexed you! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% hexed you! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

---

## Action 33: !degrade (Hurter only)

**Trigger:** Command Triggered → `!degrade`

**Usage:** `!degrade` — **Hurter only.** Applies Degrade debuff to the hero. Cost configurable (default 100 pts).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" degrade %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% degraded you! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% degraded you! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

---

## Action 34: !sabotage (Hurter only)

**Trigger:** Command Triggered → `!sabotage`

**Usage:** `!sabotage` — **Hurter only.** Removes one random buff from the hero. Cost configurable (default 75 pts).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" sabotage %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%allyName%`, `%userPointsRemaining%`:

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
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    itemName = parts.Length >= 2 ? parts[1].Trim() : "";
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    itemName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("allyName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% sabotaged %allyName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% sabotaged %allyName%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

---

## Action 35: !corruptally (Helper only)

**Trigger:** Command Triggered → `!corruptally`

**Usage:** `!corruptally` — **Helper only.** Summons a corrupted (allied) enemy from the current biome to fight for you. Boss floors allowed. Cost configurable (default 100 pts). Helper discount applies.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" corruptally %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%mobName%`, `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string mobName = "";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 3 && int.TryParse(parts[parts.Length - 1].Trim(), out _))
                {
                    userPointsRemaining = parts[parts.Length - 1].Trim();
                    mobName = parts.Length >= 2 ? parts[1].Trim() : "";
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    mobName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("mobName", mobName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% summoned a corrupted %mobName% to fight for you! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% summoned a corrupted %mobName% to fight for you! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Add to the same blocking queue** as other helper commands.

---

## Commands Quick Reference

| Command | Usage | Cost | Description |
|---------|-------|------|-------------|
| **!points** | `!points` | Free | Check your point balance. |
| **!toppoints** | `!toppoints` | Free | Show top 3 point holders. |
| **!spawn** | `!spawn <monster>` | Varies by monster (5–80 pts) | Spawn a monster near the hero. Half price when spawned beyond its native biome. Valid monsters: rat, albino, snake, gnoll, crab, slime, swarm, thief, skeleton, bat, brute, shaman, spinner, dm100, guard, necromancer, ghoul, elemental, warlock, monk, golem, succubus, eye, scorpio. |
| **!champion** | `!champion <monster>` | 2× base (10–160 pts) | Spawn a **champion** version of the monster (random type: Blazing, Projecting, Antimagic, Giant, Blessed, Growing). Same monster list as spawn. No zone discount. |
| **!gold** | `!gold <amount>` | 2 pts per gold | Drop gold near the hero. Amount 1–100 required (e.g. `!gold 10` = 20 pts). |
| **!curse** | `!curse` | 200 pts | Curse a **random** equipped item (weapon, armor, ring, artifact, or misc). |
| **!gas** | `!gas` | 75 pts | Spawn random gas (Chaotic Censer +3). Toxic, confusion, regrowth, storm clouds, smoke, stench, inferno, blizzard, or corrosive gas. |
| **!scroll** | `!scroll` | 100 pts | Use a random scroll (like +10 Unstable Spellbook). 50% chance for exotic version. |
| **!trap** | `!trap` | 50 pts | Place a random visible trap 1–4 tiles from the hero. Pool of 27 traps (instant-death/high-damage ones blacklisted). |
| **!transmute** | `!transmute` | 150 pts | Transmute a random transmutable item from bag or equipped. Same rules as Scroll of Transmutation. |
| **!bee** | `!bee` | 75 pts | Summon an allied bee next to the hero for 50 turns. Fights for you like Elixir of Honeyed Healing. |
| **!buff** | `!buff` | 75 pts | Apply a random buff (Haste, Healing, Barrier, Invisibility, etc.). Healing = 10% HP over 10 turns; Barrier = 10% HP shield. |
| **!debuff** | `!debuff` | 50 pts | Apply a random debuff (Blindness, Slow, Roots, Daze, etc.). Excludes Paralysis, Burning, Poison. |
| **!wand** | `!wand common` (tier required) | 50–400 pts | Trigger a cursed wand effect. Tier required: common, uncommon, rare, or veryrare. |
| **!doublepoints** | `!doublepoints <minutes>` | — | **Streamer only.** 2× points for N minutes (max 120). `!doublepoints 5` for 5 min. |
| **!myside** | `!myside` | Free | Remind user their side (helper/hurter). Requires Helpers vs Hurters ON. |
| **!switch** | `!switch` | Configurable (default 50) | Switch helper ↔ hurter. Requires Helpers vs Hurters ON. |
| **!heal** | `!heal` | Configurable (default 100) | **Helper only.** Heal hero ~15% HP. |
| **!cleanse** | `!cleanse` | Configurable (default 150) | **Helper only.** Remove one random debuff. |
| **!dew** | `!dew` | Configurable (default 30) | **Helper only.** Drop dewdrop near hero. |
| **!corruptally** | `!corruptally` | Configurable (default 100) | **Helper only.** Summon a corrupted ally from the current biome. Boss floors allowed. |
| **!hex** | `!hex` | Configurable (default 75) | **Hurter only.** Apply Hex debuff. |
| **!degrade** | `!degrade` | Configurable (default 100) | **Hurter only.** Apply Degrade debuff. |
| **!sabotage** | `!sabotage` | Configurable (default 75) | **Hurter only.** Remove one random buff. |

**Spawn costs (base):** rat 5, albino/snake/gnoll 10, crab/slime/swarm 15, thief/skeleton/dm100 20, guard/necromancer/spinner 25, bat/brute 30, shaman 35, ghoul/elemental 40, warlock 45, monk/golem 50, succubus 60, eye 70, scorpio 80. Unknown monsters default to 100. Edit `points_config.json` or use the config UI to change.

---

## Summary

| # | Action | Trigger | Purpose |
|---|--------|---------|---------|
| 01 | Earn Points (on chat) | Message Received | +1 per message (30s cooldown; 2x double/top farder/sub) |
| 02 | Earn Points (passive) | Present Viewers | +1 per tick (60s cooldown) |
| 03 | First Words Bonus | (add to First Words) | +5 on first chat |
| 04 | Check Points | !points | Show viewer their balance |
| 05 | Top Points | !toppoints | Show top 3 point holders |
| 06 | Spawn Monster | !spawn | Spend points (cost varies by monster) |
| 07 | Spawn Champion | !champion | Spawn champion (2× base, no discount) |
| 08 | Drop Gold | !gold | Spend points to drop gold |
| 09 | Curse Item | !curse | Spend points to curse equipped item |
| 10 | Spawn Gas | !gas | Spend points to spawn random gas |
| 11 | Random Scroll | !scroll | Spend points to use random scroll |
| 12 | Place Trap | !trap | Spend points to place trap |
| 13 | Transmute | !transmute | Spend points to transmute item |
| 14 | Bee | !bee | Spend points to summon allied bee |
| 15 | Ward | !ward | Spend points to summon ward |
| 16 | Random Buff | !buff | Spend points to apply random buff |
| 17 | Random Debuff | !debuff | Spend points to apply random debuff |
| 18 | Cursed Wand | !wand | Spend points for cursed wand effect |
| 19 | Double Points | !doublepoints | Streamer only: 2× points for N min |
| 20 | Earn Points (Cheer) | Twitch Cheer | 1 pt per bit |
| 21 | Earn Points (Super Chat) | YouTube Super Chat | 1 pt per $0.01 |
| 22 | Reset Points | Stream Started | Clear non-donor points |
| 23 | Spend OFF | Hotkey | Disable spend commands |
| 24 | Spend ON | Hotkey | Enable spend commands |
| 25 | Helpers/Hurters OFF | Hotkey | Disable helpers vs hurters |
| 26 | Helpers/Hurters ON | Hotkey | Enable helpers vs hurters |
| 27 | !myside | !myside | Remind user their side (free) |
| 28 | !switch | !switch | Switch helper/hurter (cost configurable) |
| 29 | !heal | !heal | Helper: heal hero ~15% HP |
| 30 | !cleanse | !cleanse | Helper: remove one debuff |
| 31 | !dew | !dew | Helper: drop dewdrop |
| 32 | !hex | !hex | Hurter: apply Hex debuff |
| 33 | !degrade | !degrade | Hurter: apply Degrade debuff |
| 34 | !sabotage | !sabotage | Hurter: remove one buff |
| 35 | !corruptally | !corruptally | Helper: summon corrupted ally from biome |

---

## Notes

- **Reset Points** runs when your stream goes live. Non-donors lose all points; donors (Super Chat / Cheer) keep their donation amount.
- **Passive earn** only adds to users already in the file—they must send at least one message first. Enable **Live Update** under Platform → Twitch → Settings for Present Viewers to work.
- **Message Received** fires on every chat message. Ensure the Earn action does not also fire on bot messages or your own messages if you want to exclude them (add a conditional if needed).
- For `!spawn`, the Command Triggered must pass `input1` (the text after the command). Use `%rawInput%` or `%input1%` depending on your Streamer.bot version.
- Edit `POINTS_PER_MESSAGE`, `POINTS_PER_TICK`, and `COOLDOWN_SEC` (in Earn Points on chat for chat, Earn Points passive for passive) in the C# code. Edit points costs via http://localhost:5000/points-config or `points_config.json`.
- **Double points** persists until the duration ends. To clear it when the stream starts, add `File.WriteAllText(DOUBLE_FILE, "0");` to the Reset Points action.
- **Top farder 2x:** The earn actions read from `TOP_FARDER_FILE` (default: `OBS files\textread\leader.txt`). Expected format: `Top Farder: USERNAME - 45`. Change the path if your fard counter writes to a different file.
- **Super Chat / Cheer:** Uses `points_command.py` (superchat/cheer subcommands). Currency conversion via Frankfurter (free, no key). Add these actions to the same blocking queue as earn/spend. Anonymous cheers are skipped.

---

## User-Facing Summary

- **[youtube-description.md](youtube-description.md)** — Full YouTube description (channel assets, stream commands, Discord, chat commands)
- **[user-facing-summary.md](user-facing-summary.md)** — Chat commands only (copy-paste block)
- **[twitch-panel.md](twitch-panel.md)** — Formatted version for Twitch panels
