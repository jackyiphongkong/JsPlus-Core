// ============================================================================
// JsPlus-Core TestLog - log package test suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestLog.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/test_reports/TestLog-Report.html / .md
// Coverage : com.killcoding.log
//            - LoggerFactory.getLogger
//            - Logger level state (INFO from JsPlus/config/Logger.properties)
//            - log.info/warn/error/mark/debug do not throw
//            - FileOut=true writes <PROJECT_ROOT>/logs/app/<yyyyMMdd>.log
//              containing the marker message (LogRecord async flush, OutLogTimer=1s)
//            - Logger.setLevel("DEBUG") flips isDebugEnabled
// PREREQ   : <PROJECT_ROOT>/JsPlus/config/Logger.properties is loaded by
//            ScriptConsole.initEnv() at startup (Level=INFO, FileOut=true,
//            FileOutFolder=${JS_PLUS_PATH}/../logs/app/).
// ============================================================================

var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
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

var REPORT_NAME = "TestLog-Report.html";
var REPORT_TITLE = "JsPlus-Core Log Test Report";

var LoggerFactory = Java.type("com.killcoding.log.LoggerFactory");
var Logger = Java.type("com.killcoding.log.Logger");
var LogRecord = Java.type("com.killcoding.log.LogRecord");
var CommonTools = Java.type("com.killcoding.tool.CommonTools");

print("=== JsPlus-Core Log Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));

var log = LoggerFactory.getLogger("com.killcoding.test.TestLog");
var marker = "HelloCoreLog-" + CommonTools.generateId(8);

// Case C1
check({
    name: "LoggerFactory.getLogger returns a logger",
    ok: log !== null && log !== undefined,
    detail: "logger=" + log,
    objective: "Verify a logger can be obtained for a test class name",
    objectiveZh: "驗證可為測試類別名稱取得 logger",
    command: "LoggerFactory.getLogger(\"com.killcoding.test.TestLog\")"
});

// Case C2
check({
    name: "INFO level enabled (Logger.properties Level=INFO)",
    ok: log.isInfoEnabled(),
    detail: "isInfoEnabled=" + log.isInfoEnabled() + ", isDebugEnabled=" + log.isDebugEnabled(),
    objective: "Verify the runtime log config (Level=INFO) controls logger state",
    objectiveZh: "驗證執行期日誌設定（Level=INFO）控制 logger 狀態",
    command: "log.isInfoEnabled()"
});

// Case C3
var logOk = true;
var logErr = "";
try {
    log.info("C3 " + marker + " info");
    log.warn("C3 " + marker + " warn");
    log.error("C3 " + marker + " error");
    log.mark("C3 " + marker + " mark");
    log.debug("C3 " + marker + " debug");
} catch (e) {
    logOk = false;
    logErr = "" + e;
}
check({
    name: "log.info/warn/error/mark/debug do not throw",
    ok: logOk,
    detail: logOk ? "all level calls accepted" : logErr,
    objective: "Verify every logger level method accepts a message without throwing",
    objectiveZh: "驗證各等級日誌方法接受訊息且不拋例外",
    command: "log.info/warn/error/mark/debug(message)"
});

// Case C4
var lfn = LogRecord.getLogFileName();
var logDir = basePath + "../logs/app";
var fileFound = false;
var fileDetail = logDir + "/" + lfn;
try {
    for (var li = 0; li < 12; li++) {
        var logFile = new DiskFile(logDir + "/" + lfn);
        if (logFile.exists()) {
            var content = logFile.readAllString("UTF-8");
            if (content.indexOf(marker) >= 0) {
                fileFound = true;
                break;
            }
        }
        Thread.sleep(500);
    }
} catch (e) {
    fileDetail = fileDetail + " (check error: " + e + ")";
}
check({
    name: "Log file contains the marker message",
    ok: fileFound,
    detail: fileFound ? ("found in " + fileDetail) : ("NOT found in " + fileDetail + " after 6s"),
    objective: "Verify FileOut=true persists log lines to <PROJECT_ROOT>/logs/app/<yyyyMMdd>.log",
    objectiveZh: "驗證 FileOut=true 將日誌寫入 <PROJECT_ROOT>/logs/app/<yyyyMMdd>.log",
    command: "LogRecord.getLogFileName(); DiskFile(logDir + \"/\" + lfn).readAllString(\"UTF-8\")"
});

// Case C5
Logger.setLevel("DEBUG");
var dbgEnabled = log.isDebugEnabled();
Logger.setLevel("INFO");
check({
    name: "Logger.setLevel('DEBUG') enables debug",
    ok: dbgEnabled && !log.isDebugEnabled(),
    detail: "debug while DEBUG level=" + dbgEnabled + ", debug after restore INFO=" + log.isDebugEnabled(),
    objective: "Verify setLevel(String) flips the level and can be restored",
    objectiveZh: "驗證 setLevel(String) 切換等級並可還原",
    command: "Logger.setLevel(\"DEBUG\"); log.isDebugEnabled(); Logger.setLevel(\"INFO\")"
});

writeReport();
writeMd();
