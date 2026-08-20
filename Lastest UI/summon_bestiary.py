#!/usr/bin/env python3
"""Bestiary progression for !summon and paid spends: co-op XP bar, level sprint, rolling heat.

Authoritative session state for Godot HUD via GET /api/bestiary.
Paid spends grant bar XP only via apply_bar_xp(); sprint/heat remain summon-only.
"""
from __future__ import annotations

import json
import math
import os
import random
import threading
import time
from typing import Any, Optional

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(SCRIPT_DIR, "bestiary_config.json")
STATE_FILE = os.path.join(SCRIPT_DIR, "bestiary_state.json")
HEAT_LEADER_FILE = os.path.join(SCRIPT_DIR, "heat_leader.txt")
TOP_SUMMONER_FILE = os.path.join(SCRIPT_DIR, "top_summoner.txt")
TOTAL_SUMMONS_FILE = os.path.join(SCRIPT_DIR, "totalsummons.txt")
SUMMON_COUNTS_FILE = os.path.join(SCRIPT_DIR, "summon_session_counts.json")
FREE_UNTIL_FILE = os.path.join(SCRIPT_DIR, "free_until.json")
SPEND_DISABLED_FILE = os.path.join(SCRIPT_DIR, "spend_disabled.txt")

_lock = threading.Lock()
_config_cache: Optional[dict] = None

# Sidecar cost_key -> chat command label for Shatter Event announcements.
_SIDECAR_CHAT_LABEL = {
    "cost_per_plant": "!plant",
    "cost_per_debuff": "!debuff",
    "cost_per_corrupt_ally": "!corruptally",
    "cost_per_trap": "!trap",
    "cost_per_gas": "!gas",
    "cost_per_bomb": "!bomb",
    "cost_per_wand": "!wand",
    "cost_per_hex": "!hex",
    "cost_per_degrade": "!degrade",
    "cost_per_sabotage": "!sabotage",
    "cost_per_curse": "!curse",
    "cost_per_scroll": "!scroll",
    "cost_per_transmute": "!transmute",
    "cost_per_dew": "!dew",
    "cost_per_ally_bee": "!bee",
    "cost_per_ward": "!ward",
}

_DEFAULT_HOSTILE_KEYS = [
    "cost_per_plant",
    "cost_per_debuff",
    "cost_per_corrupt_ally",
    "cost_per_trap",
    "cost_per_gas",
    "cost_per_bomb",
    "cost_per_wand",
    "cost_per_hex",
    "cost_per_degrade",
    "cost_per_sabotage",
    "cost_per_curse",
    "cost_per_scroll",
    "cost_per_transmute",
]
_DEFAULT_HELPFUL_KEYS = [
    "cost_per_dew",
    "cost_per_ally_bee",
    "cost_per_ward",
]
_DEFAULT_PIP_COUNTS = {1: 3, 2: 4, 3: 5, 4: 6, 5: 6}
_DEFAULT_SIDECAR_CHANCE = {1: 0.80, 2: 0.88, 3: 0.94, 4: 0.98, 5: 1.0}


def _default_shatter_event() -> dict:
    return {
        "enabled": True,
        "duration_sec": 60,
        "jackpot_chance": 0.01,
        "sidecar_chance_by_level": dict(_DEFAULT_SIDECAR_CHANCE),
        "rat_max_fraction": 0.25,
        "helpful_max_fraction": 0.25,
        "hostile_keys": list(_DEFAULT_HOSTILE_KEYS),
        "helpful_keys": list(_DEFAULT_HELPFUL_KEYS),
    }


def _default_config() -> dict:
    return {
        "heat_window_sec": 900,
        "sprint_donor_reward": 100,
        "sprint_donor_reward_per_level": 100,
        "per_user_bar_cap_fraction": 0.0,
        "repeat_mob_diminishing": False,
        "soft_floor": {
            "enabled": True,
            "board": "sprint_eligible",
            "top_n": 3,
            "bonus": 0.25,
            "min_unique_summoners": 10,
            "exclude_users": ["DaltonGoesFast", "DaltonGoesSlow"],
            "apply_to_bar": True,
            "apply_to_sprint": True,
            "apply_to_heat": True,
        },
        "shatter_event": _default_shatter_event(),
        # Zones match NATIVE_DEPTH chapter: (depth-1)//5 (see points_command / StreamingCommandHandler).
        "levels": [
            {"level": 1, "zone": "Sewers", "bar_threshold": 1000, "pip_count": 3,
             "monsters": ["rat", "albino", "snake", "gnoll", "crab", "slime", "swarm", "thief"]},
            {"level": 2, "zone": "Prison", "bar_threshold": 1500, "pip_count": 4,
             "monsters": ["skeleton", "dm100", "guard", "necromancer"]},
            {"level": 3, "zone": "Caves", "bar_threshold": 2000, "pip_count": 5,
             "monsters": ["bat", "brute", "shaman", "spinner", "ghoul"]},
            {"level": 4, "zone": "City", "bar_threshold": 2500, "pip_count": 6,
             "monsters": ["elemental", "warlock", "monk", "golem", "succubus"]},
            {"level": 5, "zone": "Halls", "bar_threshold": 2500, "pip_count": 6,
             "monsters": ["eye", "scorpio"]},
        ],
    }


_config_mtime: float = 0.0


def load_config(force: bool = False) -> dict:
    global _config_cache, _config_mtime
    try:
        mtime = os.path.getmtime(CONFIG_FILE) if os.path.exists(CONFIG_FILE) else 0.0
    except OSError:
        mtime = 0.0
    if _config_cache is not None and not force and mtime == _config_mtime:
        return _config_cache
    cfg = _default_config()
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, encoding="utf-8") as f:
                raw = json.load(f)
            if isinstance(raw, dict):
                cfg.update({k: v for k, v in raw.items() if k != "levels"})
                if isinstance(raw.get("levels"), list) and raw["levels"]:
                    cfg["levels"] = raw["levels"]
        except (json.JSONDecodeError, OSError):
            pass
    _config_cache = cfg
    _config_mtime = mtime
    return cfg


def save_config(cfg: dict) -> dict:
    """Write bestiary_config.json and refresh cache. Returns normalized config."""
    global _config_cache, _config_mtime
    normalized = normalize_config(cfg if isinstance(cfg, dict) else {})
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(normalized, f, indent=2)
        f.write("\n")
    try:
        _config_mtime = os.path.getmtime(CONFIG_FILE)
    except OSError:
        _config_mtime = time.time()
    _config_cache = normalized
    return normalized


def normalize_config(raw: dict) -> dict:
    """Merge raw into defaults; clamp thresholds and soft_floor fields."""
    cfg = _default_config()
    if not isinstance(raw, dict):
        return cfg
    for k, v in raw.items():
        if k == "levels":
            continue
        if k == "soft_floor" and isinstance(v, dict):
            base_sf = dict(cfg.get("soft_floor") or {})
            base_sf.update(v)
            if "exclude_users" in v and isinstance(v["exclude_users"], list):
                base_sf["exclude_users"] = [str(u).strip() for u in v["exclude_users"] if str(u).strip()]
            cfg["soft_floor"] = base_sf
        else:
            cfg[k] = v
    cfg["heat_window_sec"] = max(60, int(cfg.get("heat_window_sec", 900) or 900))
    cfg["sprint_donor_reward"] = max(0, int(cfg.get("sprint_donor_reward", 100) or 0))
    cfg["sprint_donor_reward_per_level"] = max(
        0, int(cfg.get("sprint_donor_reward_per_level", 100) or 0)
    )
    try:
        cfg["per_user_bar_cap_fraction"] = max(
            0.0, min(1.0, float(cfg.get("per_user_bar_cap_fraction", 0.0) or 0.0))
        )
    except (TypeError, ValueError):
        cfg["per_user_bar_cap_fraction"] = 0.0
    cfg["repeat_mob_diminishing"] = bool(cfg.get("repeat_mob_diminishing", False))
    cfg["shatter_event"] = _normalize_shatter_event(cfg.get("shatter_event"))
    sf = cfg.get("soft_floor") if isinstance(cfg.get("soft_floor"), dict) else {}
    sf["enabled"] = bool(sf.get("enabled", True))
    sf["top_n"] = max(1, int(sf.get("top_n", 3) or 3))
    try:
        sf["bonus"] = max(0.0, float(sf.get("bonus", 0.25) or 0.0))
    except (TypeError, ValueError):
        sf["bonus"] = 0.25
    sf["min_unique_summoners"] = max(0, int(sf.get("min_unique_summoners", 10) or 0))
    sf["apply_to_bar"] = bool(sf.get("apply_to_bar", True))
    sf["apply_to_sprint"] = bool(sf.get("apply_to_sprint", True))
    sf["apply_to_heat"] = bool(sf.get("apply_to_heat", True))
    cfg["soft_floor"] = sf
    levels_in = raw.get("levels")
    if isinstance(levels_in, list) and levels_in:
        out_levels: list[dict] = []
        defaults_by_lvl = {
            int(e.get("level", 0)): e for e in (_default_config().get("levels") or [])
        }
        for entry in levels_in:
            if not isinstance(entry, dict):
                continue
            lvl = int(entry.get("level", 0) or 0)
            if lvl <= 0:
                continue
            base = dict(defaults_by_lvl.get(lvl) or {"level": lvl, "zone": f"Level {lvl}", "monsters": []})
            zone = str(entry.get("zone", base.get("zone", f"Level {lvl}"))).strip() or base.get("zone")
            thr = max(0, int(entry.get("bar_threshold", base.get("bar_threshold", 0)) or 0))
            monsters = entry.get("monsters", base.get("monsters") or [])
            if not isinstance(monsters, list):
                monsters = list(base.get("monsters") or [])
            monsters = [str(m).strip().lower() for m in monsters if str(m).strip()]
            pip_default = int(base.get("pip_count", _DEFAULT_PIP_COUNTS.get(lvl, 3)) or 0)
            try:
                pip_count = int(entry.get("pip_count", pip_default) or pip_default)
            except (TypeError, ValueError):
                pip_count = pip_default
            pip_count = max(0, min(12, pip_count))
            out_levels.append(
                {
                    "level": lvl,
                    "zone": zone,
                    "bar_threshold": thr,
                    "pip_count": pip_count,
                    "monsters": monsters,
                }
            )
        out_levels.sort(key=lambda e: int(e.get("level", 0)))
        if out_levels:
            cfg["levels"] = out_levels
    else:
        # Ensure default levels keep pip_count when no levels override
        for entry in cfg.get("levels") or []:
            if "pip_count" not in entry:
                lvl = int(entry.get("level", 1) or 1)
                entry["pip_count"] = int(_DEFAULT_PIP_COUNTS.get(lvl, 3))
    return cfg


def _normalize_shatter_event(raw: Any) -> dict:
    base = _default_shatter_event()
    if not isinstance(raw, dict):
        return base
    out = dict(base)
    out["enabled"] = bool(raw.get("enabled", True))
    try:
        out["duration_sec"] = max(5, min(600, int(raw.get("duration_sec", 60) or 60)))
    except (TypeError, ValueError):
        out["duration_sec"] = 60
    try:
        out["jackpot_chance"] = max(0.0, min(1.0, float(raw.get("jackpot_chance", 0.01) or 0.0)))
    except (TypeError, ValueError):
        out["jackpot_chance"] = 0.01
    try:
        out["rat_max_fraction"] = max(0.05, min(1.0, float(raw.get("rat_max_fraction", 0.25) or 0.25)))
    except (TypeError, ValueError):
        out["rat_max_fraction"] = 0.25
    try:
        out["helpful_max_fraction"] = max(
            0.0, min(1.0, float(raw.get("helpful_max_fraction", 0.25) or 0.25))
        )
    except (TypeError, ValueError):
        out["helpful_max_fraction"] = 0.25
    chance_in = raw.get("sidecar_chance_by_level")
    chances: dict[int, float] = dict(_DEFAULT_SIDECAR_CHANCE)
    if isinstance(chance_in, dict):
        for k, v in chance_in.items():
            try:
                lvl = int(k)
                chances[lvl] = max(0.0, min(1.0, float(v)))
            except (TypeError, ValueError):
                continue
    out["sidecar_chance_by_level"] = {str(k): float(chances[k]) for k in sorted(chances)}
    for list_key, default in (
        ("hostile_keys", _DEFAULT_HOSTILE_KEYS),
        ("helpful_keys", _DEFAULT_HELPFUL_KEYS),
    ):
        raw_list = raw.get(list_key, default)
        if not isinstance(raw_list, list) or not raw_list:
            raw_list = default
        out[list_key] = [str(x).strip() for x in raw_list if str(x).strip()]
    return out


def _shatter_cfg(cfg: Optional[dict] = None) -> dict:
    cfg = cfg or load_config()
    return _normalize_shatter_event(cfg.get("shatter_event"))


def _sidecar_chance_for_level(level: int, cfg: Optional[dict] = None) -> float:
    se = _shatter_cfg(cfg)
    chances = se.get("sidecar_chance_by_level") or {}
    if not isinstance(chances, dict):
        return float(_DEFAULT_SIDECAR_CHANCE.get(level, 1.0))
    if level in chances:
        try:
            return max(0.0, min(1.0, float(chances[level])))
        except (TypeError, ValueError):
            pass
    if str(level) in chances:
        try:
            return max(0.0, min(1.0, float(chances[str(level)])))
        except (TypeError, ValueError):
            pass
    return float(_DEFAULT_SIDECAR_CHANCE.get(level, 1.0))


def _pip_count_for_level(level: int, cfg: Optional[dict] = None) -> int:
    cfg = cfg or load_config()
    for entry in cfg.get("levels") or []:
        if int(entry.get("level", 0)) == level:
            try:
                return max(0, min(12, int(entry.get("pip_count", _DEFAULT_PIP_COUNTS.get(level, 3)) or 0)))
            except (TypeError, ValueError):
                return int(_DEFAULT_PIP_COUNTS.get(level, 3))
    return int(_DEFAULT_PIP_COUNTS.get(level, 3))


def _pip_fractions(pip_count: int) -> list[float]:
    n = max(0, int(pip_count))
    if n <= 0:
        return []
    return [float(i) / float(n + 1) for i in range(1, n + 1)]


def _monsters_for_level(level: int, cfg: Optional[dict] = None) -> list[str]:
    cfg = cfg or load_config()
    for entry in cfg.get("levels") or []:
        if int(entry.get("level", 0)) == level:
            return [str(m).strip().lower() for m in (entry.get("monsters") or []) if str(m).strip()]
    return []


def _monster_point_cost(monster: str) -> int:
    monster = (monster or "").strip().lower()
    try:
        from points_command import get_config
        pcfg = get_config()
        return int(
            pcfg.get("cost_per_monster", {}).get(monster, pcfg.get("default_monster_cost", 100))
        )
    except Exception:
        return 100


def _cost_key_point_cost(cost_key: str) -> int:
    try:
        from points_command import get_config
        pcfg = get_config()
        if cost_key.startswith("cost_per_monster."):
            return _monster_point_cost(cost_key.split(".", 1)[1])
        if cost_key in pcfg:
            return max(1, int(pcfg.get(cost_key) or 1))
    except Exception:
        pass
    return 75


def _weighted_pick(items: list[tuple[Any, float]]) -> Any:
    cleaned = [(it, max(0.0, float(w))) for it, w in items if w and float(w) > 0]
    if not cleaned:
        return None
    total = sum(w for _, w in cleaned)
    r = random.random() * total
    acc = 0.0
    for it, w in cleaned:
        acc += w
        if r <= acc:
            return it
    return cleaned[-1][0]


def _zone_monster_weights(level: int, cfg: Optional[dict] = None) -> list[tuple[str, float]]:
    se = _shatter_cfg(cfg)
    rat_cap = float(se.get("rat_max_fraction", 0.25) or 0.25)
    monsters = _monsters_for_level(level, cfg)
    weights: list[tuple[str, float]] = []
    for m in monsters:
        cost = max(1, _monster_point_cost(m))
        weights.append((m, float(max(1, round(300.0 / cost)))))
    if not weights:
        return [("rat", 1.0)]
    # Cap rat share
    total = sum(w for _, w in weights)
    rat_w = next((w for m, w in weights if m == "rat"), 0.0)
    if rat_w > 0 and total > 0 and (rat_w / total) > rat_cap:
        other = total - rat_w
        # rat_w' / (other + rat_w') = rat_cap => rat_w' = rat_cap/(1-rat_cap) * other
        if rat_cap >= 1.0:
            pass
        elif other <= 0:
            weights = [(m, (rat_cap if m == "rat" else 0.0) or w) for m, w in weights]
        else:
            new_rat = (rat_cap / (1.0 - rat_cap)) * other
            weights = [(m, new_rat if m == "rat" else w) for m, w in weights]
    return weights


def _roll_shatter_monster(level: int, cfg: Optional[dict] = None) -> str:
    se = _shatter_cfg(cfg)
    try:
        jackpot = float(se.get("jackpot_chance", 0.01) or 0.0)
    except (TypeError, ValueError):
        jackpot = 0.01
    if random.random() < jackpot:
        return "eye" if random.random() < 0.5 else "scorpio"
    picked = _weighted_pick(_zone_monster_weights(level, cfg))
    return str(picked or "rat")


def _sidecar_weight_table(cfg: Optional[dict] = None) -> list[tuple[str, float]]:
    se = _shatter_cfg(cfg)
    hostile_keys = list(se.get("hostile_keys") or _DEFAULT_HOSTILE_KEYS)
    helpful_keys = list(se.get("helpful_keys") or _DEFAULT_HELPFUL_KEYS)
    try:
        helpful_frac = float(se.get("helpful_max_fraction", 0.25) or 0.25)
    except (TypeError, ValueError):
        helpful_frac = 0.25
    hostile: list[tuple[str, float]] = []
    for key in hostile_keys:
        cost = max(1, _cost_key_point_cost(key))
        hostile.append((key, float(max(1, round(300.0 / cost)))))
    helpful: list[tuple[str, float]] = []
    for key in helpful_keys:
        cost = max(1, _cost_key_point_cost(key))
        helpful.append((key, float(max(1, round(300.0 / cost)))))
    x = sum(w for _, w in hostile)
    h = sum(w for _, w in helpful)
    if helpful and h > 0 and helpful_frac > 0 and helpful_frac < 1.0:
        # H' <= helpful_frac * (X + H') => H' <= X * helpful_frac / (1 - helpful_frac)
        max_h = x * helpful_frac / (1.0 - helpful_frac) if helpful_frac < 1.0 else h
        if h > max_h and max_h > 0:
            scale = max_h / h
            helpful = [(k, w * scale) for k, w in helpful]
    elif helpful_frac <= 0:
        helpful = []
    return hostile + helpful


def _roll_shatter_sidecar(level: int, cfg: Optional[dict] = None) -> Optional[str]:
    if random.random() >= _sidecar_chance_for_level(level, cfg):
        return None
    picked = _weighted_pick(_sidecar_weight_table(cfg))
    return str(picked) if picked else None


def _is_spend_disabled() -> bool:
    return os.path.exists(SPEND_DISABLED_FILE)


def extend_free_until(cost_keys: list[str], duration_sec: int) -> dict[str, int]:
    """Extend free windows: new_end = max(now, existing_end) + duration_sec per key."""
    now = int(time.time())
    duration_sec = max(1, int(duration_sec or 60))
    free: dict[str, Any] = {}
    if os.path.exists(FREE_UNTIL_FILE):
        try:
            with open(FREE_UNTIL_FILE, encoding="utf-8") as f:
                raw = json.load(f)
            if isinstance(raw, dict):
                free = raw
        except (json.JSONDecodeError, OSError):
            free = {}
    ends: dict[str, int] = {}
    for key in cost_keys:
        k = str(key).strip()
        if not k:
            continue
        try:
            prev = int(free.get(k) or 0)
        except (TypeError, ValueError):
            prev = 0
        new_end = max(now, prev) + duration_sec
        free[k] = new_end
        ends[k] = new_end
    tmp = FREE_UNTIL_FILE + ".tmp"
    payload = json.dumps(free, indent=2)
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(payload)
        f.write("\n")
    os.replace(tmp, FREE_UNTIL_FILE)
    return ends


def _grant_shatter_event(
    state: dict,
    cfg: dict,
    level: int,
    *,
    reason: str,
    pip_index: Optional[int] = None,
) -> Optional[dict[str, Any]]:
    """Roll + write free_until. Returns event dict or None if gated off."""
    se = _shatter_cfg(cfg)
    if not bool(se.get("enabled", True)):
        return None
    if _is_spend_disabled():
        return None
    duration = max(5, int(se.get("duration_sec", 60) or 60))
    monster = _roll_shatter_monster(level, cfg)
    monster_key = f"cost_per_monster.{monster}"
    sidecar_key = _roll_shatter_sidecar(level, cfg)
    keys = [monster_key]
    if sidecar_key:
        keys.append(sidecar_key)
    extend_free_until(keys, duration)
    event = {
        "reason": reason,
        "level": level,
        "zone": _zone_for_level(level, cfg),
        "monster": monster,
        "monster_cost_key": monster_key,
        "sidecar_cost_key": sidecar_key,
        "sidecar_label": _SIDECAR_CHAT_LABEL.get(sidecar_key or "", sidecar_key),
        "duration_sec": duration,
        "pip_index": pip_index,
        "cost_keys": keys,
    }
    return event


def _claim_due_shatter_pips(
    state: dict,
    cfg: dict,
    level: int,
    progress_xp: int,
    thresh: int,
) -> list[dict[str, Any]]:
    """Claim mid-bar pips whose XP threshold is <= progress_xp. Marks claimed only when granted."""
    events: list[dict[str, Any]] = []
    se = _shatter_cfg(cfg)
    if not bool(se.get("enabled", True)):
        return events
    if thresh <= 0:
        return events
    pip_count = _pip_count_for_level(level, cfg)
    fractions = _pip_fractions(pip_count)
    if not fractions:
        return events
    claimed = [int(x) for x in (state.get("shatter_claimed_pips") or []) if str(x).strip()]
    claimed_set = set(claimed)
    # If spend disabled, leave unclaimed for catch-up later.
    if _is_spend_disabled():
        return events
    for i, frac in enumerate(fractions, start=1):
        if i in claimed_set:
            continue
        need = int(math.ceil(float(thresh) * frac))
        if progress_xp < need:
            continue
        ev = _grant_shatter_event(state, cfg, level, reason="pip", pip_index=i)
        if ev:
            claimed.append(i)
            claimed_set.add(i)
            events.append(ev)
    state["shatter_claimed_pips"] = sorted(claimed_set)
    return events


def _monster_level_map(cfg: Optional[dict] = None) -> dict[str, int]:
    cfg = cfg or load_config()
    out: dict[str, int] = {}
    for entry in cfg.get("levels") or []:
        lvl = int(entry.get("level", 1))
        for m in entry.get("monsters") or []:
            out[str(m).strip().lower()] = lvl
    return out


def _zone_for_level(level: int, cfg: Optional[dict] = None) -> str:
    cfg = cfg or load_config()
    for entry in cfg.get("levels") or []:
        if int(entry.get("level", 0)) == level:
            return str(entry.get("zone") or f"Level {level}")
    return f"Level {level}"


def _threshold_for_level(level: int, cfg: Optional[dict] = None) -> int:
    """XP needed to advance FROM this level to the next. 0 = max level (no bar)."""
    cfg = cfg or load_config()
    for entry in cfg.get("levels") or []:
        if int(entry.get("level", 0)) == level:
            return max(0, int(entry.get("bar_threshold", 0) or 0))
    return 0


def _max_level(cfg: Optional[dict] = None) -> int:
    cfg = cfg or load_config()
    levels = [int(e.get("level", 1)) for e in (cfg.get("levels") or [])]
    return max(levels) if levels else 1


def monster_xp(monster: str) -> int:
    """XP from cost_per_monster: max(2, cost // 5)."""
    monster = (monster or "").strip().lower()
    try:
        from points_command import get_config
        cfg = get_config()
        cost = int(cfg.get("cost_per_monster", {}).get(monster, cfg.get("default_monster_cost", 100)))
    except Exception:
        cost = 100
    return max(2, cost // 5)


def _empty_state() -> dict:
    return {
        "level": 1,
        "bar_xp": 0,
        "sprint_xp": {},
        "heat_events": [],
        "hall_of_fame": [],
        "sprint_winners": [],
        "last_level_up_ts": 0,
        "session_summon_counts": {},
        "last_monster_by_user": {},
        "shatter_claimed_pips": [],
    }


def _load_state() -> dict:
    if not os.path.exists(STATE_FILE):
        return _empty_state()
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, dict):
            return _empty_state()
        base = _empty_state()
        base.update(data)
        if not isinstance(base.get("sprint_xp"), dict):
            base["sprint_xp"] = {}
        if not isinstance(base.get("heat_events"), list):
            base["heat_events"] = []
        if not isinstance(base.get("hall_of_fame"), list):
            base["hall_of_fame"] = []
        if not isinstance(base.get("sprint_winners"), list):
            # Backfill from hall so mid-stream upgrades keep prior crowns ineligible
            winners: list[str] = []
            seen: set[str] = set()
            for entry in base.get("hall_of_fame") or []:
                u = str((entry or {}).get("user") or "").strip().lower()
                if u and u not in seen:
                    seen.add(u)
                    winners.append(u)
            base["sprint_winners"] = winners
        if not isinstance(base.get("session_summon_counts"), dict):
            base["session_summon_counts"] = {}
        if not isinstance(base.get("last_monster_by_user"), dict):
            base["last_monster_by_user"] = {}
        if not isinstance(base.get("shatter_claimed_pips"), list):
            base["shatter_claimed_pips"] = []
        else:
            base["shatter_claimed_pips"] = [
                int(x) for x in base["shatter_claimed_pips"] if str(x).strip().lstrip("-").isdigit()
            ]
        base["level"] = max(1, int(base.get("level", 1) or 1))
        base["bar_xp"] = max(0, int(base.get("bar_xp", 0) or 0))
        return base
    except (json.JSONDecodeError, OSError):
        return _empty_state()


def _save_state(state: dict) -> None:
    tmp = STATE_FILE + ".tmp"
    payload = json.dumps(state, indent=2)
    last_err: Optional[BaseException] = None
    for attempt in range(8):
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                f.write(payload)
            os.replace(tmp, STATE_FILE)
            return
        except OSError as e:
            # Windows: Flask + sim can race on replace while the other process reads.
            last_err = e
            time.sleep(0.01 * (attempt + 1))
    if last_err:
        raise last_err


def unlocked_monsters(level: Optional[int] = None, cfg: Optional[dict] = None) -> list[str]:
    cfg = cfg or load_config()
    if level is None:
        with _lock:
            level = int(_load_state().get("level", 1))
    out: list[str] = []
    for entry in cfg.get("levels") or []:
        if int(entry.get("level", 99)) <= level:
            for m in entry.get("monsters") or []:
                mid = str(m).strip().lower()
                if mid and mid not in out:
                    out.append(mid)
    return out


def is_monster_unlocked(monster: str, level: Optional[int] = None) -> bool:
    return (monster or "").strip().lower() in unlocked_monsters(level)


def required_level_for_monster(monster: str) -> Optional[int]:
    return _monster_level_map().get((monster or "").strip().lower())


def resolve_monster(raw: str) -> tuple[Optional[str], Optional[str]]:
    """Resolve arg or random from unlocked pool. Returns (monster, error_message)."""
    cfg = load_config()
    with _lock:
        state = _load_state()
        level = int(state.get("level", 1))
        bar_xp = int(state.get("bar_xp", 0))
    pool = unlocked_monsters(level, cfg)
    if not pool:
        return None, "No monsters unlocked yet."

    raw = (raw or "").strip().lower()
    if not raw:
        return random.choice(pool), None

    mmap = _monster_level_map(cfg)
    if raw not in mmap:
        return None, "Unknown monster. Try: rat, gnoll, bat, brute, …"

    need = mmap[raw]
    if need > level:
        thresh = _threshold_for_level(level, cfg)
        remaining = max(0, thresh - bar_xp) if thresh > 0 else 0
        zone = _zone_for_level(need, cfg)
        return None, (
            f"Need Bestiary Lv {need} ({zone}) - {remaining} XP left on current level."
        )
    return raw, None


def _prune_heat(events: list, now: int, window_sec: int) -> list:
    cutoff = now - window_sec
    return [e for e in events if int(e.get("ts", 0)) > cutoff]


def _heat_scores(events: list) -> dict[str, int]:
    scores: dict[str, int] = {}
    for e in events:
        u = str(e.get("user") or "").strip().lower()
        if not u:
            continue
        scores[u] = scores.get(u, 0) + int(e.get("xp", 0) or 0)
    return scores


def _leader_from_scores(scores: dict[str, int], display_map: Optional[dict] = None) -> tuple[str, int]:
    if not scores:
        return "", 0
    best_user = max(scores, key=lambda k: (scores[k], k))
    # Prefer highest XP; on tie keep lexicographically first lower key for stability
    top_xp = scores[best_user]
    candidates = [u for u, xp in scores.items() if xp == top_xp]
    # Prefer incumbent display name if provided via display_map keys matching
    key = sorted(candidates)[0]
    display = (display_map or {}).get(key, key)
    return display, top_xp


def get_heat_leader() -> tuple[str, int]:
    """Return (display_username, heat_xp). Empty username if none."""
    cfg = load_config()
    window = int(cfg.get("heat_window_sec", 900))
    with _lock:
        state = _load_state()
        now = int(time.time())
        events = _prune_heat(list(state.get("heat_events") or []), now, window)
        scores = _heat_scores(events)
        # Map lower keys to last-seen display casing from events
        display_map: dict[str, str] = {}
        for e in events:
            u = str(e.get("user") or "").strip()
            if u:
                display_map[u.lower()] = u
        return _leader_from_scores(scores, display_map)


def clear_heat_events() -> None:
    """Wipe the rolling heat window (sprint XP / bar unchanged). For demos/tests."""
    with _lock:
        state = _load_state()
        state["heat_events"] = []
        _save_state(state)
        _write_heat_leader_file("", 0)


def _sprint_ineligible_keys(state: dict) -> set[str]:
    """Users who already won a sprint crown this stream (until session reset)."""
    out: set[str] = set()
    for u in state.get("sprint_winners") or []:
        k = str(u or "").strip().lower()
        if k:
            out.add(k)
    for entry in state.get("hall_of_fame") or []:
        k = str((entry or {}).get("user") or "").strip().lower()
        if k:
            out.add(k)
    return out


def _sprint_scores(sprint: dict) -> tuple[dict[str, int], dict[str, str]]:
    scores: dict[str, int] = {}
    display_map: dict[str, str] = {}
    for k, v in (sprint or {}).items():
        key = str(k).strip().lower()
        if not key:
            continue
        if isinstance(v, dict):
            scores[key] = int(v.get("xp", 0) or 0)
            display_map[key] = str(v.get("display") or key)
        else:
            scores[key] = int(v or 0)
            display_map[key] = key
    return scores, display_map


def _eligible_sprint_ranked(
    sprint: dict, ineligible: set[str]
) -> list[tuple[str, int]]:
    """Eligible sprint scorers sorted by XP desc (prior crowns excluded)."""
    scores, _display_map = _sprint_scores(sprint)
    return sorted(
        ((k, xp) for k, xp in scores.items() if xp > 0 and k not in ineligible),
        key=lambda kv: (-kv[1], kv[0]),
    )


def _pick_eligible_sprint_leader(
    sprint: dict, ineligible: set[str]
) -> tuple[str, int, int]:
    """Eligible sprint leader for crown / !topsummoner. Skips prior winners this stream."""
    scores, display_map = _sprint_scores(sprint)
    ranked = _eligible_sprint_ranked(sprint, ineligible)
    if not ranked:
        return "", 0, 0
    top_key, top_xp = ranked[0]
    second = ranked[1][1] if len(ranked) > 1 else 0
    return display_map.get(top_key, top_key), top_xp, max(0, top_xp - second)


def _unique_summoner_keys(counts: dict, include_key: str = "") -> set[str]:
    keys: set[str] = set()
    for k in (counts or {}).keys():
        kk = str(k or "").strip().lower()
        if kk:
            keys.add(kk)
    ik = (include_key or "").strip().lower()
    if ik:
        keys.add(ik)
    return keys


def _soft_floor_cfg(cfg: Optional[dict] = None) -> dict:
    cfg = cfg or load_config()
    raw = cfg.get("soft_floor")
    if not isinstance(raw, dict):
        return {}
    return raw


def _soft_floor_exclude_keys(sf: dict) -> set[str]:
    out: set[str] = set()
    for u in sf.get("exclude_users") or []:
        k = str(u or "").strip().lower()
        if k:
            out.add(k)
    return out


def _top_band_keys(ranked: list[tuple[str, int]], top_n: int) -> set[str]:
    """Keys in the protected top band. Ties at the cutoff share the seats."""
    if top_n <= 0 or not ranked:
        return set()
    if len(ranked) <= top_n:
        return {k for k, _xp in ranked}
    cutoff = ranked[top_n - 1][1]
    return {k for k, xp in ranked if xp >= cutoff}


def soft_floor_multiplier(
    username: str,
    state: dict,
    cfg: Optional[dict] = None,
) -> tuple[float, bool]:
    """Return (mult, applied). Mid-pack catch-up on eligible sprint board.

    Gate: enabled, enough unique summoners this stream, user not excluded,
    user not current heat leader, user not a prior sprint crown this stream,
    and user not in top_n eligible sprint (XP before this summon).

    Heat / past crowns are checked from ``state`` (no lock) so this is safe
    inside ``apply_summon``'s critical section.
    """
    sf = _soft_floor_cfg(cfg)
    if not sf or not bool(sf.get("enabled", False)):
        return 1.0, False
    key = (username or "").strip().lower()
    if not key:
        return 1.0, False
    if key in _soft_floor_exclude_keys(sf):
        return 1.0, False

    # Already-rewarded leaders are off the eligible sprint top-N board, so without
    # these checks they always looked "mid-pack" and kept getting catch-up.
    ineligible = _sprint_ineligible_keys(state)
    if key in ineligible:
        return 1.0, False

    cfg = cfg or load_config()
    window = int(cfg.get("heat_window_sec", 900))
    now = int(time.time())
    heat_events = _prune_heat(list(state.get("heat_events") or []), now, window)
    heat_key, _heat_xp = _leader_from_scores(_heat_scores(heat_events))
    if heat_key and heat_key == key:
        return 1.0, False

    min_unique = max(1, int(sf.get("min_unique_summoners", 10) or 10))
    counts = state.get("session_summon_counts") or {}
    if len(_unique_summoner_keys(counts, key)) < min_unique:
        return 1.0, False

    top_n = max(1, int(sf.get("top_n", 3) or 3))
    ranked = _eligible_sprint_ranked(dict(state.get("sprint_xp") or {}), ineligible)
    if key in _top_band_keys(ranked, top_n):
        return 1.0, False

    bonus = float(sf.get("bonus", 0.25) or 0.0)
    if bonus <= 0:
        return 1.0, False
    return 1.0 + bonus, True


def _apply_xp_mult(xp: int, mult: float) -> int:
    """Apply catch-up mult; always at least +1 over base when mult > 1."""
    base = max(0, int(xp))
    if mult <= 1.0 + 1e-9:
        return base
    boosted = int(math.ceil(float(base) * mult - 1e-12))
    return max(base + 1, boosted)


def is_sprint_crowned(username: str) -> bool:
    """True if this viewer already won a sprint this stream (Hall / sprint_winners).

    Used for eligibility / chat only — march crowns are active leaders via
    get_march_leader_status(), not past winners.
    """
    key = (username or "").strip().lower()
    if not key:
        return False
    with _lock:
        return key in _sprint_ineligible_keys(_load_state())


def get_march_leader_status(username: str) -> dict[str, Any]:
    """Active-competition badges for summon-march VFX.

    - heat: current heat leader (personal 2×)
    - gold / silver / bronze: eligible sprint ranks 1–3 this Bestiary level
    Heat wins if both apply. Past sprint winners are not auto-badged.
    """
    key = (username or "").strip().lower()
    empty = {
        "heat_leader": False,
        "sprint_rank": 0,
        "badge": "",
        "crowned": False,
    }
    if not key:
        return empty

    heat_user, _heat_xp = get_heat_leader()
    is_heat = bool(heat_user) and heat_user.strip().lower() == key

    with _lock:
        state = _load_state()
        sprint = dict(state.get("sprint_xp") or {})
        ineligible = _sprint_ineligible_keys(state)
    ranked = _eligible_sprint_ranked(sprint, ineligible)
    sprint_rank = 0
    for i, (k, _xp) in enumerate(ranked[:3], 1):
        if k == key:
            sprint_rank = i
            break

    # Heat (red) wins over sprint metals so the heat crown is always visible
    # on the current heat leader's marches.
    badge = ""
    if is_heat:
        badge = "heat"
    elif sprint_rank == 1:
        badge = "gold"
    elif sprint_rank == 2:
        badge = "silver"
    elif sprint_rank == 3:
        badge = "bronze"

    return {
        "heat_leader": is_heat,
        "sprint_rank": sprint_rank,
        "badge": badge,
        # Legacy field: true for any active badge (not past HoF winners).
        "crowned": bool(badge),
    }


def get_sprint_leader() -> tuple[str, int, int]:
    """Return (display_username, sprint_xp, gap_to_second) among users eligible to crown."""
    with _lock:
        state = _load_state()
        sprint = dict(state.get("sprint_xp") or {})
        ineligible = _sprint_ineligible_keys(state)
    return _pick_eligible_sprint_leader(sprint, ineligible)


def get_sprint_standing(username: str = "") -> dict[str, Any]:
    """Leader + caller's place on the eligible sprint board (for !sprint)."""
    key = (username or "").strip().lower()
    with _lock:
        state = _load_state()
        sprint = dict(state.get("sprint_xp") or {})
        ineligible = _sprint_ineligible_keys(state)
    scores, display_map = _sprint_scores(sprint)
    ranked = _eligible_sprint_ranked(sprint, ineligible)
    leader_user, leader_xp, gap = "", 0, 0
    if ranked:
        top_key, leader_xp = ranked[0]
        leader_user = display_map.get(top_key, top_key)
        second = ranked[1][1] if len(ranked) > 1 else 0
        gap = max(0, leader_xp - second)
    your_xp = int(scores.get(key, 0) or 0) if key else 0
    crowned = bool(key and key in ineligible)
    rank: Optional[int] = None
    if key and not crowned and your_xp > 0:
        for i, (k, _xp) in enumerate(ranked, 1):
            if k == key:
                rank = i
                break
    return {
        "leader": leader_user,
        "leader_xp": leader_xp,
        "gap": gap,
        "your_xp": your_xp,
        "rank": rank,
        "crowned": crowned,
        "eligible_count": len(ranked),
    }


def _write_heat_leader_file(username: str, score: int) -> None:
    try:
        with open(HEAT_LEADER_FILE, "w", encoding="utf-8") as f:
            if username:
                f.write(f"Top Summoner: {username} - {score}\n")
            else:
                f.write("")
        # Keep top_summoner.txt as heat-compatible display for legacy OBS / is_top_summoner readers
        # until points_command reads heat_leader; still write heat format there for sprint→heat migration.
        with open(TOP_SUMMONER_FILE, "w", encoding="utf-8") as f:
            if username:
                f.write(f"Top Summoner: {username} - {score}\n")
            else:
                f.write("")
    except OSError:
        pass


def _write_totals(state: dict) -> None:
    counts = state.get("session_summon_counts") or {}
    total = sum(int(v) for v in counts.values())
    try:
        with open(TOTAL_SUMMONS_FILE, "w", encoding="utf-8") as f:
            f.write(f"Total Summons: {total}\n")
        with open(SUMMON_COUNTS_FILE, "w", encoding="utf-8") as f:
            json.dump(counts, f, indent=2)
    except OSError:
        pass


def _sprint_donor_for_level(completed_level: int, cfg: Optional[dict] = None) -> int:
    """Donor pts for crowning the sprint when leaving this level: base + (level-1)*step.

    Defaults: 100, 200, 300, 400 for Sewers→Prison→Caves→City crowns.
    """
    cfg = cfg or load_config()
    base = int(cfg.get("sprint_donor_reward", 100))
    step = int(cfg.get("sprint_donor_reward_per_level", base))
    lvl = max(1, int(completed_level or 1))
    return max(0, base + (lvl - 1) * step)


def _grant_sprint_donor(username: str, amount: int) -> bool:
    if not username or amount <= 0:
        return False
    try:
        from points_command import grant_sprint_donor_reward
        grant_sprint_donor_reward(username, amount)
        return True
    except Exception as e:
        print(f"[bestiary] grant_sprint_donor_reward failed: {e}")
        return False


def spend_xp_from_cost(cost: int) -> int:
    """Bestiary bar XP from a paid command cost. Promo/zero cost → 0 (no summon floor)."""
    c = int(cost or 0)
    if c <= 0:
        return 0
    return max(2, c // 5)


def _cap_bar_contrib(state: dict, key: str, bar_xp_add: int, cfg: dict) -> int:
    """Apply optional per-user bar contribution cap; mutates state['bar_contrib']."""
    cap_frac = float(cfg.get("per_user_bar_cap_fraction", 0) or 0)
    if cap_frac <= 0:
        return max(0, int(bar_xp_add))
    level = int(state.get("level", 1))
    thresh = _threshold_for_level(level, cfg)
    if thresh <= 0:
        return max(0, int(bar_xp_add))
    contrib = dict(state.get("bar_contrib") or {})
    already = int(contrib.get(key, 0) or 0)
    cap = int(thresh * cap_frac)
    room = max(0, cap - already)
    bar_xp_add = min(max(0, int(bar_xp_add)), room)
    contrib[key] = already + bar_xp_add
    state["bar_contrib"] = contrib
    return bar_xp_add


def _add_bar_xp_and_level_up(
    state: dict,
    bar_xp_add: int,
    cfg: dict,
    now: int,
) -> tuple[bool, Optional[dict], list[dict[str, Any]]]:
    """Add co-op bar XP, Shatter Event pips, level-ups, and Halls loops.

    Mutates state. Caller holds _lock.
    Returns (leveled_up, level_up_info, shatter_events).
    """
    max_lvl = _max_level(cfg)
    leveled_up = False
    level_up_info: Optional[dict] = None
    shatter_events: list[dict[str, Any]] = []
    state["bar_xp"] = int(state.get("bar_xp", 0)) + max(0, int(bar_xp_add))

    # Safety: avoid infinite Halls loops if overflow is huge
    for _ in range(20):
        cur = int(state.get("level", 1))
        thresh = _threshold_for_level(cur, cfg)
        if thresh <= 0:
            # No bar on this level (legacy Halls thr=0): cosmetic complete
            if cur >= max_lvl:
                state["bar_xp"] = 0
            break

        progress = min(int(state.get("bar_xp", 0)), thresh)
        shatter_events.extend(
            _claim_due_shatter_pips(state, cfg, cur, progress, thresh)
        )

        if int(state.get("bar_xp", 0)) < thresh:
            break

        overflow = int(state.get("bar_xp", 0)) - thresh

        if cur >= max_lvl:
            # Halls repeatable loop — no sprint crown / donor
            ev = _grant_shatter_event(state, cfg, cur, reason="halls_loop")
            if ev:
                shatter_events.append(ev)
            state["shatter_claimed_pips"] = []
            state["sprint_xp"] = {}
            state["bar_contrib"] = {}
            state["bar_xp"] = max(0, overflow)
            state["last_level_up_ts"] = now
            continue

        # Zone level-up — crown highest eligible sprint XP
        sprint_before = dict(state.get("sprint_xp") or {})
        ineligible = _sprint_ineligible_keys(state)
        winner_display, winner_xp, _gap = _pick_eligible_sprint_leader(
            sprint_before, ineligible
        )
        winner_key = (winner_display or "").strip().lower()

        old_level = cur
        old_zone = _zone_for_level(old_level, cfg)
        new_level = cur + 1
        new_zone = _zone_for_level(new_level, cfg)
        old_pool = set(unlocked_monsters(old_level, cfg))
        new_pool = set(unlocked_monsters(new_level, cfg))
        newly_unlocked = sorted(new_pool - old_pool)

        hall = list(state.get("hall_of_fame") or [])
        hall.append({
            "level": old_level,
            "zone": old_zone,
            "user": winner_display,
            "xp": winner_xp,
            "ts": now,
        })
        state["hall_of_fame"] = hall
        if winner_key:
            winners = list(state.get("sprint_winners") or [])
            if winner_key not in {str(w).lower() for w in winners}:
                winners.append(winner_key)
            state["sprint_winners"] = winners

        # Level-up Shatter Event for the zone that just completed
        ev = _grant_shatter_event(state, cfg, old_level, reason="level_up")
        if ev:
            shatter_events.append(ev)

        state["level"] = new_level
        state["bar_xp"] = max(0, overflow)
        state["sprint_xp"] = {}
        state["bar_contrib"] = {}
        state["shatter_claimed_pips"] = []
        state["last_level_up_ts"] = now
        leveled_up = True
        donor_reward = _sprint_donor_for_level(old_level, cfg) if winner_display else 0
        level_up_info = {
            "from_level": old_level,
            "to_level": new_level,
            "from_zone": old_zone,
            "to_zone": new_zone,
            "winner": winner_display,
            "winner_xp": winner_xp,
            "newly_unlocked": newly_unlocked,
            "donor_reward": donor_reward,
        }

    return leveled_up, level_up_info, shatter_events


def apply_bar_xp(username: str, xp: int) -> dict[str, Any]:
    """Add XP to the shared co-op bar only (no sprint/heat). Used by paid spends.

    Soft-floor apply_to_bar and per-user bar cap still apply. May level up and
    crown the current summon sprint leader.
    """
    cfg = load_config()
    display = (username or "").strip() or "Anonymous"
    key = display.lower()
    xp_base = max(0, int(xp or 0))
    if xp_base <= 0:
        with _lock:
            state = _load_state()
            level = int(state.get("level", 1))
            return {
                "ok": True,
                "bar_xp_added": 0,
                "xp_base": 0,
                "xp_mult": 1.0,
                "soft_floor": False,
                "level": level,
                "zone": _zone_for_level(level, cfg),
                "bar_xp": int(state.get("bar_xp", 0)),
                "bar_threshold": _threshold_for_level(level, cfg),
                "leveled_up": False,
                "level_up": None,
                "shatter_events": [],
            }

    with _lock:
        state = _load_state()
        now = int(time.time())
        bar_xp_add = xp_base

        xp_mult, soft_floor_applied = soft_floor_multiplier(display, state, cfg)
        sf = _soft_floor_cfg(cfg)
        if soft_floor_applied and xp_mult > 1.0 and bool(sf.get("apply_to_bar", True)):
            bar_xp_add = _apply_xp_mult(bar_xp_add, xp_mult)
        else:
            soft_floor_applied = False
            xp_mult = 1.0

        bar_xp_add = _cap_bar_contrib(state, key, bar_xp_add, cfg)
        leveled_up, level_up_info, shatter_events = _add_bar_xp_and_level_up(
            state, bar_xp_add, cfg, now
        )

        # Refresh heat leader file / totals (bar-only; counts unchanged)
        window = int(cfg.get("heat_window_sec", 900))
        events = _prune_heat(list(state.get("heat_events") or []), now, window)
        heat_user, heat_xp = _leader_from_scores(
            _heat_scores(events),
            {str(e.get("user", "")).lower(): str(e.get("user", "")) for e in events if e.get("user")},
        )
        _write_heat_leader_file(heat_user, heat_xp)
        _write_totals(state)
        _save_state(state)

        level = int(state.get("level", 1))
        result = {
            "ok": True,
            "bar_xp_added": bar_xp_add,
            "xp_base": xp_base,
            "xp_mult": xp_mult,
            "soft_floor": soft_floor_applied,
            "level": level,
            "zone": _zone_for_level(level, cfg),
            "bar_xp": int(state.get("bar_xp", 0)),
            "bar_threshold": _threshold_for_level(level, cfg),
            "leveled_up": leveled_up,
            "level_up": level_up_info,
            "shatter_events": shatter_events,
            "heat_leader": heat_user,
            "heat_xp": heat_xp,
        }

    if leveled_up and level_up_info and level_up_info.get("winner"):
        _grant_sprint_donor(
            level_up_info["winner"], int(level_up_info.get("donor_reward") or 0)
        )

    return result


def apply_summon(username: str, monster: str) -> dict[str, Any]:
    """Apply XP to bar/sprint/heat. May level up. Returns result dict for chat/API."""
    cfg = load_config()
    monster = (monster or "").strip().lower()
    display = (username or "").strip() or "Anonymous"
    key = display.lower()
    xp = monster_xp(monster)
    window = int(cfg.get("heat_window_sec", 900))
    diminish = bool(cfg.get("repeat_mob_diminishing", False))

    with _lock:
        state = _load_state()
        level = int(state.get("level", 1))
        if not is_monster_unlocked(monster, level):
            need = required_level_for_monster(monster) or level + 1
            thresh = _threshold_for_level(level, cfg)
            remaining = max(0, thresh - int(state.get("bar_xp", 0))) if thresh else 0
            return {
                "ok": False,
                "error": (
                    f"Need Bestiary Lv {need} ({_zone_for_level(need, cfg)}) "
                    f"- {remaining} XP left on current level."
                ),
            }

        now = int(time.time())
        xp_base = xp
        bar_xp_add = xp
        sprint_heat_xp = xp

        if diminish:
            last = (state.get("last_monster_by_user") or {}).get(key)
            if last == monster:
                sprint_heat_xp = max(1, xp // 2)

        xp_mult, soft_floor_applied = soft_floor_multiplier(display, state, cfg)
        sf = _soft_floor_cfg(cfg)
        if soft_floor_applied and xp_mult > 1.0:
            if bool(sf.get("apply_to_sprint", True)) or bool(sf.get("apply_to_heat", True)):
                sprint_heat_xp = _apply_xp_mult(sprint_heat_xp, xp_mult)
            if bool(sf.get("apply_to_bar", True)):
                bar_xp_add = _apply_xp_mult(bar_xp_add, xp_mult)
        else:
            soft_floor_applied = False
            xp_mult = 1.0

        bar_xp_add = _cap_bar_contrib(state, key, bar_xp_add, cfg)

        # Sprint
        sprint = dict(state.get("sprint_xp") or {})
        entry = sprint.get(key)
        if isinstance(entry, dict):
            entry = {"xp": int(entry.get("xp", 0)) + sprint_heat_xp, "display": display}
        else:
            entry = {"xp": int(entry or 0) + sprint_heat_xp, "display": display}
        sprint[key] = entry
        state["sprint_xp"] = sprint

        # Heat
        events = list(state.get("heat_events") or [])
        events.append({"user": display, "xp": sprint_heat_xp, "ts": now})
        events = _prune_heat(events, now, window)
        state["heat_events"] = events

        # Session counts
        counts = dict(state.get("session_summon_counts") or {})
        counts[display] = int(counts.get(display, 0) or 0) + 1
        # Prefer display casing as key; mysummons does case-insensitive lookup
        state["session_summon_counts"] = counts

        last_mobs = dict(state.get("last_monster_by_user") or {})
        last_mobs[key] = monster
        state["last_monster_by_user"] = last_mobs

        leveled_up, level_up_info, shatter_events = _add_bar_xp_and_level_up(
            state, bar_xp_add, cfg, now
        )

        heat_user, heat_xp = _leader_from_scores(
            _heat_scores(events),
            {str(e.get("user", "")).lower(): str(e.get("user", "")) for e in events if e.get("user")},
        )
        _write_heat_leader_file(heat_user, heat_xp)
        _write_totals(state)
        _save_state(state)

        result = {
            "ok": True,
            "monster": monster,
            "xp": sprint_heat_xp,
            "xp_base": xp_base,
            "xp_mult": xp_mult,
            "soft_floor": soft_floor_applied,
            "bar_xp_added": bar_xp_add,
            "sprint_heat_xp": sprint_heat_xp,
            "level": int(state.get("level", 1)),
            "zone": _zone_for_level(int(state.get("level", 1)), cfg),
            "bar_xp": int(state.get("bar_xp", 0)),
            "bar_threshold": _threshold_for_level(int(state.get("level", 1)), cfg),
            "leveled_up": leveled_up,
            "level_up": level_up_info,
            "shatter_events": shatter_events,
            "heat_leader": heat_user,
            "heat_xp": heat_xp,
        }

    # Donor grant outside main mutation lock (uses points lock)
    if leveled_up and level_up_info and level_up_info.get("winner"):
        _grant_sprint_donor(
            level_up_info["winner"], int(level_up_info.get("donor_reward") or 0)
        )

    return result


def get_state_payload() -> dict[str, Any]:
    """Full HUD / API payload."""
    cfg = load_config()
    window = int(cfg.get("heat_window_sec", 900))
    max_lvl = _max_level(cfg)

    with _lock:
        state = _load_state()
        now = int(time.time())
        events = _prune_heat(list(state.get("heat_events") or []), now, window)
        if len(events) != len(state.get("heat_events") or []):
            state["heat_events"] = events
            _save_state(state)

        level = int(state.get("level", 1))
        bar_xp = int(state.get("bar_xp", 0))
        thresh = _threshold_for_level(level, cfg)
        zone = _zone_for_level(level, cfg)
        unlocked = unlocked_monsters(level, cfg)

        sprint = dict(state.get("sprint_xp") or {})
        ineligible = _sprint_ineligible_keys(state)
        sprint_user, sprint_xp, sprint_gap = _pick_eligible_sprint_leader(sprint, ineligible)
        sprint_winners = sorted(ineligible)
        _scores_map, display_map = _sprint_scores(sprint)
        sprint_top: list[dict[str, Any]] = []
        for i, (k, xp) in enumerate(_eligible_sprint_ranked(sprint, ineligible)[:3], 1):
            sprint_top.append({
                "rank": i,
                "username": display_map.get(k, k),
                "xp": int(xp),
            })

        heat_scores = _heat_scores(events)
        heat_display = {
            str(e.get("user", "")).lower(): str(e.get("user", ""))
            for e in events if e.get("user")
        }
        heat_user, heat_xp = _leader_from_scores(heat_scores, heat_display)

        # Next locked monsters (first locked level's list)
        next_locked: list[str] = []
        if level < max_lvl:
            for entry in cfg.get("levels") or []:
                if int(entry.get("level", 0)) == level + 1:
                    next_locked = [str(m).lower() for m in (entry.get("monsters") or [])]
                    break

        return {
            "level": level,
            "max_level": max_lvl,
            "zone": zone,
            "bar_xp": bar_xp,
            "bar_threshold": thresh,
            "bar_fraction": (float(bar_xp) / float(thresh)) if thresh > 0 else 1.0,
            "unlocked_monsters": unlocked,
            "next_locked_monsters": next_locked,
            "sprint": {
                "username": sprint_user,
                "xp": sprint_xp,
                "gap": sprint_gap,
                "has_leader": bool(sprint_user),
                "winners_this_stream": sprint_winners,
                "top": sprint_top,
            },
            "heat": {
                "username": heat_user,
                "xp": heat_xp,
                "window_sec": window,
                "has_leader": bool(heat_user),
            },
            "hall_of_fame": list(state.get("hall_of_fame") or []),
            "sprint_winners": sprint_winners,
            "last_level_up_ts": int(state.get("last_level_up_ts", 0) or 0),
            "session_summon_counts": dict(state.get("session_summon_counts") or {}),
            "heat_window_sec": window,
            "sprint_donor_reward": int(cfg.get("sprint_donor_reward", 100)),
            "sprint_donor_reward_per_level": int(
                cfg.get("sprint_donor_reward_per_level", cfg.get("sprint_donor_reward", 100))
            ),
            "next_sprint_donor_reward": _sprint_donor_for_level(level, cfg),
            "shatter": {
                "enabled": bool(_shatter_cfg(cfg).get("enabled", True)),
                "pip_count": _pip_count_for_level(level, cfg),
                "claimed": [
                    int(x) for x in (state.get("shatter_claimed_pips") or [])
                    if str(x).strip().lstrip("-").isdigit()
                ],
                "pip_fractions": _pip_fractions(_pip_count_for_level(level, cfg)),
                "duration_sec": int(_shatter_cfg(cfg).get("duration_sec", 60) or 60),
            },
        }


def user_sprint_xp(username: str) -> int:
    key = (username or "").strip().lower()
    with _lock:
        state = _load_state()
        v = (state.get("sprint_xp") or {}).get(key)
        if isinstance(v, dict):
            return int(v.get("xp", 0) or 0)
        return int(v or 0)


def user_heat_xp(username: str) -> int:
    key = (username or "").strip().lower()
    cfg = load_config()
    window = int(cfg.get("heat_window_sec", 900))
    with _lock:
        state = _load_state()
        now = int(time.time())
        events = _prune_heat(list(state.get("heat_events") or []), now, window)
        scores = _heat_scores(events)
        return int(scores.get(key, 0))


def user_session_count(username: str) -> int:
    key = (username or "").strip().lower()
    with _lock:
        state = _load_state()
        counts = state.get("session_summon_counts") or {}
        for name, n in counts.items():
            if str(name).lower() == key:
                return int(n)
        return 0


def reset_bestiary_state() -> None:
    """Clear bestiary session (stream started)."""
    with _lock:
        _save_state(_empty_state())
        for path in (HEAT_LEADER_FILE, TOP_SUMMONER_FILE, TOTAL_SUMMONS_FILE, SUMMON_COUNTS_FILE):
            try:
                if os.path.exists(path):
                    os.remove(path)
            except OSError:
                pass
