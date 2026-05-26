@echo off
REM Stream Deck: open interactive give-item prompt (edit OVERLAY_DIR if needed)
set "OVERLAY_DIR=C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI"
REM Use START /D — do not nest cd inside cmd /k quotes (breaks Instant Batch plugin temp scripts)
start "SPD Give Item" /D "%OVERLAY_DIR%" cmd /k python streamer_give_prompt.py
