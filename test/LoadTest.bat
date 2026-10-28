@echo off
setlocal enabledelayedexpansion

REM ------------------------------------------------------------------
REM JsPlus-Core load test - LoadTest.bat
REM Same flow as TestAll.bat (H2 start -> db init -> run suite -> stop H2),
REM but runs test/LoadTest.js: a database CRUD load test over the runtime
REM project datasource (Console.getDataSource() -> CacheDriverExecutor).
REM Dashboard written to <PROJECT_ROOT>/test_reports/LoadTest-Report.html
REM (format follows JsPlus-Tomcat LoadTest.js: dark cards + SVG bars +
REM percentile table).
REM Override via env:
REM      LOADTEST_ROUNDS   rounds per table, default 100 (each round = 6 ops)
REM      LOADTEST_TABLES   tables to load (1..5), default 5
REM      JsPlus_JavaDir_JsPlus-Core   - JDK directory under Java\ (default: default)
REM      JsPlus_JvmParams_JsPlus-Core - JVM params (default: -Xms256m -Xmx1024m -Xss256k)
REM      SCRIPT_ENGINE (arg %1)       - default: graalvm.js
REM ------------------------------------------------------------------

if "%1"=="" (
    set SCRIPT_ENGINE=graalvm.js
) else (
    set SCRIPT_ENGINE=%1
)

set BIN_PATH=%~dp0
set JS_PLUS_PATH_ORIGIN=%BIN_PATH%..\JsPlus\
SET "JS_PLUS_PATH=%JS_PLUS_PATH_ORIGIN:\=/%"
set LIB_HOME=%JS_PLUS_PATH%../LibHome/

REM ------------------------------------------------------------------
REM Step 0: stop leftover JsPlus/H2 JVMs first (project convention: every
REM build/test script runs StopAll up front) so a stale H2 ghost, a locked
REM testdb file or a JVM pinning an old JAR cannot skew the load metrics
REM below.
REM ------------------------------------------------------------------
call "%JS_PLUS_PATH_ORIGIN%bin\StopAll.bat"

set "DEPEND_LIBS="
for /r "%JS_PLUS_PATH_ORIGIN%..\LibHome" %%j in (*.jar) do set "DEPEND_LIBS=!DEPEND_LIBS!;%%j"
set "DEPEND_LIBS=!DEPEND_LIBS:~1!"
if not defined DEPEND_LIBS (
    echo Error: No JARs found under LibHome. Run 'ant build' first ^(produces LibHome/jsplus/default/jsplus-core-^<version^>.jar^).
    exit /b 1
)
set "CORE_JAR="
for %%j in ("%JS_PLUS_PATH_ORIGIN%..\LibHome\jsplus\default\jsplus-core-*.jar") do set "CORE_JAR=%%j"
if not defined CORE_JAR (
    echo WARN: LibHome/jsplus/default/jsplus-core-*.jar not found. Run 'ant build' first.
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

echo "%JvmParamsName%"

if "%JvmParams%"=="" (
    SET "JvmParams=-Xms256m -Xmx1024m -Xss256k"
    echo WARN: Missing %JvmParamsName% then using default params
) else (
    echo Using System Env JVM Params
)

echo Using "%JvmParams%"

REM ------------------------------------------------------------------
REM Step 1: H2 server on :3306 via BG_StartH2 (skipped when already up).
REM ------------------------------------------------------------------
netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :h2_already
echo [LoadTest] Starting H2 server (port 3306)...
call "%JS_PLUS_PATH_ORIGIN%bin\BG_StartH2.bat" %SCRIPT_ENGINE%

:h2_already
echo [LoadTest] Waiting for H2 :3306...
set "H2_READY="
for /l %%i in (1,1,45) do (
    netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 set "H2_READY=1"
    if defined H2_READY goto :h2_ready
    ping -n 2 127.0.0.1 >nul 2>&1
)
:h2_ready
if not defined H2_READY (
    echo [LoadTest] ERROR: H2 did not start on port 3306.
    exit /b 1
)
echo [LoadTest] H2 ready.

REM ------------------------------------------------------------------
REM Step 2: (Re)initialize the test database (fallback.sql -> change.sql).
REM ------------------------------------------------------------------
echo [LoadTest] Initializing database (fallback.sql -^> change.sql)...
%JAVA_BIN% %JvmParams% -cp "%DEPEND_LIBS%" org.h2.tools.RunScript -url "jdbc:h2:tcp://localhost:3306/testdb" -user sa -script "%JS_PLUS_PATH_ORIGIN%..\dbscript\fallback.sql" -checkResults
if errorlevel 1 (
    echo [LoadTest] ERROR: fallback.sql failed.
    exit /b 1
)
%JAVA_BIN% %JvmParams% -cp "%DEPEND_LIBS%" org.h2.tools.RunScript -url "jdbc:h2:tcp://localhost:3306/testdb" -user sa -script "%JS_PLUS_PATH_ORIGIN%..\dbscript\change.sql" -checkResults
if errorlevel 1 (
    echo [LoadTest] ERROR: change.sql failed.
    exit /b 1
)
echo [LoadTest] testdb initialized (ghost_remote_command + 5 test tables).

REM ------------------------------------------------------------------
REM Step 3: Run LoadTest.js against this JVM.
REM JSPLUS_TEST_EXIT=1 tells TestAll.js/LoadTest.js to System.exit(0) after
REM finishing. "< NUL" closes stdin so the JsPlus Main scanner gets EOF right
REM after the -Task completes and the JVM exits cleanly (Main.java).
REM ------------------------------------------------------------------
set JSPLUS_TEST_EXIT=1
%JAVA_BIN% %JvmParams% -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine "%SCRIPT_ENGINE%" -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/LoadTest.js" < NUL
set "RC=%ERRORLEVEL%"
echo LoadTest exit code: %RC%
REM Step 4: stop the H2 server started for this run (java ghost keyed by
REM script/StartH2.js) so LoadTest leaves nothing behind. RC was captured
REM above. StopAll.bat was already run up front as Step 0; this step only
REM recycles this run's H2 ghost precisely.
powershell -NoProfile -Command "$kw='script/StartH2.js'; $procs=Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -like \"*$kw*\" }; if($procs){ $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }" >nul 2>&1
exit /b %RC%

endlocal
