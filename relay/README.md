# Play Along room-code relay

Streamers host Play Along by dialing **out** to this process. Players connect to `wss://playalong.playpd.net` on port 443. The relay binds `127.0.0.1:8765` and speaks plain WebSocket; Cloudflare Tunnel terminates TLS.

Dalton's viewers do **not** type a room code. His game claims the reserved room `dalton` with a host token. The title-screen Play Along button joins `wss://playalong.playpd.net/join/dalton` on its own, and falls back to the direct host:port address if that room is down.

Other hosts can claim a **custom public name** (for example `freddy`) with their own host secret, or leave the name empty for a random 4-character private code (no `0`, `O`, `1`, or `I`). Players join with the name, the code, or a full `wss://…/join/…` URL via **Join with room code**.

## Protocol

Player sockets (`/join/<CODE>`) carry the normal Play Along JSON (`status_ping`, `join`, `player_state`, …). The relay does not read those messages.

The host socket is the only place that uses a multiplexing envelope:

| Direction | Frame |
| --- | --- |
| Relay → host | `{"t":"room","c":"","d":"K7MQ"}` or `"dalton"` |
| Relay → host | `{"t":"open","c":"<id>"}` when a player connects |
| Relay → host | `{"t":"msg","c":"<id>","d":"<raw player text>"}` |
| Relay → host | `{"t":"close","c":"<id>"}` when a player leaves |
| Host → relay | `{"t":"msg","c":"<id>","d":"<raw text for that player>"}` |
| Host → relay | `{"t":"close","c":"<id>"}` to drop a player |

`d` is a JSON string, so the player text is forwarded unchanged.

A private room frame also includes `"k"`, a secret only the host sees. If that host socket drops, the relay keeps the code for 2 minutes. The host redials `/host/<CODE>`, sends `{"t":"auth","d":"<k>"}`, and gets the same code back. During that window, players who retry `/join/<CODE>` are closed with "host reconnecting" and no `join_error`, so the game keeps retrying instead of ending the run. A wrong secret does not take the room. After the window, the code is free and a new `/host` connection gets a new one.

Reserved room (`dalton`):

1. Host connects to `/host/dalton`.
2. Host sends `{"t":"auth","d":"<token>"}` within 5 seconds.
3. The token must match `PLAYALONG_DALTON_TOKEN` on the server. It is never logged.
4. A good token replaces the previous holder (reconnect after a crash). A bad token is closed with `4001` and does not kick the live host.

Custom public names (slug `[a-z][a-z0-9_-]{2,23}`, not `dalton` / blocked lookalikes such as `daltongoesfast` or `official`):

1. Host connects to `/host/<name>` and sends `{"t":"auth","d":"<secret>"}`.
2. The secret binds that name while the host is live and for the same **2-minute reclaim window** as private codes after disconnect (not for the whole relay process lifetime).
3. Matching secret takeovers / reclaim; wrong secret is `4001` and does not kick the live host. After the window the name is free for a new secret.
4. Joiners use `/join/<name>` with no secret. While the name is held offline, joins get "host reconnecting" (same retry path as private reclaim).

Host meta (optional lobby fields) on the host link:

```json
{"t":"meta","d":{"displayName":"Freddy","version":"…","protocolVersion":1,"hostDepth":7,"inDungeon":true}}
```

Allowed keys only; `official` from the host is ignored. Display names are length-capped, control-stripped, and blocklist-filtered. Meta updates are rate-limited and size-capped.

`GET /rooms` returns a JSON array of **live public rooms** (named + reserved). Private codes are never listed. The relay alone sets `"official": true` for the token-held reserved room. The list is cached a few seconds and rate-limited per IP — game clients should poll every **10–15 seconds**, not every second.

Non-`dalton` rooms that have had **at least one joiner**, then sit at **zero joiners** for `empty_grace` (default **30 seconds**, env `PLAYALONG_EMPTY_GRACE`), are closed and dropped from the list. Lobbies where nobody ever joined stay up (hero select / setup). The host must claim a new room after an empty timeout (no auto-reclaim). The reserved stream room is exempt.

Other limits: per-room player cap (default 80), player frames capped at 4096 bytes (the game packet limit) and 30 forwarded messages/second per socket (a flood is dropped, then the socket is closed), host-link frames capped at 8 MiB so a long run's world-state (including every tombstone) still fits, WebSocket ping interval/timeout (default 20s/20s), and a per-IP connection rate limit (default 40 new sockets / 30s). Behind Cloudflare the client IP is `CF-Connecting-IP` or `X-Forwarded-For`. `GET /health` returns `ok`.

## Run locally

```bash
python3 -m venv relay/.venv
relay/.venv/bin/pip install -r relay/requirements.txt
PLAYALONG_DALTON_TOKEN='choose-a-long-secret' \
  relay/.venv/bin/python relay/playalong_relay.py --host 127.0.0.1 --port 8765
```

Health check and room browser:

```bash
curl -fsS http://127.0.0.1:8765/health
curl -fsS http://127.0.0.1:8765/rooms
```

Restarting the relay drops every live room. Prefer a quiet window; hosts that keep dialing can reclaim named/private rooms within two minutes.

In the desktop game (Settings → Connectivity):

- Relay: `ws://localhost:8765` (empty resets to `wss://playalong.playpd.net`)
- Host secret (public room): the same secret as `PLAYALONG_DALTON_TOKEN` for `dalton`, or any secret you choose for a custom name
- Public room name: empty + secret → `dalton`; a custom slug + secret → that name; both empty → private code
- Turn on **Host Play Along Lobby** (room-code relay defaults on)

The public title-screen button then reaches `ws://localhost:8765/join/dalton`. Leave the name and secret empty to get a private room code instead. Direct host:port (Playit or a forwarded port) still works; the button tries the relay first and the saved direct address second.

Flags and environment variables: `--host` / `PLAYALONG_RELAY_HOST`, `--port` / `PLAYALONG_RELAY_PORT`, `--reserved-room` / `PLAYALONG_RESERVED_ROOM`, `--token` / `PLAYALONG_DALTON_TOKEN`, `--max-players`, `--max-message-bytes`, `--ping-interval`, `--ping-timeout`, `--rate-limit`, `--rate-window`.

## Test

```bash
python3 -m pip install -r relay/requirements.txt
python3 relay/test_playalong_relay.py
```

The script opens a host and two players through the relay, checks that player frames are not wrapped, claims `dalton` with the token, exercises private reclaim, and checks custom public name claim / steal / takeover.

## Server install

```bash
sudo useradd --system --home /opt/playalong-relay --shell /usr/sbin/nologin playalong
sudo mkdir -p /opt/playalong-relay
sudo cp relay/playalong_relay.py relay/requirements.txt /opt/playalong-relay/
sudo python3 -m venv /opt/playalong-relay/venv
sudo /opt/playalong-relay/venv/bin/pip install -r /opt/playalong-relay/requirements.txt
sudo cp relay/playalong-relay.service /etc/systemd/system/playalong-relay.service
echo 'PLAYALONG_DALTON_TOKEN=choose-a-long-secret' | sudo tee /etc/playalong-relay.env
sudo chmod 600 /etc/playalong-relay.env
sudo systemctl daemon-reload
sudo systemctl enable --now playalong-relay
```

Put the same token in the game's **Dalton room token** setting (or export `PLAYALONG_DALTON_TOKEN` / `PLAYALONG_HOST_TOKEN` for the desktop process). Do not commit the secret.

## Cloudflare Tunnel

TLS stops at Cloudflare. The tunnel target is plain HTTP on localhost, which is what `cloudflared` uses for WebSocket too.

```yaml
ingress:
  - hostname: playalong.playpd.net
    service: http://localhost:8765
  - service: http_status:404
```

In the Cloudflare dashboard, `playalong.playpd.net` should be proxied (orange cloud) so clients use `wss://playalong.playpd.net` on 443. WebSockets need to stay enabled on the zone (they are on by default).
