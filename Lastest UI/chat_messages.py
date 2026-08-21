"""
Server-side chat message catalog for the streaming system rework.

Ported in Phase 0 from:
  - docs/streamerbot-points-from-scratch.md (success messages)
  - Lastest UI/points_command.py (errors, usage)
  - docs/streamerbot-fard-rework-apply.md, streamerbot-summon-march-apply.md (meta)
  - docs/Chat Command Economy v1.md (v1.1 templates — used in Phase 6)

chat_command.py formats final `message` strings for API responses.
"""
from contextvars import ContextVar
import re


# YouTube live chat rejects bot replies over this length (Twitch allows 500).
YOUTUBE_CHAT_LIMIT = 150
_platform_var: ContextVar[str] = ContextVar("chat_reply_platform", default="")
_URL_RE = re.compile(r"https?://\S+")


def set_reply_platform(platform: str | None) -> None:
    """Record the current chat platform so formatters can keep YouTube under 150 chars."""
    _platform_var.set((platform or "").strip())


def current_reply_platform() -> str:
    return _platform_var.get() or ""


def is_youtube_platform(platform: str | None = None) -> bool:
    p = (platform if platform is not None else current_reply_platform()).strip().lower()
    return "youtube" in p or p == "yt"


def _ellipsize(text: str, limit: int) -> str:
    if len(text) <= limit:
        return text
    if limit <= 3:
        return text[:limit]
    cut = text[: limit - 3]
    sp = cut.rfind(" ")
    if sp >= max(8, limit // 2):
        cut = cut[:sp]
    return cut.rstrip(" .,;:-") + "..."


def clamp_youtube_chat(text: str, limit: int = YOUTUBE_CHAT_LIMIT) -> str:
    """Fit a chat reply into YouTube's limit, preferring to keep a URL if one is present."""
    if not text:
        return text
    text = " ".join(text.split())
    if len(text) <= limit:
        return text
    match = _URL_RE.search(text)
    if match:
        url = match.group(0).rstrip(").,;")
        if len(url) <= limit:
            head = (text[: match.start()] + " " + text[match.end() :]).strip()
            room = limit - len(url) - 1
            if room >= 4 and head:
                candidate = f"{_ellipsize(head, room)} {url}".strip()
                if len(candidate) <= limit:
                    return candidate
            return url
        return url[:limit]
    return _ellipsize(text, limit)


def clamp_for_platform(message: str | None, platform: str | None = "") -> str | None:
    if message is None:
        return None
    plat = platform or current_reply_platform()
    if is_youtube_platform(plat):
        return clamp_youtube_chat(message)
    return message


def display_name(username: str) -> str:
    """Prefix @ for chat display when missing."""
    name = (username or "").strip()
    if not name:
        return ""
    return name if name.startswith("@") else f"@{name}"


# --- Spend success (streamerbot-points-from-scratch.md) ---

def spend_success(command: str, user: str, pts: int, extra: str = "", detail: str = "") -> str:
    """Format a spend-command success line. `extra` is command-specific detail."""
    u = display_name(user) or user
    tail = f" ({pts} left)" if is_youtube_platform() else f" You have {pts} points left."
    templates = {
        "spawn": f"{u} spawned a {extra}!{tail}",
        "champion": f"{u} spawned a champion {extra}!{tail}",
        "gold": f"{u} dropped {extra} gold!{tail}",
        "curse": f"{u} cursed your {extra}!{tail}",
        "curse_temporary": f"{u} temporarily cursed your {extra}! ({detail} turns){tail}",
        "gas": f"{u} spewed {extra}!{tail}",
        "scroll": f"{u} used a random scroll: {extra}!{tail}",
        "row": f"{u} triggered Ring of Wealth loot: {extra}!{tail}",
        "trap": f"{u} placed a {extra} nearby!{tail}",
        "bomb": f"{u} armed a {extra} nearby!{tail}",
        "transmute": f"{u} transmuted an item into {extra}!{tail}",
        "bee": f"{u} summoned a bee to help you!{tail}",
        "ward": f"{u} summoned a ward to help you!{tail}",
        "buff": f"{u} gave you {extra}!{tail}",
        "debuff": f"{u} afflicted you with {extra}!{tail}",
        "wand": f"{u} triggered a cursed wand effect: {extra}!{tail}",
        "corruptally": f"{u} summoned a corrupted {extra} to fight for you!{tail}",
        "corrupt_ally": f"{u} summoned a corrupted {extra} to fight for you!{tail}",
        "heal": f"{u} healed you!{tail}",
        "cleanse": f"{u} cleansed {extra}!{tail}",
        "dew": f"{u} dropped a dewdrop!{tail}",
        "plant": f"{u} planted a {extra} nearby!{tail}",
        "hex": f"{u} hexed you!{tail}",
        "degrade": f"{u} degraded you!{tail}",
        "sabotage": f"{u} sabotaged {extra}!{tail}",
    }
    fn = templates.get(command)
    if fn:
        return fn
    return f"{u} used !{command}!{tail}"


def spend_bestiary_xp(xp: int) -> str:
    """Suffix for paid-command success when co-op Bestiary bar XP was granted."""
    n = int(xp or 0)
    if n <= 0:
        return ""
    if is_youtube_platform():
        return f" (+{n} XP)"
    return f" (+{n} Bestiary XP)"


# --- Spend / system errors (points_command.py) ---

SPEND_DISABLED = "Spending is currently disabled by the streamer."
POINTS_BUSY = "Points file busy. Please try again in a moment."
COMMAND_IN_PROGRESS = "Another command is in progress. Please try again in a moment."


def not_enough_points(user: str, cost: int, total: int, detail: str = "") -> str:
    u = display_name(user)
    need = f"Need {cost}"
    if detail:
        need += f" {detail}"
    prefix = f"{u}, " if u else ""
    return f"{prefix}Not enough points! {need}, you have {total}."


USAGE = {
    "help": "Usage: !help or !help <command> - bare !help links to the full command list on GitHub.",
    "points": "Usage: !points - show your chat points, donor points, and Bestiary sprint/heat XP.",
    "economy": "Usage: !economy / !reminder - live chat-cap / earn / bank rules from current config.",
    "bank": "Usage: !bank / !bank all, or !bank <amount> - convert chat pts to donor pts at 10%.",
    "toppoints": "Usage: !toppoints / !leaderboard - top 3 by donor points.",
    "givepoints": "Usage: !givepoints <amount> <target> (example: !givepoints 50 @bob)",
    "fard": "Usage: !fard - once per stream: OBS flash + sound; extends global 2x (+3 min, +6 for subs/members).",
    "summon": "Usage: !summon [monster] - overlay march (60s CD). Unlocks more zones via co-op XP.",
    "bestiary": "Usage: !bestiary / !summonlevel - co-op bar, unlocked mobs, your sprint/heat XP.",
    "topsummoner": "Usage: !topsummoner - sprint leader this Bestiary level (not heat).",
    "sprint": "Usage: !sprint - sprint leader this Bestiary level, plus your rank/XP.",
    "heat": "Usage: !heat / !hot - 15-min heat leader (personal 2x on point gains).",
    "summonhall": "Usage: !summonhall - Hall of Fame: sprint winners for completed zones this stream.",
    "mysummons": "Usage: !mysummons - your summon count this stream + sprint XP + heat XP.",
    "kesha": "Usage: !kesha - OBS overlay flash + sound (~2s); 60s global / 10 min per-user cooldown.",
    "mimic": "Usage: !mimic / !tooth - mimic sound if Mimic Tooth trinket is in the run.",
    "challenge": "Usage: !challenge / !challenges - reply with active challenges.",
    "seed": "Usage: !seed - reply with current dungeon seed.",
    "spawn": "Usage: !spawn <monster> (e.g. !spawn rat)",
    "champion": "Usage: !champion <monster> (e.g. !champion rat). Costs 2x zone-adjusted spawn cost.",
    "gold": "Usage: !gold <amount> (e.g. !gold 10). Amount must be 1-100.",
    "curse": "Usage: !curse (curses a random equipped item)",
    "gas": "Usage: !gas (spawns random gas near you)",
    "scroll": "Usage: !scroll (uses a random scroll like +10 Unstable Spellbook)",
    "row": "Usage: !row (Ring of Wealth bonus loot near you, always at least one item)",
    "trap": "Usage: !trap (places a random visible trap near you)",
    "bomb": "Usage: !bomb (drops a weighted random lit bomb near you)",
    "transmute": "Usage: !transmute (transmutes a random transmutable item from bag or equipped)",
    "bee": "Usage: !bee (summons an allied bee for 150 turns, 75 pts)",
    "ward": "Usage: !ward (summons a ward, 30 pts, scales with depth)",
    "buff": "Usage: !buff (gives a random buff)",
    "debuff": "Usage: !debuff (gives a random debuff)",
    "wand": "Usage: !wand (weighted random effect) or python points_command.py wand <username>",
    "heal": "Usage: !heal (heals hero ~15% HP)",
    "cleanse": "Usage: !cleanse (removes one random debuff)",
    "dew": "Usage: !dew (drops a dewdrop near hero)",
    "plant": "Usage: !plant (plants a random plant near hero; fails if Barren Land enabled)",
    "corruptally": "Usage: !corruptally (summons a corrupted ally from the current biome)",
    "hex": "Usage: !hex (applies Hex debuff)",
    "degrade": "Usage: !degrade (applies Degrade debuff)",
    "sabotage": "Usage: !sabotage (removes one random positive buff)",
    "doublepoints": "Usage: !doublepoints <minutes> (e.g. !doublepoints 5 for 5 minutes, max 120)",
}

# Aliases -> USAGE key for !help <command>
HELP_ALIASES = {
    "commands": "help",
    "reminder": "economy",
    "2x": "doublepoints",
    "hot": "heat",
    "summonlevel": "bestiary",
    "leaderboard": "toppoints",
    "challenges": "challenge",
    "tooth": "mimic",
    "corrupt_ally": "corruptally",
    "balance": "points",
    "transfer": "givepoints",
}


def unknown_monster(name: str) -> str:
    return f"Unknown monster: {name}"


def unknown_command(name: str) -> str:
    return f"Unknown command !{name}. Type !help for the full command list or !points for your balance."


def help_link(url: str, economy_line: str = "") -> str:
    link = (url or "").strip()
    if is_youtube_platform():
        # URL first so YouTube's 150-char cap cannot drop the command list.
        msg = f"Commands: {link}  !help <cmd> for usage." if link else "Commands in the video description. !help <cmd> for usage."
        return clamp_youtube_chat(msg)
    listed = f"Full command list & prices: {link}" if link else "Full command list: type !help <command>."
    eco = (economy_line or "").strip()
    return f"{eco} {listed}" if eco else listed


def economy_reminder(
    chat_cap: int,
    pts_per_message: int = 2,
    chat_cooldown_sec: int = 20,
    bank_ratio_manual: float = 0.10,
) -> str:
    """Viewer-facing economy blurb from live points_config (chat cap, earn, bank)."""
    cap = max(1, int(chat_cap))
    pts = max(1, int(pts_per_message))
    cd = max(0, int(chat_cooldown_sec))
    pct = int(round(max(0.0, float(bank_ratio_manual)) * 100))
    if is_youtube_platform():
        return clamp_youtube_chat(
            f"Earn {cap} chat pts/stream ({pts}/msg, {cd}s CD). "
            f"!bank {pct}% to donor pts. Chat resets; donor pts stay."
        )
    return (
        f"Earn up to {cap} chat points per stream ({pts} pts/msg, {cd}s cooldown). "
        f"!bank saves {pct}% as permanent donor pts. Chat resets at stream end; donor pts never expire."
    )


def command_help(name: str) -> str | None:
    """Return the definition/usage for a command name, or None if unknown."""
    key = (name or "").strip().lstrip("!").lower()
    if not key:
        return None
    key = HELP_ALIASES.get(key, key)
    return USAGE.get(key)


def help_unknown(name: str) -> str:
    shown = (name or "").strip().lstrip("!") or name
    return f"Unknown command !{shown}. Type !help for the full command list."


# --- Stream info (handled by separate Streamer.bot Command actions) ---

def game_seed(seed: str) -> str:
    return f"Current seed: {seed}"


def active_challenges(text: str) -> str:
    msg = f"Current Active Challenges: {text}"
    return clamp_youtube_chat(msg) if is_youtube_platform() else msg


# --- Query (current economy — Phase 1–5) ---

def points_balance(user: str, total: int) -> str:
    u = display_name(user) or user
    return f"{u}, you have {total} points. Spawn costs vary by monster (5-80)."


def toppoints_empty() -> str:
    return "No donor points yet. !bank saves chat pts, or donate to rank here!"


def toppoints_leaderboard(entries: list[tuple[str, int]]) -> str:
    """entries: [(username, donor_pts), ...] sorted desc."""
    if not entries:
        return toppoints_empty()
    parts = []
    for i, (name, pts) in enumerate(entries[:3], 1):
        parts.append(f"{i}. {name} ({pts} donor pts)")
    line = " | ".join(parts)
    return clamp_youtube_chat(line) if is_youtube_platform() else line


# --- Query (economy v1.1 — Phase 6) ---

def points_balance_v11(
    user: str,
    chat_pts: int,
    chat_cap: int,
    donor_pts: int,
    sprint_xp: int = 0,
    heat_xp: int = 0,
) -> str:
    u = display_name(user) or user
    if is_youtube_platform():
        base = (
            f"{u} Chat {chat_pts}/{chat_cap} | Donor {donor_pts}"
            f" | Sprint {sprint_xp} / heat {heat_xp} XP"
        )
        cap_note = " Cap! !bank"
        if chat_pts >= chat_cap and len(base) + len(cap_note) <= YOUTUBE_CHAT_LIMIT:
            base += cap_note
        return clamp_youtube_chat(base)
    base = (
        f"{u} - Chat points: {chat_pts}/{chat_cap} | Donor points: {donor_pts}"
        f" | Bestiary: sprint {sprint_xp} XP, heat {heat_xp} XP"
    )
    if chat_pts >= chat_cap:
        base += " - Cap reached! Use !bank to save points permanently."
    return base


def chat_cap_nudge(user: str, cap: int) -> str:
    u = display_name(user) or user
    return (
        f"{u} - Chat cap reached ({cap}/{cap})! "
        f"Use !bank to save points permanently, or spend them on a command."
    )


def bank_preview(user: str, chat_pts: int, donor_gain: int, ratio_pct: int) -> str:
    u = display_name(user) or user
    return (
        f"{u} - Bankable: {chat_pts} chat pts -> {donor_gain} donor pts ({ratio_pct}%). "
        f"Use !bank all or !bank <amount>."
    )


def bank_success(user: str, chat_amount: int, donor_gain: int, donor_total: int) -> str:
    u = display_name(user) or user
    return (
        f"{u} - Banked {chat_amount} chat -> {donor_gain} donor pts. "
        f"Donor balance: {donor_total} (saved forever)."
    )


def bank_excess(user: str, chat_pts: int) -> str:
    u = display_name(user) or user
    return f"{u} - You only have {chat_pts} chat pts to bank."


def bank_invalid_amount(user: str) -> str:
    u = display_name(user) or user
    return f"{u} - Invalid bank amount. Use !bank, !bank all, or !bank <amount> (integer 1+)."


def bank_no_chat(user: str) -> str:
    u = display_name(user) or user
    return f"{u} - No chat points to bank. Earn or spend chat pts first."


def channel_points_convert(user: str, channel_points: int, donor_gain: int, donor_total: int) -> str:
    u = display_name(user) or user
    return (
        f"{u} - Converted {channel_points} Channel Points -> +{donor_gain} donor pts. "
        f"Donor balance: {donor_total} (saved forever)."
    )


# --- Meta: !doublepoints ---

def doublepoints_active(minutes: int) -> str:
    return f"Double points active for {minutes} minutes! Chat to earn 2x points."


DOUBLEPOINTS_BROADCASTER_ONLY = "Only the streamer can use !doublepoints."


# --- Meta: !fard ---

def fard_success(user: str, extend_minutes: int) -> str:
    u = display_name(user) or user
    return f"{u} used their fard! +{extend_minutes} min of 2x for everyone."


FARD_ALREADY_USED = "{user}, you already used your fard this stream."


def fard_already_used(user: str) -> str:
    u = display_name(user) or user
    return f"{u}, you already used your fard this stream."


# --- Meta: !summon / !topsummoner / !mysummons / bestiary ---

def summon_success(
    user: str,
    monster: str,
    xp: int = 0,
    soft_floor: bool = False,
    xp_mult: float = 1.0,
) -> str:
    u = display_name(user) or user
    if xp > 0:
        msg = f"{u} summoned a {monster} across the screen! (+{xp} Bestiary XP"
        if soft_floor and xp_mult > 1.0:
            # e.g. catch-up XP multiplier x1.25
            mult_txt = f"{xp_mult:.2f}".rstrip("0").rstrip(".")
            msg += f", catch-up XP multiplier x{mult_txt}"
        msg += ")"
        return msg
    return f"{u} summoned a {monster} across the screen!"


SUMMON_NO_LEADER = "No summons yet this stream."


def bestiary_status(
    level: int,
    zone: str,
    bar_xp: int,
    bar_threshold: int,
    unlocked: list,
    user: str = "",
    sprint_xp: int = 0,
    heat_xp: int = 0,
) -> str:
    if bar_threshold > 0:
        bar = f"{bar_xp}/{bar_threshold} XP"
    else:
        bar = "MAX"
    if is_youtube_platform():
        head = f"Bestiary Lv {level} {zone} {bar}"
        personal = ""
        if user:
            u = display_name(user) or user
            personal = f" | {u} {sprint_xp}/{heat_xp} XP"
        names: list[str] = []
        extra = ""
        unlocked = list(unlocked or [])
        for i, name in enumerate(unlocked):
            nxt = ", ".join(names + [str(name)])
            remain = len(unlocked) - (i + 1)
            extra_try = f" +{remain}" if remain > 0 else ""
            body = f". {nxt}{extra_try}"
            if len(head) + len(body) + len(personal) > YOUTUBE_CHAT_LIMIT:
                remain_now = len(unlocked) - len(names)
                extra = f" +{remain_now}" if names and remain_now > 0 else ""
                break
            names.append(str(name))
            extra = extra_try
        body = f". {', '.join(names)}{extra}" if names else ""
        return clamp_youtube_chat(head + body + personal)
    sample = ", ".join(unlocked[:8])
    more = f" (+{len(unlocked) - 8} more)" if len(unlocked) > 8 else ""
    # ASCII only - Streamer.bot ParseChatResponse strips \ from JSON \uXXXX escapes.
    msg = f"Bestiary Lv {level} - {zone} - {bar}. Unlocked: {sample}{more}"
    if user:
        u = display_name(user) or user
        msg += f" | {u}: sprint {sprint_xp} XP, heat {heat_xp} XP"
    return msg


def sprint_leader_line(user: str, xp: int, gap: int) -> str:
    yt = is_youtube_platform()
    if not user:
        msg = (
            "No sprint leader yet - !summon to lead!"
            if yt
            else (
                "No eligible sprint leader this Bestiary level yet - !summon to take the lead! "
                "(Past sprint winners can't crown again until next stream.)"
            )
        )
        return clamp_youtube_chat(msg) if yt else msg
    u = display_name(user) or user
    if gap > 0:
        core = f"Sprint leader: {u} with {xp} XP (leads by {gap})."
    else:
        core = f"Sprint leader: {u} with {xp} XP."
    if yt:
        return clamp_youtube_chat(core)
    return f"{core} Resets on level-up; winners can't crown again this stream."


def sprint_standing_line(
    leader: str,
    leader_xp: int,
    gap: int,
    user: str = "",
    your_xp: int = 0,
    rank: int | None = None,
    crowned: bool = False,
) -> str:
    """Leader + caller's place for !sprint."""
    yt = is_youtube_platform()
    if not leader:
        base = (
            "No sprint leader yet - !summon to lead!"
            if yt
            else (
                "No eligible sprint leader this Bestiary level yet - !summon to take the lead! "
                "(Past sprint winners can't crown again until next stream.)"
            )
        )
    else:
        u_lead = display_name(leader) or leader
        if gap > 0:
            base = f"Sprint leader: {u_lead} with {leader_xp} XP (leads by {gap})."
        else:
            base = f"Sprint leader: {u_lead} with {leader_xp} XP."
    if not user:
        msg = base if (yt or not leader) else f"{base} Resets on level-up."
        return clamp_youtube_chat(msg) if yt else msg
    u = display_name(user) or user
    if crowned:
        msg = (
            f"{base} {u}: already crowned this stream."
            if yt
            else f"{base} {u}: already crowned this stream (can't win again)."
        )
    elif rank == 1:
        msg = f"{base} {u}: you're #1!"
    elif rank is not None:
        behind = max(0, int(leader_xp) - int(your_xp))
        msg = f"{base} {u}: #{rank} with {your_xp} XP ({behind} behind)."
    else:
        msg = f"{base} {u}: not on the board - !summon to race!"
    return clamp_youtube_chat(msg) if yt else msg


def heat_leader_line(
    user: str,
    xp: int,
    window_sec: int,
    your_xp: int = 0,
    requester: str = "",
) -> str:
    mins = max(1, int(window_sec) // 60)
    req = display_name(requester) if requester else ""
    yours = f" {req} has {your_xp} heat XP." if req else ""
    if not user:
        empty = f"No heat leader in the last {mins} min - !summon to claim personal 2x!"
        msg = f"{empty}{yours}" if yours else empty
        return clamp_youtube_chat(msg) if is_youtube_platform() else msg
    u = display_name(user) or user
    msg = f"Heat leader ({mins}m): {u} with {xp} XP - personal 2x active.{yours}"
    return clamp_youtube_chat(msg) if is_youtube_platform() else msg


def summon_hall_line(hall: list) -> str:
    if not hall:
        msg = "Hall of Fame is empty - fill the Bestiary bar to crown a sprint winner!"
        return clamp_youtube_chat(msg) if is_youtube_platform() else msg
    parts = []
    for entry in hall[-5:]:
        zone = entry.get("zone") or f"Lv{entry.get('level', '?')}"
        user = entry.get("user") or "(none)"
        xp = entry.get("xp", 0)
        parts.append(f"{zone}: {user} ({xp} XP)")
    if not is_youtube_platform():
        return "Summon Hall: " + " | ".join(parts)
    kept: list[str] = []
    for part in reversed(parts):
        trial = [part] + kept
        line = "Hall: " + " | ".join(trial)
        if kept and len(line) > YOUTUBE_CHAT_LIMIT:
            break
        kept = trial
    return clamp_youtube_chat("Hall: " + " | ".join(kept) if kept else "Hall of Fame is empty.")


def bestiary_level_up(winner: str, from_zone: str, to_zone: str, donor: int) -> str:
    w = display_name(winner) or winner or "Nobody"
    if is_youtube_platform():
        if winner and donor > 0:
            msg = f"Bestiary up! {from_zone} -> {to_zone}. Sprint winner {w} +{donor} donor pts."
        elif not winner:
            msg = f"Bestiary up! {from_zone} -> {to_zone}. No eligible sprint winner."
        else:
            msg = f"Bestiary up! {from_zone} -> {to_zone}."
        return clamp_youtube_chat(msg)
    if winner and donor > 0:
        return (
            f"Bestiary level-up! {from_zone} -> {to_zone}. "
            f"Sprint winner {w} earns {donor} donor points! "
            f"(Can't win another sprint crown this stream.)"
        )
    if not winner:
        return (
            f"Bestiary level-up! {from_zone} -> {to_zone}. "
            "No eligible sprint winner (prior winners are locked out until next stream)."
        )
    return f"Bestiary level-up! {from_zone} -> {to_zone}."


def bestiary_halls_loop(winner: str, winner_xp: int = 0) -> str:
    w = display_name(winner) or winner or "Nobody"
    if is_youtube_platform():
        if winner:
            msg = f"Halls sprint! {w} takes the badge."
        else:
            msg = "Halls sprint loop! No eligible winner."
        return clamp_youtube_chat(msg)
    if winner:
        xp_bit = f" ({int(winner_xp)} XP)" if int(winner_xp or 0) > 0 else ""
        return (
            f"Halls sprint loop! {w} takes the Halls badge{xp_bit}. "
            f"(Can't win another sprint crown this stream.)"
        )
    return (
        "Halls sprint loop! No eligible sprint winner "
        "(prior winners are locked out until next stream)."
    )


def bestiary_progress_line(bestiary: dict) -> str:
    """Level-up or Halls sprint-loop chat line, or empty."""
    if not isinstance(bestiary, dict):
        return ""
    lu = bestiary.get("level_up") or {}
    if not isinstance(lu, dict):
        lu = {}
    if bestiary.get("leveled_up"):
        return bestiary_level_up(
            lu.get("winner") or "",
            lu.get("from_zone") or "",
            lu.get("to_zone") or "",
            int(lu.get("donor_reward") or 0),
        )
    if bestiary.get("halls_looped") or str(lu.get("reason") or "") == "halls_loop":
        return bestiary_halls_loop(
            lu.get("winner") or "",
            int(lu.get("winner_xp") or 0),
        )
    return ""


def shatter_event_line(event: dict) -> str:
    """Announce a Shatter Event free window from a summon_bestiary event dict."""
    if not isinstance(event, dict):
        return ""
    monster = str(event.get("monster") or "").strip().lower() or "?"
    duration = int(event.get("duration_sec") or 60)
    sidecar = event.get("sidecar_label") or event.get("sidecar_cost_key")
    reason = str(event.get("reason") or "pip")
    prefix = "Shatter Event!"
    if reason == "level_up":
        prefix = "Shatter Event (level-up)!"
    elif reason == "halls_loop":
        prefix = "Shatter Event (Halls)!"
    if sidecar:
        return f"{prefix} !spawn {monster} + {sidecar} FREE {duration}s"
    return f"{prefix} !spawn {monster} FREE {duration}s"


def shatter_events_suffix(events) -> str:
    if not events:
        return ""
    parts = []
    for ev in events:
        line = shatter_event_line(ev if isinstance(ev, dict) else {})
        if line:
            parts.append(line)
    if not parts:
        return ""
    return " " + " ".join(parts)


def mysummons_line(user: str, count: int, sprint_xp: int, heat_xp: int) -> str:
    u = display_name(user) or user
    return (
        f"{u}, you have summoned {count} time(s) this stream "
        f"(sprint XP this level: {sprint_xp}, heat XP: {heat_xp})."
    )


def givepoints_success(
    from_user: str, amount: int, to_user: str, from_remaining: int, capped: bool = False
) -> str:
    f = display_name(from_user) or from_user
    t = display_name(to_user) or to_user
    cap_note = " (chat cap)" if capped else ""
    return f"{f} gave {amount} points to {t}{cap_note}. {f} now has {from_remaining}."


def givepoints_at_cap(to_user: str, cap: int) -> str:
    t = display_name(to_user) or to_user
    return f"{t} is at the chat cap ({cap}/{cap}) and can't receive more."
