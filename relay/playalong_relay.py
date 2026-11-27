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

* Host  ->  ws://127.0.0.1:8765/host/<name>
    Custom public room (slug ``[a-z][a-z0-9_-]{2,23}``, not ``dalton`` / blocked
    lookalikes). Same auth frame; the secret binds the name while the host is
    live and for ``reclaim_grace`` (default 2 minutes) after disconnect.
    Matching secret takeovers / reclaim; wrong secret is refused and does not
    kick the live host. After the reclaim window the name is free. Joiners use
    ``/join/<name>`` with no secret.

* Player -> ws://127.0.0.1:8765/join/<CODE-or-name>
    Unchanged Play Along JSON (``join``, ``status_ping``, ``player_state``, ...).
    The relay does not interpret those messages. Incoming player frames are
    capped at 4096 bytes (the game's packet limit) and at a small per-socket
    rate so one player cannot fill the shared host link. Host → player frames
    are queued per player (bounded). A slow player or a burst of snapshots
    cannot stall the host read loop, which would drop the room on ping timeout.

Host link multiplexing (host socket only)::

    {"t":"open","c":"<id>"}
    {"t":"msg","c":"<id>","d":"<raw player text>"}
    {"t":"close","c":"<id>"}
    {"t":"meta","d":{...}}   # optional lobby listing fields (no ``official``)

The host uses the same envelope to send ``msg`` and ``close`` back. ``d`` is a
string, so player bytes are forwarded unchanged. Meta ``d`` may be an object or
a JSON object string; the relay sanitizes and stores it for ``GET /rooms``.

Cloudflare terminates TLS. This process speaks plain WebSocket and binds
127.0.0.1 by default. Point the tunnel at http://localhost:8765.

GET /health returns plain text ``ok``.
GET /rooms returns a JSON array of live **public** rooms (named + reserved).
Private codes are never listed. ``official`` is set only by the relay for the
token-held reserved room. Responses are cached a few seconds; clients should
poll every 10–15s.
"""

from __future__ import annotations

import argparse
import asyncio
import hmac
import json
import logging
import os
import re
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
# Custom public room names (stored lowercase). Not the reserved dalton room.
PUBLIC_NAME_RE = re.compile(r"^[a-z][a-z0-9_-]{2,23}$")

# Blocked public slugs (plus anything that collapses to dalton*). Keep in sync
# with PlayAlongProtocol reserved / blocked host names on the game side.
BLOCKED_PUBLIC_NAMES = frozenset({
    "dalton",
    "daltongoesfast",
    "host",
    "streamer",
    "official",
    "admin",
    "moderator",
    "mod",
    "staff",
    "system",
    "server",
    "playalong",
    "spd",
})

# Host meta → /rooms. No host-supplied "official".
META_ALLOWED_KEYS = frozenset({
    "displayName",
    "version",
    "protocolVersion",
    "hostDepth",
    "activePlayers",
    "inDungeon",
    "challenges",
    "modifiers",
})
META_DISPLAY_NAME_MAX = 24
META_MAX_BYTES = 512
META_MIN_INTERVAL = 2.0
ROOMS_CACHE_SECONDS = 4.0
# Separate from websocket connect limiting: lobbies poll /rooms.
ROOMS_HTTP_RATE_LIMIT = 30
ROOMS_HTTP_RATE_WINDOW = 30.0

# Mirrors PlayAlongProtocol username blocklist (basic public-text filter).
_META_NAME_BLOCKLIST = (
    "nigger", "nigga", "faggot", "retard", "rape", "hitler", "nazi",
    "pedo", "paedo", "fuck", "shit", "cunt", "asshole", "bitch",
    "whore", "slut", "dick", "cock", "pussy", "porn", "sex",
    "kill yourself", "kys",
)


@dataclass
class RelayConfig:
    host: str = "127.0.0.1"
    port: int = 8765
    reserved_room: str = "dalton"
    reserved_token: str = ""
    max_players: int = 80
    # Host → relay frames. World-state includes every tombstone from the run,
    # so this stays well above the player cap. Player sockets override it.
    max_message_bytes: int = 8 * 1024 * 1024
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
    # How long a private/named room can be reclaimed after the host socket drops.
    reclaim_grace: float = 120.0
    rooms_cache_seconds: float = ROOMS_CACHE_SECONDS
    rooms_http_rate_limit: int = ROOMS_HTTP_RATE_LIMIT
    rooms_http_rate_window: float = ROOMS_HTTP_RATE_WINDOW
    meta_min_interval: float = META_MIN_INTERVAL
    meta_max_bytes: int = META_MAX_BYTES


# Host → player frames kept per socket. Older world snapshots are dropped
# first so a burst cannot pin the shared host reader. Count alone is not
# enough: the host link still accepts up to max_message_bytes (8 MiB), and a
# private /host needs no token. Bound bytes too, or one host can pin eight
# huge copies on every joiner that never reads.
PLAYER_OUTBOUND_MAX = 8
# Above the game's 48 KiB host→player snapshot. Larger d is dropped, not queued.
PLAYER_OUTBOUND_MAX_FRAME_BYTES = 64 * 1024
PLAYER_OUTBOUND_MAX_BYTES = 256 * 1024


class Player:
    def __init__(self, client_id: str, ws: ServerConnection) -> None:
        self.client_id = client_id
        self.ws = ws
        self.send_lock = asyncio.Lock()
        self.window_start = time.monotonic()
        self.window_count = 0
        self.outbound: Deque[str] = deque()
        self.outbound_bytes = 0
        self.draining = False

    def clear_outbound(self) -> None:
        self.outbound.clear()
        self.outbound_bytes = 0


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
        # Custom public name claimed with a host-chosen secret (not env-reserved).
        self.named = False
        # Sanitized lobby fields from host meta frames (never includes official).
        self.meta: Dict[str, object] = {}
        self.last_meta_at = 0.0

    async def send(self, text: str) -> None:
        async with self.send_lock:
            await self.ws.send(text)


class RateLimiter:
    def __init__(self, limit: int, window: float) -> None:
        self.limit = limit
        self.window = window
        self.events: Dict[str, Deque[float]] = {}
        self.lock = asyncio.Lock()

    def allow_sync(self, ip: str) -> bool:
        """Sync path for HTTP handlers (process_request)."""
        now = time.monotonic()
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

    async def allow(self, ip: str) -> bool:
        async with self.lock:
            return self.allow_sync(ip)


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


def collapse_public_name(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", (name or "").lower())


def is_blocked_public_name(name: str, reserved_room: str = "dalton") -> bool:
    """True for reserved/lookalike/blocked public slugs."""
    if not name:
        return True
    lower = name.lower()
    reserved = (reserved_room or "dalton").lower()
    if lower == reserved:
        return True
    collapsed = collapse_public_name(lower)
    if collapsed in BLOCKED_PUBLIC_NAMES or lower in BLOCKED_PUBLIC_NAMES:
        return True
    if collapsed == collapse_public_name(reserved) or "dalton" in collapsed:
        return True
    return False


def is_public_room_name(name: str, reserved_room: str = "dalton") -> bool:
    """True when name is a custom public slug (not reserved / blocked)."""
    if not name or is_blocked_public_name(name, reserved_room):
        return False
    return PUBLIC_NAME_RE.match(name) is not None


def is_private_room_code(code: str) -> bool:
    if not code or len(code) < 4 or len(code) > 5:
        return False
    return all(ch in ALPHABET for ch in code)


def sanitize_display_name(raw: object) -> str:
    if not isinstance(raw, str):
        return ""
    # Strip controls and collapse whitespace.
    cleaned = "".join(ch for ch in raw if ch.isprintable() and ch not in "\r\n\t")
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    if len(cleaned) > META_DISPLAY_NAME_MAX:
        cleaned = cleaned[:META_DISPLAY_NAME_MAX].rstrip()
    lower = cleaned.lower()
    for bad in _META_NAME_BLOCKLIST:
        if bad in lower:
            return ""
    return cleaned


def sanitize_host_meta(raw: object, max_bytes: int) -> Dict[str, object]:
    """Return a sanitized meta dict, or {} when invalid / oversized."""
    data = raw
    if isinstance(raw, str):
        if len(raw.encode("utf-8")) > max_bytes:
            return {}
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            return {}
    if not isinstance(data, dict):
        return {}
    try:
        encoded = json.dumps(data, separators=(",", ":"), ensure_ascii=False)
    except (TypeError, ValueError):
        return {}
    if len(encoded.encode("utf-8")) > max_bytes:
        return {}
    out: Dict[str, object] = {}
    for key, value in data.items():
        if key not in META_ALLOWED_KEYS:
            continue
        if key == "displayName":
            name = sanitize_display_name(value)
            if name:
                out[key] = name
            continue
        if key in ("version",):
            if isinstance(value, str):
                text = "".join(ch for ch in value if ch.isprintable())[:32].strip()
                if text:
                    out[key] = text
            continue
        if key == "protocolVersion":
            if isinstance(value, bool):
                continue
            if isinstance(value, int) and 0 <= value <= 999:
                out[key] = value
            elif isinstance(value, float) and value.is_integer() and 0 <= value <= 999:
                out[key] = int(value)
            continue
        if key in ("hostDepth", "activePlayers", "challenges", "modifiers"):
            if isinstance(value, bool):
                continue
            if isinstance(value, int) and -1 <= value <= 1_000_000:
                out[key] = value
            elif isinstance(value, float) and value.is_integer() and -1 <= value <= 1_000_000:
                out[key] = int(value)
            continue
        if key == "inDungeon":
            if isinstance(value, bool):
                out[key] = value
            continue
    return out


def request_client_ip(request) -> str:
    if request is not None:
        for key in ("CF-Connecting-IP", "X-Forwarded-For"):
            value = request.headers.get(key)
            if value:
                return value.split(",")[0].strip()
    return "?"


class Relay:
    def __init__(self, config: RelayConfig) -> None:
        self.config = config
        self.rooms: Dict[str, Room] = {}
        # code/name -> (host_secret, expiry monotonic). Private + named rooms.
        self.reclaim: Dict[str, tuple] = {}
        self.rooms_lock = asyncio.Lock()
        self.limiter = RateLimiter(config.rate_limit, config.rate_window)
        self.rooms_http_limiter = RateLimiter(
            config.rooms_http_rate_limit, config.rooms_http_rate_window
        )
        self._rooms_cache_body = "[]\n"
        self._rooms_cache_until = 0.0

    def process_request(self, connection: ServerConnection, request):
        path = route_path(request.path)
        if path == "/health" or path == "/health/":
            return connection.respond(200, "ok\n")
        if path == "/rooms" or path == "/rooms/":
            ip = request_client_ip(request)
            if not self.rooms_http_limiter.allow_sync(ip):
                return connection.respond(429, "rate limited\n")
            body = self.rooms_list_cached()
            # websockets 15 respond(status, text) — JSON body; clients poll slowly.
            return connection.respond(200, body)
        # Apply this before data frames are parsed so an oversized player
        # payload is rejected from its header instead of buffered.
        if path.startswith("/join/"):
            connection.protocol.max_size = self.config.max_player_message_bytes
        return None

    def rooms_list_cached(self) -> str:
        now = time.monotonic()
        if now < self._rooms_cache_until:
            return self._rooms_cache_body
        body = self.build_rooms_list_json()
        self._rooms_cache_body = body
        self._rooms_cache_until = now + max(0.5, self.config.rooms_cache_seconds)
        return body

    def invalidate_rooms_cache(self) -> None:
        self._rooms_cache_until = 0.0

    def build_rooms_list_json(self) -> str:
        """Public/named/reserved rooms with a live ready host. Never private codes."""
        listing = []
        # Snapshot values; mutations happen on the event loop.
        for room in list(self.rooms.values()):
            if room.closed or not room.ready:
                continue
            if not room.reserved and not room.named:
                continue
            players = len(room.players)
            entry = {
                "code": room.code,
                "official": bool(room.reserved),
                "players": players,
            }
            meta = room.meta or {}
            if "displayName" in meta:
                entry["displayName"] = meta["displayName"]
            if "version" in meta:
                entry["version"] = meta["version"]
            if "protocolVersion" in meta:
                entry["protocolVersion"] = meta["protocolVersion"]
            if "hostDepth" in meta:
                entry["hostDepth"] = meta["hostDepth"]
            if "inDungeon" in meta:
                entry["inDungeon"] = meta["inDungeon"]
            if "challenges" in meta:
                entry["challenges"] = meta["challenges"]
            if "modifiers" in meta:
                entry["modifiers"] = meta["modifiers"]
            # Prefer relay socket count over host-reported activePlayers.
            listing.append(entry)
        listing.sort(key=lambda item: (not item.get("official", False), item.get("code", "")))
        return json.dumps(listing, separators=(",", ":"), ensure_ascii=False) + "\n"

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
        lower = requested.strip().lower()
        upper = requested.strip().upper()
        if lower == self.config.reserved_room.lower():
            if not await self.authorize_reserved(ws):
                return
            room = await self.adopt_room(
                ws, self.config.reserved_room.lower(), ip, reserved=True
            )
            if room is not None:
                await self.handle_host_frames(room)
            return
        # 4–5 alphabet paths are private codes when that uppercase key is live
        # or in reclaim; otherwise a matching slug can still be a named claim.
        if is_private_room_code(upper):
            async with self.rooms_lock:
                live = self.rooms.get(upper)
                private_held = bool(self._secret_locked(upper))
                prefer_private = private_held or (
                    live is not None and not live.named and not live.reserved
                )
            if prefer_private or not is_public_room_name(lower, self.config.reserved_room):
                secret = await self.authorize_reclaim(ws, upper)
                if secret is None:
                    return
                room = await self.adopt_room(
                    ws, upper, ip, reserved=False, host_secret=secret
                )
            if room is not None:
                await self.handle_host_frames(room)
            return
        if is_public_room_name(lower, self.config.reserved_room):
            secret = await self.authorize_named_claim(ws, lower)
            if secret is None:
                return
            room = await self.adopt_room(
                ws, lower, ip, reserved=False, host_secret=secret, named=True
            )
            if room is not None:
                await self.handle_host_frames(room)
            return
        await ws.close(1008, "unknown room")

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
        token = await self._recv_auth_token(ws)
        if token is None:
            return None
        async with self.rooms_lock:
            expected = self._secret_locked(code)
        if not token_matches(token, expected):
            log.warning("room reclaim failed code=%s", code)
            await _safe_close(ws, 4001, "unauthorized")
            return None
        return expected

    async def authorize_named_claim(self, ws: ServerConnection, name: str) -> Optional[str]:
        """Bind or match a reclaim-window secret for a custom public name."""
        token = await self._recv_auth_token(ws)
        if token is None:
            return None
        if not token:
            log.warning("named room auth empty name=%s", name)
            await _safe_close(ws, 4001, "unauthorized")
            return None
        async with self.rooms_lock:
            live = self.rooms.get(name)
            if live is not None and not live.closed and live.named and live.host_secret:
                if token_matches(token, live.host_secret):
                    matched = live.host_secret
                else:
                    matched = None
            else:
                expected = self._secret_locked(name)
                if expected:
                    matched = expected if token_matches(token, expected) else None
                else:
                    # Name is free (or reclaim expired): first secret wins.
                    matched = token
        if matched is None:
            log.warning("named room auth failed name=%s", name)
            await _safe_close(ws, 4001, "unauthorized")
            return None
        return matched

    async def _recv_auth_token(self, ws: ServerConnection) -> Optional[str]:
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=self.config.auth_timeout)
        except (asyncio.TimeoutError, ConnectionClosed):
            await _safe_close(ws, 4001, "unauthorized")
            return None
        if isinstance(raw, str):
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                msg = None
            if isinstance(msg, dict) and msg.get("t") == "auth" and isinstance(msg.get("d"), str):
                return msg.get("d")
        await _safe_close(ws, 4001, "unauthorized")
        return None

    async def adopt_room(
        self,
        ws: ServerConnection,
        code: Optional[str],
        ip: str,
        reserved: bool,
        host_secret: Optional[str] = None,
        named: bool = False,
    ) -> Optional[Room]:
        async with self.rooms_lock:
            if code is None:
                code = self._allocate_locked()
                host_secret = secrets.token_urlsafe(18)
            old = self.rooms.get(code)
            room = Room(code, ws)
            room.reserved = reserved
            room.named = named
            # Reserved: token already proven; no reclaim secret on the wire.
            # Named + private: keep host_secret for the reclaim window.
            if reserved:
                room.host_secret = ""
            else:
                room.host_secret = host_secret or ""
            # Not joinable until the host has the room frame, so a player cannot
            # emit "open" ahead of {"t":"room"}.
            self.rooms[code] = room
            self.reclaim.pop(code, None)
            self.invalidate_rooms_cache()
        if old is not None:
            await self.close_room(old, "host replaced")
        # Private rooms get "k" for reclaim. Named hosts already know their
        # settings secret; still echo "k" so reconnect matches private UX.
        wire_secret = "" if reserved else room.host_secret
        try:
            await room.send(room_frame(code, wire_secret))
        except Exception:
            log.warning("room open failed code=%s", code)
            await self.close_room(room, "host send failed")
            return None
        async with room.lock:
            room.ready = True
        self.invalidate_rooms_cache()
        log.info(
            "room open code=%s reserved=%s named=%s ip=%s",
            code,
            str(reserved).lower(),
            str(named).lower(),
            ip,
        )
        return room

    def _allocate_locked(self) -> str:
        reserved = self.config.reserved_room.lower()
        for length in (4, 5):
            for _ in range(32):
                code = "".join(secrets.choice(ALPHABET) for _ in range(length))
                if code.lower() == reserved:
                    continue
                if is_blocked_public_name(code.lower(), self.config.reserved_room):
                    continue
                if code.lower() in self.rooms:
                    continue
                if code not in self.rooms and not self._secret_locked(code):
                    return code
        raise RuntimeError("room code space exhausted")

    async def holding_for_reclaim(self, code: str) -> bool:
        async with self.rooms_lock:
            current = self.rooms.get(code)
            if current is not None and not current.closed:
                return True
            return bool(self._secret_locked(code))

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

    def resolve_join_key_locked(self, raw: str) -> Optional[str]:
        """Map a /join path to a room key. Caller holds rooms_lock when possible."""
        name = unquote(raw).strip()
        if not name or "/" in name or len(name) > 32:
            return None
        lower = name.lower()
        reserved = self.config.reserved_room.lower()
        if lower == reserved:
            return reserved
        upper = name.upper()
        private_shape = is_private_room_code(upper)
        public_shape = is_public_room_name(lower, self.config.reserved_room)
        if public_shape and lower in self.rooms:
            return lower
        if private_shape and upper in self.rooms:
            return upper
        if public_shape and self._secret_locked(lower):
            return lower
        if private_shape and self._secret_locked(upper):
            return upper
        # Offline: keep lowercase private-code joins working (code.lower()).
        if private_shape:
            return upper
        if public_shape:
            return lower
        return None

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
                if kind == "meta":
                    self.apply_host_meta(room, env.get("d"))
                    continue
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

    def apply_host_meta(self, room: Room, raw: object) -> None:
        if room.closed:
            return
        now = time.monotonic()
        if now - room.last_meta_at < self.config.meta_min_interval:
            return
        cleaned = sanitize_host_meta(raw, self.config.meta_max_bytes)
        if not cleaned:
            return
        # Hosts cannot claim official via meta; ignore if smuggled somehow.
        cleaned.pop("official", None)
        room.last_meta_at = now
        room.meta = cleaned
        self.invalidate_rooms_cache()

    async def handle_player(self, ws: ServerConnection, raw_code: str, ip: str) -> None:
        async with self.rooms_lock:
            code = self.resolve_join_key_locked(raw_code)
            if code is None:
                room = None
                held = False
            else:
                room = self.rooms.get(code)
                held = room is None and bool(self._secret_locked(code))
        if code is None:
            await self.reject_player(ws, "That room code is not valid.", "bad code")
            return
        if room is None:
            if held:
                # Host dropped but can still reclaim this code/name. Ask the
                # client to retry instead of ending the run with join_error.
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
            # A join can observe the old Room after close_room has already
            # parked its secret. That is a retry, not a dead room.
            if waiting or await self.holding_for_reclaim(code):
                log.info("player wait room=%s reason=host reconnecting", code)
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
        """Stateless parse for tests / callers that do not need live lookup."""
        name = unquote(raw).strip()
        if not name or "/" in name or len(name) > 32:
            return None
        lower = name.lower()
        if lower == self.config.reserved_room.lower():
            return self.config.reserved_room.lower()
        if is_public_room_name(lower, self.config.reserved_room):
            # Prefer private-code shape when both match so code.lower() joins work
            # offline; live named rooms are resolved via resolve_join_key_locked.
            upper = name.upper()
            if is_private_room_code(upper):
                return upper
            return lower
        upper = name.upper()
        if is_private_room_code(upper):
            return upper
        return None

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
        # Do not await the player socket here. handle_host_frames is the only
        # reader on the host link; a blocked send stops ping/pong and the
        # relay then closes the whole room.
        self._enqueue_outbound(room, player, payload)

    def _enqueue_outbound(self, room: Room, player: Player, payload: str) -> None:
        size = _utf8_len(payload)
        if size > PLAYER_OUTBOUND_MAX_FRAME_BYTES:
            log.info(
                "drop oversize host frame room=%s id=%s bytes=%s",
                room.code,
                player.client_id,
                size,
            )
            return
        while player.outbound and (
            len(player.outbound) >= PLAYER_OUTBOUND_MAX
            or player.outbound_bytes + size > PLAYER_OUTBOUND_MAX_BYTES
        ):
            if not _drop_queued_world_state(player):
                _discard_outbound(player, 0)
        player.outbound.append(payload)
        player.outbound_bytes += size
        if not player.draining:
            player.draining = True
            asyncio.create_task(self._drain_player(room, player))

    async def _drain_player(self, room: Room, player: Player) -> None:
        try:
            while True:
                async with room.lock:
                    if room.closed or room.players.get(player.client_id) is not player:
                        player.clear_outbound()
                        player.draining = False
                        return
                    if not player.outbound:
                        player.draining = False
                        if player.outbound:
                            player.draining = True
                            continue
                        return
                    payload = _pop_outbound(player)
                try:
                    async with player.send_lock:
                        await player.ws.send(payload)
                except Exception:
                    player.clear_outbound()
                    player.draining = False
                    await self.close_player(room, player.client_id, "send failed")
                    try:
                        await room.send(envelope("close", player.client_id))
                    except Exception:
                        pass
                    return
        except Exception:
            player.draining = False

    async def close_player(self, room: Room, cid: str, reason: str) -> None:
        async with room.lock:
            player = room.players.pop(cid, None)
        if player is None:
            return
        player.clear_outbound()
        log.info("player leave room=%s id=%s reason=%s", room.code, cid, reason)
        await _safe_close(player.ws, 1000, reason)

    async def detach_player(self, room: Room, cid: str, notify_host: bool) -> None:
        async with room.lock:
            player = room.players.pop(cid, None)
            closed = room.closed
        if player is None:
            return
        player.clear_outbound()
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
                # Private + named: reclaim window with host secret.
                # Reserved dalton uses token auth on every connect (no reclaim).
                if (
                    room.host_secret
                    and not room.reserved
                    and reason != "host send failed"
                ):
                    self.reclaim[room.code] = (
                        room.host_secret,
                        time.monotonic() + self.config.reclaim_grace,
                    )
                self.invalidate_rooms_cache()
        async with room.lock:
            if room.closed:
                return
            room.closed = True
            room.meta = {}
            players = list(room.players.values())
            room.players.clear()
        log.info(
            "room close code=%s reason=%s players=%d",
            room.code,
            reason,
            len(players),
        )
        for player in players:
            player.clear_outbound()
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


def _utf8_len(text: str) -> int:
    return len(text.encode("utf-8"))


def _account_outbound(player: Player, dropped: str) -> None:
    player.outbound_bytes -= _utf8_len(dropped)
    if player.outbound_bytes < 0:
        player.outbound_bytes = 0


def _pop_outbound(player: Player) -> str:
    payload = player.outbound.popleft()
    _account_outbound(player, payload)
    return payload


def _discard_outbound(player: Player, index: int) -> None:
    dropped = player.outbound[index]
    del player.outbound[index]
    _account_outbound(player, dropped)


def _drop_queued_world_state(player: Player) -> bool:
    """Drop one stale snapshot so chat batches survive a full queue."""
    for i, queued in enumerate(player.outbound):
        if '"type":"world_state"' in queued[:64]:
            _discard_outbound(player, i)
            return True
    return False


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
        default=int(os.environ.get("PLAYALONG_MAX_MESSAGE_BYTES", str(8 * 1024 * 1024))),
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
