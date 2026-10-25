@echo off
setlocal enabledelayedexpansion

REM ------------------------------------------------------------------
REM JsPlus-Core automated test suite - TestAll.bat
REM Flow:
REM   1) (Re)initialize the test database: apply dbscript/fallback.sql
REM      (DROP all) then dbscript/change.sql (CREATE all) to the embedded
REM      H2 file database <PROJECT_ROOT>/Database/testdb — mirrors the
REM      JsPlus-Tomcat TestAll "Database init + H2" step, but no H2 TCP
REM      server is started (the suites use embedded connections).
REM   2) Build the runtime classpath from every JAR under
REM      <PROJECT_ROOT>/LibHome - script engines + deps, including the
REM      project JAR produced by 'ant build':
REM      <PROJECT_ROOT>/LibHome/jsplus/default/jsplus-core-<version>.jar
REM      (single Tomcat-standard JAR; com.killcoding.* + org.* replacements,
REM      version auto-bumped from <PROJECT_ROOT>/version.properties).
REM   3) Run TestAll.js in a standalone JsPlus console in THIS JVM
REM      (com.killcoding.jsplus.Main, same entry the JsPlus-Tomcat suite uses).
REM      No external server, H2 or Tomcat is required.
REM      JS_PLUS_PATH=<PROJECT_ROOT>/JsPlus/ (runtime anchor), so the suites
REM      resolve <PROJECT_ROOT>/test/TestBase.js + suites and write reports to
REM      <PROJECT_ROOT>/test_reports/ (logs land in <PROJECT_ROOT>/logs/app/).
REM      Stdin is redirected to NUL so com.killcoding.jsplus.Main exits
REM      right after the -Task completes (otherwise it blocks on the
REM      interactive scanner, see Main.java scanner loop).
REM   4) Exit with the JVM's exit code (0 = all checks passed, 1 = failure).
REM Override via env (same convention as JsPlus-Tomcat):
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
REM Step 1: H2 server on :3306 via BG_StartH2 (StartH2.bat ->
REM script/StartH2.js -> org.h2.tools.Server, config from Appenv.properties:
REM H2_PORT / H2_BASE_DIR=<PROJECT_ROOT>/Database/). Skipped when H2 is
REM already listening so repeated runs reuse the running server.
REM ------------------------------------------------------------------
netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :h2_already
echo [TestAll] Starting H2 server (port 3306)...
call "%JS_PLUS_PATH_ORIGIN%bin\BG_StartH2.bat" %SCRIPT_ENGINE%

:h2_already
echo [TestAll] Waiting for H2 :3306...
set "H2_READY="
for /l %%i in (1,1,45) do (
    netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 set "H2_READY=1"
    if defined H2_READY goto :h2_ready
    ping -n 2 127.0.0.1 >nul 2>&1
)
:h2_ready
if not defined H2_READY (
    echo [TestAll] ERROR: H2 did not start on port 3306.
    exit /b 1
)
echo [TestAll] H2 ready.

REM ------------------------------------------------------------------
REM Step 2: (Re)initialize the test database on the running H2 server.
REM fallback.sql drops every table, change.sql recreates them, applied to
REM jdbc:h2:tcp://localhost:3306/testdb (Database/testdb.mv.db served by H2)
REM so every TestAll run starts from a clean schema (same reset semantics as
REM JsPlus-Tomcat's "StartH2Console init" step, over TCP).
REM ------------------------------------------------------------------
echo [TestAll] Initializing database (fallback.sql -^> change.sql)...
%JAVA_BIN% %JvmParams% -cp "%DEPEND_LIBS%" org.h2.tools.RunScript -url "jdbc:h2:tcp://localhost:3306/testdb" -user sa -script "%JS_PLUS_PATH_ORIGIN%..\dbscript\fallback.sql" -checkResults
if errorlevel 1 (
    echo [TestAll] ERROR: fallback.sql failed.
    exit /b 1
)
%JAVA_BIN% %JvmParams% -cp "%DEPEND_LIBS%" org.h2.tools.RunScript -url "jdbc:h2:tcp://localhost:3306/testdb" -user sa -script "%JS_PLUS_PATH_ORIGIN%..\dbscript\change.sql" -checkResults
if errorlevel 1 (
    echo [TestAll] ERROR: change.sql failed.
    exit /b 1
)
echo [TestAll] testdb initialized (ghost_remote_command + 5 test tables).

REM ------------------------------------------------------------------
REM Step 3: Run TestAll.js against this JVM.
REM JSPLUS_TEST_EXIT=1 tells TestAll.js to System.exit(0) after finishing
REM (interactive Console.eval runs skip it). "< NUL" closes stdin: the JsPlus
REM Main scanner gets EOF right after the -Task finishes and the JVM exits
REM cleanly (Main.java).
REM ------------------------------------------------------------------
set JSPLUS_TEST_EXIT=1
%JAVA_BIN% %JvmParams% -DAPP_REMOTE_FILE="%JS_PLUS_PATH_ORIGIN%..\test\RemoteFile.properties" -DAPP_CLOCK="%JS_PLUS_PATH_ORIGIN%config\AppClock.properties" -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine "%SCRIPT_ENGINE%" -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/TestAll.js" < NUL
set "RC=%ERRORLEVEL%"
echo TestAll exit code: %RC%
REM Step 4: stop the H2 server started for this run (java ghost keyed by
REM script/StartH2.js) so TestAll leaves nothing behind. RC was captured
REM above. Deliberately not StopAll.bat: its AppRootDir path pattern could
REM match the VS Code Java Language Server's command line in this workspace.
powershell -NoProfile -Command "$kw='script/StartH2.js'; $procs=Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -like \"*$kw*\" }; if($procs){ $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }" >nul 2>&1
exit /b %RC%

endlocal
