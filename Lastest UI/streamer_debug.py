#!/usr/bin/env python3
"""
Streamer-only debug commands (no points, no Streamer.bot).

Requires:
  - Game running with WebSocket streaming enabled (Settings → Streaming)
  - Overlay server: python server.py  (http://127.0.0.1:5000)

Usage:
  python streamer_debug.py heal-all
  python streamer_debug.py identify-all
  python streamer_debug.py reveal-map
  python streamer_debug.py goto-stairs-down
  python streamer_debug.py goto-stairs-up
  python streamer_debug.py give "Scroll of Upgrade x10"
  python streamer_debug.py search stylus
  python streamer_debug.py buff Haste 30
  python streamer_debug.py debuff Blindness
  python streamer_debug.py ping
  python streamer_debug.py help

Interactive give prompt: python streamer_give_prompt.py  (or streamdeck_give_item.bat)

Stream Deck (Instant Batch-PowerShell): see streamdeck-instant-batch.md for the
standard button template (OVERLAY_DIR + cd + python streamer_debug.py <cmd>).
"""
import json
import os
import sys
import urllib.error
import urllib.request
from typing import Dict, Optional, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RESULT_FILE = os.path.join(SCRIPT_DIR, "streamer_debug_result.txt")
BASE_URL = os.environ.get("SPD_OVERLAY_URL", "http://127.0.0.1:5000")

COMMANDS = {
    "heal-all": {
        "path": "/api/streamer-debug/heal-all",
        "desc": "Full HP, remove all debuffs, cleanse all curses (like a healing well)",
    },
    "identify-all": {
        "path": "/api/streamer-debug/identify-all",
        "desc": "Identify all bag and equipped items",
    },
    "reveal-map": {
        "path": "/api/streamer-debug/reveal-map",
        "desc": "Magic mapping for the current floor",
    },
    "goto-stairs-down": {
        "path": "/api/streamer-debug/goto-stairs-down",
        "desc": "Teleport to stairs down (floor exit)",
    },
    "goto-stairs-up": {
        "path": "/api/streamer-debug/goto-stairs-up",
        "desc": "Teleport to stairs up (floor entrance)",
    },
    "give": {
        "path": "/api/streamer-debug/give",
        "desc": 'Give item: give "Scroll of Upgrade x10" or give "Battle Axe +99"',
        "needs_args": True,
    },
    "ping": {
        "path": "/api/game-ping",
        "desc": "Check overlay ↔ game connection",
        "method": "GET",
    },
}


def _write_result(msg: str) -> None:
    with open(RESULT_FILE, "w", encoding="utf-8") as f:
        f.write(msg)


def _http_error_msg(exc: Exception, fallback: str) -> str:
    if isinstance(exc, urllib.error.HTTPError):
        try:
            body = json.loads(exc.read().decode("utf-8"))
            if body.get("error"):
                return str(body["error"])
        except Exception:
            pass
        return f"{fallback} (HTTP {exc.code})"
    if isinstance(exc, urllib.error.URLError):
        return f"{fallback}. Is server.py running on {BASE_URL}?"
    return f"{fallback}: {exc}"


def _call_give(spec: str) -> Tuple[bool, str]:
    from streamer_give_util import parse_give_line, post_give

    name, qty, lvl = parse_give_line(spec)
    if not name:
        return False, "Usage: give <item> e.g. give \"Scroll of Upgrade x10\""
    return post_give(name, qty, lvl)


def _call(path: str, method: str = "POST", body: Optional[Dict] = None) -> Tuple[bool, str]:
    url = BASE_URL.rstrip("/") + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return False, _http_error_msg(e, "Request failed")

    if path.endswith("/game-ping"):
        if payload.get("ok"):
            version = payload.get("version", "unknown")
            return True, f"Connected (game version: {version})"
        return False, payload.get("error", "Ping failed")

    if payload.get("ok"):
        detail = payload.get("detail", "ok")
        return True, str(detail)
    return False, payload.get("error", "Command failed")


def _print_help() -> None:
    print("Streamer debug commands (no points):\n")
    for name, info in COMMANDS.items():
        print(f"  {name:<12} {info['desc']}")
    print("  search       Find items and buffs (local + game if connected)")
    print("  list         Alias for search")
    print('  buff         Apply buff e.g. buff "Haste 30"')
    print('  debuff       Apply debuff e.g. debuff Hex')
    print(f"\nOverlay URL: {BASE_URL}")
    print(f"Result file: {RESULT_FILE}")


def main() -> None:
    args = [a.strip() for a in sys.argv[1:] if a.strip()]
    if not args or args[0].lower() in ("help", "-h", "--help"):
        _print_help()
        _write_result("Usage: python streamer_debug.py <command>")
        sys.exit(0)

    cmd = args[0].lower()

    if cmd in ("search", "list"):
        query = " ".join(args[1:]).strip()
        if not query:
            msg = "Usage: search <word> e.g. search stylus"
            print(msg)
            _write_result(msg)
            sys.exit(1)
        from streamer_give_util import search_items

        text = search_items(query)
        print(text)
        _write_result(text)
        sys.exit(0)

    if cmd not in COMMANDS:
        msg = f"Unknown command: {cmd}. Try: {', '.join(COMMANDS)}, search, buff, debuff"
        print(msg)
        _write_result(msg)
        sys.exit(1)

    if cmd == "give":
        spec = " ".join(args[1:]).strip()
        if not spec:
            msg = 'Usage: give "<item>" e.g. give "Scroll of Upgrade x10"'
            print(msg)
            _write_result(msg)
            sys.exit(1)
        ok, msg = _call_give(spec)
        if not ok:
            from streamer_give_util import parse_give_line, print_suggestions_for_failed_give

            name, _, _ = parse_give_line(spec)
            print_suggestions_for_failed_give(name)
    elif cmd == "buff":
        spec = " ".join(args[1:]).strip()
        if not spec:
            msg = 'Usage: buff <name> [turns] e.g. buff Haste 30'
            print(msg)
            _write_result(msg)
            sys.exit(1)
        from streamer_give_util import parse_buff_line, post_buff, print_suggestions_for_failed_buff

        name, dur = parse_buff_line(spec)
        ok, msg = post_buff(name, dur)
        if not ok:
            print_suggestions_for_failed_buff(name, debuff=False)
    elif cmd == "debuff":
        spec = " ".join(args[1:]).strip()
        if not spec:
            msg = 'Usage: debuff <name> [turns] e.g. debuff Blindness'
            print(msg)
            _write_result(msg)
            sys.exit(1)
        from streamer_give_util import parse_buff_line, post_debuff, print_suggestions_for_failed_buff

        name, dur = parse_buff_line(spec)
        ok, msg = post_debuff(name, dur)
        if not ok:
            print_suggestions_for_failed_buff(name, debuff=True)
    else:
        info = COMMANDS[cmd]
        method = info.get("method", "POST")
        ok, msg = _call(info["path"], method=method)
    _write_result(msg)
    print(msg)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
