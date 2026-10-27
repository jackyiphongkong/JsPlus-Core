// ============================================================================
// JsPlus-Core TestEscapeTools - Java escape / unescape
// (com.killcoding.tool.CodeEscape, EscapeTools, CodeEscapeLevel)
// ----------------------------------------------------------------------------
// Scope    : Level-1 (minimal) vs Level-2 (all non-ASCII) escaping, SEC
//            escapes (\b \t \n \f \r \" \' \\), unicode \uXXXX escapes,
//            octal escapes, CSV single-line escaping, the EscapeTools facade,
//            null and empty handling.
//            NOTE: GraalJS has no regex language - assertions use indexOf/
//            charAt, never regex literals.
// ============================================================================

var System = Java.type("java.lang.System");
var CodeEscape = Java.type("com.killcoding.tool.CodeEscape");
var EscapeTools = Java.type("com.killcoding.tool.EscapeTools");
var CodeEscapeLevel = Java.type("com.killcoding.tool.CodeEscapeLevel");

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

var REPORT_TITLE = "JsPlus-Core EscapeTools Test";
var REPORT_NAME = "TestEscapeTools-Report.html";

beginTest();

print("=== JsPlus-Core EscapeTools Test ===");
print("JS_PLUS_PATH: " + basePath);

try {
    // C1: Level-1 keeps ASCII letters/digits and common punctuation
    var s1 = CodeEscape.escapeJavaMinimal("abcXYZ123._- ");
    check({
        caseId: "C1",
        name: "escapeJavaMinimal() keeps ASCII letters/digits/punct",
        ok: s1 === "abcXYZ123._- ",
        detail: "escaped=" + s1,
        objective: "level-1 leaves printable ASCII untouched",
        objectiveZh: "level-1 保留可列印 ASCII",
        command: "CodeEscape.escapeJavaMinimal('abcXYZ123._- ')"
    });

    // C2: Level-1 escapes newline and tab
    var s2 = CodeEscape.escapeJavaMinimal("a\nb\tc");
    check({
        caseId: "C2",
        name: "escapeJavaMinimal() escapes \\n and \\t",
        ok: s2 === "a\\nb\\tc",
        detail: "escaped=" + s2,
        objective: "newline -> backslash-n, tab -> backslash-t",
        objectiveZh: "換行→反斜線 n，定位→反斜線 t",
        command: "CodeEscape.escapeJavaMinimal('a\\nb\\tc')"
    });

    // C3: Level-1 escapes quote and backslash
    var s3 = CodeEscape.escapeJavaMinimal('a"b\\c');
    check({
        caseId: "C3",
        name: "escapeJavaMinimal() escapes quote and backslash",
        ok: s3 === "a\\\"b\\\\c",
        detail: "escaped=" + s3,
        objective: "double quote and backslash get SEC escapes",
        objectiveZh: "雙引號與反斜線使用 SEC 跳脫",
        command: "CodeEscape.escapeJavaMinimal('a\"b\\\\c')"
    });

    // C4: Level-2 escapes non-ASCII (Chinese) to \\uXXXX
    var s4 = CodeEscape.escapeJava("中文");
    var ok4 = s4.indexOf("\\u") === 0 && s4.indexOf("\\") === 0;
    check({
        caseId: "C4",
        name: "escapeJava() escapes non-ASCII to \\uXXXX",
        ok: ok4 && s4.length >= 12,
        detail: "escaped=" + s4,
        objective: "level-2 converts every non-ASCII codepoint to a unicode escape",
        objectiveZh: "level-2 將每個非 ASCII 字元轉為 unicode 跳脫",
        command: "CodeEscape.escapeJava('中文')"
    });

    // C5: Level-1 leaves non-ASCII untouched (only SEC escapes)
    var s5 = CodeEscape.escapeJavaMinimal("中文a");
    check({
        caseId: "C5",
        name: "escapeJavaMinimal() leaves non-ASCII untouched",
        ok: s5 === "中文a",
        detail: "escaped=" + s5,
        objective: "level-1 does NOT escape non-ASCII characters",
        objectiveZh: "level-1 不跳脫非 ASCII 字元",
        command: "CodeEscape.escapeJavaMinimal('中文a')"
    });

    // C6: unescapeJava reverses SEC escapes
    var s6 = CodeEscape.unescapeJava("a\\nb\\tc");
    check({
        caseId: "C6",
        name: "unescapeJava() reverses \\n and \\t",
        ok: s6 === "a\nb\tc",
        detail: "unescaped=" + s6,
        objective: "backslash-n/t decode back to control characters",
        objectiveZh: "反斜線 n/t 解回控制字元",
        command: "CodeEscape.unescapeJava('a\\nb\\tc')"
    });

    // C7: unescapeJava reverses \\uXXXX escapes
    var s7 = CodeEscape.unescapeJava("\\u4E2D\\u6587");
    check({
        caseId: "C7",
        name: "unescapeJava() reverses \\uXXXX to 中文",
        ok: s7 === "中文",
        detail: "unescaped=" + s7,
        objective: "unicode escapes decode to the original characters",
        objectiveZh: "unicode 跳脫解回原字元",
        command: "CodeEscape.unescapeJava('\\\\u4E2D\\\\u6587')"
    });

    // C8: Level-1 escape + unescape roundtrip
    var plain8 = "a\nb\tc\"d\\e";
    var esc8 = CodeEscape.escapeJavaMinimal(plain8);
    var back8 = CodeEscape.unescapeJava(esc8);
    check({
        caseId: "C8",
        name: "level-1 escape/unescape roundtrip",
        ok: back8 === plain8,
        detail: "escaped=" + esc8 + " back=" + back8,
        objective: "escape then unescape restores the exact input",
        objectiveZh: "先跳脫再解跳脫還原輸入",
        command: "unescapeJava(escapeJavaMinimal(plain))"
    });

    // C9: Level-2 escape + unescape roundtrip with non-ASCII
    var plain9 = "中文 mixing 123 \n and \"quotes\"";
    var esc9 = CodeEscape.escapeJava(plain9);
    var back9 = CodeEscape.unescapeJava(esc9);
    check({
        caseId: "C9",
        name: "level-2 escape/unescape roundtrip with non-ASCII",
        ok: back9 === plain9,
        detail: "escaped=" + esc9 + " back=" + back9,
        objective: "full escaping still round-trips exactly",
        objectiveZh: "完整跳脫仍可精確往返",
        command: "unescapeJava(escapeJava(plain))"
    });

    // C10: octal escape unescape (\\101 -> 'A')
    var s10 = CodeEscape.unescapeJava("\\101");
    check({
        caseId: "C10",
        name: "unescapeJava() decodes octal \\101 -> A",
        ok: s10 === "A",
        detail: "unescaped=" + s10,
        objective: "octal escapes are fully decoded",
        objectiveZh: "八進位跳脫可完整解碼",
        command: "CodeEscape.unescapeJava('\\\\101')"
    });

    // C11: escapeToSingleLineForCsv escapes comma/newline/tab
    var csv11 = CodeEscape.escapeToSingleLineForCsv("a,b\nc\td");
    var ok11 = csv11.indexOf("\\u002C") >= 0 && csv11.indexOf("\\n") >= 0
        && csv11.indexOf("\\t") >= 0;
    check({
        caseId: "C11",
        name: "escapeToSingleLineForCsv() escapes CSV-breaking chars",
        ok: ok11,
        detail: "escaped=" + csv11,
        objective: "commas become \\u002C, newline/tab become literal escapes",
        objectiveZh: "逗號→\\u002C，換行/定位→字面跳脫",
        command: "CodeEscape.escapeToSingleLineForCsv('a,b\\nc\\td')"
    });

    // C12: escapeToSingleLineForCsv(null) -> "null"
    var csv12 = CodeEscape.escapeToSingleLineForCsv(null);
    check({
        caseId: "C12",
        name: "escapeToSingleLineForCsv(null) returns 'null'",
        ok: csv12 === "null",
        detail: "result=" + csv12,
        objective: "null input maps to the literal string 'null'",
        objectiveZh: "null 輸入對應字串 'null'",
        command: "CodeEscape.escapeToSingleLineForCsv(null)"
    });

    // C13: EscapeTools facade matches CodeEscape
    var esc13 = EscapeTools.escape("a\nb", CodeEscapeLevel.LEVEL_1_BASIC_ESCAPE_SET);
    var back13 = EscapeTools.unescape(esc13);
    var csv13 = EscapeTools.escapeToSingleLineForCsv("x,y");
    check({
        caseId: "C13",
        name: "EscapeTools facade escape/unescape/csv",
        ok: esc13 === "a\\nb" && back13 === "a\nb"
            && csv13.indexOf("\\u002C") >= 0,
        detail: "escaped=" + esc13 + " back=" + back13 + " csv=" + csv13,
        objective: "EscapeTools delegates to CodeEscape/CodeEscapeUtil",
        objectiveZh: "EscapeTools 委派 CodeEscape/CodeEscapeUtil",
        command: "EscapeTools.escape(str, LEVEL_1); EscapeTools.unescape(esc)"
    });

    // C14: null input handling
    var null14a = CodeEscape.escapeJava(null);
    var null14b = CodeEscape.escapeJavaMinimal(null);
    var null14c = CodeEscape.unescapeJava(null);
    check({
        caseId: "C14",
        name: "null input returns null (escape/minimal/unescape)",
        ok: null14a === null && null14b === null && null14c === null,
        detail: "escapeJava=" + null14a + " minimal=" + null14b + " unescape=" + null14c,
        objective: "null propagates as null",
        objectiveZh: "null 輸入回傳 null",
        command: "CodeEscape.escapeJava(null)"
    });

    // C15: empty string handling
    var esc15 = CodeEscape.escapeJavaMinimal("");
    var back15 = CodeEscape.unescapeJava(esc15);
    check({
        caseId: "C15",
        name: "empty string roundtrips as empty",
        ok: esc15 === "" && back15 === "",
        detail: "escaped=[" + esc15 + "] back=[" + back15 + "]",
        objective: "empty input stays empty",
        objectiveZh: "空字串保持為空",
        command: "unescapeJava(escapeJavaMinimal(''))"
    });

    // C16: CodeEscapeLevel constants expose the expected levels
    var lv1 = CodeEscapeLevel.LEVEL_1_BASIC_ESCAPE_SET.getEscapeLevel();
    var lv2 = CodeEscapeLevel.LEVEL_2_ALL_NON_ASCII_PLUS_BASIC_ESCAPE_SET.getEscapeLevel();
    check({
        caseId: "C16",
        name: "CodeEscapeLevel levels are 1 and 2",
        ok: lv1 === 1 && lv2 === 2,
        detail: "LEVEL_1=" + lv1 + " LEVEL_2=" + lv2,
        objective: "the two public levels carry level 1 and 2",
        objectiveZh: "兩個公開等級值為 1 與 2",
        command: "CodeEscapeLevel.LEVEL_1_BASIC_ESCAPE_SET.getEscapeLevel()"
    });

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestEscapeTools suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
