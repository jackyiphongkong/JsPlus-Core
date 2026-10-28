// ============================================================================
// JsPlus-Core TestCommonTools - static utilities (com.killcoding.tool.CommonTools)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestCommonTools.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestCommonTools-Report.html (TestBase
//            format, same as the other suites)
// Scope    : com.killcoding.tool.CommonTools - static helpers: isBlank,
//            generateId, md5, getHostname, averageAssign, findFiles /
//            findSubDirs, getJarPath, loadJars and getSystemProperties.
// ============================================================================

var System = Java.type("java.lang.System");
var ArrayList = Java.type("java.util.ArrayList");
var CommonTools = Java.type("com.killcoding.tool.CommonTools");

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

var REPORT_TITLE = "JsPlus-Core CommonTools Test";
var REPORT_NAME = "TestCommonTools-Report.html";

beginTest();

print("=== JsPlus-Core CommonTools Test ===");
print("JS_PLUS_PATH: " + basePath);

try {
    // C1: isBlank(null) -> true
    check({
        caseId: "C1",
        name: "isBlank(null) is true",
        ok: CommonTools.isBlank(null) === true,
        detail: "isBlank(null)=" + CommonTools.isBlank(null),
        objective: "null is treated as blank",
        objectiveZh: "null 視為空白",
        command: "CommonTools.isBlank(null)"
    });

    // C2: isBlank("") -> true
    check({
        caseId: "C2",
        name: "isBlank('') is true",
        ok: CommonTools.isBlank("") === true,
        detail: "isBlank('')=" + CommonTools.isBlank(""),
        objective: "empty string is treated as blank",
        objectiveZh: "空字串視為空白",
        command: "CommonTools.isBlank('')"
    });

    // C3: isBlank("   ") -> true (whitespace trimmed)
    check({
        caseId: "C3",
        name: "isBlank(whitespace) is true",
        ok: CommonTools.isBlank("   ") === true,
        detail: "isBlank('   ')=" + CommonTools.isBlank("   "),
        objective: "whitespace-only string is blank after trim",
        objectiveZh: "全空白字串 trim 後為空白",
        command: "CommonTools.isBlank('   ')"
    });

    // C4: isBlank("x") -> false
    check({
        caseId: "C4",
        name: "isBlank(non-empty) is false",
        ok: CommonTools.isBlank("x") === false,
        detail: "isBlank('x')=" + CommonTools.isBlank("x"),
        objective: "non-blank value is not blank",
        objectiveZh: "非空白值不為空白",
        command: "CommonTools.isBlank('x')"
    });

    // C5: generateId(8) - length and charset [0-9a-z]
    // NOTE: no regex literal here - the GraalJS engine in this runtime has no
    // regex language registered ("No language for id regex found"), so charset
    // validation uses plain char-range checks.
    var id5 = CommonTools.generateId(8);
    var charsetOk5 = (id5.length === 8);
    for (var c5i = 0; c5i < id5.length && charsetOk5; c5i++) {
        var ch5 = id5.charAt(c5i);
        if (!((ch5 >= '0' && ch5 <= '9') || (ch5 >= 'a' && ch5 <= 'z'))) {
            charsetOk5 = false;
        }
    }
    check({
        caseId: "C5",
        name: "generateId(8) returns an 8-char [0-9a-z] id",
        ok: charsetOk5,
        detail: "id=" + id5 + " len=" + id5.length,
        objective: "generateId(len) produces len chars from [0-9a-z]",
        objectiveZh: "generateId(len) 產生 len 個 [0-9a-z] 字元",
        command: "CommonTools.generateId(8)"
    });

    // C6: generateId(0) -> empty string
    var id6 = CommonTools.generateId(0);
    check({
        caseId: "C6",
        name: "generateId(0) returns an empty string",
        ok: id6.length === 0,
        detail: "len=" + id6.length,
        objective: "zero-length id is an empty string",
        objectiveZh: "零長度 id 為空字串",
        command: "CommonTools.generateId(0)"
    });

    // C7: generateId is random (two consecutive ids differ)
    var id7a = CommonTools.generateId(16);
    var id7b = CommonTools.generateId(16);
    check({
        caseId: "C7",
        name: "generateId(16) ids differ between calls (randomness)",
        ok: id7a !== id7b,
        detail: "idA=" + id7a + " idB=" + id7b,
        objective: "each call draws new random characters",
        objectiveZh: "每次呼叫隨機產生新字元",
        command: "CommonTools.generateId(16) twice"
    });

    // C8: md5("") matches the well-known empty-string digest (uppercase hex)
    var md58 = CommonTools.md5("");
    check({
        caseId: "C8",
        name: "md5('') returns D41D8CD98F00B204E9800998ECF8427E",
        ok: md58 === "D41D8CD98F00B204E9800998ECF8427E",
        detail: "md5('')=" + md58,
        objective: "MD5 of the empty string is the standard uppercase digest",
        objectiveZh: "空字串 MD5 為標準大寫摘要",
        command: "CommonTools.md5('')"
    });

    // C9: md5 deterministic + 32 uppercase hex chars (char-range check, no regex)
    var md59a = CommonTools.md5("jsplus-core");
    var md59b = CommonTools.md5("jsplus-core");
    var hexOk9 = (md59a.length === 32);
    for (var c9i = 0; c9i < md59a.length && hexOk9; c9i++) {
        var ch9 = md59a.charAt(c9i);
        if (!((ch9 >= '0' && ch9 <= '9') || (ch9 >= 'A' && ch9 <= 'F'))) {
            hexOk9 = false;
        }
    }
    check({
        caseId: "C9",
        name: "md5 is deterministic, 32 uppercase hex chars",
        ok: md59a === md59b && hexOk9,
        detail: "md5='jsplus-core'=" + md59a,
        objective: "same input -> same 32-char uppercase MD5",
        objectiveZh: "相同輸入 → 相同 32 字元大寫 MD5",
        command: "CommonTools.md5('jsplus-core') twice"
    });

    // C10: getHostname() non-null / non-empty
    var host10 = "";
    try { host10 = CommonTools.getHostname(); } catch (e) { host10 = "" + e; }
    check({
        caseId: "C10",
        name: "getHostname() returns a non-empty hostname",
        ok: host10 !== null && host10.length > 0,
        detail: "hostname=" + host10,
        objective: "hostname resolves from env or InetAddress, cached in HOSTNAME",
        objectiveZh: "從 env 或 InetAddress 解析主機名並快取於 HOSTNAME",
        command: "CommonTools.getHostname()"
    });

    // C11: averageAssign splits 10 items into ceil(10/3)=4 groups
    var src11 = new ArrayList();
    for (var i11 = 0; i11 < 10; i11++) { src11.add("item" + i11); }
    var groups11 = CommonTools.averageAssign(src11, 3);
    var total11 = 0;
    for (var g11 = 0; g11 < groups11.size(); g11++) { total11 += groups11.get(g11).size(); }
    check({
        caseId: "C11",
        name: "averageAssign(10 items, limit 3) -> 4 balanced groups, 10 total",
        ok: groups11.size() === 4 && total11 === 10,
        detail: "groups=" + groups11.size() + " total=" + total11,
        objective: "averageAssign splits source into balanced groups no larger than limit",
        objectiveZh: "averageAssign 將來源均分為不超過 limit 的組別",
        command: "CommonTools.averageAssign(list, 3)"
    });

    // C12: averageAssign(empty) -> empty list
    var groups12 = CommonTools.averageAssign(new ArrayList(), 3);
    check({
        caseId: "C12",
        name: "averageAssign(empty list) returns an empty list",
        ok: groups12 !== null && groups12.size() === 0,
        detail: "groups.size=" + groups12.size(),
        objective: "null/empty source returns an empty list",
        objectiveZh: "空來源回傳空清單",
        command: "CommonTools.averageAssign(new ArrayList(), 3)"
    });

    // C13: findFiles(dir, ext) - config folder contains .properties files
    var files13 = CommonTools.findFiles(basePath + "config", ".properties");
    check({
        caseId: "C13",
        name: "findFiles(config, '.properties') finds property files",
        ok: files13 !== null && files13.size() > 0,
        detail: "found=" + files13.size() + " first=" + (files13.size() > 0 ? files13.get(0) : "n/a"),
        objective: "findFiles(dir, ext) recursively collects matching files",
        objectiveZh: "findFiles(dir, ext) 遞迴收集符合副檔名的檔案",
        command: "CommonTools.findFiles(JS_PLUS_PATH + 'config', '.properties')"
    });

    // C14: findSubDirs() on LibHome finds at least one subdirectory
    var dirs14 = CommonTools.findSubDirs(basePath + "../LibHome");
    check({
        caseId: "C14",
        name: "findSubDirs(LibHome) finds subdirectories",
        ok: dirs14 !== null && dirs14.size() > 0,
        detail: "found=" + dirs14.size() + " first=" + (dirs14.size() > 0 ? dirs14.get(0) : "n/a"),
        objective: "findSubDirs(dir) recursively collects directories",
        objectiveZh: "findSubDirs(dir) 遞迴收集子目錄",
        command: "CommonTools.findSubDirs(JS_PLUS_PATH + '../LibHome')"
    });

    // C15: getJarPath(CommonTools.class) - non-null jar directory
    var jarPath15 = CommonTools.getJarPath(CommonTools.class);
    check({
        caseId: "C15",
        name: "getJarPath() resolves the jar directory",
        ok: jarPath15 !== null && jarPath15.length > 0,
        detail: "jarPath=" + jarPath15,
        objective: "getJarPath(clazz) returns the parent dir of the code source",
        objectiveZh: "getJarPath(clazz) 回傳程式碼來源所在目錄",
        command: "CommonTools.getJarPath(CommonTools.class)"
    });

    // C16: loadJars(libDir) builds a URLClassLoader able to load a class
    var loader16 = null, e16 = "", classFound16 = false;
    try {
        loader16 = CommonTools.loadJars(basePath + "../LibHome/jsplus/default");
        if (loader16 !== null) {
            loader16.loadClass("com.killcoding.tool.CommonTools");
            classFound16 = true;
        }
    } catch (e) { e16 = "" + e; }
    check({
        caseId: "C16",
        name: "loadJars() builds a working URLClassLoader",
        ok: loader16 !== null && classFound16 && e16.length === 0,
        detail: e16.length > 0 ? e16 : ("loader=" + loader16 + " classOk=" + classFound16),
        objective: "loadJars(dir) collects *.jar under dir into a URLClassLoader",
        objectiveZh: "loadJars(dir) 將 dir 下 *.jar 組入 URLClassLoader",
        command: "CommonTools.loadJars(JS_PLUS_PATH + '../LibHome/jsplus/default')"
    });

    // C17: getSystemProperties(clazz, envKey, propFileName) locates Logger.properties
    var propsFile17 = null;
    try { propsFile17 = CommonTools.getSystemProperties(CommonTools.class, "JS_PLUS_LOGGER_PROPS", "Logger.properties"); } catch (e) { propsFile17 = null; }
    check({
        caseId: "C17",
        name: "getSystemProperties() locates Logger.properties via JS_PLUS_PATH",
        ok: propsFile17 !== null && propsFile17.exists(),
        detail: propsFile17 !== null ? ("file=" + propsFile17.getPath()) : "file not found",
        objective: "getSystemProperties searches env -> jar dir -> JS_PLUS_PATH/config",
        objectiveZh: "getSystemProperties 依 env → jar 目錄 → JS_PLUS_PATH/config 順序搜尋",
        command: "CommonTools.getSystemProperties(CommonTools.class, ..., 'Logger.properties')"
    });

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestCommonTools suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
