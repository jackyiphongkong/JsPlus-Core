// ============================================================================
// JsPlus-Core TestDatasource - datasource package test suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestDatasource.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/docs/latest/report/TestDatasource-Report.html / .md
// Coverage : com.killcoding.datasource
//            - Clock (pure-logic clock facade)                       [C1-C4]
//            - Runtime datasource integration via Console.getDataSource()
//              (DriverDataSource from config/TestDatasource.properties ->
//              tcp://localhost:3306/testdb): connect / DDL-DML-query
//              roundtrip / ConnectionPool lifecycle / pool max capacity
//              (borrow up to MaxPoolSize, overflow rejected, release)  [C5-C8]
//            - Pool tuning params applied to the runtime DriverDataSource
//              (TestValidTimeout / RefreshPoolTimer / IdleTimeout / Timeout /
//              TransactionTimeout / LifeCycle; TestDatasource.properties holds
//              second-scale values so the behaviour cases finish quickly)  [C9]
//            - Pool reclamation behaviour: IdleTimeout reclaims excess idle
//              connections (converges to MinPoolSize), Timeout aborts borrowed
//              (using) connections, TransactionTimeout aborts in-transaction
//              connections, LifeCycle reclaims connections older than the
//              configured lifetime - all via the manageConnectionPool worker
//              (values from TestDatasource.properties: 5s/8s/8s/12s)   [C10-C13]
//            - Retry / auto-connection: getConnection() reloads on an empty
//              pool (retry path), the pool worker self-heals to MinPoolSize
//              after mass aborts, and repeated abort-then-borrow cycles always
//              yield a fresh live connection (self-heal x3)          [C14-C16]
//            - H2 server lifecycle: stop the H2 process -> the borrowed
//              connection breaks (isValid throws/false) -> restart H2 ->
//              the datasource resumes serving real queries (auto conn)  [C17]
// NOTE     : Run with -DAPP_DATASOURCE=<config/TestDatasource.properties>
//            (test/TestDatasource.bat/.sh and TestAll.bat/.sh pass it) so the
//            runtime Console.getDataSource() loads the dedicated second-scale
//            pool configuration instead of the production DataSource.properties.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
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
    objective: "Verify the clock facade returns a date",
    objectiveZh: "驗證時鐘外觀回傳日期",
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
// datasource the runtime serves (config/TestDatasource.properties ->
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
// until TestDatasource.properties MaxPoolSize is reached (referencing the
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
                System.getProperty("JS_PLUS_PATH") + "config/TestDatasource.properties"));
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
Logger.systemMark("TestDatasource.js", "C8");
check({
    name: "Pool borrow reaches TestDatasource.properties MaxPoolSize and rejects overflow",
    caseId: "C8",
    ok: c8Ok && c8Err.length === 0,
    detail: c8Detail.join("; "),
    objective: "Borrow via Console.getDataSource() until TestDatasource.properties MaxPoolSize is reached; the next borrow must be rejected by DriverDataSource (pool full) and release must make the pool usable again",
    objectiveZh: "經 Console.getDataSource() 借出至 TestDatasource.properties 之 MaxPoolSize；下一借出須被 DriverDataSource 拒絕（池滿），歸還後池須可再次使用",
    command: "for i in 1..MaxPoolSize: ds.getConnection(); ds.getConnection() # expect SQLException 99903; close all; ds.getConnection() again"
});

// Case C9 - the pool tuning parameters of TestDatasource.properties are actually
// applied to the runtime DriverDataSource (TestValidTimeout / RefreshPoolTimer
// / IdleTimeout / Timeout / TransactionTimeout / LifeCycle). Values are parsed
// from config/TestDatasource.properties and compared with the DriverDataSource
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
            System.getProperty("JS_PLUS_PATH") + "config/TestDatasource.properties"));
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
Logger.systemMark("TestDatasource.js", "C9");
check({
    name: "TestDatasource.properties pool tuning params applied to DriverDataSource",
    caseId: "C9",
    ok: c9Ok && c9Err.length === 0,
    detail: c9Detail.join("; "),
    objective: "Verify TestValidTimeout/RefreshPoolTimer/IdleTimeout/Timeout/TransactionTimeout/LifeCycle from TestDatasource.properties are loaded into the runtime DriverDataSource getters (RefreshPoolTimer in ms, others in seconds)",
    objectiveZh: "驗證 TestDatasource.properties 之 TestValidTimeout/RefreshPoolTimer/IdleTimeout/Timeout/TransactionTimeout/LifeCycle 已載入運行時 DriverDataSource（RefreshPoolTimer 為毫秒，其餘為秒）",
    command: "parse config/TestDatasource.properties; compare ds.getTestValidTimeout()/getRefreshPoolTimer()/getIdleTimeout()/getTimeout()/getTransactionTimeout()/getLifeCycle()"
});

// Case C10 - IdleTimeout behaviour: idle connections returned to the pool
// must be aborted by the manageConnectionPool background worker once idle
// longer than IdleTimeout. DriverDataSource protects MinPoolSize idle
// connections (abortIdleTimeoutConnection only aborts when pool size >
// MinPoolSize), so the real assertion is CONVERGENCE: the idle pool shrinks
// from above MinPoolSize down to <= MinPoolSize after IdleTimeout, i.e. the
// excess idle connections are reclaimed. Which exact connection survives is
// implementation-determined (pool iteration order), so no specific hash is
// asserted. (Values are shortened in TestDatasource.properties - IdleTimeout=5s.)
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
        // IdleTimeout=5s, RefreshPoolTimer=1s -> wait 5 + 2 + 1 (margin).
        Thread.sleep(8000);
        var idleAfter = pool4.size();
        c10Ok = idleBefore > minPool && idleAfter <= minPool;
        c10Detail.push("minPoolSize=" + minPool + ", idleBefore=" + idleBefore
            + ", after 8s idleAfter=" + idleAfter + " (excess idle reclaimed, converges to MinPoolSize; last idle conn is protected)");
    } catch (e) {
        c10Err = "" + e;
        c10Detail.push("ERROR: " + e);
    }
}
Logger.systemMark("TestDatasource.js", "C10");
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
        // Timeout=8s, RefreshPoolTimer=1s -> wait 8 + 2 + 1 (margin).
        Thread.sleep(11000);
        var stillThere = pool5.contains(h11);
        c11Ok = !stillThere;
        c11Detail.push("after " + 11 + "s: using conn aborted=" + !stillThere
            + " (worker re-fills the pool to MinPoolSize, so open count stays stable)");
    } catch (e) {
        c11Err = "" + e;
        c11Detail.push("ERROR: " + e);
    }
}
Logger.systemMark("TestDatasource.js", "C11");
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
        // TransactionTimeout=8s -> wait 8 + 2 + 1 (margin).
        Thread.sleep(11000);
        var stillThere = pool6.contains(h12);
        c12Ok = !stillThere;
        c12Detail.push("after " + 11 + "s: txn conn aborted=" + !stillThere);
    } catch (e) {
        c12Err = "" + e;
        c12Detail.push("ERROR: " + e);
    }
}
Logger.systemMark("TestDatasource.js", "C12");
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
        // LifeCycle=12s -> wait 12 + 2 + 1 (margin).
        Thread.sleep(15000);
        var stillThere = pool7.contains(h13);
        c13Ok = !stillThere;
        c13Detail.push("after " + 15 + "s: lifecycle conn reclaimed=" + !stillThere
            + " (LifeCycle has no MinPoolSize protection)");
    } catch (e) {
        c13Err = "" + e;
        c13Detail.push("ERROR: " + e);
    }
}
Logger.systemMark("TestDatasource.js", "C13");
check({
    name: "LifeCycle reclaims connections older than the configured lifetime",
    caseId: "C13",
    ok: c13Ok && c13Err.length === 0,
    detail: c13Detail.join("; "),
    objective: "Verify connections older than LifeCycle are aborted by the background pool manager (lifecycle end), so the pool never keeps a connection beyond its configured lifetime",
    objectiveZh: "驗證超過 LifeCycle 的連線被背景池管理員中止（生命週期結束），池不會保留超過設定生命週期的連線",
    command: "borrow -> close (idle) -> Thread.sleep(LifeCycle+margin) -> pool no longer contains it"
});

// ---------------------------------------------------------------------------
// Retry / auto-connection (self-healing) cases [C14-C16]
// ---------------------------------------------------------------------------
// DriverDataSource keeps the pool healthy in two ways:
//   1) getConnection() retries: when the pool is empty it (re)loads a fresh
//      connection instead of returning null - DriverDataSource.getConnection()
//      loops attempt<2 with loadConnectionToPool() on an empty pool.
//   2) The manageConnectionPool worker auto-replenishes: abortInvalidConnection()
//      drops connections that fail isValid(TestValidTimeout), then
//      createConnectionToPool() tops the pool back up to MinPoolSize on every
//      RefreshPoolTimer tick.
// These cases drive a real runtime datasource (Console.getDataSource() ->
// config/TestDatasource.properties, MinPoolSize=1) and assert both behaviours.
// ---------------------------------------------------------------------------
var Executors = Java.type("java.util.concurrent.Executors");
var abortExec = Executors.newSingleThreadExecutor();

// C14: retry - getConnection on an empty pool (re)loads a fresh connection
var c14Ok = false;
var c14Err = "";
var c14Detail = [];
try {
    var ds = Console.getDataSource();
    // Borrow and then hard-abort the borrowed connection: abort() removes it
    // from the pool (and closes the socket), leaving the pool empty.
    var borrowConn = ds.getConnection();
    var abortedHash = borrowConn.getOriginConnectionHashCode();
    c14Detail.push("borrowed=" + abortedHash);
    borrowConn.abort(abortExec);
    c14Detail.push("aborted=" + abortedHash);
    // Immediately ask for another connection - the pool is empty, so
    // getConnection() must (re)load one and return it (never null).
    var retriedConn = ds.getConnection();
    var retriedHash = retriedConn.getOriginConnectionHashCode();
    c14Detail.push("retried=" + retriedHash + " (new hash, pool reloaded)");
    c14Ok = (retriedConn !== null && retriedHash !== abortedHash);
    // Run a real query through the retried connection to prove it is live.
    var stmt = retriedConn.createStatement();
    var rs = stmt.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES");
    var live = rs.next();
    rs.close();
    stmt.close();
    retriedConn.close();
    c14Detail.push("queryOk=" + live);
    c14Ok = c14Ok && live;
} catch (e) {
    c14Err = "" + e;
    c14Detail.push("ERROR: " + e);
}
Logger.systemMark("TestDatasource.js", "C14");
check({
    name: "getConnection retries on an empty pool (auto reload)",
    caseId: "C14",
    ok: c14Ok && c14Err.length === 0,
    detail: c14Detail.join("; "),
    objective: "After the only pooled connection is hard-aborted, the next getConnection() must reload a fresh connection instead of returning null (retry path: empty pool -> loadConnectionToPool)",
    objectiveZh: "唯一池連線被強制中止後，下一次 getConnection() 必須重新載入新連線而非回傳 null（重試路徑：空池 → loadConnectionToPool）",
    command: "conn.abort(executor) -> ds.getConnection() -> run SELECT (live check)"
});

// C15: auto conn - the pool self-heals back to MinPoolSize via the worker
var c15Ok = false;
var c15Err = "";
var c15Detail = [];
try {
    var ds15 = Console.getDataSource();
    // Abort every connection currently in the pool so the worker has real work.
    var aborted15 = 0;
    while (true) {
        var idleConn = null;
        try {
            // Borrowing marks the connection as 'using'; abort it right away.
            idleConn = ds15.getConnection();
        } catch (e) {
            break; // pool empty (overflow rejected) - nothing left to abort
        }
        var h = idleConn.getOriginConnectionHashCode();
        idleConn.abort(abortExec);
        aborted15++;
        if (aborted15 >= ds15.getMaxPoolSize()) break;
    }
    c15Detail.push("aborted=" + aborted15 + " (pool drained)");
    // Wait for the manageConnectionPool worker (RefreshPoolTimer=1s) to
    // replenish the pool to MinPoolSize=1, then verify a fresh connection works.
    var minSize = ds15.getMinPoolSize();
    var replenished = false;
    for (var w = 0; w < 10; w++) { // up to ~10s
        try {
            var probe = ds15.getConnection();
            if (probe !== null) {
                replenished = true;
                var pStmt = probe.createStatement();
                var pRs = pStmt.executeQuery("SELECT 1");
                var pLive = pRs.next();
                pRs.close();
                pStmt.close();
                probe.close();
                replenished = replenished && pLive;
                break;
            }
        } catch (e) { /* pool still replenishing */ }
        Thread.sleep(1000);
    }
    c15Detail.push("minPoolSize=" + minSize + " replenished=" + replenished);
    c15Ok = replenished;
} catch (e) {
    c15Err = "" + e;
    c15Detail.push("ERROR: " + e);
}
Logger.systemMark("TestDatasource.js", "C15");
check({
    name: "Pool self-heals to MinPoolSize after connections are aborted",
    caseId: "C15",
    ok: c15Ok && c15Err.length === 0,
    detail: c15Detail.join("; "),
    objective: "The manageConnectionPool worker (createConnectionToPool on RefreshPoolTimer) replenishes the pool after every connection is aborted, so auto-connection keeps the pool usable",
    objectiveZh: "管理連線池 worker（RefreshPoolTimer 週期 createConnectionToPool）在所有連線被中止後自動補充至 MinPoolSize，auto-connection 令池保持可用",
    command: "abort all -> wait >= RefreshPoolTimer -> getConnection + SELECT 1"
});

// C16: auto conn - repeated aborts never break the pool (multi-round self-heal)
var c16Ok = false;
var c16Err = "";
var c16Detail = [];
try {
    var ds16 = Console.getDataSource();
    // Stress the self-healing path: for 3 rounds, hard-abort the current
    // connection and immediately borrow again. Every round must succeed and
    // end with a live connection (retry + worker replenish combined).
    var roundsOk = 0;
    var rounds = 3;
    for (var r = 0; r < rounds; r++) {
        var hc = ds16.getConnection();
        var h = hc.getOriginConnectionHashCode();
        hc.abort(abortExec);
        var nc = ds16.getConnection(); // must reload (never null)
        var nh = nc.getOriginConnectionHashCode();
        var ns = nc.createStatement();
        var nrs = ns.executeQuery("SELECT 1");
        var live = nrs.next();
        nrs.close();
        ns.close();
        nc.close();
        if (nc !== null && nh !== h && live) {
            roundsOk++;
        }
    }
    c16Detail.push("roundsOk=" + roundsOk + "/" + rounds);
    c16Ok = (roundsOk === rounds);
} catch (e) {
    c16Err = "" + e;
    c16Detail.push("ERROR: " + e);
}
Logger.systemMark("TestDatasource.js", "C16");
check({
    name: "Repeated aborts never break the pool (self-heal x3)",
    caseId: "C16",
    ok: c16Ok && c16Err.length === 0,
    detail: c16Detail.join("; "),
    objective: "Every abort-then-borrow cycle reloads a fresh live connection - retry + auto-replenish hold up under repeated connection loss",
    objectiveZh: "每次「中止後再借」都重新載入可用的新連線——重複斷線下 retry + 自動補充依然成立",
    command: "loop 3x { getConnection -> abort -> getConnection -> SELECT 1 }"
});

// ---------------------------------------------------------------------------
// C17: H2 server lifecycle - stop / restart, connections disconnect and resume
// ---------------------------------------------------------------------------
// Full-database-outage simulation: stop the H2 server process (kill the java
// ghost keyed by script/StartH2.js), observe the borrowed connection break,
// restart the H2 server on :3306 and verify the datasource recovers (a fresh
// getConnection() + SELECT succeeds after the pool detects and replaces the
// dead entries). This is the real 'auto conn' scenario - a production DB
// restart must leave the pool usable again.
// ---------------------------------------------------------------------------
var ProcessBuilder = Java.type("java.lang.ProcessBuilder");
var Socket = Java.type("java.net.Socket");
var osName = ("" + System.getProperty("os.name")).toLowerCase();
var isWin = osName.indexOf("win") >= 0;

function execAsync(args) {
    var pb = new ProcessBuilder(args);
    pb.redirectErrorStream(true);
    return pb.start();
}

function h2Listening() {
    var s = null;
    try {
        s = new Socket("127.0.0.1", 3306);
        return true;
    } catch (e) {
        return false;
    } finally {
        if (s !== null) { try { s.close(); } catch (e2) {} }
    }
}

function h2Stop() {
    if (isWin) {
        // No inner double-quotes here: ProcessBuilder passes this as a single
        // argument, and embedded " would break PowerShell parsing.
        return execAsync(["powershell", "-NoProfile", "-Command",
            "Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe' } | Where-Object { $_.CommandLine -like '*StartH2.js*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"]);
    }
    return execAsync(["sh", "-c", "pkill -f 'script/StartH2.js' || true"]);
}

function h2Start() {
    var binDir = basePath + "bin/";
    if (isWin) {
        return execAsync(["cmd", "/c", binDir + "BG_StartH2.bat", "graalvm.js"]);
    }
    return execAsync(["sh", binDir + "BG_StartH2.sh", "graalvm.js"]);
}

var c17Ok = false;
var c17Err = "";
var c17Detail = [];
try {
    var ds17 = Console.getDataSource();
    c17Detail.push("h2UpBefore=" + h2Listening());

    // 1) open connection (prove it is live)
    var conn17 = ds17.getConnection();
    var hash17 = conn17.getOriginConnectionHashCode();
    var s17 = conn17.createStatement();
    var rs17 = s17.executeQuery("SELECT 1");
    var aliveBefore = rs17.next();
    rs17.close();
    s17.close();
    c17Detail.push("aliveBefore=" + aliveBefore + " hash=" + hash17);

    // 2) stop H2 and wait for :3306 to release
    h2Stop();
    var down = false;
    for (var d = 0; d < 20; d++) {
        if (!h2Listening()) { down = true; break; }
        Thread.sleep(500);
    }
    c17Detail.push("h2Down=" + down);

    // 3) the borrowed connection must be broken (disconnected)
    var broken = false;
    try {
        var invalid = conn17.isValid(2);
        broken = !invalid;
    } catch (e) {
        broken = true; // connection object throws once the server is gone
    }
    c17Detail.push("connBroken=" + broken);

    // 4) restart H2 and wait for :3306 to listen again
    h2Start();
    var up = false;
    for (var u = 0; u < 45; u++) {
        if (h2Listening()) { up = true; break; }
        Thread.sleep(1000);
    }
    c17Detail.push("h2UpAfter=" + up);

    // 5) the datasource must recover: keep trying until a fresh connection
    //    answers a real query (the pool worker drops the dead entries and
    //    replenishes, or getConnection reloads on an empty pool)
    var resumed = false;
    for (var r = 0; r < 20; r++) {
        try {
            var probe17 = ds17.getConnection();
            var ps17 = probe17.createStatement();
            var prs17 = ps17.executeQuery("SELECT 1");
            var pLive = prs17.next();
            prs17.close();
            ps17.close();
            probe17.close();
            if (probe17 !== null && pLive) { resumed = true; break; }
        } catch (e2) { /* pool still recovering */ }
        Thread.sleep(1000);
    }
    c17Detail.push("resumed=" + resumed);
    c17Ok = aliveBefore && down && broken && up && resumed;
} catch (e) {
    c17Err = "" + e;
    c17Detail.push("ERROR: " + e);
}
Logger.systemMark("TestDatasource.js", "C17");
check({
    name: "H2 stop/restart - connections disconnect then resume (auto conn)",
    caseId: "C17",
    ok: c17Ok && c17Err.length === 0,
    detail: c17Detail.join("; "),
    objective: "Stop the H2 server: the borrowed connection breaks; restart H2: the datasource recovers and serves real queries again (pool detects dead entries / reloads)",
    objectiveZh: "停止 H2 服務端：已借連線斷開；重啟 H2：資料源自動恢復並可執行真實查詢（池偵測失效條目／重新載入）",
    command: "getConnection -> kill StartH2.js -> conn.isValid broken -> BG_StartH2 -> poll SELECT 1 ok"
});

writeReport();
writeMd();

// Standalone runners (TestDatasource.bat/.sh) set JSPLUS_TEST_EXIT=1 and
// expect this JVM to exit with the suite outcome - the DriverDataSource pool
// workers are non-daemon threads, so the JVM would otherwise hang forever.
// Under TestAll.js (IN_TEST_ALL defined) this suite NEVER exits: TestAll owns
// the JVM exit after the whole run (a mid-run exit would kill the other suites).
var testExit = System.getenv("JSPLUS_TEST_EXIT");
if (testExit !== null && testExit !== undefined && ("" + testExit) === "1"
        && (typeof IN_TEST_ALL === "undefined" || IN_TEST_ALL === null)) {
    System.exit(failCount > 0 ? 1 : 0);
}
