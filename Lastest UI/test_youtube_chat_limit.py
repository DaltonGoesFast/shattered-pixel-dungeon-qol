"""YouTube live chat cap: every viewer-facing command reply must fit in 150 chars."""
from __future__ import annotations

import sys

import chat_messages as m
from chat_command import COMMANDS_DOC_URL, _limit_chat_result
from points_command import ChatResult

LIMIT = m.YOUTUBE_CHAT_LIMIT
USER = "TypicalYouTubeName"
UNLOCKED = [
    "rat", "gnoll", "snake", "crab", "slime", "swarm", "thief", "skeleton", "bat", "brute",
]


def _assert_len(label: str, text: str | None) -> None:
    if text is None:
        return
    n = len(text)
    assert n <= LIMIT, f"{label} is {n} chars (> {LIMIT}): {text!r}"


def test_usage_catalog() -> None:
    for key, text in m.USAGE.items():
        _assert_len(f"USAGE.{key}", text)


def test_youtube_help_keeps_url() -> None:
    m.set_reply_platform("youtube")
    try:
        eco = m.economy_reminder(300, 2, 20, 0.10)
        msg = m.help_link(COMMANDS_DOC_URL, eco)
        _assert_len("help_link youtube", msg)
        assert COMMANDS_DOC_URL in msg
        assert "Earn up to" not in msg
        _assert_len("economy youtube", eco)
    finally:
        m.set_reply_platform("")


def test_twitch_help_can_be_long() -> None:
    m.set_reply_platform("twitch")
    try:
        eco = m.economy_reminder(300, 2, 20, 0.10)
        msg = m.help_link(COMMANDS_DOC_URL, eco)
        assert len(msg) > LIMIT
        assert COMMANDS_DOC_URL in msg
        assert "Earn up to" in eco
    finally:
        m.set_reply_platform("")


def test_youtube_query_replies() -> None:
    m.set_reply_platform("youtube")
    try:
        _assert_len("points cap", m.points_balance_v11(USER, 300, 300, 1234, 88, 42))
        _assert_len(
            "points longname cap",
            m.points_balance_v11("x" * 30, 300, 300, 12345, 888, 420),
        )
        _assert_len(
            "bestiary",
            m.bestiary_status(3, "Caves", 1200, 2000, UNLOCKED, USER, 40, 12),
        )
        _assert_len("sprint empty", m.sprint_standing_line("", 0, 0, USER, 0, None, False))
        _assert_len("sprint leader", m.sprint_leader_line(USER, 120, 15))
        _assert_len(
            "sprint crowned",
            m.sprint_standing_line(USER, 120, 15, USER, 80, 2, True),
        )
        _assert_len("heat", m.heat_leader_line(USER, 40, 900, 12, USER))
        _assert_len(
            "hall",
            m.summon_hall_line(
                [
                    {"zone": "Sewers", "user": USER, "xp": 80},
                    {"zone": "Prison", "user": USER, "xp": 120},
                    {"zone": "Caves", "user": USER, "xp": 200},
                    {"zone": "City", "user": USER, "xp": 300},
                    {"zone": "Halls", "user": USER, "xp": 400},
                ]
            ),
        )
        _assert_len("level-up", m.bestiary_level_up(USER, "Sewers", "Prison", 100))
        _assert_len("halls-loop", m.bestiary_halls_loop(USER, 400))
        _assert_len("halls-loop empty", m.bestiary_halls_loop("", 0))
        _assert_len(
            "toppoints",
            m.toppoints_leaderboard([(USER, 400), (USER, 300), ("alice", 200)]),
        )
    finally:
        m.set_reply_platform("")


def test_youtube_spend_plus_level_up_fits() -> None:
    m.set_reply_platform("youtube")
    try:
        msg = (
            m.spend_success("spawn", USER, 280, "scorpio")
            + m.spend_bestiary_xp(40)
            + " "
            + m.bestiary_level_up(USER, "Sewers", "Prison", 100)
        )
        _assert_len("spend+xp+level-up youtube", msg)
        assert "+100 donor" in msg
    finally:
        m.set_reply_platform("")


def test_dispatch_clamp_concatenated_spend() -> None:
    m.set_reply_platform("youtube")
    try:
        long_msg = (
            m.spend_success("spawn", USER, 280, "scorpio")
            + m.spend_bestiary_xp(40)
            + " "
            + m.bestiary_level_up(USER, "Sewers", "Prison", 100)
            + m.shatter_events_suffix(
                [{"monster": "rat", "duration_sec": 60, "sidecar_label": "!buff", "reason": "level_up"}]
            )
        )
        result = _limit_chat_result(ChatResult(ok=True, message=long_msg), "youtube")
        _assert_len("clamped spend+level-up", result.message)
    finally:
        m.set_reply_platform("")


def test_clamp_keeps_url() -> None:
    url = COMMANDS_DOC_URL
    padded = "Earn a very long economy blurb that would hide the github link. " + url
    out = m.clamp_youtube_chat(padded)
    assert url in out
    assert len(out) <= LIMIT


def main() -> int:
    tests = [
        test_usage_catalog,
        test_youtube_help_keeps_url,
        test_twitch_help_can_be_long,
        test_youtube_query_replies,
        test_youtube_spend_plus_level_up_fits,
        test_dispatch_clamp_concatenated_spend,
        test_clamp_keeps_url,
    ]
    failed = 0
    for fn in tests:
        try:
            fn()
            print(f"ok   {fn.__name__}")
        except Exception as e:
            failed += 1
            print(f"FAIL {fn.__name__}: {e}")
    if failed:
        print(f"{failed} test(s) failed")
        return 1
    print("all passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
