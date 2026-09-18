@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

REM ============================================================================
REM  Mental Health AI Platform - One-click launcher
REM
REM  Usage:
REM    start.bat            Start everything (deps check + backend + frontend)
REM    start.bat backend    Start backend only
REM    start.bat frontend   Start frontend only
REM    start.bat check      Run environment check only, do not start
REM    start.bat stop       Stop backend and frontend started by this script
REM
REM  First run: copy .env.example to .env and fill in your API keys.
REM ============================================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "BACKEND_DIR=%ROOT%\ai-spingboot"
set "FRONTEND_DIR=%ROOT%\ai-vue"
set "RUN_DIR=%ROOT%\.run"
set "LOG_DIR=%ROOT%\.run\logs"

REM Project requires JDK 17+. Force JDK 17 when present, since JAVA_HOME may be 21.
if exist "C:\Program Files\Java\jdk-17.0.29" set "JAVA_HOME=C:\Program Files\Java\jdk-17.0.29"
if exist "C:\Program Files\Java\jdk-17.0.19" set "JAVA_HOME=C:\Program Files\Java\jdk-17.0.19"
if exist "C:\Program Files\Java\jdk-17" set "JAVA_HOME=C:\Program Files\Java\jdk-17"
if exist "C:\Program Files\Eclipse Adoptium\jdk-17" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17"
if exist "C:\Program Files\Microsoft\jdk-17" set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17"

if not exist "%RUN_DIR%" mkdir "%RUN_DIR%" >nul 2>&1
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

set "MODE=%~1"
if "%MODE%"=="" set "MODE=all"

REM Started from a desktop shortcut (double-click) - keep the window open at the
REM end so the user can read the result. When called from a script, stay quiet.
if /i "%MH_KEEP_OPEN%"=="1" set "KEEP_OPEN=1"

if /i "%MODE%"=="stop" goto :DO_STOP
if /i "%MODE%"=="check" goto :DO_CHECK
if /i "%MODE%"=="backend" goto :DO_BACKEND_ONLY
if /i "%MODE%"=="frontend" goto :DO_FRONTEND_ONLY

call :LOAD_ENV
call :BANNER
call :CHECK_DEPS
if errorlevel 1 goto :FAIL_DEPS
call :CHECK_KEYS
if errorlevel 1 goto :FAIL_KEYS
call :START_BACKEND
call :WAIT_BACKEND
if errorlevel 1 goto :FAIL_BACKEND
call :START_FRONTEND
goto :DONE

:DO_BACKEND_ONLY
call :LOAD_ENV
call :BANNER
call :CHECK_DEPS
if errorlevel 1 goto :FAIL_DEPS
call :CHECK_KEYS
if errorlevel 1 goto :FAIL_KEYS
call :START_BACKEND
call :WAIT_BACKEND
if errorlevel 1 goto :FAIL_BACKEND
goto :DONE

:DO_FRONTEND_ONLY
call :BANNER
call :START_FRONTEND
goto :DONE

:DO_CHECK
call :LOAD_ENV
call :BANNER
call :CHECK_DEPS
if errorlevel 1 goto :FAIL_DEPS
call :CHECK_KEYS
echo.
echo   [OK] Environment check passed.
echo.
pause
exit /b 0

REM ============================================================================
REM  Load .env
REM ============================================================================
:LOAD_ENV
if not exist "%ROOT%\.env" (
    echo   [NOTE] No .env found - using system environment variables.
    echo          Copy .env.example to .env to pin your config.
    echo.
    exit /b 0
)
for /f "usebackq tokens=1,* delims==" %%a in ("%ROOT%\.env") do (
    set "K=%%a"
    set "V=%%b"
    if not "!K!"=="" if not "!K:~0,1!"=="#" (
        set "V=!V:"=!"
        for /f "tokens=* delims= " %%x in ("!V!") do set "V=%%x"
        if not "!V!"=="" set "!K!=!V!"
    )
)
exit /b 0

REM ============================================================================
REM  Banner
REM ============================================================================
:BANNER
echo.
echo  ============================================================
echo    Mental Health AI Platform  -  Launcher
echo  ============================================================
echo    Backend   http://localhost:1236
echo    Frontend  http://localhost:5173
echo    Logs      .run\logs
echo  ============================================================
echo.
exit /b 0

REM ============================================================================
REM  Dependency check: MySQL 3306 / Redis 6379 / RabbitMQ 5672
REM ============================================================================
:CHECK_DEPS
echo  [1/4] Checking dependency services...
set "DEP_FAIL="
call :PROBE_PORT 3306 MySQL
call :PROBE_PORT 6379 Redis-Memurai
call :PROBE_PORT 5672 RabbitMQ
if defined DEP_FAIL (
    echo.
    echo   [X] Not ready:
    echo !DEP_FAIL!
    echo.
    echo   How to fix:
    echo     MySQL    : net start mysql
    echo     Redis    : Memurai runs as a Windows service - use services.msc
    echo     RabbitMQ : install Erlang first, then  net start RabbitMQ
    echo.
    exit /b 1
)
echo        All dependency services are up.
echo.
exit /b 0

:PROBE_PORT
netstat -ano | findstr ":%1 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 (
    set "DEP_FAIL=!DEP_FAIL!       - %2 port %1 not listening"
    exit /b 0
)
echo        [OK] %2 - port %1
exit /b 0

REM ============================================================================
REM  API key check
REM ============================================================================
:CHECK_KEYS
echo  [2/4] Checking API keys...
set "KEY_MISSING="
if "%DEEPSEEK_API_KEY%"=="" set "KEY_MISSING=1"
if "%DASHSCOPE_API_KEY%"=="" set "KEY_MISSING=1"
if defined KEY_MISSING (
    echo        [X] Missing:
    if "%DEEPSEEK_API_KEY%"=="" echo              DEEPSEEK_API_KEY   chat and tool calling
    if "%DASHSCOPE_API_KEY%"=="" echo              DASHSCOPE_API_KEY  knowledge embedding
    echo.
    echo        These are REQUIRED - the app cannot start without them.
    echo        Spring AI builds the chat model during startup and fails hard
    echo        when the key is empty, so "continue anyway" is not an option.
    echo.
    echo        How to fix:
    echo          1. Copy  .env.example  to  .env
    echo          2. Fill in the two keys
    echo          3. Run the launcher again
    echo.
    exit /b 1
) else (
    echo        [OK] DEEPSEEK_API_KEY
    echo        [OK] DASHSCOPE_API_KEY
)
echo.
exit /b 0

REM ============================================================================
REM  Start backend
REM ============================================================================
:START_BACKEND
echo  [3/4] Starting backend...
if not exist "%BACKEND_DIR%\pom.xml" (
    echo        [X] Cannot find pom.xml in %BACKEND_DIR%
    exit /b 1
)
if exist "%RUN_DIR%\backend.pid" (
    set /p OLD_PID=<"%RUN_DIR%\backend.pid"
    tasklist /fi "PID eq !OLD_PID!" 2>nul | findstr "!OLD_PID!" >nul && (
        echo        [WARN] Backend already running, PID !OLD_PID!, skipped.
        echo            Run  start.bat stop  first to restart.
        exit /b 0
    )
)
cd /d "%BACKEND_DIR%"
REM Truncate the log first: the readiness check greps for a startup marker,
REM and a stale marker from a previous run would cause a false positive.
if exist "%LOG_DIR%\backend.log" del "%LOG_DIR%\backend.log" >nul 2>&1
REM Launch detached: "start /b" keeps it in the background without a new console
REM (a new console would inherit stdin and block callers), and ">nul 2>&1 <nul"
REM fully detaches the child's stdio so the parent can exit immediately.
start "MH-Backend" /b cmd /c "mvn spring-boot:run > ""%LOG_DIR%\backend.log"" 2>&1 <nul"
echo        [OK] Backend starting (log: .run\logs\backend.log)
echo.
exit /b 0

REM ============================================================================
REM  Wait for backend to become ready (poll port 1236, max 180s)
REM ============================================================================
:WAIT_BACKEND
echo        Waiting for backend (first build may take a while, max 180s)...
set /a WAITED=0
:WAIT_LOOP
REM Prefer the log marker: it proves the app finished starting, not just that
REM some process grabbed the port. Fall back to a port probe as well.
findstr /c:"Started AiSpingbootApplication" "%LOG_DIR%\backend.log" >nul 2>&1
if not errorlevel 1 (
    echo.
    echo        [OK] Backend ready at http://localhost:1236
    echo.
    exit /b 0
)
netstat -ano | findstr ":1236 " | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo.
    echo        [OK] Backend ready at http://localhost:1236
    echo.
    exit /b 0
)
REM Fail fast instead of waiting the full 180s: if Maven already reported
REM BUILD FAILURE, the app is dead and will never open the port.
findstr /c:"BUILD FAILURE" "%LOG_DIR%\backend.log" >nul 2>&1
if not errorlevel 1 (
    echo.
    echo        [X] Backend failed to start. Reason:
    echo.
    call :SHOW_BACKEND_ERROR
    echo.
    echo        Full log: %LOG_DIR%\backend.log
    echo.
    exit /b 1
)
REM "ping" instead of "timeout": timeout fails when stdin is redirected,
REM which would turn this loop into a busy-wait.
ping -n 4 127.0.0.1 >nul 2>&1
set /a WAITED+=3
if !WAITED! GEQ 180 (
    echo.
    echo        [X] Backend not ready within 180 seconds.
    echo            Check the log: %LOG_DIR%\backend.log
    echo.
    call :SHOW_BACKEND_ERROR
    echo.
    exit /b 1
)
<nul set /p "=."
goto :WAIT_LOOP

REM ---------------------------------------------------------------------------
REM  Pull the most useful error line out of the Maven log so the user does not
REM  have to dig through 80 lines of Maven output to find the real cause.
REM ---------------------------------------------------------------------------
:SHOW_BACKEND_ERROR
set "FOUND="
REM 1) The most common and actionable case: a missing API key.
findstr /c:"API key must be set" "%LOG_DIR%\backend.log" >nul 2>&1
if not errorlevel 1 (
    echo          Missing API key. The app needs both DEEPSEEK_API_KEY and
    echo          DASHSCOPE_API_KEY at startup.
    echo          Fix: copy .env.example to .env, fill in the keys, rerun.
    set "FOUND=1"
)
REM 2) Port already taken.
findstr /c:"Port 1236 was already in use" "%LOG_DIR%\backend.log" >nul 2>&1
if not errorlevel 1 (
    echo          Port 1236 is already in use by another process.
    echo          Fix: run the Stop shortcut first, or  start.bat stop
    set "FOUND=1"
)
REM 3) Database / broker connectivity.
findstr /c:"Communications link failure" "%LOG_DIR%\backend.log" >nul 2>&1
if not errorlevel 1 (
    echo          Cannot reach MySQL. Is the MySQL service running?
    set "FOUND=1"
)
findstr /c:"Connection refused" "%LOG_DIR%\backend.log" >nul 2>&1
if not errorlevel 1 (
    echo          Connection refused - a dependency service is not listening
    echo          ^(check MySQL 3306, Redis 6379, RabbitMQ 5672^).
    set "FOUND=1"
)
REM 4) Fallback: surface the first Caused by line verbatim.
if not defined FOUND (
    echo          ^(showing the root cause from the log^)
    for /f "tokens=1,* delims=:" %%a in ('findstr /c:"Caused by" "%LOG_DIR%\backend.log"') do (
        if not defined SHOWN (
            echo          %%b
            set "SHOWN=1"
        )
    )
)
exit /b 0

REM ============================================================================
REM  Start frontend
REM ============================================================================
:START_FRONTEND
echo  [4/4] Starting frontend...
if not exist "%FRONTEND_DIR%\package.json" (
    echo        [X] Cannot find package.json in %FRONTEND_DIR%
    exit /b 1
)
if not exist "%FRONTEND_DIR%\node_modules" (
    echo        [WARN] node_modules missing - running npm install ...
    cd /d "%FRONTEND_DIR%"
    call npm.cmd install
    if errorlevel 1 (
        echo        [X] npm install failed
        exit /b 1
    )
)
cd /d "%FRONTEND_DIR%"
start "MH-Frontend" /b cmd /c "npm.cmd run dev > ""%LOG_DIR%\frontend.log"" 2>&1 <nul"
echo        [OK] Frontend starting (log: .run\logs\frontend.log)
echo.
exit /b 0

REM ============================================================================
REM  Stop
REM ============================================================================
:DO_STOP
echo.
echo  Stopping backend and frontend...
REM With "start /b" there is no window title to match, so stop by port owner
REM (which is the actual listening process) instead of by window.
call :KILL_PORT 1236 "backend"
call :KILL_PORT 5173 "frontend"
if exist "%RUN_DIR%\backend.pid" del "%RUN_DIR%\backend.pid" >nul 2>&1
echo.
echo  Done. Note: MySQL / Redis / RabbitMQ are system services and were left running.
echo.
if defined KEEP_OPEN pause
exit /b 0

:KILL_PORT
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%~1 " ^| findstr "LISTENING"') do (
    taskkill /pid %%p /t /f >nul 2>&1
    if not errorlevel 1 echo        [OK] Stopped %~2 - PID %%p
)
exit /b 0

REM ============================================================================
REM  Finish / failure
REM ============================================================================
:DONE
echo  ============================================================
echo    Startup complete
echo  ============================================================
echo    Frontend  http://localhost:5173
echo    Backend   http://localhost:1236
echo.
echo    To stop:  double-click the "Stop" shortcut
echo              (or run  start.bat stop)
echo  ============================================================
echo.

REM Give the frontend a moment, then open it in the default browser.
if /i not "%MH_NO_BROWSER%"=="1" (
    echo  Opening http://localhost:5173 ...
    ping -n 3 127.0.0.1 >nul 2>&1
    start "" "http://localhost:5173"
    echo.
)

if defined KEEP_OPEN (
    echo  This window can stay open - it is not running the services.
    echo  Closing it will NOT stop the app.
    echo.
    pause
)
exit /b 0

:FAIL_DEPS
echo  Aborted: dependency services are not ready.
echo.
if defined KEEP_OPEN pause
exit /b 1

:FAIL_KEYS
echo  Aborted: API keys are missing - see the instructions above.
echo.
if defined KEEP_OPEN pause
exit /b 1

:FAIL_BACKEND
echo  Aborted: backend did not become ready.
echo.
if defined KEEP_OPEN pause
exit /b 1
