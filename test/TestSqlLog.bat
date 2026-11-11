@echo off
setlocal enabledelayedexpansion

REM ------------------------------------------------------------------
REM JsPlus-Core sql log writer test runner - TestSqlLog.bat
REM Flow:
REM   1) (Re)initialize the test database: apply dbscript/fallback.sql
REM      (DROP all) then dbscript/change.sql (CREATE all) to the embedded
REM      H2 file database <PROJECT_ROOT>/Database/testdb, then start the
REM      H2 TCP server on :3306 (skipped when :3306 is already listening).
REM   2) Build the runtime classpath from every JAR under
REM      <PROJECT_ROOT>/LibHome (including jsplus-core-<version>.jar from
REM      'ant build').
REM   3) Run TestSqlLog.js in a standalone JsPlus console in THIS JVM
REM      with -DAPP_DATASOURCE=<PROJECT_ROOT>/JsPlus/config/TestDatasource.properties:
REM      the suite drives DriverExecutor.writeSqlLog through real
REM      DriverConnections (enable switch, per-pid file names, size
REM      rotation, cross-process rotation tolerance, directory creation,
REM      concurrent writers).
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
set "H2_PORT=3306"
if defined JsPlus_H2_PORT set "H2_PORT=%JsPlus_H2_PORT%"

REM ------------------------------------------------------------------
REM Step 0: stop leftover JsPlus/H2 JVMs first (project convention: every
REM build/test script runs StopAll up front). In the TestAll flow
REM (TESTALL_RUN=1) this is SKIPPED on purpose - TestAll.bat started the
REM shared H2 server and this runner reuses it.
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
REM ------------------------------------------------------------------
netstat -ano | findstr ":%H2_PORT%" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :h2_already

echo [TestSqlLog] Initializing database (fallback.sql -^> change.sql)...
call "%JS_PLUS_PATH_ORIGIN%bin\StartH2Console.bat" init testdb
if errorlevel 1 (
    echo [TestSqlLog] ERROR: Database initialization failed.
    exit /b 1
)
echo [TestSqlLog] Starting H2 server (port %H2_PORT%)...
call "%JS_PLUS_PATH_ORIGIN%bin\BG_StartH2.bat" %SCRIPT_ENGINE%

:h2_already
echo [TestSqlLog] Waiting for H2 :%H2_PORT%...
set "H2_READY="
for /l %%i in (1,1,45) do (
    netstat -ano | findstr ":!H2_PORT!" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 set "H2_READY=1"
    if defined H2_READY goto :h2_ready
    ping -n 2 127.0.0.1 >nul 2>&1
)
:h2_ready
if not defined H2_READY (
    echo [TestSqlLog] ERROR: H2 did not start on port %H2_PORT%.
    exit /b 1
)
echo [TestSqlLog] H2 ready.

REM ------------------------------------------------------------------
REM Step 2: Run TestSqlLog.js against this JVM with the dedicated datasource
REM configuration (config/TestDatasource.properties via -DAPP_DATASOURCE).
REM JSPLUS_TEST_EXIT=1 lets the suite System.exit(0) after finishing.
REM "< NUL" closes stdin so the Main scanner gets EOF and the JVM exits.
REM ------------------------------------------------------------------
set JSPLUS_TEST_EXIT=1
%JAVA_BIN% %JvmParams% -DAPP_REMOTE_FILE="%JS_PLUS_PATH_ORIGIN%config\RemoteFile.properties" -DAPP_CLOCK="%JS_PLUS_PATH_ORIGIN%config\Clock.properties" -DAPP_IDENTITY_CARD="%JS_PLUS_PATH_ORIGIN%config\IdentityCard.properties" -DAPP_DATASOURCE="%JS_PLUS_PATH_ORIGIN%config\TestDatasource.properties" -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine "%SCRIPT_ENGINE%" -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/TestSqlLog.js" < NUL
set "RC=%ERRORLEVEL%"
echo TestSqlLog exit code: %RC%

REM ------------------------------------------------------------------
REM Step 3: stop the H2 server started for this run (skipped in the TestAll
REM flow - TestAll.bat owns the shared H2 teardown).
REM ------------------------------------------------------------------
if not defined TESTALL_RUN (
    powershell -NoProfile -Command "$kw='script/StartH2.js'; $root='%JS_PLUS_PATH%'; $procs=Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -like \"*$kw*\" -and $_.CommandLine -like \"*$root*\" }; if($procs){ $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }" >nul 2>&1
)
exit /b %RC%

endlocal