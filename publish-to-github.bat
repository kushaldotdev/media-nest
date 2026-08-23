@echo off
setlocal
set NOPAUSE=0
if /i "%~1"=="-nopause" (set NOPAUSE=1 && shift)
if /i "%~1"=="-nopause" (set NOPAUSE=1 && shift)

echo ============================================
echo  Publishing Media Nest to GitHub
echo  Version argument: "%~1"
echo ============================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-to-github.ps1" "%~1"
set EXIT_CODE=%ERRORLEVEL%
echo.
if not "%EXIT_CODE%"=="0" (
    echo PUBLISH FAILED with exit code %EXIT_CODE%.
) else (
    echo PUBLISH SUCCESSFUL.
)
if "%NOPAUSE%"=="0" pause
exit /b %EXIT_CODE%
