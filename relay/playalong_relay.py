#!/usr/bin/env python3
"""Play Along room-code relay.

The host's game speaks the Play Along wire protocol to many players. This
process sits in the middle so the host can dial out (no Playit, no port
forward) and players can connect with wss on 443.

Topology
--------
* Host  ->  ws://127.0.0.1:8765/host
    Random 4-character room code. First frame to the host::

        {"t":"room","c":"","d":"K7MQ"}

* Host  ->  ws://127.0.0.1:8765/host/dalton
    Reserved public room. The host must send this text frame within a few
    seconds (the token is not logged)::

        {"t":"auth","d":"<PLAYALONG_DALTON_TOKEN>"}

    A matching token claims ``dalton`` and replaces any previous holder, so a
    crashed host can reconnect. A wrong token is closed and does not kick the
    current host. Viewers of the public stream join ``/join/dalton`` with no
    code; the game's title-screen button does that itself.

* Player -> ws://127.0.0.1:8765/join/<CODE>
    Unchanged Play Along JSON (``join``, ``status_ping``, ``player_state``, ...).
    The relay does not interpret those messages. Incoming player frames are
    capped at 4096 bytes (the game's packet limit) and at a small per-socket
    rate so one player cannot fill the shared host link.

Host link multiplexing (host socket only)::

    {"t":"open","c":"<id>"}
    {"t":"msg","c":"<id>","d":"<raw player text>"}
    {"t":"close","c":"<id>"}

The host uses the same envelope to send ``msg`` and ``close`` back. ``d`` is a
string, so player bytes are forwarded unchanged.

Cloudflare terminates TLS. This process speaks plain WebSocket and binds
127.0.0.1 by default. Point the tunnel at http://localhost:8765.

GET /health returns plain text ``ok``.
"""

from __future__ import annotations

import argparse
import asyncio
import hmac
import json
import logging
import os
import secrets
import time
from collections import deque
from dataclasses import dataclass
from typing import Deque, Dict, Optional
from urllib.parse import unquote

from websockets.asyncio.server import ServerConnection, serve
from websockets.exceptions import ConnectionClosed

log = logging.getLogger("playalong_relay")

# Unambiguous alphabet: no 0/O/1/I. 32 symbols, 4 characters.
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


@dataclass
class RelayConfig:
    host: str = "127.0.0.1"
    port: int = 8765
    reserved_room: str = "dalton"
    reserved_token: str = ""
    max_players: int = 80
    # Host → relay frames (world-state envelopes). Player frames use the tighter cap.
    max_message_bytes: int = 128 * 1024
    # PlayAlongProtocol.MAX_PACKET_LENGTH. Enforced on /join before forward.
    max_player_message_bytes: int = 4096
    # Per player socket. Surplus frames are not forwarded; a sustained flood closes.
    player_msg_rate: int = 30
    player_msg_burst: int = 60
    ping_interval: float = 20.0
    ping_timeout: float = 20.0
    rate_limit: int = 40
    rate_window: float = 30.0
    auth_timeout: float = 5.0
    # How long a private code can be reclaimed after the host socket drops.
    reclaim_grace: float = 120.0


class Player:
    def __init__(self, client_id: str, ws: ServerConnection) -> None:
        self.client_id = client_id
        self.ws = ws
        self.send_lock = asyncio.Lock()
        self.window_start = time.monotonic()
        self.window_count = 0


class Room:
    def __init__(self, code: str, ws: ServerConnection) -> None:
        self.code = code
        self.ws = ws
        self.players: Dict[str, Player] = {}
        self.next_id = 1
        self.lock = asyncio.Lock()
        self.send_lock = asyncio.Lock()
        self.closed = False
        self.ready = False
        self.host_secret = ""
        self.reserved = False

    async def send(self, text: str) -> None:
        async with self.send_lock:
            await self.ws.send(text)


class RateLimiter:
    def __init__(self, limit: int, window: float) -> None:
        self.limit = limit
        self.window = window
        self.events: Dict[str, Deque[float]] = {}
        self.lock = asyncio.Lock()

    async def allow(self, ip: str) -> bool:
        now = time.monotonic()
        async with self.lock:
            bucket = self.events.get(ip)
            if bucket is None:
                bucket = deque()
                self.events[ip] = bucket
            while bucket and now - bucket[0] > self.window:
                bucket.popleft()
            if len(bucket) >= self.limit:
                return False
            bucket.append(now)
            if len(self.events) > 10000:
                self.events.clear()
                self.events[ip] = bucket
            return True


def envelope(kind: str, client_id: str, payload: Optional[str] = None) -> str:
    obj = {"t": kind, "c": client_id}
    if payload is not None:
        obj["d"] = payload
    return json.dumps(obj, separators=(",", ":"), ensure_ascii=False)


def room_frame(code: str, host_secret: str = "") -> str:
    obj = {"t": "room", "c": "", "d": code}
    if host_secret:
        obj["k"] = host_secret
    return json.dumps(obj, separators=(",", ":"), ensure_ascii=False)


def token_matches(got: Optional[str], expected: str) -> bool:
    if not expected or not isinstance(got, str):
        return False
    got_b = got.encode("utf-8")
    exp_b = expected.encode("utf-8")
    if len(got_b) != len(exp_b):
        return False
    return hmac.compare_digest(got_b, exp_b)


class Relay:
    def __init__(self, config: RelayConfig) -> None:
        self.config = config
        self.rooms: Dict[str, Room] = {}
        # code -> (host_secret, expiry monotonic). Private rooms only.
        self.reclaim: Dict[str, tuple] = {}
        self.rooms_lock = asyncio.Lock()
        self.limiter = RateLimiter(config.rate_limit, config.rate_window)

    def process_request(self, connection: ServerConnection, request):
        path = route_path(request.path)
        if path == "/health" or path == "/health/":
            return connection.respond(200, "ok\n")
        # Apply this before data frames are parsed so an oversized player
        # payload is rejected from its header instead of buffered.
        if path.startswith("/join/"):
            connection.protocol.max_size = self.config.max_player_message_bytes
        return None

    async def start(self):
        return await serve(
            self.handler,
            self.config.host,
            self.config.port,
            process_request=self.process_request,
            compression=None,
            max_size=self.config.max_message_bytes,
            ping_interval=self.config.ping_interval,
            ping_timeout=self.config.ping_timeout,
            logger=log,
        )

    async def handler(self, ws: ServerConnection) -> None:
        ip = client_ip(ws)
        if not await self.limiter.allow(ip):
            log.info("rate limit ip=%s", ip)
            await ws.close(1008, "rate limited")
            return
        path = route_path(ws.request.path if ws.request is not None else "/")
        try:
            if path == "/host":
                await self.handle_host(ws, None, ip)
            elif path.startswith("/host/"):
                await self.handle_host(ws, unquote(path[len("/host/"):]), ip)
            elif path.startswith("/join/"):
                await self.handle_player(ws, unquote(path[len("/join/"):]), ip)
            else:
                await ws.close(1008, "not found")
        except ConnectionClosed:
            pass

    async def handle_host(self, ws: ServerConnection, requested: Optional[str], ip: str) -> None:
        if requested is None:
            room = await self.adopt_room(ws, None, ip, reserved=False)
            if room is not None:
                await self.handle_host_frames(room)
            return
        if requested.strip().lower() == self.config.reserved_room.lower():
            if not await self.authorize_reserved(ws):
                return
            room = await self.adopt_room(
                ws, self.config.reserved_room.lower(), ip, reserved=True
            )
            if room is not None:
                await self.handle_host_frames(room)
            return
        code = self.canonical_join(requested)
        if code is None:
            await ws.close(1008, "unknown room")
            return
        secret = await self.authorize_reclaim(ws, code)
        if secret is None:
            return
        room = await self.adopt_room(ws, code, ip, reserved=False, host_secret=secret)
        if room is not None:
            await self.handle_host_frames(room)

    async def authorize_reserved(self, ws: ServerConnection) -> bool:
        expected = self.config.reserved_token
        if not expected:
            log.warning("reserved room refused because PLAYALONG_DALTON_TOKEN is empty")
            await ws.close(1011, "reserved room disabled")
            return False
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=self.config.auth_timeout)
        except (asyncio.TimeoutError, ConnectionClosed):
            await _safe_close(ws, 4001, "unauthorized")
            return False
        token = None
        if isinstance(raw, str):
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                msg = None
            if isinstance(msg, dict) and msg.get("t") == "auth" and isinstance(msg.get("d"), str):
                token = msg.get("d")
        if not token_matches(token, expected):
            log.warning("reserved room auth failed")
            await _safe_close(ws, 4001, "unauthorized")
            return False
        return True

    async def authorize_reclaim(self, ws: ServerConnection, code: str) -> Optional[str]:
        """Return the room secret when this host may take the code back."""
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=self.config.auth_timeout)
        except (asyncio.TimeoutError, ConnectionClosed):
            await _safe_close(ws, 4001, "unauthorized")
            return None
        token = None
        if isinstance(raw, str):
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                msg = None
            if isinstance(msg, dict) and msg.get("t") == "auth" and isinstance(msg.get("d"), str):
                token = msg.get("d")
        async with self.rooms_lock:
            expected = self._secret_locked(code)
        if not token_matches(token, expected):
            log.warning("room reclaim failed code=%s", code)
            await _safe_close(ws, 4001, "unauthorized")
            return None
        return expected

    async def adopt_room(
        self,
        ws: ServerConnection,
        code: Optional[str],
        ip: str,
        reserved: bool,
        host_secret: Optional[str] = None,
    ) -> Optional[Room]:
        async with self.rooms_lock:
            if code is None:
                code = self._allocate_locked()
                host_secret = secrets.token_urlsafe(18)
            old = self.rooms.get(code)
            room = Room(code, ws)
            room.reserved = reserved
            room.host_secret = "" if reserved else (host_secret or "")
            # Not joinable until the host has the room frame, so a player cannot
            # emit "open" ahead of {"t":"room"}.
            self.rooms[code] = room
            self.reclaim.pop(code, None)
        if old is not None:
            await self.close_room(old, "host replaced")
        try:
            await room.send(room_frame(code, room.host_secret))
        except Exception:
            log.warning("room open failed code=%s", code)
            await self.close_room(room, "host send failed")
            return None
        async with room.lock:
            room.ready = True
        log.info("room open code=%s reserved=%s ip=%s", code, str(reserved).lower(), ip)
        return room

    def _allocate_locked(self) -> str:
        reserved = self.config.reserved_room.lower()
        for length in (4, 5):
            for _ in range(32):
                code = "".join(secrets.choice(ALPHABET) for _ in range(length))
                if code.lower() == reserved:
                    continue
                if code not in self.rooms and not self._secret_locked(code):
                    return code
        raise RuntimeError("room code space exhausted")

    def _secret_locked(self, code: str) -> str:
        room = self.rooms.get(code)
        if room is not None and room.host_secret:
            return room.host_secret
        held = self.reclaim.get(code)
        if not held:
            return ""
        secret, expiry = held
        if time.monotonic() > expiry:
            self.reclaim.pop(code, None)
            return ""
        return secret

    async def handle_host_frames(self, room: Room) -> None:
        try:
            async for message in room.ws:
                if not isinstance(message, str):
                    continue
                try:
                    env = json.loads(message)
                except json.JSONDecodeError:
                    continue
                if not isinstance(env, dict):
                    continue
                kind = env.get("t")
                cid = env.get("c")
                if not isinstance(cid, str) or not cid:
                    continue
                if kind == "msg":
                    payload = env.get("d")
                    if not isinstance(payload, str):
                        continue
                    await self.forward_to_player(room, cid, payload)
                elif kind == "close":
                    await self.close_player(room, cid, "closed")
        except ConnectionClosed:
            pass
        finally:
            await self.close_room(room, "host disconnected")

    async def handle_player(self, ws: ServerConnection, raw_code: str, ip: str) -> None:
        code = self.canonical_join(raw_code)
        if code is None:
            await self.reject_player(ws, "That room code is not valid.", "bad code")
            return
        async with self.rooms_lock:
            room = self.rooms.get(code)
            held = room is None and bool(self._secret_locked(code))
        if room is None:
            if held:
                # Host dropped but can still reclaim this code. Ask the client
                # to retry instead of ending the run with join_error.
                log.info("player wait room=%s reason=host reconnecting", code)
                await _safe_close(ws, 1013, "host reconnecting")
                return
            await self.reject_player(
                ws,
                "No Play Along room with that code is open.",
                "room not found",
            )
            return
        async with room.lock:
            if room.closed or not room.ready:
                room_gone = True
                waiting = not room.ready and not room.closed
                full = False
                player = None
            elif len(room.players) >= self.config.max_players:
                room_gone = False
                waiting = False
                full = True
                player = None
            else:
                room_gone = False
                waiting = False
                full = False
                cid = str(room.next_id)
                room.next_id += 1
                player = Player(cid, ws)
                room.players[cid] = player
        if room_gone:
            if waiting:
                await _safe_close(ws, 1013, "host reconnecting")
            else:
                await self.reject_player(
                    ws,
                    "No Play Along room with that code is open.",
                    "room not found",
                )
            return
        if full or player is None:
            await self.reject_player(ws, "Play Along lobby is full.", "room full")
            return
        try:
            await room.send(envelope("open", player.client_id))
        except Exception:
            await self.detach_player(room, player.client_id, notify_host=False)
            await _safe_close(ws, 1011, "host unavailable")
            return
        log.info("player join room=%s id=%s ip=%s", room.code, player.client_id, ip)
        try:
            async for message in ws:
                if not isinstance(message, str):
                    await _safe_close(ws, 1003, "text only")
                    break
                if len(message.encode("utf-8")) > self.config.max_player_message_bytes:
                    log.info("player oversize room=%s id=%s", room.code, player.client_id)
                    await _safe_close(ws, 1009, "message too large")
                    break
                decision = self.note_player_frame(player)
                if decision == "drop":
                    continue
                if decision == "close":
                    log.info("player rate limit room=%s id=%s", room.code, player.client_id)
                    await _safe_close(ws, 1008, "rate limited")
                    break
                try:
                    await room.send(envelope("msg", player.client_id, message))
                except ConnectionClosed:
                    await _safe_close(ws, 1011, "host unavailable")
                    break
        except ConnectionClosed:
            pass
        finally:
            await self.detach_player(room, player.client_id, notify_host=True)

    def note_player_frame(self, player: Player) -> str:
        """Return ok, drop, or close. Only ok frames are copied onto the host socket."""
        now = time.monotonic()
        if now - player.window_start >= 1.0:
            player.window_start = now
            player.window_count = 0
        player.window_count += 1
        if player.window_count > self.config.player_msg_burst:
            return "close"
        if player.window_count > self.config.player_msg_rate:
            return "drop"
        return "ok"

    def canonical_join(self, raw: str) -> Optional[str]:
        name = unquote(raw).strip()
        if not name or "/" in name or len(name) > 32:
            return None
        if name.lower() == self.config.reserved_room.lower():
            return self.config.reserved_room.lower()
        code = name.upper()
        if len(code) < 4 or len(code) > 5:
            return None
        if any(ch not in ALPHABET for ch in code):
            return None
        return code

    async def forward_to_player(self, room: Room, cid: str, payload: str) -> None:
        async with room.lock:
            if room.closed:
                return
            player = room.players.get(cid)
        if player is None:
            try:
                await room.send(envelope("close", cid))
            except Exception:
                pass
            return
        try:
            async with player.send_lock:
                await player.ws.send(payload)
        except Exception:
            await self.close_player(room, cid, "send failed")
            try:
                await room.send(envelope("close", cid))
            except Exception:
                pass

    async def close_player(self, room: Room, cid: str, reason: str) -> None:
        async with room.lock:
            player = room.players.pop(cid, None)
        if player is None:
            return
        log.info("player leave room=%s id=%s reason=%s", room.code, cid, reason)
        await _safe_close(player.ws, 1000, reason)

    async def detach_player(self, room: Room, cid: str, notify_host: bool) -> None:
        async with room.lock:
            player = room.players.pop(cid, None)
            closed = room.closed
        if player is None:
            return
        log.info("player leave room=%s id=%s reason=disconnect", room.code, cid)
        if notify_host and not closed:
            try:
                await room.send(envelope("close", cid))
            except Exception:
                pass

    async def close_room(self, room: Room, reason: str) -> None:
        async with self.rooms_lock:
            if self.rooms.get(room.code) is room:
                del self.rooms[room.code]
                if room.host_secret and not room.reserved and reason != "host send failed":
                    self.reclaim[room.code] = (
                        room.host_secret,
                        time.monotonic() + self.config.reclaim_grace,
                    )
        async with room.lock:
            if room.closed:
                return
            room.closed = True
            players = list(room.players.values())
            room.players.clear()
        log.info(
            "room close code=%s reason=%s players=%d",
            room.code,
            reason,
            len(players),
        )
        for player in players:
            await _safe_close(player.ws, 1001, "host disconnected")
        await _safe_close(room.ws, 1001, reason)

    async def reject_player(self, ws: ServerConnection, error: str, reason: str) -> None:
        log.info("player reject reason=%s", reason)
        try:
            await ws.send(json.dumps(
                {"type": "join_error", "error": error},
                separators=(",", ":"),
            ))
        except Exception:
            pass
        await _safe_close(ws, 1008, reason)


def client_ip(ws: ServerConnection) -> str:
    request = ws.request
    if request is not None:
        for key in ("CF-Connecting-IP", "X-Forwarded-For"):
            value = request.headers.get(key)
            if value:
                return value.split(",")[0].strip()
    addr = ws.remote_address
    if addr:
        return str(addr[0])
    return "?"


def route_path(raw: str) -> str:
    path = raw.split("?", 1)[0]
    if len(path) > 1 and path.endswith("/"):
        path = path[:-1]
    return path or "/"


async def _safe_close(ws: ServerConnection, code: int, reason: str) -> None:
    try:
        await ws.close(code, reason)
    except Exception:
        pass


def config_from_env(argv: Optional[list] = None) -> RelayConfig:
    parser = argparse.ArgumentParser(description="Play Along room-code relay")
    parser.add_argument(
        "--host",
        default=os.environ.get("PLAYALONG_RELAY_HOST", "127.0.0.1"),
        help="Bind address (default 127.0.0.1 or PLAYALONG_RELAY_HOST)",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=int(os.environ.get("PLAYALONG_RELAY_PORT", "8765")),
        help="Bind port (default 8765 or PLAYALONG_RELAY_PORT)",
    )
    parser.add_argument(
        "--reserved-room",
        default=os.environ.get("PLAYALONG_RESERVED_ROOM", "dalton"),
        help="Stable public room name (default dalton)",
    )
    parser.add_argument(
        "--token",
        default=os.environ.get("PLAYALONG_DALTON_TOKEN", ""),
        help="Secret required to claim the reserved room (or PLAYALONG_DALTON_TOKEN)",
    )
    parser.add_argument(
        "--max-players",
        type=int,
        default=int(os.environ.get("PLAYALONG_MAX_PLAYERS", "80")),
    )
    parser.add_argument(
        "--max-message-bytes",
        type=int,
        default=int(os.environ.get("PLAYALONG_MAX_MESSAGE_BYTES", str(128 * 1024))),
        help="Max incoming frame on the host link (world-state envelopes)",
    )
    parser.add_argument(
        "--max-player-bytes",
        type=int,
        default=int(os.environ.get("PLAYALONG_MAX_PLAYER_BYTES", "4096")),
        help="Max incoming player frame (Play Along packet limit)",
    )
    parser.add_argument(
        "--player-msg-rate",
        type=int,
        default=int(os.environ.get("PLAYALONG_PLAYER_MSG_RATE", "30")),
        help="Player frames forwarded per second before extras are dropped",
    )
    parser.add_argument(
        "--ping-interval",
        type=float,
        default=float(os.environ.get("PLAYALONG_PING_INTERVAL", "20")),
    )
    parser.add_argument(
        "--ping-timeout",
        type=float,
        default=float(os.environ.get("PLAYALONG_PING_TIMEOUT", "20")),
    )
    parser.add_argument(
        "--rate-limit",
        type=int,
        default=int(os.environ.get("PLAYALONG_RATE_LIMIT", "40")),
        help="New websocket connections allowed per IP per window",
    )
    parser.add_argument(
        "--rate-window",
        type=float,
        default=float(os.environ.get("PLAYALONG_RATE_WINDOW", "30")),
    )
    args = parser.parse_args(argv)
    return RelayConfig(
        host=args.host,
        port=args.port,
        reserved_room=args.reserved_room.strip() or "dalton",
        reserved_token=args.token,
        max_players=max(1, args.max_players),
        max_message_bytes=max(4096, args.max_message_bytes),
        max_player_message_bytes=max(256, min(args.max_player_bytes, args.max_message_bytes)),
        player_msg_rate=max(1, args.player_msg_rate),
        player_msg_burst=max(max(1, args.player_msg_rate), max(1, args.player_msg_rate) * 2),
        ping_interval=args.ping_interval,
        ping_timeout=args.ping_timeout,
        rate_limit=max(1, args.rate_limit),
        rate_window=max(1.0, args.rate_window),
    )


async def _serve_forever(config: RelayConfig) -> None:
    relay = Relay(config)
    server = await relay.start()
    sockets = list(server.sockets or [])
    bound = sockets[0].getsockname() if sockets else (config.host, config.port)
    log.info(
        "listening on %s:%s reserved_room=%s token_set=%s",
        bound[0],
        bound[1],
        config.reserved_room.lower(),
        "yes" if config.reserved_token else "no",
    )
    await server.serve_forever()


def main(argv: Optional[list] = None) -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )
    config = config_from_env(argv)
    try:
        asyncio.run(_serve_forever(config))
    except KeyboardInterrupt:
        log.info("shutting down")


if __name__ == "__main__":
    main()
