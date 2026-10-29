@echo off
setlocal enabledelayedexpansion

REM ------------------------------------------------------------------
REM JsPlus-Core datasource pool test runner - TestDatasource.bat
REM Flow:
REM   1) (Re)initialize the test database: apply dbscript/fallback.sql
REM      (DROP all) then dbscript/change.sql (CREATE all) to the embedded
REM      H2 file database <PROJECT_ROOT>/Database/testdb, then start the
REM      H2 TCP server on :3306 (same init + server steps as TestAll.bat,
REM      skipped when :3306 is already listening).
REM   2) Build the runtime classpath from every JAR under
REM      <PROJECT_ROOT>/LibHome (including jsplus-core-<version>.jar from
REM      'ant build').
REM   3) Run TestDatasource.js in a standalone JsPlus console in THIS JVM
REM      with -DAPP_DATASOURCE=<PROJECT_ROOT>/JsPlus/config/TestDatasource.properties:
REM      the runtime Console.getDataSource() (ScriptConsole) loads the DEDICATED
REM      second-scale pool configuration (IdleTimeout=5s / Timeout=8s /
REM      TransactionTimeout=8s / LifeCycle=12s / MaxPoolSize=10) so the
REM      idle / using / abort / timeout behaviour cases (C10-C13) and the
REM      pool-capacity case (C8) finish quickly - the production
REM      config/DataSource.properties is never touched.
REM   4) Exit with the JVM's exit code (0 = all checks passed, 1 = failure).
REM Override via env (same convention as JsPlus-Tomcat / TestAll.bat):
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
REM testdb file or a JVM pinning an old JAR cannot interfere with the pool
REM cases below (they depend on a clean idle/using/abort state).
REM NOTE: In the TestAll flow (TESTALL_RUN=1) this is SKIPPED on purpose -
REM TestAll.bat started the shared H2 server and Step 3c is designed to
REM REUSE it (see TestAll.bat Step 3c comment). Running StopAll here kills
REM that stable H2 and forces this script to restart one from scratch -
REM the CI #43/#44 "Connection refused on :3306" failure mode. Standalone
REM runs keep the full StopAll sweep.
REM ------------------------------------------------------------------
if not defined TESTALL_RUN call "%JS_PLUS_PATH_ORIGIN%bin\StopAll.bat"

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

if "%JvmParams%"=="" (
    SET "JvmParams=-Xms256m -Xmx1024m -Xss256k"
    echo WARN: Missing %JvmParamsName% then using default params
) else (
    echo Using System Env JVM Params
)

echo Using "%JvmParams%"

REM ------------------------------------------------------------------
REM Step 1: H2 server on :3306 via BG_StartH2 (skipped when already listening).
REM On a fresh run the database is first (re)built via the EMBEDDED
REM local-file URL (StartH2Console init testdb, sa auto-created, change.sql
REM adds sample_user) and only then the TCP server is started on top of it.
REM ------------------------------------------------------------------
netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :h2_already

echo [TestDatasource] Initializing database (fallback.sql -^> change.sql)...
call "%JS_PLUS_PATH_ORIGIN%bin\StartH2Console.bat" init testdb
if errorlevel 1 (
    echo [TestDatasource] ERROR: Database initialization failed.
    exit /b 1
)
echo [TestDatasource] Starting H2 server (port 3306)...
call "%JS_PLUS_PATH_ORIGIN%bin\BG_StartH2.bat" %SCRIPT_ENGINE%

:h2_already
echo [TestDatasource] Waiting for H2 :3306...
set "H2_READY="
for /l %%i in (1,1,45) do (
    netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 set "H2_READY=1"
    if defined H2_READY goto :h2_ready
    ping -n 2 127.0.0.1 >nul 2>&1
)
:h2_ready
if not defined H2_READY (
    echo [TestDatasource] ERROR: H2 did not start on port 3306.
    exit /b 1
)
echo [TestDatasource] H2 ready.

REM ------------------------------------------------------------------
REM Step 2: Run TestDatasource.js against this JVM with the dedicated pool
REM configuration (config/TestDatasource.properties via -DAPP_DATASOURCE).
REM JSPLUS_TEST_EXIT=1 lets the suite System.exit(0) after finishing.
REM "< NUL" closes stdin so the Main scanner gets EOF and the JVM exits.
REM ------------------------------------------------------------------
set JSPLUS_TEST_EXIT=1
%JAVA_BIN% %JvmParams% -DAPP_REMOTE_FILE="%JS_PLUS_PATH_ORIGIN%config\RemoteFile.properties" -DAPP_CLOCK="%JS_PLUS_PATH_ORIGIN%config\Clock.properties" -DAPP_IDENTITY_CARD="%JS_PLUS_PATH_ORIGIN%config\IdentityCard.properties" -DAPP_DATASOURCE="%JS_PLUS_PATH_ORIGIN%config\TestDatasource.properties" -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine "%SCRIPT_ENGINE%" -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/TestDatasource.js" < NUL
set "RC=%ERRORLEVEL%"
echo TestDatasource exit code: %RC%

REM ------------------------------------------------------------------
REM Step 3: stop the H2 server started for this run (java ghost keyed by
REM script/StartH2.js) so TestDatasource leaves nothing behind.
REM ------------------------------------------------------------------
REM Step 3: stop the H2 server started for this run (Stop-Process on
REM java.exe/javaw.exe keyed by script/StartH2.js - only JVM processes,
REM never a wrapping cmd/powershell parent). In the TestAll flow
REM (TESTALL_RUN=1) the H2 is TestAll's shared server and its Step 4d
REM owns the teardown, so this step is skipped.
if not defined TESTALL_RUN (
    powershell -NoProfile -Command "$kw='script/StartH2.js'; $procs=Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -like \"*$kw*\" }; if($procs){ $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }" >nul 2>&1
)
exit /b %RC%

endlocal
