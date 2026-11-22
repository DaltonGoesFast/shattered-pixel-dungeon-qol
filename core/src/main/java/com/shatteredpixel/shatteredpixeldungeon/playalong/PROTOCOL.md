# Play Along WebSocket Protocol Specification

- **Protocol Version (`PROTOCOL_VERSION`)**: `1`
- **Transport**: JSON over WebSocket (`UTF-8` text frames)
- **Max Packet Length**: `4096` bytes (server closes with `1009` if exceeded)
- **Rate Limit**: `8` messages/sec per client connection (`RATE_LIMIT_PER_SEC`)
- **Reconnect Grace Windows**:
  - Server slot retention after disconnect: `30_000 ms` (`RECONNECT_GRACE_MS`)
  - Client reconnect retry timeout: `120_000 ms` (`CLIENT_RECONNECT_GRACE_MS`)
  - Server boot rejoin grace while host loads save: `90_000 ms`
- **Lobby Capacity (`MAX_CLIENTS`)**: `100` concurrent race players (enforced at `join`/`rejoin`)

---

## Authentication & Authorization Separation

Play Along separates **race-lobby admission** from **stream-control operations**:

1. **Race Lobby Auth (`join` / `rejoin`)**:
   - Enforces `protocolVersion == 1` (checked on raw JSON presence and value; mismatch returns `join_error` and closes with `1008`).
   - Enforces optional rotating 4-character lobby code (`SessionInfo.joinCodeRequired`, `WireMessage.joinCode`) when enabled by the host.
   - Reconnecting within grace may authenticate via `joinCode` or matching prior `clientToken` for the same `userId`.
   - Enforces `SessionInfo.version` (mod version) match when both sides report non-empty versions.
   - Sanitizes `username` (`^[a-zA-Z0-9_ ]{3,16}$`), reserves generic host titles plus the session's `hostUsername`, and blocks profanity/slurs.

2. **Stream Control Auth (`stream_state_req`, `stream_link_req`, `stream_cmd_req`)**:
   - Routed through `StreamControlProxy` independently of active race sessions.
   - Does **not** require `joinCode` or an active race lobby (works from the Title Screen or solo stream runs).
   - Validates `clientToken` format (`^[a-zA-Z0-9_-]{8,64}$`) locally before forwarding HTTP requests to the configured overlay API base URL (`SPDSettings.streamOverlayUrl()`, default `http://127.0.0.1:5000`).

---

## Client -> Host Messages (`WireMessage`)

| `type` | Required / Used Fields | Behavior |
|---|---|---|
| `status_ping` | `type` | Queries host availability; replies with `status_pong`. |
| `join` | `type`, `protocolVersion` (`1`), `userId`, `username`, `heroClass`, `armorTier`, `depth`, `branch`, `pos`, optional `modVersion`, `joinCode`, `clientToken` | Enters active race session; replies with `join_ack` + `world_state`, or `join_error`. |
| `rejoin` | Same as `join` plus `sessionId`, optional `action` (`"DEAD"` if spectating) | Resumes slot in active session or receives `host_died` if session wiped. |
| `player_state` | `userId`, `heroClass`, `armorTier`, `depth`, `branch`, `pos`, `facingLeft`, `hpPct`, `action` (`IDLE\|MOVE\|ATTACK\|OPERATE\|DEAD`) | Updates client ghost visual snapshot (ignored if dead or not joined). |
| `player_died` | `depth`, `branch`, `pos`, `cause` | Marks client dead, places spectral tombstone, broadcasts `player_died_notice`. |
| `player_won` | `durationMs`, optional `clientToken` | Broadcasts `player_won_notice` and triggers victory milestone reward via overlay HTTP bridge. |
| `player_boss_slain` | `depth` (`5\|10\|15\|20\|25`), optional `clientToken` | Idempotent per boss depth; triggers boss milestone reward via overlay HTTP bridge. |
| `stream_state_req` | `requestId`, `clientToken` | Fetches `StreamEconomyState` via `StreamControlProxy`; replies with `stream_state_resp`. |
| `stream_link_req` | `requestId`, `clientToken` | Requests `!link` pairing code via `StreamControlProxy`; replies with `stream_link_resp`. |
| `stream_cmd_req` | `requestId`, `clientToken`, `rawCommand` | Executes stream deck command via `StreamControlProxy`; replies with `stream_cmd_resp`. |

---

## Host -> Client Messages (`WireMessage`)

| `type` | Key Fields | Description |
|---|---|---|
| `status_pong` | `session` (`SessionInfo`) | Host status (`active`, `serverOnline`, `inDungeon`, `hostUsername`, `joinCodeRequired`, `seed`, `challenges`, `modifiers`, `hostDepth`, `hostHp`, `hostHt`, `activePlayers`). |
| `join_ack` | `userId`, `username`, `session` | Confirms admission with server-sanitized `username` and authoritative `SessionInfo`. |
| `join_error` | `error` | Explains rejection (protocol mismatch, bad join code, inactive lobby, mod version mismatch, lobby full). |
| `world_state` | `sessionId`, `hostGhost`, `ghosts`, `tombstones`, `depthCounts`, `activePlayers` | Periodic/dirty world snapshot (`200 ms` tick). |
| `host_ankh_popped` | `sessionId`, `activePlayers` | Host triggered an Ankh; Soul Pact remains alive. |
| `host_died` | `sessionId`, `depth`, `cause`, `victims`, `victimNames` | Host died permanently; Soul Pact triggers client wipe. |
| `player_died_notice` | `username`, `depth`, `cause` | Broadcast when a racer dies individually. |
| `player_won_notice` | `username`, `durationMs` | Broadcast when a racer secures the Amulet. |
| `milestone_reward_notice` | `username`, `message`, `pointsAwarded`, `rewardKind`, `pointsBalance`, `depth` | Targeted reward notice after boss/victory overlay confirmation. |
| `chat_command` | `sessionId`, `chatCommand` (`ChatCommandPayload`: `command`, `username`, `monster`, `amount`, `slot`, `tier`, `rngSeed`, `rolledClass`, `secondaryClass`, `positiveOnly`, `bundleJson`) | Shared-RNG mirrored chat command broadcast to alive racers (excluding sender; `spawn_scale_config` sent to all). |
| `stream_state_resp` | `requestId`, `commandOk`, `message`, `economy` | Response to `stream_state_req`. |
| `stream_link_resp` | `requestId`, `commandOk`, `linkCode`, `linkExpiresSec`, `message`, `economy` | Response to `stream_link_req`. |
| `stream_cmd_resp` | `requestId`, `commandOk`, `message`, `pointsBalance`, `economy` | Response to `stream_cmd_req`. |
