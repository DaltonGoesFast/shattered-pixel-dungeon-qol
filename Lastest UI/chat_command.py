"""
Chat command dispatcher for POST /api/chat-command.

Routes earn, spend, query, and meta commands. Spend delegates to points_command.run_points_command.
"""
import json
import os
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
    chat_earn_multiplier,
    chat_pts,
    effective_total,
    get_config,
    ignores_command_cooldowns,
    is_double_points_active,
    points_lock,
    read_points,
    run_points_command,
    write_points,
    _get_user_data,
)

SESSION_STATE_FILE = os.path.join(SCRIPT_DIR, "session_state.json")
GAME_SUMMARY_JSON = os.path.join(SCRIPT_DIR, "game_summary.json")
SUMMON_COUNTS_FILE = os.path.join(SCRIPT_DIR, "summon_session_counts.json")
CHAT_AUDIT_LOG = os.path.join(SCRIPT_DIR, "chat_command_audit.jsonl")
SUMMON_COOLDOWN_SEC = 60

# Rare Twitch/YouTube duplicate chat events can run R01 twice; collapse identical spends.
SPEND_DEDUPE_SEC = 2.0
_dedupe_lock = threading.Lock()
_spend_inflight: set[str] = set()
_spend_recent: dict[str, float] = {}

# Handled by separate Streamer.bot Command actions; R1 should skip POST (BuildChatCommandBody).
STREAM_INFO_COMMANDS = frozenset({
    "kesha", "mimic", "tooth", "seed", "challenge", "challenges",
})

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


def _track_member(state: dict, key: str, is_sub: bool, is_member: bool) -> None:
    if is_sub or is_member:
        state.setdefault("members", {})[key] = True


def _apply_capped_chat_award(
    data: dict,
    key: str,
    username: str,
    to_add: int,
    cap: int,
    update_last: Optional[int] = None,
) -> tuple[int, Optional[str], int]:
    """Apply chat earn with cap. Returns (earned, cap_nudge_message, new_total)."""
    pts, last, donation_pts, role = _get_user_data(data, key)
    cur_chat = chat_pts(pts, donation_pts)
    total = effective_total(pts, donation_pts)

    if to_add <= 0:
        return 0, None, total

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
    cap = int(cfg.get("chat_point_cap", 500))
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
    cap = int(cfg.get("chat_point_cap", 500))
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
    cap = int(cfg.get("chat_point_cap", 500))
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
    cap = int(cfg.get("chat_point_cap", 500))
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
    cap = int(cfg.get("chat_point_cap", 500))
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
    msg = chat_messages.summon_success(
        username, monster, xp, soft_floor=soft_floor, xp_mult=xp_mult
    )
    if not march_ok:
        msg += " (overlay server offline - XP counted, march not queued)"
    if bestiary.get("leveled_up") and bestiary.get("level_up"):
        lu = bestiary["level_up"]
        msg = (
            msg
            + " "
            + chat_messages.bestiary_level_up(
                lu.get("winner") or "",
                lu.get("from_zone") or "",
                lu.get("to_zone") or "",
                int(lu.get("donor_reward") or 0),
            )
        )

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


def handle_stream_info(cmd: str) -> ChatResult:
    """Silent ok for OBS/sound commands; chat replies for seed/challenges."""
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
        elif cmd == "bank":
            result = handle_bank(username, args)
        elif cmd in ("toppoints", "leaderboard"):
            result = handle_toppoints()
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
