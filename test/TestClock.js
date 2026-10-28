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
//            config/AppClock.properties is now provided (Enable=true,
//            DataSource=config/DataSource.properties, no GetAppClockSql ->
//            no app-clock table, local-time fallback in DefaultTimeZone).
//            So Clock.getDataSource()/getConfigProperties() are loaded (C8/C9)
//            and the clock still tracks the local system time (C7).
// ============================================================================

var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var Clock = Java.type("com.killcoding.datasource.Clock");

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

try {
    var clock = new Clock();
    var now = System.currentTimeMillis();

    // C1: getDate non-null
    var d1 = null, e1 = "";
    try { d1 = clock.getDate(); } catch (e) { e1 = "" + e; }
    check({
        caseId: "C1",
        name: "Clock.getDate() non-null",
        ok: d1 !== null && d1 !== undefined && e1.length === 0,
        detail: e1.length > 0 ? e1 : ("" + d1),
        objective: "Clock facade returns a date without a remote clock",
        objectiveZh: "時鐘外觀無遠端時鐘時回傳日期",
        command: "new Clock().getDate()"
    });

    // C2: getTime positive epoch millis
    var t2 = 0, e2 = "";
    try { t2 = clock.getTime(); } catch (e) { e2 = "" + e; }
    check({
        caseId: "C2",
        name: "Clock.getTime() > 0",
        ok: t2 > 0 && e2.length === 0,
        detail: e2.length > 0 ? e2 : ("time=" + t2),
        objective: "getTime() returns positive epoch millis",
        objectiveZh: "getTime() 回傳正數紀元毫秒",
        command: "clock.getTime()"
    });

    // C3: typed accessors non-null
    var ts3 = null, sd3 = null, st3 = null, ld3 = null, ldt3 = null, cal3 = null, e3 = "";
    try {
        ts3 = clock.getSqlTimestamp(); sd3 = clock.getSqlDate(); st3 = clock.getSqlTime();
        ld3 = clock.getLocalDate(); ldt3 = clock.getLocalDateTime(); cal3 = clock.getCalendar();
    } catch (e) { e3 = "" + e; }
    check({
        caseId: "C3",
        name: "typed accessors (sql/date/time/local/calendar) non-null",
        ok: ts3 !== null && sd3 !== null && st3 !== null && ld3 !== null && ldt3 !== null && cal3 !== null && e3.length === 0,
        detail: e3.length > 0 ? e3 : ("ts=" + ts3 + " localDate=" + ld3 + " localDateTime=" + ldt3),
        objective: "all typed time accessors return usable values",
        objectiveZh: "所有型別化時間存取器回傳可用值",
        command: "clock.getSqlTimestamp/getSqlDate/getSqlTime/getLocalDate/getLocalDateTime/getCalendar"
    });

    // C4: date and millis views agree
    var eq4 = false, e4 = "";
    try { eq4 = (clock.getDate().getTime() === clock.getTime()); } catch (e) { e4 = "" + e; }
    check({
        caseId: "C4",
        name: "getDate().getTime() equals getTime()",
        ok: eq4 && e4.length === 0,
        detail: e4.length > 0 ? e4 : ("date=" + clock.getDate().getTime() + " time=" + clock.getTime()),
        objective: "date and millis views agree",
        objectiveZh: "日期與毫秒視圖一致",
        command: "clock.getDate().getTime() === clock.getTime()"
    });

    // C5: sql timestamp millis agree
    var eq5 = false, e5 = "";
    try { eq5 = (clock.getSqlTimestamp().getTime() === clock.getTime()); } catch (e) { e5 = "" + e; }
    check({
        caseId: "C5",
        name: "getSqlTimestamp().getTime() equals getTime()",
        ok: eq5 && e5.length === 0,
        detail: e5.length > 0 ? e5 : ("sqlTs=" + clock.getSqlTimestamp().getTime() + " time=" + clock.getTime()),
        objective: "SQL timestamp millis agree with getTime()",
        objectiveZh: "SQL 時間戳毫秒與 getTime() 一致",
        command: "clock.getSqlTimestamp().getTime() === clock.getTime()"
    });

    // C6: calendar millis agree
    var eq6 = false, e6 = "";
    try { eq6 = (clock.getCalendar().getTimeInMillis() === clock.getTime()); } catch (e) { e6 = "" + e; }
    check({
        caseId: "C6",
        name: "getCalendar().getTimeInMillis() equals getTime()",
        ok: eq6 && e6.length === 0,
        detail: e6.length > 0 ? e6 : ("cal=" + clock.getCalendar().getTimeInMillis() + " time=" + clock.getTime()),
        objective: "calendar millis agree with getTime()",
        objectiveZh: "日曆毫秒與 getTime() 一致",
        command: "clock.getCalendar().getTimeInMillis() === clock.getTime()"
    });

    // C7: fresh Clock instance - its first getTime() queries the DB clock
    // exactly inside this measurement window, so on the same machine the
    // gap is ~0ms (a cached instance would carry the C1-snapshot delta).
    var clock7 = new Clock();
    var now7a = System.currentTimeMillis();
    var ctime7 = clock7.getTime();
    var now7b = System.currentTimeMillis();
    var gap7 = Math.min(Math.abs(ctime7 - now7a), Math.abs(ctime7 - now7b));
    check({
        caseId: "C7",
        name: "clock within 10s of system time (fresh instance, same-machine ~0ms)",
        ok: gap7 < 10000,
        detail: "clock=" + ctime7 + " system=" + now7a + ".." + now7b + " gapMs=" + gap7,
        objective: "GetAppClockSql tracks the DB server time; fresh instance measures the real gap (same machine -> ~0ms)",
        objectiveZh: "GetAppClockSql 取 DB 時間；新實例測真實差距（同機 ~0ms）",
        command: "new Clock().getTime() vs System.currentTimeMillis() (min edge gap)"
    });

    // C8: static getDataSource() - AppClock enabled -> loaded & connectable
    var ds8 = null, e8 = "", conn8 = null, connOk8 = false;
    try {
        ds8 = Clock.getDataSource();
        if (ds8 !== null) { conn8 = ds8.getConnection(); connOk8 = conn8 !== null; }
    } catch (e) { e8 = "" + e; }
    check({
        caseId: "C8",
        name: "Clock.getDataSource() usable with AppClock enabled",
        ok: ds8 !== null && connOk8 && e8.length === 0,
        detail: e8.length > 0 ? e8 : ("ds=" + ds8 + " connOk=" + connOk8),
        objective: "config/AppClock.properties Enable=true -> Clock.getDataSource() loads the remote-clock datasource and connects",
        objectiveZh: "AppClock Enable=true 時 Clock.getDataSource() 載入遠端時鐘資料源並可連接",
        command: "Clock.getDataSource().getConnection()"
    });

    // C9: static getConfigProperties() - AppClock enabled -> loaded
    var cfg9 = null, e9 = "", enable9 = false;
    try {
        cfg9 = Clock.getConfigProperties();
        if (cfg9 !== null) { enable9 = cfg9.getBoolean("Enable", false); }
    } catch (e) { e9 = "" + e; }
    check({
        caseId: "C9",
        name: "Clock.getConfigProperties() loaded with Enable=true",
        ok: cfg9 !== null && enable9 === true && e9.length === 0,
        detail: e9.length > 0 ? e9 : ("cfg loaded, Enable=" + enable9),
        objective: "config/AppClock.properties loaded by Clock -> getConfigProperties() non-null and Enable=true",
        objectiveZh: "Clock 載入 AppClock.properties：getConfigProperties() 非空且 Enable=true",
        command: "Clock.getConfigProperties().getBoolean('Enable', false)"
    });

    // C10: local date and local date-time share the same calendar day
    var y10 = false, m10 = false, dd10 = false, e10 = "";
    try {
        var ld10 = clock.getLocalDate();
        var ldt10 = clock.getLocalDateTime();
        y10 = ld10.getYear() === ldt10.getYear();
        m10 = ld10.getMonthValue() === ldt10.getMonthValue();
        dd10 = ld10.getDayOfMonth() === ldt10.getDayOfMonth();
    } catch (e) { e10 = "" + e; }
    check({
        caseId: "C10",
        name: "getLocalDate() and getLocalDateTime() agree (y/m/d)",
        ok: y10 && m10 && dd10 && e10.length === 0,
        detail: e10.length > 0 ? e10 : ("localDate=" + clock.getLocalDate() + " localDateTime=" + clock.getLocalDateTime()),
        objective: "local date and local date-time share the same calendar day",
        objectiveZh: "本地日期與本地日期時間同日（年/月/日一致）",
        command: "ld.getYear()===ldt.getYear() && ld.getMonthValue()===ldt.getMonthValue() && ld.getDayOfMonth()===ldt.getDayOfMonth()"
    });

    // C11: sql date/time millis agree with getTime()
    var eq11 = false, e11 = "";
    try {
        eq11 = (clock.getSqlDate().getTime() === clock.getTime())
            && (clock.getSqlTime().getTime() === clock.getTime());
    } catch (e) { e11 = "" + e; }
    check({
        caseId: "C11",
        name: "getSqlDate()/getSqlTime() millis agree with getTime()",
        ok: eq11 && e11.length === 0,
        detail: e11.length > 0 ? e11 : ("sqlDate=" + clock.getSqlDate().getTime() + " sqlTime=" + clock.getSqlTime().getTime() + " time=" + clock.getTime()),
        objective: "sql date/time millis agree with getTime()",
        objectiveZh: "SQL 日期/時間毫秒與 getTime() 一致",
        command: "clock.getSqlDate().getTime() === clock.getTime() && clock.getSqlTime().getTime() === clock.getTime()"
    });

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestClock suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
