#!/usr/bin/env python3
"""
Spawn monster with points: deduct only on success.
Usage: python spawn_with_points.py <monster> <username>
- Checks points, attempts spawn, deducts only if spawn succeeds.
- Monsters cost HALF PRICE when spawned beyond their native biome (e.g. sewer mobs in prison+).
- Exit 0 always so Streamer.bot captures output; success = "ok", failure = error msg.
"""
import sys
import urllib.request
import json
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
POINTS_FILE = os.path.join(SCRIPT_DIR, "viewer_points.txt")
RESULT_FILE = os.path.join(SCRIPT_DIR, "spawn_result.txt")
GAME_DATA_URL = "http://127.0.0.1:5000/api/game-data"

# Earliest depth each monster appears (from MobSpawner). Half price when spawned beyond native biome.
NATIVE_DEPTH = {
    "rat": 1, "albino": 1, "snake": 1, "gnoll": 2, "crab": 3, "slime": 4,
    "swarm": 3, "thief": 4, "skeleton": 6, "dm100": 7, "guard": 7,
    "necromancer": 8, "bat": 9, "brute": 11, "shaman": 11, "spinner": 12,
    "ghoul": 14, "elemental": 16, "warlock": 16, "monk": 17, "golem": 18,
    "succubus": 19, "eye": 21, "scorpio": 23,
}

# Cost per monster (edit these). Default 100 for any not listed.
COST_PER_MONSTER = {
    "rat": 5, "albino": 10, "snake": 10, "gnoll": 10, "crab": 15,
    "slime": 15, "swarm": 15, "thief": 20, "skeleton": 20, "bat": 30,
    "brute": 30, "shaman": 35, "spinner": 25, "dm100": 20, "guard": 25,
    "necromancer": 25, "ghoul": 40, "elemental": 40, "warlock": 45,
    "monk": 50, "golem": 50, "succubus": 60, "eye": 70, "scorpio": 80,
}
DEFAULT_COST = 100

VALID = frozenset([
    "rat", "albino", "snake", "gnoll", "crab", "slime", "swarm", "thief",
    "skeleton", "bat", "brute", "shaman", "spinner", "dm100", "guard",
    "necromancer", "ghoul", "elemental", "warlock", "monk", "golem",
    "succubus", "eye", "scorpio"
])


def get_current_depth():
    """Fetch current dungeon depth from overlay server. Returns None if unavailable."""
    try:
        req = urllib.request.Request(GAME_DATA_URL)
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode())
            stats = data.get("stats") or {}
            d = stats.get("depth")
            return int(d) if d is not None else None
    except Exception:
        return None


def compute_cost(monster: str) -> int:
    """Base cost from COST_PER_MONSTER, halved if spawned beyond native biome."""
    base = COST_PER_MONSTER.get(monster, DEFAULT_COST)
    depth = get_current_depth()
    native = NATIVE_DEPTH.get(monster)
    if depth is not None and native is not None and depth > native:
        return max(1, base // 2)
    return base


def read_points():
    data = {}
    if os.path.exists(POINTS_FILE):
        for line in open(POINTS_FILE, encoding="utf-8"):
            parts = line.strip().split("|")
            if len(parts) >= 3:
                try:
                    data[parts[0].lower()] = (int(parts[1]), int(parts[2]))
                except ValueError:
                    pass
    return data


def write_points(data):
    lines = [f"{k}|{v[0]}|{v[1]}" for k, v in data.items()]
    with open(POINTS_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    monster = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
    username = (sys.argv[2] if len(sys.argv) > 2 else "").strip()
    def write_result(msg):
        with open(RESULT_FILE, "w", encoding="utf-8") as f:
            f.write(msg)

    if not monster or not username:
        write_result("Usage: !spawn <monster> (e.g. !spawn rat)")
        sys.exit(0)
    if monster not in VALID:
        write_result(f"Unknown monster: {monster}")
        sys.exit(0)

    cost = compute_cost(monster)
    data = read_points()
    key = username.lower()
    pts, last = data.get(key, (0, 0))
    if pts < cost:
        msg = f"Not enough points! Need {cost}, you have {pts}."
        write_result(msg)
        sys.exit(0)

    url = "http://127.0.0.1:5000/api/spawn-command"
    payload = {"monster": monster, "username": username}
    req = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"), method="POST"
    )
    req.add_header("Content-Type", "application/json")

    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            body = json.loads(resp.read().decode())
            if not body.get("ok"):
                err = body.get("error", "Spawn failed")
                write_result(err)  # Points NOT deducted when no space
                sys.exit(0)
    except urllib.error.HTTPError as e:
        write_result(e.read().decode())
        sys.exit(0)
    except Exception as e:
        write_result(str(e))
        sys.exit(0)

    pts -= cost  # Only deduct on success
    data[key] = (pts, last)
    write_points(data)
    write_result("ok")  # Success marker for Streamer.bot
    sys.exit(0)


if __name__ == "__main__":
    main()
