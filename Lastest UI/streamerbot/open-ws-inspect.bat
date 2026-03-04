@echo off
REM Open main control page (config, viewer points, game inspector). Overlay server must be running (start.bat).
start "" "http://localhost:5000"
(del "%~f0") & exit
