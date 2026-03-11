import gzip
import json
import os
import time
from pathlib import Path
from typing import Dict, Any, Optional

class SPDSaveParser:
    """Parser for Shattered Pixel Dungeon save files"""
    
    def __init__(self, save_directory: str):
        self.save_directory = Path(save_directory)
        self.last_modified_times = {}
        self._last_parse_error = None  # (path, message) for throttle
        self._last_parse_error_time = 0.0
        self._parse_error_throttle_sec = 60.0
        
    def find_latest_save(self) -> Optional[Path]:
        """Find the most recently modified game save file"""
        save_files = []
        
        # Look for game.dat files in game1, game2, etc. directories
        for game_dir in self.save_directory.glob("game*"):
            if game_dir.is_dir():
                game_dat = game_dir / "game.dat"
                if game_dat.exists():
                    save_files.append(game_dat)
        
        if not save_files:
            return None
            
        # Return the most recently modified save file
        return max(save_files, key=lambda p: p.stat().st_mtime)

    def _saves_by_newest(self) -> list:
        """Return all game.dat paths sorted by mtime newest first (for fallback when latest is unparseable)."""
        save_files = []
        for game_dir in self.save_directory.glob("game*"):
            if game_dir.is_dir():
                game_dat = game_dir / "game.dat"
                if game_dat.exists():
                    save_files.append(game_dat)
        save_files.sort(key=lambda p: p.stat().st_mtime, reverse=True)
        return save_files
    
    def parse_save_file(self, save_path: Path) -> Optional[Dict[str, Any]]:
        """Parse a gzipped JSON save file"""
        try:
            with gzip.open(save_path, 'rt', encoding='utf-8') as f:
                data = json.load(f)
            return data
        except Exception as e:
            key = (str(save_path), type(e).__name__)
            now = time.time()
            if self._last_parse_error == key and (now - self._last_parse_error_time) < self._parse_error_throttle_sec:
                pass  # throttle repeated same error
            else:
                self._last_parse_error = key
                self._last_parse_error_time = now
                print(f"Error parsing save file {save_path}: {e}")
            return None

    def get_level_data(self, save_path: Path, depth: int) -> Optional[Dict[str, Any]]:
        """Load the depthX.dat file for the given depth"""
        try:
            depth_file = save_path.parent / f"depth{depth}.dat"
            if depth_file.exists():
                with gzip.open(depth_file, 'rt', encoding='utf-8') as f:
                    data = json.load(f)
                return data.get('level', {})
            return None
        except Exception as e:
            print(f"Error loading level data for depth {depth}: {e}")
            return None

    def format_feeling_name(self, feeling_enum: str) -> str:
        """Map internal feeling enums to human-readable strings"""
        feelings = {
            'NONE': 'None',
            'CHASM': 'Chasm (Falling Risk)',
            'WATER': 'Flooded (Water Everywhere)',
            'GRASS': 'Overgrown (Vegetation)',
            'SECRETS': 'Hidden Chambers (Secrets)',
            'DARK': 'Darkness (Limited Vision)',
            'TRAPS': 'Dangerous (Extra Traps)',
            'MONSTERS': 'Infested (More Monsters)',
            'FIRE': 'Burnt (Fire Hazards)',
            'CHAMPION': 'Hostile (Champion Present)',
            'TRANSIENT': 'Unstable (Transient)',
            'LOST': 'Confusing (Lost Floor)'
        }
        return feelings.get(feeling_enum, feeling_enum.capitalize() if feeling_enum else 'None')
    
    def seed_to_string(self, num: int) -> str:
        """Convert a numerical seed to the SPD alphanumeric format (e.g. YZB-SGH-FWF)"""
        if num is None:
            return ""
        
        # Following the logic from DungeonSeed.java in SPD source:
        # 1. Convert to base 26 string where digits are 0-9 and letters are a-p
        alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        interim = ""
        n = int(num)
        if n == 0:
            interim = "0"
        else:
            while n > 0:
                interim = alphabet[n % 26] + interim
                n //= 26
        
        # 2. Map characters to A-Z and pad with 'A'
        mapped = ""
        for c in interim:
            if '0' <= c <= '9':
                mapped += chr(ord(c) + 17) # 0-9 -> A-J
            else:
                mapped += chr(ord(c) - 22) # a-p -> K-Z
        
        # Pad with 'A' until length is 9
        while len(mapped) < 9:
            mapped = "A" + mapped
            
        # 3. Format with dashes: XXX-XXX-XXX
        return f"{mapped[:3]}-{mapped[3:6]}-{mapped[6:]}"
    
    def extract_game_info(self, save_data: Dict[str, Any], level_data: Dict[str, Any] = None) -> Dict[str, Any]:
        """Extract relevant game information from save data"""
        if not save_data:
            return {}
        
        hero = save_data.get('hero', {})
        
        # Extract hero class from the 'class' field first, fallback to className
        hero_class_raw = hero.get('class', '')
        if not hero_class_raw or hero_class_raw.lower() == 'hero':
            hero_class_full = hero.get('__className', '')
            hero_class_raw = hero_class_full.split('.')[-1] if hero_class_full else 'Unknown'
        
        # Format to Title Case (e.g., ROGUE -> Rogue)
        hero_class = hero_class_raw.capitalize()
        
        # Extract subclass if available
        subclass_full = hero.get('subClass', '')
        subclass = subclass_full.split('.')[-1] if subclass_full else None
        
        # Get equipped items
        weapon = None
        armor = None
        artifact = None
        ring = None
        misc = None
        
        # Check equipped slots
        # First check if items are directly in hero (newer versions)
        if 'weapon' in hero and hero['weapon']:
            weapon_obj = hero['weapon']
            weapon_class = weapon_obj.get('__className', '')
            weapon = {
                'name': weapon_class.split('.')[-1],
                'level': weapon_obj.get('level', 0),
                'enchantment': weapon_obj.get('enchantment', {}).get('__className', '').split('.')[-1] if 'enchantment' in weapon_obj else None
            }
            
        if 'armor' in hero and hero['armor']:
            armor_obj = hero['armor']
            armor_class = armor_obj.get('__className', '')
            armor = {
                'name': armor_class.split('.')[-1],
                'level': armor_obj.get('level', 0),
                'glyph': armor_obj.get('glyph', {}).get('__className', '').split('.')[-1] if 'glyph' in armor_obj else None
            }
            
        if 'artifact' in hero and hero['artifact']:
            artifact_obj = hero['artifact']
            artifact_class = artifact_obj.get('__className', '')
            artifact = {
                'name': artifact_class.split('.')[-1],
                'level': artifact_obj.get('level', 0)
            }
            
        if 'ring' in hero and hero['ring']:
            ring_obj = hero['ring']
            ring_class = ring_obj.get('__className', '')
            ring = {
                'name': ring_class.split('.')[-1],
                'level': ring_obj.get('level', 0)
            }
            
        if 'misc' in hero and hero['misc']:
            misc_obj = hero['misc']
            misc_class = misc_obj.get('__className', '')
            misc = {
                'name': misc_class.split('.')[-1],
                'level': misc_obj.get('level', 0)
            }

        # Fallback to belongings (older versions) if not found
        if 'belongings' in hero:
            belongings = hero['belongings']
            
            if not weapon and 'weapon' in belongings and belongings['weapon']:
                weapon_class = belongings['weapon'].get('__className', '')
                weapon = {
                    'name': weapon_class.split('.')[-1],
                    'level': belongings['weapon'].get('level', 0),
                    'enchantment': belongings['weapon'].get('enchantment', {}).get('__className', '').split('.')[-1] if 'enchantment' in belongings['weapon'] else None
                }
            
            if not armor and 'armor' in belongings and belongings['armor']:
                armor_class = belongings['armor'].get('__className', '')
                armor = {
                    'name': armor_class.split('.')[-1],
                    'level': belongings['armor'].get('level', 0),
                    'glyph': belongings['armor'].get('glyph', {}).get('__className', '').split('.')[-1] if 'glyph' in belongings['armor'] else None
                }
            
            if not artifact and 'artifact' in belongings and belongings['artifact']:
                artifact_class = belongings['artifact'].get('__className', '')
                artifact = {
                    'name': artifact_class.split('.')[-1],
                    'level': belongings['artifact'].get('level', 0)
                }
            
            if not misc and 'misc' in belongings and belongings['misc']:
                misc_class = belongings['misc'].get('__className', '')
                misc = {
                    'name': misc_class.split('.')[-1],
                    'level': belongings['misc'].get('level', 0)
                }
        
        # Extract inventory items
        inventory = []
        for item in hero.get('inventory', []):
            item_class = item.get('__className', '').split('.')[-1]
            inventory.append({
                'name': item_class,
                'quantity': item.get('quantity', 1),
                'level': item.get('level', 0)
            })
        
        # Extract identification info
        identification = self.extract_identification(save_data)
        
        # Compile all information
        game_info = {
            'hero': {
                'class': hero_class,
                'subclass': subclass,
                'hp': hero.get('HP', 0),
                'ht': hero.get('HT', 0),
                'exp': hero.get('exp', 0),
                'lvl': hero.get('lvl', 1),
                'str': hero.get('STR', 10)
            },
            'equipped': {
                'weapon': weapon,
                'armor': armor,
                'artifact': artifact,
                'ring': ring,
                'misc': misc
            },
            'inventory': inventory,
            'identification': identification,
            'stats': {
                'depth': save_data.get('depth', 1),
                'max_depth': save_data.get('maxDepth', 1),
                'gold': save_data.get('gold', 0),
                'energy': save_data.get('energy', 0),
                'score': save_data.get('score', 0),
                'enemies_slain': save_data.get('enemiesSlain', 0),
                'food_eaten': save_data.get('foodEaten', 0),
                'potions_cooked': save_data.get('potionsCooked', 0),
                'ankhs_used': save_data.get('ankhsUsed', 0)
            },
            'challenges': self.decode_challenges(save_data.get('challenges', 0)),
            'won': save_data.get('won', False),
            'ascended': save_data.get('ascended', False),
            'seed': save_data.get('custom_seed') or self.seed_to_string(save_data.get('seed')),
            'duration': save_data.get('duration', 0),
            'upgrades_used': save_data.get('upgradesUsed', 0),
            'combat_stats': {
                'sneak_attacks': save_data.get('sneakAttacks', 0),
                'thrown_assists': save_data.get('thrownAssists', 0),
                'hazard_assists': save_data.get('hazard_assists', 0)
            },
            'buffs': [b.get('__className', '').split('.')[-1] for b in hero.get('buffs', []) if isinstance(b, dict)],
            'talents': {
                'tier1': hero.get('talents_tier_1', {}),
                'tier2': hero.get('talents_tier_2', {}),
                'tier3': hero.get('talents_tier_3', {}),
                'tier4': hero.get('talents_tier_4', {})
            },
            'quests': save_data.get('quests', {}),
            'feeling': self.format_feeling_name(level_data.get('feeling', 'NONE')) if level_data else 'None'
        }
        
        return game_info
    
    def decode_challenges(self, mask: int) -> list:
        """Decode SPD challenge bitmask into readable names"""
        challenges = []
        if mask & 1: challenges.append("On Diet")
        if mask & 2: challenges.append("Faith is my Armor")
        if mask & 4: challenges.append("Pharmacophobia")
        if mask & 8: challenges.append("Barren Land")
        if mask & 16: challenges.append("Swarm Intelligence")
        if mask & 32: challenges.append("Into Darkness")
        if mask & 64: challenges.append("Forbidden Runes")
        if mask & 128: challenges.append("Hostile Champions")
        if mask & 256: challenges.append("Badder Bosses")
        return challenges

    def extract_identification(self, save_data: Dict[str, Any]) -> Dict[str, Any]:
        """Extract identification status of items"""
        identification = {
            'potions': [],
            'scrolls': [],
            'rings': []
        }
        
        # Helper to format class name to readable name
        def format_name(name):
            # Insert space before capital letters
            import re
            s1 = re.sub('(.)([A-Z][a-z]+)', r'\1 \2', name)
            return re.sub('([a-z0-9])([A-Z])', r'\1 \2', s1).replace('Of', 'of')

        for key, value in save_data.items():
            if key.endswith('_label'):
                base_name = key.replace('_label', '')
                known_key = f"{base_name}_known"
                is_known = save_data.get(known_key, False)
                
                item_type = None
                if base_name.startswith('Potion'):
                    item_type = 'potions'
                elif base_name.startswith('Scroll'):
                    item_type = 'scrolls'
                elif base_name.startswith('Ring'):
                    item_type = 'rings'
                
                if item_type:
                    identification[item_type].append({
                        'class_name': base_name,
                        'true_name': format_name(base_name),
                        'rune_name': value,
                        'is_known': is_known
                    })
        
        # Sort items by name
        for category in identification:
            identification[category].sort(key=lambda x: x['true_name'])
            
        return identification
    
    def get_current_game_info(self) -> Optional[Dict[str, Any]]:
        """Get information from the most recent parseable save file (tries newest first)."""
        for latest_save in self._saves_by_newest():
            save_data = self.parse_save_file(latest_save)
            if not save_data:
                continue
            depth = save_data.get('depth', 1)
            level_data = self.get_level_data(latest_save, depth)
            return self.extract_game_info(save_data, level_data)
        return None
    
    def has_save_updated(self, save_path: Path) -> bool:
        """Check if a save file has been modified since last check"""
        try:
            current_mtime = save_path.stat().st_mtime
            last_mtime = self.last_modified_times.get(str(save_path), 0)
            
            if current_mtime > last_mtime:
                self.last_modified_times[str(save_path)] = current_mtime
                return True
            return False
        except:
            return False


    def generate_summary_text(self, game_info: Dict[str, Any]) -> str:
        """Generate a human-readable summary of the game state"""
        if not game_info:
            return "No game data available."
            
        hero = game_info.get('hero', {})
        stats = game_info.get('stats', {})
        equipped = game_info.get('equipped', {})
        
        summary = []
        summary.append("=== Shattered Pixel Dungeon Game Summary ===")
        summary.append(f"Last Updated: {time.strftime('%Y-%m-%d %H:%M:%S')}")
        if game_info.get('seed'):
            summary.append(f"Seed:         {game_info['seed']}")
        
        # Format duration (turns)
        duration = game_info.get('duration', 0)
        summary.append(f"Turns:        {duration}")
        summary.append("-" * 44)
        
        # Hero Info
        hero_class = hero.get('class', 'Unknown')
        subclass = hero.get('subclass')
        class_str = f"{hero_class} ({subclass})" if subclass else hero_class
        summary.append(f"Hero:    {class_str}")
        summary.append(f"Level:   {hero.get('lvl', 1)} (XP: {hero.get('exp', 0)})")
        summary.append(f"HP:      {hero.get('hp', 0)}/{hero.get('ht', 0)}")
        summary.append(f"STR:     {hero.get('str', 10)}")
        
        # Buffs
        buffs = game_info.get('buffs', [])
        if buffs:
            summary.append(f"Buffs:   {', '.join(buffs)}")
            
        summary.append("-" * 44)
        
        # Location & Stats
        summary.append(f"Depth:   {stats.get('depth', 1)} (Max: {stats.get('max_depth', 1)})")
        summary.append(f"Feeling: {game_info.get('feeling', 'None')}")
        summary.append(f"Gold:    {stats.get('gold', 0)}")
        summary.append(f"Score:   {stats.get('score', 0)}")
        summary.append(f"Upgrades Used: {game_info.get('upgrades_used', 0)}")
        
        # Combat Stats
        c_stats = game_info.get('combat_stats', {})
        if any(c_stats.values()):
            summary.append(f"Combat:  Sneaks: {c_stats.get('sneak_attacks', 0)}, "
                         f"Thrown: {c_stats.get('thrown_assists', 0)}, "
                         f"Hazard: {c_stats.get('hazard_assists', 0)}")
            summary.append(f"Total Enemies Slain: {stats.get('enemies_slain', 0)}")
            
        summary.append("-" * 44)
        
        # Equipment
        summary.append("Equipment:")
        for slot, item in equipped.items():
            if item:
                lvl_str = f" +{item['level']}" if item.get('level', 0) > 0 else ""
                ench_str = f" ({item.get('enchantment') or item.get('glyph')})" if item.get('enchantment') or item.get('glyph') else ""
                summary.append(f"  {slot.capitalize():<8}: {item['name']}{lvl_str}{ench_str}")
            else:
                summary.append(f"  {slot.capitalize():<8}: Empty")
        
        summary.append("-" * 44)
        
        # Talents
        summary.append("Talents (Points Spent):")
        talents = game_info.get('talents', {})
        for tier, points in talents.items():
            if isinstance(points, dict) and points:
                summary.append(f"  {tier.capitalize()}:")
                for name, level in points.items():
                    # Format talent name: THIEFS_INTUITION -> Thief's Intuition
                    clean_name = name.replace('_', ' ').lower().title()
                    # Small fix for possessives
                    clean_name = clean_name.replace("S Intuition", "s Intuition")
                    summary.append(f"    - {clean_name}: {level}")
        
        summary.append("-" * 44)
        
        # Quests
        quests = game_info.get('quests', {})
        if quests:
            summary.append("Quests:")
            for q_name, q_data in quests.items():
                if isinstance(q_data, dict):
                    status = "Completed" if q_data.get('completed') else "In Progress"
                    summary.append(f"  {q_name.capitalize()}: {status}")
            summary.append("-" * 44)

        # Challenges
        challenges = game_info.get('challenges', [])
        if challenges:
            summary.append(f"Challenges: {', '.join(challenges)}")
            summary.append("-" * 44)

        # Inventory
        inventory = game_info.get('inventory', [])
        if inventory:
            summary.append("Inventory:")
            for item in inventory:
                name = item['name']
                qty = item.get('quantity', 1)
                lvl = item.get('level', 0)
                
                lvl_str = f" +{lvl}" if lvl > 0 else ""
                qty_str = f" x{qty}" if qty > 1 else ""
                summary.append(f"  - {name}{lvl_str}{qty_str}")
            summary.append("-" * 44)

        summary.append("Identified Items:")
        ident = game_info.get('identification', {})
        
        for cat in ['potions', 'scrolls', 'rings']:
            items = ident.get(cat, [])
            known = [i['true_name'] for i in items if i.get('is_known')]
            if known:
                summary.append(f"  {cat.capitalize()}: {', '.join(known)}")
        
        summary.append("=" * 44)
        
        return "\n".join(summary)


if __name__ == "__main__":
    import sys
    _script_dir = os.path.dirname(os.path.abspath(__file__))

    def _default_save_dir():
        home = os.path.expanduser("~")
        if os.name == "nt":
            return os.path.join(home, "AppData", "Roaming", ".shatteredpixel", "Shattered Pixel Dungeon QoL")
        return os.path.join(home, ".shatteredpixel", "Shattered Pixel Dungeon QoL")

    def _load_config():
        path = os.path.join(_script_dir, "config.json")
        try:
            if os.path.exists(path):
                with open(path, encoding="utf-8") as f:
                    return json.load(f)
        except (json.JSONDecodeError, OSError):
            pass
        return {}

    _cfg = _load_config()
    save_dir = sys.argv[1] if len(sys.argv) > 1 else _cfg.get("save_directory", _default_save_dir())
    parser = SPDSaveParser(save_dir)
    latest = parser.find_latest_save()
    if latest:
        info = parser.get_current_game_info()
        if info:
            summary = parser.generate_summary_text(info)
            txt_path = os.path.join(_script_dir, "game_summary.txt")
            json_path = os.path.join(_script_dir, "game_summary.json")
            with open(txt_path, "w", encoding='utf-8') as f:
                f.write(summary)
            with open(json_path, "w", encoding='utf-8') as f:
                json.dump(info, f, indent=4)
            print("Saved to game_summary.txt and game_summary.json")
    else:
        print("No save files found")
