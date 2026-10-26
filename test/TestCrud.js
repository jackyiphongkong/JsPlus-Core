// ============================================================================
// JsPlus-Core TestCrud - test tables CRUD suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestCrud.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/test_reports/TestCrud-Report.html / .md
// Coverage : The 6 tables defined in dbscript/change.sql
//              (ghost_remote_command + test_user / test_product / test_order
//              / test_log / test_kv)
//            Every test table gets its own independent CRUD test case
//            (create -> read -> update -> delete, via CacheDriverExecutor,
//            each with a dbVerify), plus soft-delete semantics and a final
//            schema dbVerify.
// DB       : All connections come from Console.getDataSource() - the runtime
//            project datasource (config/DataSource.properties ->
//            jdbc:h2:tcp://localhost:3306/testdb, sample_user). The schema is
//            initialized by TestAll.bat/.sh before this suite runs
//            (fallback.sql -> change.sql); this suite deliberately does NOT
//            re-drop it - ghost_remote_command is the runtime GhostConsole's
//            queue and tests must never touch it. The suite verifies the
//            schema (read-only) and cleans only its own test rows. CRUD is
//            executed through the CacheDriverExecutor (the runtime SQL access
//            class, DriverExecutor sync API: execute() / find() / first());
//            dbVerify confirms the server database state after each phase.
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

var REPORT_NAME = "TestCrud-Report.html";
var REPORT_TITLE = "JsPlus-Core TestCrud Report";

var CacheDriverExecutor = Java.type("com.killcoding.datasource.CacheDriverExecutor");

print("=== JsPlus-Core Test Tables CRUD Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));

// ----------------------------------------------------------------------------
// Database via the runtime project datasource (Console.getDataSource()).
// Schema is init'd by TestAll.bat/.sh (fallback.sql -> change.sql on the
// server database). This suite does NOT rebuild it: DROPping ghost_remote_
// command mid-run breaks a running GhostConsole ("table not found"), so only
// the 5 TEST tables are expected to be re-created by init and only this
// suite's own test rows are cleaned here (idempotent re-runs).
// ----------------------------------------------------------------------------
var conn = null;
var cde = null;
var loadErr = "";
try {
    conn = Console.getDataSource().getConnection();
    cde = new CacheDriverExecutor(conn);
    var t = cde.first(
        "SELECT COUNT(*) AS CNT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'");
    var tableCount = -1;
    if (t !== null && t !== undefined) {
        var tv = mapGetIgnoreCase(t, "CNT");
        if (tv !== undefined) {
            tableCount = parseInt("" + tv);
        }
    }
    if (tableCount < 6) {
        throw "testdb not initialized (PUBLIC tables=" + tableCount
            + "). Run test/TestAll.bat first - it inits testdb before the suites.";
    }
    // Clean only this suite's test rows (never ghost_remote_command).
    cde.execute("DELETE FROM test_user WHERE id IN ('u000000000000001','u000000000000099')");
    cde.execute("DELETE FROM test_product WHERE id='p000000000000001'");
    cde.execute("DELETE FROM test_order WHERE id='o000000000000001'");
    cde.execute("DELETE FROM test_log WHERE id='l000000000000001'");
    cde.execute("DELETE FROM test_kv WHERE id='k000000000000001'");
} catch (e) {
    loadErr = "" + e;
}

// ---- small helpers over the shared CacheDriverExecutor (DriverExecutor
// sync API). Rows are java.util.Map keyed by column label (H2 stores
// unquoted column names uppercase), so mapGetIgnoreCase is used for reads.
// ---- 
function mapGetIgnoreCase(map, key) {
    if (map === null || map === undefined) {
        return undefined;
    }
    var target = ("" + key).toLowerCase();
    var it = map.keySet().iterator();
    while (it.hasNext()) {
        var javaKey = it.next();
        if (("" + javaKey).toLowerCase() === target) {
            return map.get(javaKey);
        }
    }
    return undefined;
}

function firstInt(sql) {
    var m = cde.first(sql);
    if (m === null || m === undefined) {
        return -1;
    }
    var v = mapGetIgnoreCase(m, "CNT");
    return (v === undefined || v === null) ? -1 : parseInt("" + v);
}

function firstString(sql) {
    var m = cde.first(sql);
    if (m === null || m === undefined) {
        return null;
    }
    var keys = m.keySet().iterator();
    if (keys.hasNext()) {
        var v = m.get(keys.next());
        return v === null ? null : ("" + v);
    }
    return null;
}

// Case C1 - change.sql applies cleanly and all 6 tables exist
var c1Ok = false;
var c1Detail = "";
if (loadErr.length === 0) {
    try {
        var expected = ["GHOST_REMOTE_COMMAND", "TEST_KV", "TEST_LOG", "TEST_ORDER",
                        "TEST_PRODUCT", "TEST_USER"];
        var found = [];
        var list = cde.find(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'");
        for (var i = 0; i < list.size(); i++) {
            var m = list.get(i);
            var t = mapGetIgnoreCase(m, "TABLE_NAME");
            if (t !== undefined) {
                found.push("" + t);
            }
        }
        var missing = [];
        for (var i = 0; i < expected.length; i++) {
            if (found.indexOf(expected[i]) < 0) {
                missing.push(expected[i]);
            }
        }
        // The 6 core tables must all exist; extra tables (e.g. the RemoteFile
        // suite's remote_file / remote_file_data_*) do not fail this check.
        c1Ok = (missing.length === 0);
        c1Detail = "tables=" + found.join(",") + (missing.length > 0 ? " MISSING:" + missing.join(",") : "");
    } catch (e) {
        c1Detail = "" + e;
    }
}
check({
    name: "dbscript/change.sql applied - 6 tables exist",
    ok: c1Ok && loadErr.length === 0,
    detail: loadErr.length > 0 ? loadErr : c1Detail,
    objective: "Verify the shipped DDL creates ghost_remote_command + 5 test tables",
    objectiveZh: "驗證內附 DDL 建立 ghost_remote_command 與 5 張測試表",
    command: "RunScript.execute(conn, dbscript/change.sql); CacheDriverExecutor.find(SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES)"
});

// ----------------------------------------------------------------------------
// CRUD specs for the 5 generic test tables. Each spec carries the statements
// and expected values for the CREATE / READ / UPDATE / DELETE phases. The
// phases run in order (C2 -> C5), each as its own test case, and every case
// finishes with a dbVerify against the server database (the same database
// TestAll.bat/.sh initializes, reached via Console.getDataSource()).
// ----------------------------------------------------------------------------
var CRUD_SPECS = [
    {
        table: "test_user",
        id: "u000000000000001",
        insert: "INSERT INTO test_user (id, username, email, age, status, created_at, updated_at, deleted) "
            + "VALUES ('u000000000000001', 'alice', 'alice@example.com', 30, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)",
        selectInsert: "SELECT username FROM test_user WHERE id='u000000000000001'",
        insertCheck: "alice",
        update: "UPDATE test_user SET age=31, status='disabled' WHERE id='u000000000000001'",
        selectUpdate: "SELECT status FROM test_user WHERE id='u000000000000001'",
        updateCheck: "disabled",
        delete: "DELETE FROM test_user WHERE id='u000000000000001'",
        verifyRow: "SELECT * FROM test_user WHERE id='u000000000000001'",
        countAfterDelete: "SELECT COUNT(*) AS CNT FROM test_user WHERE id='u000000000000001'"
    },
    {
        table: "test_product",
        id: "p000000000000001",
        insert: "INSERT INTO test_product (id, product_name, category, price, stock, created_at, updated_at, deleted) "
            + "VALUES ('p000000000000001', 'Coffee Beans', 'Beverage', 12.50, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)",
        selectInsert: "SELECT product_name FROM test_product WHERE id='p000000000000001'",
        insertCheck: "Coffee Beans",
        update: "UPDATE test_product SET price=13.00, stock=99 WHERE id='p000000000000001'",
        selectUpdate: "SELECT price FROM test_product WHERE id='p000000000000001'",
        updateCheck: "13.00",
        delete: "DELETE FROM test_product WHERE id='p000000000000001'",
        verifyRow: "SELECT * FROM test_product WHERE id='p000000000000001'",
        countAfterDelete: "SELECT COUNT(*) AS CNT FROM test_product WHERE id='p000000000000001'"
    },
    {
        table: "test_order",
        id: "o000000000000001",
        insert: "INSERT INTO test_order (id, order_no, user_id, product_id, quantity, amount, status, created_at, updated_at, deleted) "
            + "VALUES ('o000000000000001', 'ORD-20261024-001', 'u000000000000001', 'p000000000000001', 2, 25.00, 'paid', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)",
        selectInsert: "SELECT order_no FROM test_order WHERE id='o000000000000001'",
        insertCheck: "ORD-20261024-001",
        update: "UPDATE test_order SET quantity=3, amount=37.50, status='shipped' WHERE id='o000000000000001'",
        selectUpdate: "SELECT status FROM test_order WHERE id='o000000000000001'",
        updateCheck: "shipped",
        delete: "DELETE FROM test_order WHERE id='o000000000000001'",
        verifyRow: "SELECT * FROM test_order WHERE id='o000000000000001'",
        countAfterDelete: "SELECT COUNT(*) AS CNT FROM test_order WHERE id='o000000000000001'"
    },
    {
        table: "test_log",
        id: "l000000000000001",
        insert: "INSERT INTO test_log (id, level, source, message, log_time, created_at, updated_at, deleted) "
            + "VALUES ('l000000000000001', 'INFO', 'TestCrud.js', 'hello from table test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)",
        selectInsert: "SELECT level FROM test_log WHERE id='l000000000000001'",
        insertCheck: "INFO",
        update: "UPDATE test_log SET level='WARN', message='updated message' WHERE id='l000000000000001'",
        selectUpdate: "SELECT message FROM test_log WHERE id='l000000000000001'",
        updateCheck: "updated message",
        delete: "DELETE FROM test_log WHERE id='l000000000000001'",
        verifyRow: "SELECT * FROM test_log WHERE id='l000000000000001'",
        countAfterDelete: "SELECT COUNT(*) AS CNT FROM test_log WHERE id='l000000000000001'"
    },
    {
        table: "test_kv",
        id: "k000000000000001",
        insert: "INSERT INTO test_kv (id, kv_key, kv_value, remark, created_at, updated_at, deleted) "
            + "VALUES ('k000000000000001', 'theme', 'dark', 'ui preference', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)",
        selectInsert: "SELECT kv_value FROM test_kv WHERE kv_key='theme'",
        insertCheck: "dark",
        update: "UPDATE test_kv SET kv_value='light' WHERE id='k000000000000001'",
        selectUpdate: "SELECT kv_value FROM test_kv WHERE id='k000000000000001'",
        updateCheck: "light",
        delete: "DELETE FROM test_kv WHERE id='k000000000000001'",
        verifyRow: "SELECT * FROM test_kv WHERE id='k000000000000001'",
        countAfterDelete: "SELECT COUNT(*) AS CNT FROM test_kv WHERE id='k000000000000001'"
    }
];

// ----------------------------------------------------------------------------
// Per-table CRUD split into FOUR independent test cases (c / r / u / d), one
// per phase - each phase re-selects the row (SELECT *) after the statement so
// the persisted state is verified at every step. All statements run through
// the CacheDriverExecutor (DriverExecutor sync API: execute() / first());
// dbVerify uses Console.getDataSource() against the same server database
// TestAll.bat/.sh initializes.
// ----------------------------------------------------------------------------
function perTableCreateCase(caseId, table, spec) {
    var ok = false;
    var detail = [];
    var err = "";
    var dbvText = "";
    if (loadErr.length === 0) {
        try {
            var ir = cde.execute(spec.insert);
            var v1 = firstString(spec.selectInsert);
            detail.push("insert rows=" + ir + "; re-select read=" + v1);
            ok = (ir >= 1 && v1 !== null && ("" + v1) === ("" + spec.insertCheck));
            dbvText = dbVerify(function () {
                return spec.verifyRow;
            });
            ok = ok && dbvResult !== null && dbvResult.rows.length === 1;
        } catch (e) {
            ok = false;
            err = "" + e;
            detail.push("ERROR: " + e);
        }
    }
    check({
        name: table + " CREATE via CacheDriverExecutor (H2)",
        caseId: caseId,
        ok: ok && err.length === 0 && loadErr.length === 0,
        detail: detail.join("; ")
            + (dbvText.length > 0 ? "; dbVerify(SELECT *)=1 row:" + dbvText : "")
            + (loadErr.length > 0 ? "; LOAD:" + loadErr : ""),
        objective: "Verify INSERT on " + table + " through CacheDriverExecutor and re-select (SELECT *) the row back",
        objectiveZh: "驗證 " + table + " 經 CacheDriverExecutor INSERT 後以 SELECT * 重新選回該列",
        command: table + ": CacheDriverExecutor.execute(INSERT) -> first(SELECT) -> dbVerify(SELECT * WHERE id)"
    });
}

function perTableReadCase(caseId, table, spec) {
    var ok = false;
    var detail = [];
    var err = "";
    var dbvText = "";
    if (loadErr.length === 0) {
        try {
            var v1 = firstString(spec.selectInsert);
            detail.push("re-select read=" + v1);
            ok = (v1 !== null && ("" + v1) === ("" + spec.insertCheck));
            dbvText = dbVerify(function () {
                return spec.verifyRow;
            });
            ok = ok && dbvResult !== null && dbvResult.rows.length === 1;
        } catch (e) {
            ok = false;
            err = "" + e;
            detail.push("ERROR: " + e);
        }
    }
    check({
        name: table + " READ via CacheDriverExecutor (H2)",
        caseId: caseId,
        ok: ok && err.length === 0 && loadErr.length === 0,
        detail: detail.join("; ")
            + (dbvText.length > 0 ? "; dbVerify(SELECT *)=1 row:" + dbvText : "")
            + (loadErr.length > 0 ? "; LOAD:" + loadErr : ""),
        objective: "Verify the created " + table + " row is readable via CacheDriverExecutor and SELECT * still returns it",
        objectiveZh: "驗證已建立之 " + table + " 列可經 CacheDriverExecutor 讀取，SELECT * 仍返回該列",
        command: table + ": CacheDriverExecutor.first(SELECT) -> dbVerify(SELECT * WHERE id)"
    });
}

function perTableUpdateCase(caseId, table, spec) {
    var ok = false;
    var detail = [];
    var err = "";
    var dbvText = "";
    if (loadErr.length === 0) {
        try {
            var ur = cde.execute(spec.update);
            var v2 = firstString(spec.selectUpdate);
            detail.push("update rows=" + ur + "; re-select read=" + v2);
            ok = (ur >= 1 && v2 !== null && ("" + v2) === ("" + spec.updateCheck));
            dbvText = dbVerify(function () {
                return spec.verifyRow;
            });
            ok = ok && dbvResult !== null && dbvResult.rows.length === 1;
        } catch (e) {
            ok = false;
            err = "" + e;
            detail.push("ERROR: " + e);
        }
    }
    check({
        name: table + " UPDATE via CacheDriverExecutor (H2)",
        caseId: caseId,
        ok: ok && err.length === 0 && loadErr.length === 0,
        detail: detail.join("; ")
            + (dbvText.length > 0 ? "; dbVerify(SELECT *)=1 row:" + dbvText : "")
            + (loadErr.length > 0 ? "; LOAD:" + loadErr : ""),
        objective: "Verify UPDATE on " + table + " through CacheDriverExecutor and re-select (SELECT *) the updated row",
        objectiveZh: "驗證 " + table + " 經 CacheDriverExecutor UPDATE 後以 SELECT * 重新選回更新後之列",
        command: table + ": CacheDriverExecutor.execute(UPDATE) -> first(SELECT) -> dbVerify(SELECT * WHERE id)"
    });
}

function perTableDeleteCase(caseId, table, spec) {
    var ok = false;
    var detail = [];
    var err = "";
    var dbvText = "";
    if (loadErr.length === 0) {
        try {
            var dr = cde.execute(spec.delete);
            var left = firstInt(spec.countAfterDelete);
            detail.push("delete rows=" + dr + ", left=" + left);
            ok = (dr >= 1 && left === 0);
            dbvText = dbVerify(function () {
                return spec.verifyRow;
            });
            ok = ok && dbvResult !== null && dbvResult.rows.length === 0;
        } catch (e) {
            ok = false;
            err = "" + e;
            detail.push("ERROR: " + e);
        }
    }
    check({
        name: table + " DELETE via CacheDriverExecutor (H2)",
        caseId: caseId,
        ok: ok && err.length === 0 && loadErr.length === 0,
        detail: detail.join("; ")
            + (dbvText.length > 0 ? "; dbVerify(SELECT *)=0 rows:" + dbvText : "")
            + (loadErr.length > 0 ? "; LOAD:" + loadErr : ""),
        objective: "Verify DELETE on " + table + " through CacheDriverExecutor and re-select (SELECT *) finds no row",
        objectiveZh: "驗證 " + table + " 經 CacheDriverExecutor DELETE 後以 SELECT * 重新選回確認無列",
        command: table + ": CacheDriverExecutor.execute(DELETE) -> first(COUNT)=0 -> dbVerify(SELECT * WHERE id)"
    });
}

// Every test table gets its own c/r/u/d case group, run in phase order:
//   create -> read -> update -> delete (each phase re-selects the row).
// caseId per table: C2/C3/C4/C5/C6 split as C{n}-CREATE / -READ / -UPDATE / -DELETE.
function runTableCrudCases(group, table, spec) {
    perTableCreateCase(group + "-CREATE", table, spec);
    perTableReadCase(group + "-READ", table, spec);
    perTableUpdateCase(group + "-UPDATE", table, spec);
    perTableDeleteCase(group + "-DELETE", table, spec);
}

// Case C2 - test_user CRUD (c/r/u/d, 4 cases)
runTableCrudCases("C2", "test_user", CRUD_SPECS[0]);

// Case C3 - test_product CRUD (c/r/u/d, 4 cases)
runTableCrudCases("C3", "test_product", CRUD_SPECS[1]);

// Case C4 - test_order CRUD (c/r/u/d, 4 cases)
runTableCrudCases("C4", "test_order", CRUD_SPECS[2]);

// Case C5 - test_log CRUD (c/r/u/d, 4 cases)
runTableCrudCases("C5", "test_log", CRUD_SPECS[3]);

// Case C6 - test_kv CRUD (c/r/u/d, 4 cases)
runTableCrudCases("C6", "test_kv", CRUD_SPECS[4]);


// Case C7 - soft-delete semantics (deleted flag + deleted_at timestamp)
// (CacheDriverExecutor.execute / first)
var c6Ok = false;
var c6Detail = "";
var c6Err = "";
var c6DbvText = "";
try {
    cde.execute(
        "INSERT INTO test_user (id, username, email, age, status, created_at, updated_at, deleted) "
        + "VALUES ('u000000000000099', 'bob', 'bob@example.com', 25, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)");
    cde.execute(
        "UPDATE test_user SET deleted=TRUE, deleted_at=CURRENT_TIMESTAMP WHERE id='u000000000000099'");
    var deletedFlag = firstInt(
        "SELECT COUNT(*) AS CNT FROM test_user WHERE id='u000000000000099' AND deleted=TRUE");
    var hasDeletedAt = firstInt(
        "SELECT COUNT(*) AS CNT FROM test_user WHERE id='u000000000000099' AND deleted_at IS NOT NULL");
    cde.execute("DELETE FROM test_user WHERE id='u000000000000099'");
    c6Ok = (deletedFlag === 1) && (hasDeletedAt === 1);
    c6Detail = "soft-deleted rows: flag=TRUE=" + deletedFlag + ", deleted_at set=" + hasDeletedAt;
} catch (e) {
    c6Err = "" + e;
    c6Detail = "ERROR: " + e;
}
try {
    c6DbvText = dbVerify(function () {
        return "SELECT * FROM test_user WHERE id='u000000000000099'";
    });
} catch (e) {
    c6DbvText = "(query failed) " + e;
}
var c6DbvOk = dbvResult !== null && dbvResult.rows.length === 0;
check({
    name: "Soft-delete semantics (deleted flag + deleted_at)",
    ok: c6Ok && c6DbvOk && c6Err.length === 0,
    detail: c6Detail + (c6DbvText.length > 0 ? "; dbVerify:" + c6DbvText : ""),
    objective: "Verify the soft-delete columns behave as designed (flag + timestamp) and cleanup leaves no row",
    objectiveZh: "驗證軟刪除欄位（deleted 標記 + deleted_at 時間戳）行為符合設計，清理後無殘留",
    command: "CacheDriverExecutor.execute(UPDATE test_user SET deleted=TRUE, deleted_at=CURRENT_TIMESTAMP WHERE id=...); dbVerify(SELECT * = 0 rows)"
});

// Case C8 - dbVerify: the server database that TestAll.bat/.sh initializes
// (jdbc:h2:tcp://localhost:3306/testdb, H2 started by BG_StartH2) is
// reachable through the project's standard data path (Console.getDataSource())
// and carries the 6 tables created from dbscript/. This is the "db verify"
// step aligned with JsPlus-Tomcat: the suite confirms the database init'd by
// the TestAll flow actually serves the expected schema (the same database
// the CRUD cases above exercise).
var c7Detail = "";
var c7Ok = false;
var c7Err = "";
try {
    var dbvText = dbVerify(function () {
        return "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME";
    });
    c7Detail = dbvText;
    c7Ok = dbvText.indexOf("(query failed)") < 0
        && dbvText.indexOf("GHOST_REMOTE_COMMAND") >= 0
        && dbvText.indexOf("TEST_KV") >= 0
        && dbvText.indexOf("TEST_LOG") >= 0
        && dbvText.indexOf("TEST_ORDER") >= 0
        && dbvText.indexOf("TEST_PRODUCT") >= 0
        && dbvText.indexOf("TEST_USER") >= 0;
} catch (e) {
    c7Err = "" + e;
    c7Detail = "EXCEPTION: " + e;
}
check({
    name: "dbVerify: testdb on :3306 serves the 6 initialized tables",
    ok: c7Ok && c7Err.length === 0,
    detail: c7Detail,
    objective: "Verify the H2 server database (tcp://localhost:3306/testdb) init'd by TestAll is reachable via Console.getDataSource() and holds the expected schema",
    objectiveZh: "驗證 TestAll 初始化之 H2 服務端資料庫（tcp://localhost:3306/testdb）可經 Console.getDataSource() 連線並包含預期 Schema",
    command: "dbVerify(SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC')"
});

// Close the shared CacheDriverExecutor (closes the underlying connection,
// returning it to the pool - best-effort)
if (cde !== null && cde !== undefined) {
    try {
        cde.close();
    } catch (e) {
        print("[WARN] close CacheDriverExecutor: " + e);
    }
}

writeReport();
writeMd();
