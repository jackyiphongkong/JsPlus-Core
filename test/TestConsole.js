// ============================================================================
// JsPlus-Core TestConsole - console environment test suite
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestConsole.js))
// Harness  : Shared plumbing (Java bindings, counters, check and writeReport)
//            lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/test_reports/TestConsole-Report.html (green PASS /
//            red FAIL per check), written with DiskFile.
// Coverage : The JsPlus console environment the other suites rely on:
//            JS_PLUS_PATH resolution, global Console object, evalScript/load/
//            print, Java interop (Java.type / ArrayList / HashMap) and a
//            DiskFile write/read roundtrip.
// ============================================================================

var System = Java.type("java.lang.System");
var basePath = System.getProperty("JS_PLUS_PATH");
try {
    if (basePath === null || basePath === undefined || basePath.length === 0) {
        throw "JS_PLUS_PATH is not set - cannot locate TestBase.js";
    }
    load(basePath + "../test/TestBase.js");
} catch (e) {
    print("[ERROR] Failed to load TestBase.js: " + e);
    throw e;
}
beginTest();

// Report file name and title for this suite (consumed by writeReport in TestBase.js).
var REPORT_NAME = "TestConsole-Report.html";
var REPORT_TITLE = "JsPlus-Core Console Environment Test Report";

print("=== JsPlus-Core Console Environment Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));
print("Mode        : " + (CONSOLE_MODE ? "console" : "standalone"));

// Case C1
var baseDirOk = false;
var baseDirDetail = "";
try {
    baseDirOk = (new DiskFile(basePath)).isDirectory();
    baseDirDetail = basePath + (baseDirOk ? " (dir)" : " (NOT a directory)");
} catch (e) {
    baseDirDetail = "" + e;
}
check({
    name: "JS_PLUS_PATH is set and points to an existing directory",
    ok: baseDirOk,
    detail: baseDirDetail,
    objective: "Ensure the console resolved JS_PLUS_PATH to an existing runtime anchor directory",
    objectiveZh: "確保主控台已將 JS_PLUS_PATH 解析為存在的執行期錨點目錄",
    command: "System.getProperty(\"JS_PLUS_PATH\")"
});

// Case C2
check({
    name: "global Console object exists",
    ok: CONSOLE_MODE,
    detail: "typeof Console = " + (typeof Console),
    objective: "Ensure the JsPlus console exposes the global Console binding",
    objectiveZh: "確保 JsPlus 主控台暴露全域 Console 綁定",
    command: "typeof Console"
});

// Case C3
var evalOk = false;
var evalVal = "";
try {
    evalVal = "" + Console.evalScript("1 + 2");
    evalOk = (evalVal === "3");
} catch (e) {
    evalVal = "EXCEPTION: " + e;
}
check({
    name: "Console.evalScript('1 + 2') === 3",
    ok: evalOk,
    detail: evalVal,
    objective: "Verify inline JavaScript evaluation works through the console",
    objectiveZh: "驗證透過主控台執行內聯 JavaScript 求值正常",
    command: "Console.evalScript(\"1 + 2\")"
});

// Case C4
check({
    name: "load() function available",
    ok: (typeof load === "function"),
    detail: "typeof load = " + (typeof load),
    objective: "Ensure suites can load each other via load()",
    objectiveZh: "確保套件可透過 load() 相互載入",
    command: "typeof load"
});

// Case C5
var printOk = true;
try {
    print("[TestConsole] print() works");
} catch (e) {
    printOk = false;
}
check({
    name: "print() works",
    ok: printOk,
    detail: printOk ? "console output OK" : "print() threw an exception",
    objective: "Ensure console output (print) is available",
    objectiveZh: "確保主控台輸出（print）可用",
    command: "print(\"...\")"
});

// Case C6
var ArrayList = Java.type("java.util.ArrayList");
var list = new ArrayList();
list.add("a");
list.add("b");
check({
    name: "Java.type + ArrayList interop",
    ok: (list.size() === 2),
    detail: "list.size() = " + list.size(),
    objective: "Verify Java type interop and collection usage from JavaScript",
    objectiveZh: "驗證 JavaScript 中 Java 型別互操作與集合使用",
    command: "Java.type(\"java.util.ArrayList\"); list.add(\"a\"); list.size()"
});

// Case C7
var HashMap = Java.type("java.util.HashMap");
var hm = new HashMap();
hm.put("k", "v");
check({
    name: "HashMap interop",
    ok: ("v" === ("" + hm.get("k"))),
    detail: "hm.get(\"k\") = " + hm.get("k"),
    objective: "Verify Map interop (used by ConfigProperties / API params in later suites)",
    objectiveZh: "驗證 Map 互操作（後續套件的 ConfigProperties / API 參數會用到）",
    command: "new HashMap().put(\"k\", \"v\"); hm.get(\"k\")"
});

// Case C8
var ctxOk = false;
try {
    var ctx = Console.getContext();
    ctxOk = (ctx !== null && ctx !== undefined);
} catch (e) {
    ctxOk = false;
}
check({
    name: "Console.getContext() available",
    ok: ctxOk,
    detail: ctxOk ? "context bound" : "getContext() unavailable",
    objective: "Ensure Console.getContext() can be reached (used by interactive consoles)",
    objectiveZh: "確保可取得 Console.getContext()（互動式主控台使用）",
    command: "Console.getContext()"
});

// Case C9
var dfPath = basePath + "../test/.core-test-tmp/console-write.txt";
try {
    var tmpFile = new DiskFile(dfPath);
    if (tmpFile.exists()) {
        tmpFile.delete();
    }
    tmpFile.write("[TestConsole] console write check", "UTF-8");
    var readBack = tmpFile.readAllString("UTF-8");
    check({
        name: "DiskFile write/read via console",
        ok: readBack.indexOf("console write check") >= 0,
        detail: readBack,
        objective: "Verify file I/O through DiskFile works from the console (reports depend on it)",
        objectiveZh: "驗證主控台可透過 DiskFile 進行檔案讀寫（報告依賴此能力）",
        command: "new DiskFile(path).write(text, \"UTF-8\"); readAllString(\"UTF-8\")"
    });
    if (tmpFile.exists()) {
        tmpFile.delete();
    }
} catch (e) {
    check({
        name: "DiskFile write/read via console",
        ok: false,
        detail: "EXCEPTION: " + e,
        objective: "Verify file I/O through DiskFile works from the console (reports depend on it)",
        objectiveZh: "驗證主控台可透過 DiskFile 進行檔案讀寫（報告依賴此能力）",
        command: "new DiskFile(path).write(text, \"UTF-8\"); readAllString(\"UTF-8\")"
    });
}

writeReport();
writeMd();
