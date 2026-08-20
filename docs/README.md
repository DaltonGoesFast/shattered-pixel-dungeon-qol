# Documentation index

Shattered Pixel Dungeon QoL + **Shatter the Streamer** streaming integration.

**New here?** Read [streaming-setup-guide.md](streaming-setup-guide.md) end to end, then [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md) if you are wiring Streamer.bot.

---

## By role

| Role | Start with |
|------|------------|
| Viewer / panel copy | [COMMANDS.md](../COMMANDS.md), [user-facing-summary.md](user-facing-summary.md) |
| Streamer (go live) | [streaming-setup-guide.md](streaming-setup-guide.md) § **6. Pre-stream checklist** |
| Collaborator (code) | [CONTRIBUTING.md](../CONTRIBUTING.md), [project-structure.md](project-structure.md) |
| Upstream / SPD 4.0 merge | [custom-surface-inventory.md](custom-surface-inventory.md) |
| Economy tuning | [Chat Command Economy v1.md](Chat%20Command%20Economy%20v1.md), [economy-v11-apply.md](economy-v11-apply.md) |
| Release binaries | [RELEASE.md](RELEASE.md) |

---

## Live architecture (July 2026)

```
Chat / donations → Streamer.bot (R1–R10 + native !kesha/!seed/…)
                 → POST http://127.0.0.1:5000/api/chat-command (and /api/donation/*, session)
                 → Lastest UI/server.py + chat_command.py + points_command.py
                 → Game WebSocket :5001 (spawns, scrolls, etc.)
```

| Streamer.bot | Purpose |
|--------------|---------|
| **R1** | All points-related chat |
| **R2** | First Words OBS only |
| **R3** | Passive earn |
| **R4–R6** | Cheer, Super Chat, gifts |
| **R7** | Twitch Stream Started → session reset |
| **R8** | Spend on/off (two actions for Stream Deck) |
| **R9** | Presentation queue (`!fard` OBS) |
| **R10** | Stream Offline → debounced chat wipe + auto-bank |
| **Commands** | `!kesha`, `!seed`, `!mimic`, `!challenge` — [stream-info-commands.md](stream-info-commands.md) |

Import bundle: `Lastest UI/streamerbot/shatter-the-streamer-export-0.2.0` (no `.txt` extension).

---

## Setup & apply guides

| Doc | When to use |
|-----|-------------|
| [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md) | Build or repair R1–R10 |
| [stream-info-commands.md](stream-info-commands.md) | Fun/info commands outside R1 |
| [fard-system.md](fard-system.md) | How `!fard` works (server + R9) |
| [summon-march-system.md](summon-march-system.md) | Godot companion + `!summon` marches |
| [bestiary-summon-system.md](bestiary-summon-system.md) | Zone unlocks, sprint, heat 2×, Hall of Fame |
| [economy-v11-apply.md](economy-v11-apply.md) | Cap, bank, R10 session end |
| [obs-scene-rebuild-plan.md](obs-scene-rebuild-plan.md) | OBS source naming / rebuild |

---

## Tests (no live chat required)

| Script | What it checks |
|--------|----------------|
| `Lastest UI/test_chat_command_api.ps1` | Overlay API (9 cases) |
| `Lastest UI/phase3_rapid_test.ps1` | Scenarios including SpawnStorm |

---

## Planning & history

| Doc | Notes |
|-----|-------|
| [custom-surface-inventory.md](custom-surface-inventory.md) | Java QoL/streaming hooks for upstream merge (WS, buffs, settings, assets) |
| [streaming-system-rework-plan.md](streaming-system-rework-plan.md) | Master plan; phases 0–6 complete |
| [setpiece-commands-brainstorm.md](setpiece-commands-brainstorm.md) | Draft high-cost setpiece spends (`!mimic`, `!pedestal`, `!trial`, `!ambush`, `!greed`) |

---

## Archived legacy docs

Pre–HTTP-gateway Streamer.bot (~40 actions, `spawn_result.txt`, per-command C#): **[archive/README.md](archive/README.md)** — includes old apply guides and v0.1.0 exports. Do not use for new installs.

---

## Upstream SPD (vanilla)

Android / desktop / iOS build guides: [getting-started-android.md](getting-started-android.md), [getting-started-desktop.md](getting-started-desktop.md), [getting-started-ios.md](getting-started-ios.md), [recommended-changes.md](recommended-changes.md).

For merging a new SPD version into this fork, start with [custom-surface-inventory.md](custom-surface-inventory.md) before re-discovering WebSocket and QoL hooks.
