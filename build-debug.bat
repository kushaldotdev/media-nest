@echo off
setlocal
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d D:\dev\media-nest

set "CLEAN="
if "%1"=="clean" set "CLEAN=-Clean"

set "NOPAUSE="
if "%1"=="-nopause" set "NOPAUSE=1"
if "%2"=="-nopause" set "NOPAUSE=1"

echo ============================================
echo  Building [Debug] APK  -  live progress below
echo ============================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" -Task ":app:assembleDebug" %CLEAN% -ApkSource "D:\dev\media-nest\app\build\outputs\apk\debug\app-debug.apk" -ApkDestination "D:\dev\media-nest\dist\debug\app-debug.apk" -LogPath "D:\dev\media-nest\build-debug.log"
set EXIT_CODE=%ERRORLEVEL%

echo.
if not "%EXIT_CODE%"=="0" (
    echo BUILD FAILED with exit code %EXIT_CODE%. Full log: build-debug.log
) else (
    echo BUILD SUCCESSFUL. Full log: build-debug.log
)
if not defined NOPAUSE (
    echo.
    pause
)
exit /b %EXIT_CODE%
