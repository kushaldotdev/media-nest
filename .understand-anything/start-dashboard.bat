@echo off
title Understand Anything Dashboard - MediaNest
echo Starting dashboard server...

:: Resolve project root dynamically using batch script folder (%~dp0)
set "GRAPH_DIR=%~dp0.."

:: Resolve dashboard installation directory dynamically using %USERPROFILE%
set "DASHBOARD_DIR="
if exist "%USERPROFILE%\.understand-anything-plugin\packages\dashboard" (
    set "DASHBOARD_DIR=%USERPROFILE%\.understand-anything-plugin\packages\dashboard"
) else if exist "%USERPROFILE%\.understand-anything\repo\understand-anything-plugin\packages\dashboard" (
    set "DASHBOARD_DIR=%USERPROFILE%\.understand-anything\repo\understand-anything-plugin\packages\dashboard"
)

if "%DASHBOARD_DIR%"=="" (
    echo Error: Could not find Understand Anything plugin under %USERPROFILE%
    echo Please verify the plugin installation.
    pause
    exit /b 1
)

:: Resolve real physical path of DASHBOARD_DIR using Node.js to prevent Vite symlink resolution bugs
for /f "usebackq delims=" %%i in (`node -e "console.log(require('fs').realpathSync(process.env.DASHBOARD_DIR))"`) do set "REAL_DIR=%%i"

set UNDERSTAND_ACCESS_TOKEN=medianest_token_2026

echo Graph Directory: %GRAPH_DIR%
echo Dashboard Directory: %REAL_DIR%

cd /d "%REAL_DIR%"
start /b npx vite --host 127.0.0.1
echo Waiting 2 seconds for server to start...
timeout /t 2 /nobreak >nul
echo Opening dashboard in your browser...
start "" "http://127.0.0.1:5173/?token=medianest_token_2026"
echo.
echo Dashboard is running in the background. Close this terminal to stop the server.
echo.
