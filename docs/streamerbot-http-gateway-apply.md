# Streamer.bot HTTP Gateway — Phase 2 Apply Guide

Step-by-step setup for the **9-action** model on a **fresh** Streamer.bot copy.  
**Prerequisites:** Phase 1 done (`server.py` running, `/api/chat-command` tests pass).

**Related:** [streaming-system-rework-plan.md](streaming-system-rework-plan.md), [archive/phase-0-action-inventory.md](archive/phase-0-action-inventory.md), [presentation_config.py](../Lastest%20UI/presentation_config.py)

**C# snippets (copy-paste):** [Lastest UI/streamerbot/phase2/](../Lastest%20UI/streamerbot/phase2/)

---

## Before you start


| Check    |                                                             |
| -------- | ----------------------------------------------------------- |
| Overlay  | `python server.py` in `Lastest UI`                          |
| API test | `test_chat_command_api.ps1` → 9 passed                      |
| Backup   | Fresh export saved (rollback)                               |
| OBS      | Connected in Streamer.bot                                   |
| Paths    | No author paths in HTTP URLs — only `http://127.0.0.1:5000` |


**Do not delete old actions yet.** Build Actions **R1–R9** (rework) disabled; cut over in Phase 4.

---



## Step 0 — Queues

Create or confirm two queues:


| Queue            | Name (example)                     | Used by                         |
| ---------------- | ---------------------------------- | ------------------------------- |
| **Blocking**     | `points` (or existing spend queue) | R1 Chat router, R3 Passive earn |
| **Presentation** | `default` or `presentation`        | R2 First Words, R9 Presentation |


**Rule:** OBS/sound never runs on the blocking queue.

---



## Step 1 — Action R1: Chat router

**Name:** `R1 - Chat router`  
**Trigger:** Message Received — **Twitch** and **YouTube** (same action)  
**Queue:** `points` (blocking)  
**Enabled:** No (until Phase 4)

### Sub-actions (in order)



#### 1a. Execute C# — build JSON body

Copy from: `Lastest UI/streamerbot/phase2/BuildChatCommandBody.cs` (no Newtonsoft — works in Streamer.bot inline C#)

Sets `%chatCommandBody%` with proper boolean `context` fields.

**Stream-info skip:** Returns `false` (halts R1) for `!kesha`, `!mimic`, `!tooth`, `!seed`, `!challenge`, `!challenges` — those use separate Command actions. See [stream-info-commands.md](stream-info-commands.md).

#### 1b. Run `curl` — POST to `/api/chat-command`

Streamer.bot inline C# has **no** `System.Net` or `System.Net.Http` — you cannot POST from Execute C# Code. Use the **Run a Program** sub-action instead.

**Delete** any `PostChatCommand.cs` sub-action if you added one. Replace it with **Run a Program**:


| Field             | Value                                                                                                           |
| ----------------- | --------------------------------------------------------------------------------------------------------------- |
| Program           | `C:\Windows\System32\curl.exe`                                                                                  |
| Arguments         | see below                                                                                                       |
| Working directory | `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`                      |
| Wait for exit     | **15** seconds (**must be > 0** — this is how stdout is captured; there is no separate “Capture output” toggle) |
| Output            | Automatic: curl JSON → `%output0%` (read by `ParseChatResponse.cs`)                                             |


**Arguments** (one line — update path if your `Lastest UI` folder moves):

```
-s -S -m 12 -X POST -H "Content-Type: application/json" --data-binary "@C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\chat_command_body.json" http://127.0.0.1:5000/api/chat-command
```

`BuildChatCommandBody.cs` (step 1a) writes that JSON file before curl runs. Requires `python server.py` running.

#### 1c. Execute C# — parse JSON response

Copy from: `Lastest UI/streamerbot/phase2/ParseChatResponse.cs` (plain string JSON parse — no System.Core)

Sets: `%apiOk%`, `%apiMessage%`, `%apiPts%`, `%apiHasPresentation%`, `%apiPresentationChat%`

#### 1d. Presentation → R9 / summon sound *(auto in ParseChatResponse.cs)*

`ParseChatResponse.cs` handles API `presentation`:

- **`kind: "fard"`** (default) → `CPH.PlaySound(...)` in R1, then `CPH.RunAction("R09 - Presentation", false)` — OBS flash only in R9
- **`kind: "summon"`** → `CPH.PlaySound(...)` inline (no R9)

**R09 tip:** OBS / GDI / delay steps must sit **outside** the `%apiPresentationChat%` If. That If is only for optional duplicate chat. Sound is handled in R1 — you can **remove** the Play Sound sub-action from R09 to avoid double audio.

**Leave step 1d empty in R1** — do not duplicate Run Action or Play Sound here.

- R9 action name: **`R09 - Presentation`** (must match your action name exactly)
- R9 queue: **`presentation`**
- R9 **enabled**



#### 1e. If `%apiMessage%` Is Null or Empty

Streamer.bot has **Is Null or Empty** only (no “Is NOT…”). Invert the branches:

- **True:** leave empty (silent earn / no chat spam)
- **False:**
  - If `%commandSource%` Equals (Ignore Case) `youtube` → **YouTube Message:** `%apiMessage%`
  - If `%commandSource%` Equals (Ignore Case) `twitch` → **Twitch Message:** `%apiMessage%`

`ParseChatResponse.cs` leaves `%apiMessage%` **empty** when the overlay server is down (empty/unparseable response) so a restart does not flood chat. Check Streamer.bot **Action History / logs** for `ParseChatResponse: empty API response…`.

`BuildChatCommandBody.cs` copies `%eventSource%` into `%commandSource%` (Message Received does not set `commandSource` by default).

**No** Run Program. **No** `spawn_result.txt`.

### Manual test (enable R1 only, disable old earn)

1. Temporarily enable R1; disable old Action 01 earn.
2. Type `hello` in chat → silent (no message), points file updates.
3. Type `!points` → balance reply on correct platform.

---



## Step 2 — Action R2: First Words presentation

**Name:** `R2 - First Words presentation`  
**Trigger:** First Words — Twitch + YouTube  
**Queue:** `presentation` (NOT points)  
**Enabled:** No

**Remove** any C# earn block from First Words. Points (+5) are awarded by the server via R1.

### Sub-actions (OBS only — copy from your current First Words)

Typical order:

1. OBS GDI Text — set chatter name (horizontal + vertical text sources)
2. OBS Source Visibility — show welcome group
3. Play sound
4. Delay 4000–7000 ms
5. OBS Source Visibility — hide group

**Do not** call `points_command.py` or write `viewer_points.txt` here.

---



## Step 3 — Action R3: Passive earn

**Name:** `R3 - Passive earn`  
**Trigger:** Present Viewers — Twitch (+ YouTube if available)  
**Queue:** `points`  
**Enabled:** No

### Sub-actions



#### 3a. Execute C# — queue passive earn batch

Copy from: `Lastest UI/streamerbot/phase2/PassiveEarnDispatch.cs`

Reads the Present Viewers **`users`** list and writes `passive_earn_batch.json` (no `System.Net` / `System.Diagnostics` — same constraints as R1 C#).

#### 3b. Run Program — POST batch

| Field             | Value                                                                                      |
| ----------------- | ------------------------------------------------------------------------------------------ |
| Program           | `python`                                                                                   |
| Working directory | `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI` |
| Arguments         | `"passive_earn_batch.py"`                                                                  |
| Wait for exit     | **10** seconds                                                                             |

Requires `python server.py` running. Logs: `passive_earn_batch: posted N/M` in the Run Program output.

Legacy one-user curl (`BuildPassiveEarnBody.cs` + curl) posted `username: ""` when the `users` list was not iterated.

---



## Step 4 — Actions R4–R6: Donations

Replace old Actions **20** (Cheer), **21** (Super Chat), **40** (Gift) with HTTP + `curl`.  
**C# files:** [Lastest UI/streamerbot/phase2/](../Lastest%20UI/streamerbot/phase2/)


| File                                | Used by                          |
| ----------------------------------- | -------------------------------- |
| `BuildCheerBody.cs`                 | R4                               |
| `BuildSuperchatBody.cs`             | R5                               |
| `BuildGiftTwitchSubBody.cs`         | R6a Twitch gift sub              |
| `BuildGiftYouTubeMembershipBody.cs` | R6c YouTube gift membership      |
| `BuildGiftTwitchGiftBombBody.cs`    | R6b Twitch gift bomb (optional)  |
| `ParseDonationResponse.cs`          | R4/R5/R6 optional thank-you chat |


**Shared paths** (update if your `Lastest UI` folder moves):

```
C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI
```

All donation actions: **Queue** `points`, **Enabled** No until Phase 4.

---



### R4 — Cheer

**Name:** `R4 - Cheer`  
**Trigger:** Twitch → **Cheer**  
**Replaces:** Action 20

#### Sub-actions (in order)

**4a.** Execute C# — `BuildCheerBody.cs`  
Writes `donation_cheer_body.json`.

**4b.** Run a Program — `curl.exe`


| Field             | Value                          |
| ----------------- | ------------------------------ |
| Program           | `C:\Windows\System32\curl.exe` |
| Working directory | `Lastest UI` path (above)      |
| Wait for exit     | **15** s                       |


**Arguments:**

```
-s -S -m 12 -X POST -H "Content-Type: application/json" --data-binary "@C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\donation_cheer_body.json" http://127.0.0.1:5000/api/donation/cheer
```

**4c.** *(Optional)* Execute C# — `ParseDonationResponse.cs`

**4d.** *(Optional)* If `%donationChatMessage%` Is Null or Empty  

- **True:** (empty)  
- **False:**  
  - If `%commandSource%` Equals (Ignore Case) `twitch` → **Twitch Message:** `%donationChatMessage%`  
  - If `%commandSource%` Equals (Ignore Case) `youtube` → **YouTube Message:** `%donationChatMessage%`

Skip 4c–4d if you want silent donations (points still apply).

---



### R5 — Super Chat

**Name:** `R5 - Super Chat`  
**Trigger:** YouTube → **Super Chat**  
**Replaces:** Action 21

#### Sub-actions (in order)

**5a.** Execute C# — `BuildSuperchatBody.cs`  
Writes `donation_superchat_body.json`.

**5b.** Run a Program — `curl.exe` (Wait **15** s)

**Arguments:**

```
-s -S -m 15 -X POST -H "Content-Type: application/json" --data-binary "@C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\donation_superchat_body.json" http://127.0.0.1:5000/api/donation/superchat
```

**5c.** *(Optional)* `ParseDonationResponse.cs`  
**5d.** *(Optional)* If `%donationChatMessage%` Is Null or Empty → **False:** YouTube Message `%donationChatMessage%`

---



### R6a — Twitch Gift Subscription (single)

**Name:** `R6a - Twitch gift sub`  
**Trigger:** Twitch → Subscriptions → **Gift Subscription**  
**Replaces:** part of Action 40

#### Sub-actions (in order)

**6a-1.** Execute C# — `BuildGiftTwitchSubBody.cs`  
Uses `%recipientUserName%`, `%tier%`. Writes `donation_gift_body.json`.

**6a-2.** Run a Program — `curl.exe` (Wait **15** s)

**Arguments:**

```
-s -S -m 12 -X POST -H "Content-Type: application/json" --data-binary "@C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\donation_gift_body.json" http://127.0.0.1:5000/api/donation/gift-membership
```

**6a-3/4.** *(Optional)* `ParseDonationResponse.cs` + thank-you If/Else (Twitch branch).

---



### R6b — Twitch Gift Bomb *(optional)*

**Name:** `R6b - Twitch gift bomb`  
**Trigger:** Twitch → Subscriptions → **Gift Bomb**

#### Sub-actions

**6b-1.** Execute C# — `BuildGiftTwitchGiftBombBody.cs` **only**  
Loops `%totalGifts%` recipients (`gift.recipientUserName0`, …) and POSTs each via `curl` internally.

If this fails to compile (`System.Diagnostics`), keep your old Action 40 gift-bomb until Phase 4.

---



### R6c — YouTube Gift Membership

**Name:** `R6c - YouTube gift membership`  
**Trigger:** YouTube → Membership → **Gift Membership Received**

#### Sub-actions (in order)

**6c-1.** Execute C# — `BuildGiftYouTubeMembershipBody.cs`  
Uses `%gifterUserName%`, `%tier%`, `%gifterIsSponsor%`. Writes `donation_gift_body.json`.

**6c-2.** Run a Program — `curl.exe` (same gift-membership URL as 6a-2)

**6c-3/4.** *(Optional)* `ParseDonationResponse.cs` + thank-you If/Else (YouTube branch).

---



### Step 4 summary — sub-action counts


| Action                 | Required sub-actions | Optional thank-you |
| ---------------------- | -------------------- | ------------------ |
| R4 Cheer               | 4a → 4b              | + 4c → 4d          |
| R5 Super Chat          | 5a → 5b              | + 5c → 5d          |
| R6a Gift sub           | 6a-1 → 6a-2          | + parse + chat     |
| R6b Gift bomb          | 6b-1 only            | —                  |
| R6c YT gift membership | 6c-1 → 6c-2          | + parse + chat     |


**No** `points_command.py`. **No** `donation_result.txt`.

---



## Step 5 — Action R7: Stream started

**Name:** `R7 - Stream session reset`  
**Trigger:** **Twitch → Stream Started only** (recommended if you always go live on Twitch + YouTube together)  
**Queue:** any

**Why not YouTube Broadcast Started?** Multiple YouTube broadcasts can fire separate start events and reset `session_state.json` more than once per session. One Twitch go-live = one reset.

**Do not add** YouTube Broadcast Started to this action unless you add server debounce or a manual override.

### Sub-action: Run `curl`


| Field         | Value                          |
| ------------- | ------------------------------ |
| Program       | `C:\Windows\System32\curl.exe` |
| Wait for exit | **10** seconds                 |


**Arguments:**

```
-s -S -m 8 -X POST -H "Content-Type: application/json" -d "{}" http://127.0.0.1:5000/api/session/reset
```

Replaces old Action 22 Run Program `summon_march_post.py reset` — server reset clears summon session files too.

---

## Step 5b — Action R10: Stream end (Economy v1.1)

**Name:** `R10 - Stream End`  
**Trigger:** **Stream Offline** (Twitch) + **Broadcast Offline** (YouTube)  
**Queue:** any

### Sub-action: Run `curl`

| Field         | Value                          |
| ------------- | ------------------------------ |
| Program       | `C:\Windows\System32\curl.exe` |
| Wait for exit | **10** seconds                 |

**Arguments:**

```
-s -S -m 8 -X POST -H "Content-Type: application/json" -d "{}" http://127.0.0.1:5000/api/session/end
```

Schedules debounced chat wipe + auto-bank (default **4 h**). Overlay server must be running when you go offline. Manual immediate reset: `{"force":true}` — see [economy-v11-apply.md](economy-v11-apply.md).

---

## Step 6 — Action R8: Spend Toggle (one action)

One action for the Streamer.bot **Action Switch** key on Stream Deck (replaces old Actions **23** and **24** as two separate actions).  
**C# file:** `SpendToggle.cs` in [phase2/](../Lastest%20UI/streamerbot/phase2/)  
(`SpendOff.cs` / `SpendOn.cs` are legacy — do not use for new setups.)

**Flag file** (update path if your `Lastest UI` folder moves):

```
C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spend_disabled.txt
```

When the file exists, spend commands return *"Spending is currently disabled by the streamer."* Earn/donations are unaffected.

---



### R8 — Spend toggle

**Name:** `R8 - Spend Toggle`  
**Trigger:** None (invoked only from Stream Deck plugin)  
**Queue:** any (not on `points`)  
**Enabled:** Yes (safe to leave on — only writes/deletes a file)

#### Sub-actions (in order)

**8.** Execute C# — `SpendToggle.cs`  
Reads `%state%` from the Stream Deck Action Switch (`0` or `1`) and creates or deletes `spend_disabled.txt`.

**State mapping (verified on Stream Deck Action Switch):**

| `%state%` | File | Spending |
|-----------|------|----------|
| `0` | create `spend_disabled.txt` | **OFF** |
| `1` | delete `spend_disabled.txt` | **ON** |

If your button art is reversed, swap the `state == "0"` / `else` branches in `SpendToggle.cs`.

**No other sub-actions.**

---



### Stream Deck setup

1. Install the [Streamer.bot Stream Deck plugin](https://streamdeck.streamer.bot/) and connect your instance.
2. Add a **Streamer.bot → Action Switch** key (not Elgato’s generic switch, not hotkeys).
3. Assign **`R8 - Spend Toggle`** to **both** slots:
   - **Toggle On** → `R8 - Spend Toggle`
   - **Toggle Off** → `R8 - Spend Toggle`
4. Set two background images for state 0 and state 1 (disabled / enabled).

The plugin passes `%state%`, `%sdButtonId%`, and `%sdButtonUuid%` automatically. Same behavior as old Actions 23–24; one Streamer.bot action instead of two.

---



## Step 7 — Action R9: Presentation

**Name:** `R9 - Presentation`  
**Trigger:** None (invoked only via Run Action from R1)  
**Queue:** `presentation`

Handles `!fard` presentation hints from API `presentation` field.

### Sub-actions (in order)

**0.** Execute C# — `R9LoadContext.cs` **first** (loads `userName`, `apiPresentationChat`, `commandSource` from globals)

1. **If** `%apiPresentationChat%` Is Null or Empty
  - **True:** leave empty (no duplicate chat)
  - **False:** platform branch → Twitch/YouTube Message `%apiPresentationChat%`
2. **OBS Set GDI Text** — **two** sub-actions (**siblings after the If**, not nested inside it):

| Sub-action | Scene | Source | **Text** (required) |
|------------|-------|--------|---------------------|
| Set GDI Text | `HUD` (or pick from dropdown) | `TEXT - farder` | `%userName%` |
| Set GDI Text | `V - HUD` | `TEXT - farder` | `%userName%` |

Copy Scene/Source from your **old working fard** action if names differ. The **Text** field must be `%userName%` — an empty Text field updates nothing visible.

3. **OBS Source Visibility — Show**
  - `HUD :: GROUP - Farder`
  - `V - HUD :: GROUP - Farder`
4. *(Optional — sound now plays in R1 `ParseChatResponse.cs`)* Play sound — `fart-with-reverb.mp3` at **122%**
5. Execute C# — `IncrementTotalfard.cs` (keep **outside** the chat If block)
6. Delay **4000** ms
7. **OBS Source Visibility — Hide** both GROUP - Farder sources

**R9 debug:** After `!fard`, open Action History → **R9 - Presentation** → confirm `%userName%` and `%apiPresentationChat%` are set. If empty, add **R9LoadContext.cs** as sub-action **0** and re-paste `ParseChatResponse.cs` (sets `r9_*` globals before RunAction).

Logical key mapping (server → OBS): see [presentation_config.py](../Lastest%20UI/presentation_config.py).

---



## Step 8 — Disable rework actions *(historical — Phase 2 only)*

During initial build, leave **R1–R9 disabled** until Phase 4 cutover. **If you are on export 0.2.0, skip this** — R1–R9 should already be enabled and old actions disabled.

---



## Phase 2 checklist

- [x] Queues: `points` + `presentation` configured
- [x] R1 Chat router built (curl + parse + platform reply)
- [x] R2 First Words OBS-only (no earn C#)
- [x] R3 Passive earn HTTP
- [x] R4–R6 Donations HTTP (`/api/donation/*`) — R4/R5 live; R6 optional
- [x] R7 Stream started → `/api/session/reset`
- [x] R8 Spend Toggle (`SpendToggle.cs`; Action Switch both slots)
- [x] R9 Presentation (fard OBS + sound + totalfard)
- [x] R10 Stream end → `/api/session/end` (Stream Offline)
- [x] All HTTP URLs use `127.0.0.1:5000` only (no file paths)
- [x] R1–R9 **enabled**; old actions disabled (Phase 4 cutover done)

When export `0.2.0` is committed → Phase 5 complete → proceed to **Phase 6** after one live stream.

**Also keep** separate Command actions for `!kesha`, `!mimic`, `!challenge`, `!seed` — see [stream-info-commands.md](stream-info-commands.md) (includes **Kesha/Seed Toggle** Stream Deck action). These do **not** go through R1.

---



## Phase 3 quick test (after enabling R1–R9 off-stream)


| Test           | Expected                                 |
| -------------- | ---------------------------------------- |
| Chat `hello`   | Silent; points increase                  |
| `!points`      | Balance in chat                          |
| `!spawn rat`   | Spawn message (game running)             |
| `!fard`        | OBS + sound; presentation chat line      |
| Second `!fard` | Chat: “already used your fard this stream” (no OBS) |
| First Words    | OBS only; +5 on server (check `!points`) |
| Stream Started | `session_state.json` reset               |


---



## Troubleshooting


| Symptom                              | Fix                                                                                                             |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------- |
| Nothing on `!points`                 | Check R1 enabled; curl wait 15s; `server.py` running                                                            |
| JSON parse error                     | Re-paste C# from `phase2/` — do **not** use Newtonsoft.Json in inline C#                                        |
| OBS delayed on First Words           | R2 must be on **presentation** queue, not `points`                                                              |
| Donations 400                        | Match field names to `/api/donation/*` in `server.py`                                                           |
| Wrong platform reply / no chat reply | Message Received uses `%eventSource%` — re-paste `BuildChatCommandBody.cs` (sets `%commandSource%` for If/Else) |
| `Unknown command !kesha` / `!mimic` etc. | Re-paste `BuildChatCommandBody.cs` (skips R1 for stream-info commands); keep separate Command actions enabled |
| `!summon` silent (no sound) | Re-paste `ParseChatResponse.cs`; set `SUMMON_SOUND` in `presentation_config.py` to your mp3 full path |
| Passive points not increasing | R03: `PassiveEarnDispatch.cs` + Run Program `python passive_earn_batch.py`; chatter must have messaged once; 60s shared cooldown with chat earn |
| `!fard` silent / no OBS | Re-paste `ParseChatResponse.cs` + `R9LoadContext.cs`; sound plays from R1 — remove Play Sound from R09 or move OBS steps **outside** the `%apiPresentationChat%` If |


---



## Next

- **Phase 5 (in progress):** Export `shatter-the-streamer-export-0.2.0.txt` — [README-backup.md](../Lastest%20UI/streamerbot/README-backup.md)
- **Phase 6:** Economy v1.1 — [Chat Command Economy v1.md](Chat%20Command%20Economy%20v1.md) (server-only, after one live stream)

---

## Hero name poll (N01–N04)

Chat stashes `!name` in Python (`hero_name.py`). After Goo (depth 5), `server.py` POSTs Streamer.bot `DoAction` with `option1`–`option4`. You apply the winner yourself (OBS `hero_name_winner.txt` or N04).

### N01 — Hero Name Poll (create)

**Action name (exact):** `N01 - Hero Name Poll`  
**Triggers:** none (HTTP `DoAction` only)  
**Enabled:** Yes

Sub-actions (UI only — no create-poll C#):

1. **Twitch → Polls → Create Poll**
   - Title: `Name the hero` (or `%pollTitle%`)
   - Choices: `%option1%`, `%option2%`, `%option3%`, `%option4%`
   - Duration: `60` (or `%pollDuration%`)
   - Channel points per vote: `0`
2. **YouTube → Polls → Create Poll**
   - Same title / duration / four `%option#%` choices

Python already supplies args via:

`POST http://127.0.0.1:7474/DoAction` with body `{ "action": { "name": "N01 - Hero Name Poll" }, "args": { "option1": "...", ... } }`.

Requires Streamer.bot **HTTP server** on `127.0.0.1:7474`.

### N02 — Twitch poll completed

**Trigger:** Twitch → Poll Completed  
**Queue:** `points` (or default)

1. Optional: **Set Argument** `heroNamePollPlatform` = `twitch`
2. **Execute C#** — paste [`ReportHeroNamePollClosed.cs`](../Lastest%20UI/streamerbot/phase2/ReportHeroNamePollClosed.cs) (writes `hero_name_poll_result_body.json` only)
3. **Run a Program** — `curl.exe` (same pattern as R1):

| Field | Value |
|-------|--------|
| Program | `C:\Windows\System32\curl.exe` |
| Arguments | `-s -S -m 12 -X POST -H "Content-Type: application/json" --data-binary "@hero_name_poll_result_body.json" http://127.0.0.1:5000/api/hero-name/poll-result` |
| Working directory | `C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI` |
| Wait | 15 s |

Skips polls whose title is not `Name the hero` (C# returns false → curl should be in a True branch, or leave curl always-on and accept empty/stale body only if C# ran).

**Important:** Put the curl step in an **If** `%heroNamePollResultBodyPath%` Is Not Empty (or run C# then curl only when C# returns true via Streamer.bot’s action continuation). Simplest: keep both sequential — C# returns false early for wrong titles without rewriting the body; for wrong titles, either delete the body file in the skip path or gate curl. Current C# does **not** clear the file on skip, so prefer gating:

- After C#: **If** `%heroNamePollResultBodyPath%` Is Not Empty → Run curl  
- Or set that arg only on success (already done) and clear it at action start with Set Argument empty.

Skips polls whose title is not `Name the hero`.

### N03 — YouTube poll closed

**Trigger:** YouTube → Poll Closed  
Same as N02: optional Set Argument `heroNamePollPlatform` = `youtube`, same C#, same curl.

**Note:** YouTube **does** expose vote vars on Poll Closed (`poll.option0.text` / `poll.option0.votes`, etc.). Python waits for **both** Twitch + YouTube (or duration+grace timeout if one never reports). N03 title must be `Name the hero` or the C# skips.

### N04 — Apply hero name (optional Stream Deck)

**Trigger:** Stream Deck / hotkey

1. **Execute C#** — paste [`ApplyHeroName.cs`](../Lastest%20UI/streamerbot/phase2/ApplyHeroName.cs)
2. **Run a Program** — `curl.exe`:

| Field | Value |
|-------|--------|
| Program | `C:\Windows\System32\curl.exe` |
| Arguments | `-s -S -m 12 -X POST -H "Content-Type: application/json" --data-binary "@hero_name_apply_body.json" http://127.0.0.1:5000/api/hero-name/apply` |
| Working directory | `Lastest UI` path (same as above) |
| Wait | 15 s |

POSTs `/api/hero-name/apply` → game WS `set_hero_name` (no GLog). Until N04 is wired, rename in-game from `Lastest UI/hero_name_winner.txt`.

### N05 — Winner chat announce (required for chat line)

**Action name (exact):** `N05 - Hero Name Winner`  
**Trigger:** none (Python `DoAction` after the poll finalizes)  
**Enabled:** Yes

Sub-actions:

1. **Twitch → Send Message to Channel** (or Chat → Send Message): `%announceMessage%`
2. **YouTube → Send Message to Channel**: `%announceMessage%`

Python sets args:

- `%announceMessage%` → `@burnz217 named hero 6 7 Kevin`
- `%userName%` / `%heroName%` also available

Example line: `@username named hero NameHere`

### Files

| File | Role |
|------|------|
| `hero_name_pot.json` | Last `!name` per viewer |
| `hero_name_blocklist.txt` | Editable reject list |
| `hero_name_winners.json` | Past suggesters excluded from later draws |
| `hero_name_pending.json` | Winner + vote split for you |
| `hero_name_winner.txt` | OBS text source |
| `hero_name_poll_state.json` | In-flight combine state |

### Quick test

1. Restart `server.py`. `!name Kesha` → silent; `!name !!!` → `@user: Name rejected.`
2. Seed four viewers in the pot; clear `hero_name_goo_polled` in `session_state.json` if needed.
3. Fake Goo: from Python overlay log path, or POST nothing — easiest is kill Goo in-game, or call `try_hero_name_goo_poll()` from a Python REPL in `Lastest UI`.
4. Confirm both platform polls show the four names; vote; confirm `hero_name_winner.txt` updates.
5. Rebuild desktop game for N04 / `set_hero_name`.

