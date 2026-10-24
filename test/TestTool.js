// ============================================================================
// JsPlus-Core TestTool - tool package test suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestTool.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/test_reports/TestTool-Report.html / .md
// Coverage : com.killcoding.tool
//            - Base64Tools: encodeString/decodeString + byte[] encode/decode
//            - CipherTools: AES / DES / 3DES encrypt+decrypt roundtrips
//            - CodeEscape: escapeJava/unescapeJava, escapeJavaMinimal, CSV line
//            - EscapeTools: escape/unescape roundtrip
//            - CommonTools: isBlank / md5 / generateId
//            - ConfigProperties: put/get typed getters + load(File)
//            - ResultMap: put/get/size
//            - XmlParser: root name + path navigation
// NOTE     : No RegExp literals (GraalVM 25 without the regex polyglot language
//            throws "No language for id regex found") - string checks only.
// ============================================================================

var System = Java.type("java.lang.System");
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
beginTest();

var REPORT_NAME = "TestTool-Report.html";
var REPORT_TITLE = "JsPlus-Core Tool Test Report";

var Base64Tools = Java.type("com.killcoding.tool.Base64Tools");
var CipherTools = Java.type("com.killcoding.tool.CipherTools");
var CodeEscape = Java.type("com.killcoding.tool.CodeEscape");
var EscapeTools = Java.type("com.killcoding.tool.EscapeTools");
var CommonTools = Java.type("com.killcoding.tool.CommonTools");
var ConfigProperties = Java.type("com.killcoding.tool.ConfigProperties");
var ResultMap = Java.type("com.killcoding.tool.ResultMap");
var XmlParser = Java.type("com.killcoding.tool.XmlParser");
var JString = Java.type("java.lang.String");

// NOTE: in GraalJS a java.lang.String result is auto-mapped to a JS string, so
// JS strings have no .getBytes(). These helpers bridge JS string <-> UTF-8
// byte[] through the JDK charset stream API (deterministic, no polyglot magic).
var BAOS = Java.type("java.io.ByteArrayOutputStream");
var OSW = Java.type("java.io.OutputStreamWriter");
var BIS = Java.type("java.io.ByteArrayInputStream");
var ISR = Java.type("java.io.InputStreamReader");
function toBytes(s) {
    var bos = new BAOS();
    var w = new OSW(bos, "UTF-8");
    w.write(s);
    w.flush();
    return bos.toByteArray();
}
function fromBytes(b) {
    var r = new ISR(new BIS(b), "UTF-8");
    var out = "";
    var c = r.read();
    while (c !== -1) {
        out += String.fromCharCode(c);
        c = r.read();
    }
    return out;
}

print("=== JsPlus-Core Tool Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));

// Case C1
var b64 = Base64Tools.encodeString("Hello Base64 世界");
var b64dec = Base64Tools.decodeString(b64);
check({
    name: "Base64Tools encodeString/decodeString roundtrip",
    ok: b64dec.indexOf("Hello Base64 世界") >= 0,
    detail: "enc=" + b64 + " | dec=" + b64dec,
    objective: "Verify Base64 string roundtrip preserves UTF-8 text",
    objectiveZh: "驗證 Base64 字串往返保留 UTF-8 文字",
    command: "Base64Tools.encodeString(s); Base64Tools.decodeString(s)"
});

// Case C2
var raw2 = toBytes("byte-roundtrip");
var chars2 = Base64Tools.encode(raw2);
var b64s2 = ("" + JString.valueOf(chars2));
var decBytes2 = Base64Tools.decode(b64s2);
var decStr2 = fromBytes(decBytes2);
check({
    name: "Base64Tools encode(byte[]) / decode(String)",
    ok: decStr2 === "byte-roundtrip",
    detail: "b64=" + b64s2 + " | dec=" + decStr2,
    objective: "Verify byte[] Base64 encode/decode roundtrip",
    objectiveZh: "驗證 byte[] Base64 編解碼往返",
    command: "Base64Tools.encode(byte[]); Base64Tools.decode(String)"
});

// Case C3
var aesEnc = CipherTools.AESEncrypt("my-aes-key-123", toBytes("AES 測試"));
var aesDec = fromBytes(CipherTools.AESDecrypt("my-aes-key-123", aesEnc));
check({
    name: "CipherTools AESEncrypt/AESDecrypt roundtrip",
    ok: aesDec.indexOf("AES 測試") >= 0,
    detail: "dec=" + aesDec,
    objective: "Verify AES encrypt/decrypt roundtrip (SHA-256 derived key)",
    objectiveZh: "驗證 AES 加解密往返（SHA-256 派生金鑰）",
    command: "CipherTools.AESEncrypt(keyText, data); CipherTools.AESDecrypt(keyText, enc)"
});

// Case C4
var desEnc = CipherTools.DESEncrypt("12345678", "12345678", toBytes("DES data"));
var desDec = fromBytes(CipherTools.DESDecrypt("12345678", "12345678", desEnc));
check({
    name: "CipherTools DESEncrypt/DESDecrypt roundtrip",
    ok: desDec === "DES data",
    detail: "dec=" + desDec,
    objective: "Verify DES encrypt/decrypt roundtrip (CBC)",
    objectiveZh: "驗證 DES 加解密往返（CBC）",
    command: "CipherTools.DESEncrypt(keyText, ivText, data); CipherTools.DESDecrypt(keyText, ivText, enc)"
});

// Case C5
var tdesEnc = CipherTools._3DESEncrypt("123456789012345678901234", "12345678", toBytes("3DES data"));
var tdesDec = fromBytes(CipherTools._3DESDecrypt("123456789012345678901234", "12345678", tdesEnc));
check({
    name: "CipherTools _3DESEncrypt/_3DESDecrypt roundtrip",
    ok: tdesDec === "3DES data",
    detail: "dec=" + tdesDec,
    objective: "Verify 3DES (DESede) encrypt/decrypt roundtrip (CBC)",
    objectiveZh: "驗證 3DES（DESede）加解密往返（CBC）",
    command: "CipherTools._3DESEncrypt(keyText, ivText, data); CipherTools._3DESDecrypt(keyText, ivText, enc)"
});

// Case C6
var escSrc = "a\"b\\c\nd";
var esc = CodeEscape.escapeJava(escSrc);
var unesc = CodeEscape.unescapeJava(esc);
check({
    name: "CodeEscape.escapeJava/unescapeJava roundtrip",
    ok: unesc === escSrc,
    detail: "escaped=" + esc + " | unescaped=" + unesc,
    objective: "Verify Java-string escaping roundtrip (quotes, backslash, newline)",
    objectiveZh: "驗證 Java 字串跳脫往返（引號、反斜線、換行）",
    command: "CodeEscape.escapeJava(text); CodeEscape.unescapeJava(text)"
});

// Case C7
var min = CodeEscape.escapeJavaMinimal("x\"y");
check({
    name: "CodeEscape.escapeJavaMinimal escapes quotes",
    ok: min.indexOf("\\\"") >= 0,
    detail: "min=" + min,
    objective: "Verify minimal escaping quotes a double-quote",
    objectiveZh: "驗證最小跳脫會跳脫雙引號",
    command: "CodeEscape.escapeJavaMinimal(text)"
});

// Case C8
var csv = CodeEscape.escapeToSingleLineForCsv("a\nb\r\nc");
check({
    name: "CodeEscape.escapeToSingleLineForCsv removes newlines",
    ok: csv.indexOf("\n") < 0 && csv.indexOf("\r") < 0,
    detail: "csv=[" + csv + "]",
    objective: "Verify CSV single-line escaping removes CR/LF",
    objectiveZh: "驗證 CSV 單行跳脫移除 CR/LF",
    command: "CodeEscape.escapeToSingleLineForCsv(text)"
});

// Case C9
var CodeEscapeLevel = Java.type("com.killcoding.tool.CodeEscapeLevel");
var etEsc = EscapeTools.escape("a\nb", CodeEscapeLevel.LEVEL_1_BASIC_ESCAPE_SET);
var etUn = EscapeTools.unescape(etEsc);
check({
    name: "EscapeTools.escape/unescape roundtrip",
    ok: etUn === "a\nb",
    detail: "escaped=" + etEsc + " | unescaped=" + etUn,
    objective: "Verify EscapeTools escape/unescape roundtrip (LEVEL_1 basic escape set)",
    objectiveZh: "驗證 EscapeTools 跳脫/還原往返（LEVEL_1 基本跳脫集）",
    command: "EscapeTools.escape(str, CodeEscapeLevel.LEVEL_1_BASIC_ESCAPE_SET); EscapeTools.unescape(str)"
});

// Case C10
check({
    name: "CommonTools.isBlank('') true / isBlank('x') false",
    ok: CommonTools.isBlank("") && CommonTools.isBlank(null) && !CommonTools.isBlank("x"),
    detail: "isBlank('')=" + CommonTools.isBlank("") + ", isBlank(null)=" + CommonTools.isBlank(null) + ", isBlank('x')=" + CommonTools.isBlank("x"),
    objective: "Verify isBlank() for empty, null and non-empty inputs",
    objectiveZh: "驗證 isBlank() 對空值、null 與非空輸入的判斷",
    command: "CommonTools.isBlank(o)"
});

// Case C11
var md5abc = CommonTools.md5("abc");
check({
    name: "CommonTools.md5('abc') known digest",
    ok: md5abc === "900150983CD24FB0D6963F7D28E17F72",
    detail: "md5=" + md5abc,
    objective: "Verify md5() produces the well-known uppercase hex digest",
    objectiveZh: "驗證 md5() 產生已知的大寫十六進位摘要",
    command: "CommonTools.md5(\"abc\")"
});

// Case C12
var gid = CommonTools.generateId(8);
check({
    name: "CommonTools.generateId(8).length === 8",
    ok: gid.length === 8,
    detail: "id=" + gid,
    objective: "Verify generateId(len) returns a string of the requested length",
    objectiveZh: "驗證 generateId(len) 回傳指定長度字串",
    command: "CommonTools.generateId(8)"
});

// Case C13
var cp = new ConfigProperties();
cp.putAndReturnSlef("strKey", "strVal");
cp.putAndReturnSlef("intKey", 42);
cp.putAndReturnSlef("boolKey", true);
check({
    name: "ConfigProperties put/getString/getInteger/getBoolean",
    ok: cp.getString("strKey") === "strVal"
        && cp.getInteger("intKey", 0) === 42
        && cp.getBoolean("boolKey") === true,
    detail: "str=" + cp.getString("strKey") + ", int=" + cp.getInteger("intKey", 0) + ", bool=" + cp.getBoolean("boolKey"),
    objective: "Verify typed getters return the stored values",
    objectiveZh: "驗證型別化 getter 回傳已存值",
    command: "cp.putAndReturnSlef(key, value); cp.getString(key)"
});

// Case C14
check({
    name: "ConfigProperties default values",
    ok: cp.getString("missing", "def") === "def" && cp.getInteger("missing2", 7) === 7,
    detail: "str default=" + cp.getString("missing", "def") + ", int default=" + cp.getInteger("missing2", 7),
    objective: "Verify typed getters fall back to defaults for missing keys",
    objectiveZh: "驗證型別化 getter 對缺失鍵回退預設值",
    command: "cp.getString(key, default); cp.getInteger(key, default)"
});

// Case C15
var cpF = new DiskFile(basePath + "../test/.core-test-tmp/tool/cp.properties");
cpF.write("loadKey=loadValue\nloadInt=99", "UTF-8");
var cp2 = new ConfigProperties();
var cp2Loaded = true;
try {
    cp2.load(cpF.getOrigin());
} catch (e) {
    cp2Loaded = false;
}
check({
    name: "ConfigProperties.load(File)",
    ok: cp2Loaded && cp2.getString("loadKey") === "loadValue" && cp2.getInteger("loadInt", 0) === 99,
    detail: "loadKey=" + cp2.getString("loadKey") + ", loadInt=" + cp2.getInteger("loadInt", 0),
    objective: "Verify load(File) parses a properties file",
    objectiveZh: "驗證 load(File) 解析 properties 檔案",
    command: "cp.load(file)"
});
if (cpF.exists()) {
    cpF.delete();
}

// Case C16
var rm = new ResultMap();
rm.put("rk", "rv");
check({
    name: "ResultMap put/get/size",
    ok: ("rv" === ("" + rm.get("rk"))) && rm.size() === 1,
    detail: "get=" + rm.get("rk") + ", size=" + rm.size(),
    objective: "Verify ResultMap basic map operations",
    objectiveZh: "驗證 ResultMap 基本 Map 操作",
    command: "new ResultMap().put(k, v)"
});

// Case C17
var xp = null;
var xpErr = null;
try {
    xp = new XmlParser("<root><user><name>KILLCODING</name><age>30</age></user></root>");
} catch (e) {
    xpErr = "" + e;
}
check({
    name: "XmlParser root name",
    ok: xp !== null && xp.getRootName() === "root",
    detail: xpErr === null ? ("root=" + xp.getRootName()) : xpErr,
    objective: "Verify XmlParser parses a document and exposes the root name",
    objectiveZh: "驗證 XmlParser 解析文件並暴露根節點名稱",
    command: "new XmlParser(xml).getRootName()"
});

// Case C18
check({
    name: "XmlParser path navigation",
    ok: xp !== null && xp.path("user.name") === "KILLCODING" && ("" + xp.path("user.age")) === "30",
    detail: xpErr === null ? ("user.name=" + xp.path("user.name") + ", user.age=" + xp.path("user.age")) : xpErr,
    objective: "Verify dot-path navigation reaches nested element text",
    objectiveZh: "驗證點路徑導覽可到達巢狀元素文字",
    command: "xp.path(\"user.name\")"
});

writeReport();
writeMd();
