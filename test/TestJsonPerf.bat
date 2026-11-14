@echo off
setlocal enabledelayedexpansion

REM ------------------------------------------------------------------
REM JsPlus-Core Json perf bench (org.json vs com.killcoding.jsplus.Json)
REM Adds the org.json jar to the classpath because LibHome does not ship it.
REM Usage: TestJsonPerf.bat [path\to\org.json.jar]
REM Not part of TestAll - timing must not run inside the CI suite list.
REM
REM Main initialises the datasource on startup, so H2 must be listening
REM first (same as _RunOne.bat); without it the pool spins retrying.
REM ------------------------------------------------------------------

set BIN_PATH=%~dp0
set JS_PLUS_PATH_ORIGIN=%BIN_PATH%..\JsPlus\
SET "JS_PLUS_PATH=%JS_PLUS_PATH_ORIGIN:\=/%"
set "H2_PORT=3306"
if defined JsPlus_H2_PORT set "H2_PORT=%JsPlus_H2_PORT%"

set "ORG_JSON_JAR=%~1"
if "%ORG_JSON_JAR%"=="" set "ORG_JSON_JAR=D:\Working\KillCoding-Core\LibHome\ext\default\org.json-1.6-20240205.jar"
if not exist "%ORG_JSON_JAR%" (
    echo [TestJsonPerf] ERROR: org.json jar not found at "%ORG_JSON_JAR%"
    echo [TestJsonPerf] Pass the jar path as the first argument.
    exit /b 1
)

REM ---- H2 must be listening before Main starts ----
netstat -ano | findstr ":%H2_PORT%" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :h2_ready

echo [TestJsonPerf] Starting H2 on port %H2_PORT%...
call "%JS_PLUS_PATH_ORIGIN%bin\BG_StartH2.bat" graalvm.js

:h2_ready
set "H2_READY="
for /l %%i in (1,1,45) do (
    netstat -ano | findstr ":%H2_PORT%" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 set "H2_READY=1"
    if defined H2_READY goto :run
    ping -n 2 127.0.0.1 >nul 2>&1
)
:run
if not defined H2_READY (
    echo [TestJsonPerf] ERROR: H2 did not start on port %H2_PORT%.
    exit /b 1
)

set "DEPEND_LIBS="
for /r "%JS_PLUS_PATH_ORIGIN%..\LibHome" %%j in (*.jar) do set "DEPEND_LIBS=!DEPEND_LIBS!;%%j"
set "DEPEND_LIBS=!DEPEND_LIBS!;%ORG_JSON_JAR%"
set "DEPEND_LIBS=!DEPEND_LIBS:~1!"

SET "JavaDirEnvName=JsPlus_JavaDir_JsPlus-Core"
call set "JAVA_DIR=%%%JavaDirEnvName%%%"
if not defined JAVA_DIR set "JAVA_DIR=default"
set "JAVA_BIN=%JS_PLUS_PATH%../Java/%JAVA_DIR%/bin/java.exe"
if not exist "%JAVA_BIN%" (
    echo [TestJsonPerf] WARN: bundled Java not found, falling back to JAVA_HOME / PATH
    if defined JAVA_HOME (
        set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
    ) else (
        set "JAVA_BIN=java"
    )
)

echo [TestJsonPerf] org.json jar: %ORG_JSON_JAR%
"%JAVA_BIN%" -Xms256m -Xmx1024m -DAPP_REMOTE_FILE="%JS_PLUS_PATH_ORIGIN%config\RemoteFile.properties" -DAPP_CLOCK="%JS_PLUS_PATH_ORIGIN%config\Clock.properties" -DAPP_IDENTITY_CARD="%JS_PLUS_PATH_ORIGIN%config\IdentityCard.properties" -DAPP_DATASOURCE="%JS_PLUS_PATH_ORIGIN%config\TestDatasource.properties" -cp "%DEPEND_LIBS%" com.killcoding.jsplus.Main -ScriptEngine graalvm.js -Env script -WriteHistory false -Task "%JS_PLUS_PATH%../test/TestJsonPerf.js" < NUL
set "RC=%ERRORLEVEL%"

echo [TestJsonPerf] exit code: %RC%
call "%JS_PLUS_PATH_ORIGIN%bin\StopAll.bat" >nul 2>&1
exit /b %RC%