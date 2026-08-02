#!/usr/bin/env python3
"""Simulate chatters for bestiary HUD testing.

Bypasses the 60s !summon cooldown by calling apply_summon + /api/summon-march.
Still hits /api/chat-command for queries and intentional failures.

Usage (Flask + companion running):
  cd "Lastest UI"
  python sim_bestiary_chatters.py                  # ~3.5 levels, 20-60 random names
  python sim_bestiary_chatters.py --levels 1.5     # Sewers + half Prison
  python sim_bestiary_chatters.py --max            # grind to Halls (Lv 5)
  python sim_bestiary_chatters.py --chatters 40 --sleep 0
"""
from __future__ import annotations

import argparse
import json
import random
import string
import time
import urllib.error
import urllib.request

from summon_bestiary import apply_summon, get_state_payload, load_config, reset_bestiary_state

BASE = "http://127.0.0.1:5000"
# Pace so Godot can show bar / marches / banner (0 = fastest grind).
SLEEP_SEC = 0.0
RNG = random.Random(42)

# Fallback pools if API omits unlocked list (mirrors bestiary_config.json).
ZONE_POOLS = {
    1: ["rat", "albino", "snake", "gnoll", "crab", "slime", "swarm", "thief"],
    2: ["skeleton", "dm100", "guard", "necromancer"],
    3: ["bat", "brute", "shaman", "spinner", "ghoul"],
    4: ["elemental", "warlock", "monk", "golem", "succubus"],
    5: ["eye", "scorpio"],
}

# Seed names (short → Twitch-length) mixed into generated chatter set.
NAME_SEED = [
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
    "qq",
    "Xy",
    "lootgoblin_x",
    "ItsMe_TheDungeonDelver2026",
]


def _rand_username(rng: random.Random) -> str:
    """Twitch-ish name with varied length (1–25)."""
    bucket = rng.choice(
        [1, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 12, 14, 16, 18, 20, 22, 25]
    )
    if bucket == 1:
        return rng.choice(list(string.ascii_lowercase))
    if bucket == 2:
        return rng.choice(["OK", "zz", "qq", "Xy", "Jo", "Ash", "ii", "xX"])

    style = rng.choice(("alpha", "alnum", "under", "camel", "leet"))
    if style == "alpha":
        return "".join(rng.choice(string.ascii_letters) for _ in range(bucket))
    if style == "alnum":
        chars = string.ascii_letters + string.digits
        return "".join(rng.choice(chars) for _ in range(bucket))
    if style == "under":
        left = max(1, bucket // 2)
        right = max(1, bucket - left - 1)
        return (
            "".join(rng.choice(string.ascii_lowercase) for _ in range(left))
            + "_"
            + "".join(rng.choice(string.ascii_lowercase + string.digits) for _ in range(right))
        )[:bucket]
    if style == "camel":
        parts: list[str] = []
        remain = bucket
        words = ["Loot", "Rat", "Scroll", "Dungeon", "Pixel", "Guard", "Mage", "Bee", "Nova"]
        while remain > 0:
            word = rng.choice(words)
            chunk = word[: min(remain, len(word))]
            parts.append(chunk)
            remain -= len(chunk)
        return "".join(parts)[:bucket]
    base = "".join(rng.choice("xXzZoO0_") for _ in range(max(3, bucket - 2)))
    return (base + str(rng.randint(0, 99)))[:bucket]


def build_chatter_names(count: int, rng: random.Random) -> list[str]:
    """20–60 names: seed pool + generated, unique, mixed lengths."""
    names: list[str] = []
    seen: set[str] = set()
    # Always include extremes for HUD truncation tests
    for n in ("i", "OK", "a", "MaximilianTheSummoner", "ItsMe_TheDungeonDelver2026"):
        if n.lower() not in seen:
            seen.add(n.lower())
            names.append(n)
    seed = list(NAME_SEED)
    rng.shuffle(seed)
    for n in seed:
        if len(names) >= count:
            break
        if n.lower() not in seen:
            seen.add(n.lower())
            names.append(n)
    guard = 0
    while len(names) < count and guard < count * 20:
        guard += 1
        n = _rand_username(rng)
        if not n or n.lower() in seen:
            continue
        seen.add(n.lower())
        names.append(n)
    rng.shuffle(names)
    return names[:count]


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
    """Apply XP + queue march (no cooldown). Retries on Windows state-file races."""
    result: dict = {}
    for attempt in range(6):
        try:
            result = apply_summon(username, monster)
            break
        except OSError:
            time.sleep(0.02 * (attempt + 1))
            result = {"ok": False, "error": "state_save_busy"}
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
    print(f"  chat {username[:24]:24} {message:22} -> ok={ok} {msg}")


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
    last_print = 0
    while guard < max_summons:
        p = get_state_payload()
        level = int(p.get("level") or 1)
        bar = int(p.get("bar_xp") or 0)
        thresh = int(p.get("bar_threshold") or 0)
        if level > target_level:
            break
        if level == target_level:
            if target_bar_xp is None:
                break
            if bar >= target_bar_xp:
                break
        pool = current_pool(p)
        # Prefer current zone mobs so XP matches unlocked chapter
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
        if guard - last_print >= 250:
            last_print = guard
            print(
                f"  … {guard} summons | Lv {level} {bar}/{thresh} "
                f"sprint={((p.get('sprint') or {}).get('username') or '-')}"
            )
        if sleep_sec > 0:
            time.sleep(sleep_sec)
    return level_ups


def _half_bar_target(level: int) -> int:
    cfg = load_config(force=True)
    for entry in cfg.get("levels") or []:
        if int(entry.get("level", 0)) == level:
            return max(1, int(entry.get("bar_threshold", 0) or 0) // 2)
    return 50


def main() -> None:
    ap = argparse.ArgumentParser(description="Simulate bestiary chatters")
    ap.add_argument(
        "--max",
        action="store_true",
        help="Grind through all zones to Halls (Lv 5)",
    )
    ap.add_argument(
        "--levels",
        type=float,
        default=3.5,
        help="Progress target in levels (default 3.5 = through Caves + half City)",
    )
    ap.add_argument(
        "--chatters",
        type=int,
        default=0,
        help="Chatter count (default: random 20-60)",
    )
    ap.add_argument(
        "--sleep",
        type=float,
        default=SLEEP_SEC,
        help="Delay between summons (default %.2f; use 0.05+ for HUD demo)" % SLEEP_SEC,
    )
    ap.add_argument(
        "--seed",
        type=int,
        default=42,
        help="RNG seed for names/summons",
    )
    args = ap.parse_args()
    sleep_sec = max(0.0, float(args.sleep))
    global RNG
    RNG = random.Random(int(args.seed))

    def pause() -> None:
        if sleep_sec > 0:
            time.sleep(sleep_sec)

    chatter_n = int(args.chatters) if args.chatters > 0 else RNG.randint(20, 60)
    chatter_n = max(5, min(120, chatter_n))
    names = build_chatter_names(chatter_n, RNG)
    lengths = sorted(len(n) for n in names)

    print("Reset session...")
    try:
        _post_json("/api/session/reset", {})
    except Exception:
        reset_bestiary_state()

    # Uneven activity: ~15% whales, ~25% mid, rest lurkers
    order = list(names)
    RNG.shuffle(order)
    n_whale = max(2, chatter_n // 7)
    n_mid = max(3, chatter_n // 4)
    whales = set(order[:n_whale])
    mids = set(order[n_whale : n_whale + n_mid])
    weights: list[tuple[str, int]] = []
    for n in names:
        if n in whales:
            weights.append((n, 8))
        elif n in mids:
            weights.append((n, 4))
        elif len(n) <= 3:
            weights.append((n, 2))
        else:
            weights.append((n, 1))

    print(
        f"\nChatters: {len(names)} (len {lengths[0]}-{lengths[-1]}, "
        f"median {lengths[len(lengths)//2]})"
    )
    print("  sample:", ", ".join(sorted(names, key=len)[:6]), "…",
          ", ".join(sorted(names, key=len)[-4:]))
    print(f"  whales ({len(whales)}):", ", ".join(list(whales)[:5]),
          ("…" if len(whales) > 5 else ""))

    locked_early = ["bat", "scorpio", "eye", "golem", "succubus"]
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
    for n in RNG.sample(names, min(12, len(names))):
        log_chat(n, RNG.choice(junk_cmds))
        pause()
    for n in RNG.sample(names, min(10, len(names))):
        mob = RNG.choice(locked_early)
        log_chat(n, f"!summon {mob}")
        pause()

    level_ups = 0
    # High thresholds need a large summon budget (~35k XP ≈ thousands of summons).
    max_summons = 25000

    if args.max:
        print("\n--- Grind to max (Halls / Lv 5) ---")
        for next_lvl in (2, 3, 4, 5):
            print(f"\n  -> pushing toward Lv {next_lvl}...")
            level_ups = grind_until(
                weights=weights,
                target_level=next_lvl,
                target_bar_xp=None,
                level_ups=level_ups,
                max_summons=max_summons,
                sleep_sec=sleep_sec,
            )
        print("\n  -> Halls activity...")
        for _ in range(20):
            user = pick_user(weights)
            mob = RNG.choice(ZONE_POOLS[5])
            r = summon_direct(user, mob)
            if not r.get("ok"):
                print(f"  fail {user} {mob}: {r.get('error')}")
            pause()
    else:
        # levels=3.5 → finish 3 zones (arrive Lv4) + half of City bar
        target = float(args.levels)
        whole = int(target)
        frac = target - whole
        # whole=3 means reach level 4 (3 level-ups); frac fills that level's bar
        arrive_level = whole + 1
        print(f"\n--- Grind to ~{target:g} levels (arrive Lv {arrive_level}"
              + (f", ~{frac:.0%} bar)" if frac > 0 else ")"))

        for next_lvl in range(2, arrive_level + 1):
            print(f"\n  -> pushing toward Lv {next_lvl}...")
            level_ups = grind_until(
                weights=weights,
                target_level=next_lvl,
                target_bar_xp=None,
                level_ups=level_ups,
                max_summons=max_summons,
                sleep_sec=sleep_sec,
            )

        if frac > 0:
            half = _half_bar_target(arrive_level)
            # Use fractional bar when not exactly 0.5
            cfg_thresh = half * 2
            target_bar = max(1, int(cfg_thresh * frac))
            print(f"\n  -> Lv {arrive_level} partial bar to {target_bar} XP...")
            level_ups = grind_until(
                weights=weights,
                target_level=arrive_level,
                target_bar_xp=target_bar,
                level_ups=level_ups,
                max_summons=max_summons,
                sleep_sec=sleep_sec,
            )

        print("\n--- Mid-run queries ---")
        for cmd_user, cmd in (
            (names[0], "!summonhall"),
            (names[min(1, len(names) - 1)], "!bestiary"),
            (names[min(2, len(names) - 1)], "!topsummoner"),
            (pick_user(weights), "!heat"),
            (pick_user(weights), "!mysummons"),
        ):
            log_chat(cmd_user, cmd)
            pause()

    print("\n--- Final payload ---")
    try:
        p = _get_json("/api/bestiary")
    except Exception:
        p = get_state_payload()
    print(
        json.dumps(
            {
                "chatters": len(names),
                "name_len_min_max": [lengths[0], lengths[-1]],
                "level": p.get("level"),
                "zone": p.get("zone"),
                "bar_xp": p.get("bar_xp"),
                "bar_threshold": p.get("bar_threshold"),
                "bar_fraction": p.get("bar_fraction"),
                "sprint": p.get("sprint"),
                "heat": p.get("heat"),
                "hall_of_fame": p.get("hall_of_fame"),
                "sprint_winners": p.get("sprint_winners"),
                "unlocked_monsters": p.get("unlocked_monsters"),
                "level_ups_seen": level_ups,
            },
            indent=2,
        )
    )
    print("\nDone. Check companion HUD + !summonhall / !heat / !topsummoner.")


if __name__ == "__main__":
    main()
