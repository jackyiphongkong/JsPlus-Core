// ============================================================================
// JsPlus-Core TestJsString - GraalJS-friendly java.lang.String wrapper
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestJsString.js < NUL
// Output   : <PROJECT_ROOT>/test_reports/TestJsString-Report.html (TestBase
//            format, same as the other suites)
// Scope    : com.killcoding.jsplus.JsString - the GraalJS-friendly wrapper
//            around java.lang.String. GraalJS auto-converts every
//            java.lang.String crossing the boundary into a JS primitive (which
//            has no Java methods), so JsString wraps the real String in a
//            plain object and exposes the full public surface: valueOf /
//            format / join / copyValueOf factories, the 5 constructors and the
//            instance methods (getBytes / substring / replace / split / ...).
// ============================================================================

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

var REPORT_TITLE = "JsPlus-Core JsString Test";
var REPORT_NAME = "TestJsString-Report.html";

beginTest();

print("=== JsPlus-Core JsString Test ===");
print("JS_PLUS_PATH: " + basePath);

try {
    // C1: valueOf(Object) wraps and toString()/asString() unwrap the real String
    var s1 = JsString.valueOf("JsPlus-Core 加密");
    check({
        caseId: "C1",
        name: "valueOf(Object) wraps; toString()/asString() unwrap",
        ok: s1.toString() === "JsPlus-Core 加密" && s1.asString() === "JsPlus-Core 加密",
        detail: "toString=" + s1.toString() + " asString=" + s1.asString(),
        objective: "JsString keeps the native String callable from JS and unwraps it",
        objectiveZh: "JsString 在 JS 中保留原生 String 並可解包",
        command: "JsString.valueOf('JsPlus-Core 加密')"
    });

    // C2: numeric/boolean/char valueOf overloads render like String.valueOf
    var s2 = JsString.valueOf(42).asString();
    var s2b = JsString.valueOf(3.14).asString();
    var s2c = JsString.valueOf(true).asString();
    check({
        caseId: "C2",
        name: "valueOf numeric/boolean overloads",
        ok: s2 === "42" && s2b === "3.14" && s2c === "true",
        detail: "int=" + s2 + " double=" + s2b + " bool=" + s2c,
        objective: "primitive overloads delegate to String.valueOf",
        objectiveZh: "基本型別多載委派至 String.valueOf",
        command: "JsString.valueOf(42) / valueOf(3.14) / valueOf(true)"
    });

    // C3: String constructor wraps a JS string; wrap(null) is the null-safe entry
    // (GraalJS cannot disambiguate the constructor overloads for null, so JS
    // callers use JsString.wrap(null) - a single-Object-overload factory).
    var s3 = new JsString("hello");
    var s3b = JsString.wrap(null);
    check({
        caseId: "C3",
        name: "JsString(String) constructor; wrap(null) normalises to empty",
        ok: s3.length() === 5 && s3b.length() === 0,
        detail: "hello len=" + s3.length() + " wrap(null) len=" + s3b.length(),
        objective: "constructor accepts a JS string; wrap(null) is the unambiguous null entry",
        objectiveZh: "建構子接受 JS 字串；wrap(null) 為無歧義的 null 入口",
        command: "new JsString('hello') / JsString.wrap(null)"
    });

    // C4: getBytes("UTF-8") + byte[] constructor round-trip (the core reason JsString exists)
    var s4 = "JsPlus-Core 加密";
    var bytes4 = JsString.valueOf(s4).getBytes("UTF-8");
    var back4 = new JsString(bytes4, "UTF-8").asString();
    check({
        caseId: "C4",
        name: "getBytes('UTF-8') + byte[] constructor round-trip",
        ok: bytes4 !== null && bytes4.length > 12 && back4 === s4,
        detail: "bytes=" + bytes4.length + " back=" + back4,
        objective: "JS can encode to a real byte[] (native JS strings cannot) and rebuild",
        objectiveZh: "JS 可取真實 byte[]（原生 JS 字串做不到）並重建字串",
        command: "JsString.valueOf(s).getBytes('UTF-8') -> new JsString(bytes, 'UTF-8')"
    });

    // C5: length / isEmpty / charAt
    var s5 = JsString.valueOf("abc");
    var ch5 = s5.charAt(0);
    check({
        caseId: "C5",
        name: "length / isEmpty / charAt",
        ok: s5.length() === 3 && !s5.isEmpty() && (ch5 === "a" || ch5 === 97),
        detail: "len=" + s5.length() + " charAt(0)=" + ch5,
        objective: "basic accessors match java.lang.String semantics",
        objectiveZh: "基礎存取器符合 java.lang.String 語義",
        command: "s5.length() / s5.isEmpty() / s5.charAt(0)"
    });

    // C6: equals / equalsIgnoreCase / compareTo / compareToIgnoreCase
    // All comparison helpers now accept a raw JS string directly (String
    // overloads were added to JsString for JS interop) as well as JsString.
    var s6 = JsString.valueOf("Hello");
    check({
        caseId: "C6",
        name: "equals / equalsIgnoreCase / compareTo accept raw JS strings",
        ok: s6.equals("Hello") === true
            && s6.equalsIgnoreCase("HELLO") === true
            && s6.compareTo("Hello") === 0
            && s6.compareToIgnoreCase("HELLO") === 0
            && s6.compareTo(JsString.valueOf("Hello")) === 0,
        detail: "equals=" + s6.equals("Hello") + " ignoreCase=" + s6.equalsIgnoreCase("HELLO") + " compareTo=" + s6.compareTo("Hello"),
        objective: "String overloads let JS callers pass native strings without wrapping",
        objectiveZh: "String 多載讓 JS 呼叫方可直接傳入原生字串",
        command: "s6.equalsIgnoreCase('HELLO') / s6.compareTo('Hello')"
    });

    // C7: indexOf / lastIndexOf / contains / startsWith / endsWith
    var s7 = JsString.valueOf("JsPlus-Core engine");
    check({
        caseId: "C7",
        name: "search methods (indexOf/lastIndexOf/contains/startsWith/endsWith)",
        ok: s7.indexOf("Core") === 7 && s7.indexOf("Plus", 1) === 2 && s7.lastIndexOf("e") > 0 && s7.contains("engine") === true && s7.startsWith("JsPlus") === true && s7.endsWith("engine") === true,
        detail: "indexOf(Core)=" + s7.indexOf("Core") + " indexOf(Plus,1)=" + s7.indexOf("Plus", 1) + " contains=" + s7.contains("engine") + " endsWith=" + s7.endsWith("engine"),
        objective: "all search helpers resolve against the wrapped value",
        objectiveZh: "所有搜尋輔助方法都針對包裝值解析",
        command: "s7.indexOf('Core') / s7.indexOf('Plus', 1) / s7.endsWith('engine')"
    });

    // C8: substring / subSequence slices
    var s8 = JsString.valueOf("hello world");
    check({
        caseId: "C8",
        name: "substring / subSequence slices",
        ok: s8.substring(6).asString() === "world" && s8.substring(0, 5).asString() === "hello" && s8.subSequence(0, 5).asString() === "hello",
        detail: "substring(6)=" + s8.substring(6).asString() + " substring(0,5)=" + s8.substring(0, 5).asString(),
        objective: "slices return chained JsString instances",
        objectiveZh: "切片回傳可繼續鏈接的 JsString 實例",
        command: "s8.substring(6) / s8.subSequence(0, 5)"
    });

    // C9: replace / replaceAll / replaceFirst
    var s9 = JsString.valueOf("a-b-c");
    var s9b = JsString.valueOf("cat 1, cat 2");
    check({
        caseId: "C9",
        name: "replace / replaceAll / replaceFirst",
        ok: s9.replace("-", "+").asString() === "a+b+c"
            && s9b.replaceAll("cat", "dog").asString() === "dog 1, dog 2"
            && s9b.replaceFirst("cat", "dog").asString() === "dog 1, cat 2",
        detail: "replace=" + s9.replace("-", "+").asString() + " replaceAll=" + s9b.replaceAll("cat", "dog").asString() + " replaceFirst=" + s9b.replaceFirst("cat", "dog").asString(),
        objective: "char/substring and regex replacement are exposed",
        objectiveZh: "字元/子字串與正則替換均已暴露",
        command: "s9.replace('-', '+') / s9b.replaceAll('cat', 'dog')"
    });

    // C10: case conversion + trim/strip family
    var s10 = JsString.valueOf("  AbC  ");
    check({
        caseId: "C10",
        name: "toLowerCase/toUpperCase/trim/strip",
        ok: JsString.valueOf("AbC").toLowerCase().asString() === "abc"
            && JsString.valueOf("AbC").toUpperCase().asString() === "ABC"
            && s10.trim().asString() === "AbC"
            && s10.strip().asString() === "AbC"
            && s10.stripLeading().asString() === "AbC  "
            && s10.stripTrailing().asString() === "  AbC",
        detail: "trim=" + s10.trim().asString() + " strip=" + s10.strip().asString(),
        objective: "case and whitespace helpers use Locale.ROOT semantics",
        objectiveZh: "大小寫與空白輔助方法使用 Locale.ROOT 語義",
        command: "s10.trim().asString() / s10.stripLeading().asString()"
    });

    // C11: split / toCharArray / hashCode / intern
    var s11 = JsString.valueOf("a,b,c");
    var parts11 = s11.split(",");
    check({
        caseId: "C11",
        name: "split / toCharArray / hashCode / intern",
        ok: parts11.length === 3 && parts11[0] === "a" && s11.toCharArray().length === 5
            && s11.hashCode() !== 0 && s11.intern().asString() === "a,b,c",
        detail: "split=" + parts11.length + " chars=" + s11.toCharArray().length + " hashCode=" + s11.hashCode(),
        objective: "split returns a usable String[] and identity helpers work",
        objectiveZh: "split 回傳可用 String[] 且識別輔助方法正常",
        command: "s11.split(',') / s11.toCharArray() / s11.intern()"
    });

    // C12: format / join / copyValueOf / matches static + instance helpers
    var s12 = JsString.format("v=%d", 5);
    var s12b = JsString.join("-", "a", "b", "c");
    var s12c = JsString.copyValueOf(["x", "y", "z"]);
    check({
        caseId: "C12",
        name: "format / join / copyValueOf / matches",
        ok: s12.asString() === "v=5" && s12b.asString() === "a-b-c" && s12c.asString() === "xyz"
            && JsString.valueOf("12345").matches("\\d+") === true,
        detail: "format=" + s12.asString() + " join=" + s12b.asString() + " copyValueOf=" + s12c.asString(),
        objective: "printf-style format, join and char-array copy are exposed; matches runs regex",
        objectiveZh: "printf 格式、join 與字元陣列複製已暴露；matches 執行正則",
        command: "JsString.format('v=%d', 5) / JsString.join('-', 'a','b','c') / s.matches('\\d+')"
    });

    // C13: chained fluent calls stay JsString until asString()
    var s13 = JsString.valueOf("  hello-world  ").trim().replace("-", "_").toUpperCase();
    check({
        caseId: "C13",
        name: "fluent chaining (trim -> replace -> toUpperCase)",
        ok: s13.asString() === "HELLO_WORLD" && s13.length() === 11,
        detail: "chained=" + s13.asString() + " len=" + s13.length(),
        objective: "every transforming method returns JsString so chains keep working in JS",
        objectiveZh: "每個轉換方法都回傳 JsString，JS 中鏈式呼叫保持可用",
        command: "JsString.valueOf('  hello-world  ').trim().replace('-', '_').toUpperCase()"
    });

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestJsString suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
