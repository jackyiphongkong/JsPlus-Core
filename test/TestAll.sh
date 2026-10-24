#!/bin/bash
set -e

# ------------------------------------------------------------------
# JsPlus-Core automated test suite - TestAll.sh
# Flow:
#   1) Start the H2 server on :3306 via BG_StartH2 (StartH2.sh ->
#      script/StartH2.js -> org.h2.tools.Server, config from
#      Appenv.properties: H2_PORT / H2_BASE_DIR=<PROJECT_ROOT>/Database/).
#      Skipped when H2 is already listening so repeated runs reuse the
#      running server.
#   2) (Re)initialize the test database on the running H2 server: apply
#      dbscript/fallback.sql (DROP all) then dbscript/change.sql (CREATE
#      all) to jdbc:h2:tcp://localhost:3306/testdb (Database/testdb.mv.db
#      served by H2) - same reset semantics as JsPlus-Tomcat's
#      "StartH2Console init" step, over TCP.
#   3) Build the runtime classpath from every JAR under
#      <PROJECT_ROOT>/LibHome - script engines + deps, including the
#      project JAR produced by 'ant build':
#      <PROJECT_ROOT>/LibHome/jsplus/default/jsplus-core-<version>.jar
#      (single Tomcat-standard JAR; com.killcoding.* + org.* replacements,
#      version auto-bumped from <PROJECT_ROOT>/version.properties).
#   4) Run TestAll.js in a standalone JsPlus console in THIS JVM
#      (com.killcoding.jsplus.Main, same entry the JsPlus-Tomcat suite uses).
#      No external Tomcat is required.
#      JS_PLUS_PATH=<PROJECT_ROOT>/JsPlus/ (runtime anchor), so the suites
#      resolve <PROJECT_ROOT>/test/TestBase.js + suites and write reports to
#      <PROJECT_ROOT>/test_reports/ (logs land in <PROJECT_ROOT>/logs/app/).
#      Stdin is redirected to /dev/null so com.killcoding.jsplus.Main exits
#      right after the -Task completes (otherwise it blocks on the
#      interactive scanner, see Main.java scanner loop).
#   5) Exit with the JVM's exit code (0 = all checks passed, 1 = failure),
#      after stopping the H2 server started for this run.
# Override via env (same convention as JsPlus-Tomcat):
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

# The JDK directory name under Java/ comes from per-script env var
# JsPlus_JavaDir_<AppName> (default: default).
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
    echo "[TestAll] H2 already listening on :3306."
else
    echo "[TestAll] Starting H2 server (port 3306)..."
    "${JS_PLUS_PATH}bin/BG_StartH2.sh" "$SCRIPT_ENGINE"
fi

echo "[TestAll] Waiting for H2 :3306..."
H2_READY=""
for _ in $(seq 1 45); do
    if netstat -an 2>/dev/null | grep -E ":3306\s+.*LISTEN(ING)?" >/dev/null 2>&1; then
        H2_READY=1
        break
    fi
    sleep 2
done
if [ -z "$H2_READY" ]; then
    echo "[TestAll] ERROR: H2 did not start on port 3306."
    exit 1
fi
echo "[TestAll] H2 ready."

# ------------------------------------------------------------------
# Step 2: (Re)initialize the test database on the running H2 server.
# fallback.sql drops every table, change.sql recreates them, applied to
# jdbc:h2:tcp://localhost:3306/testdb (Database/testdb.mv.db served by H2)
# so every TestAll run starts from a clean schema.
# ------------------------------------------------------------------
echo "[TestAll] Initializing database (fallback.sql -> change.sql)..."
mkdir -p "${JS_PLUS_PATH}../Database"
"$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" org.h2.tools.RunScript -url "jdbc:h2:tcp://localhost:3306/testdb" -user sa -script "${JS_PLUS_PATH}../dbscript/fallback.sql" -checkResults
"$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" org.h2.tools.RunScript -url "jdbc:h2:tcp://localhost:3306/testdb" -user sa -script "${JS_PLUS_PATH}../dbscript/change.sql" -checkResults
echo "[TestAll] testdb initialized (ghost_remote_command + 5 test tables)."

# ------------------------------------------------------------------
# Step 3: Run TestAll.js against this JVM.
# JSPLUS_TEST_EXIT=1 tells TestAll.js to System.exit(0) after finishing
# (interactive Console.eval runs skip it). "< /dev/null" closes stdin: the
# JsPlus Main scanner gets EOF right after the -Task finishes and the JVM
# exits cleanly (Main.java).
# NOTE: set -e is suspended around the java call so a non-zero JVM exit is
# captured and propagated explicitly (instead of the shell dying silently).
# ------------------------------------------------------------------
export JSPLUS_TEST_EXIT=1
set +e
"$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" com.killcoding.jsplus.Main -ScriptEngine "$SCRIPT_ENGINE" -Env script -WriteHistory false -Task "${JS_PLUS_PATH}../test/TestAll.js" < /dev/null
RC=$?
set -e
echo "TestAll exit code: $RC"

# ------------------------------------------------------------------
# Step 4: stop the H2 server started for this run (java ghost keyed by
# script/StartH2.js) so TestAll leaves nothing behind. RC was captured
# above. Deliberately not StopAll.sh: its AppRootDir path pattern could
# match the VS Code Java Language Server's command line in this workspace.
# ------------------------------------------------------------------
pkill -f 'script/StartH2.js' 2>/dev/null || true
exit "$RC"
