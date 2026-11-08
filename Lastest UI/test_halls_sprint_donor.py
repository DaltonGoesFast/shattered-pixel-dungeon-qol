"""Halls sprint loops pay the same donor purse as a zone crown, once per winner."""
from __future__ import annotations

import summon_bestiary as b


def _cfg() -> dict:
    return {
        "sprint_donor_reward": 100,
        "sprint_donor_reward_per_level": 100,
        "shatter_event": {"enabled": False},
        "levels": [
            {"level": 1, "zone": "Sewers", "bar_threshold": 100, "monsters": ["rat"]},
            {"level": 4, "zone": "Metro", "bar_threshold": 100, "monsters": ["monk"]},
            {"level": 5, "zone": "Halls", "bar_threshold": 100, "monsters": ["eye"]},
        ],
    }


def _state(level: int, sprint: dict, winners: list[str] | None = None) -> dict:
    state = b._empty_state()
    state["level"] = level
    state["bar_xp"] = 0
    state["sprint_xp"] = sprint
    state["sprint_winners"] = list(winners or [])
    return state


def _sprint(name: str, xp: int) -> dict:
    return {name.lower(): {"xp": xp, "display": name}}


def test_halls_loop_pays_once() -> None:
    cfg = _cfg()
    state = _state(5, _sprint("Alice", 40))
    leveled_up, info, _events, halls_looped, grants = b._add_bar_xp_and_level_up(
        state, 100, cfg, 1
    )
    assert not leveled_up
    assert halls_looped
    assert info["reason"] == "halls_loop"
    assert info["winner"] == "Alice"
    assert info["donor_reward"] == 500
    assert grants == [("Alice", 500)]
    assert state["last_crown_donor"] == 500
    assert state["last_crown_reason"] == "halls_loop"
    assert "alice" in {w.lower() for w in state["sprint_winners"]}
    assert state["sprint_xp"] == {}
    assert state["level"] == 5


def test_locked_winner_is_not_paid_again() -> None:
    cfg = _cfg()
    state = _state(5, _sprint("Alice", 40), ["alice"])
    _leveled, info, _events, halls_looped, grants = b._add_bar_xp_and_level_up(
        state, 100, cfg, 2
    )
    assert halls_looped
    assert info["winner"] == ""
    assert info["donor_reward"] == 0
    assert grants == []


def test_next_eligible_winner_is_paid_once() -> None:
    cfg = _cfg()
    sprint = _sprint("Alice", 40)
    sprint.update(_sprint("Bob", 15))
    state = _state(5, sprint, ["alice"])
    _leveled, info, _events, _looped, grants = b._add_bar_xp_and_level_up(
        state, 100, cfg, 3
    )
    assert info["winner"] == "Bob"
    assert grants == [("Bob", 500)]


def test_overflow_does_not_pay_the_same_sprint_twice() -> None:
    cfg = _cfg()
    state = _state(5, _sprint("Alice", 40))
    _leveled, info, _events, halls_looped, grants = b._add_bar_xp_and_level_up(
        state, 250, cfg, 4
    )
    assert halls_looped
    assert info["winner"] == "Alice"
    assert grants == [("Alice", 500)]


def test_zone_clear_still_pays_and_does_not_also_pay_halls() -> None:
    cfg = _cfg()
    state = _state(4, _sprint("Alice", 40))
    leveled_up, info, _events, halls_looped, grants = b._add_bar_xp_and_level_up(
        state, 250, cfg, 5
    )
    assert leveled_up
    assert halls_looped
    assert info["reason"] == "level_up"
    assert info["from_level"] == 4
    assert info["donor_reward"] == 400
    # The sprint is crowned and cleared on the Metro clear, so the Halls loop
    # in the same fill has nobody left to pay.
    assert grants == [("Alice", 400)]
    assert state["level"] == 5


def test_zone_clear_pays_its_own_purse() -> None:
    cfg = _cfg()
    state = _state(1, _sprint("Alice", 10))
    leveled_up, info, _events, halls_looped, grants = b._add_bar_xp_and_level_up(
        state, 100, cfg, 6
    )
    assert leveled_up
    assert not halls_looped
    assert info["donor_reward"] == 100
    assert grants == [("Alice", 100)]


def test_settle_pays_each_viewer_once() -> None:
    paid: list[tuple[str, int]] = []

    def fake_grant(username: str, amount: int) -> bool:
        paid.append((username, amount))
        return True

    original = b._grant_sprint_donor
    b._grant_sprint_donor = fake_grant
    try:
        b._settle_sprint_donors([("Alice", 500), ("alice", 500), ("Bob", 400), ("", 100)])
    finally:
        b._grant_sprint_donor = original
    assert paid == [("Alice", 500), ("Bob", 400)]


def main() -> int:
    tests = [
        test_halls_loop_pays_once,
        test_locked_winner_is_not_paid_again,
        test_next_eligible_winner_is_paid_once,
        test_overflow_does_not_pay_the_same_sprint_twice,
        test_zone_clear_still_pays_and_does_not_also_pay_halls,
        test_zone_clear_pays_its_own_purse,
        test_settle_pays_each_viewer_once,
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
    raise SystemExit(main())
