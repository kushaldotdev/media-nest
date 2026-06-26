@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d D:\dev\media-nest
call .\gradlew.bat --stop
if exist .gradle\configuration-cache rmdir /s /q .gradle\configuration-cache
if "%1"=="clean" (
    echo Running clean build [Release]...
    call .\gradlew.bat clean :app:assembleRelease --no-daemon 2>&1
) else (
    echo Running build [Release] without clean...
    call .\gradlew.bat :app:assembleRelease --no-daemon 2>&1
)
