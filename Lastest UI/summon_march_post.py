#!/usr/bin/env python3
"""Summon march helper for Streamer.bot (same pattern as points_command.py).

Usage:
  python summon_march_post.py summon <username> [monster]
  python summon_march_post.py count <username>
  python summon_march_post.py reset

Writes summon_result.txt:
  ok|<monster>     on success
  <error message>  on failure

On success also updates summon_session_counts.json, top_summoner.txt, totalsummons.txt.
"""
import json
import os
import random
import sys
import urllib.error
import urllib.request

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RESULT_FILE = os.path.join(SCRIPT_DIR, "summon_result.txt")
COUNTS_FILE = os.path.join(SCRIPT_DIR, "summon_session_counts.json")
TOP_SUMMONER_FILE = os.path.join(SCRIPT_DIR, "top_summoner.txt")
TOTAL_SUMMONS_FILE = os.path.join(SCRIPT_DIR, "totalsummons.txt")
URL = "http://127.0.0.1:5000/api/summon-march"

MONSTER_POOL = [
    "rat", "albino", "snake", "gnoll", "crab", "slime", "swarm", "thief",
    "skeleton", "bat", "brute", "shaman", "spinner", "dm100", "guard",
    "necromancer", "ghoul", "elemental", "warlock", "monk", "golem",
    "succubus", "eye", "scorpio",
]


def write_result(text):
    with open(RESULT_FILE, "w", encoding="utf-8") as f:
        f.write(text)


def resolve_monster(raw):
    """Resolve monster via bestiary unlocked pool when available."""
    try:
        from summon_bestiary import resolve_monster as bestiary_resolve
        return bestiary_resolve(raw)
    except Exception:
        pass
    raw = (raw or "").strip().lower()
    if not raw:
        return random.choice(MONSTER_POOL), None
    if raw in MONSTER_POOL:
        return raw, None
    return None, "Unknown monster. Try: rat, gnoll, bat, brute, …"


def post_summon(username, monster, xp=0, bestiary_level=1):
    payload = json.dumps({
        "username": username,
        "monster": monster,
        "layout": "horizontal",
        "xp": xp,
        "bestiary_level": bestiary_level,
    }).encode("utf-8")
    req = urllib.request.Request(
        URL,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=5) as resp:
        if not (200 <= resp.status < 300):
            raise urllib.error.HTTPError(URL, resp.status, "", resp.headers, None)


def update_leaderboard(username):
    """Legacy no-op wrapper: bestiary apply_summon owns leaderboards."""
    try:
        from summon_bestiary import get_heat_leader
        user, score = get_heat_leader()
        with open(TOP_SUMMONER_FILE, "w", encoding="utf-8") as f:
            if user:
                f.write(f"Top Summoner: {user} - {score}\n")
            else:
                f.write("")
    except Exception:
        pass


def cmd_summon(argv):
    if len(argv) < 1:
        write_result("Usage: summon <username> [monster]")
        return 1
    username = argv[0].strip()
    if not username:
        write_result("Missing username")
        return 1
    monster_arg = argv[1] if len(argv) > 1 else ""
    monster, err = resolve_monster(monster_arg)
    if err:
        write_result(err)
        return 1
    try:
        from summon_bestiary import apply_summon
        bestiary = apply_summon(username, monster)
        if not bestiary.get("ok"):
            write_result(bestiary.get("error") or "Summon failed")
            return 1
        xp = int(bestiary.get("xp", 0) or 0)
        level = int(bestiary.get("level", 1) or 1)
        post_summon(username, monster, xp=xp, bestiary_level=level)
        write_result(f"ok|{monster}")
        return 0
    except urllib.error.HTTPError:
        write_result("Summon failed — invalid monster or server error")
        return 1
    except (urllib.error.URLError, OSError):
        write_result("Summon failed — is the overlay server running?")
        return 1


def cmd_reset(argv):
    for path in (COUNTS_FILE, TOP_SUMMONER_FILE, TOTAL_SUMMONS_FILE, RESULT_FILE,
                 os.path.join(SCRIPT_DIR, "heat_leader.txt")):
        try:
            if os.path.exists(path):
                os.remove(path)
        except OSError:
            pass
    try:
        from summon_bestiary import reset_bestiary_state
        reset_bestiary_state()
    except Exception:
        pass
    return 0


def cmd_count(argv):
    if len(argv) < 1:
        write_result("count|0")
        return 1
    username = argv[0].strip()
    counts = {}
    if os.path.exists(COUNTS_FILE):
        try:
            with open(COUNTS_FILE, encoding="utf-8") as f:
                counts = json.load(f)
        except (json.JSONDecodeError, OSError):
            counts = {}
    # case-insensitive lookup
    count = 0
    for name, n in counts.items():
        if name.lower() == username.lower():
            count = n
            break
    write_result(f"count|{count}")
    return 0


def main():
    if len(sys.argv) < 2:
        write_result("Usage: summon_march_post.py summon|count …")
        return 1
    sub = sys.argv[1].lower()
    if sub == "summon":
        return cmd_summon(sys.argv[2:])
    if sub == "count":
        return cmd_count(sys.argv[2:])
    if sub == "reset":
        return cmd_reset(sys.argv[2:])
    write_result(f"Unknown subcommand: {sub}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
