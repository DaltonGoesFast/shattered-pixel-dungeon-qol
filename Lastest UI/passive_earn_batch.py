"""
R03 Passive earn — POST each body from passive_earn_batch.json to /api/chat-command.

Called by Streamer.bot Run Program after PassiveEarnDispatch.cs writes the batch file.
"""
import json
import os
import urllib.error
import urllib.request

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BATCH_FILE = os.path.join(SCRIPT_DIR, "passive_earn_batch.json")
API_URL = "http://127.0.0.1:5000/api/chat-command"


def main() -> int:
    if not os.path.exists(BATCH_FILE):
        return 0
    try:
        with open(BATCH_FILE, encoding="utf-8-sig") as f:
            batch = json.load(f)
    except (json.JSONDecodeError, OSError) as e:
        print(f"passive_earn_batch: read failed: {e}")
        return 1
    if not isinstance(batch, list) or not batch:
        return 0

    ok = 0
    for body in batch:
        if not isinstance(body, dict) or not body.get("username"):
            continue
        payload = json.dumps(body).encode("utf-8")
        req = urllib.request.Request(
            API_URL,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=8) as resp:
                resp.read()
            ok += 1
        except (urllib.error.URLError, OSError) as e:
            print(f"passive_earn_batch: POST failed for {body.get('username')}: {e}")
    print(f"passive_earn_batch: posted {ok}/{len(batch)}")
    return 0 if ok > 0 or not batch else 1


if __name__ == "__main__":
    raise SystemExit(main())
