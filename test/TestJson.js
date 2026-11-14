// ============================================================================
// JsPlus-Core TestJson - dependency-free JSON builder/parser suite (Json)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestJson.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestJson-Report.html
//            (TestBase format, same as the other suites)
// Scope    : com.killcoding.jsplus.Json - the replacement for
//            com.killcoding.ext.Json with no org.json / commons-text jar:
//            quote/format escaping (quotes, backslash, control chars),
//            numbers/booleans/null, Timestamp/Date/Time formatting, the fluent
//            builder (append/skipNull/skipBlank/generate), clone/toMap, and
//            parse(String[,def]) which delegates JSON.parse to the JS engine
//            (object -> LinkedHashMap, array -> ArrayList, int/long/double,
//            invalid input -> null).
// NOTE     : Every java.lang.String crossing the GraalJS boundary is wrapped
//            with com.killcoding.jsplus.JsString (project convention). The
//            rendered output depends on debugSequence(), which returns "\n" when
//            error logging is off and "" when it is on, so assertions compare
//            a newline-stripped form instead of pinning that behaviour.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var JsString = Java.type("com.killcoding.jsplus.JsString");
var ArrayList = Java.type("java.util.ArrayList");

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

var REPORT_TITLE = "JsPlus-Core Json Test";
var REPORT_NAME = "TestJson-Report.html";

beginTest();

print("=== JsPlus-Core Json Test ===");
print("JS_PLUS_PATH: " + basePath);

// Unwrap a Java String / JsString / JS value to a plain JS string.
function str(s) {
    return JsString.valueOf(s).asString();
}

// Strip the pretty-print line breaks so debugSequence() (newline vs compact)
// does not change the assertions.
function jc(s) {
    return str(s).split("\r").join("").split("\n").join("");
}

try {
    var Json = Java.type("com.killcoding.jsplus.Json");
    var Timestamp = Java.type("java.sql.Timestamp");
    var SqlDate = Java.type("java.sql.Date");
    var SqlTime = Java.type("java.sql.Time");
    var LinkedHashMap = Java.type("java.util.LinkedHashMap");

    // C1: format(Map) renders a flat object, preserving insertion order
    var m1 = new LinkedHashMap();
    m1.put("name", "JsPlus");
    m1.put("n", 3);
    Logger.systemMark("TestJson.js", "C1");
    check({
        caseId: "C1",
        name: "format(Map) renders a flat object in insertion order",
        ok: jc(Json.format(m1)) === '{"name":"JsPlus","n":3}',
        detail: "format=" + jc(Json.format(m1)),
        objective: "static format(Object) serializes a Map through quotes(Map): insertion order preserved, string quoted, number raw",
        objectiveZh: "靜態 format(Object) 經 quotes(Map) 序列化 Map：保持插入順序、字符串加引號、數字原樣",
        command: "Json.format(map)"
    });

    // C2: string escaping covers quote, backslash, control chars and CJK
    var m2 = new LinkedHashMap();
    m2.put("q", "a\"b");
    m2.put("bs", "c\\d");
    m2.put("nl", "e\nf");
    m2.put("tab", "g\th");
    m2.put("ctl", "i\u0001j");
    m2.put("cjk", "\u4e2d\u6587");
    var f2 = jc(Json.format(m2));
    Logger.systemMark("TestJson.js", "C2");
    check({
        caseId: "C2",
        name: "string escaping: quote/backslash/control chars/CJK",
        ok: f2 === '{"q":"a\\"b","bs":"c\\\\d","nl":"e\\nf","tab":"g\\th","ctl":"i\\u0001j","cjk":"\u4e2d\u6587"}',
        detail: "format=" + f2,
        objective: "escapeJson replaces quote/backslash/newline/tab with their JSON escapes, encodes chars below 0x20 as \\uXXXX and leaves CJK untouched (the old implementation relied on commons-text StringEscapeUtils)",
        objectiveZh: "escapeJson 將引號/反斜杠/換行/tab 轉義，0x20 以下用 \\uXXXX，CJK 原樣保留（舊實現依賴 commons-text StringEscapeUtils）",
        command: "Json.format(map with escaped strings)"
    });

    // C3: numbers, booleans and null render as JSON scalars
    Logger.systemMark("TestJson.js", "C3");
    check({
        caseId: "C3",
        name: "scalars: number / boolean / null",
        ok: jc(Json.format(1)) === "1"
            && jc(Json.format(1.5)) === "1.5"
            && jc(Json.format(true)) === "true"
            && jc(Json.format(null)) === "null",
        detail: "1=" + jc(Json.format(1)) + " 1.5=" + jc(Json.format(1.5))
            + " true=" + jc(Json.format(true)) + " null=" + jc(Json.format(null)),
        objective: "quotes(Object) dispatches Number/Boolean/null to their raw JSON literals",
        objectiveZh: "quotes(Object) 將 Number/Boolean/null 分派為原始 JSON 字面量",
        command: "Json.format(1|1.5|true|null)"
    });

    // C4: Timestamp / Date / Time use the Foramts patterns
    var ts = Timestamp.valueOf("2026-11-14 12:34:56.789");
    var dt = SqlDate.valueOf("2026-11-14");
    var tm = SqlTime.valueOf("12:34:56");
    var m4 = new LinkedHashMap();
    m4.put("ts", ts);
    m4.put("dt", dt);
    m4.put("tm", tm);
    Logger.systemMark("TestJson.js", "C4");
    check({
        caseId: "C4",
        name: "Timestamp/Date/Time formatted with Json.Foramts",
        ok: jc(Json.format(m4)) === '{"ts":"2026-11-14 12:34:56.789","dt":"2026-11-14","tm":"12:34:56.000"}',
        detail: "format=" + jc(Json.format(m4)),
        objective: "quote(Timestamp|Date|Time) applies Foramts.Datetime / Foramts.Date / Foramts.Time (the misspelled class name is kept on purpose so old references still compile)",
        objectiveZh: "quote(Timestamp|Date|Time) 應用 Foramts.Datetime / Foramts.Date / Foramts.Time（類名拼寫 Foramts 故意保留，舊引用仍可編譯）",
        command: "Json.format({ts,dt,tm})"
    });

    // C5: fluent builder append + generate
    var j5 = new Json();
    j5.append("name", "JsPlus").append("n", 3);
    Logger.systemMark("TestJson.js", "C5");
    check({
        caseId: "C5",
        name: "fluent builder append + generate",
        ok: jc(j5.generate()) === '{"name":"JsPlus","n":3}' && jc(j5.toString()) === jc(j5.generate()),
        detail: "generate=" + jc(j5.generate()) + " toString=" + jc(j5.toString()),
        objective: "append returns this for chaining and generate()/toString() produce the same object text",
        objectiveZh: "append 返回自身可鏈式調用，generate()/toString() 產出同一物件文本",
        command: "new Json().append(...).generate()"
    });

    // C6: skipNull drops the entry AND leaves no trailing comma
    var j6 = new Json();
    j6.skipNull(true);
    j6.append("a", null).append("b", 1).append("c", null).append("d", 2);
    Logger.systemMark("TestJson.js", "C6");
    check({
        caseId: "C6",
        name: "skipNull drops null entries without a trailing comma",
        ok: jc(j6.generate()) === '{"b":1,"d":2}',
        detail: "generate=" + jc(j6.generate()),
        objective: "the comma is driven by a 'written' flag, so a skipped middle entry can never leave a trailing/invalid comma (the index-based bug fixed in ext.Json stays fixed here)",
        objectiveZh: "逗號由 written 旗標驅動，中間項被跳過也不會留下尾逗號（ext.Json 已修的索引式 bug 在此保持修復）",
        command: "new Json().skipNull(true).append(a,null)...generate()"
    });

    // C7: skipBlank drops empty / whitespace-only strings
    var j7 = new Json();
    j7.skipBlank(true);
    j7.append("a", "").append("b", "   ").append("c", "x");
    Logger.systemMark("TestJson.js", "C7");
    check({
        caseId: "C7",
        name: "skipBlank drops empty and whitespace-only values",
        ok: jc(j7.generate()) === '{"c":"x"}',
        detail: "generate=" + jc(j7.generate()),
        objective: "skipBlank filters blank values via CommonTools.isBlank, replacing the KillCoding-Core Support.object(value).blank() dependency",
        objectiveZh: "skipBlank 經 CommonTools.isBlank 過濾空白值，替代 KillCoding-Core 的 Support.object(value).blank() 依賴",
        command: "new Json().skipBlank(true).append(a,\"\")...generate()"
    });

    // C8: clone returns an independent copy
    var j8 = new Json();
    j8.append("a", 1);
    var c8 = j8.clone();
    j8.append("b", 2);
    Logger.systemMark("TestJson.js", "C8");
    check({
        caseId: "C8",
        name: "clone() yields an independent copy",
        ok: jc(c8.generate()) === '{"a":1}' && jc(j8.generate()) === '{"a":1,"b":2}',
        detail: "clone=" + jc(c8.generate()) + " source=" + jc(j8.generate()),
        objective: "clone() copies the entries (not just the reference), so later appends on the source do not leak into the clone",
        objectiveZh: "clone() 複製條目而非引用，源對象後續 append 不會滲入副本",
        command: "new Json().append(a,1).clone()"
    });

    // C9: toMap returns a plain Map view
    var j9 = new Json();
    j9.append("a", 1).append("b", 2);
    var t9 = j9.toMap();
    Logger.systemMark("TestJson.js", "C9");
    check({
        caseId: "C9",
        name: "toMap() returns a plain Map with the same entries",
        ok: String(t9.getClass().getSimpleName()) === "HashMap" && Number(t9.size()) === 2,
        detail: "class=" + t9.getClass().getSimpleName() + " size=" + Number(t9.size()),
        objective: "toMap() hands back a java.util.HashMap copy so callers do not keep a reference to the JSON builder itself",
        objectiveZh: "toMap() 返回 java.util.HashMap 副本，調用方不會持有 JSON builder 本體",
        command: "new Json().append(...).toMap()"
    });

    // C10: parse(object) -> LinkedHashMap with correct key order and value types
    var p10 = Json.parse('{"a":1,"b":"x","c":true,"d":null,"e":1.5}');
    var keys10 = new ArrayList(p10.keySet()).toString();
    Logger.systemMark("TestJson.js", "C10");
    check({
        caseId: "C10",
        name: "parse(object) yields a key-ordered Map with typed values",
        ok: p10 !== null
            && Number(p10.size()) === 5
            && str(keys10) === "[a, b, c, d, e]"
            && Number(p10.get("a")) === 1
            && str(p10.get("b")) === "x"
            && String(p10.get("c")) === "true"
            && p10.get("d") === null
            && Number(p10.get("e")) === 1.5,
        detail: "keys=" + str(keys10) + " a=" + p10.get("a") + " b=" + str(p10.get("b"))
            + " c=" + String(p10.get("c")) + " d=" + p10.get("d") + " e=" + p10.get("e"),
        objective: "JSON.parse runs in the JS engine and the result is deep-converted on the JS side into a LinkedHashMap: key order preserved, whole numbers as Java Integer, text as Java String, Boolean, null and non-integers as Double (numeric host values surface as plain JS numbers in the test, so only the value semantics are asserted here)",
        objectiveZh: "JSON.parse 在 JS 引擎執行，結果在 JS 側深轉為 LinkedHashMap：保持鍵序、整數為 Java Integer、文本為 Java String、Boolean、null、非整數為 Double（數值型 host 值在測試中呈現為 JS number，故此處只斷言值語義）",
        objectiveZh: "JSON.parse 在 JS 引擎執行，結果在 JS 側深轉為 LinkedHashMap：保持鍵序、整數為 Integer、文本為 String、Boolean、null 與 Double",
        command: "Json.parse('{\"a\":1,...}')"
    });

    // C11: parse(array) -> ArrayList
    var p11 = Json.parse('[1,"two",false,null]');
    Logger.systemMark("TestJson.js", "C11");
    check({
        caseId: "C11",
        name: "parse(array) yields an ArrayList of typed elements",
        ok: p11 !== null
            && String(p11.getClass().getSimpleName()) === "ArrayList"
            && Number(p11.size()) === 4
            && Number(p11.get(0)) === 1
            && str(p11.get(1)) === "two"
            && String(p11.get(2)) === "false"
            && p11.get(3) === null,
        detail: "class=" + p11.getClass().getSimpleName() + " size=" + Number(p11.size()),
        objective: "array text is dispatched to the '[' branch and becomes an ArrayList, recursively converted",
        objectiveZh: "數組文本走 '[' 分支成為 ArrayList 並遞歸轉換",
        command: "Json.parse('[1,\"two\",false,null]')"
    });

    // C12: nested structures + long-range integers
    var p12 = Json.parse('{"o":{"k":[1,2]},"big":1234567890123}');
    Logger.systemMark("TestJson.js", "C12");
    check({
        caseId: "C12",
        name: "nested objects/arrays and long integers convert correctly",
        ok: p12 !== null
            && Number(p12.get("o").get("k").size()) === 2
            && Number(p12.get("o").get("k").get(1)) === 2
            && String(p12.get("big")) === "1234567890123",
        detail: "nested k size=" + Number(p12.get("o").get("k").size()) + " big=" + String(p12.get("big")),
        objective: "conversion recurses into nested maps/arrays; a whole number beyond int range is built as a Java Long on the JS side (JSON.parse itself has no bigint, so integers above 2^53 lose precision - an inherent limit of delegating to JSON.parse)",
        objectiveZh: "轉換遞歸進入嵌套 map/array，整數超出 int 範圍時選 Long 而非 Integer",
        command: "Json.parse('{\"o\":{\"k\":[1,2]},\"big\":1234567890123}')"
    });

    // C13: invalid JSON returns null instead of throwing
    Logger.systemMark("TestJson.js", "C13");
    check({
        caseId: "C13",
        name: "invalid JSON returns null (no exception)",
        ok: Json.parse("{bad}") === null && Json.parse("hello") === null && Json.parse("['x',]") === null,
        detail: "bad=" + Json.parse("{bad}") + " plain=" + Json.parse("hello") + " trailing=" + Json.parse("['x',]"),
        objective: "a JSON.parse failure is logged and swallowed, matching ext.Json which caught JSONException and returned null; text not starting with '{' or '[' returns null without touching the engine",
        objectiveZh: "JSON.parse 失敗只記錄並吞掉，與 ext.Json 捕獲 JSONException 返回 null 一致；不以 '{' 或 '[' 開頭的文本不進引擎直接返回 null",
        command: "Json.parse('{bad}') / Json.parse('hello')"
    });

    // C14: blank input falls back to the def argument
    Logger.systemMark("TestJson.js", "C14");
    check({
        caseId: "C14",
        name: "blank input uses the def argument",
        ok: Json.parse("") === null && Json.parse(null) === null && Json.parse("  ") === null
            && Json.parse("", '{"x":1}') !== null && Number(Json.parse("", '{"x":1}').get("x")) === 1,
        detail: "empty=" + Json.parse("") + " def-empty=" + Json.parse("", '{"x":1}'),
        objective: "parse(String) passes the single-space def, so blank/null input returns null; an explicit def is substituted first and then parsed",
        objectiveZh: "parse(String) 以單空格作 def，空白/null 輸入返回 null；顯式 def 先替換再解析",
        command: "Json.parse('') / Json.parse('', '{\"x\":1}')"
    });

    // C15: format -> parse round trip
    var m15 = new LinkedHashMap();
    m15.put("s", "a\"b\nc");
    m15.put("i", 7);
    var p15 = Json.parse(Json.format(m15));
    Logger.systemMark("TestJson.js", "C15");
    check({
        caseId: "C15",
        name: "format -> parse round trip preserves text and numbers",
        ok: p15 !== null && str(p15.get("s")) === "a\"b\nc" && Number(p15.get("i")) === 7,
        detail: "s=" + str(p15.get("s")).split("\n").join("\\n") + " i=" + p15.get("i"),
        objective: "the escaping written by this class is accepted by JSON.parse and decodes back to the original text, so the replacement is self-consistent",
        objectiveZh: "本類寫出的轉義能被 JSON.parse 接受並解回原文，替換實現自洽",
        command: "Json.parse(Json.format(map))"
    });

} catch (e) {
    Logger.systemMark("TestJson.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestJson suite error",
        ok: false,
        detail: str(e.toString())
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
