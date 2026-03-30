# Streamer.bot: `!scroll` with global paths (`SPD_LASTEST_UI`)

This doc is a **drop-in example** of [Action 11: Random Scroll (!scroll)](streamerbot-points-from-scratch.md#action-11-random-scroll-scroll-with-points) from **Points System (From Scratch)** with **portable C#** via a Streamer.bot global, and a **Run a Program** setup that matches real Streamer.bot limits (see below).

**Recommended:** use the **[Portable setup (junction + fixed path)](#portable-setup-recommended-junction--fixed-path--global)** so imports ship with **one** literal path for every **Run a Program** working directory and for **`SPD_LASTEST_UI`**.

**Important:** In many Streamer.bot builds, **Run a Program → Working Directory does not resolve `%variables%`** and **there is no variable picker** for that field—only a **plain text** path works. **`points_command.py`** is therefore run with a **literal** working directory; the portable setup avoids pasting a long `Documents\…` path into every action.

For C# access to globals, see Streamer.bot’s [Arguments & Variables](https://docs.streamer.bot/api/csharp/guide/variables) (`CPH.GetGlobalVar<T>()` / `CPH.SetGlobalVar<T>()`).

---

<p id="portable-setup-recommended-junction--fixed-path--global"></p>

## Portable setup (recommended): junction + fixed path + global

Use a **short, fixed** path everywhere (Streamer.bot + docs + export). The real `Lastest UI` folder can live **anywhere**; a **directory junction** makes that folder appear at the fixed path.

### 1. Pick a canonical path (convention for this project)

Use one path **for all collaborators** in exported actions and in **`SPD_LASTEST_UI`**:

`C:\SPD\LastestUI`

*(No trailing backslash. You can choose another drive or folder, but then replace it consistently in exports and in this doc’s examples.)*

Create the parent directory if needed (e.g. `C:\SPD` must exist before the junction name is created).

### 2. Create the junction (once per machine, after extracting the repo)

In **Command Prompt**, point the junction at your **real** `Lastest UI` folder (quotes handle paths with spaces):

```bat
mkdir C:\SPD 2>nul
mklink /J "C:\SPD\LastestUI" "C:\Users\YOU\Documents\My Games\SPD\...\shattered-pixel-dungeon-qol\Lastest UI"
```

- **Remove only the junction** later (does **not** delete your project): `rmdir "C:\SPD\LastestUI"`
- If you **move** the repo, delete the junction and run `mklink /J` again with the new target.

Opening `C:\SPD\LastestUI` in Explorer should show `points_command.py`, `server.py`, etc.

### 3. Overlay server

Run the server from the **real** folder or via the junction (same files):

- From `Lastest UI`: `python server.py` (see [streaming-setup-guide.md](streaming-setup-guide.md)), **or**
- Your repo’s `start.bat` if you use one.

Keep **`config.json`**, **`points_config.json`**, etc. in that folder as usual.

### 4. Streamer.bot global (persistent)

| Name | Value |
|------|--------|
| `SPD_LASTEST_UI` | `C:\SPD\LastestUI` |

Use the same **persistent** flag you use in **`GetGlobalVar(..., true)`** ([persist vs non-persist](https://docs.streamer.bot/api/csharp/guide/variables)).

### 5. Run a Program (every `points_command.py` action)

- **Working Directory:** `C:\SPD\LastestUI` (literal — same for everyone using this convention)
- **Arguments:** `points_command.py <subcommand> …` (e.g. `points_command.py scroll %userName%`)
- **Target:** `python` or a **literal** full path to `python.exe` ([Optional: `SPD_PYTHON`](#optional-spd_python))

### 6. Import order for a new user

1. Extract / clone the project.  
2. Create **`C:\SPD\LastestUI`** junction → real `Lastest UI`.  
3. Copy `config.example.json` → `config.json`, set `save_directory`, etc.  
4. Install Python / deps, run **`server.py`**.  
5. In Streamer.bot, set global **`SPD_LASTEST_UI`** = `C:\SPD\LastestUI`.  
6. Import Streamer.bot export; **do not** edit working-directory paths unless you changed the canonical path.

---

## Prerequisites

1. **Overlay** — `python server.py` running from `Lastest UI` (or via `C:\SPD\LastestUI` — same files)
2. **Game** — streaming enabled (WebSocket matches your setup)
3. **Python** — on PATH as `python` for **Run a Program**, **or** a **literal** path to `python.exe` in **Target** (see [Optional: `SPD_PYTHON`](#optional-spd_python))
4. **Global variable** — **`SPD_LASTEST_UI`** = **`C:\SPD\LastestUI`** when using the portable setup above, **or** the full literal path to your `Lastest UI` if you skip the junction.

---

## Run a Program: what actually works

| Field | Typical Streamer.bot behavior |
|-------|-------------------------------|
| **Working Directory** | **Literal path only.** Do not rely on `%SPD_LASTEST_UI%` or any Streamer.bot global here—many builds **never** parse variables in this field, and **there is no variable picker** for it. |
| **Arguments** | Often chat variables like **`%userName%`** still work (command context). Do **not** put `%SPD_LASTEST_UI%` in front of `points_command.py` unless you have verified expansion; use a **relative** script name and set working directory instead. |

**If chat shows “No result file - is overlay server running?”** — Python never wrote `spawn_result.txt` (wrong/missing working directory, or script path not found). **Fix:** **Working Directory** = literal path to `Lastest UI` (use **`C:\SPD\LastestUI`** if you use the [portable setup](#portable-setup-recommended-junction--fixed-path--global)); **Arguments** = `points_command.py scroll %userName%` only. Ensure **`SPD_LASTEST_UI`** matches that same folder.

---

<p id="optional-one-path-via-cmdexe--process-environment"></p>

## Optional: one path via `cmd.exe` + process environment

If you want **`cd`** to use a name like `%SPD_LASTEST_UI%` **without** putting it in Streamer.bot’s Working Directory field, let **cmd** expand it from the **process environment**:

1. In **Run a Program → Environment Variables**, add **`SPD_LASTEST_UI`** = your full path to `Lastest UI` (each collaborator sets this the same way on their PC—still per-sub-action unless you use a **Windows user** environment variable with the same name instead).
2. **Target:** `cmd.exe`
3. **Arguments:** `/c cd /d "%SPD_LASTEST_UI%" && python points_command.py scroll %userName%`  
   - **cmd** expands `%SPD_LASTEST_UI%` from the environment passed to the process.  
   - Paths with spaces: keep the **quotes** around `%SPD_LASTEST_UI%` as shown.  
   - **Wait maximum:** `10` seconds
4. **Working Directory:** leave empty or use a harmless default (e.g. `%SystemRoot%`); the `cd /d` line establishes the real cwd for Python.

**Parser C#** uses **`CPH.GetGlobalVar("SPD_LASTEST_UI")`** only—that is **Streamer.bot’s** global store, not Windows. If you use this **`cmd.exe`** trick, set **the same path** in both places: persistent global `SPD_LASTEST_UI` (for C#) **and** the sub-action **Environment Variables** row (for **cmd**), unless you rely on a **Windows** user/system `SPD_LASTEST_UI` for cmd and still maintain the Streamer.bot global for C# to match.

---

## Action: Random Scroll (`!scroll`, with points)

**Trigger:** Command Triggered → `!scroll` (enable **both Twitch and YouTube** as sources)

**Usage:** `!scroll` — same behavior as the main guide: random scroll spend, `ok|ScrollName|pts` in the result file on success.

**Sub-Actions (in order):**

1. **Run a Program** — **literal cwd**
   - **Target:** `python`  
     *(or full path to `python.exe` if needed — see [Optional: `SPD_PYTHON`](#optional-spd_python))*
   - **Working Directory:** **`C:\SPD\LastestUI`** when using the [portable setup](#portable-setup-recommended-junction--fixed-path--global); otherwise paste your real `Lastest UI` path
   - **Arguments:** `points_command.py scroll %userName%`
   - **Wait maximum:** `10` seconds *(required — otherwise C# runs before Python writes `spawn_result.txt`)*

   *Or use the **`cmd.exe` + Environment Variables** block above instead of this step.*

2. **Execute C# Code** — **[Parser C#](#parser-c-read-spawn_resulttxt)** below.

---

<p id="parser-c-read-spawn_resulttxt"></p>

### Parser C# — read `spawn_result.txt`

Use this **once** after **Run a Program**. It uses global `SPD_LASTEST_UI` to locate `spawn_result.txt` (same folder as `points_command.py`). That global must match the folder Python used (literal **Working Directory**, or the path in **`cmd.exe`’s** `SPD_LASTEST_UI` environment variable).

```csharp
using System;
using System.IO;

public class CPHInline
{
    public bool Execute()
    {
        // Persistent global — must match how you created SPD_LASTEST_UI in Streamer.bot
        string root = CPH.GetGlobalVar<string>("SPD_LASTEST_UI", true);
        if (string.IsNullOrWhiteSpace(root))
        {
            CPH.SetArgument("spawnResult", "SPD_LASTEST_UI global not set in Streamer.bot");
            CPH.SetArgument("scrollName", "");
            CPH.SetArgument("userPointsRemaining", "");
            return true;
        }

        string resultFile = Path.Combine(root.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar), "spawn_result.txt");

        string result = "No result file - is overlay server running?";
        string itemName = "";
        string userPointsRemaining = "";
        try
        {
            if (File.Exists(resultFile))
            {
                result = File.ReadAllText(resultFile).Trim();
                File.Delete(resultFile);
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

### Chat replies (after parser)

**Conditional (final sub-action in this action):** `if ("%spawnResult%" Equals "ok")`
   - **True branch:** commandSource pattern (same as main guide):
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%userName% used a random scroll: %scrollName%! You have %userPointsRemaining% points left.`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%userName% used a random scroll: %scrollName%! You have %userPointsRemaining% points left.`
     - Leave **False Result** empty for both.
   - **False branch:** commandSource pattern:
     - `if ("%commandSource%" Equals (Ignore Case) "youtube")` → **True:** YouTube Message: `%spawnResult%`
     - `if ("%commandSource%" Equals (Ignore Case) "twitch")` → **True:** Twitch Message: `%spawnResult%`
     - Leave **False Result** empty for both.

**Cost:** 100 points (edit via points config / `points_config.json` — same as [Action 11](streamerbot-points-from-scratch.md#action-11-random-scroll-scroll-with-points)).

**Add to the same blocking queue** as spawn, gold, curse, gas, and earn actions (see main guide).

**Fails when:** Not in an active run, hero dead, magic immune, or blinded.

---

## Optional: `SPD_PYTHON`

If Streamer.bot cannot find `python` on PATH:

1. **Run a Program → Target:** paste the **full path** to `python.exe` (e.g. `C:\Users\you\AppData\Local\Programs\Python\Python312\python.exe`).  
   *(A Streamer.bot global name here will only work if your build substitutes it in **Target**—many do not; literals are safe.)*
2. **Arguments** stay as above (`points_command.py scroll %userName%` with literal **Working Directory**, or the same inside your `cmd /c` line).

---

## Test manually

From a terminal, after `cd` to your `Lastest UI` folder:

`python points_command.py scroll YourUsername`

Expect `ok|SomeScrollName|{pts}` or an error written to `spawn_result.txt`. Matches [Action 11 troubleshooting](streamerbot-points-from-scratch.md#action-11-random-scroll-scroll-with-points) flow.

---

## Applying this pattern elsewhere

Any action that **runs `points_command.py`** should:

1. **Run a Program** — **literal** **Working Directory** = **`C:\SPD\LastestUI`** ([portable setup](#portable-setup-recommended-junction--fixed-path--global)) or your real path + `points_command.py <subcommand> …`, **or** the **`cmd.exe` + environment variable** pattern ([see above](#optional-one-path-via-cmdexe--process-environment)).
2. **Parser / other C#** — `Path.Combine(root, "filename.txt")` with `root` from `CPH.GetGlobalVar<string>("SPD_LASTEST_UI", true)` (match the persistent flag to your global). **`SPD_LASTEST_UI`** must be the **same** folder string as **Working Directory** (e.g. both `C:\SPD\LastestUI`).

Full command reference: [Points System (From Scratch)](streamerbot-points-from-scratch.md).

