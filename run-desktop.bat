@echo off
REM Runs the desktop fat JAR with JDK 17; no console window (start + javaw).
REM JAR name follows appVersionName in build.gradle (currently 4.0.2-QoL-10).
REM Rebuild: gradlew.bat desktop:release
REM For stream telemetry game_log: enable Streaming in SPD settings.

set "JAR=%~dp0desktop\build\libs\desktop-4.0.2-QoL-10.jar"
set "JAVA=C:\Program Files\Java\jdk-17\bin\javaw.exe"

if not exist "%JAR%" (
    echo Missing JAR: %JAR%
    echo Run: gradlew.bat desktop:release
    pause
    exit /b 1
)
if not exist "%JAVA%" (
    echo Missing Java: %JAVA%
    pause
    exit /b 1
)

start "" "%JAVA%" -jar "%JAR%"
