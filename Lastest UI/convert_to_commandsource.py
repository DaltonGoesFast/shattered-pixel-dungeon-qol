#!/usr/bin/env python3
"""
Convert Streamer.bot export to use commandSource for Twitch/YouTube.
Reads export from file, decodes, modifies actions, re-encodes for import.

Usage:
  1. Save your Streamer.bot export to a file (e.g. export.txt)
  2. Run: python convert_to_commandsource.py export.txt
  3. Import the generated export_commandsource.txt in Streamer.bot
"""
import base64
import zlib
import gzip
import json
import sys
import re
import copy

# Sub-action type IDs (Streamer.bot internal - may need adjustment)
# We're looking for Twitch Message and YouTube Message sub-actions to wrap
TWITCH_MSG = 'TwitchMessage'  # or similar - we'll search by content
YOUTUBE_MSG = 'YouTubeMessage'

def decode_export(data):
    """Decode Streamer.bot export string to JSON.
    Format: base64(sbar_header + gzip(json))
    """
    data = data.replace('\n', '').replace('\r', '').replace(' ', '').strip()
    # Fix base64 padding (must be multiple of 4)
    if len(data) % 4:
        data += '=' * (4 - len(data) % 4)
    try:
        decoded = base64.b64decode(data)
    except Exception as e:
        raise ValueError(f"Base64 decode failed: {e}")
    # Streamer.bot uses 4-byte "SBAR" header + gzip
    if len(decoded) > 4 and decoded[:4] == b'SBAR':
        payload = decoded[4:]
    else:
        payload = decoded
    try:
        decompressed = gzip.decompress(payload)
    except (gzip.BadGzipFile, EOFError):
        try:
            decompressed = zlib.decompress(decoded)
        except zlib.error:
            decompressed = decoded
    try:
        return json.loads(decompressed.decode('utf-8'))
    except json.JSONDecodeError as e:
        raise ValueError(f"JSON parse failed: {e}")


def encode_export(obj):
    """Encode JSON back to Streamer.bot import format."""
    json_bytes = json.dumps(obj, separators=(',', ':')).encode('utf-8')
    compressed = b'SBAR' + gzip.compress(json_bytes)
    return base64.b64encode(compressed).decode('ascii')


def needs_commandsource(action):
    """Check if action has platform-specific messages that should use commandSource."""
    # Look for sub-actions that are Twitch Message or YouTube Message
    # and are NOT already inside a commandSource conditional
    subs = action.get('SubActions', action.get('subActions', []))
    has_twitch = any(_is_twitch_msg(s) for s in subs)
    has_youtube = any(_is_youtube_msg(s) for s in subs)
    # If it has only one platform, we might want to add the other
    # If it has both as siblings, we should consolidate with commandSource
    return has_twitch or has_youtube


def _is_twitch_msg(sub):
    t = sub.get('$type', sub.get('Type', ''))
    return 'Twitch' in str(t) and 'Message' in str(t)


def _is_youtube_msg(sub):
    t = sub.get('$type', sub.get('Type', ''))
    return 'YouTube' in str(t) and 'Message' in str(t)


def create_commandsource_conditional(platform, message, is_success=True):
    """Create a conditional sub-action: if commandSource == platform, send message."""
    return {
        "$type": "Streamer.bot.Streamerbot.Condition",
        "Condition": {
            "Left": {"$type": "Streamer.bot.Streamerbot.Variable", "Name": "commandSource"},
            "Operator": "Equals",
            "Right": {"$type": "Streamer.bot.Streamerbot.String", "Value": platform},
            "IgnoreCase": True
        },
        "TrueSubActions": [{
            "$type": f"Streamer.bot.Streamerbot.{platform}Message",
            "Message": message
        }],
        "FalseSubActions": []
    }


def convert_action(action, depth=0):
    """
    Recursively convert an action's sub-actions to use commandSource.
    When we find a spawnResult conditional with Twitch/YouTube messages,
    replace with commandSource-nested structure.
    """
    # This is a simplified converter - the exact structure depends on Streamer.bot's
    # internal format which we don't have full docs for.
    # We'll output instructions + a best-effort conversion.
    return action  # Placeholder - real impl needs schema


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        print("\nError: Provide export file path as argument.")
        sys.exit(1)

    path = sys.argv[1]
    try:
        with open(path, 'r', encoding='utf-8') as f:
            data = f.read()
    except FileNotFoundError:
        print(f"Error: File not found: {path}")
        sys.exit(1)

    try:
        obj = decode_export(data)
    except ValueError as e:
        print(f"Decode error: {e}")
        sys.exit(1)

    # Save decoded JSON for inspection
    out_json = path.replace('.txt', '_decoded.json').replace('.sb', '_decoded.json')
    if out_json == path:
        out_json = 'export_decoded.json'
    with open(out_json, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2)
    print(f"Decoded export saved to: {out_json}")

    # Inspect structure
    if isinstance(obj, dict):
        print("Top-level keys:", list(obj.keys()))
        actions = obj.get('Actions', obj.get('actions', []))
        if actions:
            print(f"Found {len(actions)} actions")
            for i, a in enumerate(actions[:5]):
                name = a.get('Name', a.get('name', '?'))
                print(f"  - {name}")
    else:
        print("Root type:", type(obj))

    print("\nManual conversion required:")
    print("Streamer.bot's export format is proprietary. Use the decoded JSON as reference")
    print("and apply the commandSource pattern manually in Streamer.bot for each action:")
    print("  1. Replace Twitch Message / YouTube Message with:")
    print("     - if (commandSource == youtube) -> YouTube Message")
    print("     - if (commandSource == twitch) -> Twitch Message")
    print("  2. Put both inside the success/failure branches of spawnResult check")
    sys.exit(0)


if __name__ == '__main__':
    main()
