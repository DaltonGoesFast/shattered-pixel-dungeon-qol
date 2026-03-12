#!/usr/bin/env python3
"""
Unified points command script for Streamer.bot.
Usage:
  spawn:    python points_command.py spawn <monster> <username>
  champion: python points_command.py champion <monster> <username>  (2× base cost, random champion type)
  gold:     python points_command.py gold <amount> <username>
  curse:    python points_command.py curse <username>  (picks random slot)
  gas:      python points_command.py gas <username>
  scroll:   python points_command.py scroll <username>
  trap:     python points_command.py trap <username>
  transmute: python points_command.py transmute <username>
  bee:       python points_command.py bee <username>  (summon allied bee, 75 pts, 50 turns)
  ward:      python points_command.py ward <username>  (summon ward, 30 pts, scales with depth)
  buff:     python points_command.py buff <username>
  debuff:   python points_command.py debuff <username>
  wand:     python points_command.py wand <common|uncommon|rare|veryrare> <username>  (tier required)
  superchat: python points_command.py superchat <microAmount> <currencyCode> <username>
  cheer:    python points_command.py cheer <bits> <username>

All spend commands write to spawn_result.txt (ok or ok|extra|pts). The last value is remaining points. Donation writes to donation_result.txt.
"""
import sys
import urllib.request
import json
import os
import time
import random
import datetime
from contextlib import contextmanager

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
POINTS_FILE = os.path.join(SCRIPT_DIR, "viewer_points.txt")
POINTS_LOCK_FILE = POINTS_FILE + ".lock"
POINTS_LOCK_TIMEOUT = 10.0  # seconds to wait for lock
SPAWN_RESULT_FILE = os.path.join(SCRIPT_DIR, "spawn_result.txt")
DONATION_RESULT_FILE = os.path.join(SCRIPT_DIR, "donation_result.txt")
CONFIG_FILE = os.path.join(SCRIPT_DIR, "points_config.json")
FREE_UNTIL_FILE = os.path.join(SCRIPT_DIR, "free_until.json")
SPEND_DISABLED_FILE = os.path.join(SCRIPT_DIR, "spend_disabled.txt")
HELPERS_HURTERS_DISABLED_FILE = os.path.join(SCRIPT_DIR, "helpers_hurters_disabled.txt")
GAME_DATA_URL = "http://127.0.0.1:5000/api/game-data"
DOUBLE_POINTS_END_FILE = os.path.join(SCRIPT_DIR, "double_points_end.txt")


def is_spend_disabled():
    """True if streamer has disabled spending (e.g. via Stream Deck toggle)."""
    return os.path.exists(SPEND_DISABLED_FILE)


def is_helpers_hurters_disabled():
    """True if Helpers vs Hurters system is turned off (e.g. via Stream Deck toggle)."""
    return os.path.exists(HELPERS_HURTERS_DISABLED_FILE)


def is_double_points_active():
    """True if !doublepoints is currently active (2x period not yet ended)."""
    try:
        if not os.path.exists(DOUBLE_POINTS_END_FILE):
            return False
        with open(DOUBLE_POINTS_END_FILE, encoding="utf-8") as f:
            s = f.read().strip()
        if not s or s == "0":
            return False
        end_time = int(s)
        if end_time <= 0:
            return False
        return int(time.time()) < end_time
    except (ValueError, OSError):
        return False

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
        "helper_discount_percent": 50,
        "hurter_discount_percent": 50,
        "helper_discount_commands": ["ward", "bee", "buff"],
        "hurter_discount_commands": ["debuff", "curse", "trap", "gas"],
        "cost_to_switch_side": 50,
        "cost_per_heal": 100,
        "cost_per_cleanse": 150,
        "cost_per_dew": 30,
        "cost_per_hex": 75,
        "cost_per_degrade": 100,
        "cost_per_sabotage": 75,
        "command_allowed_roles": {},
        "cost_per_gold": 5,
        "cost_per_curse": 200,
        "cost_per_gas": 75,
        "cost_per_scroll": 100,
        "cost_per_trap": 50,
        "cost_per_transmute": 150,
        "cost_per_ally_bee": 75,
        "cost_per_ward": 30,
        "cost_per_buff": 75,
        "cost_per_debuff": 50,
        "cost_per_wand_common": 50,
        "cost_per_wand_uncommon": 100,
        "cost_per_wand_rare": 200,
        "cost_per_wand_veryrare": 400,
        "default_monster_cost": 100,
        "cost_per_monster": {
            "rat": 5, "albino": 10, "snake": 10, "gnoll": 10, "crab": 15,
            "slime": 15, "swarm": 15, "thief": 20, "skeleton": 20, "bat": 30,
            "brute": 30, "shaman": 35, "spinner": 25, "dm100": 20, "guard": 25,
            "necromancer": 25, "ghoul": 40, "elemental": 40, "warlock": 45,
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
        helper_discount_cmds = cfg.get("helper_discount_commands")
        if not isinstance(helper_discount_cmds, list):
            helper_discount_cmds = defaults["helper_discount_commands"]
        hurter_discount_cmds = cfg.get("hurter_discount_commands")
        if not isinstance(hurter_discount_cmds, list):
            hurter_discount_cmds = defaults["hurter_discount_commands"]
        return {
            "helper_discount_percent": max(0, min(100, int(cfg.get("helper_discount_percent", defaults["helper_discount_percent"])))),
            "hurter_discount_percent": max(0, min(100, int(cfg.get("hurter_discount_percent", defaults["hurter_discount_percent"])))),
            "helper_discount_commands": helper_discount_cmds,
            "hurter_discount_commands": hurter_discount_cmds,
            "cost_per_gold": int(cfg.get("cost_per_gold", defaults["cost_per_gold"])),
            "cost_per_curse": int(cfg.get("cost_per_curse", defaults["cost_per_curse"])),
            "cost_per_gas": int(cfg.get("cost_per_gas", defaults["cost_per_gas"])),
            "cost_per_scroll": int(cfg.get("cost_per_scroll", defaults["cost_per_scroll"])),
            "cost_per_trap": int(cfg.get("cost_per_trap", defaults["cost_per_trap"])),
            "cost_per_transmute": int(cfg.get("cost_per_transmute", defaults["cost_per_transmute"])),
            "cost_per_ally_bee": int(cfg.get("cost_per_ally_bee", defaults["cost_per_ally_bee"])),
            "cost_per_ward": int(cfg.get("cost_per_ward", defaults["cost_per_ward"])),
            "cost_per_buff": int(cfg.get("cost_per_buff", defaults["cost_per_buff"])),
            "cost_per_debuff": int(cfg.get("cost_per_debuff", defaults["cost_per_debuff"])),
            "cost_per_wand_common": int(cfg.get("cost_per_wand_common", defaults["cost_per_wand_common"])),
            "cost_per_wand_uncommon": int(cfg.get("cost_per_wand_uncommon", defaults["cost_per_wand_uncommon"])),
            "cost_per_wand_rare": int(cfg.get("cost_per_wand_rare", defaults["cost_per_wand_rare"])),
            "cost_per_wand_veryrare": int(cfg.get("cost_per_wand_veryrare", defaults["cost_per_wand_veryrare"])),
            "default_monster_cost": int(cfg.get("default_monster_cost", defaults["default_monster_cost"])),
            "cost_per_monster": monsters,
            "cost_to_switch_side": max(0, int(cfg.get("cost_to_switch_side", defaults["cost_to_switch_side"]))),
            "cost_per_heal": max(1, int(cfg.get("cost_per_heal", defaults["cost_per_heal"]))),
            "cost_per_cleanse": max(1, int(cfg.get("cost_per_cleanse", defaults["cost_per_cleanse"]))),
            "cost_per_dew": max(1, int(cfg.get("cost_per_dew", defaults["cost_per_dew"]))),
            "cost_per_hex": max(1, int(cfg.get("cost_per_hex", defaults["cost_per_hex"]))),
            "cost_per_degrade": max(1, int(cfg.get("cost_per_degrade", defaults["cost_per_degrade"]))),
            "cost_per_sabotage": max(1, int(cfg.get("cost_per_sabotage", defaults["cost_per_sabotage"]))),
            "command_allowed_roles": cfg.get("command_allowed_roles") or {},
        }
    except Exception:
        return defaults


def get_config():
    """Cached config (reloads each command to allow live edits)."""
    return load_config()


def is_cost_free(cost_key):
    """True if cost_key is free until a future timestamp (from free_until.json)."""
    if not os.path.exists(FREE_UNTIL_FILE):
        return False
    try:
        with open(FREE_UNTIL_FILE, encoding="utf-8") as f:
            free_until = json.load(f)
        end_ts = free_until.get(cost_key)
        if end_ts is None:
            return False
        return int(time.time()) < int(end_ts)
    except (json.JSONDecodeError, OSError, TypeError, ValueError):
        return False


def effective_cost(cost_key, base_cost):
    """Return 0 if cost is free, else base_cost."""
    return 0 if is_cost_free(cost_key) else base_cost


DEFAULT_ALLOWED_ROLES = {
    "heal": "helper", "cleanse": "helper", "dew": "helper",
    "hex": "hurter", "degrade": "hurter", "sabotage": "hurter",
}


def check_command_access(command_id, role):
    """Return (True, None) if allowed, else (False, error_msg). When helpers/hurters disabled, treat as both."""
    if is_helpers_hurters_disabled():
        if command_id == "switch":
            return False, "Helpers/Hurters is currently turned off."
        return True, None
    allowed = get_config().get("command_allowed_roles") or {}
    val = allowed.get(command_id, DEFAULT_ALLOWED_ROLES.get(command_id, "both"))
    if val == "disabled":
        return False, "This command is currently disabled."
    if val == "both":
        return True, None
    if not role or (role != "helper" and role != "hurter"):
        return False, "Chat once to get a side, then you can use this command."
    if val == "helper" and role == "hurter":
        return False, "Only helpers can use !" + command_id + "."
    if val == "hurter" and role == "helper":
        return False, "Only hurters can use !" + command_id + "."
    return True, None


def apply_role_discount(base_cost, command_id, role):
    """Apply helper/hurter discount if system is ON and user has matching role. Spawn is always neutral."""
    if command_id == "spawn":
        return base_cost
    if is_helpers_hurters_disabled():
        return base_cost
    cfg = get_config()
    helper_pct = cfg.get("helper_discount_percent", 50)
    hurter_pct = cfg.get("hurter_discount_percent", 50)
    helper_cmds = cfg.get("helper_discount_commands") or ["ward", "bee", "buff"]
    hurter_cmds = cfg.get("hurter_discount_commands") or ["debuff", "curse", "trap", "gas"]
    if role == "helper" and command_id in helper_cmds:
        discounted = base_cost - (base_cost * helper_pct // 100)
        return max(1, discounted)
    if role == "hurter" and command_id in hurter_cmds:
        discounted = base_cost - (base_cost * hurter_pct // 100)
        return max(1, discounted)
    return base_cost
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
    "KRW": 0.00075, "CHF": 1.13, "PLN": 0.25, "SEK": 0.095, "ARS": 0.00075,  # 1 ARS ≈ 0.00075 USD (1 USD ≈ 1333 ARS)
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
    """Read viewer_points. Returns dict[username] = (pts, last, donation_pts, role). role is 'helper'|'hurter'|'' for legacy."""
    data = {}
    if os.path.exists(POINTS_FILE):
        with open(POINTS_FILE, encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split("|")
                if len(parts) >= 3:
                    try:
                        donation_pts = int(parts[3]) if len(parts) >= 4 else 0
                        role = (parts[4].strip() or "") if len(parts) >= 5 else ""
                        if role not in ("helper", "hurter"):
                            role = ""
                        data[parts[0].lower()] = (int(parts[1]), int(parts[2]), donation_pts, role)
                    except (ValueError, IndexError):
                        pass
    return data


def write_points(data):
    """Write viewer_points. data[username] = (pts, last, donation_pts, role)."""
    def _row(k, v):
        role = (v[3] or "") if len(v) >= 4 else ""
        return f"{k}|{v[0]}|{v[1]}|{v[2]}|{role}"
    lines = [_row(k, v) for k, v in data.items()]
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


def compute_champion_cost(monster: str) -> int:
    """2× base cost, no early-zone discount."""
    cfg = get_config()
    base = cfg["cost_per_monster"].get(monster, cfg["default_monster_cost"])
    return 2 * base


def _http_error_msg(e, default_timeout: str) -> str:
    if e.code == 504:
        return default_timeout
    try:
        body = e.read().decode("utf-8", errors="replace")
        return json.loads(body).get("error", body) if body.strip().startswith("{") else body
    except Exception:
        return str(e)


def effective_total(pts: int, donation_pts: int) -> int:
    """Total spendable points. Handles legacy format where pts may be chat-only."""
    if donation_pts > 0 and pts < donation_pts:
        return pts + donation_pts
    return pts


def deduct_points(pts: int, donation_pts: int, cost: int):
    """Deduct cost from total. Returns (new_pts, new_donation_pts) or None if insufficient."""
    total = effective_total(pts, donation_pts)
    if total < cost:
        return None
    new_total = total - cost
    new_donation_pts = min(donation_pts, new_total)
    return (new_total, new_donation_pts)


def _get_user_data(data, key):
    """Get (pts, last, donation_pts, role) from data. Handles legacy 3-tuple."""
    existing = data.get(key, (0, 0, 0, ""))
    pts = existing[0] if len(existing) >= 1 else 0
    last = existing[1] if len(existing) >= 2 else 0
    donation_pts = existing[2] if len(existing) >= 3 else 0
    role = existing[3] if len(existing) >= 4 else ""
    return (pts, last, donation_pts, role)


def cmd_spawn(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 2:
        return SPAWN_RESULT_FILE, "Usage: !spawn <monster> (e.g. !spawn rat)"
    monster = args[0].lower()
    username = args[1]
    if monster not in VALID_MONSTERS:
        return SPAWN_RESULT_FILE, f"Unknown monster: {monster}"

    base_cost = effective_cost("cost_per_monster." + monster, compute_spawn_cost(monster))
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("spawn", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "spawn", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost}, you have {total}."

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

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_champion(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 2:
        return SPAWN_RESULT_FILE, "Usage: !champion <monster> (e.g. !champion rat). Costs 2× base spawn cost, no zone discount."
    monster = args[0].lower()
    username = args[1]
    if monster not in VALID_MONSTERS:
        return SPAWN_RESULT_FILE, f"Unknown monster: {monster}"

    base_cost = effective_cost("cost_per_monster." + monster, compute_champion_cost(monster))
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("champion", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "champion", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} for champion {monster}, you have {total}."

            url = "http://127.0.0.1:5000/api/champion-command"
            payload = {"monster": monster, "username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Champion spawn failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Champion spawn failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Champion spawn failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Champion spawn timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Champion spawn failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, "ok|" + body.get("monster", monster) + f"|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_gold(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 2:
        return SPAWN_RESULT_FILE, "Usage: !gold <amount> (e.g. !gold 10)"
    try:
        amount = int(args[0])
    except ValueError:
        return SPAWN_RESULT_FILE, "Usage: !gold <amount> (e.g. !gold 10). Amount must be 1-100."
    if amount < 1 or amount > 100:
        return SPAWN_RESULT_FILE, "Amount must be 1-100. Example: !gold 10"
    username = args[1]

    base_cost = effective_cost("cost_per_gold", amount * get_config()["cost_per_gold"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("gold", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "gold", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} for {amount} gold, you have {total}."

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

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|{amount}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def _curse_error_retryable(err):
    """True if curse failed due to empty slot or already cursed — try another slot."""
    if not err:
        return False
    err_lower = err.lower()
    return "no item in" in err_lower or "already cursed" in err_lower


def cmd_curse(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !curse (curses a random equipped item)"
    username = args[0]

    base_cost = effective_cost("cost_per_curse", get_config()["cost_per_curse"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("curse", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "curse", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to curse, you have {total}."

            url = "http://127.0.0.1:5000/api/curse-command"
            slots_left = list(VALID_SLOTS)
            last_error = None

            while slots_left:
                slot = random.choice(slots_left)
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
                        if body.get("ok"):
                            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
                            data[key] = (new_pts, last, new_donation, role)
                            write_points(data)
                            item_name = body.get("item_name", slot)
                            return SPAWN_RESULT_FILE, f"ok|{item_name}|{new_pts}"
                        last_error = body.get("error", "Curse failed")
                        if not _curse_error_retryable(last_error):
                            return SPAWN_RESULT_FILE, last_error
                        slots_left.remove(slot)
                except urllib.error.HTTPError as e:
                    return SPAWN_RESULT_FILE, _http_error_msg(
                        e, "Curse timed out. Is the game running and in an active run?"
                    )
                except urllib.error.URLError as e:
                    return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
                except Exception as e:
                    msg = str(e).strip() if e else ""
                    return SPAWN_RESULT_FILE, "Curse failed. " + (msg if msg else "Check overlay server and try again.")

            return SPAWN_RESULT_FILE, last_error or "No curseable item in any slot (all empty or already cursed)"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_gas(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !gas (spawns random gas near you)"
    username = args[0]

    base_cost = effective_cost("cost_per_gas", get_config()["cost_per_gas"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("gas", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "gas", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to spew gas, you have {total}."

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

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            gas_name = body.get("gas_name", "gas")
            return SPAWN_RESULT_FILE, f"ok|{gas_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_scroll(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !scroll (uses a random scroll like +10 Unstable Spellbook)"
    username = args[0]

    base_cost = effective_cost("cost_per_scroll", get_config()["cost_per_scroll"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("scroll", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "scroll", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} for random scroll, you have {total}."

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

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            scroll_name = body.get("scroll_name", "scroll")
            return SPAWN_RESULT_FILE, f"ok|{scroll_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_trap(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !trap (places a random visible trap near you)"
    username = args[0]

    base_cost = effective_cost("cost_per_trap", get_config()["cost_per_trap"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("trap", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "trap", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to place a trap, you have {total}."

            url = "http://127.0.0.1:5000/api/trap-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Trap command failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Trap command failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Trap command failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Trap command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Trap command failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            trap_name = body.get("trap_name", "trap")
            return SPAWN_RESULT_FILE, f"ok|{trap_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_transmute(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !transmute (transmutes a random transmutable item from bag or equipped)"
    username = args[0]

    base_cost = effective_cost("cost_per_transmute", get_config()["cost_per_transmute"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("transmute", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "transmute", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to transmute, you have {total}."

            url = "http://127.0.0.1:5000/api/transmute-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Transmute command failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Transmute command failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Transmute command failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Transmute command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Transmute command failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            item_name = body.get("item_name", "item")
            return SPAWN_RESULT_FILE, f"ok|{item_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_ally_bee(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !bee (summons an allied bee for 50 turns, 75 pts)"
    username = args[0]

    base_cost = effective_cost("cost_per_ally_bee", get_config()["cost_per_ally_bee"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("bee", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "bee", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to summon a bee, you have {total}."

            url = "http://127.0.0.1:5000/api/summon-bee-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Summon bee failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Summon bee failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Summon bee failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Summon bee timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Summon bee failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            ally_name = body.get("ally_name", "Bee")
            return SPAWN_RESULT_FILE, f"ok|{ally_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_ward(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !ward (summons a ward, 30 pts, scales with depth)"
    username = args[0]

    base_cost = effective_cost("cost_per_ward", get_config()["cost_per_ward"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("ward", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "ward", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to summon a ward, you have {total}."

            url = "http://127.0.0.1:5000/api/ward-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Summon ward failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Summon ward failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Summon ward failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Summon ward timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Summon ward failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            ward_name = body.get("ward_name", "Ward")
            return SPAWN_RESULT_FILE, f"ok|{ward_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_buff(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !buff (gives a random buff)"
    username = args[0]

    base_cost = effective_cost("cost_per_buff", get_config()["cost_per_buff"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("buff", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "buff", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} for random buff, you have {total}."

            url = "http://127.0.0.1:5000/api/buff-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Buff command failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Buff command failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Buff command failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Buff command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Buff command failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            buff_name = body.get("buff_name", "buff")
            return SPAWN_RESULT_FILE, f"ok|{buff_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_debuff(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !debuff (gives a random debuff)"
    username = args[0]

    base_cost = effective_cost("cost_per_debuff", get_config()["cost_per_debuff"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("debuff", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "debuff", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} for random debuff, you have {total}."

            url = "http://127.0.0.1:5000/api/debuff-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Debuff command failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Debuff command failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Debuff command failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Debuff command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Debuff command failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            debuff_name = body.get("debuff_name", "debuff")
            return SPAWN_RESULT_FILE, f"ok|{debuff_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


WAND_TIERS = frozenset(["common", "uncommon", "rare", "veryrare", "very_rare"])


WAND_COST_KEYS = ["cost_per_wand_common", "cost_per_wand_uncommon", "cost_per_wand_rare", "cost_per_wand_veryrare"]


def _wand_cost_for_rarity(cfg, rarity):
    """Rarity: 0=common, 1=uncommon, 2=rare, 3=very_rare."""
    costs = [
        cfg.get("cost_per_wand_common", 50),
        cfg.get("cost_per_wand_uncommon", 100),
        cfg.get("cost_per_wand_rare", 200),
        cfg.get("cost_per_wand_veryrare", 400),
    ]
    return costs[min(rarity, 3)]


def _wand_effective_cost(cfg, rarity):
    """Effective cost for wand at given rarity (0 if free)."""
    base = _wand_cost_for_rarity(cfg, rarity)
    key = WAND_COST_KEYS[min(rarity, 3)]
    return effective_cost(key, base)


def _tier_to_int(tier_str):
    """Convert tier string to 0-3. Returns -1 for random/invalid."""
    if not tier_str:
        return -1
    m = {"common": 0, "uncommon": 1, "rare": 2, "veryrare": 3, "very_rare": 3}
    return m.get(tier_str.lower().strip(), -1)


def cmd_wand(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
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
        base_cost_check = _wand_effective_cost(cfg, tier)
    else:
        base_cost_check = max(
            _wand_effective_cost(cfg, 0),
            _wand_effective_cost(cfg, 1),
            _wand_effective_cost(cfg, 2),
            _wand_effective_cost(cfg, 3),
        )
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("wand", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost_check = apply_role_discount(base_cost_check, "wand", role)
            total = effective_total(pts, donation_pts)
            if total < cost_check:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost_check}, you have {total}."

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
            base_cost = _wand_effective_cost(cfg, rarity)
            cost = apply_role_discount(base_cost, "wand", role)
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            effect_name = body.get("effect_name", "effect")
            return SPAWN_RESULT_FILE, f"ok|{effect_name}|{new_pts}"
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
    # args: [microAmount, currencyCode, userName] from Streamer.bot Super Chat trigger
    if os.environ.get("DEBUG_SUPERCHAT") or os.path.exists(os.path.join(SCRIPT_DIR, "superchat_debug.txt")):
        with open(os.path.join(SCRIPT_DIR, "superchat_debug.log"), "a", encoding="utf-8") as f:
            f.write(f"{datetime.datetime.now().isoformat()} args={args!r} len={len(args)}\n")
    if len(args) < 3:
        return DONATION_RESULT_FILE, "invalid|0"
    try:
        micro_amount = int(args[0])
        currency = (args[1] or "USD").upper()[:3]
    except (ValueError, IndexError):
        return DONATION_RESULT_FILE, "invalid|0"
    username = args[2]
    if not username or username.lower() == "anonymous":
        return DONATION_RESULT_FILE, "skip|0"

    key = username.lower()
    amount_in_currency = micro_amount / 1_000_000
    rate = fetch_usd_rate(currency)
    amount_usd = amount_in_currency * rate
    to_add = max(0, int(round(amount_usd * 100)))
    if to_add <= 0:
        return DONATION_RESULT_FILE, "ok|0"
    if is_double_points_active():
        to_add *= 2
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            pts += to_add
            data[key] = (pts, last, donation_pts + to_add, role)
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
    if is_double_points_active():
        to_add *= 2
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            pts += to_add
            data[key] = (pts, last, donation_pts + to_add, role)
            write_points(data)
        return DONATION_RESULT_FILE, f"ok|{to_add}"
    except TimeoutError:
        return DONATION_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_heal(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !heal (heals hero ~15% HP)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("heal", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            base_cost = effective_cost("cost_per_heal", get_config()["cost_per_heal"])
            cost = apply_role_discount(base_cost, "heal", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to heal, you have {total}."

            url = "http://127.0.0.1:5000/api/heal-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = json.loads(resp.read().decode())
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Heal failed")
            except Exception as e:
                return SPAWN_RESULT_FILE, _http_error_msg(e, "Heal timed out. Is the game running?")
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|Healing|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_cleanse(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !cleanse (removes one random debuff)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("cleanse", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            base_cost = effective_cost("cost_per_cleanse", get_config()["cost_per_cleanse"])
            cost = apply_role_discount(base_cost, "cleanse", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to cleanse, you have {total}."

            url = "http://127.0.0.1:5000/api/cleanse-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = json.loads(resp.read().decode())
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Cleanse failed")
                    buff_name = body.get("buff_name", "debuff")
            except Exception as e:
                return SPAWN_RESULT_FILE, _http_error_msg(e, "Cleanse timed out. Is the game running?")
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|{buff_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_dew(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !dew (drops a dewdrop near hero)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("dew", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            base_cost = effective_cost("cost_per_dew", get_config()["cost_per_dew"])
            cost = apply_role_discount(base_cost, "dew", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} for dewdrop, you have {total}."

            url = "http://127.0.0.1:5000/api/dew-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = json.loads(resp.read().decode())
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Dew failed")
            except Exception as e:
                return SPAWN_RESULT_FILE, _http_error_msg(e, "Dew timed out. Is the game running?")
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|Dewdrop|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_hex(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !hex (applies Hex debuff)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("hex", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            base_cost = effective_cost("cost_per_hex", get_config()["cost_per_hex"])
            cost = apply_role_discount(base_cost, "hex", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to hex, you have {total}."

            url = "http://127.0.0.1:5000/api/hex-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = json.loads(resp.read().decode())
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Hex failed")
            except Exception as e:
                return SPAWN_RESULT_FILE, _http_error_msg(e, "Hex timed out. Is the game running?")
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|Hex|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_degrade(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !degrade (applies Degrade debuff)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("degrade", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            base_cost = effective_cost("cost_per_degrade", get_config()["cost_per_degrade"])
            cost = apply_role_discount(base_cost, "degrade", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to degrade, you have {total}."

            url = "http://127.0.0.1:5000/api/degrade-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = json.loads(resp.read().decode())
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Degrade failed")
            except Exception as e:
                return SPAWN_RESULT_FILE, _http_error_msg(e, "Degrade timed out. Is the game running?")
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|Degrade|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_sabotage(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !sabotage (removes one random buff)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("sabotage", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            base_cost = effective_cost("cost_per_sabotage", get_config()["cost_per_sabotage"])
            cost = apply_role_discount(base_cost, "sabotage", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to sabotage, you have {total}."

            url = "http://127.0.0.1:5000/api/sabotage-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = json.loads(resp.read().decode())
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Sabotage failed")
                    buff_name = body.get("buff_name", "buff")
            except Exception as e:
                return SPAWN_RESULT_FILE, _http_error_msg(e, "Sabotage timed out. Is the game running?")
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|{buff_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_switch(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if is_helpers_hurters_disabled():
        return SPAWN_RESULT_FILE, "Helpers/Hurters is currently turned off."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !switch (switch helper/hurter side)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            if not role or (role != "helper" and role != "hurter"):
                return SPAWN_RESULT_FILE, "You don't have a side yet. Chat once to be assigned."
            cost = get_config()["cost_to_switch_side"]
            total = effective_total(pts, donation_pts)
            if cost > 0 and total < cost:
                return SPAWN_RESULT_FILE, f"Not enough points! Need {cost} to switch side, you have {total}."
            new_role = "hurter" if role == "helper" else "helper"
            if cost > 0:
                new_pts, new_donation = deduct_points(pts, donation_pts, cost)
                data[key] = (new_pts, last, new_donation, new_role)
            else:
                data[key] = (pts, last, donation_pts, new_role)
                new_pts = pts
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|{new_role}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_myside(args):
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !myside"
    username = args[0]
    key = username.lower()
    data = read_points()
    pts, last, donation_pts, role = _get_user_data(data, key)
    if not role or (role != "helper" and role != "hurter"):
        return SPAWN_RESULT_FILE, f"{username}, chat once to get a side, then use !myside to see it."
    return SPAWN_RESULT_FILE, f"{username}, you're on the {role} side!"


COMMANDS = {
    "spawn": cmd_spawn,
    "champion": cmd_champion,
    "gold": cmd_gold,
    "curse": cmd_curse,
    "gas": cmd_gas,
    "scroll": cmd_scroll,
    "trap": cmd_trap,
    "transmute": cmd_transmute,
    "bee": cmd_ally_bee,
    "ward": cmd_ward,
    "buff": cmd_buff,
    "debuff": cmd_debuff,
    "wand": cmd_wand,
    "heal": cmd_heal,
    "cleanse": cmd_cleanse,
    "dew": cmd_dew,
    "hex": cmd_hex,
    "degrade": cmd_degrade,
    "sabotage": cmd_sabotage,
    "switch": cmd_switch,
    "myside": cmd_myside,
    "superchat": cmd_superchat,
    "cheer": cmd_cheer,
}


def main():
    args = [a.strip() for a in sys.argv[1:] if a.strip()]
    if len(args) < 1:
        with open(SPAWN_RESULT_FILE, "w", encoding="utf-8") as f:
            f.write("Usage: points_command.py <spawn|champion|gold|curse|gas|scroll|trap|transmute|bee|ward|buff|debuff|wand|heal|cleanse|dew|hex|degrade|sabotage|switch|myside|superchat|cheer> [args...]")
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
