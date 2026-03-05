# Shattered Pixel Dungeon Text Overlay

A lightweight, real-time text-only overlay for Shattered Pixel Dungeon. It automatically parses your save files and generates a beautiful ASCII game summary that you can use as a Browser Source in OBS.

## Features

✨ **Real-time Updates** - Updates automatically whenever you save your game.  
📊 **Comprehensive Stats** - Hero level, HP, Strength, Buffs, Depth, Gold, and Score.  
🗡️ **Detailed Combat** - Tracking for Sneak attacks, Thrown assists, and Hazard assists.  
🎒 **Full Inventory** - Complete list of carried items with quantities and levels.  
🏆 **Challenges & Quests** - See active challenges and regional quest progress at a glance.  
🌿 **Talent Breakdown** - Specific talent names and levels invested in each tier.  
🤖 **JSON Export** - Automatically generates `game_summary.json` for integration with other projects.

## Getting Started

1. **Install Python** - Ensure [Python](https://www.python.org/downloads/) is installed and added to your PATH.
2. **Configure (Optional)** - Open `config.txt` to adjust your save file location if it's not in the default directory.
3. **Launch** - Double-click `start.bat`. This will:
   - Check your Python installation.
   - Install required libraries (from `requirements.txt`).
   - Start the local server.

## Using in OBS

1. Open **OBS Studio**.
2. Add a new **Browser Source**.
3. Set the URL to: `http://localhost:5000`
4. Set the Width/Height as needed (e.g., 400x800).
5. The overlay will automatically refresh as you play.

## Using JSON Data

If you want to use the game data in another project, you can:
- Read `game_summary.json` directly from the project folder.
- Fetch it from the local server at: `http://localhost:5000/game_summary.json`

## Project Structure

- `start.bat`: The primary launch script.
- `server.py`: The local web server.
- `spd_parser.py`: The core logic that decodes and summarizes game saves.
- `index.html`: The clean, dark-themed dashboard frontend.
- `game_summary.txt`: The human-readable text output of your current game.
- `game_summary.json`: The machine-readable JSON output for external projects.
- `streamerbot/`: Streamer.bot exports and batch helpers (e.g. `open-ws-inspect.bat`).

## Backups
Your previous work and graphical overlay components are available in the `backup` and `backup_20251201` folders.
