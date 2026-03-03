import gzip
import json
import os
from pathlib import Path

def inspect_save_keys():
    save_directory = Path(r"C:\Users\dalto\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon")
    save_files = []
    for game_dir in save_directory.glob("game*"):
        if game_dir.is_dir():
            game_dat = game_dir / "game.dat"
            if game_dat.exists():
                save_files.append(game_dat)
    
    if not save_files:
        print("No save files found.")
        return

    latest_save = max(save_files, key=lambda p: p.stat().st_mtime)
    print(f"Inspecting latest save: {latest_save}")

    try:
        with gzip.open(latest_save, 'rt', encoding='utf-8') as f:
            data = json.load(f)
        
        current_depth = data.get('depth', 1)
        print(f"Current depth: {current_depth}")

        generated_levels = data.get('generated_levels', [])
        print(f"Number of generated levels: {len(generated_levels)}")

        if generated_levels:
            print(f"Type of first element in generated_levels: {type(generated_levels[0])}")
            if isinstance(generated_levels[0], list):
                 print(f"Size of first element: {len(generated_levels[0])}")
            
            # Check for strings that look like feelings
            common_words = ["water", "smell", "air", "feel", "dark", "humid", "vegetation", "death", "watching", "clear"]
            
            print("\nSearching for common feeling words...")
            def search_recursive(obj, path=""):
                if isinstance(obj, dict):
                    for k, v in obj.items():
                        new_path = f"{path}.{k}" if path else k
                        if isinstance(v, str):
                            for word in common_words:
                                if word in v.lower():
                                    print(f"Match found at {new_path}: {v}")
                        search_recursive(v, new_path)
                elif isinstance(obj, list):
                    for i, item in enumerate(obj):
                        search_recursive(item, f"{path}[{i}]")

            search_recursive(data)

            # Let's also check if there is a 'feeling' key in the level data if it's nested
            # Maybe it's in a sub-object of the entries in generated_levels?
            # Let's dump the first level data fully if it's not too big
            # or just its keys if it's a dict.
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    inspect_save_keys()
