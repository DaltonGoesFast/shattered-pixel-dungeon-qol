# Economy v1.1 apply guide (Phase 6)

**Spec:** [Chat Command Economy v1.md](Chat%20Command%20Economy%20v1.md)

**Prerequisite:** Phases 0–5 complete (`Lastest UI/streamerbot/shatter-the-streamer-export-0.2.0` live).

---

## 1. Deploy server + config

1. Restart overlay server (`python server.py` in `Lastest UI/`).
2. Confirm `points_config.json` includes v1.1 keys (2 pt/msg, 20s CD, cap 500, bank ratios, donation cap 4×).
3. Optional: tune economy fields at `http://127.0.0.1:5000/points-config` (Economy v1.1 section).

---

## 2. One-time balance migration

Before go-live, announce on stream that excess chat above 500 will convert at 10% into donor pts.

```powershell
cd "Lastest UI"
python migrate_economy_v11.py          # dry-run
python migrate_economy_v11.py --apply  # write
```

---

## 3. Streamer.bot — Stream Offline (R10)

Same pattern as [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md) **Step 5b** — use **Run a Program → curl**, not inline C# (no `System.Net` in Streamer.bot).

**Action:** `R10 - Stream End`  
**Trigger:** Stream Offline (Twitch; add YouTube if you use it)

| Field | Value |
|-------|--------|
| Program | `C:\Windows\System32\curl.exe` |
| Arguments | `-s -S -m 8 -X POST -H "Content-Type: application/json" -d "{}" http://127.0.0.1:5000/api/session/end` |
| Wait for exit | **10** s |

Response `reason: debounce` is normal — the server schedules wipe/auto-bank after `reset_debounce_hours` (default 4 h). The overlay server must be running when the debounce fires, or use manual force reset below.

### Manual “bank now!” reset (Stream Deck)

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:5000/api/session/end" -Method POST `
  -ContentType "application/json" -Body '{"force":true}'
```

Runs immediately: auto-bank at 5% (10% for subs/members tracked this stream) + zero chat pts.

---

## 4. Smoke test

```powershell
cd "Lastest UI"
.\phase3_rapid_test.ps1 -Scenario Default
```

Manual checks in chat:

| Command | Expected |
|---------|----------|
| `!points` | `Chat: X/500 \| Donor: Y` |
| `!bank` | Preview line |
| `!bank all` | Converts chat → donor at 10% |
| Earn at 500 chat | Cap nudge every blocked earn |
| Super Chat during fard + sub | Donation capped at 4× (not 8×) |

---

## 5. Go-live gate

- [x] Migration run (if existing balances) — *skipped: full wipe for go-live*
- [x] Stream Offline → `POST /api/session/end` wired (R10)
- [x] One stream on v1.1 economy validated
- [x] Viewer panel / Discord one-liner updated (economy doc § Viewer-facing one-liner)
