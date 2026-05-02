@echo off
REM Runs the desktop fat JAR with JDK 17; no console window (start + javaw).
REM If the JAR name changes, update appVersionName in build.gradle and this filename.
start "" "C:\Program Files\Java\jdk-17\bin\javaw.exe" -jar "%~dp0desktop\build\libs\desktop-3.3.7.jar"
