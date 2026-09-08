@echo off
setlocal
cd /d "%~dp0"
rem Use the current user's permissions. Run as administrator explicitly only when required.
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\launch_agent.ps1"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo.
  echo [ERROR] Kiyori PC Agent launcher failed with code %EXIT_CODE%.
  pause
)
exit /b %EXIT_CODE%
