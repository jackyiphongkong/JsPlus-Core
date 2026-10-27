// ============================================================================
// JsPlus-Core TestEvalJava - dynamic Java compile + temp jar + ClassLoader
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestEvalJava.js < NUL
// Output   : <PROJECT_ROOT>/test_reports/TestEvalJava-Report.html (TestBase
//            format, same as the other suites)
// Scope    : Console.evalJavaSource(String) / evalJavaFile(String|File) -
//            dynamic compilation with the JDK system compiler, packaging into
//            a temporary jar under <PROJECT_ROOT>/work/evaljava and loading
//            through a dedicated URLClassLoader. Evaluated sources may use the
//            default package or a declared package and may import framework
//            classes (the dynamic classpath covers the whole LibHome).
// ============================================================================

var System = Java.type("java.lang.System");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var StandardCharsets = Java.type("java.nio.charset.StandardCharsets");
var StringType = Java.type("java.lang.String");
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

var REPORT_TITLE = "JsPlus-Core EvalJava Test";
var REPORT_NAME = "TestEvalJava-Report.html";

beginTest();

print("=== JsPlus-Core EvalJava Test ===");
print("JS_PLUS_PATH: " + basePath);

var SRC_HELLO = [
    "public class EvalHello {",
    "    public String hello() {",
    "        return \"hello-eval\";",
    "    }",
    "    public static String staticHello() {",
    "        return \"static-hello\";",
    "    }",
    "}"
].join("\n");

var SRC_PACKAGED = [
    "package com.killcoding.evaltest;",
    "public class EvalPackaged {",
    "    public String tag() {",
    "        return \"packaged-ok\";",
    "    }",
    "}"
].join("\n");

var SRC_FRAMEWORK_REF = [
    "import com.killcoding.tool.CommonTools;",
    "public class EvalFrameworkRef {",
    "    public String frameworkId() {",
    "        return CommonTools.generateId(6);",
    "    }",
    "}"
].join("\n");

var SRC_FROM_FILE = [
    "package com.killcoding.evaltestfile;",
    "public class EvalFromFile {",
    "    public String fromFile() {",
    "        return \"file-ok\";",
    "    }",
    "}"
].join("\n");

try {
    // C1: evalJavaSource - default package class, instance + static methods
    var clazz1 = Console.evalJavaSource(SRC_HELLO);
    var inst1 = clazz1.newInstance();
    var msg1 = inst1.hello();
    var staticMsg1 = clazz1.getMethod("staticHello").invoke(null);
    check({
        caseId: "C1",
        name: "evalJavaSource compiles + loads a default-package class",
        ok: clazz1.getName() === "EvalHello" && msg1 === "hello-eval" && staticMsg1 === "static-hello",
        detail: "class=" + clazz1.getName() + " inst=" + msg1 + " static=" + staticMsg1,
        objective: "source string is compiled to a temp jar and loaded via URLClassLoader",
        objectiveZh: "源碼字串被編譯為臨時 jar 並經 URLClassLoader 載入",
        command: "Console.evalJavaSource(source)"
    });

    // C2: evalJavaSource - declared package resolves the qualified class name
    var clazz2 = Console.evalJavaSource(SRC_PACKAGED);
    var inst2 = clazz2.newInstance();
    check({
        caseId: "C2",
        name: "evalJavaSource honours a declared package",
        ok: clazz2.getName() === "com.killcoding.evaltest.EvalPackaged" && inst2.tag() === "packaged-ok",
        detail: "class=" + clazz2.getName() + " tag=" + inst2.tag(),
        objective: "a package declaration is applied to the source layout and Class name",
        objectiveZh: "套件宣告套用至源碼擺放與類別名稱",
        command: "Console.evalJavaSource(packaged source)"
    });

    // C3: evalJavaSource - evaluated code can reference framework classes
    var clazz3 = Console.evalJavaSource(SRC_FRAMEWORK_REF);
    var inst3 = clazz3.newInstance();
    var id3 = inst3.frameworkId();
    check({
        caseId: "C3",
        name: "evalJavaSource classpath covers the framework (LibHome)",
        ok: id3 !== null && id3.length === 6,
        detail: "frameworkId=" + id3,
        objective: "dynamic -classpath includes the process classpath and LibHome jars",
        objectiveZh: "動態 -classpath 涵蓋程序 classpath 與 LibHome 的 jar",
        command: "Console.evalJavaSource(source using CommonTools)"
    });

    // C4: evalJavaFile(String) - compile a .java file on disk
    var fileSrcDir = new File(basePath + "../work/evaljava-test-src/com/killcoding/evaltestfile");
    fileSrcDir.mkdirs();
    var fileSrc = new File(fileSrcDir, "EvalFromFile.java");
    FileTools.write(fileSrc.getAbsolutePath(), SRC_FROM_FILE, false);
    var clazz4 = Console.evalJavaFile(fileSrc.getAbsolutePath());
    var inst4 = clazz4.newInstance();
    check({
        caseId: "C4",
        name: "evalJavaFile(String) compiles + loads a .java file",
        ok: clazz4.getName() === "com.killcoding.evaltestfile.EvalFromFile" && inst4.fromFile() === "file-ok",
        detail: "class=" + clazz4.getName() + " fromFile=" + inst4.fromFile(),
        objective: "a file path is read, compiled, jarred and loaded",
        objectiveZh: "檔案路徑被讀取、編譯、打包並載入",
        command: "Console.evalJavaFile(filePath)"
    });

    // C5: evalJavaFile(File) - File overload behaves the same
    var clazz5 = Console.evalJavaFile(fileSrc);
    var inst5 = clazz5.newInstance();
    check({
        caseId: "C5",
        name: "evalJavaFile(File) overload works",
        ok: clazz5.getName() === "com.killcoding.evaltestfile.EvalFromFile" && inst5.fromFile() === "file-ok",
        detail: "class=" + clazz5.getName() + " fromFile=" + inst5.fromFile(),
        objective: "the File overload delegates to the same pipeline",
        objectiveZh: "File 多載委派至同一條管線",
        command: "Console.evalJavaFile(new File(filePath))"
    });

    // C6: evalJavaSource - malformed source surfaces the compiler diagnostics
    var c6threw = false, c6msg = "";
    try {
        Console.evalJavaSource("public class BadEval { public void broken( { } }");
    } catch (e) {
        c6threw = true;
        c6msg = "" + e;
    }
    check({
        caseId: "C6",
        name: "evalJavaSource rejects malformed source with diagnostics",
        ok: c6threw && c6msg.indexOf("compilation failed") >= 0,
        detail: c6msg.length > 0 ? c6msg.substring(0, 120) : "no exception thrown",
        objective: "compile errors raise an Exception carrying the javac diagnostics",
        objectiveZh: "編譯錯誤拋出帶有 javac 診斷訊息的 Exception",
        command: "Console.evalJavaSource(malformed source)"
    });

    // C7: evalJavaSource / evalJavaFile - blank input is rejected
    var c7threw = false, c7bThrew = false;
    try { Console.evalJavaSource(""); } catch (e) { c7threw = true; }
    try { Console.evalJavaFile("   "); } catch (e) { c7bThrew = true; }
    check({
        caseId: "C7",
        name: "blank source / blank file path are rejected",
        ok: c7threw && c7bThrew,
        detail: "sourceThrew=" + c7threw + " fileThrew=" + c7bThrew,
        objective: "blank inputs fail fast with a clear Exception",
        objectiveZh: "空白輸入快速失敗並給出明確 Exception",
        command: "Console.evalJavaSource('') / Console.evalJavaFile('   ')"
    });

    // C8: each eval gets its own ClassLoader (distinct Class for the same source)
    var clazz8a = Console.evalJavaSource(SRC_HELLO);
    var clazz8b = Console.evalJavaSource(SRC_HELLO);
    var inst8a = clazz8a.newInstance();
    var inst8b = clazz8b.newInstance();
    check({
        caseId: "C8",
        name: "each eval builds an independent ClassLoader",
        ok: clazz8a !== clazz8b && inst8a.hello() === "hello-eval" && inst8b.hello() === "hello-eval",
        detail: "classA=" + clazz8a + " classB=" + clazz8b,
        objective: "every call packages a fresh jar under a fresh URLClassLoader",
        objectiveZh: "每次呼叫都打包新 jar 並以新的 URLClassLoader 載入",
        command: "Console.evalJavaSource(SRC_HELLO) twice"
    });

    // C9: evalJavaFile of a missing file is rejected
    var c9threw = false;
    try { Console.evalJavaFile(basePath + "../work/no-such-file.java"); } catch (e) { c9threw = true; }
    check({
        caseId: "C9",
        name: "evalJavaFile rejects a missing file",
        ok: c9threw,
        detail: "missingFileThrew=" + c9threw,
        objective: "a non-existent path fails fast instead of compiling nothing",
        objectiveZh: "不存在的路徑快速失敗而非編譯空內容",
        command: "Console.evalJavaFile(missing path)"
    });

    // Cleanup: remove the source file written for C4/C5
    var testSrcRoot = new File(basePath + "../work/evaljava-test-src");
    if (testSrcRoot.exists()) {
        FileTools.deleteDirectory(testSrcRoot);
    }

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestEvalJava suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
