#!/bin/bash
set -e

# ------------------------------------------------------------------
# JsPlus-Core shared single-suite launcher - _RunOne.sh
# Usage: _RunOne.sh <TestName> [scriptEngine]
#   <TestName>    e.g. TestTextScript, TestSimpleJsCompressor, ...
#   [scriptEngine] default graalvm.js
# Flow (mirrors TestAll.sh for one suite):
#   -1) pre-cleanup: remove JsPlus/.tmp, clear logs/* and testdata/*
#    0) StopAll (stop leftover JsPlus/H2 JVMs)
#    1) (re)init testdb + InitDBSyncDB + start H2 on :3306 (skipped when
#       already listening - mirrors TestAll.sh Step 1/2)
#    2) run <TestName>.js in a standalone JsPlus console in THIS JVM
#       (JSPLUS_TEST_EXIT=1, stdin < /dev/null)
#    3) StopAll sweep
#   Exit with the JVM's exit code (0 = all checks passed, 1 = failure).
# Thin wrappers (TestXxx.sh) call this file with their own TestName.
# Override via env (same convention as TestAll.sh):
#      JsPlus_JavaDir_JsPlus-Core   - JDK directory under Java/ (default: default)
#      JsPlus_JvmParams_JsPlus-Core - JVM params (default: -Xms256m -Xmx1024m -Xss256k)
# ------------------------------------------------------------------

if [ -z "$1" ]; then
    echo "Usage: $0 <TestName> [scriptEngine]"
    exit 1
fi
TEST_NAME="$1"

if [ -z "$2" ]; then
    SCRIPT_ENGINE=graalvm.js
else
    SCRIPT_ENGINE="$2"
fi

BIN_PATH="$(cd "$(dirname "$0")" && pwd)/"
JS_PLUS_PATH_ORIGIN="${BIN_PATH}../JsPlus/"
export JS_PLUS_PATH="${JS_PLUS_PATH_ORIGIN//\\//}"
LIB_HOME="${JS_PLUS_PATH}../LibHome/"

# CI checkouts often lose the executable bit on helper scripts, so make
# every helper executable up front (no-op when the bit is already set).
chmod +x "${JS_PLUS_PATH}bin/"*.sh "${BIN_PATH}"*.sh 2>/dev/null || true

# ------------------------------------------------------------------
# Step -1: pre-cleanup (align with TestAll.sh Step 0b/0c/0d)
# ------------------------------------------------------------------
if [ -d "${JS_PLUS_PATH_ORIGIN}.tmp" ]; then
    rm -rf "${JS_PLUS_PATH_ORIGIN}.tmp"
    echo "[$TEST_NAME] Removed JsPlus/.tmp"
fi
if [ -d "${JS_PLUS_PATH_ORIGIN}../logs" ]; then
    rm -rf "${JS_PLUS_PATH_ORIGIN}../logs"/*
    echo "[$TEST_NAME] Cleared logs"
fi
if [ -d "${JS_PLUS_PATH_ORIGIN}../testdata" ]; then
    rm -rf "${JS_PLUS_PATH_ORIGIN}../testdata"/*
    echo "[$TEST_NAME] Removed testdata"
fi

# ------------------------------------------------------------------
# Step 0: stop leftover JsPlus/H2 JVMs first (project convention).
# ------------------------------------------------------------------
echo "[$TEST_NAME] Stopping existing services..."
"${JS_PLUS_PATH}bin/StopAll.sh" || true

DEPEND_LIBS="$(find "${LIB_HOME}" -type f -name '*.jar' | tr '\n' ':')"
DEPEND_LIBS="${DEPEND_LIBS%:}"
if [ -z "$DEPEND_LIBS" ]; then
    echo "Error: No JARs found under LibHome. Run 'ant build' first (produces LibHome/jsplus/default/jsplus-core-<version>.jar)."
    exit 1
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
if [ -z "$JvmParams" ]; then
    JvmParams="-Xms256m -Xmx1024m -Xss256k"
fi

# ------------------------------------------------------------------
# Step 1: H2 init + start on :3306 (skipped when already listening).
# Mirrors TestAll.sh Step 1/2: embedded (re)init of Database/testdb via
# StartH2Console, then the TCP server on top of it; InitDBSyncDB builds
# the dedicated dbsync SOURCE/TARGET file DBs.
# ------------------------------------------------------------------
if netstat -an 2>/dev/null | grep -E ":3306\s+.*LISTEN(ING)?" >/dev/null 2>&1; then
    echo "[$TEST_NAME] H2 already listening on :3306."
else
    echo "[$TEST_NAME] Initializing database (fallback.sql -> change.sql)..."
    mkdir -p "${JS_PLUS_PATH}../Database"
    "${JS_PLUS_PATH}bin/StartH2Console.sh" init testdb
    "${JS_PLUS_PATH}bin/InitDBSyncDB.sh"
    echo "[$TEST_NAME] Starting H2 server (port 3306)..."
    "${JS_PLUS_PATH}bin/BG_StartH2.sh" "$SCRIPT_ENGINE"
fi

echo "[$TEST_NAME] Waiting for H2 :3306..."
H2_READY=""
for _ in $(seq 1 45); do
    if netstat -an 2>/dev/null | grep -E ":3306\s+.*LISTEN(ING)?" >/dev/null 2>&1; then
        H2_READY=1
        break
    fi
    sleep 2
done
if [ -z "$H2_READY" ]; then
    echo "[$TEST_NAME] ERROR: H2 did not start on port 3306."
    exit 1
fi
echo "[$TEST_NAME] H2 ready."

# ------------------------------------------------------------------
# Step 2: run the single suite via _RunOne.js (mirrors JsPlus-Tomcat's
# launcher). _RunOne.js loads the suite then hard-halts the JVM, because the
# ScriptConsoleBase DetectSystemExit / ConfigProperties AutoLoad threads are
# non-daemon and would otherwise keep the JVM alive after main() returns (the
# suite itself never calls System.exit; the old direct -Task form hung here).
# RC is the suite's failCount (0 = all checks passed, 1 = failure/load error),
# matching the JVM exit-code convention of TestAll.sh.
# NOTE: set -e is suspended around the java call so a non-zero JVM exit is
# captured and propagated explicitly.
# ------------------------------------------------------------------
export TESTALL_TASK=1
export RUN_SUITE="$TEST_NAME"
echo "[$TEST_NAME] Running ${TEST_NAME}.js (via _RunOne.js) ..."
set +e
"$JAVA_BIN" $JvmParams -DAPP_REMOTE_FILE="${JS_PLUS_PATH}config/RemoteFile.properties" -DAPP_CLOCK="${JS_PLUS_PATH}config/Clock.properties" -DAPP_IDENTITY_CARD="${JS_PLUS_PATH}config/IdentityCard.properties" -DAPP_DATASOURCE="${JS_PLUS_PATH}config/TestDatasource.properties" -cp "$DEPEND_LIBS" com.killcoding.jsplus.Main -ScriptEngine "$SCRIPT_ENGINE" -Env script -WriteHistory false -Task "${JS_PLUS_PATH}../test/_RunOne.js" < /dev/null
RC=$?
set -e
echo "[$TEST_NAME] exit code: $RC"

# ------------------------------------------------------------------
# Step 3: full StopAll sweep (kills the H2 started above and any leftover
# JsPlus JVM) so a single-suite run leaves nothing behind.
# ------------------------------------------------------------------
echo "[$TEST_NAME] Stopping all remaining services..."
"${JS_PLUS_PATH}bin/StopAll.sh" || true

exit "$RC"
