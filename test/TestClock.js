// ============================================================================
// JsPlus-Core TestClock - Clock facade (com.killcoding.datasource.Clock) test
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestClock.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestClock-Report.html (TestBase
//            format, same as the other suites)
// Scope    : com.killcoding.datasource.Clock - the time facade used across
//            JsPlus (getDate/getTime/getSqlTimestamp/getLocalDate/...).
//            config/Clock.properties is provided (Enable=true,
//            DataSource=config/DataSource.properties, GapCachePeriod=60s,
//            GetAppClockSql tracks the DB server time with an offset). The
//            remote time is fetched once and its gap (local - remote) is
//            cached via Cache.set with a life of GapCachePeriod, so hot-path
//            calls never hit the DB again; an expired entry re-fetches once.
//            C12-C14 prove the Cache-based gap cache: a +10s GetAppClockSql
//            offset is tracked (C12), kept while the cache is fresh (C13) and
//            re-calibrated after the cache expires (C14). Both
//            "CURRENT_TIMESTAMP [+-] N SECOND" and
//            "CURRENT_TIMESTAMP [+-] INTERVAL 'N' HOUR" SQL forms are
//            recognised when deriving the expected offset.
// ============================================================================

var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var Clock = Java.type("com.killcoding.datasource.Clock");
var Cache = Java.type("com.killcoding.cache.Cache");
var JsString = Java.type("com.killcoding.jsplus.JsString");
var Logger = Java.type("com.killcoding.log.Logger");

// Every case emits a Logger.systemMark("TestClock.js", "Cxx: ...") so the
// app log records which js file and which case is executing.
function caseMark(caseId, what) {
    Logger.systemMark("TestClock.js", caseId + ": " + what);
}

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

var REPORT_TITLE = "JsPlus-Core Clock Test";
var REPORT_NAME = "TestClock-Report.html";

beginTest();

print("=== JsPlus-Core Clock Test ===");
print("JS_PLUS_PATH: " + basePath);

var Paths = Java.type("java.nio.file.Paths");
var Files = Java.type("java.nio.file.Files");
var StandardCharsets = Java.type("java.nio.charset.StandardCharsets");
var LocalDateTime = Java.type("java.time.LocalDateTime");
var LocalDate = Java.type("java.time.LocalDate");
var ZoneId = Java.type("java.time.ZoneId");
var clockPropsPath = basePath + "config/Clock.properties";

function readClockProps() {
    // Wrap the raw bytes in a JsString (never auto-converted by GraalJS),
    // so every String method stays callable from JS (getBytes, split, ...).
    return new JsString(Files.readAllBytes(Paths.get(clockPropsPath)), StandardCharsets.UTF_8);
}
function writeClockProps(jsStr) {
    Files.write(Paths.get(clockPropsPath), jsStr.getBytes(StandardCharsets.UTF_8));
}
function waitForSqlContains(marker, timeoutMs) {
    var deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
        try {
            var sql = Clock.getConfigProperties().getString("GetAppClockSql");
            if (sql !== null && sql !== undefined && sql.indexOf(marker) >= 0) {
                return true;
            }
        } catch (e) { }
        Thread.sleep(200);
    }
    return false;
}
function extractGetAppClockSqlLine(jsStr) {
    // JsString.split returns String[]; GraalJS converts each element to a
    // JS primitive string, so plain JS indexOf works on the elements.
    var lines = jsStr.split("\n");
    for (var i = 0; i < lines.length; i++) {
        if (lines[i].indexOf("GetAppClockSql=") === 0) {
            return lines[i];
        }
    }
    return null;
}
function replaceLine(jsStr, key, newLine) {
    var lines = jsStr.split("\n");
    for (var i = 0; i < lines.length; i++) {
        if (lines[i].indexOf(key + "=") === 0) {
            lines[i] = newLine;
        }
    }
    return JsString.valueOf(lines.join("\n"));
}
function parseOffsetMs(sqlLine) {
    // Uses java.util.regex (not JS regex) - the GraalVM JS regex engine
    // (TRegex) is not registered in this embedding, so JS regex literals
    // fail with "No language for id regex found".
    var Pattern = Java.type("java.util.regex.Pattern");
    if (sqlLine === null || sqlLine === undefined) return 0;
    var javaSql = JsString.valueOf(sqlLine).asString();
    // "CURRENT_TIMESTAMP [+-] INTERVAL 'N' HOUR/MINUTE/SECOND/DAY"
    var m1 = Pattern.compile(
        "CURRENT_TIMESTAMP\\s*([+-])\\s*INTERVAL\\s*'(\\d+)'\\s*(HOUR|MINUTE|SECOND|DAY)",
        Pattern.CASE_INSENSITIVE).matcher(javaSql);
    if (m1.find()) {
        var mult = m1.group(3).toUpperCase() === "HOUR" ? 3600000
            : m1.group(3).toUpperCase() === "MINUTE" ? 60000
            : m1.group(3).toUpperCase() === "DAY" ? 86400000 : 1000;
        var v = parseInt("" + m1.group(2), 10) * mult;
        // group() returns a JS primitive string here (GraalJS converts it),
        // so compare with === rather than Java equals.
        return m1.group(1) === "-" ? -v : v;
    }
    // "CURRENT_TIMESTAMP [+-] N SECOND/MINUTE/HOUR/DAY"
    var m2 = Pattern.compile(
        "CURRENT_TIMESTAMP\\s*([+-])\\s*(\\d+)\\s*(SECOND|MINUTE|HOUR|DAY)",
        Pattern.CASE_INSENSITIVE).matcher(javaSql);
    if (m2.find()) {
        var mult2 = m2.group(3).toUpperCase() === "HOUR" ? 3600000
            : m2.group(3).toUpperCase() === "MINUTE" ? 60000
            : m2.group(3).toUpperCase() === "DAY" ? 86400000 : 1000;
        var v2 = parseInt("" + m2.group(2), 10) * mult2;
        return m2.group(1) === "-" ? -v2 : v2;
    }
    return 0;
}

// ----------------------------------------------------------------------------
// C1-C11: facade basics (same Clock instance -> one consistent snapshot)
// ----------------------------------------------------------------------------
caseMark("C1", "getDate()/getTime() consistency");
var clock = new Clock();
var now = System.currentTimeMillis();

var d1 = clock.getDate();
check({ caseId: "C1", name: "Clock.getDate() returns a Date", ok: d1 !== null, detail: "getDate()=" + d1 });
check({ caseId: "C1", name: "getDate() consistent with getTime()", ok: Math.abs(d1.getTime() - clock.getTime()) < 5000,
    detail: "getDate()=" + d1.getTime() + " getTime()=" + clock.getTime() });

var ts2 = clock.getSqlTimestamp();
caseMark("C2", "getSqlTimestamp()/getTime() consistency");
check({ caseId: "C2", name: "Clock.getSqlTimestamp() returns a Timestamp", ok: ts2 !== null, detail: "getSqlTimestamp()=" + ts2 });
check({ caseId: "C2", name: "getSqlTimestamp() consistent with getTime()", ok: Math.abs(ts2.getTime() - clock.getTime()) < 5000,
    detail: "getSqlTimestamp()=" + ts2.getTime() + " getTime()=" + clock.getTime() });

caseMark("C3", "getLocalDate() returns a LocalDate");
var ld3 = clock.getLocalDate();
check({ caseId: "C3", name: "Clock.getLocalDate() returns a LocalDate", ok: ld3 !== null, detail: "getLocalDate()=" + ld3 });

caseMark("C4", "getCalendar()/getTime() consistency");
var cal4 = clock.getCalendar();
check({ caseId: "C4", name: "Clock.getCalendar() returns a Calendar", ok: cal4 !== null, detail: "getCalendar()=" + cal4 });
check({ caseId: "C4", name: "getCalendar() consistent with getTime()", ok: Math.abs(cal4.getTimeInMillis() - clock.getTime()) < 5000,
    detail: "getCalendar()=" + cal4.getTimeInMillis() + " getTime()=" + clock.getTime() });

caseMark("C5", "getSqlDate() returns a java.sql.Date");
var sd5 = clock.getSqlDate();
check({ caseId: "C5", name: "Clock.getSqlDate() returns a java.sql.Date", ok: sd5 !== null, detail: "getSqlDate()=" + sd5 });

caseMark("C6", "getSqlTime() returns a java.sql.Time");
var st6 = clock.getSqlTime();
check({ caseId: "C6", name: "Clock.getSqlTime() returns a java.sql.Time", ok: st6 !== null, detail: "getSqlTime()=" + st6 });

caseMark("C7", "getLocalDateTime() returns a LocalDateTime");
var ldt7 = clock.getLocalDateTime();
check({ caseId: "C7", name: "Clock.getLocalDateTime() returns a LocalDateTime", ok: ldt7 !== null, detail: "getLocalDateTime()=" + ldt7 });

caseMark("C8", "getDataSource() returns the configured DataSource");
check({ caseId: "C8", name: "Clock.getDataSource() returns the configured DataSource",
    ok: Clock.getDataSource() !== null, detail: "getDataSource()=" + Clock.getDataSource() });

caseMark("C9", "getConfigProperties() returns the loaded properties");
check({ caseId: "C9", name: "Clock.getConfigProperties() returns the loaded properties",
    ok: Clock.getConfigProperties() !== null, detail: "getConfigProperties()=" + Clock.getConfigProperties() });

caseMark("C10", "a new Clock instance agrees with the first");
var c10 = new Clock();
var diff10 = Math.abs(c10.getTime() - now);
check({ caseId: "C10", name: "a new Clock instance agrees with the first within 15s",
    ok: diff10 < 15000, detail: "first=" + now + " second=" + c10.getTime() + " diff=" + diff10 });

caseMark("C11", "getLocalDate() matches getCalendar() zone conversion");
var d11 = c10.getLocalDate();
var cal11 = c10.getCalendar();
var fromMillis = new java.util.Date(cal11.getTimeInMillis()).toInstant()
    .atZone(cal11.getTimeZone().toZoneId()).toLocalDate();
check({ caseId: "C11", name: "getLocalDate() matches getCalendar() zone conversion",
    ok: d11 !== null && d11.equals(fromMillis), detail: "getLocalDate()=" + d11 + " converted=" + fromMillis });

// ----------------------------------------------------------------------------
// C12-C14: Cache-based gap cache (uses a +10s GetAppClockSql offset)
// ----------------------------------------------------------------------------
var txt = readClockProps();
var origLine = extractGetAppClockSqlLine(txt);
var PLUS10_LINE = "GetAppClockSql=SELECT (CURRENT_TIMESTAMP + 10 SECOND) AS app_systime_date, 'GMT+8' AS app_time_zone";
var GAP_30S_LINE = "GapCachePeriod=30s";
var cacheKey = "Clock.gap";

try {
    // --- C12: after Cache.remove, the +10s offset must be tracked ---
    caseMark("C12", "+10s GetAppClockSql offset tracked via gap cache");
    var modified = replaceLine(replaceLine(txt, "GetAppClockSql", PLUS10_LINE), "GapCachePeriod", GAP_30S_LINE);
    writeClockProps(modified);
    Cache.remove(cacheKey);
    var reloaded12 = waitForSqlContains("+ 10 SECOND", 6000);
    var c12 = new Clock();
    var d12 = c12.getTime() - System.currentTimeMillis();
    check({ caseId: "C12", name: "+10s GetAppClockSql offset tracked via gap cache",
        ok: reloaded12 && d12 > 7500 && d12 < 12500,
        detail: "reloaded=" + reloaded12 + " diffMs=" + d12 });

    // --- C12b: date-level view of the +10s offset - Clock.getLocalDateTime()
    //          vs local LocalDateTime.now() must differ by ~10s, and the
    //          LocalDate parts must be the same day (or 1 day apart when the
    //          +10s straddles midnight). Uses the same C12 snapshot instance.
    caseMark("C12b", "Clock LocalDateTime vs local differs by ~10s");
    var ldtClock = c12.getLocalDateTime();
    var ldtNow = LocalDateTime.now();
    var ldtDiffSec = Math.abs(ldtClock.toEpochSecond(ZoneId.systemDefault())
        - ldtNow.toEpochSecond(ZoneId.systemDefault()));
    var dayDiff = Math.abs(ldtClock.toLocalDate().toEpochDay() - ldtNow.toLocalDate().toEpochDay());
    check({ caseId: "C12b", name: "Clock LocalDateTime vs local differs by ~10s",
        ok: ldtDiffSec >= 7 && ldtDiffSec <= 13 && dayDiff <= 1,
        detail: "clock=" + ldtClock + " local=" + ldtNow + " diffSec=" + ldtDiffSec + " dayDiff=" + dayDiff });

    // --- C13: SQL restored, cache still fresh (30s life) -> +10s kept ---
    caseMark("C13", "cache stays fresh after SQL restore (still +10s)");
    var c13Txt = JsString.valueOf(modified.asString().replace(PLUS10_LINE, origLine));
    writeClockProps(c13Txt);
    var marker13 = origLine.indexOf("=") >= 0 ? origLine.substring(origLine.indexOf("=") + 1, origLine.indexOf("=") + 40) : origLine;
    var restored13 = waitForSqlContains(marker13, 6000);
    var c13 = new Clock();
    var d13 = c13.getTime() - System.currentTimeMillis();
    check({ caseId: "C13", name: "cache stays fresh after SQL restore (still +10s)",
        ok: restored13 && d13 > 7500 && d13 < 12500,
        detail: "restored=" + restored13 + " diffMs=" + d13 });

    // --- C14: cache entry dropped (simulates expiry; real expiry is driven
    //          by the Cache life, 30s here) -> re-fetches and re-calibrates ---
    caseMark("C14", "cache miss re-calibrates to the original SQL offset");
    var origOffsetMs = parseOffsetMs(origLine);
    Cache.remove(cacheKey);
    var c14 = new Clock();
    var d14 = c14.getTime() - System.currentTimeMillis();
    check({ caseId: "C14", name: "cache miss re-calibrates to the original SQL offset",
        ok: Math.abs(d14 - origOffsetMs) <= 5000,
        detail: "expected=" + origOffsetMs + " diffMs=" + d14 });
} finally {
    writeClockProps(txt);
    Cache.remove(cacheKey);
}

writeReport();
