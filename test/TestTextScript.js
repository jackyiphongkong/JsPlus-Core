// ============================================================================
// JsPlus-Core TestTextScript - text-script loader API suite (TextScript)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestTextScript.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestTextScript-Report.html
//            (TestBase format, same as the other suites)
// Scope    : TextScript(String|File) constructors, parse() of the '#'
//            directive syntax (plain text, #comment, #{} expression,
//            #if/#else/#end, #for, #code, #block/#end), addParams variable
//            injection, useEngine/getContext/evalScript, setPrefix/setVarName,
//            parse(true) function-script view, null-input guard.
// NOTE     : Every java.lang.String crossing the GraalJS boundary is wrapped
//            with com.killcoding.jsplus.JsString (project convention) before
//            JS-level comparisons / regex ops; line-break characters depend on
//            the host OS (\r\n on Windows), so text assertions normalize \r
//            away first.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var File = Java.type("java.io.File");
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

var REPORT_TITLE = "JsPlus-Core TextScript Test";
var REPORT_NAME = "TestTextScript-Report.html";

beginTest();

print("=== JsPlus-Core TextScript Test ===");
print("JS_PLUS_PATH: " + basePath);

// Normalize CRLF -> LF so assertions are platform-independent. The input may
// be a Java String from TextScript.parse(): unwrap it via JsString first.
// NOTE: no regex literal here - the GraalVM JS engine in this LibHome ships
// without the TRegex language, so any /.../ literal fails at parse time with
// "No language for id regex found". split()/join() avoid that entirely.
function norm(s) {
    var str = JsString.valueOf(s).asString();
    return str.split("\r").join("");
}

// Unwrap a Java String / JsString / JS value to a plain JS string.
function str(s) {
    return JsString.valueOf(s).asString();
}

try {
    var TextScript = Java.type("com.killcoding.jsplus.TextScript");
    var DiskFile = Java.type("com.killcoding.file.DiskFile");

    // C1: default constructor - prefix / varName defaults
    // (allowAllAccess was a TextScript instance flag with no consumer once the
    // pool concept took over - removed; the security flag now lives on
    // TextScriptConsole.isAllowAllAccess(), default true)
    var ts1 = new TextScript();
    Logger.systemMark("TestTextScript.js", "C1");
    check({
        caseId: "C1",
        name: "TextScript() default constructor exposes defaults",
        ok: str(ts1.getPrefix()) === "#" && str(ts1.getVarName()) === "theContent",
        detail: "prefix=" + str(ts1.getPrefix()) + " varName=" + str(ts1.getVarName()),
        objective: "empty constructor sets the documented defaults (prefix '#', varName 'theContent'); the allowAllAccess instance flag was removed with the pool takeover - it now lives on TextScriptConsole (default true)",
        objectiveZh: "無參構造設定文件化預設值（prefix '#、varName theContent）；allowAllAccess 實例旗標隨 pool 接管已廢除——現由 TextScriptConsole 持有（默認 true）",
        command: "new TextScript(); getPrefix()/getVarName()"
    });

    // C2: plain single-line text parses back verbatim
    var ts2 = new TextScript("Hello World");
    var v2 = norm(ts2.parse());
    Logger.systemMark("TestTextScript.js", "C2");
    check({
        caseId: "C2",
        name: "plain text line parses verbatim",
        ok: v2 === "Hello World",
        detail: "parse=" + v2,
        objective: "a line with no directive is emitted as literal content",
        objectiveZh: "無指令行以原文內容輸出",
        command: "new TextScript('Hello World').parse()"
    });

    // C3: multi-line text joins lines with a newline
    var ts3 = new TextScript("Line1\nLine2");
    var v3 = norm(ts3.parse());
    Logger.systemMark("TestTextScript.js", "C3");
    check({
        caseId: "C3",
        name: "multi-line text joins lines with line break",
        ok: v3 === "Line1\nLine2",
        detail: "parse=" + v3,
        objective: "consecutive plain lines are joined by a line-break append",
        objectiveZh: "連續普通行以換行連接",
        command: "new TextScript('Line1\\nLine2').parse()"
    });

    // C4: #comment lines are skipped
    var ts4 = new TextScript("#comment ignored line\nVisible");
    var v4 = norm(ts4.parse());
    Logger.systemMark("TestTextScript.js", "C4");
    check({
        caseId: "C4",
        name: "#comment lines are skipped",
        ok: v4 === "Visible",
        detail: "parse=" + v4,
        objective: "lines matching '#comment ...' are dropped from the output",
        objectiveZh: "符合 '#comment ...' 的行自輸出中移除",
        command: "new TextScript('#comment ignored\\nVisible').parse()"
    });

    // C5: #{...} expression is evaluated inline
    var ts5 = new TextScript("#{(1+1)}");
    var v5 = norm(ts5.parse());
    Logger.systemMark("TestTextScript.js", "C5");
    check({
        caseId: "C5",
        name: "#{expression} is evaluated inline",
        ok: v5 === "2",
        detail: "parse=" + v5,
        objective: "the #{...} token is replaced by the evaluated JS expression",
        objectiveZh: "#{...} 標記以 JS 表達式求值結果替換",
        command: "new TextScript('#{(1+1)}').parse()"
    });

    // C6: literal text and #{...} expression on the same line
    var ts6 = new TextScript("Hi #{(1+1)}");
    var v6 = norm(ts6.parse());
    Logger.systemMark("TestTextScript.js", "C6");
    check({
        caseId: "C6",
        name: "mixed literal text + #{expression} on one line",
        ok: v6 === "Hi 2",
        detail: "parse=" + v6,
        objective: "literal segments around #{...} are preserved and concatenated with the evaluated result",
        objectiveZh: "#{...} 前後的文字與求值結果拼接",
        command: "new TextScript('Hi #{(1+1)}').parse()"
    });

    // C7: #if(true) branch is taken (block content keeps its trailing line
    //     break because #end is a following line - by design)
    var ts7 = new TextScript("#if(1===1)\nyes\n#end");
    var v7 = norm(ts7.parse());
    Logger.systemMark("TestTextScript.js", "C7");
    check({
        caseId: "C7",
        name: "#if true branch emits its body",
        ok: v7 === "yes\n",
        detail: "parse=" + v7,
        objective: "a true #if(...) block appends the body lines to the content",
        objectiveZh: "#if(...) 為真時輸出區塊內行",
        command: "new TextScript('#if(1===1)\\nyes\\n#end').parse()"
    });

    // C8: #if(false) falls through to #else
    var ts8 = new TextScript("#if(1===2)\nno\n#else\nyes\n#end");
    var v8 = norm(ts8.parse());
    Logger.systemMark("TestTextScript.js", "C8");
    check({
        caseId: "C8",
        name: "#if false falls through to #else",
        ok: v8 === "yes\n",
        detail: "parse=" + v8,
        objective: "a false #if(...) skips its body and emits the #else body instead",
        objectiveZh: "#if(...) 為假時跳過本體、改輸出 #else 本體",
        command: "new TextScript('#if(1===2)\\nno\\n#else\\nyes\\n#end').parse()"
    });

    // C9: #for loop repeats its body (each iteration appends the line break)
    var ts9 = new TextScript("#for(var i=0;i<3;i++)\n#{i}\n#end");
    var v9 = norm(ts9.parse());
    Logger.systemMark("TestTextScript.js", "C9");
    check({
        caseId: "C9",
        name: "#for loop repeats the body",
        ok: v9 === "0\n1\n2\n",
        detail: "parse=" + v9,
        objective: "a #for(...) block executes its body once per iteration, appending each line",
        objectiveZh: "#for(...) 區塊每次迭代執行本體並輸出",
        command: "new TextScript('#for(var i=0;i<3;i++)\\n#{i}\\n#end').parse()"
    });

    // C10: addParams injects variables into the engine context
    var ts10 = new TextScript("Hello #{name}");
    ts10.addParams({ name: "JsPlus" });
    var v10 = norm(ts10.parse());
    Logger.systemMark("TestTextScript.js", "C10");
    check({
        caseId: "C10",
        name: "addParams injects a variable used by #{...}",
        ok: v10 === "Hello JsPlus",
        detail: "parse=" + v10,
        objective: "addParams puts keys into the engine context so #{name} resolves",
        objectiveZh: "addParams 將鍵寫入引擎上下文，供 #{name} 解析",
        command: "new TextScript('Hello #{name}').addParams({name:'JsPlus'}).parse()"
    });

    // C11: getContext returns a live context; evalScript evaluates raw JS
    var ts11 = new TextScript();
    var ctx = ts11.getContext();
    var v11 = str(ts11.evalScript("1+1"));
    Logger.systemMark("TestTextScript.js", "C11");
    check({
        caseId: "C11",
        name: "getContext live + evalScript raw JS",
        ok: ctx !== null && ctx !== undefined && v11 === "2",
        detail: "context=" + (ctx === null ? "null" : "" + ctx.getClass().getSimpleName()) + " evalScript(1+1)=" + v11,
        objective: "getContext() lazily initializes the default engine; evalScript evaluates raw JS source",
        objectiveZh: "getContext() 延遲初始化預設引擎；evalScript 求值原生 JS 源碼",
        command: "new TextScript().getContext(); evalScript('1+1')"
    });

    // C12: setVarName renames the accumulator variable
    var ts12 = new TextScript("Hi");
    ts12.setVarName("buf");
    var v12 = norm(ts12.parse());
    Logger.systemMark("TestTextScript.js", "C12");
    check({
        caseId: "C12",
        name: "setVarName renames the accumulator variable",
        ok: str(ts12.getVarName()) === "buf" && v12 === "Hi",
        detail: "varName=" + str(ts12.getVarName()) + " parse=" + v12,
        objective: "setVarName switches the internal accumulator name; parsing still works",
        objectiveZh: "setVarName 切換內部累加變量名，解析仍正常",
        command: "ts.setVarName('buf'); ts.parse()"
    });

    // C13: setPrefix switches the directive marker (parenthesised expression -
    //      an unparenthesised @{1+1} string-concatenates to "11" by design)
    var ts13 = new TextScript("@comment skip\n@{(2*3)}");
    ts13.setPrefix("@");
    var v13 = norm(ts13.parse());
    Logger.systemMark("TestTextScript.js", "C13");
    check({
        caseId: "C13",
        name: "setPrefix switches the directive marker",
        ok: str(ts13.getPrefix()) === "@" && v13 === "6",
        detail: "prefix=" + str(ts13.getPrefix()) + " parse=" + v13,
        objective: "setPrefix changes the directive char; @comment is skipped and @{(...)} evaluates",
        objectiveZh: "setPrefix 更換指令符；@comment 跳過、@{(...)} 求值",
        command: "ts.setPrefix('@'); ts.parse()"
    });

    // C14: parse(true) returns the generated function script without running it
    var ts14 = new TextScript("Hi");
    var v14 = str(ts14.parse(true));
    Logger.systemMark("TestTextScript.js", "C14");
    check({
        caseId: "C14",
        name: "parse(true) returns the generated function script",
        ok: v14.indexOf("function") >= 0 && v14.indexOf("theContent") >= 0 && v14.indexOf("Hi") >= 0,
        detail: "script=" + v14,
        objective: "parse(true) exposes the wrapped function source instead of evaluating it",
        objectiveZh: "parse(true) 返回包裝函數源碼而非執行",
        command: "new TextScript('Hi').parse(true)"
    });

    // C15: TextScript(File) constructor reads and parses a script file
    var tsDataDir = basePath + "../testdata/textscript";
    new DiskFile(tsDataDir).mkdirs();
    var tsFile = tsDataDir + "/sample.txt";
    new DiskFile(tsFile).write("FileText #{(2*3)}", "UTF-8");
    var ts15 = new TextScript(new File(tsFile));
    var v15 = norm(ts15.parse());
    var delOk = new File(tsFile).delete();
    Logger.systemMark("TestTextScript.js", "C15");
    check({
        caseId: "C15",
        name: "TextScript(File) reads and parses a script file",
        ok: v15 === "FileText 6" && delOk,
        detail: "parse=" + v15 + " fileDeleted=" + delOk,
        objective: "the File constructor reads the script bytes and parses them like the String constructor",
        objectiveZh: "File 構造讀取腳本位元組並如 String 構造般解析",
        command: "new TextScript(new File(path)).parse()"
    });

    // C16: null input is rejected with NullPointerException (constructor)
    var npeCaught = false;
    var npeMsg = "";
    try {
        new TextScript(null);
    } catch (e) {
        npeCaught = true;
        npeMsg = str(e.toString());
    }
    Logger.systemMark("TestTextScript.js", "C16");
    check({
        caseId: "C16",
        name: "null constructor input throws NullPointerException",
        ok: npeCaught,
        detail: "npeCaught=" + npeCaught + " msg=" + npeMsg,
        objective: "a null script string is rejected up front instead of failing later",
        objectiveZh: "null 腳本字符串前置拒絕，而非稍後失敗",
        command: "new TextScript(null)"
    });

    // C17: #code emits raw JS source that later #{...} can use
    var ts17 = new TextScript("#code var c = 40;\n#{(c+2)}");
    var v17 = norm(ts17.parse());
    Logger.systemMark("TestTextScript.js", "C17");
    check({
        caseId: "C17",
        name: "#code raw JS feeds a later #{expression}",
        ok: v17 === "42",
        detail: "parse=" + v17,
        objective: "a #code line is emitted verbatim into the generated function, so later expressions see its variables",
        objectiveZh: "#code 行原樣寫入生成函數，後續表達式可見其變量",
        command: "new TextScript('#code var c = 40;\\n#{(c+2)}').parse()"
    });

    // C18: #block ... #end emits its body verbatim as prelude code
    var ts18 = new TextScript("#block\nvar b = 10;\n#end\n#{(b*2)}");
    var v18 = norm(ts18.parse());
    Logger.systemMark("TestTextScript.js", "C18");
    check({
        caseId: "C18",
        name: "#block/#end prelude code feeds a later #{expression}",
        ok: v18 === "20",
        detail: "parse=" + v18,
        objective: "a #block section is placed before the content accumulator so its declarations are visible",
        objectiveZh: "#block 區塊置於內容累加器之前，其宣告對後續可見",
        command: "new TextScript('#block\\nvar b = 10;\\n#end\\n#{(b*2)}').parse()"
    });

    // C19: complex HTML template - real page tags interleaved with every
    //      directive kind at once: #{...} inline (two expressions on the
    //      same line), #comment, #code array definition, nested
    //      #if/#for/#end over real markup, and addParams string params.
    //      The template is written as a literal backtick block (no
    //      array-join string building) so the page reads as real markup.
    var ts19 = new TextScript(`<html>
<head>
<title>#{appName} - #{pageName}</title>
</head>
<body>
#comment rendered body below
#code var itemNames = ['A', 'B', 'C'];
#if(showMenu)
<nav>
#for(var i=0;i<3;i++)
<li>#{itemNames[i]}</li>
#end
</nav>
#end
#if(showFooter)
<footer>#{copyright}</footer>
#end
</body>
</html>`);
    ts19.addParams({
        appName: "JsPlus",
        pageName: "Home",
        showMenu: true,
        showFooter: true,
        copyright: "(c) 2026"
    });
    var v19 = norm(ts19.parse());
    var exp19 = `<html>
<head>
<title>JsPlus - Home</title>
</head>
<body>
<nav>
<li>A</li>
<li>B</li>
<li>C</li>
</nav>
<footer>(c) 2026</footer>
</body>
</html>`;
    Logger.systemMark("TestTextScript.js", "C19");
    check({
        caseId: "C19",
        name: "complex HTML template renders the full page",
        ok: v19 === exp19,
        detail: "parse=" + v19,
        objective: "a realistic HTML page with #comment/#code/#if/#for/#{} and addParams renders the expected markup, with no directive tokens leaking through",
        objectiveZh: "含 #comment/#code/#if/#for/#{} 與 addParams 的完整 HTML 頁面渲染出預期標記，無指令殘留",
        command: "new TextScript(`<html>...`).addParams({...}).parse()"
    });

} catch (e) {
    Logger.systemMark("TestTextScript.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestTextScript suite error",
        ok: false,
        detail: str(e.toString())
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
