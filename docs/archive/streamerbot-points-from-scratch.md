# Points System — Streamer.bot setup

> **Superseded (Phase 5):** The live setup is the **9-action HTTP gateway**, not the ~40-action model below.  
> **Use these instead:**
> - **Build / maintain:** [streamerbot-http-gateway-apply.md](../streamerbot-http-gateway-apply.md)
> - **Roadmap:** [streaming-system-rework-plan.md](../streaming-system-rework-plan.md)
> - **Quick setup:** [streaming-setup-guide.md](../streaming-setup-guide.md)
> - **Off-stream tests:** `Lastest UI/phase3_rapid_test.ps1`
> - **Import bot:** `Lastest UI/streamerbot/shatter-the-streamer-export-0.2.0.txt` (export from your live R1–R9 bot)

## Current model (9 actions)

| Action | Role |
|--------|------|
| R1 Chat Router | All chat commands → `POST /api/chat-command` |
| R2 First Words | OBS presentation only (+5 on server) |
| R3 Passive earn | Present viewers → HTTP passive earn |
| R4–R6 Donations | Cheer / Super Chat / gifts → `/api/donation/*` |
| R7 Session reset | Twitch Stream Started → `/api/session/reset` |
| R8 Spend Toggle | `spend_disabled.txt` toggle (Stream Deck Action Switch) |
| R9 Presentation | `!fard` OBS + sound (queued from R1) |
| R10 Stream end | Stream Offline → `POST /api/session/end` (debounced chat wipe + auto-bank) |

**Economy v1.1 (server):** 2 pt/msg, 20s CD, 500 chat cap, `!bank` (10%), donor wallet, donation **4×** cap, stream-end auto-bank (5% / 10% members). Spec: [Chat Command Economy v1.md](../Chat%20Command%20Economy%20v1.md). Apply: [economy-v11-apply.md](../economy-v11-apply.md).

**No** per-command Streamer.bot actions. **No** `spawn_result.txt` / `donation_result.txt` in the live path.  
C# snippets: `Lastest UI/streamerbot/phase2/`.

**Fard:** Server returns `message` + `presentation` → R1 chat reply + R9 OBS/sound. See [fard-system.md](../fard-system.md).  
**Summon march:** `!summon` handled in R1; Godot overlay polls `/api/summon-march`. See [summon-march-system.md](../summon-march-system.md).

---

## Archived: ~40-action walkthrough (pre–HTTP gateway)

The content below (~3800 lines) documents the **old** Run Program + `spawn_result.txt` + per-command C# parsers.  
**Do not build new setups from it.** Index: [README.md](README.md).

---

# Points System (From Scratch) — legacy

A simple file-based points system for Streamer.bot—no extensions. Viewers earn points by chatting and spend them to spawn monsters. Works with **Twitch and YouTube**.

**Current mod:** Helper/hurter roles, `!switch`, `!myside`, role discounts, and passive boss/death/floor role points are **removed**. Disable or delete Streamer.bot actions that toggle `helpers_hurters_disabled.txt`, assign roles, or run `!switch` / `!myside`. `!heal`, `!cleanse`, `!dew`, `!corruptally`, `!hex`, `!degrade`, and `!sabotage` are normal spend commands for everyone.

**`!wand` (unified):** One price — `cost_per_wand` in `points_config.json` / points-config UI (default **75**). No tier in chat: the **game** picks a valid cursed-wand effect with **weighted** rarity (~60% / ~30% / ~9% / ~1% for common → very rare, same as vanilla `CursedWand`). Streamer.bot should call `points_command.py wand %userName%` only (see [Action 18](#action-18-cursed-wand-effect-wand-with-points)).

---

## Prerequisites

Before implementing, ensure:
1. **Overlay server** running (`python server.py` in `Lastest UI`)
2. **Game** running with streaming enabled (WebSocket on port 5001, the default in Settings)
3. **Streamer.bot** connected to Twitch and/or YouTube
4. **Python** installed (for `points_command.py`)

---

## Streamer.bot checklist (changes you make in the bot)

Use this list when updating an **existing** setup. Game code fixes (corrupt `!ally` XP/loot, invalid `!gold 0`, cursed wand not costing a turn) need **no** Streamer.bot edits. **`!wand` layout changed:** if you still use `wand %rawInput% %userName%` for historic **tiers** (`common`, `veryrare`, etc.), switch to **`wand %userName%`** so bare `!wand` is not broken when `%rawInput%` is empty.

| What | Why | Where to look |
|------|-----|----------------|
| **`!wand` Run a Program** | Unified wand: **one** cost (`cost_per_wand`), weighted random effect in the game. | [Action 18](#action-18-cursed-wand-effect-wand-with-points) |
| **Cheer + Super Chat optional args** | So bits / Super Chat get the same **stacking 2×** as chat (!doublepoints, **top summoner**, sub/member). Without the extra args, only global double points + top summoner apply on donations. | [Action 20](#action-20-earn-points-cheer), [Action 21](#action-21-earn-points-super-chat), [argument reference](#cheer--super-chat--argument-reference). |
| **Top summoner 2× (chat earn)** | Session leader from `!summon` — handled on server via R1; see [summon-march-system.md](../summon-march-system.md). | Legacy C# in archived Actions 01–03 below. |
| **Gift sub / gift membership** | Not automatic — add [Action 40](#action-40-earn-points-gift-sub--gift-membership). | [Action 40](#action-40-earn-points-gift-sub--gift-membership) (after `!plant`). |
| **Super Chat / Cheer** | Donation points require Actions 20–21 on the points queue; see HTTP fallback if Run Program fails. | [Action 20](#action-20-earn-points-cheer), [Action 21](#action-21-earn-points-super-chat). |
| **Chat → donor by %** | Transfer part of chat-only points into donor points from the overlay. | Open **points-config** in the browser (`/points-config`): set **Chat→Donor %** next to the button, then **Chat → Donor**. Not a Streamer.bot change. |

**Files the Python script writes (for your paths):**

- `Lastest UI/spawn_result.txt` — result of spend commands (often deleted by the next C# step).
- `Lastest UI/top_summoner.txt` — session summon leader for **2× personal points** (written by `summon_march_post.py` from `!summon`).

---

## Implementation Order

**Action numbering matches Streamer.bot.** Keep actions in sync with this doc.

| # | Action | Trigger | Purpose |
|---|--------|---------|---------|
| 01 | Earn Points (on chat) | Message Received | +1 per message (30s cooldown; stacks 2×) |
| 02 | Earn Points (passive) | Present Viewers | +1 per tick for users already in file |
| 03 | First Words Bonus | (add to First Words) | +5 on first chat |
| 04 | Check Points | !points | Show viewer their balance |
| 05 | Top Points | !toppoints | Show top 3 point holders |
| 06 | Spawn Monster | !spawn | Spend points to spawn monster |
| 07 | Spawn Champion | !champion | Spend points to spawn champion (2× zone-adjusted spawn) |
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
| 18 | Cursed Wand Effect | !wand | Spend points; **one** price, **weighted** random cursed-wand effect (no viewer tier) |
| 19 | Double Points | !doublepoints | Streamer only: 2× points for N minutes |
| 20 | Earn Points (Cheer) | Twitch Cheer | 1 pt per bit; optional args for stacked 2× |
| 21 | Earn Points (Super Chat) | YouTube Super Chat | 1 pt per $0.01; same optional args |
| 22 | Reset Summon March | Stream Started | Clear top summoner + session summon counts |
| 23 | Spend OFF | Hotkey (Stream Deck OFF) | Disable spend commands |
| 24 | Spend ON | Hotkey (Stream Deck ON) | Enable spend commands |
| 25 | !heal | !heal | Heal hero ~15% HP |
| 26 | !cleanse | !cleanse | Remove one random debuff |
| 27 | !dew | !dew | Drop dewdrop near hero |
| 28 | !plant | !plant | Plant a random seed near hero (fails if Barren Land enabled) |
| 28 | !corruptally | !corruptally | Summon corrupted ally from current biome |
| 29 | !hex | !hex | Apply Hex debuff |
| 30 | !degrade | !degrade | Apply Degrade debuff |
| 31 | !sabotage | !sabotage | Remove one random buff |
| 36 | Bomb | !bomb | Spend points; weighted random lit bomb 1–4 tiles from hero |
| 37 | Ring of Wealth loot | !row | Spend points; RoW-style drops by chapter, always ≥1 item |
| 40 | Earn Points (Gift sub / membership) | Twitch Gift Sub / Gift Bomb; YouTube Gift Membership | Configurable per tier (`points_config.json`) |

---

## YouTube Support

- **Commands (!spawn, !champion, !gold, !curse, !gas, !scroll, !row, !trap, !bomb, !transmute, !bee, !ward, !buff, !debuff, !wand, !points, !toppoints, !corruptally, !heal, !cleanse, !dew, !plant, !hex, !degrade, !sabotage):** When creating the command, enable **both Twitch and YouTube** as sources so one action handles both platforms.
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
| !row | `%userName% triggered Ring of Wealth loot: %rowLoot%! You have %userPointsRemaining% points left.` |
| !trap | `%userName% placed a %trapName% nearby! You have %userPointsRemaining% points left.` |
| !bomb | `%userName% armed a %bombName% nearby! You have %userPointsRemaining% points left.` |
| !transmute | `%userName% transmuted an item into %transmuteItemName%! You have %userPointsRemaining% points left.` |
| !bee | `%userName% summoned a bee to help you! You have %userPointsRemaining% points left.` |
| !ward | `%userName% summoned a ward to help you! You have %userPointsRemaining% points left.` |
| !buff | `%userName% gave you %buffName%! You have %userPointsRemaining% points left.` |
| !debuff | `%userName% afflicted you with %debuffName%! You have %userPointsRemaining% points left.` |
| !wand | `%userName% triggered a cursed wand effect: %wandEffectName%! You have %userPointsRemaining% points left.` |
| !corruptally | `%userName% summoned a corrupted %allyName% to fight for you! You have %userPointsRemaining% points left.` |
| !heal | `%userName% healed you! You have %userPointsRemaining% points left.` |
| !cleanse | `%userName% cleansed %allyName%! You have %userPointsRemaining% points left.` |
| !dew | `%userName% dropped a dewdrop! You have %userPointsRemaining% points left.` |
| !hex | `%userName% hexed you! You have %userPointsRemaining% points left.` |
| !degrade | `%userName% degraded you! You have %userPointsRemaining% points left.` |
| !sabotage | `%userName% sabotaged %allyName%! You have %userPointsRemaining% points left.` |

*`!wand`:* Run a Program args must be `wand %userName%` (not `wand %rawInput% %userName%`). `%wandEffectName%` is the game’s effect id (Java class simple name).

*Removed (do not add to Streamer.bot):* `!myside` and `!switch` — no longer implemented in `points_command.py`.

---

## File Location

**Path quoting:** If your project path contains spaces (e.g. `My Games`), the script path in Run a Program **Arguments** must be in quotes: `"C:\...\points_command.py"`. Otherwise the command will fail.

Points are stored in (update the path in all C# code if your project is elsewhere):
```
C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\viewer_points.txt
```
Format: one line per user: `username|points|lastEarnTimestamp|donationPts|role` (5 columns). The optional `role` column is **legacy** (e.g. `helper` / `hurter`); the overlay keeps it for file compatibility but does not use it for discounts, passive payouts, or command gating. Legacy 4-column lines have no role field; 3-column lines are treated as donationPts=0.
The file is created automatically when the first action runs.

**Legacy:** `helper_hurter_counter.txt` was used for alternating role assignment on first chat; you can delete it and stop writing it from Streamer.bot if you are on the current mod only.

**Double points:** Stored in `double_points_end.txt` (Unix timestamp when 2x ends; `0` = off). Created when you first use `!doublepoints`.

**Top summoner:** Stored in `top_summoner.txt` (`Top Summoner: name - count`). Updated when someone uses `!summon`. The leader earns **2× personal points** on chat, passive, First Words, and donations (see [Top summoner 2×](#top-summoner-2-personal-points)).

---

## Top summoner 2× (personal points)

Replaces the removed **top farder** bonus. Whoever has the most successful `!summon` uses **this stream** is the top summoner and earns **2×** on personal point gains (stacks with !doublepoints and sub/member).

**Multiplier order** (each step doubles when active):

1. Global **!doublepoints** (and `!fard` extensions)
2. **Top summoner** (`top_summoner.txt`)
3. **Subscriber OR channel member**

Examples: sub + top summoner = **4×** base; all three = **8×** base.

| Earn path | Where 2× is applied |
|-----------|---------------------|
| Chat message (Action 01) | C# `IsTopSummoner()` |
| Passive tick (Action 02) | C# `IsTopSummoner()` |
| First Words (Action 03) | C# `IsTopSummoner()` |
| Cheer / Super Chat / gift (Actions 20, 21, 40) | `points_command.py` reads `top_summoner.txt` automatically — **no extra Streamer.bot args** |

**Setup:** [streamerbot-summon-march-apply.md](streamerbot-summon-march-apply.md) (`!summon`, `!topsummoner`, stream reset).

### What to change if your Earn Points C# predates summon march

In **Earn Points (chat)**, **Earn Points (passive)**, and **First Words** C#:

1. Add next to your other `const` paths:

```csharp
const string TOP_SUMMONER_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\top_summoner.txt";
```

2. Replace your multiplier line with:

```csharp
int mult = (IsDoublePointsActive(unixNow) ? 2 : 1)
         * (IsTopSummoner(key) ? 2 : 1)
         * (IsSubscriberOrMember() ? 2 : 1);
```

(First Words uses `user.ToLowerInvariant()` instead of `key` where applicable.)

3. Add **before** `IsSubscriberOrMember()`:

```csharp
bool IsTopSummoner(string userKey)
{
    try
    {
        if (string.IsNullOrEmpty(userKey) || !File.Exists(TOP_SUMMONER_FILE)) return false;
        string line = File.ReadAllText(TOP_SUMMONER_FILE).Trim();
        const string prefix = "Top Summoner: ";
        if (!line.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return false;
        string rest = line.Substring(prefix.Length);
        int dash = rest.LastIndexOf(" - ");
        string leader = dash >= 0 ? rest.Substring(0, dash).Trim() : rest.Trim();
        return leader.Equals(userKey, StringComparison.OrdinalIgnoreCase);
    }
    catch { return false; }
}
```

4. **Action 22** on Stream Started runs `"summon_march_post.py" reset` so the leaderboard clears each stream (see below). Points reset is manual / optional.

**Donations:** If `points_command.py` is up to date, cheer/Super Chat/gift already honor top summoner — no Run Program argument changes.

---

## Configuration (edit in the C# code)

- **Points per message:** `1` (change `POINTS_PER_MESSAGE` in Earn Points on chat)
- **Points per passive tick:** `1` (change `POINTS_PER_TICK` in Earn Points passive)
- **Bot exclusion:** `daltongoesslow` never earns points (change `BOT_USER` in Earn Points on chat and passive)
- **Chat cooldown:** `30` seconds (change `COOLDOWN_SEC` in Earn Points on chat; `0` = no cooldown)
- **Passive cooldown:** `60` seconds (change `COOLDOWN_SEC` in Earn Points passive; shares `lastEarn` with chat)
- **Points costs:** Edit `points_config.json` or open **http://localhost:5000** in your browser (main control page; overlay server must be running). The overlay also has **Delete all points** (clears non-donor only; donors keep donation) and **Delete all donor points** (full wipe).
- **Donation rate:** 1 point per $0.01 (Super Chat uses Frankfurter API for conversion; not in points config)
- **Fard extends 2×:** Viewers use `!fard` once per stream to add **+3 min** (**+6 min** for subs/members) to global 2×. See [fard-system.md](../fard-system.md) and R1/R9 in [streamerbot-http-gateway-apply.md](../streamerbot-http-gateway-apply.md).
- **Top summoner 2×:** Session `!summon` leader earns 2× on personal points (chat, passive, First Words, donations). See [Top summoner 2×](#top-summoner-2-personal-points).
- **Subscriber / member 2×:** Twitch subscribers and YouTube channel members earn 2× points. Uses Streamer.bot variables `isSubscribed` (Twitch) and `userIsSponsor` (YouTube). Stacks with double points and top summoner (e.g. **8×** when all three apply).

---

## Action 01: Earn Points (on chat message)

**Trigger:** Message Received (Twitch → Triggers → Message Received)

**Sub-Action:** Execute C# Code (Inline)

**Multipliers:** `POINTS_PER_MESSAGE` × !doublepoints × **top summoner** × sub/member (see [Top summoner 2×](#top-summoner-2-personal-points)).

Uses plain-text format (no JSON) to avoid System.Core dependency. **All code that reads or writes `viewer_points.txt` must use the same lock file** (`viewer_points.txt.lock`) so Earn Points, Passive earn, !points, !toppoints, First Words, Reset, the overlay server, and Python (superchat, spawn, etc.) don't overwrite each other. Each C# block below opens the **persistent** lock file and uses `FileStream.Lock` (matches Python `msvcrt` / server `fcntl`). Do **not** use `FileMode.CreateNew` + delete — that waits 10s whenever the lock file exists.

**Skip chat commands:** Message Received also fires on `!spawn`, `!scroll`, etc. Those lines have their own Command actions on the same **blocking queue**. If this action runs first, it can sit in `AcquirePointsLock()` for up to 10 seconds before spend commands start (multi-second chat delay). The C# below returns immediately when the message starts with `!`, **before** acquiring the lock. Optional alternative: add a trigger filter on this action (Message does not start with `!`) instead of the C# check.

**Paste the entire block below** into Streamer.bot Action 01 (replace all existing C#). After **Save and Compile**, search your live code for `CreateNew` — if it still appears, the old lock is still in place and **earn will silently fail** whenever `viewer_points.txt.lock` exists on disk. The working version uses `OpenOrCreate`, `_pointsLockStream`, and `FileStream.Lock(0, 1)`.

**Earn fires but no points?** In Action History, a `return false` with no error usually means: (1) old `CreateNew` lock — `AcquirePointsLock()` failed after 10s; (2) **30s cooldown** — same user chatted within `COOLDOWN_SEC`; (3) message starts with `!`; (4) user is `BOT_USER`. A successful earn shows the C# sub-action completing with return **true**.

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
    const string TOP_SUMMONER_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\top_summoner.txt";
    const int POINTS_PER_MESSAGE = 1;
    const int COOLDOWN_SEC = 30;  // 0 = no cooldown

    const string BOT_USER = "daltongoesslow";  // Bot never earns points (case-insensitive)

    public bool Execute()
    {
        string user = CPH.TryGetArg("userName", out string u) ? u : null;
        if (string.IsNullOrEmpty(user)) return false;
        if (user.Equals(BOT_USER, StringComparison.OrdinalIgnoreCase)) return false;

        // Skip chat commands (!spawn, !scroll, etc.) — they have their own actions on the blocking queue.
        string chatMsg = CPH.TryGetArg("message", out string cm) ? cm : null;
        if (string.IsNullOrEmpty(chatMsg)) CPH.TryGetArg("rawMessage", out chatMsg);
        if (!string.IsNullOrEmpty(chatMsg) && chatMsg.TrimStart().StartsWith("!"))
            return false;

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

                int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopSummoner(key) ? 2 : 1) * (IsSubscriberOrMember() ? 2 : 1);
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

    FileStream _pointsLockStream;

    bool AcquirePointsLock()
    {
        for (int i = 0; i < 200; i++)  // 10 sec at 50ms
        {
            try
            {
                _pointsLockStream = new FileStream(LOCK_FILE, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
                _pointsLockStream.Lock(0, 1);
                return true;
            }
            catch (IOException)
            {
                try { _pointsLockStream?.Dispose(); } catch { }
                _pointsLockStream = null;
                Thread.Sleep(50);
            }
        }
        return false;
    }

    void ReleasePointsLock()
    {
        try
        {
            if (_pointsLockStream != null)
            {
                _pointsLockStream.Unlock(0, 1);
                _pointsLockStream.Dispose();
                _pointsLockStream = null;
            }
        }
        catch { }
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

    // Twitch: isSubscribed. YouTube: userIsSponsor (channel member). Stacks with double points and top summoner.
    bool IsTopSummoner(string userKey)
    {
        try
        {
            if (string.IsNullOrEmpty(userKey) || !File.Exists(TOP_SUMMONER_FILE)) return false;
            string line = File.ReadAllText(TOP_SUMMONER_FILE).Trim();
            const string prefix = "Top Summoner: ";
            if (!line.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return false;
            string rest = line.Substring(prefix.Length);
            int dash = rest.LastIndexOf(" - ");
            string leader = dash >= 0 ? rest.Substring(0, dash).Trim() : rest.Trim();
            return leader.Equals(userKey, StringComparison.OrdinalIgnoreCase);
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

**Multipliers:** Same stacking as Action 01 (!doublepoints × **top summoner** × sub/member).

Adds points over time to viewers who are already in the file (have chatted at least once). Enable **Live Update** under Platform → Twitch → Settings and set the interval (e.g. 5 minutes).

**Note:** The C# tries both `userName` and `presentUserName`. If Present Viewers uses a list, add **Add Present User** (index 0, 1, 2, …) in a loop before the C# so each viewer gets processed. Subscriber/member 2x applies when Streamer.bot provides `isSubscribed` or `userIsSponsor` for that viewer (Message Received does; Present Viewers may not, depending on your setup).

**Paste the entire block below** and search live code for `CreateNew` after compile — same lock fix as Action 01. Passive earn only adds to viewers **already in** `viewer_points.txt` (must chat once first). Shares `lastEarn` / **60s cooldown** with Action 01 on the same row.

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
    const string TOP_SUMMONER_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\top_summoner.txt";
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

                int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopSummoner(key) ? 2 : 1) * (IsSubscriberOrMember() ? 2 : 1);
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

    FileStream _pointsLockStream;

    bool AcquirePointsLock()
    {
        for (int i = 0; i < 200; i++)  // 10 sec at 50ms
        {
            try
            {
                _pointsLockStream = new FileStream(LOCK_FILE, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
                _pointsLockStream.Lock(0, 1);
                return true;
            }
            catch (IOException)
            {
                try { _pointsLockStream?.Dispose(); } catch { }
                _pointsLockStream = null;
                Thread.Sleep(50);
            }
        }
        return false;
    }

    void ReleasePointsLock()
    {
        try
        {
            if (_pointsLockStream != null)
            {
                _pointsLockStream.Unlock(0, 1);
                _pointsLockStream.Dispose();
                _pointsLockStream = null;
            }
        }
        catch { }
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

    bool IsTopSummoner(string userKey)
    {
        try
        {
            if (string.IsNullOrEmpty(userKey) || !File.Exists(TOP_SUMMONER_FILE)) return false;
            string line = File.ReadAllText(TOP_SUMMONER_FILE).Trim();
            const string prefix = "Top Summoner: ";
            if (!line.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return false;
            string rest = line.Substring(prefix.Length);
            int dash = rest.LastIndexOf(" - ");
            string leader = dash >= 0 ? rest.Substring(0, dash).Trim() : rest.Trim();
            return leader.Equals(userKey, StringComparison.OrdinalIgnoreCase);
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

Add the points C# as a **sub-action inside your existing First Words action** (OBS overlay, sound, etc.). Gives bonus points the first time a user chats (`FIRST_WORDS_BONUS`, default 5 in doc — you may use e.g. 50).

### First Words overlay feels delayed?

Two common causes:

1. **Blocking points queue** — If the whole First Words action is on the same **blocking queue** as `!spawn` / earn, the **entire** action (OBS + sound + C#) waits until everything ahead of it finishes. **Fix:** move First Words to the **Default** queue (or a dedicated **presentation** queue), **not** the spend/earn blocking queue. File locking still keeps points safe without Streamer.bot queue serialization.

2. **Sub-action order** — If **Execute C# Code (points)** is **first**, OBS/sound do not run until the lock is acquired and `viewer_points.txt` is read/written. **Fix:** run **OBS text → show group → play sound first**, then **Execute C# Code**, then your hide delay. Example order:

   1. OBS GDI Text (set name)
   2. OBS Source Visibility (show)
   3. Play sound
   4. **Execute C# Code** (points bonus) ← after overlay is visible
   5. Delay (e.g. 7000 ms)
   6. OBS Source Visibility (hide)

   Points may grant a moment after the overlay appears; that is fine.

**Optional split:** Two actions on the same First Words trigger — **(A)** OBS/sound on Default queue; **(B)** points C# only on Default queue or points queue. Presentation never waits on spawns.

**Paste the entire C# block below** and search live code for `CreateNew` after compile — same lock fix as Action 01. First Words runs on a user's **first chat** of the session; if Action 01 already ran on that message, both can fire (chat earn + first-words bonus).

**Multipliers:** Same stacking as Action 01 (!doublepoints × **top summoner** × sub/member).

**Sub-Action:** Execute C# Code (Inline) — place **after** OBS show + sound (see order above), not before.

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
    const string TOP_SUMMONER_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\top_summoner.txt";
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
                int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsTopSummoner(key) ? 2 : 1) * (IsSubscriberOrMember() ? 2 : 1);
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

    FileStream _pointsLockStream;

    bool AcquirePointsLock()
    {
        for (int i = 0; i < 200; i++)  // 10 sec at 50ms
        {
            try
            {
                _pointsLockStream = new FileStream(LOCK_FILE, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
                _pointsLockStream.Lock(0, 1);
                return true;
            }
            catch (IOException)
            {
                try { _pointsLockStream?.Dispose(); } catch { }
                _pointsLockStream = null;
                Thread.Sleep(50);
            }
        }
        return false;
    }

    void ReleasePointsLock()
    {
        try
        {
            if (_pointsLockStream != null)
            {
                _pointsLockStream.Unlock(0, 1);
                _pointsLockStream.Dispose();
                _pointsLockStream = null;
            }
        }
        catch { }
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

    bool IsTopSummoner(string userKey)
    {
        try
        {
            if (string.IsNullOrEmpty(userKey) || !File.Exists(TOP_SUMMONER_FILE)) return false;
            string line = File.ReadAllText(TOP_SUMMONER_FILE).Trim();
            const string prefix = "Top Summoner: ";
            if (!line.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return false;
            string rest = line.Substring(prefix.Length);
            int dash = rest.LastIndexOf(" - ");
            string leader = dash >= 0 ? rest.Substring(0, dash).Trim() : rest.Trim();
            return leader.Equals(userKey, StringComparison.OrdinalIgnoreCase);
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

**Queue:** Do **not** add this action to the same **blocking queue** as spend commands (`!spawn`, `!scroll`, etc.). Putting `!points` on the spend queue makes it wait behind every queued spawn during promos. Use the default queue or a separate **fast reads** queue.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Program:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" balance %userName%`
   - **Working directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait for Exit:** `5` seconds (**required** — Streamer.bot label may be **Wait maximum** or **Wait for Exit**. If this is `0`, the next step runs before Python finishes and `%userPoints%` stays `0`.)

2. **Execute C# Code** (Inline) — reads **`%output0%`** from step 1 (Python prints `ok|123`). File read is fallback only:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_balance_result.txt";

    public bool Execute()
    {
        string userPoints = "0";
        string line = null;

        if (CPH.TryGetArg("output0", out string stdout) && !string.IsNullOrWhiteSpace(stdout))
            line = stdout.Trim();
        else if (File.Exists(FILE))
            line = File.ReadAllText(FILE).Trim();

        if (!string.IsNullOrEmpty(line) && line.StartsWith("ok|", StringComparison.OrdinalIgnoreCase))
        {
            string[] parts = line.Split('|');
            if (parts.Length >= 2 && !string.IsNullOrWhiteSpace(parts[1]))
                userPoints = parts[1].Trim();
        }

        CPH.SetArgument("userPoints", userPoints);
        try { if (File.Exists(FILE)) File.Delete(FILE); } catch { }
        return true;
    }
}
```

**Sub-action order (all three required):** (1) Run Program → (2) C# above → (3) chat message below. Do not skip step 2.

**If everyone still shows 0:** In Streamer.bot **Action History** → inspect the `!points` run → check `output0` (should be `ok|123`) and `userName`. If `userName` is empty on YouTube, change Arguments to use `%user%` instead of `%userName%` for that platform.

**Test manually:** `python "Lastest UI\points_command.py" balance YourUsername` → prints `ok|123` and writes the same to `points_balance_result.txt`.

<details>
<summary>Alternative: single C# step with file lock (no Run Program)</summary>

Use only if you cannot add Run Program. Must use `OpenOrCreate` + `FileStream.Lock` — **not** `CreateNew` + delete (10s stall whenever `viewer_points.txt.lock` exists). Copy `AcquirePointsLock` / `ReleasePointsLock` / `ReadAll` from [Action 05](#action-05-top-points-toppoints); set `%userPoints%` from the user's row (same `(p + d)` rule as Action 04 had previously).

</details>

3. **Response message:** Use commandSource pattern:
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

    FileStream _pointsLockStream;

    bool AcquirePointsLock()
    {
        for (int i = 0; i < 200; i++)  // 10 sec at 50ms
        {
            try
            {
                _pointsLockStream = new FileStream(LOCK_FILE, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
                _pointsLockStream.Lock(0, 1);
                return true;
            }
            catch (IOException)
            {
                try { _pointsLockStream?.Dispose(); } catch { }
                _pointsLockStream = null;
                Thread.Sleep(50);
            }
        }
        return false;
    }

    void ReleasePointsLock()
    {
        try
        {
            if (_pointsLockStream != null)
            {
                _pointsLockStream.Unlock(0, 1);
                _pointsLockStream.Dispose();
                _pointsLockStream = null;
            }
        }
        catch { }
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

**Zone-adjusted spawn cost:** When **deeper** than the mob’s native depth, cost is **half** the table base (cheap sewer mobs deep in the run). When **shallower** than native — spawning later-chapter mobs in earlier areas — `_early_spawn_multiplier` compares your **chapter** (five 5-floor blocks: sewers → prison → caves → city → halls via `_dungeon_region`) to the mob’s home chapter and picks **tier** 1/2/3. Cost is **base × (1 + 0.20 × (tier − 1))** → **100% / 120% / 140%** (rounded, min 1). One edge case bumps tier while still in the **caves** chapter (see function body). If depth cannot be read from the overlay, the script uses **full table base** (no discount or surcharge).

**Troubleshooting (!spawn does nothing):**
- **Monster name not passed:** Change `%input1%` to `%rawInput%` in the Arguments (Streamer.bot versions differ).
- **No points:** Chat to earn points.
- **Overlay server not running:** Start `python server.py` in `Lastest UI`.
- **Game not connected:** Game must be running with streaming enabled (port 5001). Server console shows "Game WebSocket: waiting for game..." when disconnected.
- **Test manually:** Run `python "Lastest UI\points_command.py" spawn rat YourUsername` in a terminal—should write `ok|{pts}` to `spawn_result.txt` or an error.

---

## Action 07: Spawn Champion (!champion, with points)

**Trigger:** Command Triggered → `!champion` (enable **both Twitch and YouTube** as sources)

**Usage:** `!champion <monster>` (e.g. `!champion rat`, `!champion eye`). Spawns a **champion** version of the specified monster (random type: Blazing, Projecting, Antimagic, Giant, Blessed, or Growing). Same valid monsters as `!spawn`. **Cost:** **2×** the zone-adjusted `!spawn` cost (half-price deep spawns and chapter-gap surcharges when shallower both apply).

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

**Cost:** 2× whatever `!spawn` would charge at the current depth (examples vary: e.g. deep `!spawn rat` can be cheap, so champion rat is cheap too; early `!spawn eye` pays the chapter-gap bump on base, so champion eye scales the same way).

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

**Usage:** `!bee` — summons an **allied bee** next to the hero for 150 turns. The bee fights for the player like the one from Elixir of Honeyed Healing. Cost 75 pts (configurable).

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

**Trigger:** Command Triggered → `!wand` (enable **both Twitch and YouTube** as sources). Prefer a command **with no required arguments** after `!wand` (viewers type just `!wand`).

**Usage:** `!wand` — one **fixed** price (`cost_per_wand`, default **75**); the game rolls a **valid** cursed wand effect with **weighted** rarity (same as vanilla cursed wand: ~60% common, ~30% uncommon, ~9% rare, ~1% very rare — see `CursedWand` in game sources). Viewers do **not** pick a tier. The game still reports a `rarity` index in WebSocket results; **points deducted** are always `cost_per_wand`, not by rolled tier.

Excludes: AbortRetryFail, Explosion, FireBall, ForestFire.

**Streamer.bot:** Use **`wand %userName%`** only (no `%rawInput%`). **Migrating:** If you used `wand %rawInput% %userName%`, remove `%rawInput%` so empty chat does not become a bogus first CLI argument. **Legacy:** `wand common %userName%` (or any tier) still works — the tier word is ignored and `%userName%` is used as the spender.

**`points_config.json`:** Use **`cost_per_wand`** only; remove obsolete keys `cost_per_wand_common`, `cost_per_wand_uncommon`, `cost_per_wand_rare`, `cost_per_wand_veryrare` when you next save from the points-config UI.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python` (or full path to `python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" wand %userName%`
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

**Cost:** Single **`cost_per_wand`** (default 75). Edit via points config.

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, and earn actions.

**Fails when:** Not in an active run, hero dead, or no valid target cell (need visible tiles 2–6 from hero).

**Troubleshooting:** Same as scroll — quotations on script path, Wait maximum 10 seconds, overlay running, game connected. **`!wand` fails or wrong user:** Confirm Arguments are **`wand %userName%`** only. **Free / cost UI:** Timed “free” uses the key **`cost_per_wand`** (not the old per-tier keys).

**Test manually:** `python points_command.py wand YourUsername`

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

**OBS countdown timer:** Add a **Browser source** → URL = `http://127.0.0.1:5000/double-points-countdown`. Shows gold `2x points: N min` when active (minutes only, triple-digit safe), hides when inactive. Requires the overlay server running on port 5000.

**If it doesn't update after restarting OBS:** The page now uses no-cache headers and auto-reloads after 5 failed fetches (e.g. if the server wasn't ready). Also enable **"Refresh browser when scene becomes active"** in the Browser Source properties — it refreshes when you switch to that scene.

---

## Action 20: Earn Points (Cheer)

**Not automatic** until this action exists, is enabled, and is on your **points queue**. Same for [Action 21](#action-21-earn-points-super-chat) (Super Chat) and [Action 40](#action-40-earn-points-gift-sub--gift-membership) (gift subs).

**Rate:** 100 bits = $1 = 100 points base. **Stacks** with !doublepoints, **top summoner**, and subscriber/member 2× when you pass the optional CLI args below (top summoner is automatic via `top_summoner.txt`).

**Add this action to your points queue** (same blocking queue as earn/spend) to avoid race conditions.

**Trigger:** Twitch → Triggers → **Cheer**

**Sub-Actions (recommended order):**

1. **Run a Program**
   - **Program:** `python` (or full path to `python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" cheer %bits% %userName% %isSubscribed% %userIsSponsor%`
   - **Working directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI` (literal path — see [portable setup](streamerbot-global-paths-example-scroll.md#portable-setup-recommended-junction--fixed-path--global))
   - **Wait maximum:** `15` seconds

2. **Optional — thank-you chat:** Execute C# Code reads `donation_result.txt` in `Lastest UI` (same folder as the script). Format: `ok|150` = 150 points earned; `skip|0` = anonymous cheer skipped.

**Notes:** Anonymous cheers are skipped (no username). Omit optional args entirely to default sub/member flags off (global !doublepoints still applies).

### Cheer — HTTP alternative (if Run Program fails)

Overlay server must be running (`python server.py` in `Lastest UI`).

**Sub-Action:** Web Request → POST `http://127.0.0.1:5000/api/donation/cheer`  
**Content-Type:** `application/json`  
**Body:**

```json
{
  "bits": "%bits%",
  "username": "%userName%",
  "isSubscribed": "%isSubscribed%",
  "userIsSponsor": "%userIsSponsor%"
}
```

Response `{"ok":true,"pointsAdded":100}` on success.

### Cheer — what to change if you already had this action

1. Open your **Cheer** action → **Run Program** sub-action.
2. Replace the **Arguments** field with the line above (ending with `%isSubscribed% %userIsSponsor%`).
3. Confirm **Working directory** is your real `Lastest UI` folder (not empty, not `%variables%`).
4. **Save.** Subscriber 2× applies when Twitch sets `isSubscribed`; `userIsSponsor` is usually unused on Twitch (safe to pass).

---

## Action 21: Earn Points (Super Chat)

**Not automatic** until this action exists, is enabled, and is on your **points queue**.

**Rate:** 1 point per $0.01 USD base. Uses the [Frankfurter API](https://www.frankfurter.app/) for currency conversion (no API key). **Stacks** with !doublepoints, **top summoner**, and subscriber/member 2× when you pass optional trailing args (see [argument reference](#cheer--super-chat--argument-reference) below; top summoner is automatic via `top_summoner.txt`).

**Add this action to your points queue** (same blocking queue as earn/spend).

### YouTube Super Chat (setup from scratch)

1. **Create a new action** (e.g. **40 Super Chat Points** — name is up to you; action **number** in this doc is **21**).
2. **Add trigger:** YouTube → Triggers → **Super Chat**.
3. **Add sub-action:** Run a Program
   - **Program:** `python` (or full path to `python.exe`, e.g. `C:\Python313\python.exe`)
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" superchat %microAmount% %currencyCode% %userName% %isSubscribed% %userIsSponsor%`
   - **Working directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `15` seconds (Frankfurter currency lookup can take a few seconds)
4. **Add this action to your points queue** (same queue as earn/spend and Action 20).

**Streamer.bot variables:**

| Variable | Example | Notes |
|----------|---------|--------|
| `%microAmount%` | `1000000` | Micro-units: **1_000_000 = $1**. Do **not** use dollar display fields here unless you remove `%microAmount%` (script also accepts `1.50` or `5` as dollars). |
| `%currencyCode%` | `USD` | ISO code |
| `%userName%` | login | If empty on live events, use `%user%` (display name) as the 3rd argument after currency |
| `%isSubscribed%` | `true` / `false` | Optional 2× (Twitch-style; often empty on YouTube) |
| `%userIsSponsor%` | `true` / `false` | YouTube **channel member** 2× |

**Optional thank-you:** C# step reads `donation_result.txt` → `ok|150` means 150 points earned. Example message: `Thanks for the Super Chat! You earned %pointsEarned% points!` (parse the number after `|`).

### Super Chat — HTTP alternative (if Run Program fails)

**Sub-Action:** Web Request → POST `http://127.0.0.1:5000/api/donation/superchat`  
**Content-Type:** `application/json`  
**Body:**

```json
{
  "microAmount": "%microAmount%",
  "currencyCode": "%currencyCode%",
  "username": "%userName%",
  "isSubscribed": "%isSubscribed%",
  "userIsSponsor": "%userIsSponsor%"
}
```

If `%userName%` is empty on live Super Chats, use `"username": "%user%"` instead.

### Super Chat — what to change if you already had this action

1. Open your **Super Chat** action → **Run Program**.
2. Replace **Arguments** with the line in step 3 above (**must** include `%isSubscribed% %userIsSponsor%` at the end).
3. Set **Working directory** to your `Lastest UI` folder (literal path).
4. On **YouTube**, member status is usually **`%userIsSponsor%`**, not `%isSubscribed%`.
5. **Save** and test with a real Super Chat or `python points_command.py superchat 1000000 USD YourName` from `Lastest UI`.

#### Troubleshooting: test trigger works, real Super Chats don't

| Symptom | Fix |
|---------|-----|
| No points, no error | Action not on **points queue**, or wrong **working directory** |
| `ok\|0` in `donation_result.txt` | Amount parsed as $0 — check `%microAmount%` (use micro-units, not `$1.00` display text unless script gets decimals) |
| Wrong user credited | `%userName%` empty → use `%user%` |
| Script never runs | Try [HTTP alternative](#super-chat--http-alternative-if-run-program-fails) above |
| YouTube not firing | Reconnect YouTube in Streamer.bot → Settings → Platforms |
| Debug args | Create empty `Lastest UI/superchat_debug.txt`; next Super Chat logs to `superchat_debug.log` |

**Local test (no Streamer.bot):**

```text
cd Lastest UI
python points_command.py superchat 1000000 USD YourName 0 0
type donation_result.txt
```

Expect `ok|100` for a $1 Super Chat.

---

## Cheer / Super Chat — argument reference

`points_command.py` applies multipliers in this **order** (each step doubles when active): global **!doublepoints** → **top summoner** (`top_summoner.txt`, from `!summon`) → **subscriber OR channel member**.

| Position | Meaning | Typical Streamer.bot value |
|----------|---------|----------------------------|
| **Cheer:** 3rd arg after `cheer` | `isSubscribed` | `%isSubscribed%` (Twitch) |
| **Cheer:** 4th | `userIsSponsor` | `%userIsSponsor%` (often unused on Twitch; safe to pass) |
| **Super Chat:** 4th arg after `superchat` | `isSubscribed` | `%isSubscribed%` |
| **Super Chat:** 5th | `userIsSponsor` | `%userIsSponsor%` (YouTube member) |

**Cheer** full argument order: `cheer <bits> <username> [isSubscribed] [userIsSponsor]`.

**Super Chat** full argument order: `superchat <microAmount> <currencyCode> <username> [isSubscribed] [userIsSponsor]`.

- If you **omit** the optional sub/member args entirely, **global double points** (when `!doublepoints` is active) and **top summoner** still multiply donation points; sub/member do not.
- Empty or unknown strings for flags are treated as **off** (same as `0`).
- Adjust the **file paths** in all **Arguments** and C# `const` strings if your `Lastest UI` folder is not under the path used in this doc.

**Donation commands (CLI):** `cheer`, `superchat`, `giftmembership` — see [Action 20](#action-20-earn-points-cheer), [Action 21](#action-21-earn-points-super-chat), [Action 40](#action-40-earn-points-gift-sub--gift-membership). HTTP: `/api/donation/cheer`, `/api/donation/superchat`, `/api/donation/gift-membership`.

---

## Action 22: Reset Summon March (every stream)

**Trigger:** Stream Started (Twitch → Triggers → Stream Started). For YouTube, use the equivalent "Stream Online" or "Stream Started" trigger.

Clears **summon session data only** — not viewer points. Use this if you reset points manually (overlay **Delete all points** or your own workflow).

**Sub-Actions (single step):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"summon_march_post.py" reset`
   - **Working directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI` (literal path — same as other `Lastest UI` actions)
   - **Wait maximum:** `2` seconds

Deletes/resets: `top_summoner.txt`, `totalsummons.txt`, `summon_session_counts.json`, and any stale `summon_result.txt`. Godot march queue is unchanged (server keeps its own queue).

**Test:** Run the action once → `!topsummoner` should report no summons until someone uses `!summon` again.

See also: [streamerbot-summon-march-apply.md](streamerbot-summon-march-apply.md).

<details>
<summary>Optional: auto-reset chat points on stream start (legacy Action 22)</summary>

If you want **non-donor points cleared automatically** when you go live (instead of manual reset), add a **second sub-action** before or after the summon reset, or use a separate action:

**Execute C# Code (Inline)** — clears non-donor chat points; donors (Super Chat / Cheer) keep donation amount:

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

    FileStream _pointsLockStream;

    bool AcquirePointsLock()
    {
        for (int i = 0; i < 200; i++)  // 10 sec at 50ms
        {
            try
            {
                _pointsLockStream = new FileStream(LOCK_FILE, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
                _pointsLockStream.Lock(0, 1);
                return true;
            }
            catch (IOException)
            {
                try { _pointsLockStream?.Dispose(); } catch { }
                _pointsLockStream = null;
                Thread.Sleep(50);
            }
        }
        return false;
    }

    void ReleasePointsLock()
    {
        try
        {
            if (_pointsLockStream != null)
            {
                _pointsLockStream.Unlock(0, 1);
                _pointsLockStream.Dispose();
                _pointsLockStream = null;
            }
        }
        catch { }
    }
}
```

</details>

---

## Action 23: Spend OFF / Action 24: Spend ON (Stream Deck switch) — legacy

> **Live setup (R8):** Use **one** action — `R8 - Spend Toggle` with `SpendToggle.cs`. See [streamerbot-http-gateway-apply.md](../streamerbot-http-gateway-apply.md) Step 6. Assign the same action to **both** Toggle On and Toggle Off on the Streamer.bot Action Switch key; branch on `%state%` (`0` = spend off, `1` = spend on).

**Purpose (old two-action model):** Separate actions for Stream Deck switches — Spend ON (enable spending) vs Spend OFF (disable spending). Users can still earn points; they just can't use spend commands that check `spend_disabled.txt` while it exists. **All** such spends (including heal, hex, etc.) respect Spend OFF.

**Coverage:** Spend commands in `points_command.py` check for `spend_disabled.txt` and return "Spending is currently disabled by the streamer." when the file exists. If you add new spend commands to the script, add the same `is_spend_disabled()` check at the start of the handler.

<details>
<summary>Legacy SpendOff / SpendOn C# (pre–Spend Toggle)</summary>

### Spend OFF action

**Trigger:** Hotkey (Stream Deck switch → OFF state)

```csharp
File.WriteAllText(FILE, "1");
```

### Spend ON action

**Trigger:** Hotkey (Stream Deck switch → ON state)

```csharp
if (File.Exists(FILE)) File.Delete(FILE);
```

**Stream Deck setup (legacy):** Assign Spend ON to the ON state and Spend OFF to the OFF state.

</details>

---

## Action 25: Helpers/Hurters OFF / Action 26: Helpers/Hurters ON (Stream Deck switch)

**Historical — skip in current mod:** Helper/hurter toggles, passive role payouts, discounts, and `!switch` / `!myside` are removed. Do not create these actions unless you are maintaining an old fork.

**Purpose (legacy):** Same pattern as Spend OFF/ON. When Helpers/Hurters was OFF, the system treated everyone as "both" (no role-based point earning on boss/death, no discounts, `!switch` returned a disabled message). When ON, helpers vs hurters behavior applied.

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

**Historical — skip in current mod:** `!myside` was removed from `points_command.py`. Delete this action if you still have it.

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

**Historical — skip in current mod:** `!switch` was removed from `points_command.py`. Delete this action if you still have it.

**Trigger:** Command Triggered → `!switch`

**Usage:** `!switch` — switch from helper to hurter or vice versa. Cost configurable (default 50 pts). Requires Helpers vs Hurters ON.

### Remove trigger cooldown (important)

If **`!switch`** has a **Cooldown** set on the **command** (or trigger) in Streamer.bot, it runs on **every** invocation—including “not enough points” and other failures. Viewers then wait the full cooldown even though nothing happened.

1. Open the **`!switch`** command / action in Streamer.bot.
2. Find **Cooldown** (or equivalent) on the command or trigger and **disable it** or set it to **0** / none.
3. Apply cooldown **only on success** in the **True** branch below — either Streamer.bot’s cooldown sub-action (if your version has it) or **Execute C#** using `CPH.CommandAddToGlobalCooldown` / `CommandAddToUserCooldown` ([see below](#apply-cooldown-via-c-streamerbot-api)).

---

### Common mistake: wrong C# block

**Do not** use the **!spawn** / “Monster Spawner” Execute Code block on `!switch`. That code expects a different `spawn_result.txt` format and will not set `%newSide%` / `%userPointsRemaining%` correctly. It also will **not** set `%applySwitchCooldown%` unless you pasted the **small** `switch_side_last.txt` reader—copying the wrong block breaks the whole action.

If you use `%applySwitchCooldown%`, step 2 **must** be only the small C# that reads `switch_side_last.txt` (see optional layout below)—not spawn code.

---

### Recommended layout (3 sub-actions — simplest)

Cooldown only when the switch **actually succeeded**, without `%applySwitchCooldown%` or an extra `If` before chat.

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" switch %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds  

2. **Execute C# Code** — paste **only** the shared **!switch** C# block in the next section below (reads and deletes `spawn_result.txt`, sets `%spawnResult%`, `%newSide%`, `%userPointsRemaining%`).

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch (success):** In this order:
     1. **Apply cooldown** — see [Apply cooldown via C#](#apply-cooldown-via-c-streamerbot-api) below. Under **Commands →** you may only see **Get/Set Command State** (no separate “Set Command Cooldown” item); that is normal — use **Execute C# Code** with the official `CPH` methods there, **not** the !spawn / Monster Spawner script.
     2. Nested **commandSource** messages (same as before):
        - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% switched to %newSide%! You have %userPointsRemaining% points left.`
        - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% switched to %newSide%! You have %userPointsRemaining% points left.`
        - Leave **False Result** empty for both platform checks.
   - **False branch (failure):** Error messages only — **no** cooldown:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

### Apply cooldown via C# (Streamer.bot API)

Streamer.bot documents these on [CommandAddToGlobalCooldown](https://docs.streamer.bot/api/csharp/methods/core/commands/command-add-to-global-cooldown) and [CommandAddToUserCooldown](https://docs.streamer.bot/api/csharp/methods/core/commands/command-add-to-user-cooldown). **This is the intended way** to start a cooldown from an action when there is no “Set Command Cooldown” sub-action in your menu.

**Get the command ID:** In Streamer.bot, open **Core → Commands**, select your **`!switch`** command (the one tied to this action), and copy its **ID** / GUID from the command details (exact location varies by version; it may be under advanced/edit or right-click → copy id).

**Global cooldown** (nobody can use `!switch` for N seconds after a successful switch):

```csharp
using System;

public class CPHInline
{
    public bool Execute()
    {
        // Paste your !switch command’s GUID from Streamer.bot (Core → Commands)
        string commandId = "00000000-0000-0000-0000-000000000000";
        int seconds = 300; // 5 minutes
        CPH.CommandAddToGlobalCooldown(commandId, seconds);
        return true;
    }
}
```

**Per-user cooldown** (only the chatter who switched is blocked). If this “does nothing,” see **Troubleshooting: cooldown has no effect** below.

**Recommended: try user cooldown, fall back to global** so a failed `userId` / `userType` / `Platform` parse does not silently skip cooldown (`Enum.TryParse` is **case-sensitive** unless you pass `true` for ignore-case):

```csharp
using System;
using Streamer.bot.Plugin.Interface.Enums;

public class CPHInline
{
    public bool Execute()
    {
        string commandId = "00000000-0000-0000-0000-000000000000";
        int seconds = 300;

        bool hasUser = CPH.TryGetArg("userId", out string userId) && !string.IsNullOrEmpty(userId);
        bool hasType = CPH.TryGetArg("userType", out string userType) && !string.IsNullOrEmpty(userType);

        if (hasUser && hasType && Enum.TryParse(userType, true, out Platform platform))
        {
            CPH.CommandAddToUserCooldown(commandId, userId, platform, seconds);
            CPH.LogInfo("switch cooldown: user " + seconds + "s");
            return true;
        }

        CPH.LogInfo("switch cooldown: user args missing or bad userType; using global. Check Test Trigger for userId/userType.");
        CPH.CommandAddToGlobalCooldown(commandId, seconds);
        return true;
    }
}
```

**Troubleshooting: cooldown has no effect**

1. **User cooldown disabled on the command:** In **Core → Commands**, open your **`!switch`** command and set **User Cooldown** to a **non-zero** value (e.g. `1` second is enough). If user cooldown is **0 / off**, `CommandAddToUserCooldown` may run (your log will show the inline “cooldown” step) but **nothing blocks** the next chat use. Same idea for **Global Cooldown** if you use `CommandAddToGlobalCooldown` — enable global cooldown on that command with a non-zero value so the engine tracks timers.
2. **Wrong command ID:** `commandId` must be the **Command** GUID from **Core → Commands** (the Switch command row), **not** the Action ID from the action list. In logs, `Action 28: Switch Side` has action id `feb295f4-…` — that is **not** the id to paste into `CommandAddToGlobalCooldown` / `CommandAddToUserCooldown`.
3. **Testing as the broadcaster:** Streamer.bot **does not apply command cooldowns to the broadcaster account**. Test with a **mod account**, **alt**, or a **viewer** in chat.
4. **Silent failure (old snippet):** If `userId` / `userType` are empty or `Platform` fails to parse, use the **fallback** block above (global cooldown if user path fails).
5. **Variable names:** Run **Test Trigger** on this command and confirm arguments exist. If your build uses different names, adjust `TryGetArg` keys to match (e.g. `platform` vs `userType`).

**Reading your log:** Lines like `InlineCode :: Running … 'Switch Sides 3 step'` then `… 'cooldown'` mean the C# **did** run. If `!switch` still queues again seconds later anyway, Streamer.bot’s own cooldown gate may not be blocking (version/settings). **`points_command.py` enforces a per-user cooldown after a successful switch** using `Lastest UI/switch_success_cooldown.json` and config key **`switch_success_cooldown_seconds`** (default **300**). Failed attempts do not extend that timer. Set the key to **0** in `points_config.json` to disable Python-side cooldown and rely only on Streamer.bot.

If you use **two command names** (`!switch` and `!switchside`) as **two separate** Streamer.bot commands, repeat the `CommandAddTo…` call with **each** command’s ID, or merge aliases into one command if the app allows.

---

### Shared C# for !switch (read `spawn_result.txt`)

Use this **exact** block as step 2 in the recommended layout (or as step 4 in the optional layout). Adjust `RESULT_FILE` if your `Lastest UI` path differs.

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

---

### Optional layout (5 steps — `switch_side_last.txt`)

Use this if you want the cooldown decision **before** the spawn_result C# runs (e.g. extra paranoia about another command touching `spawn_result.txt` on a shared queue). Python still writes both files each run.

1. **Run a Program** — same as recommended step 1.

2. **Execute C# Code (inline)** — read `switch_side_last.txt` only:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string SWITCH_LAST = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\switch_side_last.txt";

    public bool Execute()
    {
        string flag = "0";
        try
        {
            if (File.Exists(SWITCH_LAST))
            {
                var t = File.ReadAllText(SWITCH_LAST).Trim();
                if (t == "1") flag = "1";
            }
        }
        catch { }
        CPH.SetArgument("applySwitchCooldown", flag);
        return true;
    }
}
```

3. **Conditional:** `if ("%applySwitchCooldown%" Equals "1")`
   - **True branch:** **Execute C#** using [Apply cooldown via C#](#apply-cooldown-via-c-streamerbot-api) (or a cooldown sub-action if your build has one) for **`!switch`**.  
   - **False branch:** leave empty.

4. **Execute C# Code** — same shared **!switch** C# block as in the section above.

5. **Conditional:** `if ("%spawnResult%" Equals "ok")` — success vs error **chat** messages only (same nested `commandSource` pattern as recommended step 3; do **not** add a second cooldown here if you already did steps 3–4).

---

### !switch — quick checklist

- [ ] Command/trigger **cooldown disabled** for `!switch` / `!switchside` (apply cooldown **only on success** via C# `CommandAddTo…` or a cooldown sub-action if present).
- [ ] **Recommended:** Sub-actions = **Python → !switch C# only → If spawnResult ok → True: cooldown C# then chat** (no Monster Spawner C#, no stray `%applySwitchCooldown%` unless you use the optional 5-step layout).
- [ ] **Optional 5-step:** Sub-actions = **Python → small switch_side_last C# → If applySwitchCooldown → cooldown C# → !switch C# → chat conditional** (step 2 must be the **small** file reader, not spawn code).

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

## Action 35: !corruptally

**Trigger:** Command Triggered → `!corruptally`

**Usage:** `!corruptally` — Summons a corrupted (allied) enemy from the current biome to fight for you. Boss floors allowed. Cost configurable (default 100 pts).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" corruptally %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%allyName%`, `%mobName%` (same value), `%userPointsRemaining%`:

If your success message uses `%allyName%` (like cleanse/sabotage), the C# **must** call `SetArgument("allyName", …)`. The block below sets **both** `allyName` and `mobName` so either placeholder works.

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
        CPH.SetArgument("allyName", mobName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% summoned a corrupted %allyName% to fight for you! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% summoned a corrupted %allyName% to fight for you! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Add to the same blocking queue** as other spend commands.

---

## Action 36: Bomb (!bomb, with points)

**Trigger:** Command Triggered → `!bomb` (enable **both Twitch and YouTube** as sources)

**Usage:** `!bomb` — drops a **weighted random lit bomb** 1–4 tiles from the hero (same placement logic as `!trap` / gold, excluding chasm). Types include regular and alchemy bombs; fuse timing matches a thrown lit bomb.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" bomb %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%bombName%`, and `%userPointsRemaining%`:

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
        CPH.SetArgument("bombName", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% armed a %bombName% nearby! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% armed a %bombName% nearby! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 75 points default (edit via points config as `cost_per_bomb`).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, trap, transmute, bee, ward, and earn actions.

**Fails when:** Not in an active run, hero dead, or no valid tile 1–4 tiles from hero (e.g. surrounded or only chasm).

---

## Action 37: Ring of Wealth loot (!row, with points)

**Trigger:** Command Triggered → `!row` (enable **both Twitch and YouTube** as sources)

**Usage:** `!row` — drops **Ring of Wealth** bonus loot near the hero (same logic as a kill with a forced ring tier by chapter: +1 sewers, +3 prison, +5 caves, +7 city, +10 halls). Simulates roll count from a random mob in the current biome’s spawn rotation. If the main pass yields nothing, **one** consumable-style RoW drop is guaranteed. Cost: **`cost_per_ring_of_wealth`** in `points_config.json` / points-config UI (default **100**).

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" row %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — same pattern as scroll; set `%rowLoot%` from the middle segment of `ok|detail|pts`:

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
        CPH.SetArgument("rowLoot", itemName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** commandSource pattern with YouTube/Twitch messages: `%userName% triggered Ring of Wealth loot: %rowLoot%! You have %userPointsRemaining% points left.`
   - **False branch:** same as scroll (print `%spawnResult%`).

**Add to the same blocking queue** as spawn, gold, curse, gas, scroll, and earn actions.

**Fails when:** Not in an active run, hero dead, no drop tile near hero, or game not connected to overlay WebSocket.

---

## Action 38: Give Points (!givepoints — viewer to viewer)

**Trigger:** Command Triggered → `!givepoints` (enable **both Twitch and YouTube** as sources)

**Usage:** `!givepoints <amount> <target>` — transfers points from the chatter running the command to another viewer.

- Works with **YouTube** (no `@` required): `!givepoints 50 bob`
- Works with **Twitch** (with or without `@`): `!givepoints 50 bob` or `!givepoints 50 @bob`
- Uses **all points**: it spends **chat-earned points first**, then **donor points** if needed.

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" givepoints %rawInput% %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — read `spawn_result.txt` and print it to chat (this command returns a full message string):

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

3. **Send Message** — commandSource pattern:
   - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
   - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
   - Leave **False Result** empty for both.

**Add to the same blocking queue** as other points commands.

---

## Action 39: Plant random seed (!plant, with points)

**Trigger:** Command Triggered → `!plant` (enable **both Twitch and YouTube** as sources)

**Usage:** `!plant` — plants a **random seed** 1–4 tiles from the hero (on valid ground tiles). **Fails if the Barren Land challenge is enabled.**

**Sub-Actions (in order):**

1. **Run a Program**
   - **Target:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" plant %userName%`
   - **Working Directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds

2. **Execute C# Code** — reads `spawn_result.txt`, sets `%spawnResult%`, `%plantName%`, and `%userPointsRemaining%`:

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spawn_result.txt";

    public bool Execute()
    {
        string result = "No result file - is overlay server running?";
        string plantName = "";
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
                    plantName = parts.Length >= 2 ? parts[1].Trim() : "";
                    result = parts[0].Trim();
                }
                else if (parts.Length >= 2)
                {
                    plantName = parts[1].Trim();
                    result = parts[0].Trim();
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("spawnResult", result);
        CPH.SetArgument("plantName", plantName);
        CPH.SetArgument("userPointsRemaining", userPointsRemaining);
        return true;
    }
}
```

3. **Conditional:** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% planted %plantName% nearby! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% planted %plantName% nearby! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** Use commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 30 points default (edit via points config as `cost_per_plant`).

**Add to the same blocking queue** as other spend commands.

**Fails when:** Not in an active run, hero dead, **Barren Land enabled**, or no valid ground tile 1–4 tiles from the hero.

---

## Action 40: Earn Points (Gift sub / gift membership)

**Not automatic** until you create this action and add it to your **points queue**. Super Chats and cheers need [Action 21](#action-21-earn-points-super-chat) and [Action 20](#action-20-earn-points-cheer) separately.

**Script:** `points_command.py giftmembership <username> [tier] [isSubscribed] [userIsSponsor]`

**Defaults** (`points_config.json`): tier 1 / YouTube gift = **500** pts, tier 2 = **1000**, tier 3 = **2500** (≈ $5 / $10 / $25 at 1 pt per cent). Keys: `points_per_gift_sub_tier1`, `points_per_gift_sub_tier2`, `points_per_gift_sub_tier3`, `points_per_gift_membership`, `points_per_gift_sub_prime`.

**Stacks** the same donation multipliers as Super Chat: !doublepoints → **top summoner** → sub/member ([argument reference](#cheer--super-chat--argument-reference)). Top summoner requires no extra args — `points_command.py` reads `top_summoner.txt`.

---

### Twitch — Gift Subscription (single gift)

1. **Create action** (e.g. **40 Gift Sub Points**).
2. **Trigger:** Twitch → Subscriptions → **Gift Subscription**
3. **Sub-Action:** Run a Program
   - **Program:** `python`
   - **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" giftmembership %recipientUserName% %tier% %isSubscribed% %userIsSponsor%`
   - **Working directory:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`
   - **Wait maximum:** `10` seconds
4. Credits the **recipient** (`%recipientUserName%`), not the gifter.
5. **Add to points queue.**

**Optional thank-you:** Read `donation_result.txt` → `ok|500` = 500 points to recipient.

---

### Twitch — Gift Bomb (many gifts at once)

**Trigger:** Twitch → Subscriptions → **Gift Bomb**

Streamer.bot exposes indexed recipients: `%gift.recipientUserName0%`, `%gift.recipientUserName1%`, … up to `%totalGifts% - 1`.

**Option A — one action, C# loop:** Execute C# that loops `0 .. totalGifts-1` and runs `giftmembership` per index (or POST to `/api/donation/gift-membership` per recipient).

**Option B — duplicate Run Program sub-actions** for each index you expect (fragile for large bombs).

Example **Run Program** for recipient 0 only (repeat pattern or use C#):

```text
points_command.py giftmembership %gift.recipientUserName0% %tier% %isSubscribed% %userIsSponsor%
```

---

### YouTube — Gift Membership Received

**Trigger:** YouTube → Membership → **Gift Membership Received**

YouTube’s API (via Streamer.bot) exposes the **gifter** only — there is **no recipient login** variable. Credit the **gifter** who paid:

- **Arguments:** `"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\points_command.py" giftmembership %gifterUserName% %tier% 0 %userIsSponsor%`

(`0` = not Twitch-subscribed on YouTube; `%userIsSponsor%` if the gifter is already a member for 2×.)

| Variable | Role |
|----------|------|
| `%gifterUserName%` | Who gets the points (payer) |
| `%tier%` | Membership tier for point amount |
| `%gifterIsSponsor%` | Optional; map to `userIsSponsor` if you prefer |

---

### Action 40 — HTTP alternative

**POST** `http://127.0.0.1:5000/api/donation/gift-membership`  
**Content-Type:** `application/json`

**Twitch gift sub (recipient):**

```json
{
  "username": "%recipientUserName%",
  "tier": "%tier%",
  "isSubscribed": "%isSubscribed%",
  "userIsSponsor": "%userIsSponsor%"
}
```

**YouTube gift membership (gifter):**

```json
{
  "username": "%gifterUserName%",
  "tier": "%tier%",
  "userIsSponsor": "%gifterIsSponsor%"
}
```

---

### Action 40 — local test

```text
cd Lastest UI
python points_command.py giftmembership testviewer "tier 1" 0 0 0
type donation_result.txt
```

Expect `ok|500` (tier 1 default). Tier 2: `giftmembership testviewer "tier 2"` → `ok|1000`.

---

## Commands Quick Reference

| Command | Usage | Cost | Description |
|---------|-------|------|-------------|
| **!points** | `!points` | Free | Check your point balance. |
| **!toppoints** | `!toppoints` | Free | Show top 3 point holders. |
| **!givepoints** | `!givepoints <amount> <target>` | Free | Transfer points to another viewer (no `@` required on YouTube). Spends chat points first, then donor points if needed. |
| **!spawn** | `!spawn <monster>` | Varies by monster and chapter gap (see script) | Spawn a monster near the hero. Half price when deeper than native; when shallower, chapter comparison yields **+0% / +20% / +40%** on table base (rounded). Valid monsters: rat, albino, snake, gnoll, crab, slime, swarm, thief, skeleton, bat, brute, shaman, spinner, dm100, guard, necromancer, ghoul, elemental, warlock, monk, golem, succubus, eye, scorpio. |
| **!champion** | `!champion <monster>` | 2× zone-adjusted spawn | Spawn a **champion** version of the monster (random type: Blazing, Projecting, Antimagic, Giant, Blessed, Growing). Same monster list and zone rules as spawn. |
| **!gold** | `!gold <amount>` | 2 pts per gold | Drop gold near the hero. Amount 1–100 required (e.g. `!gold 10` = 20 pts). |
| **!curse** | `!curse` | 200 pts | Curse a **random** equipped item (weapon, armor, ring, artifact, or misc). |
| **!gas** | `!gas` | 75 pts | Spawn random gas (Chaotic Censer +3). Toxic, confusion, regrowth, storm clouds, smoke, stench, inferno, blizzard, or corrosive gas. |
| **!scroll** | `!scroll` | 100 pts | Use a random scroll (like +10 Unstable Spellbook). 50% chance for exotic version. |
| **!row** | `!row` | 100 pts (default) | Ring of Wealth–style bonus loot; tier by chapter; always at least one item. Config: `cost_per_ring_of_wealth`. |
| **!trap** | `!trap` | 50 pts | Place a random visible trap 1–4 tiles from the hero. Pool of 27 traps (instant-death/high-damage ones blacklisted). |
| **!plant** | `!plant` | Configurable (default 30) | Plant a random seed near the hero. **Fails if Barren Land challenge is enabled.** |
| **!bomb** | `!bomb` | 75 pts (default) | Drop a weighted random **lit bomb** 1–4 tiles from the hero (regular + alchemy bombs). |
| **!transmute** | `!transmute` | 150 pts | Transmute a random transmutable item from bag or equipped. Same rules as Scroll of Transmutation. |
| **!bee** | `!bee` | 75 pts | Summon an allied bee next to the hero for 150 turns. Fights for you like Elixir of Honeyed Healing. |
| **!buff** | `!buff` | 75 pts | Apply a random buff (Haste, Healing, Barrier, Invisibility, etc.). Healing = 10% HP over 10 turns; Barrier = 10% HP shield. |
| **!debuff** | `!debuff` | 50 pts | Apply a random debuff (Blindness, Slow, Roots, Daze, etc.). Excludes Paralysis, Burning, Poison. |
| **!wand** | `!wand` | Configurable (default 75) | Random cursed wand effect; weighted common → very rare. |
| **!doublepoints** | `!doublepoints <minutes>` | — | **Streamer only.** 2× points for N minutes (max 120). `!doublepoints 5` for 5 min. |
| **!heal** | `!heal` | Configurable (default 100) | Heal hero ~15% HP. |
| **!cleanse** | `!cleanse` | Configurable (default 150) | Remove one random debuff. |
| **!dew** | `!dew` | Configurable (default 30) | Drop dewdrop near hero. |
| **!corruptally** | `!corruptally` | Configurable (default 100) | Summon a corrupted ally from the current biome. Boss floors allowed. |
| **!hex** | `!hex` | Configurable (default 75) | Apply Hex debuff. |
| **!degrade** | `!degrade` | Configurable (default 100) | Apply Degrade debuff. |
| **!sabotage** | `!sabotage` | Configurable (default 75) | Remove one random buff. |

**Spawn costs (base):** rat 5, albino/snake/gnoll 10, crab/slime/swarm 15, thief/skeleton/dm100 20, guard/necromancer/spinner 25, bat/brute 30, shaman 35, ghoul/elemental 40, warlock 45, monk/golem 50, succubus 60, eye 70, scorpio 80. Unknown monsters default to 100. Edit `points_config.json` or use the config UI to change.

---

## Summary

| # | Action | Trigger | Purpose |
|---|--------|---------|---------|
| 01 | Earn Points (on chat) | Message Received | +1 per message (30s cooldown; stacks 2× double / top summoner / sub) |
| 02 | Earn Points (passive) | Present Viewers | +1 per tick (60s cooldown; same multipliers) |
| 03 | First Words Bonus | (add to First Words) | +5 on first chat (same multipliers) |
| 04 | Check Points | !points | Show viewer their balance |
| 05 | Top Points | !toppoints | Show top 3 point holders |
| 38 | Give Points | !givepoints | Viewer-to-viewer transfer (chat points first, then donor points) |
| 06 | Spawn Monster | !spawn | Spend points (cost varies by monster) |
| 07 | Spawn Champion | !champion | Spawn champion (2× zone-adjusted !spawn cost) |
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
| 18 | Cursed Wand | !wand | Fixed cost; weighted random cursed-wand effect |
| 19 | Double Points | !doublepoints | Streamer only: 2× points for N min |
| 20 | Earn Points (Cheer) | Twitch Cheer | 1 pt per bit; stacks 2× (double / top summoner / sub) |
| 21 | Earn Points (Super Chat) | YouTube Super Chat | 1 pt per $0.01; same stacked 2× |
| 22 | Reset Summon March | Stream Started | Clear top summoner + session summon counts |
| 23 | Spend OFF | Hotkey | Disable spend commands |
| 24 | Spend ON | Hotkey | Enable spend commands |
| 25 | !heal | !heal | Heal hero ~15% HP |
| 26 | !cleanse | !cleanse | Remove one random debuff |
| 27 | !dew | !dew | Drop dewdrop near hero |
| 28 | !corruptally | !corruptally | Summon corrupted ally from current biome |
| 29 | !hex | !hex | Apply Hex debuff |
| 30 | !degrade | !degrade | Apply Degrade debuff |
| 31 | !sabotage | !sabotage | Remove one random buff |
| 36 | Bomb | !bomb | Spend points; weighted random lit bomb near hero |
| 37 | Ring of Wealth | !row | Spend points; RoW-style loot by chapter |
| 39 | Plant | !plant | Spend points; plant a random seed near hero (fails if Barren Land enabled) |
| 40 | Earn Points (Gift sub / membership) | Twitch Gift Sub / Gift Bomb; YouTube Gift Membership | Configurable per tier; recipient on Twitch, gifter on YouTube |

*Legacy Streamer.bot actions (omit):* Helpers/Hurters OFF/ON, `!myside`, `!switch` — for historical setup only, see the sections **"Action 25: Helpers/Hurters OFF"** through **"Action 28: !switch"** earlier in this document (not the Summary row numbers in this table).

---

## Points lock compatibility

Python (`points_command.py`) and the overlay server keep `viewer_points.txt.lock` on disk and use OS byte-range locks. Streamer.bot C# must **not** use `FileMode.CreateNew` + `File.Delete` on that path — that waits up to 10 seconds whenever the file exists, even when nothing holds the lock.

All inline C# blocks in this guide (Actions 01–05 and optional stream-start reset) use the same helpers:

- `FileStream _pointsLockStream` — instance field on `CPHInline`
- `AcquirePointsLock()` — `FileMode.OpenOrCreate`, then `Lock(0, 1)` on byte 0
- `ReleasePointsLock()` — `Unlock`, dispose stream, **do not delete** the lock file

If you update from an older export, replace `AcquirePointsLock` / `ReleasePointsLock` in each action and add the `_pointsLockStream` field. Action 01 also skips messages starting with `!` before acquiring the lock (spend commands on the blocking queue).

**Actions using this lock pattern:** 01 (chat earn), 02 (passive), 03 (First Words), 05 (`!toppoints`), optional Action 22 points reset C#. **Action 04 (`!points`)** uses `points_command.py balance` (Run Program) instead of C# file lock.

### Slow !points or First Words?

| Symptom | Likely cause | Fix |
|--------|----------------|-----|
| `!points` slow; `!spawn` fast | `!points` on **blocking spend queue** | Remove `!points` from spend queue (default or fast-reads queue) |
| `!points` compile errors (`System.Net`) | Old doc used `WebClient` — not available in Streamer.bot inline C# | Use Action 04 **Run Program + balance** + result C# from this guide |
| First Words overlay slow | Points C# **before** OBS/sound, or whole action on **blocking spend queue** | OBS + sound first; move First Words off blocking queue (Default queue) |
| First Words slow (points only) | Old lock helpers in First Words C# | Paste updated lock helpers from Action 03 |
| Both slow ~10s | Old `FileMode.CreateNew` lock still in live Streamer.bot | Search action C# for `CreateNew` — replace with `OpenOrCreate` + `Lock` |
| Action fires, no points earned | Old `CreateNew` lock in live Action **01/02/03**, or cooldown / `!` skip | Paste full C# from this guide for each earn action; search Streamer.bot for `CreateNew` |
| `!points` shows 0 for everyone | **Wait for Exit** is `0`, step 2 C# missing, or empty `%userName%` | Set Wait to **5**; add C# between Run Program and message; check Action History `output0` |

---

## Notes

- **Reset Summon March (Action 22)** runs on stream start — clears `top_summoner.txt`, summon counts, and related session files. **Viewer points** are not reset unless you do that manually (overlay **Delete all points**) or add the optional points-reset C# under Action 22.
- **Passive earn** only adds to users already in the file—they must send at least one message first. Enable **Live Update** under Platform → Twitch → Settings for Present Viewers to work.
- **Points lock file:** All earn/check/reset C# in this guide uses `FileStream.Lock` on the persistent lock file (see [Points lock compatibility](#points-lock-compatibility)). Paste updated C# into Streamer.bot if your live actions still use `CreateNew` + delete.
- For `!spawn`, the Command Triggered must pass `input1` (the text after the command). Use `%rawInput%` or `%input1%` depending on your Streamer.bot version.
- For **`!wand`**, the opposite: **do not** require extra text after the command for the script — use **`wand %userName%`** in Run a Program.
- Edit `POINTS_PER_MESSAGE`, `POINTS_PER_TICK`, and `COOLDOWN_SEC` (in Earn Points on chat for chat, Earn Points passive for passive) in the C# code. Edit points costs via http://localhost:5000/points-config or `points_config.json`.
- **Double points** persists until the duration ends. To clear it when the stream starts, add `File.WriteAllText(DOUBLE_FILE, "0");` to an optional points-reset action (see Action 22 collapsible).
- **Fard / 2× timer:** `!fard` extends global 2× (see [fard-system.md](../fard-system.md)). Countdown displays minutes only via overlay server.
- **Top summoner:** `!summon` session leader earns 2× personal points — [Top summoner 2×](#top-summoner-2-personal-points), [summon apply guide](streamerbot-summon-march-apply.md).
- **Super Chat / Cheer / gifts:** Require Streamer.bot Actions **20**, **21**, and **40** (or HTTP `/api/donation/*`). Uses `points_command.py` (`superchat`, `cheer`, `giftmembership`). Super Chat currency via Frankfurter (free, no key). Add all donation actions to the same blocking queue as earn/spend. Anonymous cheers are skipped.

---

## User-Facing Summary

- **[youtube-description.md](../youtube-description.md)** — Full YouTube description (channel assets, stream commands !fard/!summon, Discord, chat commands)
- **[user-facing-summary.md](../user-facing-summary.md)** — Chat commands only (copy-paste block)
- **[twitch-panel.md](../twitch-panel.md)** — Formatted version for Twitch panels
- **[COMMANDS.md](../../COMMANDS.md)** — Full command reference including free !fard / !summon
