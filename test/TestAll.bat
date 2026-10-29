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
REM      <PROJECT_ROOT>/docs/latest/report/ (logs land in <PROJECT_ROOT>/logs/app/).
REM      Stdin is redirected to NUL so com.killcoding.jsplus.Main exits
REM      right after the -Task completes (otherwise it blocks on the
REM      interactive scanner, see Main.java scanner loop).
REM   3b) Run LoadTest.js (DB CRUD load test over the same testdb) and write
REM      docs/latest/report/LoadTest-Report.html. Any load failure fails the run.
REM   3c) Run TestDatasource.bat LAST (after every H2-dependent step): its C17
REM      case stops and restarts the H2 server (pool disconnect/resume proof)
REM      and the restarted H2 is not guaranteed to stay alive, so the suite
REM      must never run before suites that need :3306 up (e.g. TestClock's
REM      background time-refresh cases). Contained here, right before the
REM      StopH2/StopAll sweep below.
REM   4) Exit with the JVM's exit code (0 = all checks passed, 1 = failure).
REM Override via env (same convention as JsPlus-Tomcat):
REM      JsPlus_JavaDir_JsPlus-Core   - JDK directory under Java\ (default: default)
REM      JsPlus_JvmParams_JsPlus-Core - JVM params (default: -Xms256m -Xmx1024m -Xss256k)
REM      LOADTEST_ROUNDS / LOADTEST_TABLES - LoadTest.js tuning (default 100 / 5)
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
REM testdb file or a JVM pinning an old JAR cannot interfere with the db
REM init and the suite JVM below.
REM ------------------------------------------------------------------
call "%JS_PLUS_PATH_ORIGIN%bin\StopAll.bat"

REM ------------------------------------------------------------------
REM Step 0b: remove JsPlus\.tmp (evalJavaSource/evalJavaFile scratch:
REM compiled temp classes / MD5 source dirs) so every test run starts
REM with a clean temp tree and stale MD5 dirs cannot leak between runs.
REM ------------------------------------------------------------------
if exist "%JS_PLUS_PATH_ORIGIN%.tmp" (
    rmdir /s /q "%JS_PLUS_PATH_ORIGIN%.tmp"
    echo [TestAll] Removed JsPlus\.tmp
)

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

REM ------------------------------------------------------------------
REM Step 2: (Re)initialize the test database, then start the H2 server.
REM The DB file is locked while a server holds it, so on a fresh run the
REM database is first (re)built via the EMBEDDED local-file URL
REM (jdbc:h2:<abs>/Database/testdb - StartH2Console init, same reset
REM semantics as JsPlus-Tomcat's "StartH2Console init tomcatdb") and only
REM then the TCP server is started on top of it. Embedded init
REM auto-creates the file with the default admin (sa, empty password) and
REM change.sql adds the application account sample_user - so a fresh CI
REM checkout never hits the 28000 auth error (the 'sa' user always exists).
REM ------------------------------------------------------------------
echo [TestAll] Initializing database (fallback.sql -^> change.sql)...
call "%JS_PLUS_PATH_ORIGIN%bin\StartH2Console.bat" init testdb
if errorlevel 1 (
    echo [TestAll] ERROR: Database initialization failed.
    exit /b 1
)
REM Step 2b: initialize the dedicated dbsync SOURCE/TARGET file databases
REM (dbscript/fallback_sync.sql -> change_sync.sql). The dbsync suite
REM (TestDBSync.js) syncs exclusively between these two file DBs and never
REM touches the shared testdb.
call "%JS_PLUS_PATH_ORIGIN%bin\InitDBSyncDB.bat"
if errorlevel 1 (
    echo [TestAll] ERROR: DBSync database initialization failed.
    exit /b 1
)
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
echo [TestAll] testdb initialized (ghost_remote_command + 5 test tables).

REM ------------------------------------------------------------------
REM Step 3: Run TestAll.js against this JVM.
REM JSPLUS_TEST_EXIT=1 tells TestAll.js to System.exit(0) after finishing
REM (interactive Console.eval runs skip it). "< NUL" closes stdin: the JsPlus
REM Main scanner gets EOF right after the -Task finishes and the JVM exits
REM cleanly (Main.java).
REM ------------------------------------------------------------------
set JSPLUS_TEST_EXIT=1
%JAVA_BIN% %JvmParams% -DAPP_REMOTE_FILE="%JS_PLUS_PATH_ORIGIN%config\RemoteFile.properties" -DAPP_CLOCK="%JS_PLUS_PATH_ORIGIN%config\Clock.properties" -DAPP_IDENTITY_CARD="%JS_PLUS_PATH_ORIGIN%config\IdentityCard.properties" -DAPP_DATASOURCE="%JS_PLUS_PATH_ORIGIN%config\TestDatasource.properties" -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine "%SCRIPT_ENGINE%" -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/TestAll.js" < NUL
set "RC=%ERRORLEVEL%"
echo TestAll exit code: %RC%

REM ------------------------------------------------------------------
REM Step 3b: Run LoadTest.js against this JVM (DB CRUD load test, same H2
REM server and freshly initialized testdb as the suites above). Uses the
REM default runtime datasource (config/DataSource.properties) exactly like
REM test/LoadTest.bat; writes docs/latest/report/LoadTest-Report.html. Any load
REM failure is merged into the overall exit code below.
REM ------------------------------------------------------------------
set JSPLUS_TEST_EXIT=1
%JAVA_BIN% %JvmParams% -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine "%SCRIPT_ENGINE%" -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/LoadTest.js" < NUL
set "RC_LOAD=%ERRORLEVEL%"
echo LoadTest exit code: %RC_LOAD%
if not "%RC_LOAD%"=="0" set "RC=%RC_LOAD%"

REM ------------------------------------------------------------------
REM Step 3c: TestDatasource pool suite runs LAST (after every suite that
REM depends on a live H2). Its C17 case deliberately stops and restarts the
REM H2 server to prove the pool's disconnect/resume behaviour; the restarted
REM H2 is not guaranteed to stay alive, so the suite must NOT run before
REM TestClock's background-refresh cases (they need :3306 up). Running it
REM here, right before the StopH2/StopAll sweep, keeps the restarted-server
REM instability contained to this suite only. TestDatasource.bat reuses the
REM already-running H2 (its :3306 check below skips the init/start step) and
REM its own exit code is merged into the overall RC below.
REM ------------------------------------------------------------------
echo [TestAll] Running TestDatasource suite (last, H2 lifecycle-sensitive)...
call "%BIN_PATH%TestDatasource.bat" %SCRIPT_ENGINE%
set "RC_DS=%ERRORLEVEL%"
echo TestDatasource exit code: %RC_DS%
if not "%RC_DS%"=="0" set "RC=%RC_DS%"

REM ------------------------------------------------------------------
REM Step 3d: Merge the TestDatasource outcome into the TestAll report.
REM TestDatasource runs in its OWN JVM AFTER TestAll.js, so its result
REM (suite summary + per-case table) is appended here - the TestAll report
REM must reflect the full run, not just the suites TestAll.js ran itself.
REM ------------------------------------------------------------------
set "REPORT_DIR=%JS_PLUS_PATH_ORIGIN%..\docs\latest\report"
if exist "%REPORT_DIR%\TestDatasource-Report.md" (
    echo [TestAll] Merging TestDatasource result into TestAll report...
    echo.>> "%REPORT_DIR%\TestAll-Report.md"
    echo ## TestDatasource>> "%REPORT_DIR%\TestAll-Report.md"
    echo.>> "%REPORT_DIR%\TestAll-Report.md"
    type "%REPORT_DIR%\TestDatasource-Report.md">> "%REPORT_DIR%\TestAll-Report.md"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$d='%REPORT_DIR%'; $m=Get-Content -LiteralPath (Join-Path $d 'TestDatasource-Report.md') -Raw -Encoding UTF8; $h=Get-Content -LiteralPath (Join-Path $d 'TestDatasource-Report.html') -Raw -Encoding UTF8; $sum=[regex]::Match($m,'\*\*(\d+) passed / (\d+) failed\*\*'); $sp=$sum.Groups[1].Value; $sf=$sum.Groups[2].Value; $cls= if($sf -eq '0'){'summary-pass'}else{'summary-fail'}; $tbl=[regex]::Match($h,'(?s)<table.*?</table>').Value; if($tbl){ $p=Join-Path $d 'TestAll-Report.html'; $o=Get-Content -LiteralPath $p -Raw -Encoding UTF8; $block=[Environment]::NewLine+'<h2>TestDatasource</h2>'+[Environment]::NewLine+'<div class='+$cls+'>'+$sp+' passed / '+$sf+' failed</div>'+[Environment]::NewLine+$tbl+[Environment]::NewLine+'<p><a href=''TestDatasource-Report.html''>Full TestDatasource report</a></p>'; $o=$o -replace '</table>',('</table>'+$block); Set-Content -LiteralPath $p -Value $o -Encoding UTF8 }"
    if errorlevel 1 echo WARN: Failed to merge TestDatasource into TestAll-Report.html
)

REM Step 4: stop the H2 server started for this run (java ghost keyed by
REM script/StartH2.js) so TestAll leaves nothing behind. RC was captured
REM above. Then, per the project convention, the run finishes with a full
REM StopAll sweep (AppRootDir path match) so NO JsPlus/H2 JVM survives this
REM run - any leftover JVM that escaped the precise recycle is killed here.
powershell -NoProfile -Command "$kw='script/StartH2.js'; $procs=Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -like \"*$kw*\" }; if($procs){ $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }" >nul 2>&1
call "%JS_PLUS_PATH_ORIGIN%bin\StopAll.bat"

REM ------------------------------------------------------------------
REM Step 4b: Archive the test reports under the same docs layout as the
REM javadoc site: docs/reports/<version>.jar (one jar per run) plus
REM docs/latest/report/ as the browsable newest reports. Version is taken
REM from the core jar name. A failure to archive is only a WARN and never
REM changes the suite exit code (RC was captured above).
REM ------------------------------------------------------------------
for %%f in ("%CORE_JAR%") do set "CORE_FNAME=%%~nxf"
set "CORE_VERSION=%CORE_FNAME:jsplus-core-=%"
set "CORE_VERSION=%CORE_VERSION:.jar=%"
if not defined CORE_VERSION (
    echo WARN: Cannot extract version from %CORE_JAR%, skipping report archive.
) else (
    echo [TestAll] Archiving test reports as docs/reports/%CORE_VERSION%.jar ^(docs/latest/report^)...
    if not exist "%JS_PLUS_PATH_ORIGIN%..\docs\reports" mkdir "%JS_PLUS_PATH_ORIGIN%..\docs\reports"
    if exist "%JS_PLUS_PATH_ORIGIN%..\docs\reports\%CORE_VERSION%.jar" del "%JS_PLUS_PATH_ORIGIN%..\docs\reports\%CORE_VERSION%.jar"
    "%JS_PLUS_PATH_ORIGIN%..\Java\%JAVA_DIR%\bin\jar.exe" cf "%JS_PLUS_PATH_ORIGIN%..\docs\reports\%CORE_VERSION%.jar" -C "%JS_PLUS_PATH_ORIGIN%..\docs/latest/report" .
    if errorlevel 1 (
        echo WARN: Failed to pack test report jar ^(jar.exe missing?^), continuing.
    ) else (
        echo [TestAll] Packed docs/reports/%CORE_VERSION%.jar
    )
    REM Keep only the 10 newest report jars - the reports archive is owned
    REM by TestAll (ant only prunes its own docs/apis tree), so this suite
    REM bounds it here, right after the new jar is packed.
    powershell -NoProfile -Command "$d='%JS_PLUS_PATH_ORIGIN%..\docs\reports'; $jars=@(Get-ChildItem -LiteralPath $d -Filter *.jar | Sort-Object LastWriteTime); if($jars.Count -gt 10){ $jars | Select-Object -First ($jars.Count-10) | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }; Write-Output ('[TestAll] Pruned docs/reports to newest 10 (' + (Get-ChildItem -LiteralPath $d -Filter *.jar).Count + ' jars kept)') }"
)
exit /b %RC%

endlocal
