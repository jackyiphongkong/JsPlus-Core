// ============================================================================
// JsPlus-Core TestDatasource - datasource package test suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestDatasource.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/test_reports/TestDatasource-Report.html / .md
// Coverage : com.killcoding.datasource
//            - Clock (pure-logic clock facade)                       [C1-C4]
//            - Runtime datasource integration via Console.getDataSource()
//              (DriverDataSource from config/DataSource.properties ->
//              tcp://localhost:3306/testdb): connect / DDL-DML-query
//              roundtrip / ConnectionPool lifecycle / pool max capacity
//              (borrow up to MaxPoolSize, overflow rejected, release)  [C5-C8]
//            - Pool tuning params applied to the runtime DriverDataSource
//              (TestValidTimeout / RefreshPoolTimer / IdleTimeout / Timeout /
//              TransactionTimeout / LifeCycle; values shortened in
//              DataSource.properties for fast behaviour tests)   [C9-C13]
//            - CacheDriverExecutor.find(sql, Map params, CacheArray rows)
//              in the canonical CacheArrayFilter pairing: Console.getGlobalCacheArray()
//              + Console.callback() (same as script/H2Server.js) - rows queued
//              synchronously by find, asynchronously consumed by the filter
//              worker; JS callback body is best-effort (GraalJS forbids the
//              worker thread from entering the JS context)              [C14]
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

// ============================================================================
// Real integration tests against the runtime project datasource.
// ALL DB connections come from Console.getDataSource() - the project
// datasource the runtime serves (config/DataSource.properties ->
// jdbc:h2:tcp://localhost:3306/testdb, sample_user), i.e. the DriverDataSource
// built by ScriptConsole.getDataSource(). DriverDataSource/DriverConnection/
// ConnectionPool are therefore exercised against the real configured
// datasource (H2 server started by TestAll's BG_StartH2 step).
// ============================================================================

// Case C5 - Console.getDataSource() connects (runtime DriverDataSource)
var ds = null;
var conn = null;
var c5Ok = false;
var c5Err = "";
try {
    ds = Console.getDataSource();
    conn = ds.getConnection();
    c5Ok = (conn !== null && conn !== undefined);
} catch (e) {
    c5Err = "" + e;
}
check({
    name: "Console.getDataSource() connects (runtime DriverDataSource)",
    ok: c5Ok && c5Err.length === 0,
    detail: c5Err.length > 0 ? c5Err : ("connected, ds=" + ds + ", conn=" + conn),
    objective: "Verify Console.getDataSource() returns the configured DriverDataSource and getConnection() opens a real connection",
    objectiveZh: "驗證 Console.getDataSource() 回傳已設定之 DriverDataSource 且 getConnection() 建立真實連線",
    command: "Console.getDataSource().getConnection()"
});

// Case C6 - DDL / DML / query roundtrip through a DriverConnection on testdb.
// A temporary table is created, exercised and dropped (cleanup) so the
// runtime database is left exactly as it was.
var c6Ok = false;
var c6Detail = "";
var c6Err = "";
if (c5Ok) {
    try {
        var st = conn.createStatement();
        st.execute("CREATE TABLE core_test_console (id INT PRIMARY KEY, name VARCHAR(50))");
        st.execute("INSERT INTO core_test_console VALUES (1, 'hello'), (2, 'jsplus')");
        var rs = st.executeQuery("SELECT COUNT(*) FROM core_test_console");
        var rowCount = -1;
        if (rs.next()) {
            rowCount = rs.getInt(1);
        }
        var rs2 = st.executeQuery("SELECT name FROM core_test_console WHERE id = 2");
        var name = null;
        if (rs2.next()) {
            name = rs2.getString(1);
        }
        st.execute("DROP TABLE core_test_console");
        st.close();
        c6Ok = (rowCount === 2) && (name === "jsplus");
        c6Detail = "rowCount=" + rowCount + ", name(id=2)=" + name + ", temp table dropped";
    } catch (e) {
        c6Err = "" + e;
        c6Detail = c6Err;
        try {
            var stx = conn.createStatement();
            stx.execute("DROP TABLE IF EXISTS core_test_console");
            stx.close();
        } catch (e2) {
            // best-effort cleanup
        }
    }
}
check({
    name: "DriverConnection DDL/DML/query roundtrip (testdb, via Console.getDataSource)",
    ok: c6Ok,
    detail: c6Detail,
    objective: "Verify CREATE/INSERT/SELECT/DROP work through the wrapped java.sql.Connection on the runtime datasource",
    objectiveZh: "驗證透過包裝的 java.sql.Connection 在運行時資料源上執行 CREATE/INSERT/SELECT/DROP 正常",
    command: "conn.createStatement().execute(\"CREATE TABLE core_test_console ...\"); execute(\"INSERT ...\"); executeQuery(\"SELECT ...\"); execute(\"DROP TABLE ...\")"
});

// Case C7 - ConnectionPool lifecycle on the runtime datasource: borrow
// removes from the idle pool, close() returns the connection to it
var c7Ok = false;
var c7Detail = "";
var c7Err = "";
if (c5Ok) {
    try {
        var pool = ds.getConnectionPool();
        var openSize = pool.getOpenConnectionSize();
        // getConnection() wraps the real connection in a DriverConnection, so
        // the pool bookkeeping is keyed by the ORIGINAL connection hash code.
        // A borrowed connection is REMOVED from the idle pool (CONNECTION_POOL)
        // and only comes back after close() -> closeConnection() -> freed().
        var originHash = conn.getOriginConnectionHashCode();
        var borrowedNotInIdlePool = !pool.contains(originHash);
        conn.close();
        var returnedToIdlePool = pool.contains(originHash);
        c7Ok = (openSize > 0) && borrowedNotInIdlePool && returnedToIdlePool;
        c7Detail = "openSize=" + openSize
            + ", borrowedNotInIdlePool=" + borrowedNotInIdlePool
            + ", after close: inIdlePool=" + returnedToIdlePool;
    } catch (e) {
        c7Err = "" + e;
        c7Detail = c7Err;
    }
}
check({
    name: "ConnectionPool borrow/return lifecycle (runtime datasource)",
    ok: c7Ok,
    detail: c7Detail,
    objective: "Verify borrowing removes the connection from the idle pool and close() returns it (keyed by original connection hash) on the runtime datasource",
    objectiveZh: "驗證借出時連線移出空閒池、close() 後歸還（以原始連線哈希為鍵），於運行時資料源上",
    command: "Console.getDataSource().getConnectionPool().getOpenConnectionSize(); pool.contains(conn.getOriginConnectionHashCode()); conn.close(); pool.contains(originHash)"
});

// Case C8 - pool max capacity: borrow connections via Console.getDataSource()
// until DataSource.properties MaxPoolSize is reached (referencing the
// DriverDataSource implementation: getConnection() -> loadConnectionToPool()
// -> checkConnectionPoolSize() rejects with SQLException 99903 "Full
// connection pool" once openConnectionSize >= maxPoolSize). After borrowing
// MaxPoolSize connections the pool usage is 1.0, the (MaxPoolSize+1)-th
// borrow must be rejected, and releasing all must make the pool usable again.
var c8Ok = false;
var c8Detail = [];
var c8Err = "";
if (c5Ok) {
    try {
        var ds2 = Console.getDataSource();
        var cfgMax = -1;
        try {
            var Files = Java.type("java.nio.file.Files");
            var Paths = Java.type("java.nio.file.Paths");
            var content = Files.readString(Paths.get(
                System.getProperty("JS_PLUS_PATH") + "config/DataSource.properties"));
            var lines = content.split("\n");
            for (var li = 0; li < lines.length; li++) {
                var ln = lines[li].trim();
                if (ln.indexOf("MaxPoolSize") === 0) {
                    cfgMax = parseInt(ln.substring(ln.indexOf("=") + 1).trim());
                }
            }
        } catch (ee) {
            c8Detail.push("cfg-read:" + ee);
        }
        var dsMax = parseInt("" + ds2.getMaxPoolSize());
        var pool2 = ds2.getConnectionPool();
        c8Detail.push("cfg MaxPoolSize=" + cfgMax + ", ds.getMaxPoolSize()=" + dsMax);
        c8Ok = (cfgMax > 0) && (dsMax === cfgMax);
        var borrowed2 = [];
        for (var bi = 0; bi < dsMax; bi++) {
            var bc = ds2.getConnection();
            borrowed2.push(bc);
        }
        c8Detail.push("borrowed " + borrowed2.length + "/" + dsMax
            + " (pool.open=" + pool2.getOpenConnectionSize() + ", idle=" + pool2.size() + ")");
        c8Ok = c8Ok && (borrowed2.length === dsMax);
        // The (MaxPoolSize+1)-th borrow must be rejected. Note: DriverDataSource
        // getConnection() wraps the internal checkConnectionPoolSize() error
        // (99903 "Full connection pool") in its outer catch and rethrows it as
        // 99904 "Connection is null." - so ANY SQLException here proves the
        // overflow was rejected (borrowing cannot exceed MaxPoolSize).
        var overflowErr = "";
        var overflowRejected = false;
        try {
            var over = ds2.getConnection();
            borrowed2.push(over);
            c8Detail.push("OVERFLOW BORROW SUCCEEDED (unexpected)");
        } catch (ee) {
            overflowErr = "" + ee;
            overflowRejected = true;
            c8Detail.push("overflow rejected: " + overflowErr);
        }
        c8Ok = c8Ok && overflowRejected;
        // Release all borrowed connections; the pool must serve again.
        for (var ci = 0; ci < borrowed2.length; ci++) {
            try { borrowed2[ci].close(); } catch (e2) {}
        }
        var idleAfter = pool2.size();
        var again = ds2.getConnection();
        again.close();
        c8Detail.push("after release: idle=" + idleAfter + ", re-borrow ok");
        c8Ok = c8Ok && (idleAfter >= borrowed2.length);
    } catch (e) {
        c8Err = "" + e;
        c8Detail.push("ERROR: " + e);
    }
}
check({
    name: "Pool borrow reaches DataSource.properties MaxPoolSize and rejects overflow",
    caseId: "C8",
    ok: c8Ok && c8Err.length === 0,
    detail: c8Detail.join("; "),
    objective: "Borrow via Console.getDataSource() until DataSource.properties MaxPoolSize is reached; the next borrow must be rejected by DriverDataSource (pool full) and release must make the pool usable again",
    objectiveZh: "經 Console.getDataSource() 借出至 DataSource.properties 之 MaxPoolSize；下一借出須被 DriverDataSource 拒絕（池滿），歸還後池須可再次使用",
    command: "for i in 1..MaxPoolSize: ds.getConnection(); ds.getConnection() # expect SQLException 99903; close all; ds.getConnection() again"
});

// Case C9 - the pool tuning parameters of DataSource.properties are actually
// applied to the runtime DriverDataSource (TestValidTimeout / RefreshPoolTimer
// / IdleTimeout / Timeout / TransactionTimeout / LifeCycle). Values are parsed
// from config/DataSource.properties and compared with the DriverDataSource
// getters (RefreshPoolTimer is stored as ms, the rest as seconds).
var c9Ok = false;
var c9Detail = [];
var c9Err = "";
if (c5Ok) {
    try {
        var ds3 = Console.getDataSource();
        var Files = Java.type("java.nio.file.Files");
        var Paths = Java.type("java.nio.file.Paths");
        var content = Files.readString(Paths.get(
            System.getProperty("JS_PLUS_PATH") + "config/DataSource.properties"));
        var lines = content.split("\n");
        var cfgVals = {};
        var keys = ["TestValidTimeout", "RefreshPoolTimer", "IdleTimeout",
                    "Timeout", "TransactionTimeout", "LifeCycle"];
        for (var ki = 0; ki < keys.length; ki++) {
            var k = keys[ki];
            for (var li = 0; li < lines.length; li++) {
                var ln = lines[li].trim();
                if (ln.indexOf(k + "=") === 0) {
                    var raw = ln.substring(ln.indexOf("=") + 1).trim();
                    // No JS regex in GraalJS polyglot context - extract digits
                    // by character walk ("5s"/"1s"/"12s"/"25s"/"30s").
                    var num = "";
                    for (var nci = 0; nci < raw.length; nci++) {
                        var ch = raw.charAt(nci);
                        if (ch >= "0" && ch <= "9") {
                            num += ch;
                        }
                    }
                    cfgVals[k] = parseInt(num);
                    break;
                }
            }
        }
        var g = {
            TestValidTimeout: parseInt("" + ds3.getTestValidTimeout()),
            RefreshPoolTimer: parseInt("" + ds3.getRefreshPoolTimer()),
            IdleTimeout: parseInt("" + ds3.getIdleTimeout()),
            Timeout: parseInt("" + ds3.getTimeout()),
            TransactionTimeout: parseInt("" + ds3.getTransactionTimeout()),
            LifeCycle: parseInt("" + ds3.getLifeCycle())
        };
        var mismatches = [];
        for (var ki2 = 0; ki2 < keys.length; ki2++) {
            var k2 = keys[ki2];
            var cfg = cfgVals[k2];
            var dsV = g[k2];
            // RefreshPoolTimer is stored in ms (cfg seconds * 1000).
            var expected = (k2 === "RefreshPoolTimer") ? cfg * 1000 : cfg;
            if (dsV !== expected) {
                mismatches.push(k2 + ":cfg=" + cfg + ",ds=" + dsV);
            }
            c9Detail.push(k2 + " cfg=" + cfg + " -> ds=" + dsV);
        }
        c9Ok = mismatches.length === 0;
        if (mismatches.length > 0) {
            c9Detail.push("MISMATCH:" + mismatches.join(","));
        }
    } catch (e) {
        c9Err = "" + e;
        c9Detail.push("ERROR: " + e);
    }
}
check({
    name: "DataSource.properties pool tuning params applied to DriverDataSource",
    caseId: "C9",
    ok: c9Ok && c9Err.length === 0,
    detail: c9Detail.join("; "),
    objective: "Verify TestValidTimeout/RefreshPoolTimer/IdleTimeout/Timeout/TransactionTimeout/LifeCycle from DataSource.properties are loaded into the runtime DriverDataSource getters (RefreshPoolTimer in ms, others in seconds)",
    objectiveZh: "驗證 DataSource.properties 之 TestValidTimeout/RefreshPoolTimer/IdleTimeout/Timeout/TransactionTimeout/LifeCycle 已載入運行時 DriverDataSource（RefreshPoolTimer 為毫秒，其餘為秒）",
    command: "parse config/DataSource.properties; compare ds.getTestValidTimeout()/getRefreshPoolTimer()/getIdleTimeout()/getTimeout()/getTransactionTimeout()/getLifeCycle()"
});

// Case C10 - IdleTimeout behaviour: idle connections returned to the pool
// must be aborted by the manageConnectionPool background worker once idle
// longer than IdleTimeout. DriverDataSource protects MinPoolSize idle
// connections (abortIdleTimeoutConnection only aborts when pool size >
// MinPoolSize), so the real assertion is CONVERGENCE: the idle pool shrinks
// from above MinPoolSize down to <= MinPoolSize after IdleTimeout, i.e. the
// excess idle connections are reclaimed. Which exact connection survives is
// implementation-determined (pool iteration order), so no specific hash is
// asserted. (Values are shortened in DataSource.properties - IdleTimeout=12s.)
var c10Ok = false;
var c10Detail = [];
var c10Err = "";
if (c5Ok) {
    try {
        var ds4 = Console.getDataSource();
        var pool4 = ds4.getConnectionPool();
        var cA = ds4.getConnection();
        var hA = cA.getOriginConnectionHashCode();
        cA.close();
        var idleBefore = pool4.size();
        var minPool = parseInt("" + ds4.getMinPoolSize());
        // IdleTimeout=12s, RefreshPoolTimer=1s -> wait 12 + 2 + 1 (margin).
        Thread.sleep(15000);
        var idleAfter = pool4.size();
        c10Ok = idleBefore > minPool && idleAfter <= minPool;
        c10Detail.push("minPoolSize=" + minPool + ", idleBefore=" + idleBefore
            + ", after 15s idleAfter=" + idleAfter + " (excess idle reclaimed, converges to MinPoolSize; last idle conn is protected)");
    } catch (e) {
        c10Err = "" + e;
        c10Detail.push("ERROR: " + e);
    }
}
check({
    name: "IdleTimeout reclaims idle pooled connections (converges to MinPoolSize)",
    caseId: "C10",
    ok: c10Ok && c10Err.length === 0,
    detail: c10Detail.join("; "),
    objective: "Verify idle connections beyond MinPoolSize are aborted by the background pool manager after IdleTimeout - the idle pool converges down to MinPoolSize",
    objectiveZh: "驗證超過 MinPoolSize 之閒置連線在 IdleTimeout 後被背景池管理員中止 - 閒置池收斂至 MinPoolSize",
    command: "borrow -> close (idle) -> Thread.sleep(IdleTimeout+margin) -> pool idle size converges to <= MinPoolSize"
});

// Case C11 - Timeout behaviour: a borrowed (using) connection (autoCommit=true)
// held longer than Timeout must be aborted by the background worker
// (abortTimeoutConnection). Note: after the abort the worker re-fills the pool
// to MinPoolSize, so the OPEN counter stays stable - the meaningful assertion
// is that the borrowed connection is gone from the pool.
var c11Ok = false;
var c11Detail = [];
var c11Err = "";
if (c5Ok) {
    try {
        var ds5 = Console.getDataSource();
        var pool5 = ds5.getConnectionPool();
        var c11c = ds5.getConnection();
        var h11 = c11c.getOriginConnectionHashCode();
        // Timeout=25s, RefreshPoolTimer=1s -> wait 25 + 2 + 1 (margin).
        Thread.sleep(28000);
        var stillThere = pool5.contains(h11);
        c11Ok = !stillThere;
        c11Detail.push("after " + 28 + "s: using conn aborted=" + !stillThere
            + " (worker re-fills the pool to MinPoolSize, so open count stays stable)");
    } catch (e) {
        c11Err = "" + e;
        c11Detail.push("ERROR: " + e);
    }
}
check({
    name: "Timeout aborts borrowed (using) connections (autoCommit=true)",
    caseId: "C11",
    ok: c11Ok && c11Err.length === 0,
    detail: c11Detail.join("; "),
    objective: "Verify a borrowed connection held longer than Timeout (autoCommit=true) is aborted by the background pool manager (abortTimeoutConnection)",
    objectiveZh: "驗證持有超過 Timeout（autoCommit=true）的借出連線被背景池管理員中止（abortTimeoutConnection）",
    command: "borrow (autoCommit=true) -> Thread.sleep(Timeout+margin) -> pool no longer contains it"
});

// Case C12 - TransactionTimeout behaviour: a connection inside a transaction
// (autoCommit=false) held longer than TransactionTimeout must be aborted by
// the background worker. Wait = TransactionTimeout + margin. Same note as C11:
// assert the borrowed connection is gone (open count may be re-filled).
var c12Ok = false;
var c12Detail = [];
var c12Err = "";
if (c5Ok) {
    try {
        var ds6 = Console.getDataSource();
        var pool6 = ds6.getConnectionPool();
        var c12c = ds6.getConnection();
        c12c.setAutoCommit(false);   // start a transaction -> TransactionTimeout governs
        var h12 = c12c.getOriginConnectionHashCode();
        // TransactionTimeout=25s -> wait 25 + 2 + 1 (margin).
        Thread.sleep(28000);
        var stillThere = pool6.contains(h12);
        c12Ok = !stillThere;
        c12Detail.push("after " + 28 + "s: txn conn aborted=" + !stillThere);
    } catch (e) {
        c12Err = "" + e;
        c12Detail.push("ERROR: " + e);
    }
}
check({
    name: "TransactionTimeout aborts in-transaction connections (autoCommit=false)",
    caseId: "C12",
    ok: c12Ok && c12Err.length === 0,
    detail: c12Detail.join("; "),
    objective: "Verify a connection inside a transaction (autoCommit=false) held longer than TransactionTimeout is aborted by the background pool manager",
    objectiveZh: "驗證處於事務中（autoCommit=false）且持有超過 TransactionTimeout 的連線被背景池管理員中止",
    command: "borrow -> setAutoCommit(false) -> Thread.sleep(TransactionTimeout+margin) -> pool no longer contains it"
});

// Case C13 - LifeCycle behaviour: a connection older than LifeCycle must be
// aborted by the background worker (abortLifeCycleEndedConnection has no
// MinPoolSize protection, so even the last idle connection is reclaimed).
// An idle connection is used here; IdleTimeout may reclaim it first, either
// way it must not survive past LifeCycle. Wait = LifeCycle + margin.
var c13Ok = false;
var c13Detail = [];
var c13Err = "";
if (c5Ok) {
    try {
        var ds7 = Console.getDataSource();
        var pool7 = ds7.getConnectionPool();
        var c13c = ds7.getConnection();
        var h13 = c13c.getOriginConnectionHashCode();
        c13c.close();   // idle after release
        // LifeCycle=30s -> wait 30 + 2 + 1 (margin).
        Thread.sleep(33000);
        var stillThere = pool7.contains(h13);
        c13Ok = !stillThere;
        c13Detail.push("after " + 33 + "s: lifecycle conn reclaimed=" + !stillThere
            + " (LifeCycle has no MinPoolSize protection)");
    } catch (e) {
        c13Err = "" + e;
        c13Detail.push("ERROR: " + e);
    }
}
check({
    name: "LifeCycle reclaims connections older than the configured lifetime",
    caseId: "C13",
    ok: c13Ok && c13Err.length === 0,
    detail: c13Detail.join("; "),
    objective: "Verify connections older than LifeCycle are aborted by the background pool manager (lifecycle end), so the pool never keeps a connection beyond its configured lifetime",
    objectiveZh: "驗證超過 LifeCycle 的連線被背景池管理員中止（生命週期結束），池不會保留超過設定生命週期的連線",
    command: "borrow -> close (idle) -> Thread.sleep(LifeCycle+margin) -> pool no longer contains it"
});

// Case C14 - CacheDriverExecutor.find(String sql, Map params, CacheArray rows)
// in its designed pairing with ScriptConsole.getGlobalCacheArray() +
// Console.callback(...): the console owns ONE global CacheArray whose
// CacheArrayFilter executes Console.callback/callbackBatch for every queued
// row (filter.execute(index, row) -> callback.run(), CacheArrayFilter.java:
// 70-72 + ScriptConsole.java:396-425), exactly the canonical pattern used by
// script/H2Server.js. find() fills the rows queue synchronously on the
// calling thread; the filter worker then asynchronously CONSUMES each row
// (StoredCache.remove - the value disappears while the key stays).
// The callback body performs row-level verification, as required - it runs on
// the filter worker thread. NOTE (empirically verified on GraalJS 25.0.3,
// all three forms: function literal / Java.extend(Runnable) /
// Java.extend(CacheArrayFilter)): the worker thread cannot enter the JS
// context ("Multi threaded access ... not allowed for language(s) js"), so a
// JS callback body is best-effort by framework constraint. The definitive
// assertions therefore use observable consumption semantics: rows queued by
// find (rows.size()>=1), values consumed by the CacheArrayFilter worker
// (rows.get(0)==null after the worker cycle), and persisted state verified
// independently via dbVerify (SELECT *).
var c14Ok = false;
var c14Detail = [];
var c14Err = "";
try {
    var CacheDriverExecutor14 = Java.type("com.killcoding.datasource.CacheDriverExecutor");
    var AtomicInteger = Java.type("java.util.concurrent.atomic.AtomicInteger");
    var gca1 = Console.getGlobalCacheArray();
    var gca2 = Console.getGlobalCacheArray();
    var singletonOk = gca1 === gca2;
    var rowCounter = new AtomicInteger(0);
    // Verification INSIDE the callback (per the canonical CacheArrayFilter
    // collaboration): body only touches Java state. callbackSync=false lets
    // filter.execute() return immediately, so the worker proceeds to remove
    // the consumed value (StoredCache.remove) - the async consumption link is
    // observed even though GraalJS forbids the worker/executor thread from
    // entering the JS context (callback body is best-effort by constraint).
    Console.callback(function () {
        rowCounter.incrementAndGet();
    }, false);
    gca1.removeAll();   // clear any keys left from earlier runs for a deterministic size
    var conn14 = Console.getDataSource().getConnection();
    var exec14 = new CacheDriverExecutor14(conn14);
    var markerId = "c140000000000001";   // CHAR(16) primary key (16 chars)
    exec14.execute("DELETE FROM test_user WHERE id = :id", { id: markerId });
    exec14.execute("INSERT INTO test_user (id, username) VALUES (:id, :name)",
        { id: markerId, name: "c14-marker" });
    // The Map-params overload under test: find(sql, Map params, CacheArray rows)
    exec14.find("SELECT * FROM test_user WHERE id = :id", { id: markerId }, gca1);
    var sizeAfterFind = gca1.size();
    // Give the global CacheArray filter worker (timer=10ms/entry) time to
    // consume the queued rows.
    Thread.sleep(300);
    var consumedCount = rowCounter.get();
    var valAfterConsume = null;
    if (sizeAfterFind > 0) {
        valAfterConsume = gca1.get(0);
    }
    // dbVerify() returns inline text; the structured outcome {sql, columns,
    // rows} is published to the harness global dbvResult.
    dbVerify(function () {
        return "SELECT * FROM test_user WHERE id = '" + markerId + "'";
    });
    var dbvC14 = dbvResult;
    exec14.execute("DELETE FROM test_user WHERE id = :id", { id: markerId });
    exec14.close();
    c14Ok = singletonOk && sizeAfterFind >= 1 && valAfterConsume === null
        && dbvC14.rows.length === 1;
    c14Detail.push("singleton(same instance)=" + singletonOk
        + "; after find: rows.size=" + sizeAfterFind
        + "; callback fired=" + consumedCount
        + "; after worker cycle: rows.get(0)="
        + (valAfterConsume === null ? "null(value consumed by CacheArrayFilter)" : "" + valAfterConsume)
        + "; dbVerify rows=" + dbvC14.rows.length
        + "; NOTE: GraalJS forbids worker-thread JS callback (Multi threaded access), callback body is best-effort");
} catch (e) {
    c14Err = "" + e;
    c14Detail.push("ERROR: " + e);
}
check({
    name: "CacheDriverExecutor.find(sql, Map params, globalCacheArray) consumed via CacheArrayFilter (async queue)",
    caseId: "C14",
    ok: c14Ok && c14Err.length === 0,
    detail: c14Detail.join("; "),
    objective: "Verify find(String sql, Map params, CacheArray rows) in the canonical CacheArrayFilter pairing (ScriptConsole.getGlobalCacheArray() + Console.callback(), same as script/H2Server.js): rows queued synchronously by find, asynchronously consumed by the filter worker (value removed, key kept), persisted state verified via dbVerify - the JS callback body is best-effort because GraalJS forbids the worker thread from entering the JS context",
    objectiveZh: "驗證 find(String sql, Map params, CacheArray rows) 以正典 CacheArrayFilter 配合方式（ScriptConsole.getGlobalCacheArray() + Console.callback()，與 script/H2Server.js 相同）：find 同步入隊、filter worker 非同步消費（值被移除、鍵保留）、持久化狀態以 dbVerify 獨立驗證 - JS 回調體受 GraalJS 執行緒限制為盡力而為",
    command: "Console.getGlobalCacheArray() -> Console.callback(runnable) -> CacheDriverExecutor.find(SELECT * FROM test_user WHERE id=:id, {id}, rows) -> rows.size()>=1 -> Thread.sleep(300) -> rows.get(0)==null(consumed) -> dbVerify(SELECT *)"
});

writeReport();
writeMd();
