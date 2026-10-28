#!/bin/bash
set -e

# ------------------------------------------------------------------
# JsPlus-Core datasource pool test runner - TestDatasource.sh
# Flow:
#   1) (Re)initialize the test database: apply dbscript/fallback.sql
#      (DROP all) then dbscript/change.sql (CREATE all) to the embedded
#      H2 file database <PROJECT_ROOT>/Database/testdb, then start the
#      H2 TCP server on :3306 (same init + server steps as TestAll.sh,
#      skipped when :3306 is already listening).
#   2) Build the runtime classpath from every JAR under
#      <PROJECT_ROOT>/LibHome (including jsplus-core-<version>.jar from
#      'ant build').
#   3) Run TestDatasource.js in a standalone JsPlus console in THIS JVM
#      with -DAPP_DATASOURCE=<PROJECT_ROOT>/JsPlus/config/TestDatasource.properties:
#      the runtime Console.getDataSource() (ScriptConsole) loads the DEDICATED
#      second-scale pool configuration (IdleTimeout=5s / Timeout=8s /
#      TransactionTimeout=8s / LifeCycle=12s / MaxPoolSize=10) so the
#      idle / using / abort / timeout behaviour cases (C10-C13) and the
#      pool-capacity case (C8) finish quickly - the production
#      config/DataSource.properties is never touched.
#   4) Exit with the JVM's exit code (0 = all checks passed, 1 = failure),
#      after stopping the H2 server started for this run.
# Override via env (same convention as JsPlus-Tomcat / TestAll.sh):
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

# CI checkouts often lose the executable bit on helper scripts - make every
# helper executable up front (a no-op when the bit is already set).
chmod +x "${JS_PLUS_PATH}bin/"*.sh "${BIN_PATH}"*.sh 2>/dev/null || true

# ------------------------------------------------------------------
# Step 0: stop leftover JsPlus/H2 JVMs first (project convention: every
# build/test script runs StopAll up front) so a stale H2 ghost, a locked
# testdb file or a JVM pinning an old JAR cannot interfere with the pool
# cases below (they depend on a clean idle/using/abort state).
# ------------------------------------------------------------------
"${JS_PLUS_PATH}bin/StopAll.sh" || true

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
    echo "[TestDatasource] H2 already listening on :3306."
else
    # (Re)initialize the test database via the EMBEDDED local-file URL
    # (StartH2Console init testdb, sa auto-created, change.sql adds
    # sample_user), then start the TCP server on top of it.
    echo "[TestDatasource] Initializing database (fallback.sql -> change.sql)..."
    mkdir -p "${JS_PLUS_PATH}../Database"
    "${JS_PLUS_PATH}bin/StartH2Console.sh" init testdb
    echo "[TestDatasource] Starting H2 server (port 3306)..."
    "${JS_PLUS_PATH}bin/BG_StartH2.sh" "$SCRIPT_ENGINE"
fi

echo "[TestDatasource] Waiting for H2 :3306..."
H2_READY=""
for _ in $(seq 1 45); do
    if netstat -an 2>/dev/null | grep -E ":3306\s+.*LISTEN(ING)?" >/dev/null 2>&1; then
        H2_READY=1
        break
    fi
    sleep 2
done
if [ -z "$H2_READY" ]; then
    echo "[TestDatasource] ERROR: H2 did not start on port 3306."
    exit 1
fi
echo "[TestDatasource] H2 ready."

# ------------------------------------------------------------------
# Step 2: Run TestDatasource.js with the dedicated pool configuration
# (config/TestDatasource.properties via -DAPP_DATASOURCE).
# JSPLUS_TEST_EXIT=1 lets the suite System.exit(0) after finishing.
# ------------------------------------------------------------------
export JSPLUS_TEST_EXIT=1
set +e
"$JAVA_BIN" $JvmParams -DAPP_REMOTE_FILE="${JS_PLUS_PATH}config/RemoteFile.properties" -DAPP_CLOCK="${JS_PLUS_PATH}config/AppClock.properties" -DAPP_IDENTITY_CARD="${JS_PLUS_PATH}config/IdentityCard.properties" -DAPP_DATASOURCE="${JS_PLUS_PATH}config/TestDatasource.properties" -cp "$DEPEND_LIBS" com.killcoding.jsplus.Main -ScriptEngine "$SCRIPT_ENGINE" -Env script -WriteHistory false -Task "${JS_PLUS_PATH}../test/TestDatasource.js" < /dev/null
RC=$?
set -e
echo "TestDatasource exit code: $RC"

# ------------------------------------------------------------------
# Step 3: stop the H2 server started for this run (java ghost keyed by
# script/StartH2.js) so TestDatasource leaves nothing behind.
# ------------------------------------------------------------------
pkill -f 'script/StartH2.js' 2>/dev/null || true
exit "$RC"
