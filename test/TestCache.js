// ============================================================================
// JsPlus-Core TestCache - Console.callback + CacheArray.getObject() canonical
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite; needs H2 for the find
//            cases) or standalone with testdb initialized:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestCache.js < NUL
// Output   : <PROJECT_ROOT>/test_reports/TestCache-Report.html (TestBase
//            format, same as the other suites)
// Scope    : the canonical consumption pattern (H2Server.js/StartH2.js):
//            two+ consoles created via Console.newBuilder() - one console
//            drives find(), a callback console registers Console.callback
//            and consumes each entry via ca.getObject().
//            GraalJS rejects worker callbacks on the MAIN console
//            (PolyglotThreadAccessException); a newBuilder console serialises
//            the CacheArray worker onto one thread, so callbackSync=true
//            (default) delivers every entry reliably (probe-verified 3/3).
//            C1 = find -> callback; C2 = insert 1000 -> find -> callback delete.
// ============================================================================

var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var HashMap = Java.type("java.util.HashMap");
var CacheDriverExecutor = Java.type("com.killcoding.datasource.CacheDriverExecutor");

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

var REPORT_TITLE = "JsPlus-Core Cache Test";
var REPORT_NAME = "TestCache-Report.html";

// Column-name case mode: LOWER (same assignment as TestAll.js / GhostConsole).
// The callback deletes rows by map key 'id', so find() results must expose
// lowercase keys.
var DriverExecutor = Java.type("com.killcoding.datasource.DriverExecutor");
DriverExecutor.COLUMN_NAME_CASE_MODE = DriverExecutor.COLUMN_NAME_CASE_LOWER;

beginTest();

print("=== JsPlus-Core Cache Test ===");
print("JS_PLUS_PATH: " + basePath);

try {

// ---------------------------------------------------------------------------
// C1: canonical callback - two consoles via Console.newBuilder()
//     Console A drives find() into Console B's globalCacheArray; B's
//     Console.callback consumes each row via ca.getObject().
// ---------------------------------------------------------------------------
var conn1 = null, executor1 = null;
try {
    conn1 = Console.getDataSource().getConnection();
    executor1 = new CacheDriverExecutor(conn1);
    executor1.execute("DELETE FROM test_user WHERE username LIKE 'c9user%'");
    for (var i = 1; i <= 3; i++) {
        executor1.execute("INSERT INTO test_user (id, username, email, age, status, created_at, updated_at, deleted) "
            + "VALUES ('c9u" + i + "','c9user" + i + "','c9-" + i + "@example.com'," + (20 + i)
            + ",'active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE)");
    }

    // Console B (callback console): results collected into a Java ArrayList
    // held by this console - no cross-context JS reads.
    var callbackResults = new (Java.type("java.util.ArrayList"))();
    var callbackConsole = Console.newBuilder();
    callbackConsole.getContext().put("callbackResults", callbackResults);
    callbackConsole.evalScript(
        "var the = {}; the.ca = Console.getGlobalCacheArray(0, 50);"
        + "var R = Java.type('java.lang.Runnable');"
        + "var CB = Java.extend(R, { run: function(){ var o = the.ca.getObject() || null; if (o !== null) { callbackResults.add('' + o); } } });"
        + "Console.callback(new CB());"
    );

    // Console A (find): find() into the callback console's globalCacheArray
    executor1.find("SELECT id, username FROM test_user WHERE username LIKE 'c9user%' ORDER BY id",
        new HashMap(), callbackConsole.getGlobalCacheArray());

    var wait1 = 0;
    while (callbackResults.size() < 3 && wait1 < 8000) { Thread.sleep(20); wait1 += 20; }
    check({
        caseId: "C1",
        name: "Console.callback + getObject() (two newBuilder consoles: find + callback)",
        ok: callbackResults.size() === 3,
        detail: "consumed=" + callbackResults.size() + " waited=" + wait1 + "ms",
        objective: "callback console consumes each find row via ca.getObject(); find console feeds ca via find()",
        objectiveZh: "callback console 用 getObject 消費每行；find console 用 find 寫入 ca",
        command: "cb=Console.newBuilder(); cb.evalScript(callback); cde.find(sql, {}, cb.getGlobalCacheArray())"
    });
} catch (e1) {
    check({ caseId: "C1", name: "Console.callback + getObject() (two newBuilder consoles: find + callback)", ok: false, detail: "error=" + e1 });
} finally {
    if (executor1 !== null) { try { executor1.execute("DELETE FROM test_user WHERE username LIKE 'c9user%'"); } catch (eC1) {} }
    if (conn1 !== null) { try { conn1.close(); } catch (eC1b) {} }
}

// ---------------------------------------------------------------------------
// C2: THREE consoles - insert console inserts 1000 rows, find console finds
//     them, callback console deletes each row as it consumes it
//     (find -> callback(getObject) -> delete). 1000-row volume probe.
// ---------------------------------------------------------------------------
var conn2 = null, executor2 = null;
try {
    conn2 = Console.getDataSource().getConnection();
    executor2 = new CacheDriverExecutor(conn2);
    executor2.execute("DELETE FROM test_user WHERE username LIKE 'c9user%'");

    // Console C (insert console): 1000 rows in one batch
    var insertConsole = Console.newBuilder();
    insertConsole.evalScript(
        "var conn = Console.getDataSource().getConnection();"
        + "var CDE = Java.type('com.killcoding.datasource.CacheDriverExecutor');"
        + "var cde = new CDE(conn);"
        + "var AL = Java.type('java.util.ArrayList');"
        + "var recs = new AL();"
        + "for (var i = 1; i <= 1000; i++) { var r = new AL(); r.add('c9u' + i); r.add('c9user' + i); r.add('c9-' + i + '@example.com'); r.add(20 + (i % 30)); r.add('active'); r.add(false); recs.add(r); }"
        + "cde.executeBatchList('INSERT INTO test_user (id, username, email, age, status, created_at, updated_at, deleted) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)', recs);"
        + "conn.close();"
    );

    // Console B (delete callback console): deletes each row it consumes
    var deletedRows = new (Java.type("java.util.ArrayList"))();
    var deleteCallbackConsole = Console.newBuilder();
    deleteCallbackConsole.getContext().put("deletedRows", deletedRows);
    deleteCallbackConsole.evalScript(
        "var conn = Console.getDataSource().getConnection();"
        + "var CDE = Java.type('com.killcoding.datasource.CacheDriverExecutor');"
        + "var cde = new CDE(conn);"
        + "var AL = Java.type('java.util.ArrayList');"
        + "var the = {}; the.ca = Console.getGlobalCacheArray(0, 50);"
        + "var R = Java.type('java.lang.Runnable');"
        + "var CB = Java.extend(R, { run: function(){ var o = the.ca.getObject() || null; if (o !== null) { var p = new AL(); p.add(o.get('id')); cde.execute('DELETE FROM test_user WHERE id = ?', p); deletedRows.add('' + o.get('id')); } } });"
        + "Console.callback(new CB());"
    );

    // Console A (find): find all 1000 ids into the delete callback console's array
    executor2.find("SELECT id FROM test_user WHERE username LIKE 'c9user%'", new HashMap(),
        deleteCallbackConsole.getGlobalCacheArray());

    var wait2 = 0;
    while (deletedRows.size() < 1000 && wait2 < 30000) { Thread.sleep(20); wait2 += 20; }
    var d2 = dbVerify(function () {
        return "SELECT id, username FROM test_user WHERE username LIKE 'c9user%'";
    });
    check({
        caseId: "C2",
        name: "three consoles: insert 1000 -> find -> callback delete",
        ok: deletedRows.size() === 1000,
        detail: "consumed=" + deletedRows.size() + " waited=" + wait2 + "ms" + d2,
        objective: "insert console adds 1000 rows via executeBatchList; find console finds them; callback console deletes each row via getObject()",
        objectiveZh: "insert console 插入 1000 行；find console 查找；callback console 用 getObject 逐行 delete",
        command: "insert=Console.newBuilder(); cb=Console.newBuilder(); cde.find(sql, {}, cb.getGlobalCacheArray())"
    });
} catch (e2) {
    check({ caseId: "C2", name: "three consoles: insert 1000 -> find -> callback delete", ok: false, detail: "error=" + e2 });
} finally {
    if (executor2 !== null) { try { executor2.execute("DELETE FROM test_user WHERE username LIKE 'c9user%'"); } catch (eC2) {} }
    if (conn2 !== null) { try { conn2.close(); } catch (eC2b) {} }
}

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestCache suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
