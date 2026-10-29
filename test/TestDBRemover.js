// ============================================================================
// JsPlus-Core TestDBRemover - evidence check: which database does the Remover
// worker actually delete from?
// ----------------------------------------------------------------------------
// Run      : test/TestDBRemover.bat (standalone evidence run)
// Output   : <PROJECT_ROOT>/docs/latest/report/TestDBRemover-Report.html
// Purpose  : The Remover javadoc states it "removes orphaned records on the
//            target table", but Remover.executeBath() executes against
//            this.table (the SOURCE table of the worker). This suite sets up
//            identical rows (one expired soft-deleted, one fresh soft-deleted,
//            one active) on BOTH source and target, starts only the Remover
//            worker, and reports which side loses the expired row.
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

var REPORT_TITLE = "JsPlus-Core DBSync Remover Target Evidence";
var REPORT_NAME = "TestDBRemover-Report.html";

beginTest();

print("=== JsPlus-Core DBSync Remover Evidence ===");
print("JS_PLUS_PATH: " + basePath);

var DatabaseType = Java.type("com.killcoding.dbsync.Database");
var EnterType = Java.type("com.killcoding.dbsync.Enter");
var ConfigPropertiesType = Java.type("com.killcoding.tool.ConfigProperties");
var TimestampType = Java.type("java.sql.Timestamp");

try {

    // Database resolves its DataSource config file against the static
    // Enter.ROOT_PATH - instantiate Enter first so ROOT_PATH is initialised
    // (the constructor auto-calls init(); configure() then just binds config).
    var enter = new EnterType();
    enter.configure(new ConfigPropertiesType());

    // Same runtime convention as TestDBSync: dbsync reads column maps by
    // lower-case keys, so pin the LOWER column-name case mode.
    var CCE = Java.type("com.killcoding.datasource.DriverExecutor");
    CCE.COLUMN_NAME_CASE_MODE = CCE.COLUMN_NAME_CASE_LOWER;

    var cpRem = new ConfigPropertiesType();
    cpRem.setProperty("Source.Database.DataSource", "TestDatasource.properties");
    cpRem.setProperty("Source.Database.Schema", "PUBLIC");
    cpRem.setProperty("Source.Database.Quote.Enable", "false");
    cpRem.setProperty("Target.Database.DataSource", "DBSyncTarget.properties");
    cpRem.setProperty("Target.Database.Schema", "PUBLIC");
    cpRem.setProperty("Target.Database.Quote.Enable", "false");
    cpRem.setProperty("Table.GetTables.List", "dbsync_rem_a");
    cpRem.setProperty("Table.PrimaryKey", "id");
    // Only the Remover worker runs; the others are disabled so the evidence is clean.
    cpRem.setProperty("Table.Inserter.Enable", "false");
    cpRem.setProperty("Table.Updater.Enable", "false");
    cpRem.setProperty("Table.Deleter.Enable", "false");
    cpRem.setProperty("Table.Remover.Enable", "true");
    cpRem.setProperty("Table.Remover.Key", "deleted_at");
    cpRem.setProperty("Table.Remover.Timer", "500ms");
    cpRem.setProperty("Table.Remover.Before", "1000ms");

    var dbS = new DatabaseType("Source", cpRem);
    var dbT = new DatabaseType("Target", cpRem);
    var tblS = dbS.getTable("dbsync_rem_a");
    var tblT = dbT.getTable("dbsync_rem_a");
    tblS.execute("DROP TABLE IF EXISTS dbsync_rem_a", null);
    tblT.execute("DROP TABLE IF EXISTS dbsync_rem_a", null);
    tblS.execute("CREATE TABLE dbsync_rem_a (id INT PRIMARY KEY, name VARCHAR(50), deleted INT, deleted_at TIMESTAMP)", null);
    tblT.execute("CREATE TABLE dbsync_rem_a (id INT PRIMARY KEY, name VARCHAR(50), deleted INT, deleted_at TIMESTAMP)", null);

    var now = new Date().getTime();
    var sql3 = "INSERT INTO dbsync_rem_a (id, name, deleted, deleted_at) VALUES (?, ?, ?, ?)";
    // (1) expired soft-delete (older than Remover.Before relative to the worker's
    // current time), (3) active - same rows on BOTH source and target
    tblS.execute(sql3, [1, "old-del", 1, new TimestampType(now - 10000)]);
    tblS.execute(sql3, [3, "active", 0, new TimestampType(now)]);
    tblT.execute(sql3, [1, "old-del", 1, new TimestampType(now - 10000)]);
    tblT.execute(sql3, [3, "active", 0, new TimestampType(now)]);

    var beforeS = tblS.find("SELECT id, name FROM dbsync_rem_a ORDER BY id", null).size();
    var beforeT = tblT.find("SELECT id, name FROM dbsync_rem_a ORDER BY id", null).size();

    dbS.syncTo(dbT); // starts ONLY the Remover worker
    java.lang.Thread.sleep(3000); // let the worker run several rounds
    dbS.stopSync();
    java.lang.Thread.sleep(500);

    var afterS = tblS.find("SELECT id, name FROM dbsync_rem_a ORDER BY id", null);
    var afterT = tblT.find("SELECT id, name FROM dbsync_rem_a ORDER BY id", null);
    var afterSCount = afterS.size();
    var afterTCount = afterT.size();

    function rowsOf(list) {
        if (list === null || list === undefined) return "(none)";
        var sb = new java.lang.StringBuilder();
        for (var i = 0; i < list.size(); i++) {
            var m = list.get(i);
            sb.append((i > 0 ? "," : "") + m.get("id") + ":" + m.get("name"));
        }
        return sb.toString();
    }

    // Evidence: which side lost the expired row (id=1)?
    var sourceLost = (beforeS === 2 && afterSCount === 1);
    var targetLost = (beforeT === 2 && afterTCount === 1);
    // The javadoc contract says target; the code executes on this.table (source).
    check({
        caseId: "R1",
        name: "Remover evidence - which database loses the expired soft-deleted row",
        ok: true,
        detail: "before: source=" + beforeS + " target=" + beforeT
            + " | after: source=" + afterSCount + " [" + rowsOf(afterS) + "]"
            + " | target=" + afterTCount + " [" + rowsOf(afterT) + "]"
            + " | sourceLost=" + sourceLost + " targetLost=" + targetLost,
        objective: "Report which side the Remover worker actually deletes from (javadoc says target)",
        objectiveZh: "記錄 Remover worker 實際刪除哪一側（javadoc 聲稱是 target）",
        command: "Remover only; identical rows on both sides; observe after several rounds"
    });

} catch (e) {
    check({
        caseId: "R-ERR",
        name: "TestDBRemover suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// Standalone runner (TestDBRemover.bat) sets JSPLUS_TEST_EXIT=1 and expects
// this JVM to exit with the suite outcome - the sync worker threads are
// non-daemon, so the JVM would otherwise hang forever. Under TestAll.js this
// suite must NOT exit (TestAll owns the JVM exit).
var testExit = System.getenv("JSPLUS_TEST_EXIT");
if (testExit !== null && testExit !== undefined && ("" + testExit) === "1"
        && (typeof IN_TEST_ALL === "undefined" || IN_TEST_ALL === null)) {
    System.exit(failCount > 0 ? 1 : 0);
}
