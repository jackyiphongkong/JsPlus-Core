// ============================================================================
// JsPlus-Core TestSqlLog - sql log writer test suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestSqlLog.bat / test/TestSqlLog.sh (or inside the console:
//              Console.eval(<PROJECT_ROOT>/test/TestSqlLog.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/docs/latest/report/TestSqlLog-Report.html / .md
// Coverage : com.killcoding.datasource.DriverExecutor.writeSqlLog and the
//            flush -> rotate path behind it (CacheArray filter -> executeBatch
//            -> getLogFileIndex -> backupLog -> archiveLog):
//            - SqlLogEnable=false writes nothing                          [C1]
//            - SqlLogEnable=true writes <yyyyMMdd>.csv with the header
//              row plus the logged SQL text                              [C2]
//            - SqlLogIncludePid=true switches to <yyyyMMdd>.<pid>.csv     [C3]
//            - SqlLogMaxFileSize trip rotates to <yyyyMMdd>.1.csv and
//              rebuilds the active file with a fresh header              [C4]
//            - backupLog when the active file is already gone (another
//              process rotated it first) must not throw and must rebuild
//              the active file with this batch                          [C5]
//            - SqlLogFolder whose parent directories do not exist is
//              created on the write path                                [C6]
//            - 8 threads writing concurrently: every thread marker lands
//              in the file, no write fails                              [C7]
// NOTE     : Run with -DAPP_DATASOURCE=<config/TestDatasource.properties>
//            (test/TestSqlLog.bat/.sh pass it) so the runtime
//            Console.getDataSource() loads the dedicated test datasource.
//            Every case uses its OWN SqlLogFolder: the runtime caches one
//            CacheArray per normalized folder path and captures the rotation
//            settings (max size / pid flag / timer) when that CacheArray is
//            created, so a shared folder would keep the first case's values.
//            Log lines reach the disk asynchronously (CacheArrayFilter,
//            SqlLogTimer), so each case sleeps before asserting on the file.
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

var REPORT_NAME = "TestSqlLog-Report.html";
var REPORT_TITLE = "JsPlus-Core SqlLog Test Report";

var Logger = Java.type("com.killcoding.log.Logger");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Thread = Java.type("java.lang.Thread");
var SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
var JavaDate = Java.type("java.util.Date");
var StandardCharsets = Java.type("java.nio.charset.StandardCharsets");
var StringClass = Java.type("java.lang.String").class;
var FileClass = Java.type("java.io.File").class;
var IntType = Java.type("java.lang.Integer").TYPE;
var CacheDriverExecutor = Java.type("com.killcoding.datasource.CacheDriverExecutor");
var DriverExecutor = Java.type("com.killcoding.datasource.DriverExecutor");
var ProcessHandle = Java.type("java.lang.ProcessHandle");

print("=== JsPlus-Core SqlLog Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));

// ----------------------------------------------------------------------------
// Shared helpers.
// ----------------------------------------------------------------------------

// The runtime datasource built from -DAPP_DATASOURCE (config/TestDatasource.properties).
var ds = Console.getDataSource();
var cfg = ds.getConfigProperties();
var today = "" + new SimpleDateFormat("yyyyMMdd").format(new JavaDate());
var pidText = "0";
try {
    pidText = "" + ProcessHandle.current().pid();
} catch (e) {
    pidText = "0";
}

// Root of the throw-away log folders (never touches logs/SqlLog).
var workRoot = new File(System.getProperty("java.io.tmpdir"), "jsp-sqllog-test");

// Recursive delete helper (no regex literals: this suite must also run on the
// GraalVM build without TRegex).
function rmrf(f) {
    if (f === null || f === undefined || !f.exists()) {
        return;
    }
    if (f.isDirectory()) {
        var kids = f.listFiles();
        if (kids !== null) {
            for (var i = 0; i < kids.length; i++) {
                rmrf(kids[i]);
            }
        }
    }
    f.delete();
}

// One unique tag per JVM run: a leftover folder from an earlier run (a file
// still held by the OS for a moment) must never leak into this run's cases.
var runTag = "" + System.nanoTime();

// Fresh (empty, run-unique) folder for one case.
function freshDir(name) {
    var d = new File(workRoot, name + "-" + runTag);
    rmrf(d);
    d.mkdirs();
    return d;
}

// Configure one SqlLog key on the live datasource.
function conf(key, value) {
    cfg.putAndReturnSelf(key, value);
}

// Read a UTF-8 text file, empty string on any failure.
function readText(f) {
    try {
        return "" + new java.lang.String(Files.readAllBytes(Paths.get(f.getAbsolutePath())),
                StandardCharsets.UTF_8);
    } catch (e) {
        return "";
    }
}

// *.csv file names inside a folder (string compare, no regex).
function csvNames(dir) {
    var out = [];
    if (dir === null || !dir.exists()) {
        return out;
    }
    var kids = dir.listFiles();
    if (kids === null) {
        return out;
    }
    for (var i = 0; i < kids.length; i++) {
        var n = "" + kids[i].getName();
        if (n.lastIndexOf(".csv") === (n.length - 4)) {
            out.push(n);
        }
    }
    return out;
}

// Run n SELECTs through CacheDriverExecutor so each one reaches writeSqlLog.
function runSql(n, tag) {
    var conn = ds.getConnection();
    try {
        var cde = new CacheDriverExecutor(conn);
        for (var i = 0; i < n; i++) {
            cde.find("SELECT '" + tag + "-" + i + "' AS k");
        }
    } finally {
        try { conn.close(); } catch (ce) { }
    }
}

// Write-path settings reused by the cases that only vary the folder.
function baseLogConf(dir) {
    conf("SqlLogEnable", true);
    conf("SqlLogFolder", dir.getAbsolutePath());
    conf("SqlLogMaxFileSize", "2mb");
    conf("SqlLogTimer", "200ms");
    conf("SqlLogIncludePid", false);
    conf("SqlLogAllowed", "");
    conf("SqlLogIgnored", "");
}

// Case C1
// SqlLogEnable=false must produce no file at all, even after real queries.
var c1Ok = false;
var c1Detail = "";
try {
    var dir1 = freshDir("c1");
    conf("SqlLogEnable", false);
    conf("SqlLogFolder", dir1.getAbsolutePath());
    conf("SqlLogTimer", "200ms");
    runSql(3, "C1");
    Thread.sleep(1200);
    var names1 = csvNames(dir1);
    c1Ok = names1.length === 0;
    c1Detail = "csvFiles=" + names1.length;
} catch (e) {
    c1Detail = "" + e;
}
Logger.systemMark("TestSqlLog.js", "C1");
check({
    name: "SqlLogEnable=false writes no sql log file",
    caseId: "C1",
    ok: c1Ok,
    detail: c1Detail,
    objective: "With SqlLogEnable=false, executing queries must not create any csv file in the configured SqlLogFolder",
    objectiveZh: "SqlLogEnable=false 時執行查詢，配置的 SqlLogFolder 內不得產生任何 csv 檔案",
    command: "SqlLogEnable=false; cde.find(SELECT); sleep; dir.listFiles()"
});

// Case C2
// SqlLogEnable=true writes <yyyyMMdd>.csv containing the header plus the SQL.
var c2Ok = false;
var c2Detail = "";
try {
    var dir2 = freshDir("c2");
    baseLogConf(dir2);
    runSql(3, "C2");
    Thread.sleep(1500);
    var f2 = new File(dir2, today + ".csv");
    var txt2 = readText(f2);
    var hasHeader2 = txt2.indexOf("LOG_DATE") >= 0 && txt2.indexOf("LOG_SQL") >= 0;
    var hasSql2 = txt2.indexOf("C2-0") >= 0;
    c2Ok = f2.exists() && hasHeader2 && hasSql2;
    c2Detail = "exists=" + f2.exists() + " bytes=" + (f2.exists() ? f2.length() : 0)
            + " header=" + hasHeader2 + " sql=" + hasSql2;
} catch (e) {
    c2Detail = "" + e;
}
Logger.systemMark("TestSqlLog.js", "C2");
check({
    name: "SqlLogEnable=true writes <yyyyMMdd>.csv with header and SQL",
    caseId: "C2",
    ok: c2Ok,
    detail: c2Detail,
    objective: "With the log enabled the active file is named <yyyyMMdd>.csv and contains the LOG_DATE..LOG_PARAMS header row plus the executed SQL text",
    objectiveZh: "開啟日誌後，主檔名為 <yyyyMMdd>.csv，內容含 LOG_DATE..LOG_PARAMS 表頭列與實際執行的 SQL 文字",
    command: "SqlLogEnable=true; cde.find(SELECT 'C2-0'); sleep; read <yyyyMMdd>.csv"
});

// Case C3
// SqlLogIncludePid=true switches the file name to <yyyyMMdd>.<pid>.csv.
var c3Ok = false;
var c3Detail = "";
try {
    var dir3 = freshDir("c3");
    baseLogConf(dir3);
    conf("SqlLogIncludePid", true);
    runSql(2, "C3");
    Thread.sleep(1500);
    var f3 = new File(dir3, today + "." + pidText + ".csv");
    var txt3 = readText(f3);
    var names3 = csvNames(dir3);
    var hasPlain3 = false;
    for (var i3 = 0; i3 < names3.length; i3++) {
        if (names3[i3] === (today + ".csv")) {
            hasPlain3 = true;
        }
    }
    c3Ok = f3.exists() && txt3.indexOf("LOG_DATE") >= 0 && txt3.indexOf("C3-0") >= 0 && !hasPlain3;
    c3Detail = "pid=" + pidText + " files=[" + names3.join(",") + "] bytes="
            + (f3.exists() ? f3.length() : 0) + " plain=" + hasPlain3;
} catch (e) {
    c3Detail = "" + e;
}
Logger.systemMark("TestSqlLog.js", "C3");
check({
    name: "SqlLogIncludePid=true writes <yyyyMMdd>.<pid>.csv",
    caseId: "C3",
    ok: c3Ok,
    detail: c3Detail,
    objective: "With SqlLogIncludePid=true the active file carries the JVM pid (<yyyyMMdd>.<pid>.csv) and no plain <yyyyMMdd>.csv is created, so parallel processes no longer share one file",
    objectiveZh: "SqlLogIncludePid=true 時主檔名帶 JVM pid（<yyyyMMdd>.<pid>.csv），且不產生不帶 pid 的 <yyyyMMdd>.csv，並行進程不再共用同一檔案",
    command: "SqlLogIncludePid=true; cde.find(SELECT 'C3-0'); sleep; dir.listFiles()"
});

// Case C4
// A small SqlLogMaxFileSize must rotate to <yyyyMMdd>.1.csv and rebuild the
// active file with a fresh header (the second batch is what trips the limit).
var c4Ok = false;
var c4Detail = "";
try {
    var dir4 = freshDir("c4");
    baseLogConf(dir4);
    conf("SqlLogMaxFileSize", "1kb");
    conf("SqlLogTimer", "100ms");
    runSql(20, "C4A");
    Thread.sleep(1200);
    runSql(20, "C4B");
    Thread.sleep(1800);
    var names4 = csvNames(dir4);
    var bak4 = new File(dir4, today + ".1.csv");
    var act4 = new File(dir4, today + ".csv");
    var txt4 = readText(act4);
    var txtBak4 = readText(bak4);
    var bakHas4 = txtBak4.indexOf("C4A") >= 0 || txtBak4.indexOf("C4B") >= 0;
    c4Ok = bak4.exists() && bakHas4 && act4.exists() && (txt4.indexOf("LOG_DATE") >= 0);
    c4Detail = "files=[" + names4.join(",") + "] backupBytes="
            + (bak4.exists() ? bak4.length() : 0) + " activeBytes="
            + (act4.exists() ? act4.length() : 0) + " activeHeader="
            + (txt4.indexOf("LOG_DATE") >= 0);
} catch (e) {
    c4Detail = "" + e;
}
Logger.systemMark("TestSqlLog.js", "C4");
check({
    name: "SqlLogMaxFileSize trip rotates to <yyyyMMdd>.1.csv and rebuilds the active file",
    caseId: "C4",
    ok: c4Ok,
    detail: c4Detail,
    objective: "Once the active file exceeds SqlLogMaxFileSize the writer moves it to <yyyyMMdd>.1.csv and recreates <yyyyMMdd>.csv with the header row, so logging continues instead of truncating the previous content",
    objectiveZh: "主檔超過 SqlLogMaxFileSize 後，寫入端將其移至 <yyyyMMdd>.1.csv 並重建含表頭的 <yyyyMMdd>.csv，日誌得以續寫而非覆蓋舊內容",
    command: "SqlLogMaxFileSize=1kb; write 20 + 20 rows; expect <yyyyMMdd>.1.csv + fresh <yyyyMMdd>.csv"
});

// Case C5
// Cross-process rotation race: the active file is already gone when backupLog
// runs (another process moved it first). The call must not throw and must
// rebuild the active file carrying this batch - the pre-fix code logged an
// ERROR and dropped the batch.
var c5Ok = false;
var c5Detail = "";
try {
    var dir5 = freshDir("c5");
    var act5 = new File(dir5, today + ".csv");
    var method5 = DriverExecutor.class.getDeclaredMethod("backupLog",
            FileClass, FileClass, StringClass, IntType, StringClass, StringClass);
    method5.setAccessible(true);
    method5.invoke(null, dir5, act5, today, 1, "HDR-LINE\n", "ROW-LINE\n");
    var txt5 = readText(act5);
    var bak5 = new File(dir5, today + ".1.csv");
    c5Ok = act5.exists() && txt5.indexOf("HDR-LINE") >= 0 && txt5.indexOf("ROW-LINE") >= 0
            && !bak5.exists();
    c5Detail = "exists=" + act5.exists() + " bytes=" + (act5.exists() ? act5.length() : 0)
            + " header=" + (txt5.indexOf("HDR-LINE") >= 0)
            + " row=" + (txt5.indexOf("ROW-LINE") >= 0)
            + " backupCreated=" + bak5.exists();
} catch (e) {
    c5Detail = "" + e;
}
Logger.systemMark("TestSqlLog.js", "C5");
check({
    name: "backupLog tolerates a missing active file (other process rotated first)",
    caseId: "C5",
    ok: c5Ok,
    detail: c5Detail,
    objective: "Calling backupLog when the active file no longer exists (another process already moved it) throws nothing and rewrites the active file with header + batch, instead of failing with NoSuchFileException and losing the batch",
    objectiveZh: "active 檔已不存在（別的進程先輪轉）時呼叫 backupLog：不得拋出 NoSuchFileException，且以表頭＋本批內容重建主檔，避免遺失該批日誌",
    command: "reflect DriverExecutor.backupLog(dir, <yyyyMMdd>.csv(missing), prefix, 1, header, msg)"
});

// Case C6
// A SqlLogFolder whose parent directories are missing must be created by the
// write path (Files.createDirectories) instead of failing the write.
var c6Ok = false;
var c6Detail = "";
try {
    var dir6 = new File(new File(workRoot, "c6-" + runTag), "deep");
    rmrf(new File(workRoot, "c6-" + runTag));
    var dir6b = new File(dir6, "level2");
    baseLogConf(dir6b);
    runSql(2, "C6");
    Thread.sleep(1500);
    var f6 = new File(dir6b, today + ".csv");
    var txt6 = readText(f6);
    c6Ok = dir6b.exists() && f6.exists() && txt6.indexOf("LOG_DATE") >= 0;
    c6Detail = "folderExists=" + dir6b.exists() + " fileExists=" + f6.exists()
            + " bytes=" + (f6.exists() ? f6.length() : 0);
} catch (e) {
    c6Detail = "" + e;
}
Logger.systemMark("TestSqlLog.js", "C6");
check({
    name: "SqlLogFolder with missing parents is created on the write path",
    caseId: "C6",
    ok: c6Ok,
    detail: c6Detail,
    objective: "Pointing SqlLogFolder at a two-level path that does not exist yet, the writer creates the directories and lands the active file there",
    objectiveZh: "將 SqlLogFolder 指向尚不存在的兩層路徑，寫入端自動建立目錄並將主檔落地其中",
    command: "SqlLogFolder=<tmp>/c6/deep/level2 (missing); cde.find(SELECT); sleep; check file"
});

// Case C7
// Concurrent writers inside one JVM: 8 threads x 10 rows. writeSqlLog is
// synchronized and CacheArray is thread-safe, so every marker must land and
// no write may fail. (Cross-process sharing is covered by C3/C5.)
var SRC_SQL_LOG_STRESS = `
import com.killcoding.datasource.CacheDriverExecutor;
import com.killcoding.datasource.DriverDataSource;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class SqlLogStress {
    public String stress(final DriverDataSource ds, final int threads, final int rounds, final String tag)
            throws Exception {
        final CountDownLatch gate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger err = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            Thread th = new Thread(new Runnable() {
                public void run() {
                    try {
                        gate.await();
                    } catch (InterruptedException ie) {
                        // start gate closed early - proceed anyway
                    }
                    try {
                        Connection conn = ds.getConnection();
                        try {
                            CacheDriverExecutor cde = new CacheDriverExecutor(conn);
                            for (int r = 0; r < rounds; r++) {
                                cde.find("SELECT '" + tag + "-T" + id + "-" + r + "' AS k");
                                ok.incrementAndGet();
                            }
                        } finally {
                            try { conn.close(); } catch (Exception ce) { }
                        }
                    } catch (Exception e) {
                        err.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }
            });
            th.setDaemon(true);
            th.start();
        }
        gate.countDown();
        done.await();
        return "ok=" + ok.get() + " err=" + err.get();
    }
}
`;

var c7Ok = false;
var c7Detail = "";
try {
    var dir7 = freshDir("c7");
    baseLogConf(dir7);
    conf("SqlLogTimer", "100ms");
    var loader7 = Console.evalJavaSource(SRC_SQL_LOG_STRESS);
    var StressType7 = loader7.loadClass("SqlLogStress");
    var inst7 = new StressType7();
    var res7 = "" + inst7.stress(ds, 8, 10, "C7");
    Thread.sleep(2500);
    var f7 = new File(dir7, today + ".csv");
    var txt7 = readText(f7);
    var markers7 = 0;
    for (var t7 = 0; t7 < 8; t7++) {
        if (txt7.indexOf("C7-T" + t7 + "-") >= 0) {
            markers7++;
        }
    }
    var okNum7 = parseInt(res7.split(" ")[0].split("=")[1], 10);
    var errNum7 = parseInt(res7.split(" ")[1].split("=")[1], 10);
    c7Ok = f7.exists() && markers7 === 8 && okNum7 === 80 && errNum7 === 0;
    c7Detail = res7 + " markers=" + markers7 + "/8 bytes=" + (f7.exists() ? f7.length() : 0);
} catch (e) {
    c7Detail = "" + e;
}
Logger.systemMark("TestSqlLog.js", "C7");
check({
    name: "8 threads concurrent writes land every marker without failure",
    caseId: "C7",
    ok: c7Ok,
    detail: c7Detail,
    objective: "Eight threads logging 10 queries each through one datasource: all 80 writes succeed (ok=80, err=0) and every thread's SQL marker appears in the active file",
    objectiveZh: "八條線程各經同一資料源寫 10 條查詢：80 次寫入全成功（ok=80、err=0），且每條線程的 SQL 標記都能在主檔中找到",
    command: "evalJavaSource(SqlLogStress).stress(ds, 8, 10, 'C7'); sleep; read <yyyyMMdd>.csv"
});

// Restore the log switch so the following suites behave as before.
try {
    conf("SqlLogEnable", false);
} catch (e2) { }

writeReport();
writeMd();

// Standalone runners (TestSqlLog.bat/.sh) set JSPLUS_TEST_EXIT=1 and expect
// this JVM to exit with the suite outcome. Under TestAll.js (IN_TEST_ALL
// defined) this suite never exits: TestAll owns the JVM exit after the run.
var testExit = System.getenv("JSPLUS_TEST_EXIT");
if (testExit !== null && testExit !== undefined && ("" + testExit) === "1"
        && (typeof IN_TEST_ALL === "undefined" || IN_TEST_ALL === null)) {
    System.exit(failCount > 0 ? 1 : 0);
}
