@echo off
title ShareDash — Quick Share Windows App
cd /d "%~dp0"

echo ===============================================================
echo        ShareDash: Multipath File Transfer Windows App
echo ===============================================================
echo Starting high-performance background engine...

REM Start ShareDash in background if not already running
start /b "" "dist\sharedash.exe" --port 54321

timeout /t 1 /nobreak >nul

echo Launching Quick Share Desktop App Window...

REM Try launching with Edge or Chrome in standalone App mode
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" --app=http://127.0.0.1:54321 --window-size=1180,820
) else if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" --app=http://127.0.0.1:54321 --window-size=1180,820
) else (
    start http://127.0.0.1:54321
)

echo ShareDash Windows App is running!
