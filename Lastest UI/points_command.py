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
  superchat: python points_command.py superchat <microAmount> <currencyCode> <username> [isSubscribed 0|1] [userIsSponsor 0|1]
  cheer:    python points_command.py cheer <bits> <username> [isSubscribed 0|1] [userIsSponsor 0|1]
  giftmembership: python points_command.py giftmembership <username> [tier] [isSubscribed 0|1] [userIsSponsor 0|1]
  balance:  python points_command.py balance <username>  (!points lookup; writes points_balance_result.txt)

CLI spend commands still write spawn_result.txt for manual terminal testing.
The HTTP gateway (R1 /api/chat-command) uses structured ChatResult only — no result files.
"""
import sys
import urllib.request
import json
import os
import time
import random
import datetime
import uuid
import threading
from contextlib import contextmanager
from dataclasses import dataclass, field
from typing import Any, Optional

if os.name == "nt":
    import msvcrt
else:
    import fcntl

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
POINTS_FILE = os.path.join(SCRIPT_DIR, "viewer_points.txt")
POINTS_LOCK_FILE = POINTS_FILE + ".lock"
POINTS_LOCK_TIMEOUT = 10.0  # seconds to wait for lock
SPEND_COMMAND_LOCK_FILE = os.path.join(SCRIPT_DIR, "spend_command.lock")
SPEND_COMMAND_LOCK_TIMEOUT = 3.0  # fail fast if another python is still running; don't block chat 12s
# Must finish before Streamer.bot Run Program "Wait maximum" (10s) so C# reads after we write.
GAME_COMMAND_TIMEOUT = 9.0
SPAWN_RESULT_FILE = os.path.join(SCRIPT_DIR, "spawn_result.txt")
SPAWN_RESULT_LAST_FILE = os.path.join(SCRIPT_DIR, "spawn_result_last.txt")
POINTS_COMMAND_TRACE_FILE = os.path.join(SCRIPT_DIR, "points_command_trace.log")
DONATION_RESULT_FILE = os.path.join(SCRIPT_DIR, "donation_result.txt")
BALANCE_RESULT_FILE = os.path.join(SCRIPT_DIR, "points_balance_result.txt")
CONFIG_FILE = os.path.join(SCRIPT_DIR, "points_config.json")
FREE_UNTIL_FILE = os.path.join(SCRIPT_DIR, "free_until.json")
SPEND_DISABLED_FILE = os.path.join(SCRIPT_DIR, "spend_disabled.txt")
_free_until_lock = threading.Lock()
_free_clock_last_sec: Optional[int] = None
_free_clock_paused: bool = False
GAME_DATA_URL = "http://127.0.0.1:5000/api/game-data"
DOUBLE_POINTS_END_FILE = os.path.join(SCRIPT_DIR, "double_points_end.txt")
TOP_SUMMONER_FILE = os.path.join(SCRIPT_DIR, "top_summoner.txt")
DEATH_COST_STATE_FILE = os.path.join(SCRIPT_DIR, "death_cost_state.json")
DEATH_COST_DISPLAY_FILE = os.path.join(SCRIPT_DIR, "death_cost_display.txt")
DEATH_COST_EXEMPT_COMMANDS = frozenset({
    "heal", "bee", "ward", "ring_of_wealth", "row",
    "corrupt_ally", "corruptally", "buff", "cleanse",
})
DEATH_COST_FACTOR = 1.5
SPEND_ACTIONS_FILE = os.path.join(SCRIPT_DIR, "spend_actions.jsonl")
SPEND_ACTIONS_MAX = 200

BOT_USER = "daltongoesslow"

# Last confirmed-spend Bestiary bar grant (serialized by spend_command_lock).
_last_spend_bestiary: Optional[dict[str, Any]] = None


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


def is_top_summoner(username):
    """True if username is the rolling heat leader (15m Bestiary XP), case-insensitive.

    Prefers summon_bestiary.get_heat_leader(); falls back to heat_leader.txt /
    top_summoner.txt for legacy readers.
    """
    if not username:
        return False
    want = str(username).strip().lower()
    try:
        from summon_bestiary import get_heat_leader
        leader, _score = get_heat_leader()
        if leader and leader.strip().lower() == want:
            return True
        if leader:
            return False
    except Exception:
        pass
    # Fallback: heat_leader.txt then top_summoner.txt (same "Top Summoner: name - N" format)
    for path in (
        os.path.join(SCRIPT_DIR, "heat_leader.txt"),
        TOP_SUMMONER_FILE,
    ):
        try:
            if not os.path.exists(path):
                continue
            with open(path, encoding="utf-8") as f:
                line = f.readline().strip()
            prefix = "Top Summoner: "
            if not line.startswith(prefix):
                continue
            rest = line[len(prefix):]
            dash = rest.rfind(" - ")
            leader = rest[:dash].strip() if dash >= 0 else rest.strip()
            if leader.lower() == want:
                return True
        except OSError:
            continue
    return False


def grant_flat_donor_points(username, amount):
    """Flat donor-wallet grant with no earn multipliers (Channel Points, sprint, etc.).

    Increments both pts and donation_pts so the grant is spendable and counted as donor.
    Returns (ok, donor_total, added) or (False, 0, 0) on skip/busy.
    """
    if not username or str(username).strip().lower() in ("", "anonymous"):
        return False, 0, 0
    to_add = max(0, int(amount))
    if to_add <= 0:
        return False, 0, 0
    key = str(username).strip().lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            pts += to_add
            donation_pts += to_add
            data[key] = (pts, last, donation_pts, role)
            write_points(data)
        return True, donation_pts, to_add
    except TimeoutError:
        return False, 0, 0


def grant_sprint_donor_reward(username, amount=100):
    """Flat donor-wallet grant for Bestiary sprint winners (no earn multipliers)."""
    ok, _, _ = grant_flat_donor_points(username, amount)
    return ok


def donation_earn_multiplier(is_subscribed=False, is_sponsor=False, username=None):
    """Stack global 2×, top summoner 2×, and subscriber/member 2×; capped at donation_multiplier_cap."""
    m = 1
    if is_double_points_active():
        m *= 2
    if username and is_top_summoner(username):
        m *= 2
    if is_subscribed or is_sponsor:
        m *= 2
    cap = int(get_config().get("donation_multiplier_cap", 4))
    return min(max(1, cap), m)


def chat_pts(pts: int, donation_pts: int) -> int:
    """Chat-only balance (not donor wallet)."""
    return max(0, pts - donation_pts)


def _parse_positive_int(s, default=0):
    if s is None:
        return default
    t = str(s).strip().replace(",", "")
    if not t:
        return default
    try:
        return max(0, int(float(t)))
    except ValueError:
        return default


def _parse_micro_amount(s):
    """Super Chat amount: micro-units (1_000_000 = $1) or decimal/whole dollars."""
    if s is None:
        return 0
    t = str(s).strip().replace(",", "").replace("$", "")
    if not t:
        return 0
    try:
        if "." in t:
            return max(0, int(round(float(t) * 1_000_000)))
        v = int(t)
        if v < 10000:
            return v * 1_000_000
        return v
    except ValueError:
        try:
            return max(0, int(round(float(t) * 1_000_000)))
        except ValueError:
            return 0


def _looks_like_tier(s):
    t = str(s).strip().lower()
    return "tier" in t or t in ("prime", "1", "2", "3", "1000", "2000", "3000")


def gift_sub_points(tier_str, cfg):
    """Points for one gifted sub/membership (configurable per tier)."""
    tier = (tier_str or "tier 1").strip().lower()
    if "tier 3" in tier or tier in ("3", "3000"):
        base = int(cfg.get("points_per_gift_sub_tier3", 2500))
    elif "tier 2" in tier or tier in ("2", "2000"):
        base = int(cfg.get("points_per_gift_sub_tier2", 1000))
    elif "prime" in tier:
        base = int(cfg.get("points_per_gift_sub_prime", cfg.get("points_per_gift_sub_tier1", 500)))
    else:
        base = int(cfg.get("points_per_gift_sub_tier1", cfg.get("points_per_gift_membership", 500)))
    return base


def award_donation_points(username, to_add, is_subscribed=False, is_sponsor=False):
    """Add donation points. Returns (result_file, message)."""
    if not username or str(username).strip().lower() in ("", "anonymous"):
        return DONATION_RESULT_FILE, "skip|0"
    to_add = max(0, int(to_add))
    if to_add <= 0:
        return DONATION_RESULT_FILE, "ok|0"
    key = str(username).strip().lower()
    to_add *= donation_earn_multiplier(is_subscribed, is_sponsor, username=key)
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


def _donation_flags_from_args(args, start_idx):
    """Parse optional [isSubscribed] [userIsSponsor] from tail of args."""
    is_sub = _arg_bool(args[start_idx], False) if len(args) > start_idx else False
    is_sponsor = _arg_bool(args[start_idx + 1], False) if len(args) > start_idx + 1 else False
    return is_sub, is_sponsor


NATIVE_DEPTH = {
    "rat": 1, "albino": 1, "snake": 1, "gnoll": 2, "crab": 3, "slime": 4,
    "swarm": 3, "thief": 4, "skeleton": 6, "dm100": 7, "guard": 7,
    "necromancer": 8, "bat": 11, "brute": 11, "shaman": 11, "spinner": 12,
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
        "curse_class_kit_duration_turns": 100,
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
        "points_per_message": 2,
        "chat_cooldown_sec": 20,
        "passive_cooldown_sec": 60,
        "cooldown_bypass_users": ["DaltonGoesFast"],
        "first_words_bonus": 5,
        "chat_point_cap": 500,
        "bank_ratio_manual": 0.10,
        "bank_ratio_auto": 0.05,
        "bank_ratio_auto_member": 0.10,
        "donation_multiplier_cap": 4,
        "reset_debounce_hours": 4,
        "death_cost_inflation_enabled": True,
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
            "curse_class_kit_duration_turns": max(1, int(cfg.get("curse_class_kit_duration_turns", defaults["curse_class_kit_duration_turns"]))),
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
            "points_per_message": max(1, int(cfg.get("points_per_message", defaults["points_per_message"]))),
            "chat_cooldown_sec": max(0, int(cfg.get("chat_cooldown_sec", defaults["chat_cooldown_sec"]))),
            "passive_cooldown_sec": max(0, int(cfg.get("passive_cooldown_sec", defaults["passive_cooldown_sec"]))),
            "cooldown_bypass_users": [
                str(u).strip()
                for u in (cfg.get("cooldown_bypass_users") or defaults["cooldown_bypass_users"])
                if str(u).strip()
            ],
            "first_words_bonus": max(0, int(cfg.get("first_words_bonus", defaults["first_words_bonus"]))),
            "chat_point_cap": max(1, int(cfg.get("chat_point_cap", defaults["chat_point_cap"]))),
            "bank_ratio_manual": float(cfg.get("bank_ratio_manual", defaults["bank_ratio_manual"])),
            "bank_ratio_auto": float(cfg.get("bank_ratio_auto", defaults["bank_ratio_auto"])),
            "bank_ratio_auto_member": float(cfg.get("bank_ratio_auto_member", defaults["bank_ratio_auto_member"])),
            "donation_multiplier_cap": max(1, int(cfg.get("donation_multiplier_cap", defaults["donation_multiplier_cap"]))),
            "reset_debounce_hours": max(0, float(cfg.get("reset_debounce_hours", defaults["reset_debounce_hours"]))),
            "death_cost_inflation_enabled": bool(cfg.get(
                "death_cost_inflation_enabled",
                defaults["death_cost_inflation_enabled"],
            )),
        }
    except Exception:
        return defaults


def get_config():
    """Cached config (reloads each command to allow live edits)."""
    return load_config()


def is_death_cost_inflation_enabled() -> bool:
    """True when hero deaths should inflate harmful command costs."""
    return bool(get_config().get("death_cost_inflation_enabled", True))


def ignores_command_cooldowns(username: str) -> bool:
    """True if this user skips chat/passive/summon cooldowns (streamer testing)."""
    key = (username or "").strip().lower()
    if not key:
        return False
    for u in get_config().get("cooldown_bypass_users") or []:
        if str(u).strip().lower() == key:
            return True
    return False


def _parse_free_until_end(raw: Any) -> Optional[int]:
    try:
        return int(raw)
    except (TypeError, ValueError):
        return None


def _load_free_until_unlocked() -> dict[str, int]:
    if not os.path.exists(FREE_UNTIL_FILE):
        return {}
    try:
        with open(FREE_UNTIL_FILE, encoding="utf-8") as f:
            raw = json.load(f)
    except (json.JSONDecodeError, OSError):
        return {}
    if not isinstance(raw, dict):
        return {}
    out: dict[str, int] = {}
    for k, v in raw.items():
        key = str(k).strip()
        if not key or key.startswith("_"):
            continue
        end = _parse_free_until_end(v)
        if end is None:
            continue
        out[key] = end
    return out


def _save_free_until_unlocked(free: dict[str, int]) -> None:
    tmp = FREE_UNTIL_FILE + ".tmp"
    payload = json.dumps(free, indent=2)
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(payload)
        f.write("\n")
    os.replace(tmp, FREE_UNTIL_FILE)


def load_free_until() -> dict[str, int]:
    """Copy of cost_key -> unix end timestamp."""
    with _free_until_lock:
        return dict(_load_free_until_unlocked())


def save_free_until(free: dict[str, int]) -> None:
    cleaned: dict[str, int] = {}
    for k, v in (free or {}).items():
        key = str(k).strip()
        if not key or key.startswith("_"):
            continue
        end = _parse_free_until_end(v)
        if end is None:
            continue
        cleaned[key] = end
    with _free_until_lock:
        _save_free_until_unlocked(cleaned)


def is_free_until_clock_paused() -> bool:
    return bool(_free_clock_paused)


def tick_free_until_clock(paused: bool) -> int:
    """Shift active free windows forward while commands cannot run. Returns keys shifted."""
    global _free_clock_last_sec, _free_clock_paused
    now_sec = int(time.time())
    with _free_until_lock:
        was_paused = _free_clock_paused
        last = _free_clock_last_sec
        _free_clock_paused = bool(paused)
        _free_clock_last_sec = now_sec
        if last is None or not paused or not was_paused:
            return 0
        delta = now_sec - last
        if delta <= 0:
            return 0
        free = _load_free_until_unlocked()
        shifted = 0
        for key, end in list(free.items()):
            if end > last:
                free[key] = end + delta
                shifted += 1
        if shifted:
            _save_free_until_unlocked(free)
        return shifted


def extend_free_until_keys(cost_keys, duration_sec: int) -> dict[str, int]:
    """Add duration to each key: new_end = max(now, existing) + duration_sec."""
    now = int(time.time())
    duration_sec = max(1, int(duration_sec or 60))
    ends: dict[str, int] = {}
    with _free_until_lock:
        free = _load_free_until_unlocked()
        for key in cost_keys or []:
            k = str(key).strip()
            if not k:
                continue
            prev = _parse_free_until_end(free.get(k)) or 0
            new_end = max(now, prev) + duration_sec
            free[k] = new_end
            ends[k] = new_end
        if ends:
            _save_free_until_unlocked(free)
    return ends


def is_cost_free(cost_key):
    """True if cost_key is free until a future timestamp (from free_until.json)."""
    key = str(cost_key or "").strip()
    if not key:
        return False
    with _free_until_lock:
        free = _load_free_until_unlocked()
    end_ts = free.get(key)
    if end_ts is None:
        return False
    return int(time.time()) < int(end_ts)


def effective_cost(cost_key, base_cost):
    """Return 0 if cost is free, else base_cost."""
    return 0 if is_cost_free(cost_key) else base_cost


def _load_death_cost_state() -> dict:
    if not os.path.exists(DEATH_COST_STATE_FILE):
        return {"deaths": 0}
    try:
        with open(DEATH_COST_STATE_FILE, encoding="utf-8") as f:
            data = json.load(f)
        return {"deaths": max(0, int(data.get("deaths", 0)))}
    except (json.JSONDecodeError, OSError, TypeError, ValueError):
        return {"deaths": 0}


def _save_death_cost_state(state: dict) -> None:
    with open(DEATH_COST_STATE_FILE, "w", encoding="utf-8") as f:
        json.dump({"deaths": max(0, int(state.get("deaths", 0)))}, f)


def get_death_cost_deaths() -> int:
    return _load_death_cost_state()["deaths"]


def get_death_cost_multiplier() -> float:
    if not is_death_cost_inflation_enabled():
        return 1.0
    deaths = get_death_cost_deaths()
    if deaths <= 0:
        return 1.0
    return DEATH_COST_FACTOR ** deaths


def format_death_cost_display() -> str:
    """OBS-friendly one-liner; empty when costs are normal or feature is off."""
    if not is_death_cost_inflation_enabled():
        return ""
    deaths = get_death_cost_deaths()
    if deaths <= 0:
        return ""
    mult = get_death_cost_multiplier()
    pct = int(round((mult - 1.0) * 100))
    death_word = "death" if deaths == 1 else "deaths"
    return f"Harmful costs: {mult:.2f}x (+{pct}%, {deaths} {death_word})"


def refresh_death_cost_display_file() -> str:
    """Write death_cost_display.txt for OBS Read from file."""
    text = format_death_cost_display()
    try:
        with open(DEATH_COST_DISPLAY_FILE, "w", encoding="utf-8") as f:
            f.write(text)
            f.flush()
            os.fsync(f.fileno())
    except OSError:
        pass
    return text


def on_hero_death_cost_inflation() -> int:
    """Increment death counter (+50% harmful costs per death, compounded)."""
    if not is_death_cost_inflation_enabled():
        refresh_death_cost_display_file()
        return get_death_cost_deaths()
    state = _load_death_cost_state()
    state["deaths"] = state.get("deaths", 0) + 1
    _save_death_cost_state(state)
    refresh_death_cost_display_file()
    return state["deaths"]


def on_boss_slain_cost_inflation_reset() -> None:
    """Reset death-based cost inflation after a boss kill."""
    _save_death_cost_state({"deaths": 0})
    refresh_death_cost_display_file()


def apply_death_cost_inflation(command_id: str, cost: int) -> int:
    """Apply compound +50% per death to harmful commands; helpful commands exempt."""
    if cost <= 0 or not is_death_cost_inflation_enabled():
        return cost
    cmd = (command_id or "").lower()
    if cmd in DEATH_COST_EXEMPT_COMMANDS:
        return cost
    mult = get_death_cost_multiplier()
    if mult <= 1.0:
        return cost
    return max(1, int(round(cost * mult)))


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
    """Acquire exclusive OS-level lock on the lock file. Returns fd or None.

    Uses msvcrt/fcntl byte-range locks: the OS releases them automatically if the
    process dies, so a crash can never leave a stale lock behind. The lock file
    itself is persistent and never deleted (deleting would race other lockers)."""
    fd = os.open(POINTS_LOCK_FILE, os.O_CREAT | os.O_RDWR)
    start = time.monotonic()
    while (time.monotonic() - start) < POINTS_LOCK_TIMEOUT:
        try:
            if os.name == "nt":
                os.lseek(fd, 0, os.SEEK_SET)
                msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
            else:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            return fd
        except OSError:
            time.sleep(0.05)
    os.close(fd)
    return None


def _release_points_lock(fd):
    if fd is None:
        return
    try:
        if os.name == "nt":
            os.lseek(fd, 0, os.SEEK_SET)
            msvcrt.locking(fd, msvcrt.LK_UNLCK, 1)
        else:
            fcntl.flock(fd, fcntl.LOCK_UN)
    except OSError:
        pass
    try:
        os.close(fd)
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


def _acquire_spend_command_lock():
    """Serialize full spend pipelines so spawn_result.txt is not raced between Streamer.bot actions."""
    fd = os.open(SPEND_COMMAND_LOCK_FILE, os.O_CREAT | os.O_RDWR)
    start = time.monotonic()
    while (time.monotonic() - start) < SPEND_COMMAND_LOCK_TIMEOUT:
        try:
            if os.name == "nt":
                os.lseek(fd, 0, os.SEEK_SET)
                msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
            else:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            return fd
        except OSError:
            time.sleep(0.05)
    os.close(fd)
    return None


def _release_spend_command_lock(fd):
    if fd is None:
        return
    try:
        if os.name == "nt":
            os.lseek(fd, 0, os.SEEK_SET)
            msvcrt.locking(fd, msvcrt.LK_UNLCK, 1)
        else:
            fcntl.flock(fd, fcntl.LOCK_UN)
    except OSError:
        pass
    try:
        os.close(fd)
    except OSError:
        pass


@contextmanager
def spend_command_lock():
    """Hold for an entire spend command (reserve + game round-trip + result write)."""
    fd = _acquire_spend_command_lock()
    if fd is None:
        raise TimeoutError("Another spend command is in progress")
    try:
        yield
    finally:
        _release_spend_command_lock(fd)


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


def compute_spawn_cost(monster: str, depth: int | None = None) -> int:
    """Late discount when deeper than native; early surcharge when shallower (+20% per tier step above baseline)."""
    cfg = get_config()
    base = cfg["cost_per_monster"].get(monster, cfg["default_monster_cost"])
    if depth is None:
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


def compute_champion_cost(monster: str, depth: int | None = None) -> int:
    """2× zone-adjusted spawn cost (same early/late rules as !spawn)."""
    return 2 * compute_spawn_cost(monster, depth)


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


def _trace(label):
    """Append timing line for debugging slow commands (check points_command_trace.log)."""
    try:
        with open(POINTS_COMMAND_TRACE_FILE, "a", encoding="utf-8") as f:
            f.write(f"{time.strftime('%H:%M:%S')} pid={os.getpid()} {label}\n")
    except OSError:
        pass


def _write_result(cmd, result_file, msg):
    """Write spawn/donation result for Streamer.bot C# and keep a debug copy."""
    with open(result_file, "w", encoding="utf-8") as f:
        f.write(msg)
    if result_file == SPAWN_RESULT_FILE:
        try:
            with open(SPAWN_RESULT_LAST_FILE, "w", encoding="utf-8") as f:
                f.write(f"{cmd}|{msg}\n")
        except OSError:
            pass
    print(msg, flush=True)


def _caller_username(args):
    """Trusted chat user from Streamer.bot (%userName%) — always the last CLI arg."""
    return (args[-1] or "").strip()


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


def reserve_points(username, command_id, base_cost, detail=""):
    """Deduct cost up-front under a short-lived lock (reserve-then-refund pattern).

    Returns (reservation, None) on success or (None, error_msg) on failure.
    The lock is held only for the read-modify-write, never during the game
    round-trip. If the game call later fails, call refund_points(reservation)."""
    key = username.lower()
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            ok, err = check_command_access(command_id, role)
            if not ok:
                return None, err
            cost = apply_role_discount(base_cost, command_id, role)
            cost = apply_death_cost_inflation(command_id, cost)
            total = effective_total(pts, donation_pts)
            if total < cost:
                return None, not_enough_points_msg(username, cost, total, detail)
            new_pts, new_donation = deduct_points(pts, donation_pts, cost)
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return {
                "key": key,
                "cost": cost,
                "new_pts": new_pts,
                "donation_used": donation_pts - new_donation,
            }, None
    except TimeoutError:
        return None, "Points file busy. Please try again in a moment."


def cmd_balance(args):
    if len(args) != 1:
        return BALANCE_RESULT_FILE, "Usage: balance <username>"
    username = args[0].strip()
    if not username:
        return BALANCE_RESULT_FILE, "ok|0|0|500"
    try:
        with points_lock():
            data = read_points()
            pts, _, donation_pts, _ = _get_user_data(data, username.lower())
            cap = int(get_config().get("chat_point_cap", 500))
            c = chat_pts(pts, donation_pts)
            return BALANCE_RESULT_FILE, f"ok|{c}|{donation_pts}|{cap}"
    except TimeoutError:
        return BALANCE_RESULT_FILE, "Points file busy. Please try again in a moment."


def cmd_bank(args):
    """CLI: bank <username> [all|<amount>] — all chat pts if amount omitted."""
    if len(args) < 1:
        return BALANCE_RESULT_FILE, "Usage: bank <username> [all|<amount>]"
    username = args[0].strip()
    if not username:
        return BALANCE_RESULT_FILE, "Usage: bank <username> [all|<amount>]"
    amount_arg = args[1].strip().lower() if len(args) > 1 else "all"
    try:
        with points_lock():
            data = read_points()
            key = username.lower()
            pts, last, donation_pts, role = _get_user_data(data, key)
            cfg = get_config()
            ratio = float(cfg.get("bank_ratio_manual", 0.10))
            c = chat_pts(pts, donation_pts)
            if amount_arg == "all":
                amount = c
            else:
                try:
                    amount = int(amount_arg)
                except ValueError:
                    return BALANCE_RESULT_FILE, f"invalid|{c}"
            if amount < 1:
                return BALANCE_RESULT_FILE, "invalid|0"
            if amount > c:
                return BALANCE_RESULT_FILE, f"excess|{c}"
            donor_gain = int(amount * ratio)
            new_donation = donation_pts + donor_gain
            new_pts = pts - amount + donor_gain
            data[key] = (new_pts, last, new_donation, role)
            write_points(data)
            return BALANCE_RESULT_FILE, f"ok|{amount}|{donor_gain}|{new_donation}"
    except TimeoutError:
        return BALANCE_RESULT_FILE, "Points file busy. Please try again in a moment."


def refund_points(res):
    """Give reserved points back after a failed game call (additive, so safe even if the balance changed meanwhile)."""
    if not res:
        return
    if res["cost"] <= 0 and res["donation_used"] <= 0:
        return
    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, res["key"])
            data[res["key"]] = (pts + res["cost"], last, donation_pts + res["donation_used"], role)
            write_points(data)
    except TimeoutError:
        pass


def _read_spend_actions():
    """Load spend ledger entries (newest last). Missing/corrupt file → []."""
    if not os.path.exists(SPEND_ACTIONS_FILE):
        return []
    out = []
    try:
        with open(SPEND_ACTIONS_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    out.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    except OSError:
        return []
    return out


def _write_spend_actions(entries):
    """Rewrite ledger, keeping at most SPEND_ACTIONS_MAX entries."""
    trimmed = entries[-SPEND_ACTIONS_MAX:] if len(entries) > SPEND_ACTIONS_MAX else entries
    tmp = SPEND_ACTIONS_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        for e in trimmed:
            f.write(json.dumps(e, ensure_ascii=False) + "\n")
    os.replace(tmp, SPEND_ACTIONS_FILE)


def record_confirmed_spend(spend_id, res, command, detail=""):
    """Append a confirmed (non-refunded) spend for Activity undo. Returns spend_id."""
    if not spend_id or not res:
        return spend_id
    entry = {
        "id": spend_id,
        "ts": int(time.time() * 1000),
        "user": res.get("key") or "",
        "command": command or "",
        "detail": detail or "",
        "cost": int(res.get("cost") or 0),
        "donation_used": int(res.get("donation_used") or 0),
        "refunded": False,
    }
    try:
        with points_lock():
            entries = _read_spend_actions()
            entries.append(entry)
            _write_spend_actions(entries)
    except (TimeoutError, OSError):
        pass
    return spend_id


def refund_spend_action(spend_id):
    """Refund a confirmed spend by ledger id. Returns dict with ok/error fields."""
    sid = (spend_id or "").strip()
    if not sid:
        return {"ok": False, "error": "Missing spend id"}
    try:
        with points_lock():
            entries = _read_spend_actions()
            idx = None
            entry = None
            for i, e in enumerate(entries):
                if e.get("id") == sid:
                    idx = i
                    entry = e
                    break
            if entry is None:
                return {"ok": False, "error": "Spend action not found"}
            if entry.get("refunded"):
                return {"ok": False, "error": "Already refunded"}
            cost = int(entry.get("cost") or 0)
            donation_used = int(entry.get("donation_used") or 0)
            if cost <= 0 and donation_used <= 0:
                return {"ok": False, "error": "Nothing to refund"}
            key = (entry.get("user") or "").strip().lower()
            if not key:
                return {"ok": False, "error": "Missing username on spend action"}
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)
            data[key] = (pts + cost, last, donation_pts + donation_used, role)
            write_points(data)
            entry = dict(entry)
            entry["refunded"] = True
            entry["refunded_at"] = int(time.time() * 1000)
            entries[idx] = entry
            _write_spend_actions(entries)
            return {
                "ok": True,
                "username": key,
                "cost": cost,
                "donation_used": donation_used,
                "command": entry.get("command") or "",
                "detail": entry.get("detail") or "",
            }
    except TimeoutError:
        return {"ok": False, "error": "Points file busy. Please try again in a moment."}
    except OSError as e:
        return {"ok": False, "error": str(e)}


def _mark_spend_action_refunded(spend_id):
    """Mark ledger entry refunded without changing balances (e.g. after auto-refund)."""
    sid = (spend_id or "").strip()
    if not sid:
        return
    try:
        with points_lock():
            entries = _read_spend_actions()
            changed = False
            for i, e in enumerate(entries):
                if e.get("id") == sid and not e.get("refunded"):
                    e = dict(e)
                    e["refunded"] = True
                    e["refunded_at"] = int(time.time() * 1000)
                    e["auto_refund"] = True
                    entries[i] = e
                    changed = True
                    break
            if changed:
                _write_spend_actions(entries)
    except (TimeoutError, OSError):
        pass


def _post_game_command(endpoint, payload, fail_prefix, timeout_msg):
    """POST a command to the overlay server. Returns (body, None) on success or (None, error_msg)."""
    url = "http://127.0.0.1:5000" + endpoint
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method="POST")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=GAME_COMMAND_TIMEOUT) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
        if not raw.strip():
            return None, f"{fail_prefix} (empty response from server)"
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            return None, f"{fail_prefix} (server error). Is the overlay running?"
        if not body.get("ok"):
            return None, body.get("error", fail_prefix)
        return body, None
    except urllib.error.HTTPError as e:
        return None, _http_error_msg(e, timeout_msg)
    except urllib.error.URLError:
        return None, "Overlay server not reachable. Is it running?"
    except Exception as e:
        msg = str(e).strip() if e else ""
        return None, f"{fail_prefix}. " + (msg if msg else "Check overlay server and try again.")


def _post_spend_command(endpoint, res, command, detail, payload, fail_prefix, timeout_msg):
    """POST a spend with spend_id/cost; ledger before game call; refund+mark on failure.

    Returns (body, None) on success or (None, error_msg) on failure.
    On success, grants co-op Bestiary bar XP from the deducted cost (sprint/heat untouched).
    """
    global _last_spend_bestiary
    _last_spend_bestiary = None
    spend_id = str(uuid.uuid4())
    enriched = dict(payload or {})
    enriched["spend_id"] = spend_id
    enriched["cost"] = int((res or {}).get("cost") or 0)
    # Record before game round-trip so Activity Refund never races a missing ledger row.
    record_confirmed_spend(spend_id, res, command, detail)
    body, err = _post_game_command(endpoint, enriched, fail_prefix, timeout_msg)
    if err:
        refund_points(res)
        _mark_spend_action_refunded(spend_id)
        return None, err
    # Game confirmed — grant bar XP. Never refund the spend if bestiary fails.
    try:
        from summon_bestiary import apply_bar_xp, spend_xp_from_cost
        cost = int((res or {}).get("cost") or 0)
        xp = spend_xp_from_cost(cost)
        user_key = (res or {}).get("key") or ""
        if xp > 0 and user_key:
            _last_spend_bestiary = apply_bar_xp(user_key, xp)
        else:
            _last_spend_bestiary = {
                "ok": True,
                "bar_xp_added": 0,
                "leveled_up": False,
                "level_up": None,
            }
    except Exception as e:
        print(f"[points] bestiary bar XP after spend failed: {e}")
        _last_spend_bestiary = None
    return body, None


def _take_spend_bestiary() -> Optional[dict[str, Any]]:
    """Consume the last spend's Bestiary grant for chat formatting."""
    global _last_spend_bestiary
    out = _last_spend_bestiary
    _last_spend_bestiary = None
    return out


def _with_spend_bestiary_chat(result: "ChatResult") -> "ChatResult":
    """Append +N Bestiary XP / level-up to a successful spend ChatResult."""
    if not result.ok or not result.message:
        _take_spend_bestiary()
        return result
    bestiary = _take_spend_bestiary()
    if not bestiary:
        return result
    import chat_messages
    added = int(bestiary.get("bar_xp_added") or 0)
    if added > 0:
        result.message = result.message + chat_messages.spend_bestiary_xp(added)
    progress = chat_messages.bestiary_progress_line(bestiary)
    if progress:
        result.message = result.message + " " + progress
    result.message = result.message + chat_messages.shatter_events_suffix(
        bestiary.get("shatter_events") or []
    )
    extra = dict(result.extra or {})
    extra["bestiary_xp"] = added
    extra["bestiary_leveled_up"] = bool(bestiary.get("leveled_up"))
    extra["bestiary_halls_looped"] = bool(bestiary.get("halls_looped"))
    extra["shatter_events"] = list(bestiary.get("shatter_events") or [])
    if bestiary.get("level") is not None:
        extra["bestiary_level"] = int(bestiary.get("level") or 1)
    result.extra = extra
    return result


def cmd_spawn(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) != 2:
        return SPAWN_RESULT_FILE, "Usage: !spawn <monster> (e.g. !spawn rat)"
    monster = args[0].lower()
    username = _caller_username(args)
    if monster not in VALID_MONSTERS:
        return SPAWN_RESULT_FILE, f"Unknown monster: {monster}"

    base_cost = effective_cost("cost_per_monster." + monster, compute_spawn_cost(monster))
    res, err = reserve_points(username, "spawn", base_cost)
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/spawn-command", res, "spawn", monster,
        {"monster": monster, "username": username},
        "Spawn failed", "Spawn timed out. Is the game running and in an active run (not title screen)?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    return SPAWN_RESULT_FILE, f"ok|{res['new_pts']}"


def cmd_champion(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) != 2:
        return SPAWN_RESULT_FILE, "Usage: !champion <monster> (e.g. !champion rat). Costs 2× zone-adjusted spawn cost."
    monster = args[0].lower()
    username = _caller_username(args)
    if monster not in VALID_MONSTERS:
        return SPAWN_RESULT_FILE, f"Unknown monster: {monster}"

    base_cost = effective_cost("cost_per_monster." + monster, compute_champion_cost(monster))
    res, err = reserve_points(username, "champion", base_cost, f"for champion {monster}")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/champion-command", res, "champion", monster,
        {"monster": monster, "username": username},
        "Champion spawn failed", "Champion spawn timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    return SPAWN_RESULT_FILE, "ok|" + body.get("monster", monster) + f"|{res['new_pts']}"


def cmd_gold(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) != 2:
        return SPAWN_RESULT_FILE, "Usage: !gold <amount> (e.g. !gold 10)"
    try:
        amount = int(args[0])
    except ValueError:
        return SPAWN_RESULT_FILE, "Usage: !gold <amount> (e.g. !gold 10). Amount must be 1-100."
    if amount < 1 or amount > 100:
        return SPAWN_RESULT_FILE, "Amount must be 1-100. Example: !gold 10"
    username = _caller_username(args)

    base_cost = effective_cost("cost_per_gold", amount * get_config()["cost_per_gold"])
    res, err = reserve_points(username, "gold", base_cost, f"for {amount} gold")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/gold-command", res, "gold", str(amount),
        {"amount": amount, "username": username},
        "Gold drop failed", "Gold drop timed out. Is the game running and in an active run (not title screen)?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    return SPAWN_RESULT_FILE, f"ok|{amount}|{res['new_pts']}"


def cmd_curse(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !curse (curses a random equipped item)"
    username = args[0]

    base_cost = effective_cost("cost_per_curse", get_config()["cost_per_curse"])
    scaled_cost = curse_cost_for_equipped_curses(base_cost, count_known_cursed_equipped())
    res, err = reserve_points(username, "curse", scaled_cost, "to curse")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/curse-command", res, "curse", "",
        {
            "username": username,
            "class_kit_curse_duration_turns": get_config()["curse_class_kit_duration_turns"],
        },
        "Curse failed", "Curse timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    item_name = body.get("item_name", "item")
    if body.get("temporary"):
        duration = body.get("duration_turns", get_config()["curse_class_kit_duration_turns"])
        return SPAWN_RESULT_FILE, f"ok|{item_name}|{res['new_pts']}|temporary|{duration}"
    return SPAWN_RESULT_FILE, f"ok|{item_name}|{res['new_pts']}"


def cmd_gas(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !gas (spawns random gas near you)"
    username = args[0]

    base_cost = effective_cost("cost_per_gas", get_config()["cost_per_gas"])
    res, err = reserve_points(username, "gas", base_cost, "to spew gas")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/gas-command", res, "gas", "",
        {"username": username},
        "Gas spawn failed", "Gas command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    gas_name = body.get("gas_name", "gas")
    return SPAWN_RESULT_FILE, f"ok|{gas_name}|{res['new_pts']}"


def cmd_scroll(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !scroll (uses a random scroll like +10 Unstable Spellbook)"
    username = args[0]

    base_cost = effective_cost("cost_per_scroll", get_config()["cost_per_scroll"])
    res, err = reserve_points(username, "scroll", base_cost, "for random scroll")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/scroll-command", res, "scroll", "",
        {"username": username},
        "Scroll command failed", "Scroll command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    scroll_name = body.get("scroll_name", "scroll")
    return SPAWN_RESULT_FILE, f"ok|{scroll_name}|{res['new_pts']}"


def cmd_row(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !row (Ring of Wealth bonus loot near you, always at least one item)"
    username = args[0]

    base_cost = effective_cost("cost_per_ring_of_wealth", get_config()["cost_per_ring_of_wealth"])
    res, err = reserve_points(username, "ring_of_wealth", base_cost, "for Ring of Wealth loot")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/ring-of-wealth-command", res, "ring_of_wealth", "",
        {"username": username},
        "Ring of wealth command failed", "Ring of wealth command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    detail = body.get("detail", "loot")
    return SPAWN_RESULT_FILE, f"ok|{detail}|{res['new_pts']}"


def cmd_trap(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !trap (places a random visible trap near you)"
    username = args[0]

    base_cost = effective_cost("cost_per_trap", get_config()["cost_per_trap"])
    res, err = reserve_points(username, "trap", base_cost, "to place a trap")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/trap-command", res, "trap", "",
        {"username": username},
        "Trap command failed", "Trap command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    trap_name = body.get("trap_name", "trap")
    return SPAWN_RESULT_FILE, f"ok|{trap_name}|{res['new_pts']}"


def cmd_bomb(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !bomb (drops a weighted random lit bomb near you)"
    username = args[0]

    base_cost = effective_cost("cost_per_bomb", get_config()["cost_per_bomb"])
    res, err = reserve_points(username, "bomb", base_cost, "for !bomb")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/bomb-command", res, "bomb", "",
        {"username": username},
        "Bomb command failed", "Bomb command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    bomb_name = body.get("bomb_name", "bomb")
    return SPAWN_RESULT_FILE, f"ok|{bomb_name}|{res['new_pts']}"


def cmd_transmute(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !transmute (transmutes a random transmutable item from bag or equipped)"
    username = args[0]

    base_cost = effective_cost("cost_per_transmute", get_config()["cost_per_transmute"])
    res, err = reserve_points(username, "transmute", base_cost, "to transmute")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/transmute-command", res, "transmute", "",
        {"username": username},
        "Transmute command failed", "Transmute command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    # Transmute: echo both item names for Streamer.bot/Twitch (matches in-game GLog when game sends original_item_name).
    item_name = (body.get("item_name") or "item").strip()
    original_item_name = (body.get("original_item_name") or "").strip()
    return SPAWN_RESULT_FILE, f"ok|{original_item_name}|{item_name}|{res['new_pts']}"


def cmd_ally_bee(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !bee (summons an allied bee for 150 turns, 75 pts)"
    username = args[0]

    base_cost = effective_cost("cost_per_ally_bee", get_config()["cost_per_ally_bee"])
    res, err = reserve_points(username, "bee", base_cost, "to summon a bee")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/summon-bee-command", res, "summon_bee", "",
        {"username": username},
        "Summon bee failed", "Summon bee timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    ally_name = body.get("ally_name", "Bee")
    return SPAWN_RESULT_FILE, f"ok|{ally_name}|{res['new_pts']}"


def cmd_ward(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !ward (summons a ward, 30 pts, scales with depth)"
    username = args[0]

    base_cost = effective_cost("cost_per_ward", get_config()["cost_per_ward"])
    res, err = reserve_points(username, "ward", base_cost, "to summon a ward")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/ward-command", res, "ward", "",
        {"username": username},
        "Summon ward failed", "Summon ward timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    ward_name = body.get("ward_name", "Ward")
    return SPAWN_RESULT_FILE, f"ok|{ward_name}|{res['new_pts']}"


def cmd_buff(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !buff (gives a random buff)"
    username = args[0]

    base_cost = effective_cost("cost_per_buff", get_config()["cost_per_buff"])
    res, err = reserve_points(username, "buff", base_cost, "for random buff")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/buff-command", res, "buff", "",
        {"username": username},
        "Buff command failed", "Buff command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    buff_name = body.get("buff_name", "buff")
    return SPAWN_RESULT_FILE, f"ok|{buff_name}|{res['new_pts']}"


def cmd_debuff(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !debuff (gives a random debuff)"
    username = args[0]

    base_cost = effective_cost("cost_per_debuff", get_config()["cost_per_debuff"])
    res, err = reserve_points(username, "debuff", base_cost, "for random debuff")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/debuff-command", res, "debuff", "",
        {"username": username},
        "Debuff command failed", "Debuff command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    debuff_name = body.get("debuff_name", "debuff")
    return SPAWN_RESULT_FILE, f"ok|{debuff_name}|{res['new_pts']}"


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
    base_cost = effective_cost("cost_per_wand", cfg["cost_per_wand"])
    res, err = reserve_points(username, "wand", base_cost)
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/wand-command", res, "wand", "",
        {"username": username},
        "Wand command failed", "Wand command timed out. Is the game running and in an active run?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    effect_name = body.get("effect_name", "effect")
    return SPAWN_RESULT_FILE, f"ok|{effect_name}|{res['new_pts']}"


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
    micro_amount = _parse_micro_amount(args[0])
    currency = (args[1] or "USD").upper()[:3]
    username = args[2]
    is_sub, is_sponsor = _donation_flags_from_args(args, 3)

    amount_in_currency = micro_amount / 1_000_000
    rate = fetch_usd_rate(currency)
    amount_usd = amount_in_currency * rate
    to_add = max(0, int(round(amount_usd * 100)))
    return award_donation_points(username, to_add, is_sub, is_sponsor)


def cmd_cheer(args):
    if len(args) < 2:
        return DONATION_RESULT_FILE, "invalid|0"
    bits = _parse_positive_int(args[0], 0)
    username = args[1]
    is_sub, is_sponsor = _donation_flags_from_args(args, 2)
    return award_donation_points(username, bits, is_sub, is_sponsor)


def cmd_giftmembership(args):
    """Award points for a gifted sub (Twitch) or gift membership (YouTube).

    Args: <username> [tier] [isSubscribed] [userIsSponsor]
    Twitch: credit %recipientUserName%. YouTube gift membership: use %gifterUserName% (no recipient in API).
    """
    if len(args) < 1:
        return DONATION_RESULT_FILE, "invalid|0"
    username = args[0]
    tier = "tier 1"
    flag_idx = 1
    if len(args) > 1 and _looks_like_tier(args[1]):
        tier = args[1]
        flag_idx = 2
    is_sub, is_sponsor = _donation_flags_from_args(args, flag_idx)
    to_add = gift_sub_points(tier, get_config())
    return award_donation_points(username, to_add, is_sub, is_sponsor)


def cmd_heal(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !heal (heals hero ~15% HP)"
    username = args[0]
    base_cost = effective_cost("cost_per_heal", get_config()["cost_per_heal"])
    res, err = reserve_points(username, "heal", base_cost, "to heal")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/heal-command", res, "heal", "",
        {"username": username},
        "Heal failed", "Heal timed out. Is the game running?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    return SPAWN_RESULT_FILE, f"ok|Healing|{res['new_pts']}"


def cmd_cleanse(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !cleanse (removes one random debuff)"
    username = args[0]
    base_cost = effective_cost("cost_per_cleanse", get_config()["cost_per_cleanse"])
    res, err = reserve_points(username, "cleanse", base_cost, "to cleanse")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/cleanse-command", res, "cleanse", "",
        {"username": username},
        "Cleanse failed", "Cleanse timed out. Is the game running?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    buff_name = body.get("buff_name", "debuff")
    return SPAWN_RESULT_FILE, f"ok|{buff_name}|{res['new_pts']}"


def cmd_dew(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !dew (drops a dewdrop near hero)"
    username = args[0]
    base_cost = effective_cost("cost_per_dew", get_config()["cost_per_dew"])
    res, err = reserve_points(username, "dew", base_cost, "for dewdrop")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/dew-command", res, "dew", "",
        {"username": username},
        "Dew failed", "Dew timed out. Is the game running?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    return SPAWN_RESULT_FILE, f"ok|Dewdrop|{res['new_pts']}"


def cmd_plant(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !plant (plants a random plant near hero; fails if Barren Land enabled)"
    username = args[0]
    base_cost = effective_cost("cost_per_plant", get_config()["cost_per_plant"])
    res, err = reserve_points(username, "plant", base_cost, "to plant")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/plant-command", res, "plant", "",
        {"username": username},
        "Plant failed", "Plant timed out. Is the game running?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    plant_name = body.get("plant_name", "plant")
    return SPAWN_RESULT_FILE, f"ok|{plant_name}|{res['new_pts']}"


def cmd_corrupt_ally(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !corruptally (summons a corrupted ally from the current biome)"
    username = args[0]
    base_cost = effective_cost("cost_per_corrupt_ally", get_config()["cost_per_corrupt_ally"])
    res, err = reserve_points(username, "corrupt_ally", base_cost, "to summon corrupted ally")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/corrupt-ally-command", res, "corrupt_ally", "",
        {"username": username},
        "Corrupt ally failed", "Corrupt ally timed out. Is the game running?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    mob_name = body.get("mob_name", "ally")
    return SPAWN_RESULT_FILE, f"ok|{mob_name}|{res['new_pts']}"


def cmd_hex(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !hex (applies Hex debuff)"
    username = args[0]
    base_cost = effective_cost("cost_per_hex", get_config()["cost_per_hex"])
    res, err = reserve_points(username, "hex", base_cost, "to hex")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/hex-command", res, "hex", "",
        {"username": username},
        "Hex failed", "Hex timed out. Is the game running?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    return SPAWN_RESULT_FILE, f"ok|Hex|{res['new_pts']}"


def cmd_degrade(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !degrade (applies Degrade debuff)"
    username = args[0]
    base_cost = effective_cost("cost_per_degrade", get_config()["cost_per_degrade"])
    res, err = reserve_points(username, "degrade", base_cost, "to degrade")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/degrade-command", res, "degrade", "",
        {"username": username},
        "Degrade failed", "Degrade timed out. Is the game running?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    return SPAWN_RESULT_FILE, f"ok|Degrade|{res['new_pts']}"


def cmd_sabotage(args):
    if is_spend_disabled():
        return SPAWN_RESULT_FILE, "Spending is currently disabled by the streamer."
    if len(args) < 1:
        return SPAWN_RESULT_FILE, "Usage: !sabotage (removes one random buff)"
    username = args[0]
    base_cost = effective_cost("cost_per_sabotage", get_config()["cost_per_sabotage"])
    res, err = reserve_points(username, "sabotage", base_cost, "to sabotage")
    if err:
        return SPAWN_RESULT_FILE, err

    body, err = _post_spend_command(
        "/api/sabotage-command", res, "sabotage", "",
        {"username": username},
        "Sabotage failed", "Sabotage timed out. Is the game running?"
    )
    if err:
        return SPAWN_RESULT_FILE, err
    buff_name = body.get("buff_name", "buff")
    return SPAWN_RESULT_FILE, f"ok|{buff_name}|{res['new_pts']}"


def cmd_transfer(args):
    """Transfer points from one viewer to another.

    Deduction order: chat-earned points first, then donation-backed points if needed.
    Recipient receives chat points, clipped to remaining room under chat_point_cap.
    """
    if len(args) != 3:
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
    from_username = _caller_username(args)
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

            import chat_messages
            cap = int(get_config().get("chat_point_cap", 500))
            to_chat = chat_pts(to_pts, to_donation_pts)
            room = max(0, cap - to_chat)
            if room <= 0:
                return SPAWN_RESULT_FILE, chat_messages.givepoints_at_cap(to_display, cap)

            credit = min(amount, room)
            deducted = deduct_points(from_pts, from_donation_pts, credit)
            if deducted is None:
                return SPAWN_RESULT_FILE, f"{from_display}, not enough points. You have {from_total}."
            new_from_pts, new_from_donation = deducted

            to_total = effective_total(to_pts, to_donation_pts)
            new_to_pts = to_total + credit
            new_to_donation = to_donation_pts

            data[from_key] = (new_from_pts, from_last, new_from_donation, from_role)
            data[to_key] = (new_to_pts, to_last, new_to_donation, to_role)
            write_points(data)

            from_remaining = effective_total(new_from_pts, new_from_donation)
            return SPAWN_RESULT_FILE, chat_messages.givepoints_success(
                from_display, credit, to_display, from_remaining, capped=credit < amount
            )
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
    "giftmembership": cmd_giftmembership,
    "gift_membership": cmd_giftmembership,
    "giftsub": cmd_giftmembership,
    "balance": cmd_balance,
    "bank": cmd_bank,
}

# Commands that run through the spend pipeline lock (CLI still writes spawn_result.txt).
SPEND_PIPELINE_COMMANDS = frozenset(k for k in COMMANDS if k not in (
    "superchat", "cheer", "giftmembership", "gift_membership", "giftsub", "balance", "bank",
))


@dataclass
class ChatResult:
    """Structured result for /api/chat-command (replaces spawn_result.txt in HTTP path)."""
    ok: bool
    message: Optional[str] = None
    pts: Optional[int] = None
    earned: int = 0
    extra: Optional[dict[str, Any]] = None
    presentation: Optional[dict[str, Any]] = None

    def to_api_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {
            "ok": self.ok,
            "message": self.message,
            "pts": self.pts,
            "earned": self.earned,
        }
        if self.extra is not None:
            out["extra"] = self.extra
        if self.presentation is not None:
            out["presentation"] = self.presentation
        return out


def chat_earn_multiplier(username: str, is_subscribed: bool = False, is_member: bool = False) -> int:
    """Stack global 2x, top summoner 2x, sub/member 2x for chat/passive/first-words earn."""
    m = 1
    if is_double_points_active():
        m *= 2
    if username and is_top_summoner(username):
        m *= 2
    if is_subscribed or is_member:
        m *= 2
    return m


def _legacy_ok_parts(msg: str) -> Optional[list[str]]:
    if not msg or not msg.startswith("ok|"):
        return None
    return msg.split("|")


def legacy_to_chat_result(cmd: str, msg: str, username: str, cmd_args: list) -> ChatResult:
    """Convert spawn_result.txt / balance line into a ChatResult with viewer-facing message."""
    import chat_messages

    if msg.startswith("excess|"):
        chat_avail = int(msg.split("|", 1)[1])
        return ChatResult(ok=False, message=chat_messages.bank_excess(username, chat_avail), extra={"command": "bank"})
    if msg.startswith("invalid|"):
        return ChatResult(ok=False, message=chat_messages.bank_invalid_amount(username), extra={"command": "bank"})
    if msg.startswith("preview|"):
        c, gain, pct = [int(x) for x in msg.split("|")[1:4]]
        return ChatResult(
            ok=True,
            message=chat_messages.bank_preview(username, c, gain, pct),
            extra={"command": "bank", "preview": True},
        )

    parts = _legacy_ok_parts(msg)
    if parts:
        if cmd == "balance":
            c = int(parts[1])
            donor = int(parts[2]) if len(parts) > 2 else 0
            cap = int(parts[3]) if len(parts) > 3 else int(get_config().get("chat_point_cap", 500))
            total = c + donor
            return ChatResult(
                ok=True,
                message=chat_messages.points_balance_v11(username, c, cap, donor),
                pts=total,
                extra={"command": "points", "chat_pts": c, "donor_pts": donor, "chat_cap": cap},
            )
        if cmd == "bank" and len(parts) >= 4:
            amount, gain, donor_total = int(parts[1]), int(parts[2]), int(parts[3])
            return ChatResult(
                ok=True,
                message=chat_messages.bank_success(username, amount, gain, donor_total),
                extra={"command": "bank", "chat_amount": amount, "donor_gain": gain, "donor_pts": donor_total},
            )
        if cmd == "transmute" and len(parts) >= 4:
            extra_name = parts[2].strip() or parts[1].strip()
            pts = int(parts[-1])
            return ChatResult(
                ok=True,
                message=chat_messages.spend_success("transmute", username, pts, extra_name),
                pts=pts,
                extra={"command": cmd, "item": extra_name},
            )
        if cmd == "curse" and len(parts) >= 5 and parts[3] == "temporary":
            extra = parts[1].strip()
            pts = int(parts[2])
            duration = parts[4].strip()
            return ChatResult(
                ok=True,
                message=chat_messages.spend_success("curse_temporary", username, pts, extra, duration),
                pts=pts,
                extra={"command": cmd, "detail": extra, "temporary": True, "duration_turns": int(duration)},
            )
        if len(parts) >= 3:
            extra = parts[1].strip()
            pts = int(parts[-1])
            display_cmd = "corruptally" if cmd in ("corrupt_ally", "corruptally") else cmd
            return ChatResult(
                ok=True,
                message=chat_messages.spend_success(display_cmd, username, pts, extra),
                pts=pts,
                extra={"command": cmd, "detail": extra},
            )
        if len(parts) == 2:
            pts = int(parts[1])
            if cmd in ("spawn", "champion"):
                monster = cmd_args[0] if cmd_args else "monster"
                return ChatResult(
                    ok=True,
                    message=chat_messages.spend_success(cmd, username, pts, monster),
                    pts=pts,
                    extra={"command": cmd, "monster": monster},
                )
            return ChatResult(
                ok=True,
                message=chat_messages.spend_success(cmd, username, pts, ""),
                pts=pts,
                extra={"command": cmd},
            )

    if cmd == "transfer" and " gave " in msg:
        return ChatResult(ok=True, message=msg, extra={"command": "givepoints"})

    return ChatResult(ok=False, message=msg, extra={"command": cmd})


def run_points_command(cmd: str, cmd_args: list, username: str = "") -> ChatResult:
    """Run a points_command handler and return structured ChatResult."""
    global _last_spend_bestiary
    cmd = cmd.lower()
    if cmd not in COMMANDS:
        import chat_messages
        return ChatResult(ok=False, message=chat_messages.unknown_command(cmd))

    user = username or (_caller_username(cmd_args) if cmd_args else "")
    try:
        if cmd in SPEND_PIPELINE_COMMANDS:
            with spend_command_lock():
                _last_spend_bestiary = None
                result_file, msg = COMMANDS[cmd](cmd_args)
                result = legacy_to_chat_result(cmd, msg, user, cmd_args)
                if cmd not in ("transfer", "givepoints"):
                    result = _with_spend_bestiary_chat(result)
                else:
                    _take_spend_bestiary()
                return result
        result_file, msg = COMMANDS[cmd](cmd_args)
    except TimeoutError:
        import chat_messages
        _last_spend_bestiary = None
        return ChatResult(ok=False, message=chat_messages.COMMAND_IN_PROGRESS)

    return legacy_to_chat_result(cmd, msg, user, cmd_args)


def main():
    args = [a.strip() for a in sys.argv[1:] if a.strip()]
    if len(args) < 1:
        _write_result("", SPAWN_RESULT_FILE, "Usage: points_command.py <spawn|champion|gold|...>")
        sys.exit(0)

    cmd = args[0].lower()
    cmd_args = args[1:]
    _trace(f"start {cmd} args={cmd_args!r}")
    if cmd not in COMMANDS:
        _write_result(cmd, SPAWN_RESULT_FILE, f"Unknown command: {cmd}")
        sys.exit(0)

    try:
        if cmd in SPEND_PIPELINE_COMMANDS:
            _trace(f"{cmd} waiting for spend pipeline lock")
            with spend_command_lock():
                _trace(f"{cmd} pipeline lock acquired")
                try:
                    os.remove(SPAWN_RESULT_FILE)
                except OSError:
                    pass
                t0 = time.monotonic()
                result_file, msg = COMMANDS[cmd](cmd_args)
                _trace(f"{cmd} handler done in {time.monotonic() - t0:.2f}s -> {msg[:80]!r}")
                _write_result(cmd, result_file, msg)
        else:
            result_file, msg = COMMANDS[cmd](cmd_args)
            _write_result(cmd, result_file, msg)
    except TimeoutError:
        _trace(f"{cmd} pipeline lock timeout")
        result_file = SPAWN_RESULT_FILE if cmd in SPEND_PIPELINE_COMMANDS else DONATION_RESULT_FILE
        msg = "Another command is in progress. Please try again in a moment."
        _write_result(cmd, result_file, msg)
    _trace(f"{cmd} exit")
    sys.exit(0)


if __name__ == "__main__":
    main()
