#!/bin/bash
set -eu

ScriptDir="$(cd "$(dirname "$0")" && pwd)"

# 專案根：只有命令行含其下路徑的進程才符合，避免跨專案誤殺
ProjRoot="$(dirname "$(dirname "$ScriptDir")")"

if [ "$#" -eq 0 ]; then
    echo "Usage: $(basename "$0") <StartXxx.sh>"
    echo "  e.g. $(basename "$0") StartH2.sh"
    exit 1
fi

Arg="$1"
case "$Arg" in
    /*) TargetSh="$Arg" ;;
    *)  TargetSh="${ScriptDir}/${Arg}" ;;
esac
if [ -d "$(dirname "$TargetSh")" ]; then
    TargetSh="$(cd "$(dirname "$TargetSh")" && pwd)/$(basename "$TargetSh")"
fi

# 從檔名推導關鍵字：StartH2.sh -> StartH2
Base="$(basename "$Arg")"
Base="${Base%.*}"

if [ -z "$Base" ]; then
    echo "[ERROR] Cannot derive keyword from \"$Arg\"."
    exit 1
fi

echo "=============================================="
echo "TargetSh    : $TargetSh"
echo "Keyword     : $Base"
echo "ProjectRoot : $ProjRoot"
echo "=============================================="
echo "Searching and terminating Java processes..."
echo "(only processes whose command line holds a path under ProjectRoot are eligible,"
echo " so a launcher in another project that happens to share the script or jar name is skipped)"

# 尾隨斜線不可省：它保證匹配的是路徑段而不是名字前綴
rootPath="${ProjRoot%/}/"

matched=0
# -ww 關閉欄寬截斷，否則長 -cp 會被切掉導致匹配失敗；
# comm 精確判 java/javaw，避免舊寫法 grep 'java' 抓到命令行裡含該字樣的無關進程
while read -r pid comm args; do
    case "$comm" in
        java|javaw) ;;
        *) continue ;;
    esac
    case "$args" in
        *"$rootPath"*) ;;
        *) continue ;;
    esac
    case "$args" in
        *"$Base"*) ;;
        *) continue ;;
    esac

    echo "Terminating PID: $pid"
    echo "  CMD: $args"
    kill -9 "$pid" 2>/dev/null || true
    matched=$((matched + 1))
done < <(ps -ww -eo pid=,comm=,args= 2>/dev/null || true)

if [ "$matched" -eq 0 ]; then
    echo "No matching Java processes found."
else
    echo "Terminated ${matched} process(es)."
fi

echo
