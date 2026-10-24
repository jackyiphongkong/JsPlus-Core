@echo off
setlocal enabledelayedexpansion

:: ===========================================================================
:: packJdk.bat - Build a trimmed custom JRE for JsPlus-Core (jdeps + jlink)
::
:: Usage:   packJdk.bat
::
:: Prereq:  A JDK with jdeps/jlink must be available. The bundled GraalVM
::          (Java\default) is used by default; system JAVA_HOME is the fallback.
::          Run 'ant build' FIRST: jdeps resolves the project JAR produced by
::          the build (LibHome\jsplus\default\jsplus-core-<version>.jar).
:: Output:  %BIN_PATH%Java\runtime  (jlink-trimmed runtime, previous output is replaced)
:: ===========================================================================

:: ---------------------------------------------------------------------------
:: 1. Base paths (all path variables are validated before any delete below)
:: ---------------------------------------------------------------------------
set "BIN_PATH=%~dp0"
if not defined BIN_PATH (
    echo Error: BIN_PATH is empty.
    exit /b 1
)
set "LIB_HOME=%BIN_PATH%LibHome"
set "RUNTIME_PACK_OUTPUT=%BIN_PATH%Java\runtime"
if not defined RUNTIME_PACK_OUTPUT (
    echo Error: RUNTIME_PACK_OUTPUT is empty.
    exit /b 1
)

:: ---------------------------------------------------------------------------
:: 2. Build classpath from every JAR under LibHome (script engines & deps)
:: ---------------------------------------------------------------------------
set "DEPEND_LIBS="
for /r "%LIB_HOME%" %%j in (*.jar) do (
    set "DEPEND_LIBS=!DEPEND_LIBS!;%%j"
)
set "DEPEND_LIBS=!DEPEND_LIBS:~1!"
if not defined DEPEND_LIBS (
    echo Error: No JAR files found under %LIB_HOME%
    exit /b 1
)

:: ---------------------------------------------------------------------------
:: 3. Locate the project JAR built by 'ant build'
::    (LibHome\jsplus\default\jsplus-core.jar - Tomcat-standard layout)
:: ---------------------------------------------------------------------------
set "PROJECT_JARS=%LIB_HOME%\jsplus\default\*.jar"
for %%j in (%PROJECT_JARS%) do (
    if not exist "%%j" (
        echo Error: Missing project JAR '%%j'. Run 'ant build' first.
        exit /b 1
    )
)

:: ---------------------------------------------------------------------------
:: 4. Locate Java - bundled GraalVM preferred, system JAVA_HOME as fallback
:: ---------------------------------------------------------------------------
set "JAVA_HOME_ORIG=%JAVA_HOME%"
set "JAVA_HOME=%BIN_PATH%Java\default"
set "JAVA_BIN=%JAVA_HOME%\bin"

if not exist "%JAVA_BIN%\jdeps.exe" (
    echo WARN: Bundled Java not found at %JAVA_HOME%, falling back to system JAVA_HOME...
    if defined JAVA_HOME_ORIG (
        set "JAVA_HOME=%JAVA_HOME_ORIG%"
        set "JAVA_BIN=%JAVA_HOME_ORIG%\bin"
    ) else (
        echo Error: Java JDK not found. Set JAVA_HOME to a JDK to use packJdk.
        exit /b 1
    )
)

:: ---------------------------------------------------------------------------
:: 5. Resolve module dependencies with jdeps
:: ---------------------------------------------------------------------------
if not defined TEMP set "TEMP=%BIN_PATH%.tmp"
if not exist "%TEMP%\" mkdir "%TEMP%" >nul 2>&1
set "TMPFILE=%TEMP%\jdeps_modules_%RANDOM%.txt"
if not defined TMPFILE (
    echo Error: TMPFILE is empty.
    exit /b 1
)

"%JAVA_BIN%\jdeps.exe" --multi-release 25 --ignore-missing-deps -cp "%DEPEND_LIBS%" --print-module-deps %PROJECT_JARS% > "%TMPFILE%" 2>nul
if errorlevel 1 (
    echo Error: jdeps failed. Check the classpath or JDK version.
    if defined TMPFILE if exist "%TMPFILE%" del "%TMPFILE%" >nul 2>&1
    exit /b 1
)

:: Keep the last non-warning line (jdeps prints "Warning: split package ..."
:: before the module list); the final line is the comma-separated module list.
set "modules="
for /f "delims=" %%m in ('findstr /v /c:"Warning:" "%TMPFILE%"') do set "modules=%%m"
if defined TMPFILE if exist "%TMPFILE%" del "%TMPFILE%" >nul 2>&1

if not defined modules (
    echo Error: jdeps returned no module dependencies.
    exit /b 1
)

echo Depend modules: %modules%

:: ---------------------------------------------------------------------------
:: 6. Build the trimmed runtime with jlink
::    Guard: only delete the runtime directory when it is exactly
::    %BIN_PATH%Java\runtime (never an empty/root/unexpected path).
:: ---------------------------------------------------------------------------
echo %RUNTIME_PACK_OUTPUT% | findstr /i /b /c:"%BIN_PATH%Java" >nul 2>&1
if errorlevel 1 (
    echo Error: Refusing to remove unexpected path %RUNTIME_PACK_OUTPUT%
    exit /b 1
)
if exist "%RUNTIME_PACK_OUTPUT%\" (
    echo Removing previous runtime at %RUNTIME_PACK_OUTPUT%
    rmdir /s /q "%RUNTIME_PACK_OUTPUT%"
)

"%JAVA_BIN%\jlink.exe" --module-path "%JAVA_HOME%\jmods" --add-modules %modules% --output "%RUNTIME_PACK_OUTPUT%"
if errorlevel 1 (
    echo Error: jlink failed. See messages above.
    exit /b 1
)

echo Custom JRE created at %RUNTIME_PACK_OUTPUT%
endlocal
exit /b 0
