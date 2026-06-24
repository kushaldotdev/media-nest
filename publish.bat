@echo off
if "%~1"=="" (
    echo Usage: publish.bat [version]
    echo Example: publish.bat v1.0.1
    exit /b 1
)

set VERSION=%~1

echo Building APK...
call .\build.bat

echo Publishing release %VERSION% to GitHub...
set APK_NAME=medianest-%VERSION%.apk
copy .\app\build\outputs\apk\debug\app-debug.apk .\%APK_NAME%

gh release create %VERSION% .\%APK_NAME% --title "Release %VERSION%" --notes "Update to %VERSION%"

if %ERRORLEVEL% equ 0 (
    echo Successfully published %VERSION%
    del .\%APK_NAME%
) else (
    echo Failed to publish %VERSION%
    del .\%APK_NAME%
)
