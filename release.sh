#!/bin/bash
set -e

# ------------------------------------------------------------------
# JsPlus-Core release helper - release.sh
# Scans every sibling project folder under the scan root (default
# D:/Working). Any project that contains a LibHome/jsplus/default
# folder is a release target: the freshly built jsplus-core-<version>
# .jar is copied into its LibHome/jsplus/default. The previous
# jsplus-core-*.jar there is renamed to *.jar.bak (not deleted);
# other jars are left untouched.
# The source project itself (JsPlus-Core) is skipped.
# Scan root   : default D:/Working, override via JS_PLUS_RELEASE_ROOT
# Exit code   : 0 = success, 1 = failure
# ------------------------------------------------------------------

CORE_ROOT="$(cd "$(dirname "$0")" && pwd)"

JAR_DIR="$CORE_ROOT/LibHome/jsplus/default"
if [ ! -d "$JAR_DIR" ]; then
    echo "[release] ERROR: build output dir not found: $JAR_DIR"
    exit 1
fi

LATEST_JAR="$(ls -1 "$JAR_DIR"/jsplus-core-*.jar 2>/dev/null | sort -V | tail -n 1)"
if [ -z "$LATEST_JAR" ]; then
    echo "[release] ERROR: no jsplus-core-*.jar under $JAR_DIR - run 'ant build' first."
    exit 1
fi
SRC_JAR="$JAR_DIR/$LATEST_JAR"
echo "[release] Source : $SRC_JAR"

SCAN_ROOT="${JS_PLUS_RELEASE_ROOT:-D:/Working}"
COPIED=0

for PROJ in "$SCAN_ROOT"/*/; do
    [ -d "$PROJ" ] || continue
    PROJ="${PROJ%/}"
    [ "$PROJ" = "$CORE_ROOT" ] && continue
    TGT_DIR="$PROJ/LibHome/jsplus/default"
    if [ -d "$TGT_DIR" ]; then
        echo "[release] Target : $TGT_DIR"
        for OLD in "$TGT_DIR"/jsplus-core-*.jar; do
            [ -e "$OLD" ] || continue
            mv -f "$OLD" "$OLD.bak"
        done
        cp -f "$SRC_JAR" "$TGT_DIR/$LATEST_JAR"
        echo "[release]   Copied : $TGT_DIR/$LATEST_JAR"
        COPIED=$((COPIED+1))
    fi
done

if [ "$COPIED" -eq 0 ]; then
    echo "[release] No target project found under $SCAN_ROOT (none has LibHome/jsplus/default)."
fi
echo "[release] Done."
exit 0
