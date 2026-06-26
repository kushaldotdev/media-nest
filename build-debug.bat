@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d D:\dev\media-nest
call .\gradlew.bat --stop
if exist .gradle\configuration-cache rmdir /s /q .gradle\configuration-cache
if "%1"=="clean" (
    echo Running clean build [Debug]...
    call .\gradlew.bat clean :app:assembleDebug --no-daemon 2>&1
) else (
    echo Running build [Debug] without clean...
    call .\gradlew.bat :app:assembleDebug --no-daemon 2>&1
)
