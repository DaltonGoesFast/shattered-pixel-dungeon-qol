# Phase 0 — Action Inventory

**Parent plan:** [streaming-system-rework-plan.md](streaming-system-rework-plan.md)  
**Status:** Complete (July 2026)  
**Source of truth for current actions:** [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md)

Maps every live Streamer.bot action to its rework disposition. After Phase 4 cutover, only the **Target (~9)** column remains enabled.

---

## Target inventory after rework

| # | New action | Trigger | Replaces |
|---|------------|---------|----------|
| 1 | Chat router | Message Received (Twitch + YouTube) | Actions 01, 03 (points), 04–19, 25–31, 36–37, fard, summon |
| 2 | First Words presentation | First Words (Twitch + YouTube) | Action 03 (OBS half only) |
| 3 | Passive earn | Present Viewers | Action 02 |
| 4 | Cheer | Twitch Cheer | Action 20 |
| 5 | Super Chat | YouTube Super Chat | Action 21 |
| 6 | Gift | Gift sub / gift membership | Action 40 |
| 7 | Stream started | Stream Started | Action 22 (session reset → server) |
| 8 | Spend toggle | Stream Deck Action Switch | Actions 23–24 → `R8 - Spend Toggle` |
| 9 | Presentation | Sub-action from router / First Words | fard OBS block, optional summon flash |

**Delete Command registry entries:** `!spawn`, `!champion`, `!gold`, `!curse`, `!gas`, `!scroll`, `!row`, `!trap`, `!bomb`, `!transmute`, `!bee`, `!ward`, `!buff`, `!debuff`, `!wand`, `!points`, `!toppoints`, `!doublepoints`, `!heal`, `!cleanse`, `!dew`, `!plant`, `!corruptally`, `!hex`, `!degrade`, `!sabotage`, `!fard`, `!summon`, `!topsummoner`, `!mysummons`, `!givepoints` — router parses `rawMessage` instead.

---

## Current → disposition

| Current | Trigger | Disposition | Notes |
|---------|---------|-------------|-------|
| **01** Earn (chat) | Message Received | **Delete** → router `earn.message` | C# earn port to `chat_command.py` |
| **02** Earn (passive) | Present Viewers | **Replace** → Action 3 HTTP | `type: earn.passive` |
| **03** First Words | First Words | **Split** | Points → server; OBS → Action 2 only |
| **04** `!points` | Command | **Delete** → router | |
| **05** `!toppoints` | Command | **Delete** → router | |
| **06** `!spawn` | Command | **Delete** → router | |
| **07** `!champion` | Command | **Delete** → router | |
| **08** `!gold` | Command | **Delete** → router | |
| **09** `!curse` | Command | **Delete** → router | |
| **10** `!gas` | Command | **Delete** → router | |
| **11** `!scroll` | Command | **Delete** → router | |
| **12** `!trap` | Command | **Delete** → router | |
| **13** `!transmute` | Command | **Delete** → router | |
| **14** `!bee` | Command | **Delete** → router | |
| **15** `!ward` | Command | **Delete** → router | |
| **16** `!buff` | Command | **Delete** → router | |
| **17** `!debuff` | Command | **Delete** → router | |
| **18** `!wand` | Command | **Delete** → router | Args: `wand <user>` only |
| **19** `!doublepoints` | Command | **Delete** → router | Server gates `isBroadcaster` |
| **20** Cheer | Twitch Cheer | **Replace** → Action 4 HTTP | Existing `/api/donation/cheer` OK |
| **21** Super Chat | YouTube Super Chat | **Replace** → Action 5 HTTP | Existing `/api/donation/superchat` OK |
| **22** Stream Started | Stream Started | **Replace** → Action 7 | `POST /api/session/reset` |
| **23** Spend OFF | Hotkey | **Merged** → Action 8 | Was separate OFF action; now `R8 - Spend Toggle` (`state=0`) |
| **24** Spend ON | Hotkey | **Merged** → Action 8 | Was separate ON action; now `R8 - Spend Toggle` (`state=1`) |
| **25** `!heal` | Command | **Delete** → router | |
| **26** `!cleanse` | Command | **Delete** → router | |
| **27** `!dew` | Command | **Delete** → router | |
| **28** `!plant` | Command | **Delete** → router | |
| **28** `!corruptally` | Command | **Delete** → router | |
| **29** `!hex` | Command | **Delete** → router | |
| **30** `!degrade` | Command | **Delete** → router | |
| **31** `!sabotage` | Command | **Delete** → router | |
| **36** `!bomb` | Command | **Delete** → router | |
| **37** `!row` | Command | **Delete** → router | |
| **40** Gift | Gift sub / membership | **Replace** → Action 6 HTTP | `/api/donation/gift-membership` |
| **fard** | `!fard` | **Delete** → router + Action 9 | Gate/timer on server; OBS on presentation queue |
| **summon** | `!summon` | **Delete** → router | Logic from `summon_march_post.py` |
| **topsummoner** | `!topsummoner` | **Delete** → router | Read `top_summoner.txt` |
| **mysummons** | `!mysummons` | **Delete** → router | Read `summon_session_counts.json` |

### Removed / do not restore

| Item | Status |
|------|--------|
| fard counter, Record Checker, TopfarderChecker, myFardChecker | Removed in fard rework |
| `!myfards`, `!fardcount`, `!topfarder` | Removed |
| `!myside`, `!switch`, helper/hurter actions | Removed from mod |
| Actions using `helper_hurter_counter.txt` / `assigned_role.txt` | Legacy — disable |

---

## First Words split (ordering)

| Path | Trigger | Responsibility |
|------|---------|----------------|
| Points | Chat router on first message | Server `session_state.json` `first_words` idempotency |
| OBS | First Words trigger (Action 2) | Overlay + sound on **presentation queue** — no C# earn |

On a user's first message, Message Received and First Words may both fire. Server idempotency prevents double +5.

---

## Rollback reference

| Backup | Location | Scope |
|--------|----------|-------|
| Pre-fard export | `Lastest UI/streamerbot/fard-pre-rework-export.txt` | Fard actions only |
| Full bot v0.1.0 | `Lastest UI/streamerbot/shatter-the-streamer-export-0.1.0.txt` | Earlier full export |
| **Required before Phase 4** | Export manually → `shatter-the-streamer-export-0.2.0-pre-cutover.txt` | Full current bot |

See [Lastest UI/streamerbot/README-backup.md](../Lastest%20UI/streamerbot/README-backup.md) for export steps.
