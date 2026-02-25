import gzip
import json
from pathlib import Path

def inspect_hero():
    save_directory = Path(r"C:\Users\dalto\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon")
    save_files = []
    for game_dir in save_directory.glob("game*"):
        if game_dir.is_dir():
            game_dat = game_dir / "game.dat"
            if game_dat.exists():
                save_files.append(game_dat)
    
    # Also check the local folder just in case
    local_dir = Path(r"c:\Users\dalto\Documents\My Games\SPD\Lastest UI\Shattered Pixel Dungeon")
    for game_dir in local_dir.glob("game*"):
        if game_dir.is_dir():
            game_dat = game_dir / "game.dat"
            if game_dat.exists():
                save_files.append(game_dat)

    if not save_files:
        print("No save files found.")
        return

    latest_save = max(save_files, key=lambda p: p.stat().st_mtime)
    print(f"Inspecting latest save: {latest_save}")

    output = []
    def log(msg):
        print(msg)
        output.append(str(msg))

    try:
        with gzip.open(latest_save, 'rt', encoding='utf-8') as f:
            data = json.load(f)
        
        hero = data.get('hero', {})
        log("\nHero object keys and types:")
        for k, v in hero.items():
            log(f"  {k}: {type(v).__name__}")
        
        log("\nHero __className:")
        log(hero.get('__className'))
        
        # Check heroClass sub-object
        if 'heroClass' in hero:
            hc = hero['heroClass']
            log("\nheroClass details:")
            if isinstance(hc, dict):
                for k, v in hc.items():
                    log(f"  {k}: {v}")
            else:
                log(f"  Value: {hc}")
        
        # Check subClass
        if 'subClass' in hero:
            sc = hero['subClass']
            log(f"\nsubClass: {sc}")

        # Search for any string containing 'Rogue', 'Warrior', 'Mage', 'Huntress', 'Duelist'
        class_names = ['Rogue', 'Warrior', 'Mage', 'Huntress', 'Duelist']
        log("\nSearching for class names in hero object:")
        def search_class(obj, path="hero"):
            if isinstance(obj, dict):
                for k, v in obj.items():
                    search_class(v, f"{path}.{k}")
            elif isinstance(obj, list):
                for i, item in enumerate(obj):
                    search_class(item, f"{path}[{i}]")
            elif isinstance(obj, str):
                for name in class_names:
                    if name.lower() in obj.lower():
                        log(f"  Match at {path}: {obj}")

        search_class(hero)

        with open("hero_inspection.txt", "w", encoding='utf-8') as f:
            f.write("\n".join(output))

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    inspect_hero()
