#!/usr/bin/env python3
"""POST spawn command to overlay server. Usage: python spawn_post.py <monster> [username]"""
import sys
import urllib.request
import json

def main():
    monster = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
    if not monster:
        print("Usage: python spawn_post.py <monster> [username]", file=sys.stderr)
        sys.exit(1)
    username = (sys.argv[2] if len(sys.argv) > 2 else "").strip() or None

    url = "http://127.0.0.1:5000/api/spawn-command"
    payload = {"monster": monster}
    if username:
        payload["username"] = username
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")

    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            body = resp.read().decode()
            print(body)
            data = json.loads(body)
            if not data.get("ok"):
                sys.exit(1)
    except urllib.error.HTTPError as e:
        print(e.read().decode(), file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
