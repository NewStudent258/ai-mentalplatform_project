@echo off
chcp 65001 >nul
setlocal

REM ============================================================================
REM  Creates desktop shortcuts for the project launcher.
REM
REM  Usage:  install-shortcuts.bat
REM
REM  Creates two shortcuts on the Desktop:
REM    "Start Mental Health AI"   - launches backend + frontend
REM    "Stop Mental Health AI"    - stops them
REM
REM  Re-run this any time (e.g. after moving the project folder) to refresh
REM  the shortcuts with the new path.
REM ============================================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

if not exist "%ROOT%\start.bat" (
    echo  [X] Cannot find start.bat next to this script.
    echo      Expected: %ROOT%\start.bat
    echo.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\install-shortcuts.ps1"
set "RC=%ERRORLEVEL%"

echo.
if not "%RC%"=="0" (
    echo  [X] Failed to create shortcuts. Exit code: %RC%
) else (
    echo  Tip: if you move the project folder, re-run this script
    echo       to point the shortcuts at the new location.
)
echo.
pause
exit /b %RC%
