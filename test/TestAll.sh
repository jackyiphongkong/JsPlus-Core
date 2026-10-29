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
#      <PROJECT_ROOT>/docs/latest/report/ (logs land in <PROJECT_ROOT>/logs/app/).
#      Stdin is redirected to /dev/null so com.killcoding.jsplus.Main exits
#      right after the -Task completes (otherwise it blocks on the
#      interactive scanner, see Main.java scanner loop).
#   4b) Run LoadTest.js (DB CRUD load test over the same testdb) and write
#      docs/latest/report/LoadTest-Report.html. Any load failure fails the run.
#   4c) Run TestDatasource.sh LAST (after every H2-dependent step): its C17
#      case stops and restarts the H2 server (pool disconnect/resume proof)
#      and the restarted H2 is not guaranteed to stay alive, so the suite
#      must never run before suites that need :3306 up (e.g. TestClock's
#      background time-refresh cases). Contained here, right before the
#      StopH2/StopAll sweep below.
#   5) Exit with the JVM's exit code (0 = all checks passed, 1 = failure),
#      after stopping the H2 server started for this run.
# Override via env (same convention as JsPlus-Tomcat):
#      JsPlus_JavaDir_JsPlus-Core   - JDK directory under Java/ (default: default)
#      JsPlus_JvmParams_JsPlus-Core - JVM params (default: -Xms256m -Xmx1024m -Xss256k)
#      LOADTEST_ROUNDS / LOADTEST_TABLES - LoadTest.js tuning (default 100 / 5)
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

# CI checkouts often lose the executable bit on helper scripts (Git stores
# them as mode 644), so calling e.g. BG_StartH2.sh below would fail with
# "Permission denied" (exit 126). Make every helper executable up front -
# a no-op when the bit is already set.
chmod +x "${JS_PLUS_PATH}bin/"*.sh "${BIN_PATH}"*.sh 2>/dev/null || true

# ------------------------------------------------------------------
# Step 0: stop leftover JsPlus/H2 JVMs first (project convention: every
# build/test script runs StopAll up front) so a stale H2 ghost, a locked
# testdb file or a JVM pinning an old JAR cannot interfere with the db
# init and the suite JVM below.
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

# Same fallback for the jar tool (used when archiving the test report jar):
# CI checkouts do not ship Java/<JAVA_DIR>, so the bundled jar.exe/binary is
# not present and we must fall back to the system JDK's jar, exactly like
# JAVA_BIN above.
JAR_BIN="${JS_PLUS_PATH}../Java/${JAVA_DIR}/bin/jar"
if [ ! -f "$JAR_BIN" ]; then
    if [ -n "$JAVA_HOME" ]; then
        JAR_BIN="$JAVA_HOME/bin/jar"
    else
        JAR_BIN="jar"
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
    # ------------------------------------------------------------------
    # (Re)initialize the test database, then start the H2 server.
    # The DB file is locked while a server holds it, so on a fresh run the
    # database is first (re)built via the EMBEDDED local-file URL
    # (jdbc:h2:<abs>/Database/testdb - StartH2Console init, same reset
    # semantics as JsPlus-Tomcat's "StartH2Console init tomcatdb") and only
    # then the TCP server is started on top of it. Embedded init
    # auto-creates the file with the default admin (sa, empty password) and
    # change.sql adds the application account sample_user - so a fresh CI
    # checkout never hits the 28000 auth error (the 'sa' user always exists).
    # ------------------------------------------------------------------
    echo "[TestAll] Initializing database (fallback.sql -> change.sql)..."
    mkdir -p "${JS_PLUS_PATH}../Database"
    "${JS_PLUS_PATH}bin/StartH2Console.sh" init testdb
    # Step 2b: initialize the dedicated dbsync SOURCE/TARGET file databases
    # (dbscript/fallback_sync.sql -> change_sync.sql). The dbsync suite
    # (TestDBSync.js) syncs exclusively between these two file DBs and never
    # touches the shared testdb.
    "${JS_PLUS_PATH}bin/InitDBSyncDB.sh"
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
"$JAVA_BIN" $JvmParams -DAPP_REMOTE_FILE="${JS_PLUS_PATH}config/RemoteFile.properties" -DAPP_CLOCK="${JS_PLUS_PATH}config/Clock.properties" -DAPP_IDENTITY_CARD="${JS_PLUS_PATH}config/IdentityCard.properties" -DAPP_DATASOURCE="${JS_PLUS_PATH}config/TestDatasource.properties" -cp "$DEPEND_LIBS" com.killcoding.jsplus.Main -ScriptEngine "$SCRIPT_ENGINE" -Env script -WriteHistory false -Task "${JS_PLUS_PATH}../test/TestAll.js" < /dev/null
RC=$?
set -e
echo "TestAll exit code: $RC"

# ------------------------------------------------------------------
# Step 3b: Run LoadTest.js against this JVM (DB CRUD load test, same H2
# server and freshly initialized testdb as the suites above). Uses the
# default runtime datasource (config/DataSource.properties) exactly like
# test/LoadTest.sh; writes docs/latest/report/LoadTest-Report.html. Any load
# failure is merged into the overall exit code below.
# ------------------------------------------------------------------
export JSPLUS_TEST_EXIT=1
set +e
"$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" com.killcoding.jsplus.Main -ScriptEngine "$SCRIPT_ENGINE" -Env script -WriteHistory false -Task "${JS_PLUS_PATH}../test/LoadTest.js" < /dev/null
RC_LOAD=$?
set -e
echo "LoadTest exit code: $RC_LOAD"
if [ "$RC_LOAD" != "0" ]; then
    RC="$RC_LOAD"
fi

# ------------------------------------------------------------------
# Step 4c: TestDatasource pool suite runs LAST (after every suite that
# depends on a live H2). Its C17 case deliberately stops and restarts the
# H2 server to prove the pool's disconnect/resume behaviour; the restarted
# H2 is not guaranteed to stay alive, so the suite must NOT run before
# TestClock's background-refresh cases (they need :3306 up). Running it
# here, right before the StopH2/StopAll sweep, keeps the restarted-server
# instability contained to this suite only. TestDatasource.sh reuses the
# already-running H2 (its :3306 check below skips the init/start step) and
# its own exit code is merged into the overall RC below.
# ------------------------------------------------------------------
echo "[TestAll] Running TestDatasource suite (last, H2 lifecycle-sensitive)..."
set +e
"${BIN_PATH}TestDatasource.sh" "$SCRIPT_ENGINE"
RC_DS=$?
set -e
echo "TestDatasource exit code: $RC_DS"
if [ "$RC_DS" != "0" ]; then
    RC="$RC_DS"
fi

# ------------------------------------------------------------------
# Step 4d: Merge the TestDatasource outcome into the TestAll report
# (TestDatasource runs in its own JVM AFTER TestAll.js, so its summary
# and per-case table are appended here - the TestAll report must reflect
# the full run, not just the suites TestAll.js ran itself).
# ------------------------------------------------------------------
REPORT_DIR="${JS_PLUS_PATH}../docs/latest/report"
if [ -f "$REPORT_DIR/TestDatasource-Report.md" ]; then
    echo "[TestAll] Merging TestDatasource result into TestAll report..."
    echo "" >> "$REPORT_DIR/TestAll-Report.md"
    echo "## TestDatasource" >> "$REPORT_DIR/TestAll-Report.md"
    echo "" >> "$REPORT_DIR/TestAll-Report.md"
    cat "$REPORT_DIR/TestDatasource-Report.md" >> "$REPORT_DIR/TestAll-Report.md"
    python3 - "$REPORT_DIR" <<'PYEOF' || echo "WARN: Failed to merge TestDatasource into TestAll-Report.html"
import re, sys
d = sys.argv[1]
m = open(d + "/TestDatasource-Report.md", encoding="utf-8").read()
h = open(d + "/TestDatasource-Report.html", encoding="utf-8").read()
sum_m = re.search(r"\*\*(\d+) passed / (\d+) failed\*\*", m)
sp, sf = (sum_m.group(1), sum_m.group(2)) if sum_m else ("?", "?")
cls = "summary-pass" if sf == "0" else "summary-fail"
tbl = re.search(r"<table.*?</table>", h, re.S)
if tbl:
    p = d + "/TestAll-Report.html"
    o = open(p, encoding="utf-8").read()
    block = "\n<h2>TestDatasource</h2>\n<div class='" + cls + "'>" + sp + " passed / " + sf + " failed</div>\n" + tbl.group(0) + "\n<p><a href='TestDatasource-Report.html'>Full TestDatasource report</a></p>\n"
    open(p, "w", encoding="utf-8").write(o.replace("</table>", "</table>" + block, 1))
PYEOF
fi

# ------------------------------------------------------------------
# Step 5: stop the H2 server started for this run (java ghost keyed by
# script/StartH2.js) so TestAll leaves nothing behind. RC was captured
# above. Then, per the project convention, the run finishes with a full
# StopAll sweep (AppRootDir path match) so NO JsPlus/H2 JVM survives this
# run - any leftover JVM that escaped the precise recycle is killed here.
# ------------------------------------------------------------------
pkill -f 'script/StartH2.js' 2>/dev/null || true
"${JS_PLUS_PATH}bin/StopAll.sh" || true

# ------------------------------------------------------------------
# Step 5b: Archive the test reports under the same docs layout as the
# javadoc site: docs/reports/<version>.jar (one jar per run) plus
# docs/latest/report/ as the browsable newest reports. Version is taken
# from the core jar name. A failure to archive is only a WARN and never
# changes the suite exit code (RC was captured above).
# ------------------------------------------------------------------
CORE_VERSION="$(basename "$CORE_JAR" .jar)"
CORE_VERSION="${CORE_VERSION#jsplus-core-}"
if [ -z "$CORE_VERSION" ]; then
    echo "WARN: Cannot extract version from $CORE_JAR, skipping report archive."
else
    echo "[TestAll] Archiving test reports as docs/reports/${CORE_VERSION}.jar (docs/latest/report)..."
    mkdir -p "${JS_PLUS_PATH_ORIGIN}../docs/reports" "${JS_PLUS_PATH_ORIGIN}../docs/latest/report"
    rm -f "${JS_PLUS_PATH_ORIGIN}../docs/reports/${CORE_VERSION}.jar"
    "${JAR_BIN}" cf "${JS_PLUS_PATH_ORIGIN}../docs/reports/${CORE_VERSION}.jar" -C "${JS_PLUS_PATH_ORIGIN}../docs/latest/report" . || {
        echo "WARN: Failed to pack test report jar (jar missing?), continuing."
    }
    # Keep only the 10 newest report jars - the reports archive is owned by
    # TestAll (ant only prunes its own docs/apis tree), so this suite bounds
    # it here, right after the new jar is packed.
    python3 - "${JS_PLUS_PATH_ORIGIN}../docs/reports" <<'PYEOF' || echo "WARN: Failed to prune docs/reports"
import os, sys
d = sys.argv[1]
try:
    jars = sorted([os.path.join(d, f) for f in os.listdir(d) if f.endswith('.jar')],
                  key=lambda p: os.path.getmtime(p), reverse=True)
except OSError:
    sys.exit(0)
for p in jars[10:]:
    try:
        os.remove(p)
    except OSError as e:
        print("WARN: could not delete %s: %s" % (p, e))
if len(jars) > 10:
    print("[TestAll] Pruned docs/reports to newest 10 (%d jars kept)" % len(jars[:10]))
PYEOF
fi
exit "$RC"
