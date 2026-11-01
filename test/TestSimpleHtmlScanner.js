// ============================================================================
// JsPlus-Core TestSimpleHtmlScanner - HTML minifier API suite
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestSimpleHtmlScanner.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestSimpleHtmlScanner-Report.html
//            (TestBase format, same as the other suites)
// Scope    : com.killcoding.tpl.SimpleHtmlScanner - single forward scan HTML
//            minifier (no DOM). Covers: null source, getSource() immutability,
//            indentation collapse, comment removal, quoted '>' inside attributes,
//            pre/textarea verbatim bodies, script/style body minification,
//            case-insensitive raw-text tags, text whitespace collapse, CDATA and
//            DOCTYPE preservation, bare '<' passthrough, static compressor(),
//            empty raw-text body, unterminated tag, and text-run collapse.
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

var REPORT_TITLE = "JsPlus-Core SimpleHtmlScanner Test";
var REPORT_NAME = "TestSimpleHtmlScanner-Report.html";

beginTest();

print("=== JsPlus-Core SimpleHtmlScanner Test ===");
print("JS_PLUS_PATH: " + basePath);

// Unwrap a Java String / JsString / JS value to a plain JS string.
function str(s) {
    return JsString.valueOf(s).asString();
}

try {
    var SimpleHtmlScanner = Java.type("com.killcoding.tpl.SimpleHtmlScanner");

    // C1: a null source compresses to null
    Logger.systemMark("TestSimpleHtmlScanner.js", "C1");
    var v1 = new SimpleHtmlScanner(null).compressor();
    check({
        caseId: "C1",
        name: "null source compresses to null",
        ok: v1 === null || v1 === undefined,
        detail: "out=" + v1,
        objective: "compressor() returns null when the wrapped source is null",
        objectiveZh: "當被包裝源為 null 時 compressor() 返回 null",
        command: "new SimpleHtmlScanner(null).compressor()"
    });

    // C2: getSource() returns the wrapped HTML unchanged
    var html2 = "<p>hi</p>";
    var v2 = str(new SimpleHtmlScanner(html2).getSource());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C2");
    check({
        caseId: "C2",
        name: "getSource() returns the wrapped HTML",
        ok: v2 === html2,
        detail: "source=" + v2,
        objective: "the constructor stores the HTML text and getSource() exposes it verbatim",
        objectiveZh: "構造器保存 HTML 文本，getSource() 原樣返回",
        command: "new SimpleHtmlScanner('<p>hi</p>').getSource()"
    });

    // C3: indentation runs abutting a tag are dropped
    var html3 = "<div>\n  <span>A</span>\n</div>";
    var v3 = str(new SimpleHtmlScanner(html3).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C3");
    check({
        caseId: "C3",
        name: "indentation between tags is dropped",
        ok: v3 === "<div><span>A</span></div>",
        detail: "out=" + v3,
        objective: "a whitespace run containing a line break next to a tag is removed",
        objectiveZh: "緊挨標籤且含換行的空白 run 被刪除",
        command: "new SimpleHtmlScanner('<div>\\n  <span>A</span>\\n</div>').compressor()"
    });

    // C4: comments are dropped
    var html4 = "<!-- note -->\n<p>x</p>";
    var v4 = str(new SimpleHtmlScanner(html4).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C4");
    check({
        caseId: "C4",
        name: "comments are dropped",
        ok: v4.indexOf("note") < 0 && v4.indexOf("<p>x</p>") >= 0,
        detail: "out=" + v4,
        objective: "<!-- ... --> blocks are removed from the output",
        objectiveZh: "<!-- ... --> 註釋從輸出中移除",
        command: "new SimpleHtmlScanner('<!-- note -->\\n<p>x</p>').compressor()"
    });

    // C5: a '>' inside a quoted attribute value does not close the tag
    var html5 = "<a href=\"a>b.html\">link</a>";
    var v5 = str(new SimpleHtmlScanner(html5).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C5");
    check({
        caseId: "C5",
        name: "quoted '>' inside an attribute is kept",
        ok: v5 === "<a href=\"a>b.html\">link</a>",
        detail: "out=" + v5,
        objective: "findTagEnd honours quoted attribute values, so a > inside quotes stays",
        objectiveZh: "標籤結束查找尊重引號，引號內的 > 不結束標籤",
        command: "new SimpleHtmlScanner('<a href=\"a>b.html\">link</a>').compressor()"
    });

    // C6: pre body is kept verbatim
    var html6 = "<pre>\n  a  b\n</pre>";
    var v6 = str(new SimpleHtmlScanner(html6).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C6");
    check({
        caseId: "C6",
        name: "pre body is kept verbatim",
        ok: v6 === "<pre>\n  a  b\n</pre>",
        detail: "out=" + v6,
        objective: "pre is raw text: its body is never whitespace-collapsed",
        objectiveZh: "pre 為 raw text：其內容不做空白壓縮",
        command: "new SimpleHtmlScanner('<pre>\\n  a  b\\n</pre>').compressor()"
    });

    // C7: textarea body is kept verbatim
    var html7 = "<textarea>  x  </textarea>";
    var v7 = str(new SimpleHtmlScanner(html7).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C7");
    check({
        caseId: "C7",
        name: "textarea body is kept verbatim",
        ok: v7 === "<textarea>  x  </textarea>",
        detail: "out=" + v7,
        objective: "textarea is raw text: its body is never whitespace-collapsed",
        objectiveZh: "textarea 為 raw text：其內容不做空白壓縮",
        command: "new SimpleHtmlScanner('<textarea>  x  </textarea>').compressor()"
    });

    // C8: script body is minified by SimpleJsCompressor
    var html8 = "<script>\n  var a = 1;  \n</script>";
    var v8 = str(new SimpleHtmlScanner(html8).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C8");
    check({
        caseId: "C8",
        name: "script body is minified",
        ok: v8.indexOf("<script>") >= 0 && v8.indexOf("var a = 1;") >= 0 && v8.indexOf("  ") < 0,
        detail: "out=" + v8,
        objective: "a script body is run through SimpleJsCompressor and rejoined without padding",
        objectiveZh: "script 內容經 SimpleJsCompressor 壓縮後無留白拼接",
        command: "new SimpleHtmlScanner('<script>\\n  var a = 1;  \\n</script>').compressor()"
    });

    // C9: style body is minified by SimpleCssCompressor
    var html9 = "<style>\n  body { color: red; }\n</style>";
    var v9 = str(new SimpleHtmlScanner(html9).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C9");
    check({
        caseId: "C9",
        name: "style body is minified",
        ok: v9.indexOf("<style>") >= 0 && v9.indexOf("color") >= 0 && v9.indexOf("  ") < 0,
        detail: "out=" + v9,
        objective: "a style body is run through SimpleCssCompressor and rejoined without padding",
        objectiveZh: "style 內容經 SimpleCssCompressor 壓縮後無留白拼接",
        command: "new SimpleHtmlScanner('<style>\\n  body { color: red; }\\n</style>').compressor()"
    });

    // C10: whitespace inside plain text collapses to a single space
    var html10 = "hello\n  world";
    var v10 = str(new SimpleHtmlScanner(html10).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C10");
    check({
        caseId: "C10",
        name: "text whitespace collapses to one space",
        ok: v10 === "hello world",
        detail: "out=" + v10,
        objective: "a whitespace run inside text content becomes one space so words never join",
        objectiveZh: "文本內容中的空白 run 壓成一個空格，詞不粘連",
        command: "new SimpleHtmlScanner('hello\\n  world').compressor()"
    });

    // C11: CDATA sections are preserved
    var html11 = "<![CDATA[ a < b ]]>";
    var v11 = str(new SimpleHtmlScanner(html11).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C11");
    check({
        caseId: "C11",
        name: "CDATA is preserved verbatim",
        ok: v11 === "<![CDATA[ a < b ]]>",
        detail: "out=" + v11,
        objective: "a CDATA section is emitted unchanged",
        objectiveZh: "CDATA 段原樣輸出",
        command: "new SimpleHtmlScanner('<![CDATA[ a < b ]]>').compressor()"
    });

    // C12: declarations such as DOCTYPE are preserved
    var html12 = "<!DOCTYPE html>\n<p>x</p>";
    var v12 = str(new SimpleHtmlScanner(html12).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C12");
    check({
        caseId: "C12",
        name: "DOCTYPE declaration is preserved",
        ok: v12 === "<!DOCTYPE html><p>x</p>",
        detail: "out=" + v12,
        objective: "<! ... > declarations are emitted verbatim; only the following newline is shed",
        objectiveZh: "<! ... > 聲明原樣輸出，其後換行被移除",
        command: "new SimpleHtmlScanner('<!DOCTYPE html>\\n<p>x</p>').compressor()"
    });

    // C13: a bare '<' that does not start a tag passes through as text
    var html13 = "a < b";
    var v13 = str(new SimpleHtmlScanner(html13).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C13");
    check({
        caseId: "C13",
        name: "bare '<' is kept as text",
        ok: v13 === "a < b",
        detail: "out=" + v13,
        objective: "a '<' followed by whitespace is not a tag start and is emitted literally",
        objectiveZh: "後跟空白的 < 不是標籤起點，按字面輸出",
        command: "new SimpleHtmlScanner('a < b').compressor()"
    });

    // C14: the static compressor(String) one-shot works
    var v14 = str(SimpleHtmlScanner.compressor("<p>\n  x\n</p>"));
    Logger.systemMark("TestSimpleHtmlScanner.js", "C14");
    check({
        caseId: "C14",
        name: "static compressor(String) one-shot",
        ok: v14 === "<p>x</p>",
        detail: "out=" + v14,
        objective: "the static convenience wraps a new scanner and compresses",
        objectiveZh: "靜態便捷方法包裝新 scanner 並壓縮",
        command: "SimpleHtmlScanner.compressor('<p>\\n  x\\n</p>')"
    });

    // C15: an empty raw-text body is kept as-is
    var html15 = "<style></style>";
    var v15 = str(new SimpleHtmlScanner(html15).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C15");
    check({
        caseId: "C15",
        name: "empty raw-text body is kept",
        ok: v15 === "<style></style>",
        detail: "out=" + v15,
        objective: "a raw-text body with only whitespace returns the original body",
        objectiveZh: "僅含空白的 raw-text 內容返回原樣",
        command: "new SimpleHtmlScanner('<style></style>').compressor()"
    });

    // C16: an unterminated tag degrades to literal text
    var html16 = "<div";
    var v16 = str(new SimpleHtmlScanner(html16).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C16");
    check({
        caseId: "C16",
        name: "unterminated '<' degrades to text",
        ok: v16 === "<div",
        detail: "out=" + v16,
        objective: "when no '>' closes the tag, the '<' is emitted and scanning continues",
        objectiveZh: "找不到閉合 > 時，< 按字面輸出並繼續掃描",
        command: "new SimpleHtmlScanner('<div').compressor()"
    });

    // C17: raw-text tags are matched case-insensitively
    var html17 = "<SCRIPT>\n  var a = 1;  \n</SCRIPT>";
    var v17 = str(new SimpleHtmlScanner(html17).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C17");
    check({
        caseId: "C17",
        name: "raw-text tags match case-insensitively",
        ok: v17.indexOf("<SCRIPT>") >= 0 && v17.indexOf("var a = 1;") >= 0 && v17.indexOf("  ") < 0,
        detail: "out=" + v17,
        objective: "tagName is lower-cased, so <SCRIPT> bodies are still minified and </SCRIPT> still closes",
        objectiveZh: "標籤名轉小寫，<SCRIPT> 內容仍被壓縮，</SCRIPT> 仍能閉合",
        command: "new SimpleHtmlScanner('<SCRIPT>\\n  var a = 1;  \\n</SCRIPT>').compressor()"
    });

    // C18: a text run inside a tag body collapses without gluing words
    var html18 = "<p>A\n  B</p>";
    var v18 = str(new SimpleHtmlScanner(html18).compressor());
    Logger.systemMark("TestSimpleHtmlScanner.js", "C18");
    check({
        caseId: "C18",
        name: "text run inside a tag body collapses",
        ok: v18 === "<p>A B</p>",
        detail: "out=" + v18,
        objective: "a whitespace run not abutting a tag becomes one space, keeping words apart",
        objectiveZh: "不緊挨標籤的空白 run 壓成一個空格，詞保持分離",
        command: "new SimpleHtmlScanner('<p>A\\n  B</p>').compressor()"
    });

} catch (e) {
    Logger.systemMark("TestSimpleHtmlScanner.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestSimpleHtmlScanner suite error",
        ok: false,
        detail: str(e.toString())
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
