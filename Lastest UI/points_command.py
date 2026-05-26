#!/usr/bin/env python3
"""
Unified points command script for Streamer.bot.
Usage:
  spawn:    python points_command.py spawn <monster> <username>
  champion: python points_command.py champion <monster> <username>  (2× base cost, random champion type)
  gold:     python points_command.py gold <amount> <username>
  transfer: python points_command.py transfer <amount> <to_username> <from_username>
  curse:    python points_command.py curse <username>  (picks random slot)
  gas:      python points_command.py gas <username>
  scroll:   python points_command.py scroll <username>
  row:      python points_command.py row <username>  (Ring of Wealth loot, 100 pts, scales with depth)
  trap:     python points_command.py trap <username>
  plant:    python points_command.py plant <username>
  bomb:     python points_command.py bomb <username>
  transmute: python points_command.py transmute <username>
  bee:       python points_command.py bee <username>  (summon allied bee, 75 pts, 150 turns)
  ward:      python points_command.py ward <username>  (summon ward, 30 pts, scales with depth)
  corruptally: python points_command.py corruptally <username>  (summon corrupted ally from biome)
  buff:     python points_command.py buff <username>
  debuff:   python points_command.py debuff <username>
  wand:     python points_command.py wand <username>  (weighted random cursed-wand effect; legacy tier arg optional)
  superchat: python points_command.py superchat <microAmount> <currencyCode> <username> [isSubscribed 0|1] [userIsSponsor 0|1] [topFarder 0|1]
  cheer:    python points_command.py cheer <bits> <username> [isSubscribed 0|1] [userIsSponsor 0|1] [topFarder 0|1]

All spend commands write to spawn_result.txt (ok or ok|extra|pts). The last value is remaining points. Donation writes to donation_result.txt.
Transmute uses four fields: ok|<original_item_name>|<result_item_name>|<points> (original may be empty if the game build omits it).
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
GAME_DATA_URL = "http://127.0.0.1:5000/api/game-data"
DOUBLE_POINTS_END_FILE = os.path.join(SCRIPT_DIR, "double_points_end.txt")


def is_spend_disabled():
    """True if streamer has disabled spending (e.g. via Stream Deck toggle)."""
    return os.path.exists(SPEND_DISABLED_FILE)


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


def _arg_bool(s, default=False):
    """Parse 0/1, true/false, yes/no from Streamer.bot CLI args."""
    if s is None:
        return default
    t = str(s).strip().lower()
    if not t:
        return default
    return t in ("1", "true", "yes", "on")


def donation_earn_multiplier(is_subscribed=False, is_sponsor=False, top_farder=False):
    """Stack global 2×, subscriber/member 2×, and optional top-farder 2× (matches chat earning)."""
    m = 1
    if is_double_points_active():
        m *= 2
    if is_subscribed or is_sponsor:
        m *= 2
    if top_farder:
        m *= 2
    return m


NATIVE_DEPTH = {
    "rat": 1, "albino": 1, "snake": 1, "gnoll": 2, "crab": 3, "slime": 4,
    "swarm": 3, "thief": 4, "skeleton": 6, "dm100": 7, "guard": 7,
    "necromancer": 8, "bat": 9, "brute": 11, "shaman": 11, "spinner": 12,
    "ghoul": 14, "elemental": 16, "warlock": 16, "monk": 17, "golem": 18,
    "succubus": 19, "eye": 21, "scorpio": 23,
}


def load_config():
    """Load costs from points_config.json. Falls back to defaults if missing/invalid."""
    defaults = {
        "cost_per_heal": 100,
        "cost_per_cleanse": 150,
        "cost_per_dew": 30,
        "cost_per_plant": 30,
        "cost_per_corrupt_ally": 100,
        "cost_per_hex": 75,
        "cost_per_degrade": 100,
        "cost_per_sabotage": 75,
        "command_allowed_roles": {},
        "cost_per_gold": 5,
        "cost_per_curse": 200,
        "cost_per_gas": 75,
        "cost_per_scroll": 100,
        "cost_per_ring_of_wealth": 100,
        "cost_per_trap": 50,
        "cost_per_bomb": 75,
        "cost_per_transmute": 150,
        "cost_per_ally_bee": 75,
        "cost_per_ward": 30,
        "cost_per_buff": 75,
        "cost_per_debuff": 50,
        "cost_per_wand": 75,
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
        return {
            "cost_per_gold": int(cfg.get("cost_per_gold", defaults["cost_per_gold"])),
            "cost_per_curse": int(cfg.get("cost_per_curse", defaults["cost_per_curse"])),
            "cost_per_gas": int(cfg.get("cost_per_gas", defaults["cost_per_gas"])),
            "cost_per_scroll": int(cfg.get("cost_per_scroll", defaults["cost_per_scroll"])),
            "cost_per_ring_of_wealth": max(1, int(cfg.get("cost_per_ring_of_wealth", defaults["cost_per_ring_of_wealth"]))),
            "cost_per_trap": int(cfg.get("cost_per_trap", defaults["cost_per_trap"])),
            "cost_per_bomb": int(cfg.get("cost_per_bomb", defaults["cost_per_bomb"])),
            "cost_per_transmute": int(cfg.get("cost_per_transmute", defaults["cost_per_transmute"])),
            "cost_per_ally_bee": int(cfg.get("cost_per_ally_bee", defaults["cost_per_ally_bee"])),
            "cost_per_ward": int(cfg.get("cost_per_ward", defaults["cost_per_ward"])),
            "cost_per_buff": int(cfg.get("cost_per_buff", defaults["cost_per_buff"])),
            "cost_per_debuff": int(cfg.get("cost_per_debuff", defaults["cost_per_debuff"])),
            "cost_per_wand": max(1, int(cfg.get("cost_per_wand", defaults["cost_per_wand"]))),
            "default_monster_cost": int(cfg.get("default_monster_cost", defaults["default_monster_cost"])),
            "cost_per_monster": monsters,
            "cost_per_heal": max(1, int(cfg.get("cost_per_heal", defaults["cost_per_heal"]))),
            "cost_per_cleanse": max(1, int(cfg.get("cost_per_cleanse", defaults["cost_per_cleanse"]))),
            "cost_per_dew": max(1, int(cfg.get("cost_per_dew", defaults["cost_per_dew"]))),
            "cost_per_plant": max(1, int(cfg.get("cost_per_plant", defaults["cost_per_plant"]))),
            "cost_per_corrupt_ally": max(1, int(cfg.get("cost_per_corrupt_ally", defaults["cost_per_corrupt_ally"]))),
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


def check_command_access(command_id, role):
    """Return (True, None) if allowed, else (False, error_msg). Only \"disabled\" in command_allowed_roles blocks a command."""
    allowed = get_config().get("command_allowed_roles") or {}
    val = allowed.get(command_id, "both")
    if val == "disabled":
        return False, "This command is currently disabled."
    return True, None


def apply_role_discount(base_cost, command_id, role):
    """Role discounts removed; always returns base_cost (signature kept for call sites)."""
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

# USD value of 1 unit of foreign currency. Ballpark ~early 2026; tune for your region if needed.
# Used when Frankfurter fails (offline, timeout, unsupported code, empty rates).
FALLBACK_RATES = {
    "AED": 0.27, "AFN": 0.014, "ALL": 0.011, "AMD": 0.0025, "ANG": 0.56, "AOA": 0.0011,
    "ARS": 0.00075, "AUD": 0.65, "AWG": 0.56, "AZN": 0.59, "BAM": 0.55, "BBD": 0.50,
    "BDT": 0.0083, "BGN": 0.55, "BHD": 2.65, "BIF": 0.00035, "BMD": 1.0, "BND": 0.74,
    "BOB": 0.145, "BRL": 0.18, "BSD": 1.0, "BTN": 0.012, "BWP": 0.074, "BYN": 0.31,
    "BZD": 0.50, "CAD": 0.72, "CDF": 0.00036, "CHF": 1.13, "CLP": 0.00105, "CNY": 0.138,
    "COP": 0.00025, "CRC": 0.0020, "CUP": 0.038, "CVE": 0.0097, "CZK": 0.044, "DJF": 0.0056,
    "DKK": 0.145, "DOP": 0.017, "DZD": 0.0075, "EGP": 0.020, "ERN": 0.067, "ETB": 0.0070,
    "EUR": 1.05, "FJD": 0.445, "FKP": 1.27, "GBP": 1.27, "GEL": 0.36, "GHS": 0.065,
    "GIP": 1.27, "GMD": 0.015, "GNF": 0.000116, "GTQ": 0.13, "GYD": 0.0048, "HKD": 0.128,
    "HNL": 0.040, "HRK": 0.14, "HTG": 0.0076, "HUF": 0.0028, "IDR": 0.000062, "ILS": 0.27,
    "INR": 0.012, "IQD": 0.00076, "IRR": 0.000024, "ISK": 0.0071, "JMD": 0.0064, "JOD": 1.41,
    "JPY": 0.0067, "KES": 0.0077, "KGS": 0.011, "KHR": 0.00025, "KMF": 0.0022, "KRW": 0.00075,
    "KWD": 3.25, "KYD": 1.20, "KZT": 0.0020, "LAK": 0.000046, "LBP": 0.000011, "LKR": 0.0033,
    "LRD": 0.0052, "LSL": 0.055, "LYD": 0.18, "MAD": 0.10, "MDL": 0.055, "MGA": 0.00022,
    "MKD": 0.017, "MMK": 0.00048, "MNT": 0.00029, "MOP": 0.125, "MRU": 0.025, "MUR": 0.022,
    "MVR": 0.065, "MWK": 0.00058, "MXN": 0.055, "MYR": 0.225, "MZN": 0.016, "NAD": 0.055,
    "NGN": 0.00065, "NIO": 0.027, "NOK": 0.091, "NPR": 0.0075, "NZD": 0.59, "OMR": 2.60,
    "PAB": 1.0, "PEN": 0.27, "PGK": 0.25, "PHP": 0.017, "PKR": 0.0035, "PLN": 0.25,
    "PYG": 0.00013, "QAR": 0.27, "RON": 0.22, "RSD": 0.0095, "RUB": 0.010, "RWF": 0.00076,
    "SAR": 0.27, "SBD": 0.12, "SCR": 0.074, "SDG": 0.0017, "SEK": 0.096, "SGD": 0.74,
    "SHP": 1.27, "SLE": 0.045, "SOS": 0.0018, "SRD": 0.032, "SSP": 0.00077, "STN": 0.046,
    "SVC": 0.114, "SZL": 0.055, "THB": 0.029, "TJS": 0.092, "TMT": 0.29, "TND": 0.32,
    "TOP": 0.42, "TRY": 0.0225, "TTD": 0.148, "TWD": 0.031, "TZS": 0.00039, "UAH": 0.024,
    "UGX": 0.00027, "USD": 1.0, "UYU": 0.025, "UZS": 0.000078, "VES": 0.027, "VND": 0.000039,
    "VUV": 0.0084, "WST": 0.36, "XAF": 0.0017, "XCD": 0.37, "XOF": 0.0017, "XPF": 0.0091,
    "YER": 0.0040, "ZAR": 0.055, "ZMW": 0.036,
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


def count_known_cursed_equipped():
    """Count equipped slots with a known curse (matches StreamingCommandHandler.handleCurse)."""
    try:
        req = urllib.request.Request(GAME_DATA_URL)
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode())
        equipped = data.get("equipped") or {}
        n = 0
        for slot in VALID_SLOTS:
            item = equipped.get(slot)
            if item and item.get("cursed") and item.get("cursedKnown"):
                n += 1
        return n
    except Exception:
        return None


def curse_cost_for_equipped_curses(base_cost, cursed_count):
    """Double cost per known-cursed equip: 0 = base, 1 = 2×, 2 = 4×, etc."""
    if cursed_count is None:
        return base_cost
    return base_cost * (2 ** cursed_count)


def _dungeon_region(depth: int) -> int:
    """0=sewers, 1=prison, 2=caves, 3=city, 4=halls — matches Desktop StreamingCommandHandler."""
    return (depth - 1) // 5


def _early_spawn_multiplier(depth: int, native: int) -> int:
    """When depth < native: return 1, 2, or 3 (tier steps). Maps to +0%, +20%, +40% on base cost."""
    cur_r = _dungeon_region(depth)
    nat_r = _dungeon_region(native)
    if cur_r < nat_r:
        if nat_r == 1:
            return 2 if cur_r == 0 else 1
        if nat_r == 2:
            if cur_r == 0:
                return 3
            if cur_r == 1:
                return 2
            return 1
        if nat_r >= 3:
            if cur_r <= 1:
                return 3
            if cur_r == 2:
                return 2
            return 1
    if cur_r == nat_r == 2 and depth < native:
        return 2
    return 1


def compute_spawn_cost(monster: str) -> int:
    """Late discount when deeper than native; early surcharge when shallower (+20% per tier step above baseline)."""
    cfg = get_config()
    base = cfg["cost_per_monster"].get(monster, cfg["default_monster_cost"])
    depth = get_current_depth()
    native = NATIVE_DEPTH.get(monster)
    if depth is None or native is None:
        return base
    if depth > native:
        return max(1, base // 2)
    if depth < native:
        tier = _early_spawn_multiplier(depth, native)  # 1, 2, or 3 → +0%, +20%, +40%
        factor = 1.0 + 0.20 * (tier - 1)
        return max(1, int(round(base * factor)))
    return base


def compute_champion_cost(monster: str) -> int:
    """2× zone-adjusted spawn cost (same early/late rules as !spawn)."""
    return 2 * compute_spawn_cost(monster)


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


def not_enough_points_msg(username: str, cost: int, total: int, detail: str = "") -> str:
    """Chat error when a viewer cannot afford a spend command (no | — safe for spawn_result parser)."""
    name = username.strip()
    if name and not name.startswith("@"):
        name = "@" + name
    need = f"Need {cost}"
    if detail:
        need += f" {detail}"
    prefix = f"{name}, " if name else ""
    return f"{prefix}Not enough points! {need}, you have {total}."


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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total)

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
        return SPAWN_RESULT_FILE, "Usage: !champion <monster> (e.g. !champion rat). Costs 2× zone-adjusted spawn cost."
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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, f"for champion {monster}")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, f"for {amount} gold")

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


def cmd_curse(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !curse (curses a random equipped item)"
    username = args[0]

    base_cost = effective_cost("cost_per_curse", get_config()["cost_per_curse"])
    scaled_cost = curse_cost_for_equipped_curses(base_cost, count_known_cursed_equipped())
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("curse", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(scaled_cost, "curse", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to curse")

            url = "http://127.0.0.1:5000/api/curse-command"
            payload = {"username": username}
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
                        item_name = body.get("item_name", "item")
                        return SPAWN_RESULT_FILE, f"ok|{item_name}|{new_pts}"
                    return SPAWN_RESULT_FILE, body.get("error", "Curse failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Curse timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Curse failed. " + (msg if msg else "Check overlay server and try again.")
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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to spew gas")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "for random scroll")

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


def cmd_row(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !row (Ring of Wealth bonus loot near you, always at least one item)"
    username = args[0]

    base_cost = effective_cost("cost_per_ring_of_wealth", get_config()["cost_per_ring_of_wealth"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("ring_of_wealth", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "ring_of_wealth", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "for Ring of Wealth loot")

            url = "http://127.0.0.1:5000/api/ring-of-wealth-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Ring of wealth command failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Ring of wealth command failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Ring of wealth command failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Ring of wealth command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Ring of wealth command failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            detail = body.get("detail", "loot")
            return SPAWN_RESULT_FILE, f"ok|{detail}|{new_pts}"
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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to place a trap")

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


def cmd_bomb(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !bomb (drops a weighted random lit bomb near you)"
    username = args[0]

    base_cost = effective_cost("cost_per_bomb", get_config()["cost_per_bomb"])
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("bomb", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            cost = apply_role_discount(base_cost, "bomb", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "for !bomb")

            url = "http://127.0.0.1:5000/api/bomb-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")

            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    raw = resp.read().decode("utf-8", errors="replace")
                    if not raw.strip():
                        return SPAWN_RESULT_FILE, "Bomb command failed (empty response from server)"
                    try:
                        body = json.loads(raw)
                    except json.JSONDecodeError:
                        return SPAWN_RESULT_FILE, "Bomb command failed (server error). Is the overlay running?"
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Bomb command failed")
            except urllib.error.HTTPError as e:
                return SPAWN_RESULT_FILE, _http_error_msg(
                    e, "Bomb command timed out. Is the game running and in an active run?"
                )
            except urllib.error.URLError as e:
                return SPAWN_RESULT_FILE, "Overlay server not reachable. Is it running?"
            except Exception as e:
                msg = str(e).strip() if e else ""
                return SPAWN_RESULT_FILE, "Bomb command failed. " + (msg if msg else "Check overlay server and try again.")

            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            bomb_name = body.get("bomb_name", "bomb")
            return SPAWN_RESULT_FILE, f"ok|{bomb_name}|{new_pts}"
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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to transmute")

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
            # Transmute: echo both item names for Streamer.bot/Twitch (matches in-game GLog when game sends original_item_name).
            write_points(data)
            item_name = (body.get("item_name") or "item").strip()
            original_item_name = (body.get("original_item_name") or "").strip()
            return SPAWN_RESULT_FILE, f"ok|{original_item_name}|{item_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_ally_bee(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !bee (summons an allied bee for 150 turns, 75 pts)"
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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to summon a bee")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to summon a ward")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "for random buff")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "for random debuff")

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


def cmd_wand(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !wand (weighted random effect) or python points_command.py wand <username>"
    if len(args) >= 2 and args[0].lower().strip() in WAND_TIERS:
        username = args[1]
    else:
        username = args[0]

    cfg = get_config()
    base_cost_check = effective_cost("cost_per_wand", cfg["cost_per_wand"])
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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost_check, total)

            url = "http://127.0.0.1:5000/api/wand-command"
            payload = {"username": username}
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

            base_cost = effective_cost("cost_per_wand", cfg["cost_per_wand"])
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
    try:
        url = f"https://api.frankfurter.dev/v1/latest?from={code}&to=USD"
        with urllib.request.urlopen(url, timeout=5) as resp:
            data = json.loads(resp.read().decode())
            usd = data.get("rates", {}).get("USD")
            if usd is not None:
                f = float(usd)
                if f > 0:
                    return f
    except Exception:
        pass
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
    is_sub = _arg_bool(args[3], False) if len(args) > 3 else False
    is_sponsor = _arg_bool(args[4], False) if len(args) > 4 else False
    top_farder = _arg_bool(args[5], False) if len(args) > 5 else False

    key = username.lower()
    amount_in_currency = micro_amount / 1_000_000
    rate = fetch_usd_rate(currency)
    amount_usd = amount_in_currency * rate
    to_add = max(0, int(round(amount_usd * 100)))
    if to_add <= 0:
        return DONATION_RESULT_FILE, "ok|0"
    to_add *= donation_earn_multiplier(is_sub, is_sponsor, top_farder)
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
    is_sub = _arg_bool(args[2], False) if len(args) > 2 else False
    is_sponsor = _arg_bool(args[3], False) if len(args) > 3 else False
    top_farder = _arg_bool(args[4], False) if len(args) > 4 else False

    key = username.lower()
    to_add = bits
    if to_add <= 0:
        return DONATION_RESULT_FILE, "ok|0"
    to_add *= donation_earn_multiplier(is_sub, is_sponsor, top_farder)
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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to heal")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to cleanse")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "for dewdrop")

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


def cmd_plant(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !plant (plants a random plant near hero; fails if Barren Land enabled)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("plant", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            base_cost = effective_cost("cost_per_plant", get_config()["cost_per_plant"])
            cost = apply_role_discount(base_cost, "plant", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to plant")

            url = "http://127.0.0.1:5000/api/plant-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = json.loads(resp.read().decode())
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Plant failed")
                    plant_name = body.get("plant_name", "plant")
            except Exception as e:
                return SPAWN_RESULT_FILE, _http_error_msg(e, "Plant timed out. Is the game running?")
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|{plant_name}|{new_pts}"
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_corrupt_ally(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !corruptally (summons a corrupted ally from the current biome)"
    username = args[0]
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access("corrupt_ally", role)
            if not ok:
                return SPAWN_RESULT_FILE, err
            base_cost = effective_cost("cost_per_corrupt_ally", get_config()["cost_per_corrupt_ally"])
            cost = apply_role_discount(base_cost, "corrupt_ally", role)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to summon corrupted ally")

            url = "http://127.0.0.1:5000/api/corrupt-ally-command"
            payload = {"username": username}
            req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = json.loads(resp.read().decode())
                    if not body.get("ok"):
                        return SPAWN_RESULT_FILE, body.get("error", "Corrupt ally failed")
                    mob_name = body.get("mob_name", "ally")
            except Exception as e:
                return SPAWN_RESULT_FILE, _http_error_msg(e, "Corrupt ally timed out. Is the game running?")
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return SPAWN_RESULT_FILE, f"ok|{mob_name}|{new_pts}"
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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to hex")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to degrade")

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
                return SPAWN_RESULT_FILE, not_enough_points_msg(username, cost, total, "to sabotage")

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


def cmd_transfer(args):
    """Transfer points from one viewer to another.

    Deduction order: chat-earned points first, then donation-backed points if needed.
    Recipient donation points do not increase.
    """
    if len(args) < 3:
        return SPAWN_RESULT_FILE, "Usage: !givepoints <amount> <target> (example: !givepoints 50 @bob)"
    try:
        amount = int(args[0])
    except ValueError:
        return SPAWN_RESULT_FILE, "Amount must be a whole number. Example: !givepoints 50 @bob"
    if amount <= 0:
        return SPAWN_RESULT_FILE, "Amount must be at least 1. Example: !givepoints 50 @bob"
    if amount > 1000000:
        return SPAWN_RESULT_FILE, "Amount too large."

    to_username = (args[1] or "").strip()
    from_username = (args[2] or "").strip()
    if not to_username or not from_username:
        return SPAWN_RESULT_FILE, "Usage: !givepoints <amount> <target> (example: !givepoints 50 @bob)"

    # Normalize @mentions and keys.
    to_display = to_username if to_username.startswith("@") else "@" + to_username
    from_display = from_username if from_username.startswith("@") else "@" + from_username
    to_key = to_username.lstrip("@").strip().lower()
    from_key = from_username.lstrip("@").strip().lower()

    if not to_key or not from_key:
        return SPAWN_RESULT_FILE, "Invalid username."
    if to_key == from_key:
        return SPAWN_RESULT_FILE, "You can't transfer points to yourself."

    try:
        with points_lock():
            data = read_points()

            from_pts, from_last, from_donation_pts, from_role = _get_user_data(data, from_key)
            to_pts, to_last, to_donation_pts, to_role = _get_user_data(data, to_key)

            from_pts = int(from_pts)
            from_donation_pts = int(from_donation_pts)
            to_pts = int(to_pts)
            to_donation_pts = int(to_donation_pts)

            from_total = effective_total(from_pts, from_donation_pts)
            if from_total < amount:
                return SPAWN_RESULT_FILE, f"{from_display}, not enough points. You have {from_total}."

            # Spend chat points first, then donation points.
            from_chat_only = max(0, from_pts - from_donation_pts)
            take_from_chat = min(amount, from_chat_only)
            take_from_donor = amount - take_from_chat
            new_from_pts = from_pts - amount
            new_from_donation = max(0, from_donation_pts - take_from_donor)

            new_to_pts = to_pts + amount
            new_to_donation = min(to_donation_pts, new_to_pts)

            data[from_key] = (new_from_pts, from_last, new_from_donation, from_role)
            data[to_key] = (new_to_pts, to_last, new_to_donation, to_role)
            write_points(data)

            return SPAWN_RESULT_FILE, f"{from_display} gave {amount} points to {to_display}. {from_display} now has {new_from_pts}."
    except TimeoutError:
        return SPAWN_RESULT_FILE, "Points file busy. Please try again in a moment."


COMMANDS = {
    "spawn": cmd_spawn,
    "champion": cmd_champion,
    "gold": cmd_gold,
    "transfer": cmd_transfer,
    "givepoints": cmd_transfer,
    "curse": cmd_curse,
    "gas": cmd_gas,
    "scroll": cmd_scroll,
    "row": cmd_row,
    "trap": cmd_trap,
    "plant": cmd_plant,
    "bomb": cmd_bomb,
    "transmute": cmd_transmute,
    "bee": cmd_ally_bee,
    "ward": cmd_ward,
    "buff": cmd_buff,
    "debuff": cmd_debuff,
    "wand": cmd_wand,
    "heal": cmd_heal,
    "cleanse": cmd_cleanse,
    "dew": cmd_dew,
    "corruptally": cmd_corrupt_ally,
    "corrupt_ally": cmd_corrupt_ally,
    "hex": cmd_hex,
    "degrade": cmd_degrade,
    "sabotage": cmd_sabotage,
    "superchat": cmd_superchat,
    "cheer": cmd_cheer,
}


def main():
    args = [a.strip() for a in sys.argv[1:] if a.strip()]
    if len(args) < 1:
        with open(SPAWN_RESULT_FILE, "w", encoding="utf-8") as f:
            f.write("Usage: points_command.py <spawn|champion|gold|transfer|givepoints|curse|gas|scroll|row|trap|plant|bomb|transmute|bee|ward|corruptally|buff|debuff|wand|heal|cleanse|dew|hex|degrade|sabotage|superchat|cheer> [args...]")
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
