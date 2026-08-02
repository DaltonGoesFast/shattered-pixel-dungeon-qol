# Twitch Channel Points → Donor Points (apply guide)

Convert Channel Point rewards into **flat donor points** (no fard/sub donation multipliers).

**Server:** `POST /api/channel-points/convert`  
**Related:** [Chat Command Economy v1.md](Chat%20Command%20Economy%20v1.md), [economy-v11-apply.md](economy-v11-apply.md)

---

## Your rewards (as created on Twitch)

| Reward title | Channel Points cost | Donor pts granted | Effective ratio |
|--------------|--------------------:|------------------:|----------------:|
| **25 Donor Points** | 500 | 25 | 20:1 |
| **60 Donor Points** | 1,000 | 60 | ~16.7:1 |
| **80 Donor Points** | 1,500 | 80 | 18.75:1 |

Streamer.bot sends the **exact donor amount** per action (not computed from cost), so these tiers work even when the ratio isn’t exactly 20:1.

### Streamer.bot action group

Group: **Twitch Rewards** (3 actions)

| Action | Twitch reward trigger | CP cost | Donor granted |
|--------|----------------------|--------:|--------------:|
| `T01 - 25 Points` | 25 Donor Points | 500 | 25 |
| `T02 - 60 Points` | 60 Donor Points | 1,000 | 60 |
| `T03 - 80 Points` | 80 Donor Points | 1,500 | 80 |

Build **T01** fully, then duplicate for **T02** / **T03** and change trigger + curl amounts only.

---

## Prerequisites

| Check | |
|-------|--|
| Overlay | `python server.py` running in `Lastest UI/` |
| Gateway | R1 chat router already live (same curl + parse pattern) |
| Twitch | Channel Points enabled; your 3 rewards **Enabled** |
| Queue | Blocking queue named `points` (same as R1) |

**Channel Points vs Custom Power-ups:** This guide is for **Channel Points** rewards (viewer loyalty points from watching). If Twitch shows a **Bits AUP** banner and the cost is charged in Bits, you created **Custom Power-ups** instead — recreate under **Creator Dashboard → Audience → Channel Points → Rewards**, then continue here.

---

## 1. Restart overlay server

Pull/restart so `/api/channel-points/convert` is available:

```powershell
cd "Lastest UI"
python server.py
```

Quick API smoke test (no Streamer.bot yet):

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:5000/api/channel-points/convert" -Method POST `
  -ContentType "application/json" `
  -Body '{"username":"testuser","channelPoints":500,"donorPoints":25}'
```

Expect `ok: true` and a `message` like `Converted 500 Channel Points -> +25 donor pts`.

---

## 2. Confirm rewards visible in Streamer.bot

1. **Platforms → Twitch → Channel Point Rewards** (or refresh rewards)
2. You should see:
   - `25 Donor Points`
   - `60 Donor Points`
   - `80 Donor Points`

Rewards created on Twitch still fire **Reward Redemption** triggers. Optional later: recreate them *inside* Streamer.bot if you want bot ownership (edit cost / auto-refund).

---

## 3. Shared parse snippet (once)

Create a reusable C# file (or paste into each action):

**File:** `Lastest UI/streamerbot/phase2/ParseCprConvertResponse.cs`

Same job as R1’s `ParseChatResponse.cs`, but only sets `%apiOk%` and `%apiMessage%` for the convert API.

Copy from that file in the repo (added next to other phase2 snippets).

---

## 4. Action: T01 - 25 Points

**Name:** `T01 - 25 Points`  
**Group:** `Twitch Rewards`  
**Queue:** `points` (blocking)  
**Enabled:** Yes after testing

### Trigger

**Twitch → Channel Reward → Reward Redemption** → select **`25 Donor Points`**

### Sub-actions (in order)

#### 4a. Run a Program → curl

| Field | Value |
|-------|--------|
| Program | `C:\Windows\System32\curl.exe` |
| Wait for exit | **10** seconds |
| Working directory | `...\Lastest UI` (optional) |

**Arguments** (one line):

```
-s -S -m 8 -X POST -H "Content-Type: application/json" -d "{\"username\":\"%userName%\",\"channelPoints\":500,\"donorPoints\":25}" http://127.0.0.1:5000/api/channel-points/convert
```

#### 4b. Execute C# — parse response

Paste `ParseCprConvertResponse.cs` (reads `%output0%` → sets `%apiOk%`, `%apiMessage%`).

#### 4c. Twitch → Send Message to Channel

**Message:** `%apiMessage%`

Only send when `%apiOk%` equals `true` (If/Else), or always send — failed grants still return a short error message.

---

## 5. Action: T02 - 60 Points

Duplicate **T01 - 25 Points**, then change:

| Field | New value |
|-------|-----------|
| Action name | `T02 - 60 Points` |
| Group | `Twitch Rewards` |
| Trigger reward | **`60 Donor Points`** |
| curl `channelPoints` | `1000` |
| curl `donorPoints` | `60` |

**Arguments:**

```
-s -S -m 8 -X POST -H "Content-Type: application/json" -d "{\"username\":\"%userName%\",\"channelPoints\":1000,\"donorPoints\":60}" http://127.0.0.1:5000/api/channel-points/convert
```

---

## 6. Action: T03 - 80 Points

Duplicate again:

| Field | New value |
|-------|-----------|
| Action name | `T03 - 80 Points` |
| Group | `Twitch Rewards` |
| Trigger reward | **`80 Donor Points`** |
| curl `channelPoints` | `1500` |
| curl `donorPoints` | `80` |

**Arguments:**

```
-s -S -m 8 -X POST -H "Content-Type: application/json" -d "{\"username\":\"%userName%\",\"channelPoints\":1500,\"donorPoints\":80}" http://127.0.0.1:5000/api/channel-points/convert
```

---

## 7. Live smoke test

With overlay + Streamer.bot running (Twitch connected):

1. Redeem **25 Donor Points** from your account (or a mod/test account with enough Channel Points).
2. Chat should show:  
   `@you - Converted 500 Channel Points -> +25 donor pts. Donor balance: N (saved forever).`
3. `!points` → **Donor** increased by 25.
4. Repeat once for **T02** / **T03** (or skip if CP-poor — the PowerShell test in §1 covers the API).

**If nothing happens:** Trigger not linked to the right reward title; refresh Channel Point Rewards list; confirm action Enabled and on the `points` queue.

**If chat reply missing but points granted:** curl Wait for exit must be **> 0**; parse C# must read `%output0%`; Send Message must use `%apiMessage%`.

---

## 8. Optional: refund on failure

Only works if Streamer.bot **owns** the reward (created/imported under Platforms → Twitch → Channel Point Rewards).

After parse, If `%apiOk%` == `false`:

**Twitch → Rewards → Update Redemption Status → Cancel**

That refunds Channel Points when the overlay is down or the grant fails.

---

## 9. Viewer-facing blurb (optional)

Add to Twitch panel / COMMANDS:

> **Channel Points:** Redeem **25 / 60 / 80 Donor Points** (500 / 1,000 / 1,500 CP). Converts to permanent donor points. No donation multipliers.

---

## Checklist

- [ ] Overlay restarted with `/api/channel-points/convert`
- [ ] PowerShell smoke test returns `ok: true`
- [ ] Three rewards visible / matched in Streamer.bot triggers
- [ ] Group **Twitch Rewards**: `T01 - 25 Points` wired (curl + parse + chat)
- [ ] `T02 - 60 Points` wired (trigger + curl amounts)
- [ ] `T03 - 80 Points` wired (trigger + curl amounts)
- [ ] Live redeem test + `!points` confirms donor bump
- [ ] (Optional) panel / COMMANDS one-liner updated
