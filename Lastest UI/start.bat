@echo off
cd /d "%~dp0"

echo ================================================
echo   SPD Overlay Server
echo ================================================
echo.
echo Checking Python installation...
python --version
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python from https://www.python.org/
    pause
    exit /b 1
)

echo.
echo Installing dependencies...
pip install -r requirements.txt

echo.
echo ================================================
echo Starting SPD Overlay Server...
echo ================================================
echo.
echo Overlay:     http://localhost:5000
echo Game WS:    ws://127.0.0.1:5001 (enable in game Settings)
echo OBS relay:  item_info_open / item_info_closed -^> Advanced Scene Switcher
echo.
echo Add http://localhost:5000 as a Browser Source in OBS
echo.
echo Press Ctrl+C to stop the server
echo ================================================
echo.

python server.py

pause
