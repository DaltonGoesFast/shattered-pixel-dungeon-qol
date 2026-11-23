#!/usr/bin/env python3
"""End-to-end check: one host, two players, plus the reserved dalton room.

Run from the repo root:

    python3 relay/test_playalong_relay.py
"""

from __future__ import annotations

import asyncio
import json
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from websockets.asyncio.client import connect
from websockets.exceptions import ConnectionClosed

import playalong_relay
from playalong_relay import Relay, RelayConfig


async def _recv_json(ws, timeout=2.0):
    raw = await asyncio.wait_for(ws.recv(), timeout)
    if not isinstance(raw, str):
        raise AssertionError("expected a text frame, got %r" % (type(raw),))
    return json.loads(raw)


async def _expect_closed(ws, timeout=2.0):
    try:
        await asyncio.wait_for(ws.recv(), timeout)
    except ConnectionClosed as exc:
        return exc
    raise AssertionError("connection stayed open")


def _http_health(port: int) -> str:
    with urllib.request.urlopen("http://127.0.0.1:%d/health" % port, timeout=2) as resp:
        body = resp.read().decode("utf-8")
        if resp.status != 200:
            raise AssertionError("health status %s" % resp.status)
        return body


async def _start(config: RelayConfig):
    relay = Relay(config)
    server = await relay.start()
    sock = next(iter(server.sockets))
    port = sock.getsockname()[1]
    return relay, server, port


async def test_host_and_two_players():
    config = RelayConfig(host="127.0.0.1", port=0, reserved_token="test-secret", max_players=2)
    _relay, server, port = await _start(config)
    base = "ws://127.0.0.1:%d" % port
    try:
        health = await asyncio.to_thread(_http_health, port)
        assert health.strip() == "ok", health

        host = await connect(base + "/host", open_timeout=2)
        room = await _recv_json(host)
        assert room["t"] == "room", room
        code = room["d"]
        assert isinstance(code, str) and len(code) == 4, code
        assert all(ch in playalong_relay.ALPHABET for ch in code), code
        assert "0" not in code and "O" not in code and "1" not in code and "I" not in code

        player_a = await connect(base + "/join/" + code.lower(), open_timeout=2)
        opened = await _recv_json(host)
        assert opened["t"] == "open", opened
        id_a = opened["c"]

        player_b = await connect(base + "/join/" + code, open_timeout=2)
        opened_b = await _recv_json(host)
        assert opened_b["t"] == "open", opened_b
        id_b = opened_b["c"]
        assert id_a != id_b

        wire_a = '{"type":"status_ping","protocolVersion":1}'
        wire_b = '{"type":"player_state","username":"Ada"}'
        await player_a.send(wire_a)
        await player_b.send(wire_b)

        forwarded = {}
        for _ in range(2):
            env = await _recv_json(host)
            assert env["t"] == "msg", env
            forwarded[env["c"]] = env["d"]
        assert forwarded[id_a] == wire_a
        assert forwarded[id_b] == wire_b

        reply_a = '{"type":"status_pong","session":{"active":true}}'
        reply_b = '{"type":"world_state","activePlayers":2}'
        await host.send(json.dumps({"t": "msg", "c": id_a, "d": reply_a}))
        await host.send(json.dumps({"t": "msg", "c": id_b, "d": reply_b}))
        got_a = await asyncio.wait_for(player_a.recv(), 2)
        got_b = await asyncio.wait_for(player_b.recv(), 2)
        assert got_a == reply_a
        assert got_b == reply_b
        # Player sockets carry the game protocol, not the host envelope.
        assert '"t"' not in got_a

        await host.send(json.dumps({"t": "close", "c": id_a}))
        closed_a = await _expect_closed(player_a)
        assert closed_a.rcvd is not None

        missing = await connect(base + "/join/ZZZZ", open_timeout=2)
        err = await _recv_json(missing)
        assert err["type"] == "join_error", err
        assert "not" in err["error"].lower() or "no " in err["error"].lower()
        await _expect_closed(missing)

        # Cap is 2. A is gone, B is still in, so one more seat, then full.
        player_c = await connect(base + "/join/" + code, open_timeout=2)
        opened_c = await _recv_json(host)
        assert opened_c["t"] == "open"
        player_d = await connect(base + "/join/" + code, open_timeout=2)
        full = await _recv_json(player_d)
        assert full["type"] == "join_error", full
        assert "full" in full["error"].lower()
        await _expect_closed(player_d)

        await host.close()
        await _expect_closed(player_b)
        await _expect_closed(player_c)
    finally:
        server.close()
        await server.wait_closed()


async def test_reserved_room():
    config = RelayConfig(
        host="127.0.0.1",
        port=0,
        reserved_room="dalton",
        reserved_token="s3cret-token",
        max_players=8,
    )
    _relay, server, port = await _start(config)
    base = "ws://127.0.0.1:%d" % port
    try:
        bad = await connect(base + "/host/dalton", open_timeout=2)
        await bad.send(json.dumps({"t": "auth", "d": "nope"}))
        closed = await _expect_closed(bad)
        assert closed.rcvd is not None and closed.rcvd.code == 4001

        # A bad claim must not create the room.
        ghost = await connect(base + "/join/DALTON", open_timeout=2)
        missing = await _recv_json(ghost)
        assert missing["type"] == "join_error"
        await _expect_closed(ghost)

        host = await connect(base + "/host/Dalton", open_timeout=2)
        await host.send(json.dumps({"t": "auth", "d": "s3cret-token"}))
        room = await _recv_json(host)
        assert room["t"] == "room" and room["d"] == "dalton", room

        # Wrong token does not kick the live host.
        attacker = await connect(base + "/host/dalton", open_timeout=2)
        await attacker.send(json.dumps({"t": "auth", "d": "other"}))
        await _expect_closed(attacker)
        player = await connect(base + "/join/dalton", open_timeout=2)
        opened = await _recv_json(host)
        assert opened["t"] == "open", opened
        await player.send('{"type":"status_ping"}')
        msg = await _recv_json(host)
        assert msg["t"] == "msg" and msg["d"] == '{"type":"status_ping"}'

        # A valid new claim replaces the host. Players of the old socket drop.
        host2 = await connect(base + "/host/dalton", open_timeout=2)
        await host2.send(json.dumps({"t": "auth", "d": "s3cret-token"}))
        room2 = await _recv_json(host2)
        assert room2["d"] == "dalton"
        await _expect_closed(host)
        await _expect_closed(player)

        player2 = await connect(base + "/join/dalton", open_timeout=2)
        opened2 = await _recv_json(host2)
        assert opened2["t"] == "open"
        await host2.send(json.dumps({
            "t": "msg",
            "c": opened2["c"],
            "d": '{"type":"status_pong","session":{"active":true,"inDungeon":true}}',
        }))
        pong = await asyncio.wait_for(player2.recv(), 2)
        assert '"status_pong"' in pong
        await host2.close()
        await _expect_closed(player2)
    finally:
        server.close()
        await server.wait_closed()


async def test_rate_limit():
    config = RelayConfig(
        host="127.0.0.1",
        port=0,
        reserved_token="t",
        rate_limit=2,
        rate_window=30,
    )
    _relay, server, port = await _start(config)
    base = "ws://127.0.0.1:%d" % port
    try:
        first = await connect(base + "/host", open_timeout=2)
        second = await connect(base + "/host", open_timeout=2)
        third = await connect(base + "/host", open_timeout=2)
        closed = await _expect_closed(third)
        assert closed.rcvd is not None and closed.rcvd.code == 1008
        await first.close()
        await second.close()
    finally:
        server.close()
        await server.wait_closed()


async def test_player_frame_limits():
    config = RelayConfig(
        host="127.0.0.1",
        port=0,
        reserved_token="t",
        max_player_message_bytes=64,
        player_msg_rate=2,
        player_msg_burst=4,
    )
    _relay, server, port = await _start(config)
    base = "ws://127.0.0.1:%d" % port
    try:
        host = await connect(base + "/host", open_timeout=2)
        room = await _recv_json(host)
        code = room["d"]

        bulky = await connect(base + "/join/" + code, open_timeout=2)
        opened = await _recv_json(host)
        assert opened["t"] == "open"
        try:
            await bulky.send("x" * 200)
        except ConnectionClosed:
            pass
        closed = await _expect_closed(bulky)
        assert closed.rcvd is not None and closed.rcvd.code == 1009
        # The player is dropped. The payload itself is not forwarded.
        notice = await _recv_json(host)
        assert notice["t"] == "close", notice

        fast = await connect(base + "/join/" + code, open_timeout=2)
        opened_fast = await _recv_json(host)
        assert opened_fast["t"] == "open"
        fast_id = opened_fast["c"]
        for i in range(8):
            await fast.send('{"type":"player_state","n":%d}' % i)
        forwarded = 0
        saw_close = False
        deadline = asyncio.get_event_loop().time() + 2
        while asyncio.get_event_loop().time() < deadline and not saw_close:
            try:
                env = await asyncio.wait_for(host.recv(), 0.4)
            except asyncio.TimeoutError:
                break
            env = json.loads(env)
            if env.get("t") == "msg" and env.get("c") == fast_id:
                forwarded += 1
            elif env.get("t") == "close" and env.get("c") == fast_id:
                saw_close = True
        assert forwarded == 2, forwarded
        assert saw_close
        await _expect_closed(fast)
        await host.close()
    finally:
        server.close()
        await server.wait_closed()


async def test_private_room_reclaim():
    config = RelayConfig(host="127.0.0.1", port=0, reserved_token="t")
    _relay, server, port = await _start(config)
    base = "ws://127.0.0.1:%d" % port
    try:
        host = await connect(base + "/host", open_timeout=2)
        room = await _recv_json(host)
        code = room["d"]
        secret = room["k"]
        assert isinstance(secret, str) and len(secret) > 8

        player = await connect(base + "/join/" + code, open_timeout=2)
        opened = await _recv_json(host)
        assert opened["t"] == "open"
        await host.close()
        await _expect_closed(player)

        # While the code is held, a retry is not a fatal join_error.
        retry = await connect(base + "/join/" + code, open_timeout=2)
        try:
            raw = await asyncio.wait_for(retry.recv(), 2)
            assert "join_error" not in raw
        except ConnectionClosed as exc:
            assert exc.rcvd is not None and exc.rcvd.code == 1013

        thief = await connect(base + "/host/" + code, open_timeout=2)
        await thief.send(json.dumps({"t": "auth", "d": "nope"}))
        denied = await _expect_closed(thief)
        assert denied.rcvd is not None and denied.rcvd.code == 4001

        host2 = await connect(base + "/host/" + code, open_timeout=2)
        await host2.send(json.dumps({"t": "auth", "d": secret}))
        again = await _recv_json(host2)
        assert again["d"] == code and again["k"] == secret

        player2 = await connect(base + "/join/" + code, open_timeout=2)
        opened2 = await _recv_json(host2)
        assert opened2["t"] == "open"
        await player2.send('{"type":"status_ping"}')
        msg = await _recv_json(host2)
        assert msg["t"] == "msg" and msg["c"] == opened2["c"]
        await host2.close()
        await _expect_closed(player2)
    finally:
        server.close()
        await server.wait_closed()


async def test_named_public_room():
    config = RelayConfig(host="127.0.0.1", port=0, reserved_token="t", max_players=8)
    relay, server, port = await _start(config)
    base = "ws://127.0.0.1:%d" % port
    try:
        bad = await connect(base + "/host/freddy", open_timeout=2)
        await bad.send(json.dumps({"t": "auth", "d": "nope-secret"}))
        # First claim binds; "bad" here is actually the first host — rename flow:
        room_bad = await _recv_json(bad)
        assert room_bad["t"] == "room" and room_bad["d"] == "freddy", room_bad
        assert "k" not in room_bad or not room_bad.get("k")
        await bad.close()

        # Claim survives host disconnect (process lifetime).
        assert "freddy" in relay.claims

        thief = await connect(base + "/host/freddy", open_timeout=2)
        await thief.send(json.dumps({"t": "auth", "d": "other-secret"}))
        denied = await _expect_closed(thief)
        assert denied.rcvd is not None and denied.rcvd.code == 4001

        host = await connect(base + "/host/Freddy", open_timeout=2)
        await host.send(json.dumps({"t": "auth", "d": "nope-secret"}))
        room = await _recv_json(host)
        assert room["t"] == "room" and room["d"] == "freddy", room

        player = await connect(base + "/join/freddy", open_timeout=2)
        opened = await _recv_json(host)
        assert opened["t"] == "open", opened
        await player.send('{"type":"status_ping"}')
        msg = await _recv_json(host)
        assert msg["t"] == "msg" and msg["d"] == '{"type":"status_ping"}'

        # Matching secret takeovers; old players drop.
        host2 = await connect(base + "/host/freddy", open_timeout=2)
        await host2.send(json.dumps({"t": "auth", "d": "nope-secret"}))
        room2 = await _recv_json(host2)
        assert room2["d"] == "freddy"
        await _expect_closed(host)
        await _expect_closed(player)

        player2 = await connect(base + "/join/FREDDY", open_timeout=2)
        opened2 = await _recv_json(host2)
        assert opened2["t"] == "open"
        await host2.close()
        await _expect_closed(player2)

        # While claimed but offline, joiners wait (not join_error).
        retry = await connect(base + "/join/freddy", open_timeout=2)
        try:
            raw = await asyncio.wait_for(retry.recv(), 2)
            assert "join_error" not in raw
        except ConnectionClosed as exc:
            assert exc.rcvd is not None and exc.rcvd.code == 1013
    finally:
        server.close()
        await server.wait_closed()


async def main():
    await test_host_and_two_players()
    await test_reserved_room()
    await test_rate_limit()
    await test_player_frame_limits()
    await test_private_room_reclaim()
    await test_named_public_room()
    print("playalong relay tests passed")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception:
        import traceback
        traceback.print_exc()
        sys.exit(1)
