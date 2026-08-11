@echo off
setlocal
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d D:\dev\media-nest
set BUILD_LOG=D:\dev\media-nest\build.log
echo Running build [Debug]... (log: %BUILD_LOG%)
call .\gradlew.bat --stop >nul 2>&1
if exist .gradle\configuration-cache rmdir /s /q .gradle\configuration-cache
if "%1"=="clean" (
    echo Running clean build [Debug]...
    call .\gradlew.bat clean :app:assembleDebug --no-daemon > "%BUILD_LOG%" 2>&1
) else (
    echo Running build [Debug] without clean...
    call .\gradlew.bat :app:assembleDebug --no-daemon > "%BUILD_LOG%" 2>&1
)
set EXIT_CODE=%ERRORLEVEL%
echo.
echo ================= BUILD LOG (also saved to %BUILD_LOG%) =================
type "%BUILD_LOG%"
echo ================= END BUILD LOG =================
if not "%EXIT_CODE%"=="0" (
    echo.
    echo BUILD FAILED with exit code %EXIT_CODE%. Full log: %BUILD_LOG%
) else (
    echo.
    echo BUILD SUCCESSFUL. Full log: %BUILD_LOG%
)
exit /b %EXIT_CODE%
