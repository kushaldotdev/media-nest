@echo off
setlocal
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
pause
exit /b %EXIT_CODE%
