@echo off
REM Launcher: .ps1 files often open in Notepad from Explorer/cmd.
REM Usage: test_all_commands.cmd -Mode Api -Section Free
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test_all_commands.ps1" %*
exit /b %ERRORLEVEL%
