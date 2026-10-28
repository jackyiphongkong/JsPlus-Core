// ============================================================================
// JsPlus-Core TestConfigProperties - typed config access (com.killcoding.tool.ConfigProperties)
// ----------------------------------------------------------------------------
// Scope    : ConfigProperties - java.util.Properties subclass with typed
//            getters (boolean/int/long/short/float/double), time-unit parsing
//            (s/m/h/d), file-size parsing (kb/mb/gb), array/map/section access,
//            fluent put/remove, null-value purge, env expansion (parseContent/
//            getEnv) and property-set comparison.
// ============================================================================

var System = Java.type("java.lang.System");
var StringReader = Java.type("java.io.StringReader");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var ArrayList = Java.type("java.util.ArrayList");
var JsString = Java.type("com.killcoding.jsplus.JsString");
var ConfigProperties = Java.type("com.killcoding.tool.ConfigProperties");

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

var REPORT_TITLE = "JsPlus-Core ConfigProperties Test";
var REPORT_NAME = "TestConfigProperties-Report.html";

beginTest();

print("=== JsPlus-Core ConfigProperties Test ===");
print("JS_PLUS_PATH: " + basePath);

var SIMPLE_PROP = "name=jsplus\nenabled=true\ncount=42\nratio=3.14\n"
    + "small=7\ntimeout=5s\nrefresh=1s\nidle=4h\nmaxsize=2gb\n"
    + "items[0]=a\nitems[1]=b\nitems[2]=c\n"
    + "ds.name=core\nds.max=100\n";

try {
    // C1: load(Reader) + getString
    var cp1 = new ConfigProperties();
    cp1.load(new StringReader(SIMPLE_PROP));
    check({
        caseId: "C1",
        name: "load(Reader) + getString()",
        ok: ("" + cp1.getString("name")) === "jsplus",
        detail: "name=" + cp1.getString("name"),
        objective: "load() parses standard key=value lines",
        objectiveZh: "load() 解析標準 key=value 行",
        command: "cp1.load(new StringReader(prop)); cp1.getString('name')"
    });

    // C2: getBoolean
    check({
        caseId: "C2",
        name: "getBoolean() parses true/false",
        ok: cp1.getBoolean("enabled") === true,
        detail: "enabled=" + cp1.getBoolean("enabled"),
        objective: "getBoolean() converts the string value",
        objectiveZh: "getBoolean() 轉換字串值",
        command: "cp1.getBoolean('enabled')"
    });

    // C3: numeric getters
    var ok3 = cp1.getInteger("count") === 42
        && ("" + cp1.getLong("count")) === "42"
        && ("" + cp1.getShort("small")) === "7"
        && Math.abs(cp1.getFloat("ratio") - 3.14) < 0.0001
        && ("" + cp1.getDouble("ratio")) === "3.14";
    check({
        caseId: "C3",
        name: "getInteger/getLong/getShort/getFloat/getDouble",
        ok: ok3,
        detail: "int=" + cp1.getInteger("count") + " long=" + cp1.getLong("count")
            + " short=" + cp1.getShort("small") + " float=" + cp1.getFloat("ratio")
            + " double=" + cp1.getDouble("ratio"),
        objective: "typed numeric getters convert the raw string",
        objectiveZh: "型別化數值 getter 轉換原始字串",
        command: "cp1.getInteger('count'); cp1.getDouble('ratio')"
    });

    // C4: getSeconds + parseTimeToSec
    var sec4 = cp1.getSeconds("timeout");
    check({
        caseId: "C4",
        name: "getSeconds() parses '5s' -> 5; parseTimeToSec('2m') -> 120",
        ok: sec4 === 5 && ConfigProperties.parseTimeToSec("2m", -1) === 120,
        detail: "timeout=" + sec4 + " 2m=" + ConfigProperties.parseTimeToSec("2m", -1),
        objective: "time units s/m/h/d convert to seconds",
        objectiveZh: "時間單位 s/m/h/d 轉為秒",
        command: "cp1.getSeconds('timeout'); ConfigProperties.parseTimeToSec('2m', -1)"
    });

    // C5: getMilliSeconds + parseTimeToMs
    var ms5 = cp1.getMilliSeconds("refresh");
    var ms5b = ConfigProperties.parseTimeToMs("2s", -1);
    check({
        caseId: "C5",
        name: "getMilliSeconds() parses '1s' -> 1000; parseTimeToMs('2s') -> 2000",
        ok: ms5 === 1000 && ms5b === 2000,
        detail: "refresh=" + ms5 + " 2s=" + ms5b,
        objective: "millisecond conversion multiplies by 1000",
        objectiveZh: "毫秒轉換乘 1000",
        command: "cp1.getMilliSeconds('refresh')"
    });

    // C6: getFileSize + parseFileSize
    var fs6 = cp1.getFileSize("maxsize", -1);
    var fs6b = ConfigProperties.parseFileSize("1mb", -1);
    check({
        caseId: "C6",
        name: "getFileSize() parses '2gb' -> 2*1024^3; '1mb' -> 1048576",
        ok: fs6 === (2 * 1024 * 1024 * 1024) && fs6b === (1024 * 1024),
        detail: "maxsize=" + fs6 + " 1mb=" + fs6b,
        objective: "file size units kb/mb/gb convert to bytes",
        objectiveZh: "檔案大小單位 kb/mb/gb 轉為位元組",
        command: "cp1.getFileSize('maxsize', -1)"
    });

    // C7: getArray (index-style keys items[0]/items[1]/items[2])
    var arr7 = cp1.getArray("items");
    var ok7 = arr7 !== null && arr7.size() === 3
        && ("" + arr7.get(0)) === "a" && ("" + arr7.get(2)) === "c";
    check({
        caseId: "C7",
        name: "getArray() collects items[0..2] into a List",
        ok: ok7,
        detail: "array=" + arr7,
        objective: "getArray(key) maps key[i] properties to a List preserving index order",
        objectiveZh: "getArray(key) 將 key[i] 屬性映射為 List 並保持索引順序",
        command: "cp1.getArray('items')"
    });

    // C8: getMap() for a section prefix
    var map8 = cp1.getMap("ds");
    var ok8 = map8 !== null && ("" + map8.get("name")) === "core"
        && ("" + map8.get("max")) === "100";
    check({
        caseId: "C8",
        name: "getMap() collects 'ds.*' entries",
        ok: ok8,
        detail: "map=" + map8,
        objective: "getMap(key) gathers every ds.* property into a Map",
        objectiveZh: "getMap(key) 收集所有 ds.* 屬性為 Map",
        command: "cp1.getMap('ds')"
    });

    // C9: getConfigProperties() returns a nested section object
    var cp9 = cp1.getConfigProperties("ds");
    var ok9 = cp9 !== null && ("" + cp9.getString("name")) === "core"
        && ("" + cp9.getString("max")) === "100";
    check({
        caseId: "C9",
        name: "getConfigProperties() returns a ConfigProperties section",
        ok: ok9,
        detail: "section.name=" + (cp9 === null ? "null" : cp9.getString("name")),
        objective: "getConfigProperties(key) wraps the section in a ConfigProperties",
        objectiveZh: "getConfigProperties(key) 將區段包成 ConfigProperties",
        command: "cp1.getConfigProperties('ds').getString('name')"
    });

    // C10: putAndReturnSelf() fluent put + getString
    var cp10 = new ConfigProperties();
    cp10.putAndReturnSelf("k1", "v1").putAndReturnSelf("k2", "v2");
    check({
        caseId: "C10",
        name: "putAndReturnSelf() chains puts",
        ok: ("" + cp10.getString("k1")) === "v1" && ("" + cp10.getString("k2")) === "v2",
        detail: "k1=" + cp10.getString("k1") + " k2=" + cp10.getString("k2"),
        objective: "putAndReturnSelf returns this for chaining",
        objectiveZh: "putAndReturnSelf 回傳自身以便串接",
        command: "cp10.putAndReturnSelf('k1','v1').putAndReturnSelf('k2','v2')"
    });

    // C11: removeAllNullValues()
    var cp11 = new ConfigProperties();
    cp11.putAndReturnSelf("a", "1");
    cp11.putAndReturnSelf("b", null);
    cp11.putAndReturnSelf("c", "3");
    cp11.removeAllNullValues();
    check({
        caseId: "C11",
        name: "removeAllNullValues() drops null-valued keys",
        ok: cp11.size() === 2 && cp11.getString("b") === null,
        detail: "size=" + cp11.size() + " b=" + cp11.getString("b"),
        objective: "null values are purged from the property set",
        objectiveZh: "值為 null 的鍵會被移除",
        command: "cp11.removeAllNullValues(); cp11.size()"
    });

    // C12: getString default-value variant
    var cp12 = new ConfigProperties();
    check({
        caseId: "C12",
        name: "getString(key, defaultValue) fallback",
        ok: ("" + cp12.getString("missing", "fallback")) === "fallback"
            && cp12.getString("missing") === null,
        detail: "withDefault=" + cp12.getString("missing", "fallback")
            + " withoutDefault=" + cp12.getString("missing"),
        objective: "default-value overload returns the default when absent",
        objectiveZh: "預設值重載在鍵不存在時回傳預設值",
        command: "cp12.getString('missing', 'fallback')"
    });

    // C13: parseContent() expands ${sys-property} references; unknown keys
    //      fall back to replaceNullChar
    var parsed13 = ConfigProperties.parseContent("path=${user.home}/app");
    var parsed13b = ConfigProperties.parseContent("x=${no_such_prop_zzz}", "?");
    check({
        caseId: "C13",
        name: "parseContent() expands ${user.home}; unknown key -> replaceNullChar",
        // parsed13 = "path=" + ${user.home} + "/app", so user.home appears
        // AFTER the "path=" prefix - indexOf(...) must be >= 0, NOT === 0
        // (String(...) converts the Java String to a JS string for indexOf).
        ok: parsed13 !== null && parsed13.indexOf(String(System.getProperty("user.home"))) >= 0
            && parsed13b === "x=?",
        detail: "parsed=" + parsed13 + " fallback=" + parsed13b,
        objective: "parseContent resolves ${key} from system properties, unknown -> replaceNullChar",
        objectiveZh: "parseContent 從系統屬性解析 ${key}，未知鍵→replaceNullChar",
        command: "ConfigProperties.parseContent('path=${user.home}/app')"
    });

    // C14: getEnv() expands an origin value with environment variables
    var origin14 = "user.home=" + System.getProperty("user.home");
    var env14 = ConfigProperties.getEnv(origin14);
    check({
        caseId: "C14",
        name: "getEnv() expands ${user.home} from system properties",
        ok: env14 !== null && env14.indexOf("user.home=") === 0
            && env14.length > origin14.indexOf("=") + 1,
        detail: "expanded=" + env14,
        objective: "getEnv expands ${sys:...}/${...} tokens",
        objectiveZh: "getEnv 展開 ${sys:...}/${...} 符記",
        command: "ConfigProperties.getEnv('user.home=${user.home}')"
    });

    // C15: parseDateTime parses a datetime string (pattern yyyy.MM.dd)
    var dt15 = ConfigProperties.parseDateTime("2026.10.24 12:00:00", null);
    check({
        caseId: "C15",
        name: "parseDateTime() parses 'yyyy.MM.dd HH:mm:ss'",
        ok: dt15 !== null,
        detail: "date=" + (dt15 === null ? "null" : dt15.toString()),
        objective: "parseDateTime converts the configured datetime (dot-separated pattern)",
        objectiveZh: "parseDateTime 轉換設定的日期時間（點分隔格式）",
        command: "ConfigProperties.parseDateTime('2026.10.24 12:00:00', null)"
    });

    // C16: compareProperties() compares two property sets (parseContentRaw set by load)
    var cp16a = new ConfigProperties();
    cp16a.load(new StringReader("x=1\ny=2\n"));
    var cp16b = new ConfigProperties();
    cp16b.load(new StringReader("x=1\ny=2\n"));
    var cp16c = new ConfigProperties();
    cp16c.load(new StringReader("x=9\ny=2\n"));
    check({
        caseId: "C16",
        name: "compareProperties() equality and difference (after load)",
        ok: ConfigProperties.compareProperties(cp16a, cp16b) === true
            && ConfigProperties.compareProperties(cp16a, cp16c) === false,
        detail: "equal=" + ConfigProperties.compareProperties(cp16a, cp16b)
            + " different=" + ConfigProperties.compareProperties(cp16a, cp16c),
        objective: "compareProperties compares the parsed raw content",
        objectiveZh: "compareProperties 比較解析後的原始內容",
        command: "ConfigProperties.compareProperties(a, b)"
    });

    // C17: load(File) end-to-end
    var tmpDir17 = Files.createTempDirectory("jsplus_cfg_").toString();
    var file17 = tmpDir17 + File.separator + "app.properties";
    Files.write(Paths.get(file17), JsString.valueOf("filekey=filevalue\n").getBytes("UTF-8"));
    var cp17 = new ConfigProperties();
    cp17.load(new File(file17));
    var ok17 = ("" + cp17.getString("filekey")) === "filevalue";
    try {
        var d17 = new File(tmpDir17);
        var fs17 = d17.listFiles();
        if (fs17 !== null) { for (var i17 = 0; i17 < fs17.length; i17++) { fs17[i17].delete(); } }
        d17.delete();
    } catch (e) {}
    check({
        caseId: "C17",
        name: "load(File) reads a properties file",
        ok: ok17,
        detail: "filekey=" + cp17.getString("filekey"),
        objective: "load(File) works end-to-end",
        objectiveZh: "load(File) 可端到端讀檔",
        command: "cp17.load(new File(path)); cp17.getString('filekey')"
    });

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestConfigProperties suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
