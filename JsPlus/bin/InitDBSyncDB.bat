@echo off
setlocal enabledelayedexpansion

REM ------------------------------------------------------------------
REM JsPlus-Core DBSync database initializer
REM Resets + rebuilds BOTH dedicated dbsync file databases:
REM   Database/dbsync_source  (DBSyncSource.properties) - sync SOURCE
REM   Database/dbsync_target  (DBSyncTarget.properties) - sync TARGET
REM by applying dbscript/fallback_sync.sql (DROP all) then
REM dbscript/change_sync.sql (CREATE all) to each file DB via the
REM embedded H2 RunScript tool. No H2 TCP server is required.
REM TestAll.bat/sh calls this right after 'init testdb' so the dbsync
REM suite never touches the shared testdb.
REM ------------------------------------------------------------------

set BIN_PATH=%~dp0
set JS_PLUS_PATH_ORIGIN=%BIN_PATH%..\
SET "JS_PLUS_PATH=%JS_PLUS_PATH_ORIGIN:\=/%"
set LIB_HOME=%JS_PLUS_PATH%../LibHome/

set "DEPEND_LIBS="
for /r "%JS_PLUS_PATH_ORIGIN%..\LibHome" %%j in (*.jar) do set "DEPEND_LIBS=!DEPEND_LIBS!;%%j"
set "DEPEND_LIBS=!DEPEND_LIBS:~1!"
if not defined DEPEND_LIBS (
    echo Error: No JARs found under LibHome
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
if "%JvmParams%"=="" SET "JvmParams=-Xms128m -Xmx128m -Xss256k"

for %%D in (dbsync_source dbsync_target) do (
    echo [InitDBSyncDB] Initializing %%D - fallback_sync.sql to change_sync.sql...
    "%JAVA_BIN%" %JvmParams% -cp "%DEPEND_LIBS%" org.h2.tools.RunScript -url "jdbc:h2:%JS_PLUS_PATH%../Database/%%D" -user sample_user -password sample_password -script "%JS_PLUS_PATH%../dbscript/fallback_sync.sql" -checkResults
    if errorlevel 1 (
        echo [InitDBSyncDB] ERROR - fallback_sync.sql failed for %%D
        exit /b 1
    )
    "%JAVA_BIN%" %JvmParams% -cp "%DEPEND_LIBS%" org.h2.tools.RunScript -url "jdbc:h2:%JS_PLUS_PATH%../Database/%%D" -user sample_user -password sample_password -script "%JS_PLUS_PATH%../dbscript/change_sync.sql" -checkResults
    if errorlevel 1 (
        echo [InitDBSyncDB] ERROR - change_sync.sql failed for %%D
        exit /b 1
    )
)
echo [InitDBSyncDB] dbsync databases initialized (8 tables + sample_user each).

endlocal
