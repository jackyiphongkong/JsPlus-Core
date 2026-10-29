// ============================================================================
// JsPlus-Core TestFileTools - file utility helpers (com.killcoding.tool.FileTools)
// ----------------------------------------------------------------------------
// Scope    : FileTools static helpers - write(String/File, append), text
//            (String/File), size (String/File) and recursive deleteDirectory.
//            All cases run in a unique temp directory cleaned up afterwards.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var FileTools = Java.type("com.killcoding.tool.FileTools");

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

var REPORT_TITLE = "JsPlus-Core FileTools Test";
var REPORT_NAME = "TestFileTools-Report.html";

beginTest();

print("=== JsPlus-Core FileTools Test ===");
print("JS_PLUS_PATH: " + basePath);

var tmpRoot = Files.createTempDirectory("jsplus_ftools_").toString();
var p = function (name) { return tmpRoot + File.separator + name; };

try {
    // C1: write(String, append=false) creates the file; text(String) reads it
    var f1 = p("c1.txt");
    FileTools.write(f1, "hello filetools", false);
    var t1 = FileTools.text(f1);
    Logger.systemMark("TestFileTools.js", "C1");
    check({
        caseId: "C1",
        name: "write(String path, msg, false) + text(String path)",
        ok: t1 === "hello filetools" && FileTools.size(f1) === "hello filetools".length,
        detail: "text=" + t1 + " size=" + FileTools.size(f1),
        objective: "write creates the file; text returns its content",
        objectiveZh: "write 建立檔案；text 回傳內容",
        command: "FileTools.write(path, msg, false); FileTools.text(path)"
    });

    // C2: append=true appends instead of overwriting
    var f2 = p("c2.txt");
    FileTools.write(f2, "part1", false);
    FileTools.write(f2, "-part2", true);
    var t2 = FileTools.text(f2);
    Logger.systemMark("TestFileTools.js", "C2");
    check({
        caseId: "C2",
        name: "write(append=true) appends content",
        ok: t2 === "part1-part2",
        detail: "text=" + t2,
        objective: "append mode preserves the previous content",
        objectiveZh: "append 模式保留先前內容",
        command: "FileTools.write(f, 'part1', false); FileTools.write(f, '-part2', true)"
    });

    // C3: overwrite=false replaces the content
    var f3 = p("c3.txt");
    FileTools.write(f3, "old-content", false);
    FileTools.write(f3, "new-content", false);
    var t3 = FileTools.text(f3);
    Logger.systemMark("TestFileTools.js", "C3");
    check({
        caseId: "C3",
        name: "write(append=false) overwrites the file",
        ok: t3 === "new-content",
        detail: "text=" + t3,
        objective: "non-append mode replaces the whole content",
        objectiveZh: "非 append 模式取代全部內容",
        command: "FileTools.write(f, 'new-content', false); FileTools.text(f)"
    });

    // C4: File overloads - write(File), text(File), size(File)
    var f4 = new File(p("c4.txt"));
    FileTools.write(f4, "file-overload", false);
    var t4 = FileTools.text(f4);
    var s4 = FileTools.size(f4);
    Logger.systemMark("TestFileTools.js", "C4");
    check({
        caseId: "C4",
        name: "File overloads write/text/size",
        ok: t4 === "file-overload" && s4 === "file-overload".length,
        detail: "text=" + t4 + " size=" + s4,
        objective: "File-based overloads behave like String-based ones",
        objectiveZh: "File 重載與 String 重載行為一致",
        command: "FileTools.write(file, msg, false); FileTools.text(file)"
    });

    // C5: size() of a missing file returns 0 (no exception)
    var s5 = FileTools.size(p("missing.txt"));
    Logger.systemMark("TestFileTools.js", "C5");
    check({
        caseId: "C5",
        name: "size() of a missing file returns 0",
        ok: s5 === 0,
        detail: "size=" + s5,
        objective: "missing file -> 0L instead of an exception",
        objectiveZh: "檔案不存在回傳 0 而非例外",
        command: "FileTools.size('missing.txt')"
    });

    // C6: text() of a missing file throws (surfaced to caller)
    var threw6 = false;
    try {
        FileTools.text(p("missing.txt"));
    } catch (e6) {
        threw6 = true;
    }
    Logger.systemMark("TestFileTools.js", "C6");
    check({
        caseId: "C6",
        name: "text() of a missing file throws",
        ok: threw6,
        detail: "threw=" + threw6,
        objective: "reading a missing file surfaces the IOException",
        objectiveZh: "讀取不存在檔案拋出 IOException",
        command: "FileTools.text('missing.txt')"
    });

    // C7: deleteDirectory() recursively deletes a tree
    var dir7 = p("tree");
    var d7 = new File(dir7);
    d7.mkdirs();
    var sub7 = new File(dir7 + File.separator + "sub");
    sub7.mkdirs();
    FileTools.write(dir7 + File.separator + "a.txt", "a", false);
    FileTools.write(sub7.getAbsolutePath() + File.separator + "b.txt", "b", false);
    var deleted7 = FileTools.deleteDirectory(d7);
    Logger.systemMark("TestFileTools.js", "C7");
    check({
        caseId: "C7",
        name: "deleteDirectory() removes a nested tree",
        ok: deleted7 === true && !Files.exists(Paths.get(dir7)),
        detail: "deleted=" + deleted7 + " exists=" + Files.exists(Paths.get(dir7)),
        objective: "deleteDirectory removes files and subdirectories recursively",
        objectiveZh: "deleteDirectory 遞迴刪除檔案與子目錄",
        command: "FileTools.deleteDirectory(dir)"
    });

    // C8: deleteDirectory() on a missing directory returns false (DiskFile semantics)
    var threw8 = false;
    var deleted8 = false;
    try {
        deleted8 = FileTools.deleteDirectory(new File(p("never-existed")));
    } catch (e8) {
        threw8 = true;
    }
    Logger.systemMark("TestFileTools.js", "C8");
    check({
        caseId: "C8",
        name: "deleteDirectory() on a missing dir returns false",
        ok: !threw8 && deleted8 === false,
        detail: "deleted=" + deleted8 + " threw=" + threw8,
        objective: "nothing to delete -> false without exception (DiskFile.delete semantics)",
        objectiveZh: "無內容可刪回傳 false 且不拋例外（DiskFile.delete 語義）",
        command: "FileTools.deleteDirectory(missingDir)"
    });

    // C9: UTF-8 content roundtrip (non-ASCII)
    var f9 = p("c9.txt");
    FileTools.write(f9, "中文內容 roundtrip", false);
    var t9 = FileTools.text(f9);
    Logger.systemMark("TestFileTools.js", "C9");
    check({
        caseId: "C9",
        name: "UTF-8 non-ASCII content roundtrip",
        ok: t9 === "中文內容 roundtrip",
        detail: "text=" + t9,
        objective: "write/text preserve non-ASCII text",
        objectiveZh: "write/text 保留非 ASCII 文字",
        command: "FileTools.write(f, '中文'); FileTools.text(f)"
    });

    // C10: size(String) and size(File) agree
    var f10 = p("c10.txt");
    FileTools.write(f10, "1234567890", false);
    var s10str = FileTools.size(f10);
    var s10file = FileTools.size(new File(f10));
    Logger.systemMark("TestFileTools.js", "C10");
    check({
        caseId: "C10",
        name: "size(String) and size(File) agree",
        ok: s10str === 10 && s10file === 10,
        detail: "stringSize=" + s10str + " fileSize=" + s10file,
        objective: "both size overloads return the same byte count",
        objectiveZh: "兩個 size 重載回傳相同位元組數",
        command: "FileTools.size(path) === FileTools.size(file)"
    });

} catch (e) {
    Logger.systemMark("TestFileTools.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestFileTools suite error",
        ok: false,
        detail: "" + e
    });
}

// Clean up the temp directory
try {
    var dirClean = new File(tmpRoot);
    var filesClean = dirClean.listFiles();
    if (filesClean !== null) {
        for (var iC = 0; iC < filesClean.length; iC++) { filesClean[iC].delete(); }
    }
    dirClean.delete();
} catch (e) {}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
