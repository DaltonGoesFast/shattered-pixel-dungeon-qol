"""
Fetch game seed from overlay server and write full message to file for Streamer.bot.
Usage: python get_game_seed.py
Writes "Current Seed: XXXX-XXX-XXX" to last_seed.txt on success.
Streamer.bot: Run a Program → Read Lines From File → use message "From Variable" with %gameSeed%
             (or just use the file content as the entire message)
"""
import urllib.request
import json
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "last_seed.txt")

try:
    with urllib.request.urlopen("http://127.0.0.1:5000/game_summary.json", timeout=3) as r:
        data = json.loads(r.read().decode())
        seed = data.get("seed")
        if seed:
            msg = f"Current Seed: {seed.strip()}"
            with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
                f.write(msg)
            exit(0)
except Exception:
    pass
exit(1)
