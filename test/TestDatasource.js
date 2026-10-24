// ============================================================================
// JsPlus-Core TestDatasource - datasource package test suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestDatasource.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/test_reports/TestDatasource-Report.html / .md
// Coverage : com.killcoding.datasource.Clock (pure-logic clock facade).
// SCOPE    : The JDBC-dependent classes (DriverDataSource / DriverConnection /
//            ConnectionPool / DriverExecutor / CacheDriverExecutor / IdentityCard)
//            require a JDBC driver and a configured DataSource; none is bundled
//            in LibHome/Core/default/lib, so they are intentionally excluded from
//            this headless suite (see the scope note check, C5).
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

var REPORT_NAME = "TestDatasource-Report.html";
var REPORT_TITLE = "JsPlus-Core Datasource Test Report";

var Clock = Java.type("com.killcoding.datasource.Clock");

print("=== JsPlus-Core Datasource Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));

var clock = new Clock();

// Case C1
var d = null;
var dErr = "";
try {
    d = clock.getDate();
} catch (e) {
    dErr = "" + e;
}
check({
    name: "Clock().getDate() non-null",
    ok: d !== null && d !== undefined && dErr.length === 0,
    detail: dErr.length > 0 ? dErr : ("" + d),
    objective: "Verify the local clock facade returns a date without a remote clock",
    objectiveZh: "驗證本地時鐘外觀在無遠端時鐘時回傳日期",
    command: "new Clock().getDate()"
});

// Case C2
var t = 0;
var tErr = "";
try {
    t = clock.getTime();
} catch (e) {
    tErr = "" + e;
}
check({
    name: "Clock().getTime() > 0",
    ok: t > 0 && tErr.length === 0,
    detail: tErr.length > 0 ? tErr : ("time=" + t),
    objective: "Verify getTime() returns a positive epoch millis value",
    objectiveZh: "驗證 getTime() 回傳正數的紀元毫秒值",
    command: "clock.getTime()"
});

// Case C3
var sqlTs = null;
var localDate = null;
var cal = null;
var c3Err = "";
try {
    sqlTs = clock.getSqlTimestamp();
    localDate = clock.getLocalDate();
    cal = clock.getCalendar();
} catch (e) {
    c3Err = "" + e;
}
check({
    name: "Clock().getSqlTimestamp/getLocalDate/getCalendar non-null",
    ok: sqlTs !== null && localDate !== null && cal !== null && c3Err.length === 0,
    detail: c3Err.length > 0 ? c3Err : ("ts=" + sqlTs + ", localDate=" + localDate),
    objective: "Verify the typed time accessors return usable values",
    objectiveZh: "驗證型別化時間存取器回傳可用值",
    command: "clock.getSqlTimestamp(); clock.getLocalDate(); clock.getCalendar()"
});

// Case C4
var eq = false;
var eqErr = "";
try {
    eq = (clock.getDate().getTime() === clock.getTime());
} catch (e) {
    eqErr = "" + e;
}
check({
    name: "Clock().getDate().getTime() equals getTime()",
    ok: eq && eqErr.length === 0,
    detail: eqErr.length > 0 ? eqErr : ("date=" + clock.getDate().getTime() + ", time=" + clock.getTime()),
    objective: "Verify date and millis views of the clock agree",
    objectiveZh: "驗證時鐘的日期與毫秒視圖一致",
    command: "clock.getDate().getTime() === clock.getTime()"
});

// Case C5 - scope note (informational, always passes)
check({
    name: "JDBC-dependent classes excluded (scope note)",
    ok: true,
    detail: "DriverDataSource/DriverConnection/ConnectionPool/DriverExecutor/CacheDriverExecutor/IdentityCard need a JDBC driver + configured DataSource; none is bundled in LibHome/Core/default/lib, so this headless suite covers Clock (pure logic) only.",
    objective: "Document the headless scope of this suite",
    objectiveZh: "記錄此套件的無外部依賴測試範圍",
    command: "n/a"
});

writeReport();
writeMd();
