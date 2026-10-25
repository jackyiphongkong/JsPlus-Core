#!/bin/bash
set -e

# ------------------------------------------------------------------
# JsPlus-Core load test - LoadTest.sh
# Same flow as TestAll.sh (H2 start -> db init -> run suite -> stop H2),
# but runs test/LoadTest.js: a database CRUD load test over the runtime
# project datasource (Console.getDataSource() -> CacheDriverExecutor).
# Dashboard written to <PROJECT_ROOT>/test_reports/LoadTest-Report.html
# (format follows JsPlus-Tomcat LoadTest.js: dark cards + SVG bars +
# percentile table).
# Override via env:
#      LOADTEST_ROUNDS   rounds per table, default 100 (each round = 6 ops)
#      LOADTEST_TABLES   tables to load (1..5), default 5
#      JsPlus_JavaDir_JsPlus-Core   - JDK directory under Java/ (default: default)
#      JsPlus_JvmParams_JsPlus-Core - JVM params (default: -Xms256m -Xmx1024m -Xss256k)
#      SCRIPT_ENGINE (arg $1)       - default: graalvm.js
# ------------------------------------------------------------------

if [ -z "$1" ]; then
    SCRIPT_ENGINE=graalvm.js
else
    SCRIPT_ENGINE="$1"
fi

BIN_PATH="$(cd "$(dirname "$0")" && pwd)/"
JS_PLUS_PATH_ORIGIN="${BIN_PATH}../JsPlus/"
export JS_PLUS_PATH="${JS_PLUS_PATH_ORIGIN//\\//}"
LIB_HOME="${JS_PLUS_PATH}../LibHome/"

DEPEND_LIBS="$(find "${LIB_HOME}" -type f -name '*.jar' | tr '\n' ':')"
DEPEND_LIBS="${DEPEND_LIBS%:}"
if [ -z "$DEPEND_LIBS" ]; then
    echo "Error: No JARs found under LibHome. Run 'ant build' first (produces LibHome/jsplus/default/jsplus-core-<version>.jar)."
    exit 1
fi
CORE_JAR="$(ls "${LIB_HOME}jsplus/default/"/jsplus-core-*.jar 2>/dev/null | head -n 1 || true)"
if [ -z "$CORE_JAR" ]; then
    echo "WARN: LibHome/jsplus/default/jsplus-core-*.jar not found. Run 'ant build' first."
fi

AppName="$(basename "$(cd "${JS_PLUS_PATH}/.." && pwd)")"
JavaDirEnvName="JsPlus_JavaDir_${AppName}"
JAVA_DIR="$(printenv "$JavaDirEnvName" || true)"
JAVA_DIR="${JAVA_DIR:-default}"
JAVA_BIN="${JS_PLUS_PATH}../Java/${JAVA_DIR}/bin/java"

if [ ! -f "$JAVA_BIN" ]; then
    echo "WARN: Bundled Java not found at $JAVA_BIN, falling back to system Java..."
    if [ -n "$JAVA_HOME" ]; then
        JAVA_BIN="$JAVA_HOME/bin/java"
    else
        JAVA_BIN="java"
    fi
fi

JvmParamsName="JsPlus_JvmParams_${AppName}"
JvmParams="$(printenv "$JvmParamsName" || true)"

echo "$JvmParamsName"

if [ -z "$JvmParams" ]; then
    JvmParams="-Xms256m -Xmx1024m -Xss256k"
    echo "WARN: Missing ${JvmParamsName} then using default params"
else
    echo "Using System Env JVM Params"
fi

echo "Using \"$JvmParams\""

# ------------------------------------------------------------------
# Step 1: H2 server on :3306 via BG_StartH2 (skipped when already listening).
# ------------------------------------------------------------------
if netstat -an 2>/dev/null | grep -E ":3306\s+.*LISTEN(ING)?" >/dev/null 2>&1; then
    echo "[LoadTest] H2 already listening on :3306."
else
    echo "[LoadTest] Starting H2 server (port 3306)..."
    "${JS_PLUS_PATH}bin/BG_StartH2.sh" "$SCRIPT_ENGINE"
fi

echo "[LoadTest] Waiting for H2 :3306..."
H2_READY=""
for _ in $(seq 1 45); do
    if netstat -an 2>/dev/null | grep -E ":3306\s+.*LISTEN(ING)?" >/dev/null 2>&1; then
        H2_READY=1
        break
    fi
    sleep 2
done
if [ -z "$H2_READY" ]; then
    echo "[LoadTest] ERROR: H2 did not start on port 3306."
    exit 1
fi
echo "[LoadTest] H2 ready."

# ------------------------------------------------------------------
# Step 2: (Re)initialize the test database (fallback.sql -> change.sql).
# ------------------------------------------------------------------
echo "[LoadTest] Initializing database (fallback.sql -> change.sql)..."
mkdir -p "${JS_PLUS_PATH}../Database"
"$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" org.h2.tools.RunScript -url "jdbc:h2:tcp://localhost:3306/testdb" -user sa -script "${JS_PLUS_PATH}../dbscript/fallback.sql" -checkResults
"$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" org.h2.tools.RunScript -url "jdbc:h2:tcp://localhost:3306/testdb" -user sa -script "${JS_PLUS_PATH}../dbscript/change.sql" -checkResults
echo "[LoadTest] testdb initialized (ghost_remote_command + 5 test tables)."

# ------------------------------------------------------------------
# Step 3: Run LoadTest.js against this JVM.
# JSPLUS_TEST_EXIT=1 tells TestAll.js/LoadTest.js to System.exit(0) after
# finishing. "< /dev/null" closes stdin so the JsPlus Main scanner gets EOF
# right after the -Task completes and the JVM exits cleanly (Main.java).
# set -e is suspended around the java call so a non-zero JVM exit is
# captured and propagated explicitly.
# ------------------------------------------------------------------
export JSPLUS_TEST_EXIT=1
set +e
"$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" com.killcoding.jsplus.Main -ScriptEngine "$SCRIPT_ENGINE" -Env script -WriteHistory false -Task "${JS_PLUS_PATH}../test/LoadTest.js" < /dev/null
RC=$?
set -e
echo "LoadTest exit code: $RC"

# ------------------------------------------------------------------
# Step 4: stop the H2 server started for this run (java ghost keyed by
# script/StartH2.js) so LoadTest leaves nothing behind. RC was captured
# above. Deliberately not StopAll.sh: its AppRootDir path pattern could
# match the VS Code Java Language Server's command line in this workspace.
# ------------------------------------------------------------------
pkill -f 'script/StartH2.js' 2>/dev/null || true
exit "$RC"
