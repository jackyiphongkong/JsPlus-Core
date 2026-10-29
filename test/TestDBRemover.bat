@echo off
setlocal enabledelayedexpansion

REM ------------------------------------------------------------------
REM JsPlus-Core dbsync Remover evidence runner - TestDBRemover.bat
REM Flow: (re)init testdb + start H2 :3306 (same as TestDatasource.bat),
REM build the LibHome classpath, then run TestDBRemover.js in a standalone
REM JsPlus console. The suite reports which database the Remover worker
REM actually deletes from (source vs target).
REM Exit code 0 = run completed, 1 = failure.
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

call "%JS_PLUS_PATH_ORIGIN%bin\StopAll.bat"

set "DEPEND_LIBS="
for /r "%JS_PLUS_PATH_ORIGIN%..\LibHome" %%j in (*.jar) do set "DEPEND_LIBS=!DEPEND_LIBS!;%%j"
set "DEPEND_LIBS=!DEPEND_LIBS:~1!"
if not defined DEPEND_LIBS (
    echo Error: No JARs found under LibHome. Run 'ant build' first.
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
if "%JvmParams%"=="" (
    SET "JvmParams=-Xms256m -Xmx1024m -Xss256k"
) 

netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :h2_already

echo [TestDBRemover] Initializing database (fallback.sql -^> change.sql)...
call "%JS_PLUS_PATH_ORIGIN%bin\StartH2Console.bat" init testdb
if errorlevel 1 (
    echo [TestDBRemover] ERROR: Database initialization failed.
    exit /b 1
)
echo [TestDBRemover] Starting H2 server (port 3306)...
call "%JS_PLUS_PATH_ORIGIN%bin\BG_StartH2.bat" %SCRIPT_ENGINE%

:h2_already
echo [TestDBRemover] Waiting for H2 :3306...
set "H2_READY="
for /l %%i in (1,1,45) do (
    netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 set "H2_READY=1"
    if defined H2_READY goto :h2_ready
    ping -n 2 127.0.0.1 >nul 2>&1
)
:h2_ready
if not defined H2_READY (
    echo [TestDBRemover] ERROR: H2 did not start on port 3306.
    exit /b 1
)
echo [TestDBRemover] H2 ready.

set JSPLUS_TEST_EXIT=1
%JAVA_BIN% %JvmParams% -DAPP_REMOTE_FILE="%JS_PLUS_PATH_ORIGIN%config\RemoteFile.properties" -DAPP_CLOCK="%JS_PLUS_PATH_ORIGIN%config\AppClock.properties" -DAPP_IDENTITY_CARD="%JS_PLUS_PATH_ORIGIN%config\IdentityCard.properties" -DAPP_DATASOURCE="%JS_PLUS_PATH_ORIGIN%config\TestDatasource.properties" -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine "%SCRIPT_ENGINE%" -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/TestDBRemover.js" < NUL
set "RC=%ERRORLEVEL%"
echo TestDBRemover exit code: %RC%

powershell -NoProfile -Command "$kw='script/StartH2.js'; $procs=Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -like \"*$kw*\" }; if($procs){ $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }" >nul 2>&1
exit /b %RC%

endlocal
