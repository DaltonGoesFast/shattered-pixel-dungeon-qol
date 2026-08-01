#!/usr/bin/env python3
"""
One-time migration for Economy v1.1 (Phase 6).

For each user in viewer_points.txt:
  - If chat_pts > chat_point_cap: move floor(excess * bank_ratio_manual) into donor wallet
  - Zero excess chat (pts = donation_pts after migration)

Dry-run by default. Pass --apply to write.

Usage:
  python migrate_economy_v11.py
  python migrate_economy_v11.py --apply
"""
import argparse
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

from points_command import (  # noqa: E402
    chat_pts,
    get_config,
    points_lock,
    read_points,
    write_points,
    _get_user_data,
)


def migrate(*, apply: bool) -> dict:
    cfg = get_config()
    cap = int(cfg.get("chat_point_cap", 500))
    ratio = float(cfg.get("bank_ratio_manual", 0.10))

    migrated = 0
    donor_total = 0
    lines = []

    with points_lock():
        data = read_points()
        for key in sorted(data.keys()):
            pts, last, donation_pts, role = _get_user_data(data, key)
            c = chat_pts(pts, donation_pts)
            if c <= cap:
                continue
            excess = c - cap
            donor_gain = int(excess * ratio)
            new_donation = donation_pts + donor_gain
            new_pts = new_donation + cap
            lines.append(
                f"{key}: chat {c} -> {cap}, donor +{donor_gain} -> {new_donation}"
            )
            migrated += 1
            donor_total += donor_gain
            if apply:
                data[key] = (new_pts, last, new_donation, role)
        if apply:
            write_points(data)

    return {
        "users_migrated": migrated,
        "donor_pts_awarded": donor_total,
        "cap": cap,
        "ratio": ratio,
        "applied": apply,
        "details": lines,
    }


def main():
    parser = argparse.ArgumentParser(description="Migrate excess chat pts above cap into donor wallet.")
    parser.add_argument("--apply", action="store_true", help="Write changes (default is dry-run)")
    args = parser.parse_args()

    result = migrate(apply=args.apply)
    mode = "APPLIED" if result["applied"] else "DRY-RUN"
    print(f"[{mode}] cap={result['cap']} ratio={result['ratio']}")
    print(f"Users migrated: {result['users_migrated']}, donor pts awarded: {result['donor_pts_awarded']}")
    for line in result["details"][:50]:
        print(f"  {line}".encode("ascii", errors="replace").decode("ascii"))
    if len(result["details"]) > 50:
        print(f"  ... and {len(result['details']) - 50} more")
    if not args.apply and result["users_migrated"]:
        print("Re-run with --apply to write.")


if __name__ == "__main__":
    main()
