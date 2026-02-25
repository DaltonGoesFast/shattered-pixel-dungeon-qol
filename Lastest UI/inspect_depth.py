import gzip
import json
import os
from pathlib import Path

def inspect_depth_file():
    depth_file = Path(r"C:\Users\dalto\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon\game2\depth4.dat")
    if not depth_file.exists():
        print(f"File {depth_file} not found.")
        return

    try:
        with gzip.open(depth_file, 'rt', encoding='utf-8') as f:
            data = json.load(f)
        
        level = data.get('level', {})
        print("\nKeys in level dict:")
        for key in sorted(level.keys()):
            val = level[key]
            if isinstance(val, (str, int, bool)):
                print(f"- {key}: {val}")
            # elif 'feeling' in key.lower():
            #     print(f"- {key}: {val}")
                
        # Search specifically for anything that looks like a feeling in level
        for key, value in level.items():
            if 'feeling' in key.lower():
                print(f"\nFOUND FEELING: {key} = {value}")

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    inspect_depth_file()
