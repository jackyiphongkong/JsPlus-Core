// ============================================================================
// JsPlus-Core TestSimpleCssCompressor - CSS minifier API suite
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestSimpleCssCompressor.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestSimpleCssCompressor-Report.html
//            (TestBase format, same as the other suites)
// Scope    : com.killcoding.tpl.SimpleCssCompressor - regex-based CSS minifier.
//            Covers: constructor wrap + getSource(), comment stripping,
//            whitespace before '{' removal, missing-semicolon repair,
//            newline-free output, output-shorter-than-input and empty input.
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

var REPORT_TITLE = "JsPlus-Core SimpleCssCompressor Test";
var REPORT_NAME = "TestSimpleCssCompressor-Report.html";

beginTest();

print("=== JsPlus-Core SimpleCssCompressor Test ===");
print("JS_PLUS_PATH: " + basePath);

// Unwrap a Java String / JsString / JS value to a plain JS string.
function str(s) {
    return JsString.valueOf(s).asString();
}

try {
    var SimpleCssCompressor = Java.type("com.killcoding.tpl.SimpleCssCompressor");

    // C1: getSource() returns the wrapped CSS source unchanged
    var css1 = new SimpleCssCompressor("body{color:red}");
    Logger.systemMark("TestSimpleCssCompressor.js", "C1");
    check({
        caseId: "C1",
        name: "getSource() returns the wrapped CSS source",
        ok: str(css1.getSource()) === "body{color:red}",
        detail: "source=" + str(css1.getSource()),
        objective: "the constructor stores the CSS text and getSource() exposes it verbatim",
        objectiveZh: "構造器保存 CSS 文本，getSource() 原樣返回",
        command: "new SimpleCssCompressor('body{color:red}').getSource()"
    });

    // C2: block comments are stripped
    var css2 = new SimpleCssCompressor("/* c */\nbody{color:red}");
    var v2 = str(css2.compressor());
    Logger.systemMark("TestSimpleCssCompressor.js", "C2");
    check({
        caseId: "C2",
        name: "block comments are stripped",
        ok: v2.indexOf("/* c */") < 0 && v2.indexOf("body{color:red}") >= 0,
        detail: "out=" + v2,
        objective: "/* ... */ comments are removed from the minified output",
        objectiveZh: "/* ... */ 註釋從壓縮輸出中移除",
        command: "new SimpleCssCompressor('/* c */\\nbody{color:red}').compressor()"
    });

    // C3: whitespace before '{' is removed
    var css3 = new SimpleCssCompressor("body {\ncolor: red;\n}");
    var v3 = str(css3.compressor());
    Logger.systemMark("TestSimpleCssCompressor.js", "C3");
    check({
        caseId: "C3",
        name: "whitespace before '{' is removed",
        ok: v3.indexOf("body{") >= 0 && v3.indexOf("body {") < 0,
        detail: "out=" + v3,
        objective: "leading whitespace of every '{' is dropped to tighten rules",
        objectiveZh: "每個 { 前的空白移除以緊湊規則",
        command: "new SimpleCssCompressor('body {\\ncolor: red;\\n}').compressor()"
    });

    // C4: a declaration missing its semicolon is repaired
    var css4 = new SimpleCssCompressor("body{\ncolor: red\n}");
    var v4 = str(css4.compressor());
    Logger.systemMark("TestSimpleCssCompressor.js", "C4");
    check({
        caseId: "C4",
        name: "missing semicolon is repaired",
        ok: v4.indexOf("color: red;") >= 0,
        detail: "out=" + v4,
        objective: "a declaration line that ends without ';' gets one appended so the minified CSS stays valid",
        objectiveZh: "行尾缺少分號的宣告自動補上，壓縮後 CSS 仍合法",
        command: "new SimpleCssCompressor('body{\\ncolor: red\\n}').compressor()"
    });

    // C5: the minified output contains no line breaks
    var css5 = new SimpleCssCompressor("body {\ncolor: red;\n}");
    var v5 = str(css5.compressor());
    Logger.systemMark("TestSimpleCssCompressor.js", "C5");
    check({
        caseId: "C5",
        name: "minified output has no line breaks",
        ok: v5.indexOf("\n") < 0 && v5.indexOf("\r") < 0,
        detail: "out=" + v5,
        objective: "the pipeline ends by removing every newline, yielding one flat line",
        objectiveZh: "管道最後移除所有換行，輸出單行",
        command: "new SimpleCssCompressor('body {\\ncolor: red;\\n}').compressor()"
    });

    // C6: the minified output is shorter than the input
    var css6 = new SimpleCssCompressor("  body {  \n  color: red;  \n  }  ");
    var v6 = str(css6.compressor());
    Logger.systemMark("TestSimpleCssCompressor.js", "C6");
    check({
        caseId: "C6",
        name: "minified output is shorter than the input",
        ok: v6.length < str(css6.getSource()).length,
        detail: "inLen=" + str(css6.getSource()).length + " outLen=" + v6.length,
        objective: "indentation/blank whitespace and newlines are shed so the output is smaller",
        objectiveZh: "縮進、空白與換行被去除，輸出更短",
        command: "new SimpleCssCompressor('  body {  \\n  color: red;  \\n  }  ').compressor()"
    });

    // C7: an empty input survives the pipeline
    var css7 = new SimpleCssCompressor("");
    var v7 = str(css7.compressor());
    Logger.systemMark("TestSimpleCssCompressor.js", "C7");
    check({
        caseId: "C7",
        name: "empty input survives the pipeline",
        ok: v7 === "",
        detail: "out=[" + v7 + "]",
        objective: "an empty source compresses to an empty string without throwing",
        objectiveZh: "空源碼壓縮為空字符串且不拋異常",
        command: "new SimpleCssCompressor('').compressor()"
    });

} catch (e) {
    Logger.systemMark("TestSimpleCssCompressor.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestSimpleCssCompressor suite error",
        ok: false,
        detail: str(e.toString())
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
