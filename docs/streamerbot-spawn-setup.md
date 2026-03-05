# Streamer.bot Setup: !spawn Command (Phase 3)

Connect chat `!spawn <monster>` to the SPD overlay server. Works with **Twitch and YouTube**. Cooldowns are handled in Streamer.bot (server cooldown is set to 0).

---

## Prerequisites

1. **SPD Overlay Server** running (`python server.py` in `Lastest UI`)
2. **Game** running with streaming enabled (WebSocket on port 5001, the default in Settings)
3. **Streamer.bot** installed and connected to Twitch and/or YouTube

---

## Step 1: Create the Command

1. In Streamer.bot, go to **Commands** → Right-click → **Add**
2. **Name:** `Spawn Monster` (or any label)
3. **Mode:** `Basic`
4. **Location:** `Start`
5. **Commands:** `!spawn` (one per line)
6. **Cooldowns:** Set **Global Cooldown** and **User Cooldown** as desired (e.g. 30s global, 60s per user)
7. **Source(s):** Enable **Twitch Message** and/or **YouTube Message**
8. Click **Save**

---

## Step 2: Create the Action

Create an action that runs when the command is triggered. Two options:

### Option A: Run Command with PowerShell

1. **Triggers** → Add **Command Triggered** → Select your `!spawn` command
2. **Actions** → Add **Run Command** sub-action
3. **Program:** `powershell`
4. **Arguments:** (PowerShell does not easily pass username; use Option B for chatter name in log)
   ```
   -NoProfile -Command "Invoke-RestMethod -Uri 'http://127.0.0.1:5000/api/spawn-command' -Method POST -ContentType 'application/json' -Body ('{\"monster\":\"%input1%\"}')"
   ```
5. **Replace in Arguments:** Enable

**Note:** The PowerShell option does not easily pass the chatter's username to the game. For chatter name in the in-game log, use Option B (C# Code) instead.

### Option B: C# Code (more control, error handling)

1. **Triggers** → Add **Command Triggered** → Select your `!spawn` command
2. **Actions** → Add **Execute C# Code** sub-action
3. Paste the code below (or add as a C# method and call it with `CPH.SetArgument("monster", args["input1"]);` before invoking)

**C# Code (inline):**

```csharp
using System;
using System.Text;
using System.Net.Http;
using Newtonsoft.Json;

public class CPHInline {
  private static readonly HttpClient _httpClient = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };

  public bool Execute() {
    if (!CPH.TryGetArg("input1", out string monster) || string.IsNullOrWhiteSpace(monster)) {
      CPH.LogWarn("!spawn: no monster specified");
      return false;
    }
    monster = monster.Trim().ToLowerInvariant();

    string userName = CPH.TryGetArg("userName", out string u) ? u : null;
    var payload = new StringContent(
      JsonConvert.SerializeObject(new { monster, username = userName }),
      Encoding.UTF8,
      "application/json"
    );

    try {
      var response = _httpClient.PostAsync("http://127.0.0.1:5000/api/spawn-command", payload).GetAwaiter().GetResult();
      string body = response.Content.ReadAsStringAsync().GetAwaiter().GetResult();

      if (response.IsSuccessStatusCode) {
        CPH.LogInfo($"!spawn {monster} succeeded");
        return true;
      }
      CPH.LogWarn($"!spawn {monster} failed: {response.StatusCode} - {body}");
      return false;
    } catch (Exception ex) {
      CPH.LogError($"!spawn request failed: {ex.Message}");
      return false;
    }
  }
}
```

**Important:** For C# inline, Streamer.bot passes command variables as arguments. Ensure the **Command Triggered** trigger is set up so the action receives `input1`. If your C# method uses `CPH.TryGetArg("input1", ...)`, the Command Triggered action must pass arguments. Check Streamer.bot docs for how arguments flow from Command Triggered into Execute C# Code.

---

## Step 3: Assign Action to Command

1. Edit your `!spawn` command
2. In the command config, there should be an option to **assign an action** when the command is triggered
3. Or: Create an **Action** that has the **Command Triggered** trigger for `!spawn`, and add the Run Command or C# sub-action to that action

---

## Allowed Monsters

| Chat name   | Mob        |
|------------|------------|
| rat        | Rat        |
| albino     | Albino     |
| snake      | Snake      |
| gnoll      | Gnoll      |
| crab       | Crab       |
| slime      | Slime      |
| swarm      | Swarm      |
| thief      | Thief      |
| skeleton   | Skeleton   |
| bat        | Bat        |
| brute      | Brute      |
| shaman     | Shaman     |
| spinner    | Spinner    |
| dm100      | DM100      |
| guard      | Guard      |
| necromancer| Necromancer|
| ghoul      | Ghoul      |
| elemental  | Random elemental (Fire/Frost/Shock/Chaos) |
| warlock    | Warlock    |
| monk       | Monk       |
| golem      | Golem      |
| succubus   | Succubus   |
| eye        | Eye        |
| scorpio    | Scorpio    |

Unknown monsters return `400` with `{"ok": false, "error": "Unknown monster: ..."}`.

---

## How It Works

- Monsters spawn **1–4 tiles** away from the hero (random valid cell in that range).
- The chatter's name appears in the in-game log (e.g. `++ ViewerName spawned a rat!`).
- Server cooldown is disabled (0); use Streamer.bot cooldowns to limit spam.

---

## Testing

1. Start the overlay server and game (with streaming enabled).
2. Enter a dungeon level (not title screen or shop).
3. In Twitch chat (or Streamer.bot test chat): `!spawn rat`
4. A rat should spawn 1–4 tiles from the hero; the log shows who spawned it.
5. Try `!spawn elemental` for a random elemental.

---

## Game State Variables (Seed, Depth, etc.)

To use the current game seed in chat commands (e.g. `!seed`), fetch from the overlay server's `game_summary.json` (e.g. `http://127.0.0.1:5000/game_summary.json`) using C# or a custom script. The `seed` key contains the current game seed when a game is active.

---

## Points System (From Scratch)

A file-based points system with no extensions. See **[streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md)** for full setup.

**Quick summary:** Three C# actions—Earn Points (on message), Check Points (!points), Spawn Monster (!spawn with cost). Points stored in `Lastest UI/viewer_points.txt`.

---

## Points System Integration (Currency Core - Deprecated)

Use the [Points System (Core)](https://extensions.streamer.bot/t/points-system-core/49) extension so viewers spend points to spawn monsters.

### 1. Import the Points System

1. Download `MultiPlatformPointsSystem_Update.sb` from the extension page.
2. In Streamer.bot: **Import** (top left) → drag the `.sb` file in.
3. Enable the **\[Currency Core\] - Timed Action** so viewers earn points while watching.
4. Check **Live Update** under Platform → Twitch → Settings (for present viewer tracking).

### 2. Create the Spawn-with-Points Action

Create a new action (e.g. **Spawn Monster (Points)**) with these sub-actions **in order**:

| # | Sub-Action | Notes |
|---|------------|-------|
| 1 | **Get Global Points** (Points System) | Source: **User** → `%userName%`. Output goes to `%pointsArgs%` (or the variable your extension uses). |
| 2 | **Conditional** | `if (%pointsArgs% >= 100)` — adjust `100` to your desired cost. |
| 3a | **True branch** | **Add Points** (Points System): add `-100` (negative = deduct). Then **Run a Program**: `python` with args `"path\to\points_command.py" spawn %input1% %userName%`. |
| 3b | **False branch** | **Twitch Message**: `Not enough points! Spawns cost 100 points. You have %pointsArgs%.` |

### 3. Cost Options

- **Flat cost:** Same for all monsters (e.g. 100 points).
- **Tiered cost:** Use separate commands or C# to look up cost by monster (e.g. rat=50, eye=200).

### 4. Wire It to !spawn

1. Edit your **!spawn** command.
2. Remove the old action assignment.
3. Assign the new **Spawn Monster (Points)** action.

### 5. Sub-Action Details

**Get Global Points:**  
- In the Points System extension, use the sub-action that gets a user's points.  
- Set the user to `%userName%` (or the variable for the command sender).  
- The extension stores the result in a variable (often `%pointsArgs%` or `%points%`).

**Add Points (deduct):**  
- Use the **Add Points** sub-action with a **negative** value (e.g. `-100`).  
- User: `%userName%`.

**Sample Redeem reference:**  
- Open the imported **\[Currency\] Sample Redeem** action to see how cost check and deduction are done.  
- Duplicate that structure and replace the “reward” part with the spawn (Run a Program).

### 6. Optional: Tiered Costs via C# (No HTTP)

If you want different costs per monster and can use C# (file I/O works; `System.Net` does not), you can use a C# action that:

1. Reads the Points System data (extension stores points in a file—check the extension folder).
2. Validates the monster and looks up its cost.
3. Deducts points and writes the spawn request to a file that a small script or the server can pick up.

For most setups, a flat cost with the sub-action flow above is enough.

---

## Troubleshooting

- **"Game not connected" (503):** Game must be running with streaming enabled, and overlay server must be connected to the game WebSocket.
- **"Unknown monster":** Use lowercase, exact names from the table above.
- **No spawn in game:** Ensure you're in a dungeon level (not title screen, not in a shop). Monsters spawn 1–4 tiles away from the hero; there must be at least one passable cell in that range.
- **"No space to spawn":** The hero is surrounded (walls, water, etc.). The API returns `ok: false` in this case—with the points system, points are **not** deducted when spawn fails.
