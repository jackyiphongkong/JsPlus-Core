/* ************************************************************************
The MIT License (MIT)
Copyright (c) 2024 KILLCODING.COM
*****************************************************************************/
// ============================================================================
// Test suite : TestSimpleHtmlCompressor.js
// Target     : com.killcoding.tpl.SimpleHtmlCompressor - jsoup-free HTML
//              minifier that repairs unbalanced tags while it scans
//            (TestBase format, same as the other suites)
// Scope    : null source, getSource(), indentation collapse, unclosed element
//            closed at end of input, stray end tag dropped, crossing tags
//            repaired in stack order, void elements never closed, attribute
//            normalisation (lower-case name, double quotes), optional end tags
//            (li / td / tr / p / option), block element closing an open p,
//            comment removal, script/style body minification, pre kept verbatim,
//            self-closing non-void element expanded, deterministic output on
//            malformed input, quoted '>' inside attributes, DOCTYPE kept,
//            upper-case tags lower-cased, multi-level mismatch repaired.
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

var REPORT_TITLE = "JsPlus-Core SimpleHtmlCompressor Test";
var REPORT_NAME = "TestSimpleHtmlCompressor-Report.html";

beginTest();

print("=== JsPlus-Core SimpleHtmlCompressor Test ===");
print("JS_PLUS_PATH: " + basePath);

// Unwrap a Java String / JsString / JS value to a plain JS string.
function str(s) {
    return JsString.valueOf(s).asString();
}

try {
    var SimpleHtmlCompressor = Java.type("com.killcoding.tpl.SimpleHtmlCompressor");

    // C1: a null source compresses to null
    var v1 = new SimpleHtmlCompressor(null).compressor();
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C1");
    check({
        caseId: "C1",
        name: "null source compresses to null",
        ok: v1 === null || v1 === undefined,
        detail: "out=" + v1,
        objective: "compressor() returns null when the wrapped source is null",
        objectiveZh: "當被包裝源為 null 時 compressor() 返回 null",
        command: "new SimpleHtmlCompressor(null).compressor()"
    });

    // C2: getSource() returns the wrapped HTML unchanged
    var html2 = "<p>hi</p>";
    var v2 = str(new SimpleHtmlCompressor(html2).getSource());
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C2");
    check({
        caseId: "C2",
        name: "getSource() returns the wrapped HTML",
        ok: v2 === html2,
        detail: "source=" + v2,
        objective: "the constructor stores the HTML text and getSource() exposes it verbatim",
        objectiveZh: "構造器保存 HTML 文本，getSource() 原樣返回",
        command: "new SimpleHtmlCompressor('<p>hi</p>').getSource()"
    });

    // C3: indentation runs abutting a tag are dropped
    var v3 = str(SimpleHtmlCompressor.compressor("<div>\n  <p>hi</p>\n</div>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C3");
    check({
        caseId: "C3",
        name: "indentation between tags is removed",
        ok: v3 === "<div><p>hi</p></div>",
        detail: "out=" + v3,
        objective: "a line-break run abutting a tag is dropped, so nested markup joins up",
        objectiveZh: "緊挨標籤且含換行的空白 run 被刪除，嵌套標記連成一行",
        command: "SimpleHtmlCompressor.compressor('<div>\\n  <p>hi</p>\\n</div>')"
    });

    // C4: an unclosed element is closed at end of input
    var v4 = str(SimpleHtmlCompressor.compressor("<div><p>a"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C4");
    check({
        caseId: "C4",
        name: "unclosed elements are closed at end of input",
        ok: v4 === "<div><p>a</p></div>",
        detail: "out=" + v4,
        objective: "whatever is still open when input ends is closed in stack order",
        objectiveZh: "輸入結束時仍未閉合的元素按棧逆序補閉合",
        command: "SimpleHtmlCompressor.compressor('<div><p>a')"
    });

    // C5: a stray end tag is dropped
    var v5 = str(SimpleHtmlCompressor.compressor("<div>a</b></div>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C5");
    check({
        caseId: "C5",
        name: "a stray end tag is dropped",
        ok: v5 === "<div>a</div>",
        detail: "out=" + v5,
        objective: "an end tag whose element is not open cannot be honoured and is removed",
        objectiveZh: "對應元素並未打開的結束標籤無法保留，直接刪除",
        command: "SimpleHtmlCompressor.compressor('<div>a</b></div>')"
    });

    // C6: crossing tags are repaired in stack order
    var v6 = str(SimpleHtmlCompressor.compressor("<div><span>x</div>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C6");
    check({
        caseId: "C6",
        name: "crossing tags are repaired in stack order",
        ok: v6 === "<div><span>x</span></div>",
        detail: "out=" + v6,
        objective: "the parent close tag closes the inner element first, so nesting is valid again",
        objectiveZh: "父元素的結束標籤會先閉合內層元素，嵌套重新合法",
        command: "SimpleHtmlCompressor.compressor('<div><span>x</div>')"
    });

    // C7: void elements never receive a close tag and lose the trailing slash
    var v7 = str(SimpleHtmlCompressor.compressor('<div><br/><img src="x.png"/></div>'));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C7");
    check({
        caseId: "C7",
        name: "void elements are emitted without a slash and never closed",
        ok: v7 === '<div><br><img src="x.png"></div>',
        detail: "out=" + v7,
        objective: "br/img are void: no end tag, and the XML-style slash is normalised away",
        objectiveZh: "br/img 是 void 元素：不產生結束標籤，XML 式斜杠被規範掉",
        command: "SimpleHtmlCompressor.compressor('<div><br/><img src=\"x.png\"/></div>')"
    });

    // C8: attribute names are lower-cased and values double-quoted
    var v8 = str(SimpleHtmlCompressor.compressor("<DIV CLASS=top ID='m'>x</DIV>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C8");
    check({
        caseId: "C8",
        name: "attributes are normalised to lower-case names with double quotes",
        ok: v8 === '<div class="top" id="m">x</div>',
        detail: "out=" + v8,
        objective: "unquoted and single-quoted values become double-quoted, names lower-cased",
        objectiveZh: "未加引號與單引號的值統一成雙引號，屬性名轉小寫",
        command: "SimpleHtmlCompressor.compressor('<DIV CLASS=top ID=\"m\">x</DIV>')"
    });

    // C9: optional end tags are implied by the next list item
    var v9 = str(SimpleHtmlCompressor.compressor("<ul><li>a<li>b</ul>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C9");
    check({
        caseId: "C9",
        name: "a new <li> closes the previous <li>",
        ok: v9 === "<ul><li>a</li><li>b</li></ul>",
        detail: "out=" + v9,
        objective: "li has an optional end tag; the next li supersedes it",
        objectiveZh: "li 的結束標籤可省略，下一個 li 自動取代它",
        command: "SimpleHtmlCompressor.compressor('<ul><li>a<li>b</ul>')"
    });

    // C10: table cells and rows use the same supersession rule
    var v10 = str(SimpleHtmlCompressor.compressor("<table><tr><td>a<td>b</table>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C10");
    check({
        caseId: "C10",
        name: "table rows and cells are closed by their successors",
        ok: v10 === "<table><tr><td>a</td><td>b</td></tr></table>",
        detail: "out=" + v10,
        objective: "td/tr carry optional end tags, so the parent close tag supplies the missing ones",
        objectiveZh: "td/tr 的結束標籤可省略，父元素結束標籤補上缺的閉合",
        command: "SimpleHtmlCompressor.compressor('<table><tr><td>a<td>b</table>')"
    });

    // C11: a block element closes an open <p>
    var v11 = str(SimpleHtmlCompressor.compressor("<p>a<div>b</div>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C11");
    check({
        caseId: "C11",
        name: "a block element closes an open <p>",
        ok: v11 === "<p>a</p><div>b</div>",
        detail: "out=" + v11,
        objective: "HTML5 closes p before a block start tag - a div may not nest inside p",
        objectiveZh: "按 HTML5，塊級開始標籤前先閉合 p - div 不能嵌在 p 內",
        command: "SimpleHtmlCompressor.compressor('<p>a<div>b</div>')"
    });

    // C12: comments are removed
    var v12 = str(SimpleHtmlCompressor.compressor("<div><!-- note -->x</div>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C12");
    check({
        caseId: "C12",
        name: "comments are removed",
        ok: v12 === "<div>x</div>",
        detail: "out=" + v12,
        objective: "comments carry no rendering effect and are part of the minification",
        objectiveZh: "註釋不影響渲染，屬於壓縮範圍",
        command: "SimpleHtmlCompressor.compressor('<div><!-- note -->x</div>')"
    });

    // C13: a script body goes through the JS compressor
    var html13 = "<script>\n  var a = 1;\n  var b = 2;\n</script>";
    var v13 = str(SimpleHtmlCompressor.compressor(html13));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C13");
    check({
        caseId: "C13",
        name: "script bodies are minified and closed",
        ok: v13.indexOf("<script>var a = 1") === 0 && v13.indexOf("</script>") > 0,
        detail: "out=" + v13,
        objective: "script is raw text: the body is minified as JS, not whitespace-collapsed",
        objectiveZh: "script 屬 raw text：體內按 JS 壓縮，不做空白折疊",
        command: "SimpleHtmlCompressor.compressor('<script>var a = 1;</script>')"
    });

    // C14: a style body goes through the CSS compressor
    var html14 = "<style>\n  .a { color : red ; }\n</style>";
    var v14 = str(SimpleHtmlCompressor.compressor(html14));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C14");
    check({
        caseId: "C14",
        name: "style bodies are minified and closed",
        ok: v14.indexOf("<style>") === 0 && v14.indexOf("color") > 0 && v14.indexOf("</style>") > 0,
        detail: "out=" + v14,
        objective: "style is raw text: the body runs through the CSS compressor",
        objectiveZh: "style 屬 raw text：體內走 CSS 壓縮器",
        command: "SimpleHtmlCompressor.compressor('<style>.a{color:red}</style>')"
    });

    // C15: pre keeps its body verbatim
    var html15 = "<pre>  a\n  b</pre>";
    var v15 = str(SimpleHtmlCompressor.compressor(html15));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C15");
    check({
        caseId: "C15",
        name: "pre bodies are kept verbatim",
        ok: v15 === html15,
        detail: "out=" + v15,
        objective: "pre is raw text whose whitespace is significant, so nothing is collapsed",
        objectiveZh: "pre 的空白有語義，屬 raw text，不做任何折疊",
        command: "SimpleHtmlCompressor.compressor('<pre>  a\\n  b</pre>')"
    });

    // C16: a self-closing non-void element becomes an explicit empty element
    var v16 = str(SimpleHtmlCompressor.compressor("<div/>x"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C16");
    check({
        caseId: "C16",
        name: "self-closing non-void elements become empty element pairs",
        ok: v16 === "<div></div>x",
        detail: "out=" + v16,
        objective: "HTML has no self-closing syntax for div; the pair form is the valid equivalent",
        objectiveZh: "HTML 對 div 沒有自閉合語法，等價寫法是成對標籤",
        command: "SimpleHtmlCompressor.compressor('<div/>x')"
    });

    // C17: the same malformed input produces the same output every time
    var html17 = "<p>a<div><span>b</p></span><li>c";
    var v17a = str(SimpleHtmlCompressor.compressor(html17));
    var v17b = str(SimpleHtmlCompressor.compressor(html17));
    var v17c = str(new SimpleHtmlCompressor(html17).compressor());
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C17");
    check({
        caseId: "C17",
        name: "malformed input is repaired deterministically",
        ok: v17a === v17b && v17b === v17c,
        detail: "a=" + v17a + " b=" + v17b + " c=" + v17c,
        objective: "no external state, no randomness: the same broken HTML is always broken the same way",
        objectiveZh: "無外部狀態、無隨機性：同一份爛 HTML 每次都爛成一致結果",
        command: "SimpleHtmlCompressor.compressor(brokenHtml) 三次"
    });

    // C18: a quoted '>' inside an attribute does not end the tag
    var html18 = '<a title="a>b" href="#">x</a>';
    var v18 = str(SimpleHtmlCompressor.compressor(html18));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C18");
    check({
        caseId: "C18",
        name: "a quoted '>' inside an attribute stays inside the tag",
        ok: v18 === '<a title="a>b" href="#">x</a>',
        detail: "out=" + v18,
        objective: "findTagEnd honours quoting, so '>' in a value is not mistaken for the tag end",
        objectiveZh: "findTagEnd 尊重引號，屬性值中的 '>' 不會被誤判為標籤結束",
        command: 'SimpleHtmlCompressor.compressor(\'<a title="a>b" href="#">x</a>\')'
    });

    // C19: DOCTYPE is preserved and upper-case tags are lower-cased
    var v19 = str(SimpleHtmlCompressor.compressor("<!DOCTYPE html><HTML><BODY>x</BODY></HTML>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C19");
    check({
        caseId: "C19",
        name: "DOCTYPE is kept and upper-case tags are lower-cased",
        ok: v19 === "<!DOCTYPE html><html><body>x</body></html>",
        detail: "out=" + v19,
        objective: "declarations pass through untouched, element names normalise to lower case",
        objectiveZh: "聲明原樣通過，元素名規範為小寫",
        command: "SimpleHtmlCompressor.compressor('<!DOCTYPE html><HTML><BODY>x</BODY></HTML>')"
    });

    // C20: a multi-level mismatch is repaired to a balanced tree
    var v20 = str(SimpleHtmlCompressor.compressor("<a><b><c></a></b>"));
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C20");
    check({
        caseId: "C20",
        name: "a multi-level mismatch still yields balanced markup",
        ok: v20 === "<a><b><c></c></b></a>",
        detail: "out=" + v20,
        objective: "closing an outer element closes every inner one in stack order; the extra end tag is dropped",
        objectiveZh: "閉合外層時按棧序閉合全部內層，多出的結束標籤被丟棄",
        command: "SimpleHtmlCompressor.compressor('<a><b><c></a></b>')"
    });

} catch (e) {
    Logger.systemMark("TestSimpleHtmlCompressor.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestSimpleHtmlCompressor suite error",
        ok: false,
        detail: str(e.toString())
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
