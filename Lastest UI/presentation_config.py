"""
Sound / GDI presentation hints for meta commands.

!fard / !summon visuals live on the Godot companion. OBS source show/hide
(GROUP - Farder, TEXT - farder, GROUP - Summoner) is retired.
"""
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

FARD_SOUND = (
    r"C:\Users\dalto\Documents\Sounds\fart-with-reverb.mp3"
)
FARD_SOUND_VOLUME = 1.22  # 122% in Streamer.bot

# !summon — played by ParseChatResponse.cs (kind=summon), not R9 fard action.
# Use a full path Streamer.bot can open (same style as mimic / fard in your export).
SUMMON_SOUND = (
    r"C:\Users\dalto\Documents\My Games\SPD assets\assets\sounds\teleport.mp3"
)
SUMMON_SOUND_VOLUME = 0.8  # 80%

# GDI / text files written relative to Lastest UI/
TOTALFARD_FILE = os.path.join(SCRIPT_DIR, "totalfard.txt")
DOUBLE_POINTS_COUNTDOWN_FILE = os.path.join(SCRIPT_DIR, "double_points_countdown.txt")

# Default presentation payload for !fard (streaming-system-rework-plan.md API)
FARD_PRESENTATION = {
    "kind": "fard",
    "sound": FARD_SOUND,
    "sound_volume": FARD_SOUND_VOLUME,
    "gdi": {"file": "totalfard.txt", "increment": 1},
    "flash_ms": 4000,
}

# Default presentation payload for !summon (sound only — Godot handles the march visual).
SUMMON_PRESENTATION = {
    "kind": "summon",
    "sound": SUMMON_SOUND,
    "sound_volume": SUMMON_SOUND_VOLUME,
}


