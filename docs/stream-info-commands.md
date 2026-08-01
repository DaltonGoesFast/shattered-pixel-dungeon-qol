# Stream info & fun commands (separate Streamer.bot actions)

**Not part of R1 / points economy.** These stay as their own **Command Triggered** actions in Streamer.bot. Do not route them through `POST /api/chat-command`.

**Parent:** [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md) (R1–R10 points gateway)

---

## Architecture (native commands vs R1)

`!kesha` and `!seed` are **native Streamer.bot Command actions** — they fire from the **Commands** registry, not from R1.

```
Chat: !spawn rat  →  R1 (Message Received)  →  BuildChatCommandBody.cs  →  POST /api/chat-command
Chat: !kesha      →  Command action "Kesha" (native)  →  OBS / sound only
Chat: !seed       →  Command action "Seed" (native)   →  ReadGameSeed.cs → chat reply
```

| Path | Toggle | Mechanism | Stream Deck |
|------|--------|-----------|-------------|
| Points spends (`!spawn`, `!heal`, …) | **R8 Spend Toggle** | `spend_disabled.txt` + server check | Action Switch |
| `!kesha` / `!seed` | **Set Command State** | Streamer.bot disables the Command entries | Action Switch(es) |

**`BuildChatCommandBody.cs` is not involved in toggling.** It only skips `!kesha` / `!seed` so R1 does not POST them. Use Streamer.bot **Set Command State** to enable/disable the native Command entries — no flag file, no gate C# inside the Kesha/Seed actions.

Do **not** add toggle logic to R1. Do **not** reuse `spend_disabled.txt` or `R8 - Spend Toggle` for kesha/seed.

---

## Summary

| Command | Cost | What it does |
|---------|------|----------------|
| **!kesha** | Free | OBS `kesha` overlay flash (HUD + V-HUD) + `tik-tok-button.mp3` (~2s). No chat reply. **Cooldown:** 60s global, 10 min per user. |
| **!mimic** / **!tooth** | Free | If hero has **Mimic Tooth** trinket (bag or equipped), play `mimic.mp3`. No chat reply. |
| **!challenge** / **!challenges** | Free | Chat reply: active run challenges from `game_summary.json`. |
| **!seed** | Free | Chat reply: current dungeon seed from `game_summary.json`. |

**Stream Deck toggle:** Use **Set Command State** to enable/disable the `Kesha` and `Seed` Command entries (see [Toggling !kesha / !seed](#toggling-kesha--seed-set-command-state)). `!mimic` and `!challenge` are unaffected.

---

## Prerequisites

- Overlay server running (`python server.py` in `Lastest UI/`) so `game_summary.json` updates from the game WebSocket.
- Game in a live run (not title screen) for seed/challenges/mimic checks to be meaningful.

**JSON path (update if you move the folder):**

```
C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\game_summary.json
```

---

## Toggling !kesha / !seed (Set Command State)

Use Streamer.bot’s built-in **Set Command State** sub-action (`Core > Commands > Set Command State`). When a Command is **Disabled**, its action does not run — no changes needed inside the Kesha or Seed actions.

**Remove** any `CheckKeshaSeedBlocked.cs` / If-gate sub-actions you added to Kesha or Seed — revert those actions to OBS-only (kesha) and ReadGameSeed + reply (seed).

When disabled, chat `!kesha` / `!seed` is **silent** (R1 also skips them via `BuildChatCommandBody.cs`).

---

### Option A — Separate toggles (recommended)

Two Stream Deck buttons; enable/disable **one command each**.

#### Action: `Enable Kesha`

**Trigger:** none  
**Sub-actions:**

1. **Set Command State** → Command: `Kesha (!kesha)` → State: **Enabled**

#### Action: `Disable Kesha`

**Trigger:** none  
**Sub-actions:**

1. **Set Command State** → Command: `Kesha (!kesha)` → State: **Disabled**

#### Action: `Enable Seed` / `Disable Seed`

Same pattern for `Seed (!seed)`.

#### Stream Deck

| Button | Toggle Off slot | Toggle On slot |
|--------|-----------------|----------------|
| Kesha | `Disable Kesha` | `Enable Kesha` |
| Seed | `Disable Seed` | `Enable Seed` |

---

### Option B — One button toggles both

One Stream Deck Action Switch; two Streamer.bot actions (no C#).

#### Action: `Enable Kesha + Seed`

**Sub-actions (in order):**

1. **Set Command State** → `Kesha (!kesha)` → **Enabled**
2. **Set Command State** → `Seed (!seed)` → **Enabled**

#### Action: `Disable Kesha + Seed`

**Sub-actions (in order):**

1. **Set Command State** → `Kesha (!kesha)` → **Disabled**
2. **Set Command State** → `Seed (!seed)` → **Disabled**

#### Stream Deck

- **Toggle On** → `Enable Kesha + Seed`
- **Toggle Off** → `Disable Kesha + Seed`

---

### Do not use State: Toggle on Stream Deck Action Switch

The **Toggle** state on Set Command State flips the command without knowing Stream Deck’s visual state — the button art and command state can desync. Use explicit **Enabled** / **Disabled** on the Toggle On / Toggle Off slots instead.

---

### Legacy (file-based gate)

`KeshaSeedToggle.cs` / `CheckKeshaSeedBlocked.cs` / `kesha_seed_disabled.txt` — superseded by Set Command State. Do not use for new setups.

---

## !kesha

**Trigger:** Command `kesha` (`!kesha`)  
**Sub-actions:** OBS show `kesha` (HUD + V-HUD) → play sound → delay 2s → OBS hide.

No gate C# — enable/disable via **Set Command State** on the `Kesha` command (see above).

### Cooldowns (Streamer.bot Command settings)

Set these on the **Kesha** Command entry (Commands tab → select `Kesha` / `!kesha` → Cooldown fields). Native Streamer.bot cooldowns — no C# needed.

| Setting | Value |
|---------|-------|
| **Cooldown** (global) | `60` seconds |
| **User Cooldown** | `600` seconds (10 minutes) |

During cooldown, the command does not run (silent — no OBS flash). Leave “Include Broadcaster / Moderators” cooldown exemptions off unless you want staff to bypass.

---

## !mimic / !tooth

**Trigger:** Command `Mimic Tooth` (`!mimic`, `!tooth`, …)  
**Sub-actions:**

1. Execute C# — `CheckMimicTooth.cs` (sets `%mimicPresent%` = `True` / `False`)
2. If `%mimicPresent%` Equals `True` → Play sound `mimic.mp3` (56%)

Canonical C#: [Lastest UI/streamerbot/phase2/CheckMimicTooth.cs](../Lastest%20UI/streamerbot/phase2/CheckMimicTooth.cs)

---

## !challenge / !challenges

**Trigger:** Command `challenges` (`!challenge`, `!challenges`, …)  
**Sub-actions:**

1. Execute C# — `ReadActiveChallenges.cs` (sets `%activeChallenges%`)
2. Platform branch → Twitch / YouTube message: `Current Active Challenges: %activeChallenges%`

Logic: 9 challenges → `All Challenges Active (9 Challenges)`; else comma-separated list or `None`.

Canonical C#: [Lastest UI/streamerbot/phase2/ReadActiveChallenges.cs](../Lastest%20UI/streamerbot/phase2/ReadActiveChallenges.cs)

---

## !seed

**Trigger:** Command `seed` (`!seed`)  
**Sub-actions:**

1. Execute C# — `ReadGameSeed.cs` (sets `%gameSeed%`)
2. Platform branch → chat message with `%gameSeed%` (e.g. `Current seed: %gameSeed%`)

No gate C# — enable/disable via **Set Command State** on the `Seed` command (see above).

Canonical C#: [Lastest UI/streamerbot/phase2/ReadGameSeed.cs](../Lastest%20UI/streamerbot/phase2/ReadGameSeed.cs)

---

## Coexistence with R1

- **Command triggers** (native) handle `!kesha`, `!mimic`, `!challenge`, `!seed` in their **own** actions. Toggle kesha/seed with **Set Command State** — R1 is untouched.
- **R1** handles all **points** commands (`!spawn`, `!bank`, `!fard`, etc.).
- **R1 must not POST** stream-info commands — `BuildChatCommandBody.cs` returns `false` for `!kesha`, `!mimic`, `!tooth`, `!seed`, `!challenge`, `!challenges` so curl/parse/reply sub-actions are skipped. Re-paste that file into R1 step **1a** if you still see `Unknown command !kesha` in chat.

Keep these four as **enabled Command actions** after gateway cutover. They are listed in [youtube-description.md](youtube-description.md) and [twitch-panel.md](twitch-panel.md) under **Stream / info commands**.

### !mimic inventory shape

`game_summary.json` nests items under each bag: `inventory[].items[].name`. Older `CheckMimicTooth.cs` only checked `inventory[].name` (bag name), so `%mimicPresent%` stayed `False` even with Mimic Tooth in the backpack. Re-paste [CheckMimicTooth.cs](../Lastest%20UI/streamerbot/phase2/CheckMimicTooth.cs) into the Mimic Tooth action.

---

## Path migration note

If `!mimic` or `!seed` stopped working after moving the repo, re-paste C# from `phase2/` — older installs pointed at `SPD\Lastest UI\` without the `march26 mod\shattered-pixel-dungeon-qol\` segment.
