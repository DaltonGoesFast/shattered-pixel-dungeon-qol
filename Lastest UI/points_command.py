#!/usr/bin/env python3
"""
Unified points command script for Streamer.bot.
Usage:
  spawn:    python points_command.py spawn <monster> <username>
  gold:     python points_command.py gold <amount> <username>
  curse:    python points_command.py curse <slot> <username>
  gas:      python points_command.py gas <username>
  scroll:   python points_command.py scroll <username>
  wand:     python points_command.py wand <common|uncommon|rare|veryrare> <username>  (tier required)
  superchat: python points_command.py superchat <microAmount> <currencyCode> <username>
  cheer:    python points_command.py cheer <bits> <username>

All spend commands write to spawn_result.txt (ok or ok|extra). Donation writes to donation_result.txt.
"""
import sys
import urllib.request
import json
import os
import time
from contextlib import contextmanager

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
POINTS_FILE = os.path.join(SCRIPT_DIR, "viewer_points.txt")
POINTS_LOCK_FILE = POINTS_FILE + ".lock"
POINTS_LOCK_TIMEOUT = 10.0  # seconds to wait for lock
SPAWN_RESULT_FILE = os.path.join(SCRIPT_DIR, "spawn_result.txt")
DONATION_RESULT_FILE = os.path.join(SCRIPT_DIR, "donation_result.txt")
CONFIG_FILE = os.path.join(SCRIPT_DIR, "points_config.json")
GAME_DATA_URL = "http://127.0.0.1:5000/api/game-data"

NATIVE_DEPTH = {
    "rat": 1, "albino": 1, "snake": 1, "gnoll": 2, "crab": 3, "slime": 4,
    "swarm": 3, "thief": 4, "skeleton": 6, "dm100": 7, "guard": 7,
    "necromancer": 8, "bat": 9, "brute": 11, "shaman": 11, "spinner": 12,
    "ghoul": 914, "elemental": 16, "warlock": 16, "monk": 17, "golem": 18,
    "succubus": 19, "eye": 21, "scorpio": 23,
}


def load_config():
    """Load costs from points_config.json. Falls back to defaults if missing/invalid."""
    defaults = {
        "cost_per_gold": 5,
        "cost_per_curse": 200,
        "cost_per_gas": 75,
        "cost_per_scroll": 100,
        "cost_per_wand_common": 50,
        "cost_per_wand_uncommon": 100,
        "cost_per_wand_rare": 200,
        "cost_per_wand_veryrare": 400,
        "default_monster_cost": 100,
        "cost_per_monster": {
            "rat": 5, "albino": 10, "snake": 10, "gnoll": 10, "crab": 15,
            "slime": 15, "swarm": 15, "thief": 20, "skeleton": 20, "bat": 30,
            "brute": 30, "shaman": 35, "spinner": 25, "dm100": 20, "guard": 25,
            "necromancer": 25, "ghoul": 940, "elemental": 40, "warlock": 45,
            "monk": 50, "golem": 50, "succubus": 60, "eye": 70, "scorpio": 80,
        },
    }
    if not os.path.exists(CONFIG_FILE):
        return defaults
    try:
        with open(CONFIG_FILE, encoding="utf-8") as f:
            cfg = json.load(f)
        monsters = dict(defaults["cost_per_monster"])
        for k, v in (cfg.get("cost_per_monster") or {}).items():
            try:
                monsters[k] = int(v)
            except (ValueError, TypeError):
                pass
        return {
            "cost_per_gold": int(cfg.get("cost_per_gold", defaults["cost_per_gold"])),
            "cost_per_curse": int(cfg.get("cost_per_curse", defaults["cost_per_curse"])),
            "cost_per_gas": int(cfg.get("cost_per_gas", defaults["cost_per_gas"])),
            "cost_per_scroll": int(cfg.get("cost_per_scroll", defaults["cost_per_scroll"])),
            "cost_per_wand_common": int(cfg.get("cost_per_wand_common", defaults["cost_per_wand_common"])),
            "cost_per_wand_uncommon": int(cfg.get("cost_per_wand_uncommon", defaults["cost_per_wand_uncommon"])),
            "cost_per_wand_rare": int(cfg.get("cost_per_wand_rare", defaults["cost_per_wand_rare"])),
            "cost_per_wand_veryrare": int(cfg.get("cost_per_wand_veryrare", defaults["cost_per_wand_veryrare"])),
            "default_monster_cost": int(cfg.get("default_monster_cost", defaults["default_monster_cost"])),
            "cost_per_monster": monsters,
        }
    except Exception:
        return defaults


def get_config():
    """Cached config (reloads each command to allow live edits)."""
    return load_config()
VALID_MONSTERS = frozenset([
    "rat", "albino", "snake", "gnoll", "crab", "slime", "swarm", "thief",
    "skeleton", "bat", "brute", "shaman", "spinner", "dm100", "guard",
    "necromancer", "ghoul", "elemental", "warlock", "monk", "golem",
    "succubus", "eye", "scorpio",
])

VALID_SLOTS = frozenset(["weapon", "armor", "ring", "artifact", "misc"])
SLOT_ALIASES = {"trinket": "misc", "middle": "misc"}
SLOT_HELP = "weapon, armor, ring, artifact, misc (middle slot)"

FALLBACK_RATES = {
    "USD": 1.0, "EUR": 1.08, "GBP": 1.27, "CAD": 0.74, "AUD": 0.65,
    "JPY": 0.0067, "MXN": 0.058, "BRL": 0.20, "INR": 0.012,
    "KRW": 0.00075, "CHF": 1.13, "PLN": 0.25, "SEK": 0.095,
}


def _acquire_points_lock():
    """Acquire exclusive lock on points file. Returns lock fd or None. Caller must call _release_points_lock."""
    start = time.monotonic()
    while (time.monotonic() - start) < POINTS_LOCK_TIMEOUT:
        try:
            fd = os.open(POINTS_LOCK_FILE, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, str(os.getpid()).encode())
            return fd
        except FileExistsError:
            time.sleep(0.05)
    return None


def _release_points_lock(fd):
    if fd is not None:
        try:
            os.close(fd)
        except OSError:
            pass
        try:
            os.remove(POINTS_LOCK_FILE)
        except OSError:
            pass


@contextmanager
def points_lock():
    """Context manager for exclusive access to viewer_points.txt. Use for any read-modify-write."""
    fd = _acquire_points_lock()
    if fd is None:
        raise TimeoutError("Could not acquire points file lock (another process may be using it)")
    try:
        yield
    finally:
        _release_points_lock(fd)


def read_points():
    data = {}
    if os.path.exists(POINTS_FILE):
        with open(POINTS_FILE, encoding="utf-8") as f:
            for line in f:
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


def get_current_depth():
    try:
        req = urllib.request.Request(GAME_DATA_URL)
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode())
            stats = data.get("stats") or {}
            d = stats.get("depth")
            return int(d) if d is not None else None
    except Exception:
        return None


def compute_spawn_cost(monster: str) -> int:
    cfg = get_config()
    base = cfg["cost_per_monster"].get(monster, cfg["default_monster_cost"])
    depth = get_current_depth()
    native = NATIVE_DEPTH.get(monster)
    if depth is not None and native is not None and depth > native:
        return max(1, base // 2)
    return base


def _http_error_msg(e, default_timeout: str) -> str:
    if e.code == 504:
        return default_timeout
    try:
        body = e.read().decode("utf-8", errors="replace")
        return json.loads(body).get("error", body) if body.strip().startswith("{") else body
    except Exception:
        return str(e)


def cmd_spawn(args):
    if len(args) < 2:
        return SPAWN_RESULT_FILE, "Usage: !spawn <monster> (e.g. !spawn rat)"
    monster = args[0].lower()
    username = args[1]
    if monster not in VALID_MONSTERS:
        return SPAWN_RESULT_FILE, f"Unknown monster: {monster}"

    cost = compute_spawn_cost(monster)
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last = data.get(key, (0, 0))
            if pts < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost}, you have {pts}."

            url = "http://127.0.0.1:5000/api/spawn-command"
            payload = {"monster": monster, "username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Spawn failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Spawn failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Spawn failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Spawn timed out. Is the game running and in an active run (not title screen)?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Spawn failed. " + (msg if msg else "Check overlay server and try again.")

            pts -= cost
            data[key] = (pts, last)
            write_points(data)
            return SPAWN_RESULT_FILE, "ok"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_gold(args):
    if len(args) < 2:
        return SPAWN_RESULT_FILE, "Usage: !gold <amount> (e.g. !gold 10)"
    try:
        amount = max(1, min(100, int(args[0])))
    except ValueError:
        return SPAWN_RESULT_FILE, "Usage: !gold <amount> (e.g. !gold 10). Amount must be 1-100."
    username = args[1]

    cost = amount * get_config()["cost_per_gold"]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last = data.get(key, (0, 0))
            if pts < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} for {amount} gold, you have {pts}."

            url = "http://127.0.0.1:5000/api/gold-command"
            payload = {"amount": amount, "username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Gold drop failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Gold drop failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Gold drop failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Gold drop timed out. Is the game running and in an active run (not title screen)?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Gold drop failed. " + (msg if msg else "Check overlay server and try again.")

            pts -= cost
            data[key] = (pts, last)
            write_points(data)
            return SPAWN_RESULT_FILE, "ok"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_curse(args):
    if len(args) < 2:
        return SPAWN_RESULT_FILE, f"Usage: !curse <slot>. Options: {SLOT_HELP}. Example: !curse weapon"
    slot_raw = args[0].lower()
    slot = SLOT_ALIASES.get(slot_raw, slot_raw)
    username = args[1]
    if slot not in VALID_SLOTS:
        return SPAWN_RESULT_FILE, f"Unknown slot \"{args[0]}\". Options: {SLOT_HELP}"

    cost = get_config()["cost_per_curse"]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last = data.get(key, (0, 0))
            if pts < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to curse, you have {pts}."

            url = "http://127.0.0.1:5000/api/curse-command"
            payload = {"slot": slot, "username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Curse failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Curse failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Curse failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Curse timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Curse failed. " + (msg if msg else "Check overlay server and try again.")

            pts -= cost
            data[key] = (pts, last)
            write_points(data)
            item_name = body.get("item_name", slot)
            return SPAWN_RESULT_FILE, f"ok|{item_name}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_gas(args):
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !gas (spawns random gas near you)"
    username = args[0]

    cost = get_config()["cost_per_gas"]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last = data.get(key, (0, 0))
            if pts < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to spew gas, you have {pts}."

            url = "http://127.0.0.1:5000/api/gas-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Gas spawn failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Gas spawn failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Gas spawn failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Gas command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Gas spawn failed. " + (msg if msg else "Check overlay server and try again.")

            pts -= cost
            data[key] = (pts, last)
            write_points(data)
            gas_name = body.get("gas_name", "gas")
            return SPAWN_RESULT_FILE, f"ok|{gas_name}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_scroll(args):
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !scroll (uses a random scroll like +10 Unstable Spellbook)"
    username = args[0]

    cost = get_config()["cost_per_scroll"]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last = data.get(key, (0, 0))
            if pts < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} for random scroll, you have {pts}."

            url = "http://127.0.0.1:5000/api/scroll-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Scroll command failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Scroll command failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Scroll command failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Scroll command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Scroll command failed. " + (msg if msg else "Check overlay server and try again.")

            pts -= cost
            data[key] = (pts, last)
            write_points(data)
            scroll_name = body.get("scroll_name", "scroll")
            return SPAWN_RESULT_FILE, f"ok|{scroll_name}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


WAND_TIERS = frozenset(["common", "uncommon", "rare", "veryrare", "very_rare"])


def _wand_cost_for_rarity(cfg, rarity):
    """Rarity: 0=common, 1=uncommon, 2=rare, 3=very_rare."""
    costs = [
        cfg.get("cost_per_wand_common", 50),
        cfg.get("cost_per_wand_uncommon", 100),
        cfg.get("cost_per_wand_rare", 200),
        cfg.get("cost_per_wand_veryrare", 400),
    ]
    return costs[min(rarity, 3)]


def _tier_to_int(tier_str):
    """Convert tier string to 0-3. Returns -1 for random/invalid."""
    if not tier_str:
        return -1
    m = {"common": 0, "uncommon": 1, "rare": 2, "veryrare": 3, "very_rare": 3}
    return m.get(tier_str.lower().strip(), -1)


def cmd_wand(args):
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !wand <common|uncommon|rare|veryrare> (tier required)"
    tier = -1
    if len(args) >= 2 and args[0].lower().strip() in WAND_TIERS:
        tier = _tier_to_int(args[0])
        username = args[1]
    else:
        username = args[0]
        return SPAWN_RESULT_FILE, "Specify a tier: !wand common, !wand uncommon, !wand rare, or !wand veryrare"

    cfg = get_config()
    if tier >= 0:
        cost = _wand_cost_for_rarity(cfg, tier)
        cost_check = cost
    else:
        cost_check = max(
            cfg.get("cost_per_wand_common", 50),
            cfg.get("cost_per_wand_uncommon", 100),
            cfg.get("cost_per_wand_rare", 200),
            cfg.get("cost_per_wand_veryrare", 400),
        )
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last = data.get(key, (0, 0))
            if pts < cost_check:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost_check}, you have {pts}."

            url = "http://127.0.0.1:5000/api/wand-command"
            payload = {"username": username}
            if tier >= 0:
                payload["tier"] = tier
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Wand command failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Wand command failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Wand command failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Wand command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Wand command failed. " + (msg if msg else "Check overlay server and try again.")

            rarity = body.get("rarity", 0)
            cost = _wand_cost_for_rarity(cfg, rarity)
            pts -= cost
            data[key] = (pts, last)
            write_points(data)
            effect_name = body.get("effect_name", "effect")
            return SPAWN_RESULT_FILE, f"ok|{effect_name}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def fetch_usd_rate(currency_code: str) -> float:
    code = currency_code.upper()[:3]
    if code == "USD":
        return 1.0
    if code in FALLBACK_RATES:
        return FALLBACK_RATES[code]
    try:
        url = f"https://api.frankfurter.dev/v1/latest?from={code}&to=USD"
        with urllib.request.urlopen(url, timeout=5) as resp:
            data = json.loads(resp.read().decode())
            return float(data.get("rates", {}).get("USD", FALLBACK_RATES.get(code, 1.0)))
    except Exception:
        return FALLBACK_RATES.get(code, 1.0)


def cmd_superchat(args):
    if len(args) < 4:
        return DONATION_RESULT_FILE, "invalid|0"
    try:
        micro_amount = int(args[1])
        currency = args[2].upper()[:3] or "USD"
    except (ValueError, IndexError):
        return DONATION_RESULT_FILE, "invalid|0"
    username = args[3]
    if not username or username.lower() == "anonymous":
        return DONATION_RESULT_FILE, "skip|0"

    key = username.lower()
    amount_in_currency = micro_amount / 1_000_000
    rate = fetch_usd_rate(currency)
    amount_usd = amount_in_currency * rate
    to_add = max(0, int(round(amount_usd * 100)))
    if to_add <= 0:
        return DONATION_RESULT_FILE, "ok|0"
    try:
        with points_lock():
            data = read_points()
            pts, last = data.get(key, (0, 0))
            pts += to_add
            data[key] = (pts, last)
            write_points(data)
        return DONATION_RESULT_FILE, f"ok|{to_add}"
    except TimeoutError:
        return DONATION_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_cheer(args):
    if len(args) < 2:
        return DONATION_RESULT_FILE, "invalid|0"
    try:
        bits = max(0, int(args[0]))
    except ValueError:
        return DONATION_RESULT_FILE, "invalid|0"
    username = args[1]
    if not username or username.lower() == "anonymous":
        return DONATION_RESULT_FILE, "skip|0"

    key = username.lower()
    to_add = bits
    if to_add <= 0:
        return DONATION_RESULT_FILE, "ok|0"
    try:
        with points_lock():
            data = read_points()
            pts, last = data.get(key, (0, 0))
            pts += to_add
            data[key] = (pts, last)
            write_points(data)
        return DONATION_RESULT_FILE, f"ok|{to_add}"
    except TimeoutError:
        return DONATION_RESULT_FILE, "Points file busy. Please try again in a moment."


COMMANDS = {
    "spawn": cmd_spawn,
    "gold": cmd_gold,
    "curse": cmd_curse,
    "gas": cmd_gas,
    "scroll": cmd_scroll,
    "wand": cmd_wand,
    "superchat": cmd_superchat,
    "cheer": cmd_cheer,
}


def main():
    args = [a.strip() for a in sys.argv[1:] if a.strip()]
    if len(args) < 1:
        with open(SPAWN_RESULT_FILE, "w", encoding="utf-8") as f:
            f.write("Usage: points_command.py <spawn|gold|curse|gas|scroll|wand|superchat|cheer> [args...]")
        sys.exit(0)

    cmd = args[0].lower()
    cmd_args = args[1:]
    if cmd not in COMMANDS:
        with open(SPAWN_RESULT_FILE, "w", encoding="utf-8") as f:
            f.write(f"Unknown command: {cmd}")
        sys.exit(0)

    result_file, msg = COMMANDS[cmd](cmd_args)
    with open(result_file, "w", encoding="utf-8") as f:
        f.write(msg)
    sys.exit(0)


if __name__ == "__main__":
    main()
