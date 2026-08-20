# Fard Rework — Streamer.bot Apply Guide

> **Superseded (Phase 5):** Fard is **R1 + R9** in the HTTP gateway. See [streamerbot-http-gateway-apply.md](../streamerbot-http-gateway-apply.md) and [README.md](README.md).

Step-by-step instructions to paste into Streamer.bot. Repo code (`server.py`, `points_command.py`) is updated separately.

**Reference:** [fard-system.md](../fard-system.md) (planned rework), [fard_system_rework plan](../.cursor/plans/fard_system_rework_0adf992a.plan.md).

---

## Step 0 — Backup (do this first)

1. **Pre-rework export saved in repo:** [`fard-pre-rework-export.txt`](fard-pre-rework-export.txt) — import in Streamer.bot to restore the old unlimited-fard / top-farder setup.
2. Export **Earn Points (chat)**, **Earn Points (passive)**, and donation actions (Cheer, Super Chat, gift) separately if you want those backed up too.
3. Save any new exports somewhere outside this repo before changing anything.

---

## Step 1 — Extend 2× C# (paste into `!fard` action)

Add as **Execute C# Code (Inline)** sub-action (after gate, before OBS).

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string DOUBLE_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\double_points_end.txt";
    const int SEC_REGULAR = 60;
    const int SEC_SUB = 300;

    public bool Execute()
    {
        try
        {
            long unixNow = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;
            long endTs = 0;
            if (File.Exists(DOUBLE_FILE))
            {
                string s = File.ReadAllText(DOUBLE_FILE).Trim();
                long.TryParse(s, out endTs);
            }
            int add = IsSubscriberOrMember() ? SEC_SUB : SEC_REGULAR;
            long newEnd = (endTs <= unixNow) ? unixNow + add : endTs + add;
            File.WriteAllText(DOUBLE_FILE, newEnd.ToString());
            CPH.SetArgument("fardExtendMinutes", (add / 60).ToString());
            return true;
        }
        catch (Exception ex) { CPH.LogInfo("Fard extend 2x: " + ex.Message); return false; }
    }

    bool IsSubscriberOrMember()
    {
        if (CPH.TryGetArg("isSubscribed", out string tw) && tw.Equals("True", StringComparison.OrdinalIgnoreCase)) return true;
        if (CPH.TryGetArg("userIsSponsor", out string yt) && yt.Equals("True", StringComparison.OrdinalIgnoreCase)) return true;
        return false;
    }
}
```

Sets `%fardExtendMinutes%` to `1` or `5` for chat messages.

---

## Step 2 — Rework `!fard` action

1. **Disable or delete the trigger** on **fard counter** (only **fard** should fire on `!fard`).
2. Rebuild **fard** sub-actions in order:

| # | Sub-action |
|---|------------|
| 1 | **If** `%userName%` equals (ignore case) `daltongoesslow` → stop (optional) |
| 2 | Get temp global `fard_used_%userName%` → local `fardUsed` |
| 3 | **If** `%fardUsed%` equals `1` → Twitch/YouTube: `%userName%, you already used your fard this stream.` → **Stop Action** |
| 4 | Set temp global `fard_used_%userName%` to `1` |
| 5 | Increment temp global `totalfard` by 1 |
| 6 | Execute C# — extend 2× (Step 1) |
| 7 | OBS GDI Text — `V - HUD :: TEXT - farder` + `HUD :: TEXT - farder` |
| 8 | OBS visibility — show `GROUP - Farder` (H + V) |
| 9 | Play sound — `fart-with-reverb.mp3` at 122% |
| 10 | Write to file — `totalfard.txt` with `%totalfard%` |
| 11 | Chat (branch `%commandSource%`): `%userName% used their fard! +%fardExtendMinutes% min of 2× for everyone.` |
| 12 | Delay 4000 ms |
| 13 | OBS visibility — hide `GROUP - Farder` (H + V) |

3. **Delete or disable:** **fard counter**, **Record Checker**, **TopfarderChecker**, **myFardChecker** (and commands `!myfards` / `!fardcount` if present).

---

## Step 3 — Remove `myFardChecker` (optional cleanup)

**Not needed** — only `!fard` is used. Delete or disable:

- Action **myFardChecker**
- Commands **!myfards**, **!fardcount**

Cooldown / already-used is handled inside **fard** (silent **Stop Action** on repeat — no chat).

---

## Step 4 — Remove top farder from Earn Points C#

In **Action 01 (chat)** and **Action 02 (passive)** and **Action 03 (First Words)** if present:

**Delete:** `TOP_FARDER_FILE` constant and entire `IsTopFarder()` method.

**Change multiplier to:**

```csharp
int mult = (IsDoublePointsActive(unixNow) ? 2 : 1) * (IsSubscriberOrMember() ? 2 : 1);
```

Updated full snippets are in [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md).

---

## Step 5 — Donation actions (Cheer, Super Chat, gift)

Ensure CLI args end with `%isSubscribed% %userIsSponsor%` only (no top-farder flag). Examples:

```text
points_command.py cheer %bits% %userName% %isSubscribed% %userIsSponsor%
points_command.py superchat %microAmount% %currencyCode% %userName% %isSubscribed% %userIsSponsor%
points_command.py giftmembership %recipientUserName% %isSubscribed% %userIsSponsor%
```

---

## Step 6 — Restart overlay server

After pulling repo changes, restart `python server.py` so the 2× countdown uses minutes-only format (`127 min`).

---

## Step 7 — OBS

- Widen any fixed-width text source reading `double_points_countdown.txt` for triple-digit minutes.
- `totalfard.txt` source unchanged.

---

## Test checklist (manual — Twitch + YouTube)

| Test | Expected |
|------|----------|
| First `!fard` (regular) | Sound + OBS; `totalfard` +1; ~1 min 2× |
| Second `!fard` same user | Silent stop; no sound, no chat |
| `!fard` as sub/member | +5 min on timer |
| Two users fard | Timer stacks |
| Chat earn during 2× | 2× (4× if sub + 2× active) |
| `!doublepoints 10` | Replaces timer to 10 min |
| Long stacked 2× | Shows `127 min` not `99:99` |
| Restart Streamer.bot | User can fard again |
