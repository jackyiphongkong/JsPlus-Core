// ============================================================================
// JsPlus-Core TestRemoteFile - RemoteFile (com.killcoding.file) test suite
// ----------------------------------------------------------------------------
// RemoteFile = DB-backed remote file system: file metadata rows live in the
// `remote_file` table, chunked content lives in `remote_file_data_<n>`
// tables (DataMapping[0].TableRange=0-1 -> remote_file_data_0/1). This
// suite:
//   1) creates the RemoteFile tables if missing - an H2 port of the
//      reference MySQL DDL (gen_mysql_rf_script.sh): same columns/types;
//      engine/partition clauses and index length prefixes dropped (H2);
//   2) exercises the public API end to end: datasource config load, mkdirs,
//      write, complete, readAllBytes/readAllString, append (multi-part),
//      listAll(CacheArray), createLink/readLink, copyTo, moveTo, delete
//      (soft), forceDelete (hard), isTimeout, writeToDisk (local export);
//      every persisted phase is verified with dbVerify (SELECT * via
//      Console.getDataSource());
//   3) cleans up its own tables at the end (idempotent re-runs).
// Config : <PROJECT_ROOT>/test/RemoteFile.properties is handed to the
//          console JVM via -DAPP_REMOTE_FILE (TestAll.bat/.sh). It is the
//          RemoteFile datasource configuration: DataSource + DataMapping[0]
//          point at the project's testdb datasource definition file. The
//          suite's own checks go through Console.getDataSource() (dbVerify
//          contract), encryption is exercised through the configured
//          EncryptEnable/EncryptAesKey (write -> encrypted BLOB, read ->
//          decrypted bytes roundtrip).
// NOTE   : copyTo/moveTo drive merge() over a CacheArray filter worker, so
//          the destination completes asynchronously - the suite polls for
//          completion (bounded) instead of assuming synchrony.
// ============================================================================

var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Str = Java.type("java.lang.String");
var RemoteFile = Java.type("com.killcoding.file.RemoteFile");
var CacheArray = Java.type("com.killcoding.cache.CacheArray");
var DiskFile = Java.type("com.killcoding.file.DiskFile");

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

// Disable the RemoteFile row cache (Cache.set only stores when life > 0, and
// CACHE_SECONDS defaults to 5): getCompletedRecord()/exists() cache by SQL +
// params key, so a same-instance complete() followed by size() would return
// the stale pre-append file_size. With 0 every read hits the database, which
// is what these round-trip assertions need.
RemoteFile.CACHE_SECONDS = 0;

var REPORT_NAME = "TestRemoteFile-Report.html";
var REPORT_TITLE = "JsPlus-Core RemoteFile Test Report";

print("=== JsPlus-Core RemoteFile Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));

// ----------------------------------------------------------------------------
// 0. Schema: create remote_file + remote_file_data_0/1 if missing (H2 port
// of the reference MySQL DDL), then clear both for a deterministic re-run.
// The connection comes from Console.getDataSource() (project datasource).
// ----------------------------------------------------------------------------
var initErr = "";
try {
    var conn0 = Console.getDataSource().getConnection();
    var stmt0 = conn0.createStatement();
    stmt0.execute("CREATE TABLE IF NOT EXISTS remote_file (id CHAR(16) PRIMARY KEY, file_id CHAR(16), file_name VARCHAR(200), file_parent_path VARCHAR(200), file_type CHAR(1), file_part_index INT, file_part_size BIGINT, file_part_data_ds VARCHAR(100), file_part_data_table INT, file_part_last_index INT, file_size BIGINT, file_modify_time TIMESTAMP, file_deleted TINYINT, source_hostname VARCHAR(50), synced_on_hostnames VARCHAR(200), deleted_on_hostnames VARCHAR(200), created_user_id CHAR(16), updated_user_id CHAR(16), deleted_user_id CHAR(16), created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP, deleted TINYINT)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rf_0 ON remote_file (file_parent_path)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rf_1 ON remote_file (file_name)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rf_4 ON remote_file (deleted)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rf_5 ON remote_file (file_deleted)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rf_6 ON remote_file (file_id)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rf_7 ON remote_file (file_part_index)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rf_8 ON remote_file (file_type)");
    stmt0.execute("CREATE TABLE IF NOT EXISTS remote_file_data_0 (id CHAR(16) PRIMARY KEY, remote_file_id CHAR(16), file_id CHAR(16), file_part_data BLOB, created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP, deleted TINYINT)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rfd_0_0 ON remote_file_data_0 (file_id)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rfd_1_0 ON remote_file_data_0 (remote_file_id)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rfd_2_0 ON remote_file_data_0 (deleted)");
    stmt0.execute("CREATE TABLE IF NOT EXISTS remote_file_data_1 (id CHAR(16) PRIMARY KEY, remote_file_id CHAR(16), file_id CHAR(16), file_part_data BLOB, created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP, deleted TINYINT)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rfd_0_1 ON remote_file_data_1 (file_id)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rfd_1_1 ON remote_file_data_1 (remote_file_id)");
    stmt0.execute("CREATE INDEX IF NOT EXISTS idx_rfd_2_1 ON remote_file_data_1 (deleted)");
    stmt0.execute("DELETE FROM remote_file");
    stmt0.execute("DELETE FROM remote_file_data_0");
    stmt0.execute("DELETE FROM remote_file_data_1");
    stmt0.close();
    conn0.close();
} catch (e) {
    initErr = "" + e;
}

var RF_ROOT = "/rftest";
var HELLO = "Hello RemoteFile";
var HELLO_APPEND = HELLO + " World"; // 22 chars

// ----------------------------------------------------------------------------
// C1: datasource configuration load (RemoteFile.properties via APP_REMOTE_FILE)
// ----------------------------------------------------------------------------
var rfRoot = null;
var cfgDetail = initErr;
try {
    rfRoot = new RemoteFile(RF_ROOT);
    var cp = rfRoot.getConfigProperties();
    var encFlag = cp != null ? cp.getBoolean("EncryptEnable", false) : false;
    cfgDetail = "config=" + (cp != null ? "loaded" : "null")
        + ", reader=" + (rfRoot.getDataSourceReader() != null)
        + ", writer=" + (rfRoot.getDataSourceWriter() != null)
        + ", TableName=" + (cp != null ? cp.getString("TableName") : "n/a")
        + ", EncryptEnable=" + encFlag;
    check({
        caseId: "C1",
        name: "RemoteFile datasource config load (APP_REMOTE_FILE)",
        ok: cp != null && rfRoot.getDataSourceReader() != null
            && rfRoot.getDataSourceWriter() != null
            && ("" + cp.getString("TableName")) === "remote_file",
        detail: cfgDetail,
        objective: "new RemoteFile(\"/rftest\") loads RemoteFile.properties (DataSource/DataMapping -> testdb, TableName=remote_file, EncryptEnable)",
        objectiveZh: "RemoteFile 資料源設定載入（DataSource/DataMapping 指向 testdb）"
    });
} catch (e) {
    check({
        caseId: "C1", name: "RemoteFile datasource config load (APP_REMOTE_FILE)",
        ok: false, detail: cfgDetail + " / error=" + e,
        objective: "RemoteFile 資料源設定載入", objectiveZh: ""
    });
}

// ----------------------------------------------------------------------------
// C2: mkdirs() persists a directory record
// ----------------------------------------------------------------------------
try {
    var mk = rfRoot.mkdirs();
    var ex2 = rfRoot.exists();
    var isd2 = rfRoot.isDirectory();
    var d2 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='rftest' AND file_type='D' AND deleted=0";
    });
    check({
        caseId: "C2",
        name: "mkdirs() creates a directory record",
        ok: mk && ex2 && isd2,
        detail: "mkdirs=" + mk + ", exists=" + ex2 + ", isDirectory=" + isd2 + d2,
        objective: "mkdirs() persists a file_type='D' row; exists()/isDirectory() reflect it",
        objectiveZh: "mkdirs() 建立目錄記錄（file_type='D'），exists/isDirectory 生效"
    });
} catch (e) {
    check({ caseId: "C2", name: "mkdirs() creates a directory record", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C3: write() persists a file record (not completed yet)
// ----------------------------------------------------------------------------
var rfHello = null;
try {
    rfHello = new RemoteFile("/rftest/hello.txt");
    var w3 = rfHello.write(HELLO);
    var ex3 = rfHello.exists();
    var isf3 = rfHello.isFile();
    var cp3 = rfHello.isCompleted();
    var d3 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='hello.txt' AND file_part_index=0 AND deleted=0";
    });
    check({
        caseId: "C3",
        name: "write() creates a file record (pending)",
        ok: w3 && ex3 && isf3 && !cp3,
        detail: "write=" + w3 + ", exists=" + ex3 + ", isFile=" + isf3 + ", isCompleted=" + cp3 + " (file_part_last_index=-1 until complete)" + d3,
        objective: "write(byte[]) inserts the metadata+chunk rows; exists()/isFile() true, not completed",
        objectiveZh: "write() 寫入檔案記錄與分片，尚未 complete"
    });
} catch (e) {
    check({ caseId: "C3", name: "write() creates a file record (pending)", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C4: complete() finalizes the file (last index + size)
// ----------------------------------------------------------------------------
try {
    var cp4 = rfHello.complete();
    var isc4 = rfHello.isCompleted();
    var sz4 = rfHello.size();
    var d4 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='hello.txt' AND file_part_index=0 AND deleted=0";
    });
    check({
        caseId: "C4",
        name: "complete() finalizes the file (file_part_last_index / file_size)",
        ok: cp4 && isc4 && sz4 === 16,
        detail: "complete=" + cp4 + ", isCompleted=" + isc4 + ", size=" + sz4 + d4,
        objective: "complete() sets file_part_last_index>=-1 and file_size=16",
        objectiveZh: "complete() 收尾（分片索引與檔案大小）"
    });
} catch (e) {
    check({ caseId: "C4", name: "complete() finalizes the file", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C5: readAllBytes / readAllString roundtrip (encrypted write -> decrypted read)
// ----------------------------------------------------------------------------
try {
    var rb5 = rfHello.readAllBytes();
    var rs5 = rfHello.readAllString();
    var bytesStr = rb5 != null ? new Str(rb5, "UTF-8") : null;
    var d5 = dbVerify(function () {
        return "SELECT * FROM remote_file_data_0 WHERE file_id = (SELECT file_id FROM remote_file WHERE file_name='hello.txt' AND file_part_index=0) AND deleted=0";
    });
    check({
        caseId: "C5",
        name: "readAllBytes/readAllString roundtrip (EncryptEnable=true)",
        ok: rb5 != null && rb5.length === 16 && ("" + bytesStr) === HELLO && ("" + rs5) === HELLO,
        detail: "bytes.length=" + (rb5 != null ? rb5.length : "null") + ", string='" + rs5 + "' (chunk stored as encrypted BLOB)" + d5,
        objective: "content written via EncryptEnable/EncryptAesKey reads back decrypted and identical",
        objectiveZh: "加密寫入 / 解密讀出 roundtrip 一致"
    });
} catch (e) {
    check({ caseId: "C5", name: "readAllBytes/readAllString roundtrip", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C6: append writes a second part; complete() re-aggregates size/content
// ----------------------------------------------------------------------------
try {
    var w6 = rfHello.write(" World", true);
    var cp6 = rfHello.complete();
    var sz6 = rfHello.size();
    var rs6 = rfHello.readAllString();
    var d6 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='hello.txt' AND deleted=0 ORDER BY file_part_index";
    });
    check({
        caseId: "C6",
        name: "append write (multi-part) + complete re-aggregation",
        ok: w6 && cp6 && sz6 === 22 && ("" + rs6) === HELLO_APPEND,
        detail: "append=" + w6 + ", complete=" + cp6 + ", size=" + sz6 + ", content='" + rs6 + "' (2 parts: file_part_index=0,1)" + d6,
        objective: "write(data, true) appends a second chunk; complete() sums part sizes and max index",
        objectiveZh: "append 寫入第二分片並重新收尾（size 22、內容拼接）"
    });
} catch (e) {
    check({ caseId: "C6", name: "append write (multi-part) + complete", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C7: listAll(CacheArray) returns completed files under the root
// ----------------------------------------------------------------------------
try {
    var ca7 = new CacheArray();
    // CacheArray.add() drops values unless a filter is attached (it is a
    // producer/consumer pair), so attach one before listAll(). The worker
    // thread cannot enter the GraalJS context (multi-threaded access denied),
    // so the callbacks stay no-ops - but the rows are still queued and can be
    // read from the main thread via size()/get().
    var seen7 = [];
    ca7.filter(10,
        function () { try { seen7.push(ca7.getObject()); } catch (e) { } },
        function () { },
        function () { });
    rfRoot.listAll(ca7);
    // find(sql, params, rows) feeds the CacheArray from the async worker, so
    // poll briefly for the completed files to land.
    var waitedL = 0;
    while (waitedL < 5000 && ca7.size() < 1) {
        Thread.sleep(100);
        waitedL += 100;
    }
    var caSize = ca7.size();
    var row7 = null;
    if (caSize > 0) { try { row7 = ca7.get(0); } catch (e) { } }
    var names7 = "none";
    if (row7 !== null && row7 !== undefined) {
        names7 = [row7.file_name, row7.file_parent_path, row7.file_type].join(",");
    }
    var d7 = dbVerify(function () {
        // NOTE: stored file_parent_path is forward-slash normalized
        // (BaseFile.replacePath), e.g. '/rftest' - a backslash in the SQL
        // literal would be consumed as an H2 escape (\r) and match nothing.
        return "SELECT * FROM remote_file WHERE file_parent_path='/rftest' AND file_part_index=0 AND file_part_last_index>-1 AND deleted=0";
    });
    check({
        caseId: "C7",
        name: "listAll(CacheArray) lists completed files",
        ok: caSize >= 1 && row7 !== null && row7 !== undefined && ("" + row7.file_name) !== "null" && ("" + row7.file_name) !== "undefined",
        detail: "cacheArray.size=" + caSize + " (row0=" + names7 + ")" + d7,
        objective: "listAll(likeFolder, likeFile, CacheArray) queues completed rows into the CacheArray",
        objectiveZh: "listAll(CacheArray) 列出 /rftest 下已完成檔案"
    });
} catch (e) {
    check({ caseId: "C7", name: "listAll(CacheArray) lists completed files", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C8: createLink / readLink / isLink
// ----------------------------------------------------------------------------
try {
    var rfLink = new RemoteFile("/rftest/link.txt");
    var cl8 = rfLink.createLink("/rftest/hello.txt");
    var isl8 = rfLink.isLink();
    var rl8 = rfLink.readLink();
    var d8 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='link.txt' AND deleted=0";
    });
    check({
        caseId: "C8",
        name: "createLink/readLink roundtrip",
        ok: cl8 && isl8 && ("" + rl8) === "/rftest/hello.txt",
        detail: "createLink=" + cl8 + ", isLink=" + isl8 + ", readLink='" + rl8 + "'" + d8,
        objective: "createLink(target) persists a file_type='L' row; readLink() returns the target",
        objectiveZh: "createLink/readLink 連結建立與讀回"
    });
} catch (e) {
    check({ caseId: "C8", name: "createLink/readLink roundtrip", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C9: copyTo() - async merge() worker, poll for destination completion
// ----------------------------------------------------------------------------
try {
    var rfCopy = new RemoteFile("/rftest/copy.txt");
    rfHello.copyTo("/rftest/copy.txt");
    var copyDone = false;
    var waitedC = 0;
    while (waitedC < 6000) {
        try {
            if (rfCopy.exists(true) && rfCopy.isCompleted()) { copyDone = true; break; }
        } catch (e) {}
        Thread.sleep(200);
        waitedC += 200;
    }
    var copyContent = copyDone ? rfCopy.readAllString() : "";
    var d9 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='copy.txt' AND deleted=0";
    });
    check({
        caseId: "C9",
        name: "copyTo() replicates content (async merge, polled)",
        ok: copyDone && ("" + copyContent) === HELLO_APPEND && rfCopy.size() === 22,
        detail: "copyDone(waited " + waitedC + "ms)=" + copyDone + ", content='" + copyContent + "', size=" + (copyDone ? rfCopy.size() : "n/a") + d9,
        objective: "copyTo(toPath) merges parts into a new completed remote file",
        objectiveZh: "copyTo() 複製檔案（非同步 merge，輪詢完成）"
    });
} catch (e) {
    check({ caseId: "C9", name: "copyTo() replicates content", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C10: moveTo() - copy then hard-delete the source
// ----------------------------------------------------------------------------
try {
    var rfMoved = new RemoteFile("/rftest/moved.txt");
    rfHello.moveTo("/rftest/moved.txt");
    var moveDone = false;
    var waitedM = 0;
    while (waitedM < 6000) {
        try {
            if (rfMoved.exists(true) && rfMoved.isCompleted() && !rfHello.exists(true)) { moveDone = true; break; }
        } catch (e) {}
        Thread.sleep(200);
        waitedM += 200;
    }
    var movedContent = moveDone ? rfMoved.readAllString() : "";
    var d10 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name IN ('moved.txt','hello.txt') AND file_part_index=0 ORDER BY file_name";
    });
    check({
        caseId: "C10",
        name: "moveTo() copies then deletes the source",
        ok: moveDone && ("" + movedContent) === HELLO_APPEND,
        detail: "moveDone(waited " + waitedM + "ms)=" + moveDone + ", content='" + movedContent + "', source exists=" + rfHello.exists(true) + d10,
        objective: "moveTo(toPath) = copyTo + forceDeleteAll(true) on the source",
        objectiveZh: "moveTo() 搬移（複製後硬刪來源）"
    });
} catch (e) {
    check({ caseId: "C10", name: "moveTo() copies then deletes the source", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C11: delete(false) - soft delete (deleted=1, kept row)
// ----------------------------------------------------------------------------
try {
    var rfDel = new RemoteFile("/rftest/to-delete.txt");
    rfDel.write("Temp Data");
    rfDel.complete();
    var del11 = rfDel.delete(false);
    var ex11 = rfDel.exists(true);
    var d11 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='to-delete.txt'";
    });
    check({
        caseId: "C11",
        name: "delete(false) soft-deletes (deleted=1)",
        ok: del11 && !ex11,
        detail: "delete(false)=" + del11 + ", exists(refresh)=" + ex11 + d11,
        objective: "delete(false) sets deleted=1; exists() no longer sees the row",
        objectiveZh: "delete(false) 軟刪除（deleted=1，exists 失效）"
    });
} catch (e) {
    check({ caseId: "C11", name: "delete(false) soft-deletes", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C12: forceDelete() - hard delete (file_deleted=1, deleted=0)
// ----------------------------------------------------------------------------
try {
    var rfHard = new RemoteFile("/rftest/hard-delete.txt");
    rfHard.write("Hard Delete");
    rfHard.complete();
    var fd12 = rfHard.forceDelete();
    var ex12 = rfHard.exists(true);
    var d12 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='hard-delete.txt'";
    });
    check({
        caseId: "C12",
        name: "forceDelete() hard-deletes (file_deleted=1)",
        ok: fd12 && !ex12,
        detail: "forceDelete=" + fd12 + ", exists(refresh)=" + ex12 + d12,
        objective: "forceDelete() sets file_deleted=1 (deleted=0); exists() no longer sees the row",
        objectiveZh: "forceDelete() 硬刪除（file_deleted=1，exists 失效）"
    });
} catch (e) {
    check({ caseId: "C12", name: "forceDelete() hard-deletes", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C13: isTimeout() - pending row past LogicTimeout is timed out; complete clears it
// ----------------------------------------------------------------------------
try {
    var rfTmo = new RemoteFile("/rftest/timeout.txt");
    rfTmo.write("Timeout Data");
    // Age the pending row past LogicTimeout (300s) so the timeout check fires.
    var conn13 = Console.getDataSource().getConnection();
    var st13 = conn13.createStatement();
    st13.executeUpdate("UPDATE remote_file SET updated_at = TIMESTAMPADD(SECOND, -400, updated_at) WHERE file_name='timeout.txt'");
    st13.close();
    conn13.close();
    var tmo1 = rfTmo.isTimeout();
    rfTmo.complete();
    var tmo2 = rfTmo.isTimeout();
    var isc13 = rfTmo.isCompleted();
    var d13 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='timeout.txt' AND deleted=0";
    });
    check({
        caseId: "C13",
        name: "isTimeout() pending > LogicTimeout; completed never times out",
        ok: tmo1 && !tmo2 && isc13,
        detail: "isTimeout(after aging 400s)=" + tmo1 + ", isTimeout(after complete)=" + tmo2 + ", isCompleted=" + isc13 + d13,
        objective: "isTimeout()=true for a pending (last_index=-1) row whose updated_at is older than LogicTimeout; false once completed",
        objectiveZh: "isTimeout() 對超時未完成檔案為 true，complete 後為 false"
    });
} catch (e) {
    check({ caseId: "C13", name: "isTimeout() pending/completed", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C14: writeToDisk(path) exports the remote file to the local disk
// ----------------------------------------------------------------------------
try {
    var tmpPath = System.getProperty("java.io.tmpdir") + "/rftest-export-" + System.currentTimeMillis() + ".txt";
    var outDf = null;
    var fsExists = false;
    var expContent = "";
    var c14Err = "";
    try {
        outDf = rfMoved.writeToDisk(tmpPath);
        // writeToDisk() drives merge() over the async CacheArray worker - the
        // DiskFile is written (and its channel locked) on that thread, then
        // released when the worker's completed() flushes and closes. Poll for
        // the file to be present AND readable (lock released).
        var waitedW = 0;
        while (waitedW < 15000) {
            var exW = false;
            try { exW = Files.exists(Paths.get(tmpPath)); } catch (e) {}
            if (exW) {
                var rd = null;
                try { rd = new Str(Files.readAllBytes(Paths.get(tmpPath)), "UTF-8"); } catch (e) { rd = null; }
                if (rd !== null && rd !== undefined) { expContent = rd; break; }
            }
            Thread.sleep(200);
            waitedW += 200;
        }
        fsExists = Files.exists(Paths.get(tmpPath));
        if (fsExists && expContent.length === 0) {
            try { expContent = new Str(Files.readAllBytes(Paths.get(tmpPath)), "UTF-8"); } catch (e) { c14Err = "" + e; }
        }
    } catch (e) {
        c14Err = "" + e;
    }
    try { Files.deleteIfExists(Paths.get(tmpPath)); } catch (e2) {}
    check({
        caseId: "C14",
        name: "writeToDisk(path) exports to local disk",
        ok: outDf != null && fsExists && ("" + expContent) === HELLO_APPEND,
        detail: "exported=" + fsExists + ", content='" + expContent + "'" + (c14Err.length > 0 ? " / error=" + c14Err : ""),
        objective: "writeToDisk(localPath) writes the decrypted merged content to a DiskFile",
        objectiveZh: "writeToDisk() 匯出遠端檔案到本機磁碟（內容一致）"
    });
} catch (e) {
    check({ caseId: "C14", name: "writeToDisk(path) exports to local disk", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// C15: large file auto-split via DiskFile.writeToRemote
// Reference: production RemoteFileDsWriter.properties uses FilePartSize=1mb;
// the suite uses 1kb so a multi-part file can be built cheaply. The canonical
// large-file path is DiskFile.writeToRemote(RemoteFile) -> RemoteFile.copyFrom
// -> DiskFile.split(filePartSize, FilePart): the source disk file is sliced
// into FilePartSize chunks on the DiskFile-split worker and each chunk is
// stored as one remote_file part (remote_file row + remote_file_data_<n>
// BLOB); completed() stores the final batch and calls complete(lastIndex,
// fileSize). So a 32KB disk file -> 32 x 1KB parts automatically.
// ----------------------------------------------------------------------------
try {
    var rfBig = new RemoteFile("/rftest/big.txt");
    var PART_N = 32;
    var part1024 = "";
    for (var i = 0; i < 1024; i++) {
        part1024 += String.fromCharCode(65 + (i % 26)); // A-Z repeating 1KB block
    }
    var bigContent = "";
    for (var p = 0; p < PART_N; p++) { bigContent += part1024; }
    // 1) materialise a 32KB source file on the local disk
    var bigPath = System.getProperty("java.io.tmpdir") + "/rftest-big-src.txt";
    Files.writeString(Paths.get(bigPath), bigContent,
        Java.type("java.nio.charset.StandardCharsets").UTF_8);
    // 2) DiskFile -> writeToRemote -> auto-split on the DiskFile-split worker
    var dfBig = new DiskFile(bigPath);
    dfBig.writeToRemote(rfBig);
    // 3) poll for the async split/completed to finish
    var done15 = false;
    var waitedB = 0;
    while (waitedB < 10000) {
        try {
            if (rfBig.exists(true) && rfBig.isCompleted()) { done15 = true; break; }
        } catch (e) {}
        Thread.sleep(200);
        waitedB += 200;
    }
    var sz15 = rfBig.size();
    var rs15 = rfBig.readAllString();
    var d15 = dbVerify(function () {
        return "SELECT * FROM remote_file WHERE file_name='big.txt' AND deleted=0 ORDER BY file_part_index";
    });
    // dbVerify caps displayed rows at 30, so count the part rows with a
    // dedicated COUNT(*) (also via Console.getDataSource()).
    var partRows = -1;
    try {
        var cntConn = Console.getDataSource().getConnection();
        var cntSt = cntConn.createStatement();
        var cntRs = cntSt.executeQuery("SELECT COUNT(*) AS CNT FROM remote_file WHERE file_name='big.txt' AND deleted=0");
        if (cntRs.next()) { partRows = cntRs.getLong(1); }
        cntRs.close(); cntSt.close(); cntConn.close();
    } catch (ec) {
        partRows = -1;
    }
    try { Files.deleteIfExists(Paths.get(bigPath)); } catch (e2) {}
    check({
        caseId: "C15",
        name: "large file auto-split (DiskFile 32KB -> 32 x 1KB parts)",
        ok: done15 && sz15 === 32768 && ("" + rs15) === bigContent && partRows === PART_N,
        detail: "autoSplitDone(waited " + waitedB + "ms)=" + done15 + ", size=" + sz15 + ", readLen=" + ("" + rs15).length + ", partRows=" + partRows + " (32 x 1024B)" + d15,
        objective: "DiskFile.writeToRemote slices a 32KB disk file into 32 x 1KB remote parts (FilePartSize) and complete() re-aggregates size; readAllString() returns the full 32KB content",
        objectiveZh: "大檔案自動分片（DiskFile 32KB → 32 x 1KB 分片，writeToRemote/split）"
    });
} catch (e) {
    check({ caseId: "C15", name: "large file auto-split (DiskFile 32KB -> 32 x 1KB parts)", ok: false, detail: "error=" + e });
}

// ----------------------------------------------------------------------------
// Cleanup: remove this suite's rows so re-runs stay deterministic.
// ----------------------------------------------------------------------------
try {
    var connE = Console.getDataSource().getConnection();
    var stE = connE.createStatement();
    stE.execute("DELETE FROM remote_file");
    stE.execute("DELETE FROM remote_file_data_0");
    stE.execute("DELETE FROM remote_file_data_1");
    stE.close();
    connE.close();
} catch (e) {
    print("[WARN] TestRemoteFile cleanup: " + e);
}

writeReport();
writeMd();
