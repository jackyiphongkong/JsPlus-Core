@echo off
setlocal enabledelayedexpansion

REM ------------------------------------------------------------------
REM JsPlus-Core shared single-suite launcher - _RunOne.bat
REM Usage: _RunOne.bat <TestName> [scriptEngine]
REM   <TestName>    e.g. TestTextScript, TestSimpleJsCompressor, ...
REM   [scriptEngine] default graalvm.js
REM Flow (mirrors TestAll.bat for one suite):
REM   -1) pre-cleanup: remove JsPlus\.tmp, clear logs/* and testdata/*
REM    0) StopAll (stop leftover JsPlus/H2 JVMs)
REM    1) (re)init testdb + InitDBSyncDB + start H2 on :3306 (skipped when
REM       already listening - mirrors TestAll.bat Step 1/2)
REM    2) run <TestName>.js in a standalone JsPlus console in THIS JVM
REM       (JSPLUS_TEST_EXIT=1, stdin < NUL)
REM    3) StopAll sweep
REM   Exit with the JVM's exit code (0 = all checks passed, 1 = failure).
REM Thin wrappers (TestXxx.bat) call this file with their own TestName.
REM Override via env (same convention as TestAll.bat):
REM      JsPlus_JavaDir_JsPlus-Core   - JDK directory under Java\ (default: default)
REM      JsPlus_JvmParams_JsPlus-Core - JVM params (default: -Xms256m -Xmx1024m -Xss256k)
REM ------------------------------------------------------------------

if "%~1"=="" (
    echo Usage: %~nx0 ^<TestName^> [scriptEngine]
    exit /b 1
)
set TEST_NAME=%~1

if "%2"=="" (
    set SCRIPT_ENGINE=graalvm.js
) else (
    set SCRIPT_ENGINE=%2
)

set BIN_PATH=%~dp0
set JS_PLUS_PATH_ORIGIN=%BIN_PATH%..\JsPlus\
SET "JS_PLUS_PATH=%JS_PLUS_PATH_ORIGIN:\=/%"
set LIB_HOME=%JS_PLUS_PATH%../LibHome/
set "H2_PORT=3306"
if defined JsPlus_H2_PORT set "H2_PORT=%JsPlus_H2_PORT%"

REM ------------------------------------------------------------------
REM Step -1: pre-cleanup (align with TestAll.bat Step 0b/0c/0d)
REM ------------------------------------------------------------------
if exist "%JS_PLUS_PATH%.tmp" (
    rmdir /s /q "%JS_PLUS_PATH%.tmp"
    echo [%TEST_NAME%] Removed JsPlus\.tmp
)
if exist "%JS_PLUS_PATH_ORIGIN%..\logs" (
    del /s /q "%JS_PLUS_PATH_ORIGIN%..\logs\*" >nul 2>&1
    echo [%TEST_NAME%] Cleared logs
)
if exist "%JS_PLUS_PATH_ORIGIN%..\testdata" (
    del /s /q "%JS_PLUS_PATH_ORIGIN%..\testdata\*" >nul 2>&1
    echo [%TEST_NAME%] Removed testdata
)

REM ------------------------------------------------------------------
REM Step 0: stop leftover JsPlus/H2 JVMs first (project convention).
REM ------------------------------------------------------------------
echo [%TEST_NAME%] Stopping existing services...
call "%JS_PLUS_PATH_ORIGIN%bin\StopAll.bat"

set "DEPEND_LIBS="
for /r "%JS_PLUS_PATH_ORIGIN%..\LibHome" %%j in (*.jar) do set "DEPEND_LIBS=!DEPEND_LIBS!;%%j"
set "DEPEND_LIBS=!DEPEND_LIBS:~1!"
if not defined DEPEND_LIBS (
    echo Error: No JARs found under LibHome. Run 'ant build' first ^(produces LibHome/jsplus/default/jsplus-core-^<version^>.jar^).
    exit /b 1
)

FOR %%I IN ("%JS_PLUS_PATH%..") DO SET "AppName=%%~nxI"

SET "JavaDirEnvName=JsPlus_JavaDir_%AppName%"
call set "JAVA_DIR=%%%JavaDirEnvName%%%"
if not defined JAVA_DIR set "JAVA_DIR=default"
set "JAVA_BIN=%JS_PLUS_PATH%../Java/%JAVA_DIR%/bin/java.exe"

if not exist "%JAVA_BIN%" (
    echo WARN: Bundled Java not found at %JAVA_BIN%, falling back to system Java...
    if defined JAVA_HOME (
        set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
    ) else (
        set "JAVA_BIN=java"
    )
)

SET "JvmParamsName=JsPlus_JvmParams_%AppName%"
call set "JvmParams=%%%JvmParamsName%%%"
if "%JvmParams%"=="" set "JvmParams=-Xms256m -Xmx1024m -Xss256k"

REM ------------------------------------------------------------------
REM Step 1: H2 init + start on :3306 (skipped when already listening).
REM Mirrors TestAll.bat Step 1/2: embedded (re)init of Database/testdb via
REM StartH2Console, then the TCP server on top of it; InitDBSyncDB builds
REM the dedicated dbsync SOURCE/TARGET file DBs.
REM ------------------------------------------------------------------
netstat -ano | findstr ":%H2_PORT%" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :h2_already

echo [%TEST_NAME%] Initializing database (fallback.sql -^> change.sql)...
call "%JS_PLUS_PATH_ORIGIN%bin\StartH2Console.bat" init testdb
if errorlevel 1 (
    echo [%TEST_NAME%] ERROR: Database initialization failed.
    exit /b 1
)
call "%JS_PLUS_PATH_ORIGIN%bin\InitDBSyncDB.bat"
if errorlevel 1 (
    echo [%TEST_NAME%] ERROR: DBSync database initialization failed.
    exit /b 1
)
echo [%TEST_NAME%] Starting H2 server (port %H2_PORT%)...
call "%JS_PLUS_PATH_ORIGIN%bin\BG_StartH2.bat" %SCRIPT_ENGINE%

:h2_already
echo [%TEST_NAME%] Waiting for H2 :%H2_PORT%...
set "H2_READY="
for /l %%i in (1,1,45) do (
    netstat -ano | findstr ":!H2_PORT!" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 set "H2_READY=1"
    if defined H2_READY goto :h2_ready
    ping -n 2 127.0.0.1 >nul 2>&1
)
:h2_ready
if not defined H2_READY (
    echo [%TEST_NAME%] ERROR: H2 did not start on port %H2_PORT%.
    exit /b 1
)
echo [%TEST_NAME%] H2 ready.

REM ------------------------------------------------------------------
REM Step 2: run the single suite via _RunOne.js (mirrors JsPlus-Tomcat's
REM launcher). _RunOne.js loads the suite then hard-halts the JVM, because the
REM ScriptConsoleBase DetectSystemExit / ConfigProperties AutoLoad threads are
REM non-daemon and would otherwise keep the JVM alive after main() returns (the
REM suite itself never calls System.exit; the old direct -Task form hung here).
REM RC is the suite's failCount (0 = all checks passed, 1 = failure/load error),
REM matching the JVM exit-code convention of TestAll.bat.
REM ------------------------------------------------------------------
set "RUN_SUITE=%TEST_NAME%"
echo [%TEST_NAME%] Running %TEST_NAME%.js (via _RunOne.js) ...
%JAVA_BIN% %JvmParams% -DAPP_REMOTE_FILE="%JS_PLUS_PATH_ORIGIN%config\RemoteFile.properties" -DAPP_CLOCK="%JS_PLUS_PATH_ORIGIN%config\Clock.properties" -DAPP_IDENTITY_CARD="%JS_PLUS_PATH_ORIGIN%config\IdentityCard.properties" -DAPP_DATASOURCE="%JS_PLUS_PATH_ORIGIN%config\TestDatasource.properties" -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine "%SCRIPT_ENGINE%" -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/_RunOne.js" < NUL
set "RC=%ERRORLEVEL%"
echo [%TEST_NAME%] exit code: %RC%

REM ------------------------------------------------------------------
REM Step 3: full StopAll sweep (kills the H2 started above and any leftover
REM JsPlus JVM) so a single-suite run leaves nothing behind.
REM ------------------------------------------------------------------
echo [%TEST_NAME%] Stopping all remaining services...
call "%JS_PLUS_PATH_ORIGIN%bin\StopAll.bat"

exit /b %RC%

endlocal
