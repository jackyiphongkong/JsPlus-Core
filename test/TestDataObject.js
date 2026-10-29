// ============================================================================
// JsPlus-Core TestDataObject - encrypted cache value (com.killcoding.cache.DataObject)
// ----------------------------------------------------------------------------
// Scope    : DataObject serializes any Serializable object to a file, encrypted
//            with AES (auto-generated or caller key); blank key disables
//            encryption. Covers: default-key roundtrip, custom key roundtrip,
//            no-encryption mode, file persistence, missing-file error and
//            nested structure roundtrip.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var HashMap = Java.type("java.util.HashMap");
var ArrayList = Java.type("java.util.ArrayList");
var DataObject = Java.type("com.killcoding.cache.DataObject");

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

var REPORT_TITLE = "JsPlus-Core DataObject Test";
var REPORT_NAME = "TestDataObject-Report.html";

beginTest();

print("=== JsPlus-Core DataObject Test ===");
print("JS_PLUS_PATH: " + basePath);

// Unique temp directory for this run (cleaned up at the end)
var tmpRoot = Files.createTempDirectory("jsplus_dobj_").toString();
var tmpFile = function (name) {
    return tmpRoot + File.separator + name;
};

try {
    // C1: default-key roundtrip - a HashMap survives setObject/getData
    var m1 = new HashMap();
    m1.put("name", "jsplus");
    m1.put("age", 42);
    m1.put("active", true);
    var path1 = tmpFile("c1.dat");
    var d1 = new DataObject(path1);
    d1.setObject(m1);
    var back1 = d1.getData();
    Logger.systemMark("TestDataObject.js", "C1");
    check({
        caseId: "C1",
        name: "DataObject(path) roundtrip with default AES key",
        ok: back1 !== null && ("" + back1.get("name")) === "jsplus"
            && ("" + back1.get("age")) === "42" && ("" + back1.get("active")) === "true",
        detail: "name=" + back1.get("name") + " age=" + back1.get("age")
            + " active=" + back1.get("active"),
        objective: "setObject() serializes+AES-encrypts; getData() decrypts+deserializes",
        objectiveZh: "setObject() 序列化+AES 加密；getData() 解密+反序列化",
        command: "new DataObject(path).setObject(map); new DataObject(path).getData()"
    });

    // C2: custom AES key roundtrip
    var path2 = tmpFile("c2.dat");
    var d2 = new DataObject("0123456789abcdef0123456789abcdef", path2);
    d2.setObject("hello-custom-key");
    var back2 = d2.getData();
    Logger.systemMark("TestDataObject.js", "C2");
    check({
        caseId: "C2",
        name: "DataObject(aesKey, path) roundtrip with a custom key",
        ok: back2 !== null && ("" + back2) === "hello-custom-key",
        detail: "value=" + back2,
        objective: "explicit AES key encrypts/decrypts consistently",
        objectiveZh: "自訂 AES 金鑰可一致地加解密",
        command: "new DataObject(key, path).setObject('hello-custom-key')"
    });

    // C3: blank key disables encryption (plain roundtrip still works)
    var path3 = tmpFile("c3.dat");
    var d3 = new DataObject("", path3);
    d3.setObject("plain-mode");
    var back3 = d3.getData();
    Logger.systemMark("TestDataObject.js", "C3");
    check({
        caseId: "C3",
        name: "blank aesKey disables encryption but roundtrip works",
        ok: back3 !== null && ("" + back3) === "plain-mode",
        detail: "value=" + back3,
        objective: "blank key -> useEncrypt=false, plain serialize+deserialize",
        objectiveZh: "空白金鑰 → useEncrypt=false，純序列化",
        command: "new DataObject('', path).setObject('plain-mode')"
    });

    // C4: the file is actually persisted on disk
    var path4 = tmpFile("c4.dat");
    var d4 = new DataObject(path4);
    d4.setObject("persisted");
    var exists4 = Files.exists(Paths.get(path4));
    var size4 = Files.size(Paths.get(path4));
    Logger.systemMark("TestDataObject.js", "C4");
    check({
        caseId: "C4",
        name: "setObject() writes the encrypted file to disk",
        ok: exists4 && size4 > 0,
        detail: "exists=" + exists4 + " size=" + size4,
        objective: "setObject() creates the target file (parent dirs auto-created)",
        objectiveZh: "setObject() 建立目標檔（父目錄自動建立）",
        command: "Files.exists(Paths.get(path))"
    });

    // C5: reading a missing file throws
    var path5 = tmpFile("does_not_exist.dat");
    var threw5 = false;
    try {
        var d5 = new DataObject(path5);
        d5.getData();
    } catch (e5) {
        threw5 = true;
    }
    Logger.systemMark("TestDataObject.js", "C5");
    check({
        caseId: "C5",
        name: "getData() on a missing file throws",
        ok: threw5,
        detail: "threw=" + threw5,
        objective: "missing file -> Files.readAllBytes throws (surfaced to caller)",
        objectiveZh: "檔案不存在 → 拋出例外（交由呼叫方處理）",
        command: "new DataObject(missing).getData()"
    });

    // C6: nested structure roundtrip (list of maps)
    var inner = new ArrayList();
    var i1 = new HashMap(); i1.put("id", 1); i1.put("tag", "a");
    var i2 = new HashMap(); i2.put("id", 2); i2.put("tag", "b");
    inner.add(i1); inner.add(i2);
    var path6 = tmpFile("c6.dat");
    var d6 = new DataObject(path6);
    d6.setObject(inner);
    var back6 = d6.getData();
    var ok6 = false;
    if (back6 !== null && back6.size() === 2) {
        ok6 = ("" + back6.get(0).get("tag")) === "a" && ("" + back6.get(1).get("tag")) === "b";
    }
    Logger.systemMark("TestDataObject.js", "C6");
    check({
        caseId: "C6",
        name: "nested List<Map> roundtrip",
        ok: ok6,
        detail: "listSize=" + (back6 === null ? "null" : back6.size()),
        objective: "deep object graphs survive serialization+encryption",
        objectiveZh: "深層物件結構可完整往返",
        command: "setObject(List<Map>); getData()"
    });

    // C7: overwrite semantics - the latest setObject wins
    var path7 = tmpFile("c7.dat");
    var d7 = new DataObject(path7);
    d7.setObject("first");
    d7.setObject("second");
    var back7 = d7.getData();
    Logger.systemMark("TestDataObject.js", "C7");
    check({
        caseId: "C7",
        name: "re-setObject() overwrites the previous value",
        ok: back7 !== null && ("" + back7) === "second",
        detail: "value=" + back7,
        objective: "setObject() replaces the file content",
        objectiveZh: "setObject() 覆寫先前內容",
        command: "d7.setObject('first'); d7.setObject('second'); d7.getData()"
    });

} catch (e) {
    Logger.systemMark("TestDataObject.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestDataObject suite error",
        ok: false,
        detail: "" + e
    });
}

// Clean up the temp directory
try {
    var dir7 = new File(tmpRoot);
    var files7 = dir7.listFiles();
    if (files7 !== null) {
        for (var i7 = 0; i7 < files7.length; i7++) { files7[i7].delete(); }
    }
    dir7.delete();
} catch (e) {}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
