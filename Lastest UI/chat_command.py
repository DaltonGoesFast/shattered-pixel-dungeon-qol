"""
Chat command dispatcher for POST /api/chat-command.

Routes earn, spend, query, and meta commands. Spend delegates to points_command.run_points_command.
"""
import json
import os
import secrets
import threading
import time
import urllib.error
import urllib.request
from typing import Any, Optional

import chat_messages
import presentation_config
from points_command import (
    BOT_USER,
    COMMANDS,
    DOUBLE_POINTS_END_FILE,
    POINTS_FILE,
    SCRIPT_DIR,
    TOP_SUMMONER_FILE,
    ChatResult,
    chat_cap_for_user,
    chat_earn_multiplier,
    chat_pts,
    effective_total,
    get_config,
    grant_flat_donor_points,
    ignores_chat_point_cap,
    ignores_command_cooldowns,
    is_double_points_active,
    points_lock,
    read_points,
    run_points_command,
    sanitize_play_along_rewards,
    write_points,
    _get_user_data,
)

SESSION_STATE_FILE = os.path.join(SCRIPT_DIR, "session_state.json")
GAME_SUMMARY_JSON = os.path.join(SCRIPT_DIR, "game_summary.json")
SUMMON_COUNTS_FILE = os.path.join(SCRIPT_DIR, "summon_session_counts.json")
CHAT_AUDIT_LOG = os.path.join(SCRIPT_DIR, "chat_command_audit.jsonl")
LINKED_CLIENTS_FILE = os.path.join(SCRIPT_DIR, "linked_clients.json")
SUMMON_COOLDOWN_SEC = 60
LINK_CODE_TTL_SEC = 300
_LINK_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
_link_lock = threading.Lock()
_pending_link_codes: dict[str, dict] = {}

# Rare Twitch/YouTube duplicate chat events can run R01 twice; collapse identical spends.
SPEND_DEDUPE_SEC = 2.0
_dedupe_lock = threading.Lock()
_spend_inflight: set[str] = set()
_spend_recent: dict[str, float] = {}

# kesha/mimic/tooth/seed/challenge are native Streamer.bot actions; R1 skips those.
# pact/pacts stay here so R1's POST is answered from game_summary.json — do not skip them.
STREAM_INFO_COMMANDS = frozenset({
    "kesha", "mimic", "tooth", "seed", "challenge", "challenges",
    "pact", "pacts",
})

# Modifiers.MASKS.length — "all pacts" reply when every pact is on.
PACT_TOTAL = 16

COMMANDS_DOC_URL = (
    "https://github.com/DaltonGoesFast/shattered-pixel-dungeon-qol/blob/master/COMMANDS.md"
)

# Parsed !command -> points_command.py handler name
COMMAND_ALIASES = {
    "points": "balance",
    "givepoints": "transfer",
    "corruptally": "corruptally",
    "2x": "doublepoints",
}

SPEND_COMMANDS = frozenset(k for k in COMMANDS if k not in (
    "superchat", "cheer", "giftmembership", "gift_membership", "giftsub", "balance",
))


def _load_session_state() -> dict:
    if not os.path.exists(SESSION_STATE_FILE):
        return {}
    try:
        with open(SESSION_STATE_FILE, encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return {}


def _save_session_state(state: dict) -> None:
    with open(SESSION_STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2)


def reset_session_state() -> None:
    """Clear per-stream session flags (Stream Started)."""
    state = {
        "fard_used": {},
        "first_words": {},
        "summon_last": {},
        "members": {},
        "stream_started_at": int(time.time()),
        "hero_name_goo_polled": False,
        "hero_name_goo_polled_seed": None,
    }
    _save_session_state(state)
    # Summon march + bestiary session files
    for path in (SUMMON_COUNTS_FILE, TOP_SUMMONER_FILE,
                 os.path.join(SCRIPT_DIR, "totalsummons.txt"),
                 os.path.join(SCRIPT_DIR, "heat_leader.txt")):
        try:
            if os.path.exists(path):
                os.remove(path)
        except OSError:
            pass
    try:
        from summon_bestiary import reset_bestiary_state
        reset_bestiary_state()
    except Exception as e:
        print(f"bestiary reset error: {e}")
    try:
        from hero_name import clear_winners
        clear_winners()
    except Exception as e:
        print(f"hero_name winners reset error: {e}")


def _track_member(state: dict, key: str, is_sub: bool, is_member: bool) -> None:
    if is_sub or is_member:
        state.setdefault("members", {})[key] = True


def _apply_capped_chat_award(
    data: dict,
    key: str,
    username: str,
    to_add: int,
    cap: Optional[int],
    update_last: Optional[int] = None,
) -> tuple[int, Optional[str], int]:
    """Apply chat earn with cap. Returns (earned, cap_nudge_message, new_total)."""
    pts, last, donation_pts, role = _get_user_data(data, key)
    cur_chat = chat_pts(pts, donation_pts)
    total = effective_total(pts, donation_pts)

    if to_add <= 0:
        return 0, None, total

    if cap is None or cap <= 0:
        actual = to_add
        pts += actual
        if update_last is not None:
            last = update_last
        data[key] = (pts, last, donation_pts, role)
        return actual, None, effective_total(pts, donation_pts)

    if cur_chat >= cap:
        return 0, chat_messages.chat_cap_nudge(username, cap), total

    actual = min(to_add, cap - cur_chat)
    pts += actual
    if update_last is not None:
        last = update_last
    data[key] = (pts, last, donation_pts, role)
    new_total = effective_total(pts, donation_pts)
    new_chat = chat_pts(pts, donation_pts)
    nudge = chat_messages.chat_cap_nudge(username, cap) if new_chat >= cap else None
    return actual, nudge, new_total


def schedule_stream_offline() -> dict:
    """Record Stream Offline time for debounced auto-bank reset."""
    state = _load_session_state()
    now = int(time.time())
    if not state.get("stream_offline_at"):
        state["stream_offline_at"] = now
        _save_session_state(state)
    return {"scheduled_at": state["stream_offline_at"]}


def end_stream_session() -> dict:
    """Auto-bank remaining chat pts and zero chat for all users."""
    cfg = get_config()
    auto_reg = float(cfg.get("bank_ratio_auto", 0.05))
    auto_mem = float(cfg.get("bank_ratio_auto_member", 0.10))
    state = _load_session_state()
    members = state.get("members", {})
    users_affected = 0
    donor_awarded = 0

    try:
        with points_lock():
            data = read_points()
            for key in list(data.keys()):
                if ignores_chat_point_cap(key, cfg):
                    continue
                pts, last, donation_pts, role = _get_user_data(data, key)
                c = chat_pts(pts, donation_pts)
                if c <= 0:
                    continue
                ratio = auto_mem if members.get(key) else auto_reg
                donor_gain = int(c * ratio)
                new_donation = donation_pts + donor_gain
                data[key] = (new_donation, last, new_donation, role)
                users_affected += 1
                donor_awarded += donor_gain
            write_points(data)
    except TimeoutError:
        return {"ok": False, "error": chat_messages.POINTS_BUSY}

    state.pop("stream_offline_at", None)
    state.pop("members", None)
    _save_session_state(state)
    return {
        "ok": True,
        "users_affected": users_affected,
        "donor_pts_awarded": donor_awarded,
    }


def try_execute_pending_stream_end() -> dict:
    """Execute stream-end reset if offline was scheduled and debounce elapsed."""
    state = _load_session_state()
    offline_at = state.get("stream_offline_at")
    if not offline_at:
        return {"ok": True, "executed": False, "reason": "not_scheduled"}

    debounce_sec = int(float(get_config().get("reset_debounce_hours", 4)) * 3600)
    elapsed = int(time.time()) - int(offline_at)
    if elapsed < debounce_sec:
        return {
            "ok": True,
            "executed": False,
            "reason": "debounce",
            "retry_after_sec": debounce_sec - elapsed,
            "scheduled_at": offline_at,
        }

    result = end_stream_session()
    return {"executed": True, **result}


def try_execute_stream_end(*, force: bool = False) -> dict:
    """Run debounced stream-end reset, or schedule only when not forced."""
    if force:
        result = end_stream_session()
        return {"executed": True, **result}

    schedule_stream_offline()
    return try_execute_pending_stream_end()


def _context_flags(context: dict) -> tuple[bool, bool, bool]:
    ctx = context or {}
    is_sub = bool(ctx.get("isSubscribed") or ctx.get("is_subscribed"))
    is_member = bool(ctx.get("isMember") or ctx.get("userIsSponsor") or ctx.get("is_member"))
    is_broadcaster = bool(ctx.get("isBroadcaster") or ctx.get("is_broadcaster"))
    return is_sub, is_member, is_broadcaster


def parse_chat_command(raw_message: str) -> tuple[Optional[str], list[str]]:
    text = (raw_message or "").strip()
    if not text.startswith("!"):
        return None, []
    body = text[1:].strip()
    if not body:
        return None, []
    parts = body.split()
    return parts[0].lower(), parts[1:]


def _build_spend_args(cmd: str, args: list[str], username: str) -> list[str]:
    if cmd in ("spawn", "champion"):
        if not args:
            return []
        return [args[0].lower(), username]
    if cmd == "gold":
        if not args:
            return []
        return [args[0], username]
    if cmd == "transfer":
        if len(args) < 2:
            return []
        return [args[0], args[1], username]
    if cmd == "wand":
        if args and args[0].lower() in ("common", "uncommon", "rare", "veryrare", "very_rare"):
            return [args[0], username]
        return [username]
    return [username]


def _extend_double_points(seconds: int) -> None:
    now = int(time.time())
    end = now
    try:
        if os.path.exists(DOUBLE_POINTS_END_FILE):
            with open(DOUBLE_POINTS_END_FILE, encoding="utf-8") as f:
                raw = f.read().strip()
            cur = int(raw) if raw and raw != "0" else 0
            if cur > now:
                end = cur
    except (ValueError, OSError):
        pass
    with open(DOUBLE_POINTS_END_FILE, "w", encoding="utf-8") as f:
        f.write(str(end + seconds))


def _set_double_points_minutes(minutes: int) -> None:
    end = int(time.time()) + minutes * 60
    with open(DOUBLE_POINTS_END_FILE, "w", encoding="utf-8") as f:
        f.write(str(end))


def _award_points(username: str, base: int, is_sub: bool, is_member: bool,
                  update_last_earn: bool = True) -> tuple[int, int]:
    """Add chat earn points. Returns (earned, new_total)."""
    key = username.strip().lower()
    cfg = get_config()
    cap = chat_cap_for_user(username, cfg)
    if base <= 0:
        with points_lock():
            data = read_points()
            pts, _, donation_pts, _ = _get_user_data(data, key)
            return 0, effective_total(pts, donation_pts)

    mult = chat_earn_multiplier(username, is_sub, is_member)
    to_add = base * mult
    now = int(time.time())
    with points_lock():
        data = read_points()
        earned, _, total = _apply_capped_chat_award(
            data, key, username, to_add, cap,
            update_last=now if update_last_earn else None,
        )
        write_points(data)
        return earned, total


def _maybe_award_first_words(username: str, is_sub: bool, is_member: bool) -> int:
    """One-time per-stream bonus (+5 base × multipliers). Returns bonus earned (0 if already awarded)."""
    key = username.strip().lower()
    if not key or key == BOT_USER.lower():
        return 0

    state = _load_session_state()
    first_words = state.setdefault("first_words", {})
    if first_words.get(key):
        return 0

    cfg = get_config()
    bonus_base = int(cfg.get("first_words_bonus", 5))
    cap = chat_cap_for_user(username, cfg)
    if bonus_base <= 0:
        return 0

    mult = chat_earn_multiplier(username, is_sub, is_member)
    bonus = bonus_base * mult
    now = int(time.time())

    try:
        with points_lock():
            data = read_points()
            earned, nudge, _ = _apply_capped_chat_award(
                data, key, username, bonus, cap, update_last=None,
            )
            write_points(data)
            if earned <= 0:
                return 0
    except TimeoutError:
        return 0

    first_words[key] = True
    state["first_words"] = first_words
    _track_member(state, key, is_sub, is_member)
    _save_session_state(state)
    return earned


def handle_earn_message(username: str, is_sub: bool, is_member: bool) -> ChatResult:
    if not username or username.strip().lower() == BOT_USER.lower():
        return ChatResult(ok=True, message=None, earned=0)

    cfg = get_config()
    cooldown = int(cfg.get("chat_cooldown_sec", 20))
    base = int(cfg.get("points_per_message", 2))
    cap = chat_cap_for_user(username, cfg)
    key = username.strip().lower()
    now = int(time.time())

    state = _load_session_state()
    earned_total = 0
    nudge_msg = None

    try:
        with points_lock():
            data = read_points()
            pts, last, donation_pts, role = _get_user_data(data, key)

            if (
                not ignores_command_cooldowns(username)
                and cooldown > 0
                and last > 0
                and (now - last) < cooldown
            ):
                total = effective_total(pts, donation_pts)
                return ChatResult(ok=True, message=None, pts=total, earned=0)

            mult = chat_earn_multiplier(username, is_sub, is_member)
            to_add = base * mult
            earned_total, nudge_msg, total = _apply_capped_chat_award(
                data, key, username, to_add, cap, update_last=now,
            )
            write_points(data)
    except TimeoutError:
        return ChatResult(ok=False, message=chat_messages.POINTS_BUSY)

    _track_member(state, key, is_sub, is_member)
    _save_session_state(state)

    return ChatResult(
        ok=True,
        message=nudge_msg,
        pts=total,
        earned=earned_total,
        extra={"earn": "message"},
    )


def handle_earn_passive(username: str, is_sub: bool, is_member: bool) -> ChatResult:
    if not username or username.strip().lower() == BOT_USER.lower():
        return ChatResult(ok=True, message=None, earned=0)

    cfg = get_config()
    cooldown = int(cfg.get("passive_cooldown_sec", 60))
    cap = chat_cap_for_user(username, cfg)
    base = 1
    key = username.strip().lower()
    now = int(time.time())
    state = _load_session_state()
    nudge_msg = None

    try:
        with points_lock():
            data = read_points()
            if key not in data:
                return ChatResult(ok=True, message=None, earned=0)
            pts, last, donation_pts, role = _get_user_data(data, key)
            if (
                not ignores_command_cooldowns(username)
                and cooldown > 0
                and last > 0
                and (now - last) < cooldown
            ):
                return ChatResult(ok=True, message=None, pts=effective_total(pts, donation_pts), earned=0)

            mult = chat_earn_multiplier(username, is_sub, is_member)
            to_add = base * mult
            earned, nudge_msg, total = _apply_capped_chat_award(
                data, key, username, to_add, cap, update_last=now,
            )
            write_points(data)
    except TimeoutError:
        return ChatResult(ok=False, message=chat_messages.POINTS_BUSY)

    _track_member(state, key, is_sub, is_member)
    _save_session_state(state)

    return ChatResult(ok=True, message=nudge_msg, pts=total, earned=earned, extra={"earn": "passive"})


def handle_points_query(username: str) -> ChatResult:
    cfg = get_config()
    cap = chat_cap_for_user(username, cfg)
    key = username.strip().lower()
    try:
        with points_lock():
            data = read_points()
            pts, _, donation_pts, _ = _get_user_data(data, key)
            c = chat_pts(pts, donation_pts)
            total = effective_total(pts, donation_pts)
    except TimeoutError:
        return ChatResult(ok=False, message=chat_messages.POINTS_BUSY)

    sprint_xp = 0
    heat_xp = 0
    try:
        from summon_bestiary import user_heat_xp, user_sprint_xp
        sprint_xp = user_sprint_xp(username)
        heat_xp = user_heat_xp(username)
    except Exception:
        pass

    return ChatResult(
        ok=True,
        message=chat_messages.points_balance_v11(
            username, c, cap, donation_pts, sprint_xp=sprint_xp, heat_xp=heat_xp
        ),
        pts=total,
        extra={
            "command": "points",
            "chat_pts": c,
            "donor_pts": donation_pts,
            "chat_cap": cap,
            "sprint_xp": sprint_xp,
            "heat_xp": heat_xp,
        },
    )


def handle_bank(username: str, args: list[str]) -> ChatResult:
    if not username:
        return ChatResult(ok=False, message=chat_messages.bank_invalid_amount(""))
    bank_args = [username]
    if args:
        bank_args.append(args[0])
    return run_points_command("bank", bank_args, username)


def handle_toppoints() -> ChatResult:
    try:
        with points_lock():
            entries = []
            if os.path.exists(POINTS_FILE):
                data = read_points()
                for key, row in data.items():
                    _, _, donation_pts, _ = _get_user_data(data, key)
                    if donation_pts > 0:
                        entries.append((key, donation_pts))
            entries.sort(key=lambda x: x[1], reverse=True)
    except TimeoutError:
        return ChatResult(ok=False, message=chat_messages.POINTS_BUSY)

    return ChatResult(
        ok=True,
        message=chat_messages.toppoints_leaderboard(entries),
        extra={"command": "toppoints", "sort": "donor"},
    )


def _current_run_seed() -> Optional[str]:
    """Best-effort seed from game_summary.json (same run the overlay sees)."""
    try:
        if os.path.exists(GAME_SUMMARY_JSON):
            with open(GAME_SUMMARY_JSON, encoding="utf-8") as f:
                data = json.load(f)
            seed = data.get("seed")
            if seed is not None and str(seed).strip():
                return str(seed).strip()
    except (OSError, json.JSONDecodeError, TypeError, ValueError):
        pass
    return None


def clear_hero_name_goo_poll_flag() -> None:
    """Allow another post-Goo name poll (hero died / new run)."""
    state = _load_session_state()
    if state.get("hero_name_goo_polled") or state.get("hero_name_goo_polled_seed"):
        state["hero_name_goo_polled"] = False
        state["hero_name_goo_polled_seed"] = None
        _save_session_state(state)


def try_hero_name_goo_poll() -> dict:
    """Once per dungeon seed after Goo: draw 4 names and DoAction N01."""
    from hero_name import try_start_goo_poll

    state = _load_session_state()
    seed = _current_run_seed()
    polled_seed = state.get("hero_name_goo_polled_seed")
    # Prefer seed so a new run is not blocked by an earlier test / abandoned run.
    if seed and polled_seed and str(polled_seed) == str(seed):
        return {"ok": False, "reason": "already_polled_this_run", "seed": seed}
    # Legacy boolean only blocks when we have no seed to compare.
    if not seed and state.get("hero_name_goo_polled"):
        return {"ok": False, "reason": "already_polled_this_run", "seed": None}

    result = try_start_goo_poll()
    if result.get("ok"):
        state["hero_name_goo_polled"] = True
        state["hero_name_goo_polled_seed"] = seed
        _save_session_state(state)
        result = dict(result)
        result["seed"] = seed
    return result


def handle_name(username: str, args: list[str]) -> ChatResult:
    """Stash viewer's last hero-name suggestion. Silent on success."""
    from hero_name import submit_name

    key = (username or "").strip().lower()
    if not key or key == BOT_USER:
        return ChatResult(ok=True, message=None, extra={"command": "name", "skipped": "bot"})
    raw = " ".join(args).strip() if args else ""
    ok, _detail = submit_name(username, raw)
    if ok:
        return ChatResult(ok=True, message=None, extra={"command": "name"})
    return ChatResult(
        ok=False,
        message=chat_messages.name_rejected(username),
        extra={"command": "name", "reason": _detail},
    )


def handle_fard(username: str, is_sub: bool, is_member: bool) -> ChatResult:
    key = username.strip().lower()
    state = _load_session_state()
    used = state.setdefault("fard_used", {})
    if used.get(key):
        try:
            with points_lock():
                data = read_points()
                pts, _, donation_pts, _ = _get_user_data(data, key)
                total = effective_total(pts, donation_pts)
        except TimeoutError:
            total = None
        return ChatResult(ok=True, message=chat_messages.fard_already_used(username), pts=total, extra={"command": "fard", "skipped": "already_used"})

    used[key] = True
    state["fard_used"] = used
    _save_session_state(state)

    extend_min = 6 if (is_sub or is_member) else 3
    _extend_double_points(extend_min * 60)

    pres = dict(presentation_config.FARD_PRESENTATION)
    chat_line = chat_messages.fard_success(username, extend_min)

    try:
        with points_lock():
            data = read_points()
            pts, _, donation_pts, _ = _get_user_data(data, key)
            total = effective_total(pts, donation_pts)
    except TimeoutError:
        total = None

    return ChatResult(
        ok=True,
        message=chat_line,
        pts=total,
        presentation=pres,
        extra={"command": "fard", "extend_minutes": extend_min},
    )


def handle_doublepoints(args: list[str], is_broadcaster: bool) -> ChatResult:
    if not is_broadcaster:
        return ChatResult(ok=False, message=chat_messages.DOUBLEPOINTS_BROADCASTER_ONLY)
    minutes = 0
    if args:
        try:
            minutes = int(args[0])
        except ValueError:
            minutes = 0
    if minutes < 1 or minutes > 120:
        return ChatResult(ok=False, message=chat_messages.USAGE["doublepoints"])
    _set_double_points_minutes(minutes)
    return ChatResult(
        ok=True,
        message=chat_messages.doublepoints_active(minutes),
        extra={"command": "doublepoints", "minutes": minutes},
    )


def _summon_resolve_monster(raw: str):
    from summon_bestiary import resolve_monster
    return resolve_monster(raw)


def _summon_post(username: str, monster: str, xp: int = 0, bestiary_level: int = 1) -> None:
    payload = json.dumps({
        "username": username,
        "monster": monster,
        "layout": "horizontal",
        "xp": xp,
        "bestiary_level": bestiary_level,
    }).encode("utf-8")
    req = urllib.request.Request(
        "http://127.0.0.1:5000/api/summon-march",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=5) as resp:
        if not (200 <= resp.status < 300):
            raise urllib.error.HTTPError(req.full_url, resp.status, "", resp.headers, None)


def handle_summon(username: str, args: list[str]) -> ChatResult:
    key = username.strip().lower()
    state = _load_session_state()
    summon_last = state.setdefault("summon_last", {})
    now = int(time.time())
    last = int(summon_last.get(key, 0) or 0)
    if (
        not ignores_command_cooldowns(username)
        and last
        and (now - last) < SUMMON_COOLDOWN_SEC
    ):
        return ChatResult(ok=True, message=None, extra={"command": "summon", "skipped": "cooldown"})

    monster_arg = args[0] if args else ""
    monster, err = _summon_resolve_monster(monster_arg)
    if err:
        return ChatResult(ok=False, message=err)

    from summon_bestiary import apply_summon
    bestiary = apply_summon(username, monster)
    if not bestiary.get("ok"):
        return ChatResult(ok=False, message=bestiary.get("error") or "Summon failed")

    xp = int(bestiary.get("xp", 0) or 0)
    level = int(bestiary.get("level", 1) or 1)
    soft_floor = bool(bestiary.get("soft_floor"))
    xp_mult = float(bestiary.get("xp_mult", 1.0) or 1.0)
    march_ok = True
    try:
        _summon_post(username, monster, xp=xp, bestiary_level=level)
    except urllib.error.HTTPError:
        march_ok = False
    except (urllib.error.URLError, OSError):
        march_ok = False

    summon_last[key] = now
    state["summon_last"] = summon_last
    _save_session_state(state)

    pres = dict(presentation_config.SUMMON_PRESENTATION) if march_ok else None
    # Routine summons stay silent (overlay + sound confirm). Chat only for
    # level-up / Halls loop / Shatter Event / overlay-down.
    progress = chat_messages.bestiary_progress_line(bestiary)
    shatter = chat_messages.shatter_events_suffix(bestiary.get("shatter_events") or [])
    msg = (progress + shatter).strip() or None
    if not march_ok:
        offline = "overlay server offline - XP counted, march not queued"
        msg = f"{msg} ({offline})" if msg else f"{username} summoned a {monster} ({offline})"

    return ChatResult(
        ok=True,
        message=msg,
        presentation=pres,
        extra={
            "command": "summon",
            "monster": monster,
            "xp": xp,
            "xp_mult": xp_mult,
            "soft_floor": soft_floor,
            "bestiary_level": level,
            "leveled_up": bool(bestiary.get("leveled_up")),
            "halls_looped": bool(bestiary.get("halls_looped")),
            "shatter_events": list(bestiary.get("shatter_events") or []),
            "march_queued": march_ok,
        },
    )


def handle_topsummoner() -> ChatResult:
    from summon_bestiary import get_sprint_leader
    user, xp, gap = get_sprint_leader()
    return ChatResult(
        ok=True,
        message=chat_messages.sprint_leader_line(user, xp, gap),
        extra={"command": "topsummoner", "username": user, "xp": xp, "gap": gap},
    )


def handle_sprint(username: str) -> ChatResult:
    from summon_bestiary import get_sprint_standing
    s = get_sprint_standing(username)
    return ChatResult(
        ok=True,
        message=chat_messages.sprint_standing_line(
            str(s.get("leader") or ""),
            int(s.get("leader_xp") or 0),
            int(s.get("gap") or 0),
            user=username,
            your_xp=int(s.get("your_xp") or 0),
            rank=s.get("rank"),
            crowned=bool(s.get("crowned")),
        ),
        extra={"command": "sprint", **s},
    )


def handle_heat(username: str = "") -> ChatResult:
    from summon_bestiary import get_state_payload, user_heat_xp
    payload = get_state_payload()
    heat = payload.get("heat") or {}
    user = heat.get("username") or ""
    xp = int(heat.get("xp") or 0)
    window = int(heat.get("window_sec") or 900)
    yours = user_heat_xp(username) if username else 0
    return ChatResult(
        ok=True,
        message=chat_messages.heat_leader_line(
            user, xp, window, yours, requester=username
        ),
        extra={"command": "heat", "username": user, "xp": xp},
    )


def handle_bestiary(username: str) -> ChatResult:
    from summon_bestiary import get_state_payload, user_heat_xp, user_sprint_xp
    p = get_state_payload()
    sprint_xp = user_sprint_xp(username)
    heat_xp = user_heat_xp(username)
    return ChatResult(
        ok=True,
        message=chat_messages.bestiary_status(
            int(p.get("level", 1)),
            str(p.get("zone") or ""),
            int(p.get("bar_xp", 0)),
            int(p.get("bar_threshold", 0)),
            list(p.get("unlocked_monsters") or []),
            user=username,
            sprint_xp=sprint_xp,
            heat_xp=heat_xp,
        ),
        extra={
            "command": "bestiary",
            "level": p.get("level"),
            "sprint_xp": sprint_xp,
            "heat_xp": heat_xp,
        },
    )


def handle_summonhall() -> ChatResult:
    from summon_bestiary import get_state_payload
    p = get_state_payload()
    return ChatResult(
        ok=True,
        message=chat_messages.summon_hall_line(list(p.get("hall_of_fame") or [])),
        extra={"command": "summonhall"},
    )


def _load_game_summary() -> dict:
    if not os.path.exists(GAME_SUMMARY_JSON):
        return {}
    try:
        with open(GAME_SUMMARY_JSON, encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return {}


def _has_mimic_tooth(data: dict) -> bool:
    for bag in data.get("inventory") or []:
        if not isinstance(bag, dict):
            continue
        for item in bag.get("items") or []:
            if not isinstance(item, dict):
                continue
            if str(item.get("name", "")).lower() == "mimictooth":
                return True
    equipped = data.get("equipped") or {}
    if isinstance(equipped, dict):
        for slot_val in equipped.values():
            if isinstance(slot_val, dict) and str(slot_val.get("name", "")).lower() == "mimictooth":
                return True
    return False


def _format_challenges(challenges: list) -> str:
    names = [str(c) for c in challenges if c]
    if len(names) == 9:
        return "All Challenges Active (9 Challenges)"
    if names:
        return ", ".join(names)
    return "None"


def _format_pacts(pacts: list) -> str:
    names = [str(p) for p in pacts if p]
    if len(names) == PACT_TOTAL:
        return f"All Pacts Active ({PACT_TOTAL} Pacts)"
    if names:
        return ", ".join(names)
    return "None"


def handle_stream_info(cmd: str) -> ChatResult:
    """Silent ok for OBS/sound commands; chat replies for seed/challenges/pacts."""
    if cmd in ("kesha", "mimic", "tooth"):
        return ChatResult(ok=True, message=None, extra={"command": cmd, "stream_info": True})
    data = _load_game_summary()
    if cmd == "seed":
        seed = str(data.get("seed") or "Unknown")
        return ChatResult(
            ok=True,
            message=chat_messages.game_seed(seed),
            extra={"command": "seed", "stream_info": True},
        )
    if cmd in ("challenge", "challenges"):
        raw = data.get("challenges") or []
        text = _format_challenges(raw if isinstance(raw, list) else [])
        return ChatResult(
            ok=True,
            message=chat_messages.active_challenges(text),
            extra={"command": cmd, "stream_info": True},
        )
    if cmd in ("pact", "pacts"):
        raw = data.get("pacts") or []
        text = _format_pacts(raw if isinstance(raw, list) else [])
        return ChatResult(
            ok=True,
            message=chat_messages.active_pacts(text),
            extra={"command": cmd, "stream_info": True},
        )
    return ChatResult(ok=True, message=None, extra={"command": cmd, "stream_info": True})


def _economy_reminder_text() -> str:
    cfg = get_config()
    return chat_messages.economy_reminder(
        int(cfg.get("chat_point_cap", 500)),
        int(cfg.get("points_per_message", 2)),
        int(cfg.get("chat_cooldown_sec", 20)),
        float(cfg.get("bank_ratio_manual", 0.10)),
    )


def handle_economy() -> ChatResult:
    """Live economy blurb for !economy / Streamer.bot timed reminders."""
    msg = _economy_reminder_text()
    return ChatResult(ok=True, message=msg, extra={"command": "economy"})


def handle_help(args: list[str] | None = None) -> ChatResult:
    args = args or []
    if not args:
        return ChatResult(
            ok=True,
            message=chat_messages.help_link(COMMANDS_DOC_URL, _economy_reminder_text()),
            extra={"command": "help", "url": COMMANDS_DOC_URL},
        )
    topic = args[0]
    text = chat_messages.command_help(topic)
    if text:
        return ChatResult(
            ok=True,
            message=text,
            extra={"command": "help", "topic": topic.lstrip("!").lower()},
        )
    return ChatResult(
        ok=False,
        message=chat_messages.help_unknown(topic),
        extra={"command": "help", "topic": topic.lstrip("!").lower()},
    )


def handle_mysummons(username: str) -> ChatResult:
    from summon_bestiary import user_heat_xp, user_session_count, user_sprint_xp
    count = user_session_count(username)
    sprint_xp = user_sprint_xp(username)
    heat_xp = user_heat_xp(username)
    return ChatResult(
        ok=True,
        message=chat_messages.mysummons_line(username, count, sprint_xp, heat_xp),
        extra={
            "command": "mysummons",
            "count": count,
            "sprint_xp": sprint_xp,
            "heat_xp": heat_xp,
        },
    )


def handle_spend(cmd: str, args: list[str], username: str) -> ChatResult:
    handler = COMMAND_ALIASES.get(cmd, cmd)
    if handler not in SPEND_COMMANDS:
        return ChatResult(ok=False, message=chat_messages.unknown_command(cmd))
    spend_args = _build_spend_args(handler, args, username)
    if handler in ("spawn", "champion", "gold", "transfer") and not spend_args:
        usage = chat_messages.USAGE.get(handler, chat_messages.USAGE.get(cmd))
        return ChatResult(ok=False, message=usage or chat_messages.unknown_command(cmd))
    return run_points_command(handler, spend_args, username)


def _spend_dedupe_key(username: str, raw: str) -> str:
    return f"{(username or '').strip().lower()}|{(raw or '').strip().lower()}"


def _begin_spend_dedupe(key: str) -> bool:
    """Return True if this request should run; False if duplicate/in-flight (skip charge)."""
    now = time.monotonic()
    with _dedupe_lock:
        stale = [k for k, t in _spend_recent.items() if now - t > SPEND_DEDUPE_SEC * 4]
        for k in stale:
            _spend_recent.pop(k, None)
        if key in _spend_inflight:
            return False
        prev = _spend_recent.get(key)
        if prev is not None and (now - prev) < SPEND_DEDUPE_SEC:
            return False
        _spend_inflight.add(key)
        return True


def _end_spend_dedupe(key: str) -> None:
    with _dedupe_lock:
        _spend_inflight.discard(key)
        _spend_recent[key] = time.monotonic()


def _audit_chat_command(body: dict, result: ChatResult, deduped: bool = False) -> None:
    """Append one JSON line for rare double-fire forensics (chat_command_audit.jsonl)."""
    try:
        raw = body.get("rawMessage") or body.get("message") or ""
        if not str(raw).strip().startswith("!"):
            return
        line = {
            "ts": round(time.time(), 3),
            "user": body.get("username") or body.get("userName") or "",
            "platform": body.get("platform") or "",
            "raw": str(raw)[:200],
            "ok": bool(result.ok),
            "pts": result.pts,
            "deduped": deduped,
            "msg": (result.message or "")[:160],
        }
        with open(CHAT_AUDIT_LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps(line, ensure_ascii=False) + "\n")
    except OSError:
        pass


def _limit_chat_result(result: ChatResult, platform: str) -> ChatResult:
    """Keep viewer-facing replies inside YouTube's 150-character chat cap."""
    result.message = chat_messages.clamp_for_platform(result.message, platform)
    pres = result.presentation
    if isinstance(pres, dict) and isinstance(pres.get("chat"), str):
        pres["chat"] = chat_messages.clamp_for_platform(pres["chat"], platform)
    return result


def dispatch_typed(body: dict) -> ChatResult:
    """Handle typed requests (passive earn, donations via type field)."""
    req_type = (body.get("type") or "").strip().lower()
    username = (body.get("username") or body.get("userName") or "").strip()
    context = body.get("context") or {}
    is_sub, is_member, _ = _context_flags(context)

    if req_type == "earn.passive":
        return handle_earn_passive(username, is_sub, is_member)
    if req_type == "earn.message":
        return handle_earn_message(username, is_sub, is_member)

    return ChatResult(ok=False, message=f"Unknown request type: {req_type}")


def dispatch_chat_command(body: dict) -> ChatResult:
    """Main entry: rawMessage router or typed body."""
    platform = str(body.get("platform") or "")
    chat_messages.set_reply_platform(platform)
    try:
        result = _dispatch_chat_command_inner(body)
        return _limit_chat_result(result, platform)
    finally:
        chat_messages.set_reply_platform("")


def _dispatch_chat_command_inner(body: dict) -> ChatResult:
    """Unclamped router; dispatch_chat_command applies the YouTube length cap."""
    if body.get("type"):
        return dispatch_typed(body)

    raw = body.get("rawMessage") or body.get("message") or ""
    username = (body.get("username") or body.get("userName") or "").strip()
    context = body.get("context") or {}
    is_sub, is_member, is_broadcaster = _context_flags(context)

    first_words_bonus = _maybe_award_first_words(username, is_sub, is_member) if username else 0

    cmd, args = parse_chat_command(raw)
    if cmd is None:
        if raw.strip() and not raw.strip().startswith("!"):
            result = handle_earn_message(username, is_sub, is_member)
            if first_words_bonus and result.ok:
                result.earned = (result.earned or 0) + first_words_bonus
                extra = dict(result.extra or {})
                extra["first_words_bonus"] = first_words_bonus
                result.extra = extra
            return result
        return ChatResult(ok=False, message="Empty message.")

    deduped = False
    dedupe_key = None
    handler = COMMAND_ALIASES.get(cmd, cmd)
    # Collapse rare platform duplicate events for point-mutating commands only.
    dedupe_cmds = SPEND_COMMANDS | {"bank", "doublepoints", "transfer", "givepoints"}
    if handler in dedupe_cmds or cmd in dedupe_cmds:
        dedupe_key = _spend_dedupe_key(username, raw)
        if not _begin_spend_dedupe(dedupe_key):
            deduped = True
            result = ChatResult(
                ok=True,
                message=None,
                extra={"command": handler, "deduped": True},
            )
            _audit_chat_command(body, result, deduped=True)
            return result

    try:
        if cmd in ("points",):
            result = handle_points_query(username)
        elif cmd == "bob":
            result = handle_points_query("bob")
            extra = dict(result.extra or {})
            extra["command"] = "bob"
            result.extra = extra
        elif cmd == "bank":
            result = handle_bank(username, args)
        elif cmd in ("toppoints", "leaderboard"):
            result = handle_toppoints()
        elif cmd == "name":
            result = handle_name(username, args)
        elif cmd == "link":
            result = handle_link(username, args, str(body.get("platform") or ""), is_sub, is_member)
        elif cmd == "fard":
            result = handle_fard(username, is_sub, is_member)
        elif cmd in ("doublepoints", "2x"):
            result = handle_doublepoints(args, is_broadcaster)
        elif cmd == "summon":
            result = handle_summon(username, args)
        elif cmd == "topsummoner":
            result = handle_topsummoner()
        elif cmd == "sprint":
            result = handle_sprint(username)
        elif cmd in ("heat", "hot"):
            result = handle_heat(username)
        elif cmd in ("bestiary", "summonlevel"):
            result = handle_bestiary(username)
        elif cmd == "summonhall":
            result = handle_summonhall()
        elif cmd == "mysummons":
            result = handle_mysummons(username)
        elif cmd in ("help", "commands"):
            result = handle_help(args)
        elif cmd in ("economy", "reminder"):
            result = handle_economy()
        elif cmd in SPEND_COMMANDS or cmd in COMMAND_ALIASES:
            result = handle_spend(cmd, args, username)
        elif cmd in STREAM_INFO_COMMANDS:
            result = handle_stream_info(cmd)
        else:
            result = ChatResult(ok=False, message=chat_messages.unknown_command(cmd))
    finally:
        if dedupe_key and not deduped:
            _end_spend_dedupe(dedupe_key)

    if first_words_bonus and result.ok:
        result.earned = (result.earned or 0) + first_words_bonus
        extra = dict(result.extra or {})
        extra["first_words_bonus"] = first_words_bonus
        result.extra = extra
    _audit_chat_command(body, result, deduped=False)
    return result


# ---------------------------------------------------------------------------
# Installed Client Account Linking (!link <code>) & Play Along Rewards
# ---------------------------------------------------------------------------


def _load_linked_clients_unlocked() -> dict:
    if not os.path.exists(LINKED_CLIENTS_FILE):
        return {"tokens": {}}
    try:
        with open(LINKED_CLIENTS_FILE, encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict) and isinstance(data.get("tokens"), dict):
            return data
    except (json.JSONDecodeError, OSError):
        pass
    return {"tokens": {}}


def _save_linked_clients_unlocked(data: dict) -> None:
    tmp = LINKED_CLIENTS_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    os.replace(tmp, LINKED_CLIENTS_FILE)


def get_linked_client(token: str) -> Optional[dict]:
    tok = (token or "").strip()
    if not tok:
        return None
    with _link_lock:
        data = _load_linked_clients_unlocked()
        entry = (data.get("tokens") or {}).get(tok)
        return dict(entry) if isinstance(entry, dict) else None


def list_linked_clients() -> list[dict]:
    with _link_lock:
        data = _load_linked_clients_unlocked()
        tokens = data.get("tokens") or {}
        out = []
        for tok, rec in tokens.items():
            if not isinstance(rec, dict):
                continue
            item = dict(rec)
            item["token_prefix"] = str(tok)[:8]
            out.append(item)
        out.sort(key=lambda r: int(r.get("linked_at") or 0), reverse=True)
        return out


def unlink_client(token_or_username: str) -> int:
    target = (token_or_username or "").strip()
    if not target:
        return 0
    low = target.lower()
    removed = 0
    with _link_lock:
        data = _load_linked_clients_unlocked()
        tokens = data.get("tokens") or {}
        for tok in list(tokens.keys()):
            rec = tokens.get(tok) or {}
            uname = str(rec.get("username") or "").strip().lower()
            if tok == target or (uname and uname == low):
                del tokens[tok]
                removed += 1
        if removed:
            data["tokens"] = tokens
            _save_linked_clients_unlocked(data)
    return removed


def create_link_code(token: str) -> dict:
    tok = (token or "").strip()
    if not tok or len(tok) > 64:
        return {"ok": False, "error": "Invalid client token"}
    now = int(time.time())
    with _link_lock:
        # Prune expired codes
        for k in list(_pending_link_codes.keys()):
            if int(_pending_link_codes[k].get("expires") or 0) <= now:
                del _pending_link_codes[k]
        # Reuse existing valid code for same token if present
        for code, info in _pending_link_codes.items():
            if info.get("token") == tok:
                info["expires"] = now + LINK_CODE_TTL_SEC
                tokens = _load_linked_clients_unlocked().get("tokens") or {}
                linked = dict(tokens[tok]) if isinstance(tokens.get(tok), dict) else None
                return {"ok": True, "code": code, "expires_in": LINK_CODE_TTL_SEC, "linked": linked}
        code = ""
        for _ in range(16):
            cand = "".join(secrets.choice(_LINK_CODE_ALPHABET) for _ in range(6))
            if cand not in _pending_link_codes:
                code = cand
                break
        if not code:
            return {"ok": False, "error": "Could not allocate link code"}
        _pending_link_codes[code] = {"token": tok, "expires": now + LINK_CODE_TTL_SEC}
        tokens = _load_linked_clients_unlocked().get("tokens") or {}
        linked = dict(tokens[tok]) if isinstance(tokens.get(tok), dict) else None
        return {"ok": True, "code": code, "expires_in": LINK_CODE_TTL_SEC, "linked": linked}


def handle_link(
    username: str,
    args: list[str],
    platform: str,
    is_sub: bool,
    is_member: bool,
) -> ChatResult:
    if not username:
        return ChatResult(ok=False, message=chat_messages.USAGE["link"])
    if not args:
        return ChatResult(ok=False, message=chat_messages.USAGE["link"])
    code = (args[0] or "").strip().upper()
    if not code:
        return ChatResult(ok=False, message=chat_messages.USAGE["link"])

    now = int(time.time())
    tok = None
    with _link_lock:
        for k in list(_pending_link_codes.keys()):
            if int(_pending_link_codes[k].get("expires") or 0) <= now:
                del _pending_link_codes[k]
        entry = _pending_link_codes.pop(code, None)
        if not entry or int(entry.get("expires") or 0) <= now:
            return ChatResult(
                ok=False,
                message=f"@{username} Invalid or expired link code. Click 'Link Stream Account' in your game client for a fresh code.",
                extra={"command": "link", "reason": "invalid_code"},
            )
        tok = str(entry.get("token") or "").strip()
        if not tok:
            return ChatResult(ok=False, message=f"@{username} Invalid link request.", extra={"command": "link"})

        data = _load_linked_clients_unlocked()
        tokens = data.setdefault("tokens", {})
        user_low = username.strip().lower()
        for existing_tok in list(tokens.keys()):
            rec = tokens.get(existing_tok) or {}
            if str(rec.get("username") or "").strip().lower() == user_low and existing_tok != tok:
                del tokens[existing_tok]
        tokens[tok] = {
            "username": username.strip(),
            "platform": (platform or "twitch").strip().lower(),
            "is_sub": bool(is_sub),
            "is_member": bool(is_member),
            "linked_at": now,
        }
        _save_linked_clients_unlocked(data)

    total = 0
    try:
        with points_lock():
            pts_data = read_points()
            pts, _, donation_pts, _ = _get_user_data(pts_data, username.strip().lower())
            total = effective_total(pts, donation_pts)
    except TimeoutError:
        pass

    return ChatResult(
        ok=True,
        message=f"@{username} linked to SPD QoL game client! Balance: {total} pts.",
        pts=total,
        extra={"command": "link", "username": username.strip()},
    )


_MILESTONE_MAP = {
    "boss_5": ("Goo", "boss_goo_pts", "chat"),
    "boss_10": ("Tengu", "boss_tengu_pts", "chat"),
    "boss_15": ("DM-300", "boss_dm300_pts", "chat"),
    "boss_20": ("Dwarf King", "boss_king_pts", "chat"),
    "boss_25": ("Yog-Dzewa", "boss_yog_pts", "chat"),
    "victory": ("Amulet Victory", "victory_donor_pts", "donor"),
}


def award_play_along_milestone(
    session_id: str,
    token: str,
    fallback_username: str,
    milestone: str,
) -> dict:
    cfg = get_config()
    rewards = sanitize_play_along_rewards(cfg.get("play_along_rewards"))
    if not rewards.get("enabled", True):
        return {"ok": False, "reason": "disabled"}

    ms_key = (milestone or "").strip().lower()
    spec = _MILESTONE_MAP.get(ms_key)
    if not spec:
        return {"ok": False, "reason": "unknown_milestone"}
    label, cfg_key, kind = spec

    linked = get_linked_client(token)
    if not linked or not linked.get("username"):
        return {
            "ok": False,
            "reason": "unlinked",
            "message": "Link your stream account (!link) to earn Play Along stream points!",
        }

    username = str(linked["username"]).strip()
    user_low = username.lower()
    sid = (session_id or "").strip() or "default"
    claim_key = f"{sid}|{user_low}|{ms_key}"

    with _link_lock:
        state = _load_session_state()
        claims = state.setdefault("play_along_claims", {})
        if claim_key in claims:
            return {"ok": False, "reason": "already_claimed"}

        amount = max(0, int(rewards.get(cfg_key, 0)))
        if amount <= 0:
            claims[claim_key] = int(time.time())
            _save_session_state(state)
            return {"ok": False, "reason": "zero_reward"}

        awarded = 0
        new_total = 0
        if kind == "donor":
            ok, _donor_tot, added = grant_flat_donor_points(username, amount)
            if not ok:
                return {"ok": False, "reason": "points_busy"}
            awarded = added
            try:
                with points_lock():
                    pts_data = read_points()
                    pts, _, donation_pts, _ = _get_user_data(pts_data, user_low)
                    new_total = effective_total(pts, donation_pts)
            except TimeoutError:
                new_total = _donor_tot
        else:
            cap = None if rewards.get("boss_ignore_chat_cap") else chat_cap_for_user(username, cfg)
            try:
                with points_lock():
                    pts_data = read_points()
                    actual, _nudge, new_total = _apply_capped_chat_award(
                        pts_data, user_low, username, amount, cap,
                    )
                    write_points(pts_data)
                    awarded = actual
            except TimeoutError:
                return {"ok": False, "reason": "points_busy"}

        claims[claim_key] = int(time.time())
        _save_session_state(state)

    if awarded <= 0:
        return {"ok": False, "reason": "chat_cap_reached", "label": label, "pts": new_total}

    return {
        "ok": True,
        "username": username,
        "label": label,
        "awarded": awarded,
        "kind": kind,
        "pts": new_total,
    }
