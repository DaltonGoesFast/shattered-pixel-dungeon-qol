#!/usr/bin/env python3
"""Decode Streamer.bot export to inspect structure."""
import base64
import zlib
import json
import sys

# Read from file or stdin
if len(sys.argv) > 1:
    with open(sys.argv[1], 'r', encoding='utf-8') as f:
        data = f.read().strip()
else:
    print("Paste the export string, then Ctrl+Z and Enter (Windows) or Ctrl+D (Unix):")
    data = sys.stdin.read().strip()

# Remove any whitespace/newlines
data = data.replace('\n', '').replace('\r', '').replace(' ', '')

try:
    decoded = base64.b64decode(data)
    print('Base64 decoded length:', len(decoded))
    print('First 20 bytes:', decoded[:20])
    
    # Try zlib decompress
    try:
        decompressed = zlib.decompress(decoded)
        print('Decompressed length:', len(decompressed))
        print('First 500 chars:', decompressed[:500].decode('utf-8', errors='replace'))
        # Try parse as JSON
        try:
            obj = json.loads(decompressed)
            print('\nJSON keys:', list(obj.keys()) if isinstance(obj, dict) else 'not a dict')
            if isinstance(obj, dict):
                with open('export_decoded.json', 'w', encoding='utf-8') as f:
                    json.dump(obj, f, indent=2)
                print('Saved to export_decoded.json')
        except json.JSONDecodeError as e:
            print('JSON parse error:', e)
    except zlib.error as e:
        print('Zlib error:', e)
        # Maybe raw JSON?
        try:
            text = decoded.decode('utf-8', errors='replace')
            print('As text (first 500):', text[:500])
        except:
            pass
except Exception as e:
    print('Error:', type(e).__name__, e)
