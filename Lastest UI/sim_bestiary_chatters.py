#!/usr/bin/env python3
"""Simulate ~30 chatters for bestiary HUD testing.

Bypasses the 60s !summon cooldown by calling apply_summon + /api/summon-march.
Still hits /api/chat-command for queries and intentional failures.

Usage (Flask + companion running):
  cd "Lastest UI"
  python sim_bestiary_chatters.py           # ~1.5 levels (Sewers + half Prison)
  python sim_bestiary_chatters.py --max     # grind to Halls (Lv 5)
"""
from __future__ import annotations

import argparse
import json
import random
import time
import urllib.error
import urllib.request

from summon_bestiary import apply_summon, get_state_payload, reset_bestiary_state

BASE = "http://127.0.0.1:5000"
# Pace so Godot can show bar / marches / banner (raise for slower demo).
SLEEP_SEC = 0.05
RNG = random.Random(42)

# Fallback pools if API omits unlocked list (mirrors bestiary_config.json).
ZONE_POOLS = {
    1: ["rat", "albino", "snake"],
    2: ["gnoll", "crab", "slime", "swarm", "thief"],
    3: ["skeleton", "dm100", "bat", "brute", "shaman", "spinner"],
    4: ["guard", "necromancer", "ghoul", "elemental", "warlock", "monk", "golem"],
    5: ["succubus", "eye", "scorpio"],
}

# Mixed lengths for truncation / hall-strip layout testing (short → Twitch-style long).
NAME_POOL = [
    "Jo",
    "Ash",
    "Bee",
    "Kai",
    "Rex",
    "Nova",
    "Pixel",
    "Mage",
    "Dungeon",
    "RatKing",
    "xXShadow",
    "Chatter7",
    "Chatter12",
    "PotatoFarmer",
    "ScrollOfIdentify",
    "VeryLongTwitchName_99",
    "a",
    "zz",
    "bob",
    "alice",
    "Streamer",
    "SPD_Fan",
    "sewer_rat",
    "PrisonBreak",
    "CavesExplorer",
    "CityGuard99",
    "HallsWalker",
    "i",
    "OK",
    "MaximilianTheSummoner",
]


def _post_json(path: str, body: dict) -> dict:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE}{path}",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=8) as resp:
        raw = resp.read().decode("utf-8")
        return json.loads(raw) if raw else {}


def _get_json(path: str) -> dict:
    with urllib.request.urlopen(f"{BASE}{path}", timeout=8) as resp:
        return json.loads(resp.read().decode("utf-8"))


def chat(username: str, message: str) -> dict:
    try:
        return _post_json("/api/chat-command", {"username": username, "message": message})
    except (urllib.error.URLError, urllib.error.HTTPError, OSError, json.JSONDecodeError) as e:
        return {"ok": False, "message": str(e)}


def summon_direct(username: str, monster: str) -> dict:
    """Apply XP + queue march (no cooldown)."""
    result = apply_summon(username, monster)
    if not result.get("ok"):
        return result
    try:
        _post_json(
            "/api/summon-march",
            {
                "username": username,
                "monster": monster,
                "layout": "horizontal",
                "xp": int(result.get("xp") or 0),
                "bestiary_level": int(result.get("level") or 1),
            },
        )
    except (urllib.error.URLError, urllib.error.HTTPError, OSError):
        result["march_queued"] = False
    else:
        result["march_queued"] = True
    return result


def log_chat(username: str, message: str) -> None:
    r = chat(username, message)
    ok = r.get("ok")
    raw = r.get("message")
    if raw is None:
        raw = r.get("extra")
    msg = str(raw if raw is not None else "")[:90]
    print(f"  chat {username:12} {message:22} -> ok={ok} {msg}")


def pick_user(weights: list[tuple[str, int]]) -> str:
    return RNG.choices(
        [n for n, _ in weights],
        weights=[w for _, w in weights],
        k=1,
    )[0]


def current_pool(payload: dict) -> list[str]:
    unlocked = list(payload.get("unlocked_monsters") or [])
    if unlocked:
        return [str(m).lower() for m in unlocked]
    lvl = int(payload.get("level") or 1)
    pool: list[str] = []
    for lv in range(1, lvl + 1):
        pool.extend(ZONE_POOLS.get(lv, []))
    return pool or ZONE_POOLS[1]


def grind_until(
    *,
    weights: list[tuple[str, int]],
    target_level: int,
    target_bar_xp: int | None,
    level_ups: int,
    max_summons: int,
    sleep_sec: float,
) -> int:
    """Summon until level >= target_level, and optionally bar_xp >= target_bar_xp."""
    guard = 0
    while guard < max_summons:
        p = get_state_payload()
        level = int(p.get("level") or 1)
        bar = int(p.get("bar_xp") or 0)
        if level > target_level:
            break
        if level == target_level:
            if target_bar_xp is None:
                break
            if bar >= target_bar_xp:
                break
        pool = current_pool(p)
        # Prefer newly unlocked (highest zone) so XP scales up a bit
        zone_mobs = ZONE_POOLS.get(level, pool)
        mob = RNG.choice(zone_mobs if zone_mobs else pool)
        user = pick_user(weights)
        r = summon_direct(user, mob)
        if r.get("leveled_up"):
            level_ups += 1
            lu = r.get("level_up") or {}
            print(
                f"  ** LEVEL UP #{level_ups}: {lu.get('from_zone')} -> {lu.get('to_zone')} "
                f"winner={lu.get('winner')} xp={lu.get('winner_xp')}"
            )
        elif not r.get("ok"):
            print(f"  fail {user} {mob}: {r.get('error')}")
        guard += 1
        if sleep_sec > 0:
            time.sleep(sleep_sec)
    return level_ups


def main() -> None:
    ap = argparse.ArgumentParser(description="Simulate bestiary chatters")
    ap.add_argument(
        "--max",
        action="store_true",
        help="Grind through all zones to Halls (Lv 5)",
    )
    ap.add_argument(
        "--sleep",
        type=float,
        default=SLEEP_SEC,
        help="Delay between summons (default %.2f)" % SLEEP_SEC,
    )
    args = ap.parse_args()
    sleep_sec = max(0.0, float(args.sleep))

    def pause() -> None:
        if sleep_sec > 0:
            time.sleep(sleep_sec)

    print("Reset session...")
    try:
        _post_json("/api/session/reset", {})
    except Exception:
        reset_bestiary_state()

    names = list(NAME_POOL)
    # Uneven activity: a few whales (incl. long names), mid, lurkers
    whale_names = {"RatKing", "ScrollOfIdentify", "MaximilianTheSummoner", "Chatter12"}
    mid_names = {"PotatoFarmer", "Dungeon", "Streamer", "PrisonBreak", "HallsWalker"}
    weights: list[tuple[str, int]] = []
    for n in names:
        if n in whale_names:
            weights.append((n, 8))
        elif n in mid_names:
            weights.append((n, 4))
        elif len(n) <= 3:
            weights.append((n, 2))
        else:
            weights.append((n, 1))

    locked_early = ["bat", "scorpio", "eye", "golem", "skeleton"]
    junk_cmds = [
        "!summon",
        "!summon potato",
        "!summonhall",
        "!bestiary",
        "!heat",
        "!topsummoner",
        "!mysummons",
        "!points",
        "!notacommand",
    ]

    print("\n--- Noise: queries + bad/locked summons (via chat-command) ---")
    for n in RNG.sample(names, 12):
        log_chat(n, RNG.choice(junk_cmds))
        pause()
    for n in RNG.sample(names, 10):
        mob = RNG.choice(locked_early)
        log_chat(n, f"!summon {mob}")
        pause()

    level_ups = 0

    if args.max:
        print("\n--- Grind to max (Halls / Lv 5) ---")
        # Cross Sewers→Prison→Caves→City→Halls (4 level-ups). Halls has no further bar.
        for next_lvl in (2, 3, 4, 5):
            print(f"\n  -> pushing toward Lv {next_lvl}...")
            level_ups = grind_until(
                weights=weights,
                target_level=next_lvl,
                target_bar_xp=None,
                level_ups=level_ups,
                max_summons=400,
                sleep_sec=sleep_sec,
            )
        # A few Halls summons so heat/sprint/chips look alive at the cap
        print("\n  -> Halls activity...")
        for _ in range(20):
            user = pick_user(weights)
            mob = RNG.choice(ZONE_POOLS[5])
            r = summon_direct(user, mob)
            if not r.get("ok"):
                print(f"  fail {user} {mob}: {r.get('error')}")
            pause()
        log_chat("Chatter7", "!summonhall")
        log_chat("Chatter3", "!bestiary")
        log_chat("Chatter12", "!topsummoner")
    else:
        print("\n--- Sewers grind (~1 level-up) with uneven summons ---")
        level_ups = grind_until(
            weights=weights,
            target_level=2,
            target_bar_xp=None,
            level_ups=level_ups,
            max_summons=120,
            sleep_sec=sleep_sec,
        )

        print("\n--- After Sewers: locked + query spam ---")
        for n in RNG.sample(names, 8):
            log_chat(n, f"!summon {RNG.choice(['scorpio', 'eye', 'succubus'])}")
            pause()
        log_chat("Chatter7", "!summonhall")
        log_chat("Chatter3", "!bestiary")
        log_chat("Chatter12", "!topsummoner")

        print("\n--- Prison half-bar (~0.5 more level) ---")
        level_ups = grind_until(
            weights=weights,
            target_level=2,
            target_bar_xp=50,
            level_ups=level_ups,
            max_summons=120,
            sleep_sec=sleep_sec,
        )

    print("\n--- Final payload ---")
    p = _get_json("/api/bestiary")
    print(
        json.dumps(
            {
                "level": p.get("level"),
                "zone": p.get("zone"),
                "bar_xp": p.get("bar_xp"),
                "bar_threshold": p.get("bar_threshold"),
                "bar_fraction": p.get("bar_fraction"),
                "sprint": p.get("sprint"),
                "heat": p.get("heat"),
                "hall_of_fame": p.get("hall_of_fame"),
                "unlocked_monsters": p.get("unlocked_monsters"),
                "level_ups_seen": level_ups,
            },
            indent=2,
        )
    )
    print("\nDone. Check companion HUD + !summonhall / !heat / !topsummoner.")


if __name__ == "__main__":
    main()
