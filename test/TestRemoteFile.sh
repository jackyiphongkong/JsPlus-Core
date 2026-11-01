#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"$SCRIPT_DIR/_RunOne.sh" TestRemoteFile "$@"
exit $?