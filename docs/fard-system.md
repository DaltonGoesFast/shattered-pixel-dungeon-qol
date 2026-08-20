# Fard System

Once-per-stream community command: viewers use **`!fard`** once to flash OBS, play the sound, and **extend global 2× points** for everyone (**+3 min** regular, **+6 min** for subs/members). Logic lives in **`chat_command.py`**; OBS/sound in **R9** (via API `presentation`).

**Build / troubleshoot Streamer.bot:** [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md) (R1 + R9). **Meta overview:** [streaming-system-rework-plan.md](streaming-system-rework-plan.md) § Streamer.bot meta commands.

**Restore pre–HTTP-gateway fard:** import [`docs/archive/fard-pre-rework-export.txt`](archive/fard-pre-rework-export.txt).

**Related:** [summon-march-system.md](summon-march-system.md), [COMMANDS.md](../COMMANDS.md), [presentation_config.py](../Lastest%20UI/presentation_config.py).

---

## Overview

| Property | Value |
|----------|-------|
| **Chat route** | **R1** → `POST /api/chat-command` → `handle_fard()` |
| **Per stream** | One successful `!fard` per viewer (`session_state.json` → `fard_used`) |
| **Reward** | Stack +3 or +6 min on `double_points_end.txt` (global 2× for all chat earn) |
| **OBS / sound** | **R9 Presentation** queue (+ optional sound in R1 per `ParseChatResponse.cs`) |
| **Counter** | `totalfard.txt` via R9 / `IncrementTotalfard.cs` |

There is **no** standalone Streamer.bot **fard** Command action in the live setup.

---

## Chat behavior

| Case | Chat | OBS / sound |
|------|------|-------------|
| First `!fard` | Success line from server (`chat_messages.py`) | R9 flash + sound |
| Second `!fard` same user | “Already used your fard this stream.” | None |

Removed commands: `!topfarder`, `!myfards`, `!fardcount`.

---

## Server flow

1. **R1** posts JSON built by `BuildChatCommandBody.cs`.
2. **`chat_command.py`** checks `fard_used`, extends `double_points_end.txt`, returns `message` + `presentation` (OBS keys from `presentation_config.py`).
3. **R1** `ParseChatResponse.cs` — platform chat reply; queue **R9** for OBS; play fard sound when configured.
4. **R9** `R9LoadContext.cs` + OBS sub-actions — show/hide fard groups, GDI `%userName%`, increment `totalfard.txt`.

---

## Data files (`Lastest UI/`)

| File | Role |
|------|------|
| `session_state.json` | `fard_used` map (per stream; reset on R7 `/api/session/reset`) |
| `double_points_end.txt` | Unix end time for global 2× |
| `totalfard.txt` | Session fard count for OBS |

Competitive **personal 2×** is [summon march](summon-march-system.md) (`top_summoner.txt`), not fard.

---

## OBS source names

Defaults are in **`presentation_config.py`** (e.g. `HUD :: GROUP - Farder`, `TEXT - farder`, `fart-with-reverb.mp3`). If your scene names differ after an OBS rebuild, update that file or match [obs-scene-rebuild-plan.md](obs-scene-rebuild-plan.md).

**Common issue:** Streamer.bot not connected to OBS — fard chat and 2× timer work but nothing appears on stream. Connect OBS in Streamer.bot settings before testing.

---

## Archived apply guide

Step-by-step for the **old** standalone `!fard` action (pre-gateway): [archive/streamerbot-fard-rework-apply.md](archive/streamerbot-fard-rework-apply.md) (superseded header at top).
