// ============================================================================
// JsPlus-Core TestFile - file package test suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestFile.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/test_reports/TestFile-Report.html / .md
// Coverage : com.killcoding.file (DiskFile / BaseFile) + com.killcoding.tool.FileTools
//            - write/read roundtrip (auto parent-dir creation, append, charset)
//            - copyTo / moveTo / delete (file + recursive directory)
//            - mkdirs / isDirectory / exists / isFile
//            - metadata (getPath / getParent / getModifyTime / size)
//            - FileTools.text / size / write / deleteDirectory
// NOTE     : All fixtures live under <PROJECT_ROOT>/test/.core-test-tmp/ and are
//            removed at the end of the run.
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

var REPORT_NAME = "TestFile-Report.html";
var REPORT_TITLE = "JsPlus-Core File Test Report";

var FileTools = Java.type("com.killcoding.tool.FileTools");
var File = Java.type("java.io.File");

print("=== JsPlus-Core File Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));

var TMP = basePath + "../test/.core-test-tmp/file";

// Prepare a clean fixture root (delete leftovers from a previous run).
try {
    var tmpRoot = new DiskFile(TMP);
    if (tmpRoot.exists()) {
        tmpRoot.delete();
    }
    var tmpDeep = new DiskFile(TMP + "/deep/sub");
    tmpDeep.mkdirs();
} catch (e) {
    print("[WARN] Could not prepare temp dir " + TMP + ": " + e);
}

// Case C1
var f1 = TMP + "/deep/sub/a.txt";
var df1 = new DiskFile(f1);
var w1 = df1.write("Hello JsPlus-Core");
check({
    name: "DiskFile.write creates file (auto parent dirs)",
    ok: w1 && df1.exists() && df1.isFile(),
    detail: "written=" + w1 + ", exists=" + df1.exists() + ", isFile=" + df1.isFile(),
    objective: "Verify write() creates missing parent directories and the file",
    objectiveZh: "驗證 write() 自動建立上層目錄與檔案",
    command: "new DiskFile(path).write(\"Hello JsPlus-Core\")"
});

// Case C2
check({
    name: "DiskFile.readAllString roundtrip",
    ok: ("Hello JsPlus-Core" === df1.readAllString()),
    detail: "content=[" + df1.readAllString() + "]",
    objective: "Verify readAllString() returns the written content",
    objectiveZh: "驗證 readAllString() 回傳寫入內容",
    command: "df.readAllString()"
});

// Case C3
check({
    name: "DiskFile.size() > 0",
    ok: df1.size() > 0,
    detail: "size=" + df1.size(),
    objective: "Verify size() reports a non-zero byte count",
    objectiveZh: "驗證 size() 回傳非零位元組數",
    command: "df.size()"
});

// Case C4
df1.write(" + appended", true);
check({
    name: "DiskFile.write append",
    ok: df1.readAllString().indexOf("Hello JsPlus-Core + appended") >= 0,
    detail: df1.readAllString(),
    objective: "Verify append mode (write(data, true)) preserves existing content",
    objectiveZh: "驗證附加模式（write(data, true)）保留原有內容",
    command: "df.write(\" + appended\", true)"
});

// Case C5
var f2 = TMP + "/utf8.txt";
var df2 = new DiskFile(f2);
df2.write("中文字元測試-測試", "UTF-8");
var back2 = df2.readAllString("UTF-8");
check({
    name: "DiskFile.write/read UTF-8 (Chinese)",
    ok: back2.indexOf("中文字元測試") >= 0,
    detail: back2,
    objective: "Verify charset-aware write/read handles non-ASCII text",
    objectiveZh: "驗證指定字元集的寫入/讀取可處理非 ASCII 文字",
    command: "df.write(text, \"UTF-8\"); df.readAllString(\"UTF-8\")"
});

// Case C6
var f3 = TMP + "/copy-target.txt";
df2.copyTo(f3);
var df3 = new DiskFile(f3);
check({
    name: "DiskFile.copyTo",
    ok: df3.exists() && df3.readAllString("UTF-8").indexOf("中文字元測試") >= 0,
    detail: "dest exists=" + df3.exists() + ", content=" + df3.readAllString("UTF-8"),
    objective: "Verify copyTo() creates a byte-identical destination",
    objectiveZh: "驗證 copyTo() 建立內容一致的目的檔案",
    command: "df.copyTo(toPath)"
});

// Case C7
var f4 = TMP + "/moved.txt";
df3.moveTo(f4);
var df4 = new DiskFile(f4);
check({
    name: "DiskFile.moveTo",
    ok: df4.exists() && !df3.exists(),
    detail: "dest exists=" + df4.exists() + ", source exists=" + df3.exists(),
    objective: "Verify moveTo() relocates the file (source removed)",
    objectiveZh: "驗證 moveTo() 移動檔案（來源被移除）",
    command: "df.moveTo(toPath)"
});

// Case C8
var dirP = TMP + "/dir-only";
var ddir = new DiskFile(dirP);
var mk = ddir.mkdirs();
check({
    name: "DiskFile.mkdirs + isDirectory",
    ok: mk && ddir.isDirectory() && !ddir.isFile(),
    detail: "mkdirs=" + mk + ", isDirectory=" + ddir.isDirectory(),
    objective: "Verify mkdirs() creates a directory and isDirectory() reports it",
    objectiveZh: "驗證 mkdirs() 建立目錄且 isDirectory() 正確回報",
    command: "df.mkdirs(); df.isDirectory()"
});

// Case C9
var df5 = new DiskFile(TMP + "/del-me.txt");
df5.write("delete me");
var delOk = df5.delete();
check({
    name: "DiskFile.delete file",
    ok: delOk && !df5.exists(),
    detail: "deleted=" + delOk + ", exists after=" + df5.exists(),
    objective: "Verify delete() removes a regular file",
    objectiveZh: "驗證 delete() 移除一般檔案",
    command: "df.delete()"
});

// Case C10
var big = new DiskFile(TMP + "/big-dir/sub");
big.mkdirs();
var f6 = new DiskFile(TMP + "/big-dir/sub/x.txt");
f6.write("x");
var dbig = new DiskFile(TMP + "/big-dir");
var delDirOk = dbig.delete();
check({
    name: "DiskFile.delete directory (recursive)",
    ok: delDirOk && !dbig.exists(),
    detail: "deleted=" + delDirOk + ", exists after=" + dbig.exists(),
    objective: "Verify delete() removes a non-empty directory recursively",
    objectiveZh: "驗證 delete() 遞迴移除非空目錄",
    command: "df.delete()"
});

// Case C11
var md = new DiskFile(f1);
var mdOk = false;
var mdDetail = "";
try {
    mdOk = md.getPath().length > 0 && md.getParent().length > 0 && md.getModifyTime() !== null;
    mdDetail = "path=" + md.getPath() + ", parent=" + md.getParent() + ", modifyTime=" + md.getModifyTime();
} catch (e) {
    mdDetail = "EXCEPTION: " + e;
}
check({
    name: "DiskFile.getPath / getParent / getModifyTime",
    ok: mdOk,
    detail: mdDetail,
    objective: "Verify path metadata accessors return usable values",
    objectiveZh: "驗證路徑元資料存取器回傳可用值",
    command: "df.getPath(); df.getParent(); df.getModifyTime()"
});

// Case C12
var ftP = TMP + "/ft.txt";
FileTools.write(ftP, "filetools-text", false);
check({
    name: "FileTools.write / text / size",
    ok: FileTools.text(ftP).indexOf("filetools-text") >= 0 && FileTools.size(ftP) > 0,
    detail: "text=" + FileTools.text(ftP) + ", size=" + FileTools.size(ftP),
    objective: "Verify FileTools convenience read/write helpers",
    objectiveZh: "驗證 FileTools 簡便讀寫輔助方法",
    command: "FileTools.write(path, text, false); FileTools.text(path); FileTools.size(path)"
});

// Case C13
var ftDir = new File(TMP + "/ft-dir");
ftDir.mkdirs();
var ftChild = new File(ftDir, "c.txt");
try {
    ftChild.createNewFile();
} catch (e) {
    print("[WARN] createNewFile: " + e);
}
var delFt = false;
try {
    delFt = FileTools.deleteDirectory(ftDir);
} catch (e) {
    delFt = false;
}
check({
    name: "FileTools.deleteDirectory",
    ok: delFt && !ftDir.exists(),
    detail: "deleted=" + delFt + ", exists after=" + ftDir.exists(),
    objective: "Verify FileTools.deleteDirectory removes a directory tree",
    objectiveZh: "驗證 FileTools.deleteDirectory 移除目錄樹",
    command: "FileTools.deleteDirectory(dir)"
});

// Cleanup the whole fixture root.
try {
    var tAll = new DiskFile(TMP);
    if (tAll.exists()) {
        tAll.delete();
    }
} catch (e) {
    print("[WARN] Could not clean " + TMP + ": " + e);
}

writeReport();
writeMd();
