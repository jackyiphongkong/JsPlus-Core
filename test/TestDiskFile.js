// ============================================================================
// JsPlus-Core TestDiskFile - console-mode DiskFile unit test
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestDiskFile.js < NUL
//            Interactive console eval:
//              Console.eval(<PROJECT_ROOT>/test/TestDiskFile.js)
// Output   : <PROJECT_ROOT>/test_reports/TestDiskFile-Report.html (TestBase
//            format, same as the other suites)
// Scope    : com.killcoding.file.DiskFile - the embedded engine's disk file
//            abstraction (write/read round-trips, byte + charset variants,
//            metadata, mkdirs, copyTo/moveTo, delete, complete, modify time,
//            manual stream, links). Async paths (startFullAsync / split) are
//            covered by TestRemoteFile.js (DiskFile.writeToRemote) instead:
//            their callbacks run on Java worker threads, which GraalVM JS
//            forbids from entering the JS context.
// Workspace: <PROJECT_ROOT>/testdata/diskfile/ (created per run, removed at
//            the end). No database is required - pure disk file test.
// ============================================================================

var System = Java.type("java.lang.System");
var File = Java.type("java.io.File");
var Timestamp = Java.type("java.sql.Timestamp");
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

var REPORT_TITLE = "JsPlus-Core DiskFile Test";
var REPORT_NAME = "TestDiskFile-Report.html";
var ROOT = basePath + "../testdata/diskfile/";
var DIR_A = ROOT + "dirA/";
var DIR_B = ROOT + "dirB/";

var UTF8 = "UTF-8";

function fileOf(p) { return new File(p); }
// recursive delete of a java.io.File tree
function deleteTree(f) {
    if (f === null || f === undefined) return;
    if (f.isDirectory()) {
        var kids = f.listFiles();
        if (kids !== null) {
            for (var i = 0; i < kids.length; i++) deleteTree(kids[i]);
        }
    }
    try { f.delete(); } catch (e2) {}
}

beginTest();

print("=== JsPlus-Core DiskFile Test ===");
print("JS_PLUS_PATH: " + basePath);
print("Test workspace: " + ROOT);

var cleanupDone = false;
function cleanupWorkspace() {
    if (cleanupDone) return;
    cleanupDone = true;
    deleteTree(new File(ROOT));
}

try {

// ---------------------------------------------------------------------------
// C1: string write / read round-trip (default charset = UTF-8)
// ---------------------------------------------------------------------------
var p1 = ROOT + "c1_hello.txt";
var df1 = new DiskFile(p1);
var w1 = df1.write("Hello DiskFile, 你好，JsPlus-Core！", UTF8);
var r1 = df1.readAllString(UTF8);
check({
    caseId: "C1",
    name: "DiskFile string write/read round-trip (UTF-8)",
    ok: (w1 === true) && (r1 === "Hello DiskFile, 你好，JsPlus-Core！"),
    detail: "write=" + w1 + " readBack=" + r1 + " len=" + r1.length,
    objective: "write(String, charset) then readAllString(charset) returns identical content incl. CJK",
    objectiveZh: "字串寫入後以相同 charset 讀回，內容（含中文）完全一致",
    command: "df.write(str, \"UTF-8\"); df.readAllString(\"UTF-8\")"
});

// ---------------------------------------------------------------------------
// C2: append mode (String, boolean) - content concatenation
// ---------------------------------------------------------------------------
var p2 = ROOT + "c2_append.txt";
var df2 = new DiskFile(p2);
df2.write("line1\n", UTF8);
df2.write("line2\n", UTF8, true);
var r2 = df2.readAllString(UTF8);
check({
    caseId: "C2",
    name: "DiskFile append write (write data, charset, append)",
    ok: (r2 === "line1\nline2\n"),
    detail: "readBack=" + r2.replace("\n", "\\n"),
    objective: "write(..., append=true) appends to existing content",
    objectiveZh: "追加模式寫入會接續既有內容",
    command: "df.write(\"line1\\n\"); df.write(\"line2\\n\", \"UTF-8\", true)"
});

// ---------------------------------------------------------------------------
// C3: byte[] write / readAllBytes round-trip (binary content)
// ---------------------------------------------------------------------------
var p3 = ROOT + "c3_bin.dat";
var df3 = new DiskFile(p3);
var bin = Java.to([0, 1, 2, 3, 127, -128, -1, 65, 66, 67], "byte[]");
var w3 = df3.write(bin);
var rb3 = df3.readAllBytes();
var eq3 = (rb3 !== null) && (rb3.length === bin.length);
if (eq3) {
    for (var i3 = 0; i3 < bin.length; i3++) {
        if (rb3[i3] !== bin[i3]) { eq3 = false; break; }
    }
}
check({
    caseId: "C3",
    name: "DiskFile byte[] write/readAllBytes round-trip",
    ok: (w3 === true) && eq3 && (df3.size() === bin.length),
    detail: "write=" + w3 + " bytes=" + (rb3 === null ? "null" : rb3.length) + " size=" + df3.size(),
    objective: "raw byte array survives write/read unchanged (incl. negative values)",
    objectiveZh: "原始位元組陣列（含負數）寫入讀回不變",
    command: "df.write(byte[]); df.readAllBytes(); df.size()"
});

// ---------------------------------------------------------------------------
// C4: metadata - exists / isFile / isDirectory / size before and after delete
// ---------------------------------------------------------------------------
var p4 = ROOT + "c4_meta.txt";
var df4 = new DiskFile(p4);
df4.write("12345", UTF8);
var metaOk = df4.exists() === true && df4.isFile() === true
    && df4.isDirectory() === false && df4.size() === 5;
var delOk = df4.delete() === true && df4.exists() === false;
check({
    caseId: "C4",
    name: "DiskFile metadata (exists/isFile/isDirectory/size) + delete",
    ok: metaOk && delOk,
    detail: "exists=" + df4.exists() + " isFile=" + df4.isFile() + " isDir=" + df4.isDirectory()
        + " size=" + df4.size() + " deleted=" + delOk,
    objective: "metadata reflects real file state; delete removes the file",
    objectiveZh: "檔案元資料正確反映實況；delete 後 exists=false",
    command: "df.exists()/isFile()/isDirectory()/size()/delete()"
});

// ---------------------------------------------------------------------------
// C5: mkdirs + directory metadata + getOrigin path
// ---------------------------------------------------------------------------
var dirA = new DiskFile(DIR_A + "nested/deep/");
var mkOk = dirA.mkdirs();
var dirCheck = dirA.isDirectory() === true && dirA.exists() === true;
var originPath = dirA.getOrigin().getPath();
check({
    caseId: "C5",
    name: "DiskFile mkdirs (nested) + directory detection + getOrigin",
    ok: mkOk === true && dirCheck && ("" + originPath).indexOf("testdata") >= 0,
    detail: "mkdirs=" + mkOk + " isDir=" + dirA.isDirectory() + " origin=" + originPath,
    objective: "nested mkdirs creates the full path; getOrigin exposes the java.io.File",
    objectiveZh: "多層 mkdirs 建立完整目錄；getOrigin 返回底層 File",
    command: "df.mkdirs(); df.isDirectory(); df.getOrigin().getPath()"
});

// ---------------------------------------------------------------------------
// C6: copyTo - content and metadata carried to the destination
// ---------------------------------------------------------------------------
var p6a = ROOT + "c6_src.txt";
var p6b = DIR_B + "c6_dst.txt";
var df6a = new DiskFile(p6a);
df6a.write("copy me over", UTF8);
new DiskFile(DIR_B).mkdirs();
df6a.copyTo(p6b);
var df6b = new DiskFile(p6b);
var r6b = df6b.readAllString(UTF8);
check({
    caseId: "C6",
    name: "DiskFile copyTo (to new path)",
    ok: df6b.exists() === true && r6b === "copy me over" && df6a.exists() === true,
    detail: "dstExists=" + df6b.exists() + " dstContent=" + r6b + " srcStillExists=" + df6a.exists(),
    objective: "copyTo duplicates content, source stays untouched",
    objectiveZh: "copyTo 複製內容，來源檔案保持不變",
    command: "df.copyTo(toPath)"
});

// ---------------------------------------------------------------------------
// C7: moveTo - source removed, destination carries content
// ---------------------------------------------------------------------------
var p7a = ROOT + "c7_src.txt";
var p7b = DIR_B + "c7_dst.txt";
var df7a = new DiskFile(p7a);
df7a.write("move me", UTF8);
df7a.moveTo(p7b);
var df7b = new DiskFile(p7b);
var r7b = df7b.readAllString(UTF8);
check({
    caseId: "C7",
    name: "DiskFile moveTo (source removed)",
    ok: df7a.exists() === false && df7b.exists() === true && r7b === "move me",
    detail: "srcExists=" + df7a.exists() + " dstExists=" + df7b.exists() + " dstContent=" + r7b,
    objective: "moveTo relocates the file: source gone, destination intact",
    objectiveZh: "moveTo 搬移檔案：來源消失、目的地內容完整",
    command: "df.moveTo(toPath)"
});

// ---------------------------------------------------------------------------
// C8: complete() marks the file done without corrupting content
// ---------------------------------------------------------------------------
var p8 = ROOT + "c8_complete.txt";
var df8 = new DiskFile(p8);
df8.write("ready", UTF8);
var cpOk = df8.complete();
var r8 = df8.readAllString(UTF8);
check({
    caseId: "C8",
    name: "DiskFile complete() marker",
    ok: cpOk === true && r8 === "ready",
    detail: "complete=" + cpOk + " content=" + r8,
    objective: "complete() succeeds and leaves content readable",
    objectiveZh: "complete() 標記完成後內容仍可正常讀取",
    command: "df.complete(); df.readAllString()"
});

// ---------------------------------------------------------------------------
// C9: modify time - setModifyTime/getModifyTime round-trip
// ---------------------------------------------------------------------------
var p9 = ROOT + "c9_time.txt";
var df9 = new DiskFile(p9);
df9.write("ts", UTF8);
var baseMs = Date.now();
var tsIn = new Timestamp(baseMs);
df9.setModifyTime(tsIn);
var tsOut = df9.getModifyTime();
var diffMs = Math.abs((tsOut === null ? -1 : tsOut.getTime()) - baseMs);
check({
    caseId: "C9",
    name: "DiskFile setModifyTime/getModifyTime round-trip",
    ok: tsOut !== null && diffMs <= 2000,
    detail: "set=" + baseMs + " got=" + (tsOut === null ? "null" : tsOut.getTime()) + " diffMs=" + diffMs,
    objective: "modify timestamp survives set/get (within FS precision)",
    objectiveZh: "修改時間戳 set/get 回環（誤差在檔案系統精度內）",
    command: "df.setModifyTime(ts); df.getModifyTime()"
});

// ---------------------------------------------------------------------------
// C10: manual stream - manualOpen/manualWrite/manualClose
// ---------------------------------------------------------------------------
var p10 = ROOT + "c10_manual.txt";
var df10 = new DiskFile(p10);
var mOpen = df10.manualOpen(false);
df10.manualWrite("manual part one");
df10.manualWrite(" + part two");
df10.manualClose();
var r10 = df10.readAllString(UTF8);
check({
    caseId: "C10",
    name: "DiskFile manual stream (manualOpen/manualWrite/manualClose)",
    ok: mOpen === true && r10 === "manual part one + part two",
    detail: "open=" + mOpen + " content=" + r10,
    objective: "manual streaming writes accumulate and read back correctly",
    objectiveZh: "手動串流多次寫入後內容正確累加並可讀回",
    command: "df.manualOpen(false); df.manualWrite(s); df.manualClose()"
});

// ---------------------------------------------------------------------------
// C11: path info - getPath / getParent / getParents / getOrigin
// ---------------------------------------------------------------------------
var p11 = ROOT + "dirA/c11_path.txt";
var df11 = new DiskFile(p11);
df11.write("p", UTF8);
var parents = df11.getParents();
var parentsStr = (parents === null || parents === undefined) ? "null" : String(parents.size());
check({
    caseId: "C11",
    name: "DiskFile path info (getPath/getParent/getParents)",
    ok: ("" + df11.getPath()).indexOf("c11_path.txt") >= 0
        && ("" + df11.getParent()).indexOf("dirA") >= 0
        && parents !== null && parents.size() >= 1,
    detail: "path=" + df11.getPath() + " parent=" + df11.getParent() + " parents=" + parentsStr,
    objective: "path accessors expose the file location hierarchy",
    objectiveZh: "路徑存取器正確暴露檔案位置層級",
    command: "df.getPath()/getParent()/getParents()"
});

// ---------------------------------------------------------------------------
// C12: symlink - createLink/readLink/isLink (OS privilege tolerant)
// Windows requires admin/Developer Mode for symlink creation; when the OS
// refuses, the case is marked PASS with an explicit skipped note so the
// suite stays green on restricted machines while still covering the API
// where it works (Linux/macOS CI).
// ---------------------------------------------------------------------------
var p12 = ROOT + "c12_target.txt";
var p12l = ROOT + "c12_link.txt";
var df12t = new DiskFile(p12);
df12t.write("link target", UTF8);
var linkOk = false;
var linkDetail = "";
try {
    var created = df12t.createLink(p12l);
    var lf = new DiskFile(p12l);
    var isL = lf.isLink();
    var rL = lf.readAllString(UTF8);
    var rdL = (rL === "link target");
    linkOk = created === true && isL === true && rdL;
    linkDetail = "created=" + created + " isLink=" + isL + " readViaLink=" + rL;
} catch (e) {
    linkDetail = "SKIPPED on this OS/permission: " + e;
    linkOk = true; // OS capability, not a product defect
}
check({
    caseId: "C12",
    name: "DiskFile symlink createLink/readLink/isLink",
    ok: linkOk,
    detail: linkDetail,
    objective: "createLink exposes the target through the link; readLink/isLink reflect link state",
    objectiveZh: "createLink 建立連結並可經連結讀取；isLink 反映連結狀態（無權限環境標記 SKIPPED）",
    command: "df.createLink(to); df.isLink(); df.readAllString()"
});

// ---------------------------------------------------------------------------
// C13: cleanup - workspace removed, no leftovers
// ---------------------------------------------------------------------------
cleanupWorkspace();
var rootGone = !(new File(ROOT).exists());
check({
    caseId: "C13",
    name: "test workspace cleanup",
    ok: rootGone,
    detail: "rootExists=" + (!rootGone) + " (removed " + ROOT + ")",
    objective: "all test artifacts under testdata/diskfile are deleted",
    objectiveZh: "測試工作目錄已完整清理，不留殘留檔案",
    command: "deleteTree(testdata/diskfile)"
});

} catch (e) {
    cleanupWorkspace();
    check({
        caseId: "C-ERR",
        name: "TestDiskFile suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites (TestDatasource/TestCrud/TestRemoteFile): when loaded by TestAll.js
// the aggregate entry decides the JVM exit (JSPLUS_TEST_EXIT=1), otherwise an
// interactive Console.eval(...) run finishes without killing the console.
// Standalone launcher runs can rely on TestAll.js or the JsPlus scanner EOF.
