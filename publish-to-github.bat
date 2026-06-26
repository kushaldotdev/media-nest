@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-to-github.ps1" "%~1"
pause

