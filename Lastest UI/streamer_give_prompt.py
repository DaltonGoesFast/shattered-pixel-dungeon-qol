#!/usr/bin/env python3
"""
Interactive give-item prompt for streamer debug.

Examples:
  Scroll of Upgrade x10
  Battle Axe +99
  ScrollOfIdentify
  Gold x500

Requires game (streaming on, in run) + python server.py.
Stream Deck: run streamdeck_give_item.bat to open this in a CMD window.
"""
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RESULT_FILE = os.path.join(SCRIPT_DIR, "streamer_debug_result.txt")

from streamer_give_util import (
    parse_buff_line,
    parse_give_line,
    post_buff,
    post_debuff,
    post_give,
    print_suggestions_for_failed_buff,
    print_suggestions_for_failed_give,
    search_items,
)


def _write_result(msg: str) -> None:
    with open(RESULT_FILE, "w", encoding="utf-8") as f:
        f.write(msg)


def main() -> None:
    print("SPD streamer give-item")
    print("Items: Scroll of Upgrade x10 | Battle Axe +99 | arcane stylus")
    print("Buffs: buff Haste 30 | debuff Blindness | search haste")
    print("Commands: help | empty line to quit.\n")

    while True:
        try:
            line = input("Give> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not line:
            break
        if line.lower() in ("help", "?"):
            print("  Scroll of Upgrade x10  — item + quantity")
            print("  Battle Axe +99         — item + level")
            print("  buff Haste 30          — buff + turns (optional)")
            print("  debuff Hex             — debuff")
            print("  search haste           — find items/buffs")
            continue

        if line.lower().startswith("buff "):
            spec = line[5:].strip()
            name, dur = parse_buff_line(spec)
            if not name:
                print("Usage: buff <name> [turns]")
                continue
            ok, msg = post_buff(name, dur)
            _write_result(msg)
            print(("OK: " if ok else "ERR: ") + msg)
            if not ok:
                print_suggestions_for_failed_buff(name, debuff=False)
            continue

        if line.lower().startswith("debuff "):
            spec = line[7:].strip()
            name, dur = parse_buff_line(spec)
            if not name:
                print("Usage: debuff <name> [turns]")
                continue
            ok, msg = post_debuff(name, dur)
            _write_result(msg)
            print(("OK: " if ok else "ERR: ") + msg)
            if not ok:
                print_suggestions_for_failed_buff(name, debuff=True)
            continue

        if line.lower().startswith("search ") or line.lower().startswith("list "):
            query = line.split(None, 1)[1] if len(line.split(None, 1)) > 1 else ""
            if not query:
                print("Usage: search <word>")
                continue
            print(search_items(query))
            continue

        name, qty, lvl = parse_give_line(line)
        if not name:
            print("Enter an item name.")
            continue

        ok, msg = post_give(name, qty, lvl)
        _write_result(msg)
        print(("OK: " if ok else "ERR: ") + msg)
        if not ok:
            print_suggestions_for_failed_give(name)

    print("Bye.")


if __name__ == "__main__":
    main()
