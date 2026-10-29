// ============================================================================
// JsPlus-Core TestDBSync - dbsync module integration test (JS-first config)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestDBSync.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestDBSync-Report.html (TestBase
//            format, same as the other suites)
// Scope    : com.killcoding.dbsync - Enter.configure(ConfigProperties) JS-first
//            config entry, Database/Table wiring against the runtime H2 testdb,
//            CacheDriverExecutor CRUD path and quote/schema behaviour. The sync
//            worker loops (syncTo) are intentionally NOT started: they spawn
//            infinite background threads that would hang the suite runner.
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

var REPORT_TITLE = "JsPlus-Core DBSync Test";
var REPORT_NAME = "TestDBSync-Report.html";

beginTest();

print("=== JsPlus-Core DBSync Test ===");
print("JS_PLUS_PATH: " + basePath);

var EnterType = Java.type("com.killcoding.dbsync.Enter");
var DatabaseType = Java.type("com.killcoding.dbsync.Database");
var ConfigPropertiesType = Java.type("com.killcoding.tool.ConfigProperties");
var DriverDataSourceType = Java.type("com.killcoding.datasource.DriverDataSource");
var FileType = Java.type("java.io.File");

// Rows are java.util.Map keyed by column label; the runtime column-name case
// mode varies (H2 stores unquoted names uppercase), so reads are case-insensitive.
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

// dbVerify for the Target FILE database - TestBase.dbVerify is bound to the
// main Console.getDataSource() (testdb), so this local twin builds a
// DriverDataSource from config/DBSyncTarget.properties and runs the query
// over plain JDBC (same deterministic read pattern as TestBase.dbVerify).
// The structured outcome {sql, columns, rows} (max 30 rows) is stored in
// dbvTargetResult for writeReport to render as a nested table.
function dbVerifyTarget(sqlFn) {
    var dds = null;
    var conn = null;
    var stmt = null;
    var rs = null;
    var sql;
    var result = { sql: "", columns: [], rows: [] };
    try {
        dds = new DriverDataSourceType(new FileType(basePath + "/config/DBSyncTarget.properties"));
        sql = sqlFn();
        conn = dds.getConnection();
        stmt = conn.createStatement();
        rs = stmt.executeQuery(sql);
        var rsmd = rs.getMetaData();
        var c = rsmd.getColumnCount();
        for (var k = 0; k < c; k++) {
            result.columns.push(rsmd.getColumnLabel(k + 1) + "");
        }
        while (rs.next() && result.rows.length < 30) {
            var row = [];
            for (var k = 0; k < c; k++) {
                var v = rs.getObject(k + 1);
                row.push(v === null ? "null" : ("" + v));
            }
            result.rows.push(row);
        }
        result.sql = sql;
        dbvTargetResult = result;
        var sb = new StringBuilder();
        for (var i = 0; i < result.rows.length; i++) {
            sb.append((i > 0 ? "\n" : "") + "[" + result.rows[i].join(" ") + "]");
        }
        return "\n | dbVerifyTarget: " + sql + "\n   -> " + (result.rows.length > 0 ? sb.toString() : "(0 rows)");
    } catch (e) {
        result.sql = (sql !== undefined ? sql : "<sqlFn failed>");
        result.error = "" + e;
        dbvTargetResult = result;
        return "\n | dbVerifyTarget: " + result.sql + "\n   -> (query failed) " + e;
    } finally {
        if (rs !== null && rs !== undefined) {
            try { rs.close(); } catch (e2) {}
        }
        if (stmt !== null && stmt !== undefined) {
            try { stmt.close(); } catch (e3) {}
        }
        if (conn !== null && conn !== undefined) {
            try { conn.close(); } catch (e4) {}
        }
        if (dds !== null && dds !== undefined) {
            try { dds.closeAll(); } catch (e5) {}
        }
    }
}

// dbVerify for the SOURCE FILE database (dbsync_source): the sync source is
// now its own dedicated file DB (config/DBSyncSource.properties), NOT the
// shared testdb. This twin of dbVerifyTarget builds a DriverDataSource from
// DBSyncSource.properties and runs the query over plain JDBC, storing the
// outcome in dbvSourceResult (rendered alongside dbvTargetResult by check's
// dbvList).
function dbVerifySource(sqlFn) {
    var dds = null;
    var conn = null;
    var stmt = null;
    var rs = null;
    var sql;
    var result = { sql: "", columns: [], rows: [] };
    try {
        dds = new DriverDataSourceType(new FileType(basePath + "/config/DBSyncSource.properties"));
        sql = sqlFn();
        conn = dds.getConnection();
        stmt = conn.createStatement();
        rs = stmt.executeQuery(sql);
        var rsmd = rs.getMetaData();
        var c = rsmd.getColumnCount();
        for (var k = 0; k < c; k++) {
            result.columns.push(rsmd.getColumnLabel(k + 1) + "");
        }
        while (rs.next() && result.rows.length < 30) {
            var row = [];
            for (var k = 0; k < c; k++) {
                var v = rs.getObject(k + 1);
                row.push(v === null ? "null" : ("" + v));
            }
            result.rows.push(row);
        }
        result.sql = sql;
        dbvSourceResult = result;
        var sb = new StringBuilder();
        for (var i = 0; i < result.rows.length; i++) {
            sb.append((i > 0 ? "\n" : "") + "[" + result.rows[i].join(" ") + "]");
        }
        return "\n | dbVerifySource: " + sql + "\n   -> " + (result.rows.length > 0 ? sb.toString() : "(0 rows)");
    } catch (e) {
        result.sql = (sql !== undefined ? sql : "<sqlFn failed>");
        result.error = "" + e;
        dbvSourceResult = result;
        return "\n | dbVerifySource: " + result.sql + "\n   -> (query failed) " + e;
    } finally {
        if (rs !== null && rs !== undefined) {
            try { rs.close(); } catch (e2) {}
        }
        if (stmt !== null && stmt !== undefined) {
            try { stmt.close(); } catch (e3) {}
        }
        if (conn !== null && conn !== undefined) {
            try { conn.close(); } catch (e4) {}
        }
        if (dds !== null && dds !== undefined) {
            try { dds.closeAll(); } catch (e5) {}
        }
    }
}

// ---- JS-first configuration (instance-level Enter: init + configure) ----
var cp = new ConfigPropertiesType();
cp.setProperty("Source.Database.DataSource", "DBSyncSource.properties");
cp.setProperty("Source.Database.Schema", "PUBLIC");
cp.setProperty("Source.Database.Quote.Enable", "false");
cp.setProperty("Source.Database.MaxUsage", "0.9");
cp.setProperty("Target.Database.DataSource", "DBSyncTarget.properties");
cp.setProperty("Target.Database.Schema", "PUBLIC");
cp.setProperty("Target.Database.Quote.Enable", "false");
cp.setProperty("QuoteSrc.Database.DataSource", "DBSyncSource.properties");
cp.setProperty("QuoteSrc.Database.Schema", "PUBLIC");
cp.setProperty("QuoteSrc.Database.Quote.Enable", "true");
cp.setProperty("QuoteSrc.Database.Quote.Format", "\"%s\"");
cp.setProperty("Table.GetTables.List", "dbsync_test_a,dbsync_test_b,dbsync_sync_a");
cp.setProperty("Table.PrimaryKey", "id");
cp.setProperty("Table.Inserter.Key", "created_at");
cp.setProperty("Table.Inserter.Period", "5000");
cp.setProperty("Table.Inserter.Batch", "100");
cp.setProperty("Table.Inserter.Enable", "true");
cp.setProperty("Table.Timer", "200ms");
var enter = new EnterType();
enter.configure(cp);

try {
    // C1: Enter.configure injects JS config into the per-instance ENTER_PORPS store
    var dataSourceCfg = enter.getPorps().getString("Source.Database.DataSource");
    var schemaCfg = enter.getPorps().getString("Source.Database.Schema");
    var keyCfg = enter.getPorps().getString("Table.Inserter.Key");
    check({
        caseId: "C1",
        name: "Enter.configure accepts a JS-built ConfigProperties",
        ok: dataSourceCfg === "DBSyncSource.properties"
            && schemaCfg === "PUBLIC"
            && keyCfg === "created_at",
        detail: "DataSource=" + dataSourceCfg + " Schema=" + schemaCfg + " Inserter.Key=" + keyCfg,
        objective: "JS config replaces the enter.properties file (JS-first entry)",
        objectiveZh: "JS 配置取代 enter.properties 檔案（JS 優先入口）",
        command: "enter.init(); enter.configure(cp)"
    });

    // C2: Database("Source") is built from the JS config; the datasource is
    // materialised (usage queryable) and the real connection is exercised via
    // the Table path in C4 (open() is protected, not callable from JS).
    var dbSource = new DatabaseType("Source", enter.getPorps());
    var usageNow = dbSource.getUsage();
    check({
        caseId: "C2",
        name: "Database(Source) builds from the JS-configured datasource",
        ok: dbSource.getSourceOrTarget() === "Source" && usageNow >= 0,
        detail: "sourceOrTarget=" + dbSource.getSourceOrTarget() + " usage=" + usageNow,
        objective: "Database resolves config/<DataSource>.properties via ROOT_PATH(JS_PLUS_PATH)",
        objectiveZh: "Database 經 ROOT_PATH(JS_PLUS_PATH) 解析 config/ 下的資料源",
        command: "new Database('Source'); getUsage()"
    });

    // C3: Database.getTable resolves a configured table with JS-injected settings
    var tblA = dbSource.getTable("dbsync_test_a");
    var tableName = tblA !== null ? tblA.getQuoteName() : "(null)";
    var usage = dbSource.getUsage();
    var allowed = dbSource.allowedUseConnection();
    check({
        caseId: "C3",
        name: "getTable + usage guard honour the JS config (quote disabled)",
        ok: tblA !== null
            && tableName === "PUBLIC.dbsync_test_a"
            && usage >= 0
            && allowed === true,
        detail: "quoteName=" + tableName + " usage=" + usage + " allowed=" + allowed,
        objective: "Table.GetTables.List drives getTable; Quote.Enable=false gives plain names",
        objectiveZh: "Table.GetTables.List 驅動 getTable；Quote.Enable=false 為無引號名稱",
        command: "dbSource.getTable('dbsync_test_a').getQuoteName()"
    });

    // C4: Table CRUD via CacheDriverExecutor (execute/find) + DB verify
    // (table lifecycle owned by dbscript/change_sync.sql - init creates it)
    tblA.execute("DELETE FROM dbsync_test_a", null);
    var ins1 = tblA.execute("INSERT INTO dbsync_test_a (id, name, created_at) VALUES (?, ?, ?)",
        [1, "dbsync-one", "2026-10-28 00:00:00"]);
    var ins2 = tblA.execute("INSERT INTO dbsync_test_a (id, name, created_at) VALUES (?, ?, ?)",
        [2, "dbsync-two", "2026-10-28 00:00:01"]);
    var foundRows = tblA.find("SELECT id, name FROM dbsync_test_a WHERE id > ? ORDER BY id", [0]);
    var foundCount = (foundRows !== null && foundRows !== undefined) ? foundRows.size() : -1;
    var firstId = (foundCount > 0) ? ("" + mapGetIgnoreCase(foundRows.get(0), "ID")) : "?";
    var firstName = (foundCount > 0) ? ("" + mapGetIgnoreCase(foundRows.get(0), "NAME")) : "?";
    var dbv = dbVerifySource(function () {
        return "SELECT * FROM dbsync_test_a";
    });
    var dbvOk = dbv.indexOf("dbsync-one") >= 0 && dbv.indexOf("dbsync-two") >= 0;
    check({
        caseId: "C4",
        name: "Table.execute/find round-trip via CacheDriverExecutor",
        ok: foundCount === 2 && firstId === "1" && firstName === "dbsync-one" && dbvOk,
        detail: "inserted=" + (ins1 + ins2) + " found=" + foundCount + " first=[" + firstId + ", " + firstName + "] dbVerifySource=" + dbv.trim(),
        objective: "dbsync Table CRUD runs over CacheDriverExecutor and persists into the dbsync source DB",
        objectiveZh: "dbsync Table CRUD 經 CacheDriverExecutor 執行並持久化到 dbsync source 庫",
        command: "tbl.execute(sql, params); tbl.find(sql, params); dbVerifySource(...)",
        dbvList: [
            { label: "Source (dbsync_source)", result: dbvSourceResult }
        ]
    });

    // no cleanup DROP: dbsync_test_a lifecycle is owned by dbscript/change_sync.sql
    // (init recreates it; tests must not drop tables that exist in change_sync.sql)

    // C5: quote-enabled database instance (separate sourceOrTarget name)
    var dbQuote = new DatabaseType("QuoteSrc", enter.getPorps());
    var quotedKey = dbQuote.getTable("dbsync_test_a").quote("id");
    var quotedName = dbQuote.getTable("dbsync_test_a").getQuoteName();
    check({
        caseId: "C5",
        name: "Quote.Enable=true wraps identifiers (quote/getQuoteName)",
        ok: quotedKey === "\"id\"" && quotedName === "\"PUBLIC\".\"dbsync_test_a\"",
        detail: "quote(id)=" + quotedKey + " quoteName=" + quotedName,
        objective: "quoteFormat '%s' applies to column keys and schema.table names",
        objectiveZh: "quoteFormat '%s' 套用於欄位鍵與 schema.table 名稱",
        command: "quote('id'); getQuoteName()"
    });

    // C6: Target database resolves to its own independent H2 FILE database
    var dbTarget = new DatabaseType("Target", enter.getPorps());
    check({
        caseId: "C6",
        name: "Database(Target) resolves to the independent DBSyncTarget database",
        ok: dbTarget.getSourceOrTarget() === "Target" && dbTarget.getUsage() >= 0,
        detail: "sourceOrTarget=" + dbTarget.getSourceOrTarget() + " usage=" + dbTarget.getUsage(),
        objective: "Target.Database.DataSource points at Database/dbsync_target (not testdb)",
        objectiveZh: "Target.Database.DataSource 指向獨立 Database/dbsync_target（非 testdb）",
        command: "new Database('Target'); getUsage()"
    });

    // C7: Target table path really writes/reads its own file database - the
    // round trip goes through Table.execute/find (CacheDriverExecutor over the
    // Target datasource). The table lifecycle is owned by
    // dbscript/change_sync.sql (init creates dbsync_test_a on BOTH dbsync
    // file databases), so the test only clears + writes rows; dbVerifyTarget
    // (DBSyncTarget.properties) is the authoritative check for the Target DB.
    var tblT = dbTarget.getTable("dbsync_test_a");
    tblT.execute("DELETE FROM dbsync_test_a", null);
    var tIns = tblT.execute("INSERT INTO dbsync_test_a (id, name) VALUES (?, ?)", [100, "target-row"]);
    var tFound = tblT.find("SELECT id, name FROM dbsync_test_a WHERE id = ?", [100]);
    var tCount = (tFound !== null && tFound !== undefined) ? tFound.size() : -1;
    var tName = (tCount > 0) ? ("" + mapGetIgnoreCase(tFound.get(0), "NAME")) : "?";
    var tId = (tCount > 0) ? ("" + mapGetIgnoreCase(tFound.get(0), "ID")) : "?";
    var dbvT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_test_a";
    });
    var dbvTOk = dbvT.indexOf("target-row") >= 0 && dbvT.indexOf("100") >= 0;
    dbvResult = dbvTargetResult;
    check({
        caseId: "C7",
        name: "Target table CRUD round-trip on the independent file database",
        ok: tCount === 1 && tId === "100" && tName === "target-row" && dbvTOk,
        detail: "inserted=" + tIns + " found=" + tCount + " [" + tId + ", " + tName + "] dbVerifyTarget=" + dbvT.trim(),
        objective: "Target writes never touch testdb (independent Database/dbsync_target)",
        objectiveZh: "Target 寫入不觸及 testdb（獨立 Database/dbsync_target）",
        command: "target.getTable('dbsync_test_a').execute(...); find(...); dbVerifyTarget('SELECT * ...')"
    });

    // C8: instance-level config - two Database instances with separate
    // ConfigProperties stores (new Database(sourceOrTarget, porps)) do not
    // pollute each other, unlike the shared static ENTER_PORPS
    var cpInstA = new ConfigPropertiesType();
    cpInstA.setProperty("Source.Database.DataSource", "DBSyncSource.properties");
    cpInstA.setProperty("Source.Database.Schema", "PUBLIC");
    cpInstA.setProperty("Table.GetTables.List", "dbsync_test_a");
    var cpInstB = new ConfigPropertiesType();
    cpInstB.setProperty("Source.Database.DataSource", "DBSyncTarget.properties");
    cpInstB.setProperty("Source.Database.Schema", "TGT_SCHEMA");
    cpInstB.setProperty("Table.GetTables.List", "dbsync_test_a");
    var dbInstA = new DatabaseType("Source", cpInstA);
    var dbInstB = new DatabaseType("Source", cpInstB);
    var instNameA = dbInstA.getTable("dbsync_test_a").getQuoteName();
    var instNameB = dbInstB.getTable("dbsync_test_a").getQuoteName();
    check({
        caseId: "C8",
        name: "Instance-level ConfigProperties (non-static) stays independent",
        ok: instNameA === "PUBLIC.dbsync_test_a"
            && instNameB === "TGT_SCHEMA.dbsync_test_a"
            && instNameA !== instNameB,
        detail: "instA=" + instNameA + " instB=" + instNameB,
        objective: "new Database(name, porps) isolates config per instance (no global pollution)",
        objectiveZh: "new Database(name, porps) 每實例隔離配置（無全局污染）",
        command: "new Database('Source', cpInstA); new Database('Source', cpInstB)"
    });

    // C9: REAL source->target table sync - the core purpose of dbsync. Source
    // (testdb) has dbsync_sync_a with 3 rows; Database.syncTo(target) starts the
    // inserter worker loop, which pushes the missing rows into Target
    // (dbsync_target). The sync worker reads column maps by lower-case keys
    // (dbsync's own convention), so this case pins the LOWER column-name mode
    // first - the same mode the runtime console sets globally (GhostConsole),
    // mirroring production. The worker loop is intentionally left running: this
    // suite is the last one, and TestAll.js System.exit terminates the JVM (and
    // with it the worker threads) right after.
    var CCE = Java.type("com.killcoding.datasource.DriverExecutor");
    CCE.COLUMN_NAME_CASE_MODE = CCE.COLUMN_NAME_CASE_LOWER;

    // C9-C11 use their own instance config so ONLY dbsync_sync_a is queued for
    // the sync workers (the shared cp queues dbsync_test_a/b too, whose tables
    // are dropped earlier - that would spawn no-op workers spamming errors).
    var cpSync = new ConfigPropertiesType();
    cpSync.setProperty("Source.Database.DataSource", "DBSyncSource.properties");
    cpSync.setProperty("Source.Database.Schema", "PUBLIC");
    cpSync.setProperty("Source.Database.Quote.Enable", "false");
    cpSync.setProperty("Target.Database.DataSource", "DBSyncTarget.properties");
    cpSync.setProperty("Target.Database.Schema", "PUBLIC");
    cpSync.setProperty("Target.Database.Quote.Enable", "false");
    cpSync.setProperty("Table.GetTables.List", "dbsync_sync_a");
    cpSync.setProperty("Table.PrimaryKey", "id");
    cpSync.setProperty("Table.Inserter.Key", "created_at");
    cpSync.setProperty("Table.Inserter.Enable", "true");
    cpSync.setProperty("Table.Timer", "200ms");

    var dbSyncS = new DatabaseType("Source", cpSync);
    var dbSyncT = new DatabaseType("Target", cpSync);
    var tblSyncS = dbSyncS.getTable("dbsync_sync_a");
    var tblSyncT = dbSyncT.getTable("dbsync_sync_a");
    tblSyncS.execute("DELETE FROM dbsync_sync_a", null); // source table owned by dbscript/change_sync.sql
    tblSyncT.execute("DELETE FROM dbsync_sync_a", null); // both file DBs owned by dbscript/change_sync.sql
    var TimestampType = Java.type("java.sql.Timestamp");
    var syncBaseTs = new TimestampType(new Date().getTime() - 5000);
    tblSyncS.execute("INSERT INTO dbsync_sync_a (id, name, created_at) VALUES (?, ?, ?)", ["sync-1", "alpha", syncBaseTs]);
    tblSyncS.execute("INSERT INTO dbsync_sync_a (id, name, created_at) VALUES (?, ?, ?)", ["sync-2", "beta", syncBaseTs]);
    tblSyncS.execute("INSERT INTO dbsync_sync_a (id, name, created_at) VALUES (?, ?, ?)", ["sync-3", "gamma", syncBaseTs]);
    var sourceCount = tblSyncS.find("SELECT id FROM dbsync_sync_a", null).size();

    dbSyncS.syncTo(dbSyncT); // starts the inserter worker loop (periodic rounds)

    var syncedCount = -1;
    for (var w = 0; w < 40; w++) {
        var syncRows = tblSyncT.find("SELECT id FROM dbsync_sync_a", null);
        syncedCount = (syncRows !== null && syncRows !== undefined) ? syncRows.size() : 0;
        if (syncedCount >= sourceCount) {
            break;
        }
        java.lang.Thread.sleep(250);
    }
    var dbvSync = dbVerifySource(function () {
        return "SELECT * FROM dbsync_sync_a ORDER BY id";
    });
    var dbvSyncT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_sync_a ORDER BY id";
    });
    var syncOk = syncedCount === sourceCount
        && dbvSync.indexOf("sync-1") >= 0
        && dbvSyncT.indexOf("sync-3") >= 0;
    check({
        caseId: "C9",
        name: "Real source->target sync pushes dbsync_sync_a rows into the Target database",
        ok: syncOk,
        detail: "sourceCount=" + sourceCount + " targetCount=" + syncedCount
            + " dbVerifySource=" + dbvSync.trim() + " dbVerifyTarget=" + dbvSyncT.trim(),
        objective: "Database.syncTo copies source rows to the target table (inserter worker)",
        objectiveZh: "Database.syncTo 將 source 資料列同步至 target 表（inserter worker）",
        command: "dbSyncS.syncTo(dbSyncT); poll target count; dbVerifySource+Target('SELECT * ...')",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

    // C10: stopSync() terminates the worker - after stopping, a new source row
    // must NOT be pushed to the target any more
    dbSyncS.stopSync();
    java.lang.Thread.sleep(500);
    tblSyncS.execute("INSERT INTO dbsync_sync_a (id, name, created_at) VALUES (?, ?, ?)", ["sync-4", "delta", syncBaseTs]);
    java.lang.Thread.sleep(1200);
    var targetAfterStop = tblSyncT.find("SELECT id FROM dbsync_sync_a", null);
    var targetAfterStopCount = (targetAfterStop !== null && targetAfterStop !== undefined) ? targetAfterStop.size() : -1;
    var sourceAfterStop = tblSyncS.find("SELECT id FROM dbsync_sync_a", null);
    var sourceAfterStopCount = (sourceAfterStop !== null && sourceAfterStop !== undefined) ? sourceAfterStop.size() : -1;
    var dbvStopS = dbVerifySource(function () {
        return "SELECT * FROM dbsync_sync_a ORDER BY id";
    });
    var dbvStopT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_sync_a ORDER BY id";
    });
    check({
        caseId: "C10",
        name: "stopSync() stops the worker (no further rows pushed)",
        ok: targetAfterStopCount === 3 && sourceAfterStopCount === 4,
        detail: "after stop: sourceCount=" + sourceAfterStopCount + " targetCount=" + targetAfterStopCount + " (stayed 3 - worker stopped)"
            + " dbVerifySource=" + dbvStopS.trim() + " dbVerifyTarget=" + dbvStopT.trim(),
        objective: "Database.stopSync terminates the sync workers cleanly",
        objectiveZh: "Database.stopSync 乾淨終止同步 worker",
        command: "dbSyncS.stopSync(); insert 1 more source row; target count must not grow",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

    // C11: start -> stop -> restart - syncTo after stopSync starts a fresh worker
    // and pushes the remaining rows (sync-4 and sync-5) into the target
    tblSyncS.execute("INSERT INTO dbsync_sync_a (id, name, created_at) VALUES (?, ?, ?)", ["sync-5", "epsilon", syncBaseTs]);
    dbSyncS.syncTo(dbSyncT); // restart the worker
    var restartCount = -1;
    for (var w2 = 0; w2 < 40; w2++) {
        var restartRows = tblSyncT.find("SELECT id FROM dbsync_sync_a", null);
        restartCount = (restartRows !== null && restartRows !== undefined) ? restartRows.size() : 0;
        if (restartCount >= 5) {
            break;
        }
        java.lang.Thread.sleep(250);
    }
    dbSyncS.stopSync(); // clean stop again
    var dbvRestart = dbVerifySource(function () {
        return "SELECT * FROM dbsync_sync_a ORDER BY id";
    });
    var dbvRestartT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_sync_a ORDER BY id";
    });
    var restartOk = restartCount === 5
        && dbvRestart.indexOf("sync-4") >= 0
        && dbvRestartT.indexOf("sync-5") >= 0;
    check({
        caseId: "C11",
        name: "start -> stop -> restart cycle works (worker can be started again)",
        ok: restartOk,
        detail: "after restart: targetCount=" + restartCount
            + " dbVerifySource=" + dbvRestart.trim() + " dbVerifyTarget=" + dbvRestartT.trim(),
        objective: "syncTo after stopSync restarts the worker and pushes remaining rows",
        objectiveZh: "stopSync 後再 syncTo 可重啟 worker 並推送剩餘資料列",
        command: "dbSyncS.syncTo(dbSyncT) again; poll target == 5; dbSyncS.stopSync()",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

    // C12: Enter.start(enterCnf) - the config-file programmatic entry. It loads
    // <ROOT_PATH>/config/<enterCnf>, builds its own Source/Target databases and
    // pushes tables; the workers are tracked so Enter.stop() terminates them all.
    // The test seeds identical tables first (via its own Database instances), then
    // start() pushes source rows, stop() halts the worker (a new source row must
    // NOT reach the target any more) and a repeated start() is idempotent.
    var cpEnt = new ConfigPropertiesType();
    cpEnt.setProperty("Source.Database.DataSource", "DBSyncSource.properties");
    cpEnt.setProperty("Source.Database.Schema", "PUBLIC");
    cpEnt.setProperty("Source.Database.Quote.Enable", "false");
    cpEnt.setProperty("Target.Database.DataSource", "DBSyncTarget.properties");
    cpEnt.setProperty("Target.Database.Schema", "PUBLIC");
    cpEnt.setProperty("Target.Database.Quote.Enable", "false");
    cpEnt.setProperty("Table.GetTables.List", "dbsync_ent_a");
    cpEnt.setProperty("Table.PrimaryKey", "id");
    cpEnt.setProperty("Table.Inserter.Key", "created_at");
    cpEnt.setProperty("Table.Inserter.Enable", "true");
    cpEnt.setProperty("Table.Updater.Enable", "false");
    cpEnt.setProperty("Table.Deleter.Enable", "false");
    cpEnt.setProperty("Table.Remover.Enable", "false");
    cpEnt.setProperty("Table.Timer", "200ms");

    var dbEntS = new DatabaseType("Source", cpEnt);
    var dbEntT = new DatabaseType("Target", cpEnt);
    var tblEntS = dbEntS.getTable("dbsync_ent_a");
    var tblEntT = dbEntT.getTable("dbsync_ent_a");
    tblEntS.execute("DELETE FROM dbsync_ent_a", null); // source table owned by dbscript/change_sync.sql
    tblEntT.execute("DELETE FROM dbsync_ent_a", null); // both file DBs owned by dbscript/change_sync.sql
    tblEntS.execute("INSERT INTO dbsync_ent_a (id, name, created_at) VALUES (?, ?, ?)", ["ent-1", "alpha", syncBaseTs]);
    tblEntS.execute("INSERT INTO dbsync_ent_a (id, name, created_at) VALUES (?, ?, ?)", ["ent-2", "beta", syncBaseTs]);
    tblEntS.execute("INSERT INTO dbsync_ent_a (id, name, created_at) VALUES (?, ?, ?)", ["ent-3", "gamma", syncBaseTs]);

    // config is static: JsPlus/config/EnterTest.properties (no dynamic generation)

    var ent = new EnterType();
    ent.configure(new ConfigPropertiesType());
    ent.start("EnterTest.properties"); // loads the file + starts the workers

    var entCount = -1;
    for (var w3 = 0; w3 < 40; w3++) {
        var entRows = tblEntT.find("SELECT id FROM dbsync_ent_a", null);
        entCount = (entRows !== null && entRows !== undefined) ? entRows.size() : 0;
        if (entCount >= 3) {
            break;
        }
        java.lang.Thread.sleep(250);
    }

    ent.stop(); // terminate all workers started by start()

    // after stop: a new source row must NOT reach the target any more
    tblEntS.execute("INSERT INTO dbsync_ent_a (id, name, created_at) VALUES (?, ?, ?)", ["ent-4", "delta", syncBaseTs]);
    java.lang.Thread.sleep(1200);
    var entAfterStop = tblEntT.find("SELECT id FROM dbsync_ent_a", null);
    var entAfterStopCount = (entAfterStop !== null && entAfterStop !== undefined) ? entAfterStop.size() : -1;

    var dbvEnt = dbVerifySource(function () {
        return "SELECT * FROM dbsync_ent_a ORDER BY id";
    });
    var dbvEntT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_ent_a ORDER BY id";
    });
    var entOk = entCount === 3
        && entAfterStopCount === 3
        && dbvEnt.indexOf("ent-1") >= 0
        && dbvEntT.indexOf("ent-3") >= 0;
    check({
        caseId: "C12",
        name: "Enter.start(configFile) one-key entry + Enter.stop()",
        ok: entOk,
        detail: "start pushed targetCount=" + entCount + "; after stop stayed=" + entAfterStopCount
            + " dbVerifySource=" + dbvEnt.trim() + " dbVerifyTarget=" + dbvEntT.trim(),
        objective: "Enter.start loads the config file, syncs tables, and stop() terminates every worker",
        objectiveZh: "Enter.start 讀取設定檔、同步表格，stop() 終止全部 worker",
        command: "ent.start('EnterTest.properties'); poll target == 3; ent.stop(); insert more; target stays 3",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

    // C13-C16: BULK load tests - large volumes through each of the four sync
    // workers (inserter / updater / deleter / remover), one dedicated table and
    // one dedicated Database pair per worker so the cases stay independent.
    var syncBaseTs2 = new TimestampType(new Date().getTime() - 10000);
    var updTs = new TimestampType(new Date().getTime() - 5000);
    var oldTs = new TimestampType(new Date().getTime() - 10000);

    function loadCp(tblName, opts) {
        var cp = new ConfigPropertiesType();
        cp.setProperty("Source.Database.DataSource", "DBSyncSource.properties");
        cp.setProperty("Source.Database.Schema", "PUBLIC");
        cp.setProperty("Source.Database.Quote.Enable", "false");
        cp.setProperty("Target.Database.DataSource", "DBSyncTarget.properties");
        cp.setProperty("Target.Database.Schema", "PUBLIC");
        cp.setProperty("Target.Database.Quote.Enable", "false");
        cp.setProperty("Table.GetTables.List", tblName);
        cp.setProperty("Table.PrimaryKey", "id");
        cp.setProperty("Table.Inserter.Key", "created_at");
        cp.setProperty("Table.Inserter.Batch", "60000");
        cp.setProperty("Table.Updater.Key", "updated_at");
        cp.setProperty("Table.Updater.Batch", "60000");
        cp.setProperty("Table.Deleter.Key", "deleted_at");
        cp.setProperty("Table.Deleter.Batch", "60000");
        cp.setProperty("Table.Deleter.DeletedKey", "deleted");
        cp.setProperty("Table.Inserter.Enable", opts.inserter ? "true" : "false");
        cp.setProperty("Table.Updater.Enable", opts.updater ? "true" : "false");
        cp.setProperty("Table.Deleter.Enable", opts.deleter ? "true" : "false");
        cp.setProperty("Table.Remover.Enable", opts.remover ? "true" : "false");
        cp.setProperty("Table.Remover.Key", "deleted_at");
        cp.setProperty("Table.Remover.Timer", "200ms");
        cp.setProperty("Table.Remover.Before", opts.beforeDays ? opts.beforeDays : "60000");
        cp.setProperty("Table.Timer", "200ms");
        return cp;
    }

    function selectCount(tbl, sql) {
        var r = tbl.first(sql, null);
        if (r === null || r === undefined) {
            return -1;
        }
        var v = mapGetIgnoreCase(r, "c");
        return (v !== undefined && v !== null) ? parseInt("" + v) : -1;
    }

    function pollCount(tbl, sql, expect, tries) {
        var c = -1;
        for (var w = 0; w < tries; w++) {
            c = selectCount(tbl, sql);
            if (c >= expect) {
                break;
            }
            java.lang.Thread.sleep(250);
        }
        return c;
    }

    // C13: BULK INSERT - 500 rows pushed by the inserter worker
    var cpI = loadCp("dbsync_load_i", { inserter: true });
    var dbI = new DatabaseType("Source", cpI);
    var dbIT = new DatabaseType("Target", cpI);
    var tblI = dbI.getTable("dbsync_load_i");
    var tblIT = dbIT.getTable("dbsync_load_i");
    tblI.execute("DELETE FROM dbsync_load_i", null); // source table owned by dbscript/change_sync.sql
    tblIT.execute("DELETE FROM dbsync_load_i", null); // both file DBs owned by dbscript/change_sync.sql
    var LOAD_I = 500;
    for (var i = 1; i <= LOAD_I; i++) {
        tblI.execute("INSERT INTO dbsync_load_i (id, name, created_at) VALUES (?, ?, ?)", ["li-" + i, "row-" + i, syncBaseTs]);
    }
    dbI.syncTo(dbIT);
    var loadICount = pollCount(tblIT, "SELECT COUNT(*) AS c FROM dbsync_load_i", LOAD_I, 60);
    dbI.stopSync();
    var dbvLoadI = dbVerifySource(function () {
        return "SELECT * FROM dbsync_load_i WHERE id IN ('li-1','li-250','li-500') ORDER BY id";
    });
    var dbvLoadIT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_load_i WHERE id IN ('li-1','li-250','li-500') ORDER BY id";
    });
    var loadIOk = loadICount === LOAD_I
        && dbvLoadI.indexOf("li-1") >= 0
        && dbvLoadIT.indexOf("li-500") >= 0;
    check({
        caseId: "C13",
        name: "Bulk insert: 500 rows pushed by the inserter worker",
        ok: loadIOk,
        detail: "targetCount=" + loadICount + "/" + LOAD_I
            + " dbVerifySource=" + dbvLoadI.trim() + " dbVerifyTarget=" + dbvLoadIT.trim(),
        objective: "Inserter handles a large volume (batch-window push) end to end",
        objectiveZh: "Inserter 大量資料（批次窗口推送）端到端驗證",
        command: "500 source rows -> syncTo -> poll SELECT COUNT(*) == 500",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

    // C14: BULK UPDATE - 300 rows inserted+synced, then 200 updated; the
    // updater overwrites the target rows (key timestamp newer)
    var cpU = loadCp("dbsync_load_u", { inserter: true, updater: true });
    var dbU = new DatabaseType("Source", cpU);
    var dbUT = new DatabaseType("Target", cpU);
    var tblU = dbU.getTable("dbsync_load_u");
    var tblUT = dbUT.getTable("dbsync_load_u");
    tblU.execute("DELETE FROM dbsync_load_u", null); // source table owned by dbscript/change_sync.sql
    tblUT.execute("DELETE FROM dbsync_load_u", null); // both file DBs owned by dbscript/change_sync.sql
    var LOAD_U = 300;
    for (var u = 1; u <= LOAD_U; u++) {
        tblU.execute("INSERT INTO dbsync_load_u (id, name, created_at) VALUES (?, ?, ?)", ["lu-" + u, "base-" + u, syncBaseTs2]);
    }
    dbU.syncTo(dbUT);
    var loadUBase = pollCount(tblUT, "SELECT COUNT(*) AS c FROM dbsync_load_u", LOAD_U, 60);
    // update 200 of them (newer updated_at)
    for (var u2 = 1; u2 <= 200; u2++) {
        tblU.execute("UPDATE dbsync_load_u SET name = ?, updated_at = ? WHERE id = ?", ["upd-" + u2, updTs, "lu-" + u2]);
    }
    var loadUUpd = pollCount(tblUT, "SELECT COUNT(*) AS c FROM dbsync_load_u WHERE name LIKE 'upd-%'", 200, 60);
    dbU.stopSync();
    var dbvLoadU = dbVerifySource(function () {
        return "SELECT * FROM dbsync_load_u WHERE id IN ('lu-1','lu-200','lu-300') ORDER BY id";
    });
    var dbvLoadUT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_load_u WHERE id IN ('lu-1','lu-200','lu-300') ORDER BY id";
    });
    var loadUOk = loadUBase === LOAD_U && loadUUpd === 200
        && dbvLoadU.indexOf("upd-200") >= 0
        && dbvLoadUT.indexOf("upd-200") >= 0;
    check({
        caseId: "C14",
        name: "Bulk update: 200 of 300 rows overwritten on the target by the updater worker",
        ok: loadUOk,
        detail: "targetBase=" + loadUBase + " updatedOnTarget=" + loadUUpd
            + " dbVerifySource=" + dbvLoadU.trim() + " dbVerifyTarget=" + dbvLoadUT.trim(),
        objective: "Updater propagates a large change set (newer updated_at) to the target",
        objectiveZh: "Updater 大量變更（updated_at 較新）傳播至 target",
        command: "insert 300 -> sync -> update 200 -> poll SELECT COUNT(*) WHERE name LIKE 'upd-%' == 200",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

    // C15: BULK DELETE (soft) - 300 rows inserted+synced, then 200 soft-deleted;
    // the deleter propagates the deleted flag to the target
    var cpD = loadCp("dbsync_load_d", { inserter: true, deleter: true });
    var dbD = new DatabaseType("Source", cpD);
    var dbDT = new DatabaseType("Target", cpD);
    var tblD = dbD.getTable("dbsync_load_d");
    var tblDT = dbDT.getTable("dbsync_load_d");
    tblD.execute("DELETE FROM dbsync_load_d", null); // source table owned by dbscript/change_sync.sql
    tblDT.execute("DELETE FROM dbsync_load_d", null); // both file DBs owned by dbscript/change_sync.sql
    var LOAD_D = 300;
    for (var d = 1; d <= LOAD_D; d++) {
        tblD.execute("INSERT INTO dbsync_load_d (id, name, created_at, deleted) VALUES (?, ?, ?, 0)", ["ld-" + d, "base-" + d, syncBaseTs2]);
    }
    dbD.syncTo(dbDT);
    var loadDBase = pollCount(tblDT, "SELECT COUNT(*) AS c FROM dbsync_load_d", LOAD_D, 60);
    for (var d2 = 1; d2 <= 200; d2++) {
        tblD.execute("UPDATE dbsync_load_d SET deleted = 1, deleted_at = ? WHERE id = ?", [updTs, "ld-" + d2]);
    }
    var loadDDel = pollCount(tblDT, "SELECT COUNT(*) AS c FROM dbsync_load_d WHERE deleted = 1", 200, 60);
    dbD.stopSync();
    var dbvLoadD = dbVerifySource(function () {
        return "SELECT * FROM dbsync_load_d WHERE id IN ('ld-1','ld-200','ld-300') ORDER BY id";
    });
    var dbvLoadDT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_load_d WHERE id IN ('ld-1','ld-200','ld-300') ORDER BY id";
    });
    var loadDOk = loadDBase === LOAD_D && loadDDel === 200
        && dbvLoadD.indexOf("ld-200") >= 0
        && dbvLoadDT.indexOf("ld-200") >= 0;
    check({
        caseId: "C15",
        name: "Bulk soft-delete: 200 of 300 rows marked deleted on the target by the deleter worker",
        ok: loadDOk,
        detail: "targetBase=" + loadDBase + " deletedOnTarget=" + loadDDel
            + " dbVerifySource=" + dbvLoadD.trim() + " dbVerifyTarget=" + dbvLoadDT.trim(),
        objective: "Deleter propagates a large soft-delete set (deleted flag) to the target",
        objectiveZh: "Deleter 大量軟刪（deleted 標記）傳播至 target",
        command: "insert 300 -> sync -> soft-delete 200 -> poll SELECT COUNT(*) WHERE deleted=1 == 200",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

    // C16: BULK REMOVE - 100 rows on source (50 soft-deleted & expired, 50
    // active); the remover worker PHYSICALLY deletes the expired rows from the
    // SOURCE table (source-side cleanup semantics, see Remover javadoc)
    var cpR = loadCp("dbsync_load_r", { remover: true, beforeDays: "1000ms" });
    var dbR = new DatabaseType("Source", cpR);
    var dbRT = new DatabaseType("Target", cpR);
    var tblR = dbR.getTable("dbsync_load_r");
    var tblRT = dbRT.getTable("dbsync_load_r");
    tblR.execute("DELETE FROM dbsync_load_r", null); // source table owned by dbscript/change_sync.sql
    tblRT.execute("DELETE FROM dbsync_load_r", null); // both file DBs owned by dbscript/change_sync.sql
    for (var r = 1; r <= 50; r++) {
        tblR.execute("INSERT INTO dbsync_load_r (id, name, deleted, deleted_at) VALUES (?, ?, 1, ?)", ["lr-" + r, "expired-" + r, oldTs]);
    }
    for (var r2 = 51; r2 <= 100; r2++) {
        tblR.execute("INSERT INTO dbsync_load_r (id, name, deleted, deleted_at) VALUES (?, ?, 0, ?)", ["lr-" + r2, "active-" + r2, null]);
    }
    dbR.syncTo(dbRT); // remover-only worker
    java.lang.Thread.sleep(2500); // several cleanup rounds
    dbR.stopSync();
    var loadRRemain = selectCount(tblR, "SELECT COUNT(*) AS c FROM dbsync_load_r");
    var loadRSoftLeft = selectCount(tblR, "SELECT COUNT(*) AS c FROM dbsync_load_r WHERE deleted = 1");
    var dbvLoadR = dbVerifySource(function () {
        return "SELECT * FROM dbsync_load_r WHERE deleted = 0 ORDER BY id";
    });
    var dbvLoadRT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_load_r ORDER BY id";
    });
    var loadROk = loadRRemain === 50 && loadRSoftLeft === 0;
    check({
        caseId: "C16",
        name: "Bulk remove: 50 expired soft-deleted rows physically cleaned from the SOURCE by the remover worker",
        ok: loadROk,
        detail: "sourceRemaining=" + loadRRemain + " (50 active kept) softDeletedLeft=" + loadRSoftLeft
            + " (0 = expired rows lr-1..lr-50 physically removed)"
            + " dbVerifySource=" + dbvLoadR.trim() + " dbVerifyTarget=" + dbvLoadRT.trim(),
        objective: "Remover cleans a large expired set from the source table (source-side semantics)",
        objectiveZh: "Remover 大量清理 source 表過期軟刪行（源端清理語義）",
        command: "100 source rows (50 expired) -> remover worker -> SELECT COUNT(*) == 50 AND softDeletedLeft == 0",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

    // C17: REVERSE (bidirectional) sync - Reverse=true makes Enter.start build a
    // second Source/Target pair pushing rows in the opposite direction too.
    // Source rows the Target lacks are pushed to the Target, and Target rows the
    // Source lacks are pushed back to the Source (inserter workers on both sides).
    var cpRev = new ConfigPropertiesType();
    cpRev.setProperty("Source.Database.DataSource", "DBSyncSource.properties");
    cpRev.setProperty("Source.Database.Schema", "PUBLIC");
    cpRev.setProperty("Source.Database.Quote.Enable", "false");
    cpRev.setProperty("Target.Database.DataSource", "DBSyncTarget.properties");
    cpRev.setProperty("Target.Database.Schema", "PUBLIC");
    cpRev.setProperty("Target.Database.Quote.Enable", "false");
    cpRev.setProperty("Reverse", "true");
    cpRev.setProperty("Table.GetTables.List", "dbsync_rev_a");
    cpRev.setProperty("Table.PrimaryKey", "id");
    cpRev.setProperty("Table.Inserter.Key", "created_at");
    cpRev.setProperty("Table.Inserter.Enable", "true");
    cpRev.setProperty("Table.Updater.Enable", "false");
    cpRev.setProperty("Table.Deleter.Enable", "false");
    cpRev.setProperty("Table.Remover.Enable", "false");
    cpRev.setProperty("Table.Timer", "200ms");
    var dbRevS = new DatabaseType("Source", cpRev);
    var dbRevT = new DatabaseType("Target", cpRev);
    var tblRevS = dbRevS.getTable("dbsync_rev_a");
    var tblRevT = dbRevT.getTable("dbsync_rev_a");
    tblRevS.execute("DELETE FROM dbsync_rev_a", null); // source table owned by dbscript/change_sync.sql
    tblRevT.execute("DELETE FROM dbsync_rev_a", null); // both file DBs owned by dbscript/change_sync.sql
    // seed each side with rows the other side lacks
    tblRevS.execute("INSERT INTO dbsync_rev_a (id, name, created_at) VALUES (?, ?, ?)", ["rev-1", "src-one", syncBaseTs]);
    tblRevS.execute("INSERT INTO dbsync_rev_a (id, name, created_at) VALUES (?, ?, ?)", ["rev-2", "src-two", syncBaseTs]);
    tblRevS.execute("INSERT INTO dbsync_rev_a (id, name, created_at) VALUES (?, ?, ?)", ["rev-3", "src-three", syncBaseTs]);
    tblRevT.execute("INSERT INTO dbsync_rev_a (id, name, created_at) VALUES (?, ?, ?)", ["rev-4", "tgt-four", syncBaseTs]);
    tblRevT.execute("INSERT INTO dbsync_rev_a (id, name, created_at) VALUES (?, ?, ?)", ["rev-5", "tgt-five", syncBaseTs]);
    // Enter.start loads the static reverse config and starts BOTH directions
    var entRev = new EnterType();
    entRev.configure(new ConfigPropertiesType());
    entRev.start("EnterReverseTest.properties");
    var revSourceCount = -1;
    var revTargetCount = -1;
    for (var w4 = 0; w4 < 60; w4++) {
        revSourceCount = selectCount(tblRevS, "SELECT COUNT(*) AS c FROM dbsync_rev_a");
        revTargetCount = selectCount(tblRevT, "SELECT COUNT(*) AS c FROM dbsync_rev_a");
        if (revSourceCount >= 5 && revTargetCount >= 5) {
            break;
        }
        java.lang.Thread.sleep(250);
    }
    entRev.stop();
    var dbvRevS = dbVerifySource(function () {
        return "SELECT * FROM dbsync_rev_a ORDER BY id";
    });
    var dbvRevT = dbVerifyTarget(function () {
        return "SELECT * FROM dbsync_rev_a ORDER BY id";
    });
    var revOk = revSourceCount === 5 && revTargetCount === 5
        && dbvRevS.indexOf("rev-4") >= 0
        && dbvRevT.indexOf("rev-1") >= 0;
    check({
        caseId: "C17",
        name: "Reverse=true bidirectional sync merges both sides (each side gains the other's rows)",
        ok: revOk,
        detail: "after reverse sync: sourceCount=" + revSourceCount + " targetCount=" + revTargetCount
            + " (rev-4/rev-5 reached Source, rev-1..rev-3 reached Target)"
            + " dbVerifySource=" + dbvRevS.trim() + " dbVerifyTarget=" + dbvRevT.trim(),
        objective: "Reverse=true builds a second pair so rows flow in both directions (inserter workers on both sides)",
        objectiveZh: "Reverse=true 建立反向配對，資料列雙向流動（兩側 inserter worker）",
        command: "ent.start('EnterReverseTest.properties'); source 3 rows + target 2 rows -> both sides == 5; ent.stop()",
        dbvList: [
            { label: "Source (testdb)", result: dbvSourceResult },
            { label: "Target (dbsync_target)", result: dbvTargetResult }
        ]
    });

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestDBSync suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
