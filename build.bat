@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d D:\dev\media-nest
if exist .gradle\configuration-cache rmdir /s /q .gradle\configuration-cache
.\gradlew.bat :app:assembleDebug 2>&1
