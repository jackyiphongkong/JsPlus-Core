#!/bin/bash
set -e

# ------------------------------------------------------------------
# JsPlus-Core DBSync database initializer
# Resets + rebuilds BOTH dedicated dbsync file databases:
#   Database/dbsync_source  (DBSyncSource.properties) - sync SOURCE
#   Database/dbsync_target  (DBSyncTarget.properties) - sync TARGET
# by applying dbscript/fallback_sync.sql (DROP all) then
# dbscript/change_sync.sql (CREATE all) to each file DB via the
# embedded H2 RunScript tool. No H2 TCP server is required.
# TestAll.bat/sh calls this right after 'init testdb' so the dbsync
# suite never touches the shared testdb.
# ------------------------------------------------------------------

BIN_PATH="$(cd "$(dirname "$0")" && pwd)/"
JS_PLUS_PATH_ORIGIN="${BIN_PATH}../"
JS_PLUS_PATH="${JS_PLUS_PATH_ORIGIN//\\//}"
LIB_HOME="${JS_PLUS_PATH}../LibHome/"

DEPEND_LIBS="$(find "${LIB_HOME}" -type f -name '*.jar' | tr '\n' ':')"
DEPEND_LIBS="${DEPEND_LIBS%:}"
if [ -z "$DEPEND_LIBS" ]; then
    echo "Error: No JARs found under LibHome"
    exit 1
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
JvmParams="${JvmParams:--Xms128m -Xmx128m -Xss256k}"

for D in dbsync_source dbsync_target; do
    echo "[InitDBSyncDB] Initializing ${D} (fallback_sync.sql -> change_sync.sql)..."
    "$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" org.h2.tools.RunScript \
        -url "jdbc:h2:${JS_PLUS_PATH}../Database/${D}" \
        -user sample_user -password sample_password \
        -script "${JS_PLUS_PATH}../dbscript/fallback_sync.sql" -checkResults
    "$JAVA_BIN" $JvmParams -cp "$DEPEND_LIBS" org.h2.tools.RunScript \
        -url "jdbc:h2:${JS_PLUS_PATH}../Database/${D}" \
        -user sample_user -password sample_password \
        -script "${JS_PLUS_PATH}../dbscript/change_sync.sql" -checkResults
done
echo "[InitDBSyncDB] dbsync databases initialized (8 tables + sample_user each)."
