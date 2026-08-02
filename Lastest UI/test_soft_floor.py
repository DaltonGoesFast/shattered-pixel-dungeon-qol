"""Smoke-test soft-floor catch-up XP. Resets bestiary session state.

Seeds XP only, then queues two demo marches via /api/summon-march (needs Flask up).
Uses rat (2 XP -> 3 with x1.25) so catch-up always shows a visible +1.
"""
from __future__ import annotations

import json
import urllib.error
import urllib.request

from chat_messages import summon_success
from summon_bestiary import apply_summon, reset_bestiary_state, user_sprint_xp

BASE = "http://127.0.0.1:5000"
DEMO_MOB = "rat"  # base 2 XP; catch-up -> 3 (ceil / min +1)


def _queue_march(username: str, monster: str, result: dict) -> bool:
    payload = json.dumps(
        {
            "username": username,
            "monster": monster,
            "layout": "horizontal",
            "xp": int(result.get("xp") or 0),
            "bestiary_level": int(result.get("level") or 1),
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE}/api/summon-march",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            return 200 <= int(resp.status) < 300
    except (urllib.error.URLError, urllib.error.HTTPError, OSError) as e:
        print(f"  (march not queued: {e})")
        return False


def main() -> None:
    reset_bestiary_state()
    print("Seeding unique summoners + top-3 lead (XP only, no marches)...")
    for i in range(10):
        apply_summon(f"Tester{i}", "rat")
    for name in ("Tester0", "Tester1", "Tester2"):
        for _ in range(5):
            apply_summon(name, "gnoll")

    before = user_sprint_xp("Tester9")
    r = apply_summon("Tester9", DEMO_MOB)
    after = user_sprint_xp("Tester9")
    print(
        summon_success(
            "Tester9",
            DEMO_MOB,
            r["xp"],
            soft_floor=bool(r.get("soft_floor")),
            xp_mult=float(r.get("xp_mult", 1)),
        )
    )
    print(
        "PROOF mid-pack: sprint",
        before,
        "->",
        after,
        f"(+{after - before})",
        "| xp_base=",
        r.get("xp_base"),
        "applied=",
        r.get("xp"),
        "soft_floor=",
        r.get("soft_floor"),
        "march=",
        _queue_march("Tester9", DEMO_MOB, r),
    )
    assert r.get("soft_floor") is True
    assert int(r.get("xp_base") or 0) == 2
    assert int(r.get("xp") or 0) == 3
    assert after - before == 3

    before0 = user_sprint_xp("Tester0")
    r2 = apply_summon("Tester0", DEMO_MOB)
    after0 = user_sprint_xp("Tester0")
    print(
        "PROOF top3:",
        summon_success(
            "Tester0",
            DEMO_MOB,
            r2["xp"],
            soft_floor=bool(r2.get("soft_floor")),
            xp_mult=float(r2.get("xp_mult", 1)),
        ),
    )
    print(
        "PROOF top3: sprint",
        before0,
        "->",
        after0,
        f"(+{after0 - before0})",
        "| soft_floor=",
        r2.get("soft_floor"),
        "applied=",
        r2.get("xp"),
        "march=",
        _queue_march("Tester0", DEMO_MOB, r2),
    )
    assert r2.get("soft_floor") is False
    assert int(r2.get("xp") or 0) == 2
    assert after0 - before0 == 2
    print("OK — catch-up adds 3 XP for mid-pack rat, 2 XP for top 3.")


if __name__ == "__main__":
    main()
