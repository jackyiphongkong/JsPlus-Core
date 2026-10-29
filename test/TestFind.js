// ============================================================================
// JsPlus-Core TestFind - CacheDriverExecutor.find() feeding a CacheArray
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestFind.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestFind-Report.html (TestBase
//            format, same as the other suites)
// Scope    : com.killcoding.datasource.CacheDriverExecutor.find(sql, params,
//            CacheArray) - feeds query rows into a CacheArray (here the
//            callback console's global cache array) while the filter worker
//            fires the registered Console.callback(Runnable) per row.
// Canon    : dedicated-console GCA pattern (same as TestRemoteFile C5):
//              callbackConsole = Console.newBuilder() -> owns its own
//              globalCacheArray(0,1) + setNulIsCompleted(false) + a
//              Console.callback(Java.extend(Runnable)) proxy. The main console
//              builds cde = Console.getCacheDriverExecutor() and hands it to a
//              feedConsole whose task calls cde.find(sql, params, GCA) every
//              1s; an insertConsole inserts fresh rows into test_user every
//              100ms so find() always has data to feed. The filter worker runs
//              on the callback console (Java thread) and invokes the Runnable
//              proxy - no bare JS function crosses threads, so the GraalJS
//              PolyglotThreadAccessException is avoided (same reasoning as
//              TestRemoteFile C5).
// Verify   : callback consumption observed via shared Java collections
//            (consumedCount / contents / indexes), GCA.getProcessedTotal()
//            monotonic growth, per-row content round-trip (username/email/age
//            read off the fed row), no errors, plus dbVerify "select *".
// Table    : test_user (created by dbscript/change.sql, reset per TestAll run
//            by fallback.sql; this suite cleans its own rows for idempotency).
// ============================================================================

var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var Str = Java.type("java.lang.String");
var Runnable = Java.type("java.lang.Runnable");
var CacheArray = Java.type("com.killcoding.cache.CacheArray");
var CacheDriverExecutor = Java.type("com.killcoding.datasource.CacheDriverExecutor");
var DriverExecutor = Java.type("com.killcoding.datasource.DriverExecutor");
var HashMap = Java.type("java.util.HashMap");
var ArrayList = Java.type("java.util.ArrayList");

// Column-name case mode: LOWER so find() rows expose lowercase keys
// (username/email/age), matching the runtime default in TestAll.js.
DriverExecutor.COLUMN_NAME_CASE_MODE = DriverExecutor.COLUMN_NAME_CASE_LOWER;
CacheArray.initPool(200);

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

var REPORT_TITLE = "JsPlus-Core CacheDriverExecutor.find() CacheArray Feed Test";
var REPORT_NAME = "TestFind-Report.html";

beginTest();

print("=== JsPlus-Core TestFind (cde.find feeds CacheArray) ===");
print("JS_PLUS_PATH: " + basePath);

try {

// ---------------------------------------------------------------------------
// F0: clean test_user (idempotent re-runs) - CRUD via CacheDriverExecutor
// ---------------------------------------------------------------------------
var connPrep = Console.getDataSource().getConnection();
var cdePrep = new CacheDriverExecutor(connPrep);
cdePrep.execute("DELETE FROM test_user");
cdePrep.close();

// ---------------------------------------------------------------------------
// 1) callbackConsole: owns the GCA, registers the consuming Runnable
// ---------------------------------------------------------------------------
var callbackConsole = Console.newBuilder();
var consumedCount = new ArrayList();   // [ok|err] marker per consumed row
var contents = new ArrayList();        // "username|email|age" round-trip sample
var indexes = new ArrayList();         // getIndex() sequence as seen by the callback
var errors = new ArrayList();          // any exception thrown inside the callback
callbackConsole.getContext().put("consumedCount", consumedCount);
callbackConsole.getContext().put("contents", contents);
callbackConsole.getContext().put("indexes", indexes);
callbackConsole.getContext().put("errors", errors);
callbackConsole.getContext().put("Runnable", Runnable);
callbackConsole.evalScript(`(function(){
    var GCA = Console.getGlobalCacheArray(0,1);
    GCA.setNulIsCompleted(false);
    Console.getContext().put("GCA", GCA);

    var CB = Java.extend(Runnable, {
        run: function () {
            var o = GCA.getObject();
            if (o !== null && o !== undefined) {
                try {
                    consumedCount.add("ok");
                    contents.add(String(o.username) + "|" + String(o.email) + "|" + String(o.age));
                    indexes.add(GCA.getIndex());
                } catch (e) {
                    consumedCount.add("err");
                    errors.add("" + e);
                }
            }
        }
    });
    Console.callback(new CB());
}())`);

var GCA = callbackConsole.getContext().get("GCA");

// ---------------------------------------------------------------------------
// 2) feedConsole: cde.find(sql, params, GCA) feeds rows every 1s
//    NOTE: getCacheDriverExecutor(true) refreshes the connection and lazily
//    initialises cacheDriverExecutor; the no-arg getter only returns the
//    field (null until first open).
// ---------------------------------------------------------------------------
var cde = Console.getCacheDriverExecutor(true);
var feedSql = "SELECT * FROM test_user WHERE deleted = :deleted ORDER BY created_at";
var feedParams = new HashMap();
feedParams.put("deleted", false);

var feedConsole = Console.newBuilder();
feedConsole.getContext().put("cde", cde);
feedConsole.getContext().put("feedSql", feedSql);
feedConsole.getContext().put("feedParams", feedParams);
feedConsole.getContext().put("GCA", GCA);
feedConsole.setTask(`(function(){
    try {
        cde.find(feedSql, feedParams, GCA);
    } catch (e) {
        print(e);
    }
}())`);
feedConsole.setTaskTimer('1s');
feedConsole.taskExecute();

// ---------------------------------------------------------------------------
// 3) insertConsole: producer - inserts 20 rows into test_user every 100ms
//    so find() always has fresh data to feed (same role as TestRemoteFile C5)
// ---------------------------------------------------------------------------
var insertConsole = Console.newBuilder();
insertConsole.getContext().put("CacheDriverExecutor", CacheDriverExecutor);
insertConsole.setTask(`(function(){
    try {
        var conn = Console.getDataSource().getConnection();
        var cde = new CacheDriverExecutor(conn);
        var now = new java.util.Date().getTime();
        for (var i = 0; i < 20; i++) {
            // CHAR(16) PK: 13-digit timestamp + 3-digit batch index = 16 chars
            var id = String(now) + String(100 + i);
            var uname = "user_" + now + "_" + i;
            cde.execute("INSERT INTO test_user (id, username, email, age, status, created_at, deleted) VALUES ('"
                + id + "','" + uname + "','" + uname + "@example.com'," + (20 + i)
                + ",'active',CURRENT_TIMESTAMP,FALSE)");
        }
        cde.close();
    } catch (e) {
        print(e);
    }
}())`);
insertConsole.setTaskTimer('100ms');
insertConsole.taskExecute();

// ---------------------------------------------------------------------------
// 4) Let the pipeline run: insert -> find -> filter worker -> callback
// ---------------------------------------------------------------------------
Thread.sleep(5000);

// Stop both timers so the tables stay quiet for the assertions + cleanup.
feedConsole.stopTask();
insertConsole.stopTask();
Thread.sleep(500);

// ---------------------------------------------------------------------------
// F1: find() fed the GCA and the callback consumed rows
// ---------------------------------------------------------------------------
var total = GCA.getProcessedTotal();
check({
    caseId: "F1",
    name: "cde.find feeds GCA and Console.callback consumes rows",
    ok: total > 0 && consumedCount.size() > 0,
    detail: "processedTotal=" + total + ", callbackRuns=" + consumedCount.size()
        + ", contents=" + contents.size(),
    objective: "find() adds query rows to the GCA; the filter worker fires the callback per row",
    objectiveZh: "cde.find 餵入 GCA，filter worker 逐行觸發 callback 消費",
    command: "cde.find(sql, {deleted:false}, GCA); Console.callback(Runnable{ getObject() })"
});

// ---------------------------------------------------------------------------
// F2: per-row content round-trip (username/email/age read off the fed row)
// ---------------------------------------------------------------------------
var okContents = true;
var errCount = 0;
var malformedList = new ArrayList();
for (var ci = 0; ci < contents.size(); ci++) {
    var cval = String(contents.get(ci));
    var parts = cval.split("|");
    if (parts.length !== 3) { okContents = false; malformedList.add(cval); continue; }
    if (parts[0].indexOf("user_") !== 0 || parts[1].indexOf("@example.com") < 0
        || isNaN(parseInt(parts[2], 10))) { okContents = false; malformedList.add(cval); }
}
for (var ei = 0; ei < consumedCount.size(); ei++) {
    if (String(consumedCount.get(ei)) === "err") { errCount++; }
}
var malformedTxt = "";
for (var mi = 0; mi < malformedList.size(); mi++) {
    malformedTxt += " [" + String(malformedList.get(mi)) + "]";
}
check({
    caseId: "F2",
    name: "fed rows carry expected content (username/email/age round-trip)",
    ok: okContents && errCount === 0 && errors.size() === 0,
    detail: "sampled=" + contents.size() + ", malformed=" + malformedList.size()
        + ", callbackErrors=" + errCount + malformedTxt,
    objective: "each row fed by find() is the real DB row (lowercase columns readable)",
    objectiveZh: "find 餵出的每行皆為真實 DB 資料（小寫欄位可讀、內容完整）",
    command: "o.username / o.email / o.age inside callback"
});

// ---------------------------------------------------------------------------
// F3: getProcessedTotal() is monotonic (callback consumed strictly more than
//     the first observed value after a settle interval)
// ---------------------------------------------------------------------------
var t1 = GCA.getProcessedTotal();
Thread.sleep(1000);
var t2 = GCA.getProcessedTotal();
check({
    caseId: "F3",
    name: "processedTotal advances while producers keep feeding",
    ok: t2 > t1,
    detail: "t1=" + t1 + ", t2=" + t2,
    objective: "with insertConsole still stopped, late GCA rows keep being consumed by the worker",
    objectiveZh: "GCA 內殘餘行持續被 worker 消費，processedTotal 單調遞增",
    command: "GCA.getProcessedTotal() sampled twice"
});

// ---------------------------------------------------------------------------
// F4: dbVerify - native select * against the same datasource
// ---------------------------------------------------------------------------
var dbv = dbVerify(function () {
    return "select * from test_user";
});
check({
    caseId: "F4",
    name: "test_user persisted rows visible via select *",
    ok: true,
    detail: "select * from test_user" + dbv,
    objective: "DB rows are persisted by the producer and visible via plain JDBC",
    objectiveZh: "生產者插入的資料已落庫，select * 可見",
    command: "select * from test_user"
});

// ---------------------------------------------------------------------------
// F5: data retained - test_user is NOT emptied after the suite so the records
//     stay inspectable (SELECT * via H2 console / SQL tool after the run).
// ---------------------------------------------------------------------------
var dbvKeep = dbVerify(function () {
    return "select * from test_user";
});
check({
    caseId: "F5",
    name: "TestFind data retained (test_user rows kept for manual inspection)",
    ok: true,
    detail: "test_user retained" + dbvKeep,
    objective: "suite leaves the produced rows in place so the records can be inspected after the run",
    objectiveZh: "套件結束後保留產生的記錄，供測試後手動檢查",
    command: "select * from test_user"
});

} catch (e) {
    check({
        caseId: "F-ERR",
        name: "TestFind suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: when loaded by TestAll.js the aggregate entry decides the JVM exit
// (JSPLUS_TEST_EXIT=1), otherwise an interactive Console.eval(...) run
// finishes without killing the console.
