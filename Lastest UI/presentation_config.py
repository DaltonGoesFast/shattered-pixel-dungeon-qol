"""
OBS / sound / GDI presentation hints for meta commands.

Phase 0: canonical names confirmed from docs/fard-system.md and
streamerbot-fard-rework-apply.md. Streamer.bot Action 9 maps logical
keys in API `presentation.obs` to these OBS source paths.

API responses use short logical keys; Action 9 resolves them via OBS_SOURCES.
"""
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Logical key → full OBS source path (scene :: source)
OBS_SOURCES = {
    "GROUP_Farder_H": "HUD :: GROUP - Farder",
    "GROUP_Farder_V": "V - HUD :: GROUP - Farder",
    "TEXT_Farder_H": "HUD :: TEXT - farder",
    "TEXT_Farder_V": "V - HUD :: TEXT - farder",
    # Optional summon flash (streamerbot-summon-march-apply.md)
    "GROUP_Summoner_H": "HUD :: GROUP - Summoner",
    "GROUP_Summoner_V": "V - HUD :: GROUP - Summoner",
}

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
    "obs": ["GROUP_Farder_H", "GROUP_Farder_V"],
    "sound": FARD_SOUND,
    "sound_volume": FARD_SOUND_VOLUME,
    "gdi": {"file": "totalfard.txt", "increment": 1},
    "obs_gdi_text": ["TEXT_Farder_H", "TEXT_Farder_V"],
    "flash_ms": 4000,
}

# Default presentation payload for !summon (sound only — Godot handles the march visual).
SUMMON_PRESENTATION = {
    "kind": "summon",
    "sound": SUMMON_SOUND,
    "sound_volume": SUMMON_SOUND_VOLUME,
}


def resolve_obs_keys(keys: list[str]) -> list[str]:
    """Map logical presentation keys to OBS source paths."""
    return [OBS_SOURCES.get(k, k) for k in keys]
