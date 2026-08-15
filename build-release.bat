@echo off
setlocal
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d %~dp0
for %%i in ("%~dp0.") do set "SCRIPT_DIR=%%~fi"

set "CLEAN="
if "%1"=="clean" set "CLEAN=-Clean"

set "NOPAUSE="
if "%1"=="-nopause" set "NOPAUSE=1"
if "%2"=="-nopause" set "NOPAUSE=1"

echo ============================================
echo  Building [Release] APK  -  live progress below
echo ============================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%\scripts\build.ps1" -Task ":app:assembleRelease" %CLEAN% -ScriptDir "%SCRIPT_DIR%" -ApkSource "%SCRIPT_DIR%\app\build\outputs\apk\release\app-release.apk" -ApkDestination "%SCRIPT_DIR%\dist\release\app-release.apk"
set EXIT_CODE=%ERRORLEVEL%

echo.
if not "%EXIT_CODE%"=="0" (
    echo BUILD FAILED with exit code %EXIT_CODE%. Full log: %SCRIPT_DIR%\build-release.log
) else (
    echo BUILD SUCCESSFUL. Full log: %SCRIPT_DIR%\build-release.log
)
if not defined NOPAUSE (
    echo.
    pause
)
exit /b %EXIT_CODE%
