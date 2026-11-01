// ============================================================================
// JsPlus-Core TestSimpleJsCompressor - JS minifier API suite
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestSimpleJsCompressor.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestSimpleJsCompressor-Report.html
//            (TestBase format, same as the other suites)
// Scope    : com.killcoding.tpl.SimpleJsCompressor - regex-based JS minifier.
//            Covers: constructor wrap + getSource(), block-comment stripping,
//            full-line // comment stripping, trailing // comment line keeping
//            (by design), whitespace before '{' removal, operator line joining,
//            output-shorter-than-input, and source immutability.
// NOTE     : Every java.lang.String crossing the GraalJS boundary is wrapped
//            with com.killcoding.jsplus.JsString (project convention) before
//            JS-level comparisons; no regex literals (GraalVM JS in this
//            LibHome has no TRegex language - /.../ fails at parse time).
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var JsString = Java.type("com.killcoding.jsplus.JsString");

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

var REPORT_TITLE = "JsPlus-Core SimpleJsCompressor Test";
var REPORT_NAME = "TestSimpleJsCompressor-Report.html";

beginTest();

print("=== JsPlus-Core SimpleJsCompressor Test ===");
print("JS_PLUS_PATH: " + basePath);

// Unwrap a Java String / JsString / JS value to a plain JS string.
function str(s) {
    return JsString.valueOf(s).asString();
}

try {
    var SimpleJsCompressor = Java.type("com.killcoding.tpl.SimpleJsCompressor");

    // C1: getSource() returns the wrapped JS source unchanged
    var js1 = new SimpleJsCompressor("var a=1;");
    Logger.systemMark("TestSimpleJsCompressor.js", "C1");
    check({
        caseId: "C1",
        name: "getSource() returns the wrapped JS source",
        ok: str(js1.getSource()) === "var a=1;",
        detail: "source=" + str(js1.getSource()),
        objective: "the constructor stores the JS text and getSource() exposes it verbatim",
        objectiveZh: "構造器保存 JS 文本，getSource() 原樣返回",
        command: "new SimpleJsCompressor('var a=1;').getSource()"
    });

    // C2: block comments are stripped
    var js2 = new SimpleJsCompressor("/* header */\nvar a=1;");
    var v2 = str(js2.compressor());
    Logger.systemMark("TestSimpleJsCompressor.js", "C2");
    check({
        caseId: "C2",
        name: "block comments are stripped",
        ok: v2.indexOf("header") < 0 && v2.indexOf("var a=1;") >= 0,
        detail: "out=" + v2,
        objective: "/* ... */ comments are removed from the minified output",
        objectiveZh: "/* ... */ 註釋從壓縮輸出中移除",
        command: "new SimpleJsCompressor('/* header */\\nvar a=1;').compressor()"
    });

    // C3: full-line // comments are stripped
    var js3 = new SimpleJsCompressor("// note\nvar a=1;");
    var v3 = str(js3.compressor());
    Logger.systemMark("TestSimpleJsCompressor.js", "C3");
    check({
        caseId: "C3",
        name: "full-line // comments are stripped",
        ok: v3.indexOf("note") < 0 && v3.indexOf("var a=1;") >= 0,
        detail: "out=" + v3,
        objective: "whole-line // comments are removed, code lines survive",
        objectiveZh: "整行 // 註釋移除，代碼行保留",
        command: "new SimpleJsCompressor('// note\\nvar a=1;').compressor()"
    });

    // C4: a trailing // comment keeps its line (documented design)
    var js4 = new SimpleJsCompressor("var a=1; // note\nvar b=2;");
    var v4 = str(js4.compressor());
    Logger.systemMark("TestSimpleJsCompressor.js", "C4");
    check({
        caseId: "C4",
        name: "trailing // comment keeps its line",
        ok: v4.indexOf("var a=1; // note") >= 0,
        detail: "out=" + v4,
        objective: "lines still containing // keep a trailing newline so the comment is not glued to the next statement",
        objectiveZh: "含 // 的行保留換行，避免註釋黏連下一語句",
        command: "new SimpleJsCompressor('var a=1; // note\\nvar b=2;').compressor()"
    });

    // C5: whitespace before '{' is removed
    var js5 = new SimpleJsCompressor("if ( a ) {\n    b();\n}");
    var v5 = str(js5.compressor());
    Logger.systemMark("TestSimpleJsCompressor.js", "C5");
    check({
        caseId: "C5",
        name: "whitespace before '{' is removed",
        ok: v5.indexOf("if ( a ){") >= 0 && v5.indexOf("if ( a ) {") < 0,
        detail: "out=" + v5,
        objective: "leading whitespace of every '{' is dropped to tighten blocks",
        objectiveZh: "每個 { 前的空白移除以緊湊區塊",
        command: "new SimpleJsCompressor('if ( a ) {\\n    b();\\n}').compressor()"
    });

    // C6: lines ending on an operator join without a newline
    var js6 = new SimpleJsCompressor("var a=1;\nvar b=2;");
    var v6 = str(js6.compressor());
    Logger.systemMark("TestSimpleJsCompressor.js", "C6");
    check({
        caseId: "C6",
        name: "operator-ending lines are joined",
        ok: v6.indexOf("var a=1;var b=2;") >= 0,
        detail: "out=" + v6,
        objective: "a line whose last char is an operator char (; , etc.) joins the next line",
        objectiveZh: "行尾為操作符（; , 等）時與下一行連接",
        command: "new SimpleJsCompressor('var a=1;\\nvar b=2;').compressor()"
    });

    // C7: the minified output is shorter than the input
    var js7 = new SimpleJsCompressor("  var a = 1;  \n  var b = 2;  ");
    var v7 = str(js7.compressor());
    Logger.systemMark("TestSimpleJsCompressor.js", "C7");
    check({
        caseId: "C7",
        name: "minified output is shorter than the input",
        ok: v7.length < str(js7.getSource()).length,
        detail: "inLen=" + str(js7.getSource()).length + " outLen=" + v7.length,
        objective: "indentation/blank whitespace is shed so the output is smaller",
        objectiveZh: "縮進與空白被去除，輸出更短",
        command: "new SimpleJsCompressor('  var a = 1;  \\n  var b = 2;  ').compressor()"
    });

    // C8: getSource() still returns the original after compressor() (immutable)
    var js8 = new SimpleJsCompressor("var a=1;");
    var v8 = str(js8.compressor());
    Logger.systemMark("TestSimpleJsCompressor.js", "C8");
    check({
        caseId: "C8",
        name: "getSource() is unaffected by compressor()",
        ok: str(js8.getSource()) === "var a=1;",
        detail: "source=" + str(js8.getSource()) + " out=" + v8,
        objective: "compressor() does not mutate the wrapped source",
        objectiveZh: "compressor() 不改動被包裝的原始源碼",
        command: "new SimpleJsCompressor('var a=1;').compressor(); getSource()"
    });

} catch (e) {
    Logger.systemMark("TestSimpleJsCompressor.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestSimpleJsCompressor suite error",
        ok: false,
        detail: str(e.toString())
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
