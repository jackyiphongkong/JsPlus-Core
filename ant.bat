@echo off
setlocal

SET BIN_PATH_ORIGIN=%~dp0

SET "BIN_PATH=%BIN_PATH_ORIGIN:\=/%"

REM ------------------------------------------------------------------
REM Step 0: stop leftover JsPlus/H2 JVMs first (project convention: ant.sh
REM and Test*.bat/sh always run StopAll first) so a stale H2 ghost or a
REM JVM locking the previous JAR cannot break the build.
REM ------------------------------------------------------------------
if exist "%BIN_PATH_ORIGIN%JsPlus\bin\StopAll.bat" (
    call "%BIN_PATH_ORIGIN%JsPlus\bin\StopAll.bat"
)

if defined JAVA_HOME set "JAVA_HOME_ORIG=%JAVA_HOME%"
if defined ANT_HOME set "ANT_HOME_ORIG=%ANT_HOME%"

SET JAVA_HOME=%BIN_PATH%/Java/default/

SET ANT_HOME=%BIN_PATH%/ant/default/

if not exist "%JAVA_HOME%" (
    echo WARN: Bundled Java not found at %JAVA_HOME%, falling back to system JAVA_HOME...
    if defined JAVA_HOME_ORIG (
        SET "JAVA_HOME=%JAVA_HOME_ORIG%"
    ) else (
        set "JAVA_HOME="
    )
)

if not exist "%ANT_HOME%" (
    echo WARN: Bundled Ant not found at %ANT_HOME%, falling back to system ANT_HOME...
    if defined ANT_HOME_ORIG (
        SET "ANT_HOME=%ANT_HOME_ORIG%"
    ) else (
        set "ANT_HOME="
    )
)

if defined ANT_HOME (
    call "%ANT_HOME%/bin/ant.bat" %*
) else (
    ant %*
)

endlocal
