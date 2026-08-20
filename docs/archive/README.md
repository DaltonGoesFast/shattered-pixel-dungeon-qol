# Archived documentation

These files describe **superseded** Streamer.bot setups. The **live** path is the HTTP gateway (R1–R10) in [../streamerbot-http-gateway-apply.md](../streamerbot-http-gateway-apply.md).

**New installs:** start with [../streaming-setup-guide.md](../streaming-setup-guide.md) — do not build from anything below.

---

## Markdown (legacy guides)

| File | What it was | Keep for |
|------|-------------|----------|
| [streamerbot-points-from-scratch.md](streamerbot-points-from-scratch.md) | ~40 actions, Run Program + `spawn_result.txt` | Migrating an old bot; C# snippet archaeology |
| [streamerbot-spawn-setup.md](streamerbot-spawn-setup.md) | Per-command `/api/spawn-command` | Historical |
| [streamerbot-commandsource-conversion.md](streamerbot-commandsource-conversion.md) | Dual Twitch/YouTube per spend action | Old bot maintenance |
| [streamerbot-global-paths-example-scroll.md](streamerbot-global-paths-example-scroll.md) | Portable `!scroll` + `C:\SPD\LastestUI` junction | Path archaeology only |
| [streamerbot-fard-rework-apply.md](streamerbot-fard-rework-apply.md) | Standalone `!fard` (pre-R1+R9) | Rollback reference |
| [streamerbot-summon-march-apply.md](streamerbot-summon-march-apply.md) | Standalone `!summon` action | Rollback reference |
| [streamerbot-http-gateway-plan.md](streamerbot-http-gateway-plan.md) | Early gateway plan | Pointer → [../streaming-system-rework-plan.md](../streaming-system-rework-plan.md) |
| [phase-0-action-inventory.md](phase-0-action-inventory.md) | Old → new action mapping (Phase 0, complete) | Migration archaeology |

**Note:** `streamerbot-points-from-scratch.md` and `streamerbot-summon-march-apply.md` still have a short **current-model** header at the top; use that only as a summary — wire the bot from [../streamerbot-http-gateway-apply.md](../streamerbot-http-gateway-apply.md).

---

## Streamer.bot exports (rollback only)

| File | What it was |
|------|-------------|
| [shatter-the-streamer-export-0.1.0.txt](shatter-the-streamer-export-0.1.0.txt) | Full ~40-action bot (v0.1.0) |
| [fard-pre-rework-export.txt](fard-pre-rework-export.txt) | Pre–HTTP-gateway fard actions only |

**Live import:** `Lastest UI/streamerbot/shatter-the-streamer-export-0.2.0` (R1–R10).
