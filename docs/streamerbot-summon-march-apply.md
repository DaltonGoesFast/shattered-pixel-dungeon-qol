# Summon March — Streamer.bot Apply Guide

> **Superseded (Phase 5):** `!summon` routes through **R1** only (no separate summon action). See [streaming-system-rework-plan.md](streaming-system-rework-plan.md) § Streamer.bot meta commands and [summon-march-system.md](summon-march-system.md).

## Current setup (HTTP gateway)

| Piece | Where |
|-------|--------|
| Chat | **R1** → `POST /api/chat-command` → `handle_summon()` in `chat_command.py` |
| Cooldown | 60 s per user (enforced on server; optional duplicate on Streamer.bot command if you still have one — remove legacy Command) |
| Queue / Godot | Server `POST /api/summon-march`; companion polls `GET /api/summon-march` |
| Leaderboard | Server writes `top_summoner.txt`; personal 2× in `points_command.py` |
| Sound | API `presentation` with `kind: "summon"` — `ParseChatResponse.cs` plays `SUMMON_SOUND` in `presentation_config.py` |
| Session reset | **R7** `/api/session/reset` clears summon counts (same as fard session state) |

**No** Run Program to `summon_march_post.py` in the live path. **No** separate Streamer.bot action on `!summon`.

Re-paste `ParseChatResponse.cs` after pulling summon-sound changes from the repo.

---

## Archived: separate `!summon` action (pre-gateway)

The steps below document the **old** Run Program + `summon_result.txt` layout. Keep for rollback reference only.

---

## Step 0 — Prerequisites

1. **Overlay server:** `python server.py` in `Lastest UI`
2. **Godot companion** polling `GET /api/summon-march`
3. **Python** on PATH (same as points commands)
4. Export **Earn Points** actions before editing C# (for `IsTopSummoner` below)

Use the same **Working Directory** as your other points actions (`C:\SPD\LastestUI` junction or your literal `Lastest UI` path). See [streamerbot-global-paths-example-scroll.md](streamerbot-global-paths-example-scroll.md).

---

## Action: Summon March (`!summon`)

**Trigger:** Command Triggered → `!summon`. Enable **Twitch** and **YouTube**.

**Command cooldowns** (on the command, not in the action):


| Setting             | Value        |
| ------------------- | ------------ |
| **User Cooldown**   | `60` seconds |
| **Global Cooldown** | `0`          |


Silent while on cooldown — same as other commands.

**Usage:** `!summon` (random monster) or `!summon rat` (specific monster). Free — no points.

**Sub-Actions (in order):**

### 1. Run a Program


| Field                 | Value                                                                                       |     |
| --------------------- | ------------------------------------------------------------------------------------------- | --- |
| **Target**            | `python`                                                                                    |     |
| **Working Directory** | `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI` |     |
| **Arguments**         | `"summon_march_post.py" summon %userName% %rawInput%`                                       |     |
| **Wait maximum**      | `10` seconds                                                                                |     |


Use `%rawInput%` for the monster (text after the command). If empty, Python picks random. If that does not work in your Streamer.bot build, try `%input1%`.

Script writes `summon_result.txt`: `ok|rat` on success, or an error line on failure. Also updates `top_summoner.txt` and `totalsummons.txt`.

### 2. Execute C# Code — read result file

Same pattern as `!spawn` / `!scroll` (`System.IO` only — works in inline C#):

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\summon_result.txt";

    public bool Execute()
    {
        string result = "Summon failed — is the overlay server running?";
        string monster = "";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                result = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = result.Split('|');
                if (parts.Length >= 2 && parts[0].Trim().Equals("ok", StringComparison.OrdinalIgnoreCase))
                {
                    monster = parts[1].Trim();
                    result = "ok";
                }
            }
        }
        catch (Exception ex) { result = ex.Message; }
        CPH.SetArgument("summonResult", result);
        CPH.SetArgument("summonMonster", monster);
        return true;
    }
}
```

### 3. Conditional — same as `!spawn`

`if ("%summonResult%" Equals "ok")`

- **True branch** (commandSource pattern):
  - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% summoned a %summonMonster% across the screen!`
  - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% summoned a %summonMonster% across the screen!`
- **False branch** (commandSource pattern):
  - YouTube / Twitch Message: `%summonResult%`

**Add to the same blocking queue** as spawn, scroll, and earn actions (if you use one).

**Troubleshooting:**

- **Nothing happens:** Fix **Working Directory**; test manually:  
`python summon_march_post.py summon TestUser rat`  
should write `ok|rat` to `summon_result.txt`.
- **Wrong monster name:** Error chat from Python before queueing.
- **No march on overlay:** Godot companion running? `server.py` running?

---

## Optional — OBS summon flash

After sub-action 3 **True branch** (success chat), you can add:

1. OBS GDI Text — `%userName% summoned %summonMonster%!`
2. Show `GROUP - Summoner` (H + V)
3. Delay 4000 ms
4. Hide group

`totalsummons.txt` / `top_summoner.txt` are already updated by Python — point OBS text sources at those files (no Streamer.bot Write sub-actions needed).

---

## Action: Top Summoner (`!topsummoner`)

**Trigger:** `!topsummoner` (Twitch + YouTube)

**Not** like `!heal` / `!spawn` — no Run Program, no `%spawnResult%`, no ok/false branch. Only read a file and chat the line.

**Sub-Actions (in order):**

### 1. Read Lines

- **File:** `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\top_summoner.txt`  
  *(or your `C:\SPD\LastestUI\top_summoner.txt` junction path)*

This sets **`%line0%`** to the first line, e.g. `Top Summoner: SomeViewer - 3`.

### 2. If file empty — `if ("%line0%" Is Null or Empty)`

- **True Result:**
  - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → YouTube Message: `No summons yet this stream.`
  - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → Twitch Message: `No summons yet this stream.`
- **False Result:** leave empty (continue to step 3)

### 3. If file has content — `if ("%line0%" Is Null or Empty)` *(same If, use the other branch)*

Put these under **False Result** of the same If from step 2:

- `if ("%commandSource%" Equals (Ignore Case) "youtube")` → YouTube Message: `%line0%`
- `if ("%commandSource%" Equals (Ignore Case) "twitch")` → Twitch Message: `%line0%`

**Delete** any `if ("%spawnResult%" Equals "ok")` block copied from `!heal` — that variable is never set here.

**Test:** After someone uses `!summon` successfully, `top_summoner.txt` should exist. Run `!topsummoner` → chat shows `Top Summoner: name - count`.

---

## Action: My Summons (`!mysummons`, optional)

**Trigger:** `!mysummons` (optional)

Same 3-step pattern as main summon:

1. **Run a Program** — Arguments: `"summon_march_post.py" count %userName%`, Wait `5` s
2. **Execute C# Code:**

```csharp
using System;
using System.IO;

public class CPHInline
{
    const string RESULT_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\summon_result.txt";

    public bool Execute()
    {
        string count = "0";
        try
        {
            if (File.Exists(RESULT_FILE))
            {
                string line = File.ReadAllText(RESULT_FILE).Trim();
                File.Delete(RESULT_FILE);
                var parts = line.Split('|');
                if (parts.Length >= 2 && parts[0].Trim().Equals("count", StringComparison.OrdinalIgnoreCase))
                    count = parts[1].Trim();
            }
        }
        catch { }
        CPH.SetArgument("mySummonCount", count);
        return true;
    }
}
```

3. **Chat** (commandSource pattern):

- YouTube / Twitch: `%userName%, you have summoned %mySummonCount% time(s) this stream.`

**Optional If** (zero vs non-zero) — use **`%mySummonCount%`**, not `%line0%`:

| Field | Value |
|-------|-------|
| **Input** | `%mySummonCount%` |
| **Operation** | **Equals** |
| **Value** | `0` |

- **True Result:** `%userName%, you haven't summoned anyone this stream yet.`
- **False Result:** `%userName%, you have summoned %mySummonCount% time(s) this stream.`

**Do not** use `Read Lines` or `%line0%` here — that is only for `!topsummoner`.

---

## Earn Points — top summoner 2×

Canonical guide: **[Top summoner 2× (personal points)](streamerbot-points-from-scratch.md#top-summoner-2-personal-points)** in the points doc (full C# snippets for Actions 01–03, donation notes, migration steps).

**Summary:**

| Action | Top summoner 2× |
|--------|-----------------|
| Earn Points chat (01) | C# `IsTopSummoner()` |
| Earn Points passive (02) | C# `IsTopSummoner()` |
| First Words (03) | C# `IsTopSummoner()` |
| Cheer / Super Chat / gift (20, 21, 40) | Automatic in `points_command.py` |

Stacks with !doublepoints and sub/member (up to **8×**). No extra Run Program args for donations.

**Stream Started — Action 22:** Wire [Action 22: Reset Summon March](streamerbot-points-from-scratch.md#action-22-reset-summon-march-every-stream) on Stream Started (single **Run a Program**: `"summon_march_post.py" reset`). Does **not** reset viewer points.

---

## OBS (optional)


| Source        | Path / URL                                               |
| ------------- | -------------------------------------------------------- |
| Top Summoner  | `Lastest UI/top_summoner.txt` or `GET /api/top-summoner` |
| Total Summons | `Lastest UI/totalsummons.txt` — `Total Summons: 12`      |
| Summon flash  | `GROUP - Summoner` (H + V)                               |


---

## Quick checklist

1. **Command `summon`** — triggers + **User Cooldown 60 s**
2. **Action `summon`** — 3 sub-actions (Run Program → C# → If ok)
3. **Optional** — `!topsummoner`, `!mysummons`, OBS flash, Earn Points `IsTopSummoner`
4. **Test** — `server.py` + Godot running, then `!summon rat`

**Delete if you built the old version:** cooldown C# blocks, monster resolver C#, multiple If/Else chains, Summoner Record Checker action, increment globals.

---

## Restart overlay server

After pulling repo changes:

```bash
cd "Lastest UI"
python server.py
```

Confirm startup log includes `Summon march: POST/GET /api/summon-march`.

---

## Test checklist


| Test                                          | Expected                                           |
| --------------------------------------------- | -------------------------------------------------- |
| `!summon`                                     | Queue entry in `summon_march_queue.jsonl`; chat OK |
| `!summon` within 60 s                         | Silent stop; no queue entry; no chat               |
| `!summon badmob`                              | Rejection before HTTP                              |
| `!summon` no arg                              | Random monster                                     |
| Two users                                     | Leader in `top_summoner.txt`; leader earns 2×      |
| `!topsummoner`                                | Echoes file                                        |
| server.py stopped                             | HTTP fail; no count increment                      |
| `curl http://localhost:5000/api/summon-march` | Returns events                                     |


**Smoke test without Streamer.bot:**

```bash
curl -X POST http://127.0.0.1:5000/api/summon-march -H "Content-Type: application/json" -d "{\"username\":\"TestUser\",\"monster\":\"rat\"}"
curl "http://127.0.0.1:5000/api/summon-march"
```

