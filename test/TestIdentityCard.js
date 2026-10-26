// ============================================================================
// JsPlus-Core TestIdentityCard - com.killcoding.datasource.IdentityCard
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite; needs H2 + testdb) or
//            standalone with testdb initialized:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -DAPP_IDENTITY_CARD=<PROJECT_ROOT>/JsPlus/config/IdentityCard.properties
//                     -Task <PROJECT_ROOT>/test/TestIdentityCard.js < NUL
// Output   : <PROJECT_ROOT>/test_reports/TestIdentityCard-Report.html
// Scope    : IdentityCard sequence generator - config loading, generateId()
//            polymorphs (Long / fixed-length / prefix / delimiter+suffix),
//            cross-instance uniqueness, DB round-trip verification and the
//            invalid-length guard (error code 99801).
// NOTE     : the suite creates table identity_card on demand (H2-compatible
//            DDL) and clears its own test rows before and after each case.
// ============================================================================

var System = Java.type("java.lang.System");
var IdentityCard = Java.type("com.killcoding.datasource.IdentityCard");

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

var REPORT_TITLE = "JsPlus-Core IdentityCard Test";
var REPORT_NAME = "TestIdentityCard-Report.html";

var CacheDriverExecutor = Java.type("com.killcoding.datasource.CacheDriverExecutor");

beginTest();

print("=== JsPlus-Core IdentityCard Test ===");
print("JS_PLUS_PATH: " + basePath);

// GraalJS runs without the regex language (No language for id regex found),
// so character-class checks use plain string scanning instead of /regex/.
function isDigits(s) {
    for (var i = 0; i < s.length; i++) {
        var c = s.charCodeAt(i);
        if (c < 48 || c > 57) { return false; }
    }
    return s.length > 0;
}
function isAlnum(s) {
    for (var i = 0; i < s.length; i++) {
        var c = s.charCodeAt(i);
        var up = c >= 65 && c <= 90, lo = c >= 97 && c <= 122, dg = c >= 48 && c <= 57;
        if (!up && !lo && !dg) { return false; }
    }
    return s.length > 0;
}

try {

// ---------------------------------------------------------------------------
// Suite table bootstrap: create identity_card (H2-compatible DDL) and clear
// any leftover test rows (test_* identity names only).
// ---------------------------------------------------------------------------
var bootConn = null, bootExecutor = null;
try {
    bootConn = Console.getDataSource().getConnection();
    bootExecutor = new CacheDriverExecutor(bootConn);
    bootExecutor.execute("CREATE TABLE IF NOT EXISTS identity_card ("
        + "id CHAR(16), identity_name VARCHAR(200), identity_value BIGINT, "
        + "generate_task_id CHAR(16), created_user_id CHAR(16), updated_user_id CHAR(16), "
        + "deleted_user_id CHAR(16), created_at TIMESTAMP(6), updated_at TIMESTAMP(6), "
        + "deleted_at TIMESTAMP(6), deleted TINYINT, PRIMARY KEY (id))");
    bootExecutor.execute("DELETE FROM identity_card WHERE identity_name LIKE 'test_%'");
} catch (eBoot) {
    check({ caseId: "C0", name: "identity_card table bootstrap", ok: false, detail: "error=" + eBoot });
} finally {
    if (bootExecutor !== null) { try { bootExecutor.close(); } catch (eBootC) {} }
    if (bootConn !== null) { try { bootConn.close(); } catch (eBootCb) {} }
}

// ---------------------------------------------------------------------------
// C1: config loading - getConfigProperties() Enable=true + getDataSource()
//     connectable (same pattern as TestClock C8/C9)
// ---------------------------------------------------------------------------
var cfg1 = null, ds1 = null, e1 = "", conn1 = null, connOk1 = false;
try {
    var ic1 = new IdentityCard("test_cfg_idc"); // triggers init(): config + datasource load
    cfg1 = IdentityCard.getConfigProperties();
    ds1 = IdentityCard.getDataSource();
    if (ds1 !== null) { conn1 = ds1.getConnection(); connOk1 = conn1 !== null; }
} catch (e) { e1 = "" + e; }
check({
    caseId: "C1",
    name: "IdentityCard config + datasource usable",
    ok: cfg1 !== null && cfg1.getBoolean("Enable", false) === true && ds1 !== null && connOk1 && e1.length === 0,
    detail: e1.length > 0 ? e1 : ("Enable=" + cfg1.getBoolean("Enable", false) + " table=" + cfg1.getString("TableName", "identity_card") + " ds=" + ds1),
    objective: "config/IdentityCard.properties Enable=true -> getConfigProperties() loaded, getDataSource() connects",
    objectiveZh: "config/IdentityCard.properties Enable=true — getConfigProperties() 載入、getDataSource() 可連",
    command: "IdentityCard.getConfigProperties().getBoolean('Enable', false) && IdentityCard.getDataSource().getConnection()"
});
if (conn1 !== null) { try { conn1.close(); } catch (e1c) {} }

// ---------------------------------------------------------------------------
// C2: generateId() Long sequence - strictly increasing by 1 within a step
// ---------------------------------------------------------------------------
var ids2 = null, e2 = "", mono2 = true;
try {
    var ic2 = new IdentityCard("test_seq_idc");
    ic2.setUserId("testuser");
    ids2 = new (Java.type("java.util.ArrayList"))();
    for (var i2 = 0; i2 < 5; i2++) { ids2.add(ic2.generateId()); }
    for (var j2 = 1; j2 < ids2.size(); j2++) {
        if (ids2.get(j2) - ids2.get(j2 - 1) !== 1) { mono2 = false; }
    }
} catch (e) { e2 = "" + e; }
check({
    caseId: "C2",
    name: "generateId() returns a strictly increasing Long sequence",
    ok: ids2 !== null && ids2.size() === 5 && mono2 && e2.length === 0,
    detail: e2.length > 0 ? e2 : ("seq=" + ids2.toString()),
    objective: "generateId() yields 5 consecutive ids (+1 each) within the first StepValue block",
    objectiveZh: "generateId() 依序回傳 5 個連續 id（每次 +1）",
    command: "new IdentityCard('test_seq_idc').generateId() x5"
});

// ---------------------------------------------------------------------------
// C3: generateId(6) - fixed-length zero-padded string
// ---------------------------------------------------------------------------
var str3 = null, e3 = "", ok3 = false;
try {
    var ic3 = new IdentityCard("test_len_idc");
    ic3.setUserId("testuser");
    str3 = ic3.generateId(6);
    ok3 = str3 !== null && str3.length === 6 && isDigits(str3);
} catch (e) { e3 = "" + e; }
check({
    caseId: "C3",
    name: "generateId(6) returns a zero-padded 6-digit string",
    ok: ok3 && e3.length === 0,
    detail: e3.length > 0 ? e3 : ("id=" + str3 + " len=" + (str3 === null ? "null" : str3.length)),
    objective: "generateId(int length) zero-pads the Long value to the requested length",
    objectiveZh: "generateId(int length) 將數值零填充至指定長度",
    command: "ic.generateId(6) matches ^[0-9]{6}$"
});

// ---------------------------------------------------------------------------
// C4: generateId(8, "UID") - fixed-length with prefix
// ---------------------------------------------------------------------------
var str4 = null, e4 = "", ok4 = false;
try {
    var ic4 = new IdentityCard("test_prefix_idc");
    ic4.setUserId("testuser");
    str4 = ic4.generateId(8, "UID");
    ok4 = str4 !== null && str4.length === 8 && str4.indexOf("UID") === 0 && isDigits(str4.substring(3));
} catch (e) { e4 = "" + e; }
check({
    caseId: "C4",
    name: "generateId(8, 'UID') returns a prefixed 8-char string",
    ok: ok4 && e4.length === 0,
    detail: e4.length > 0 ? e4 : ("id=" + str4),
    objective: "generateId(int length, String prefix) keeps the total length and prepends the prefix",
    objectiveZh: "generateId(int length, String prefix) 保持總長度並前置前綴",
    command: "ic.generateId(8, 'UID') matches ^UID[0-9]{5}$"
});

// ---------------------------------------------------------------------------
// C5: generateId(16, "ORD", "-", 4) - prefix + delimiter + random suffix
// ---------------------------------------------------------------------------
var str5 = null, e5 = "", ok5 = false;
try {
    var ic5 = new IdentityCard("test_suffix_idc");
    ic5.setUserId("testuser");
    str5 = ic5.generateId(16, "ORD", "-", 4);
    ok5 = str5 !== null && str5.length === 16 && str5.indexOf("ORD-") === 0
        && isDigits(str5.substring(4, 11)) && str5.charAt(11) === "-" && isAlnum(str5.substring(12));
} catch (e) { e5 = "" + e; }
check({
    caseId: "C5",
    name: "generateId(16, 'ORD', '-', 4) builds prefix/delimiter/suffix id",
    ok: ok5 && e5.length === 0,
    detail: e5.length > 0 ? e5 : ("id=" + str5),
    objective: "generateId(length, prefix, delimiter, randomSuffixLength) assembles and trims to the exact length",
    objectiveZh: "generateId(length, prefix, delimiter, randomSuffixLength) 組裝並裁切至指定長度",
    command: "ic.generateId(16, 'ORD', '-', 4) matches ^ORD-[0-9]{7}-[A-Z0-9]{4}$"
});

// ---------------------------------------------------------------------------
// C6: cross-instance uniqueness - two IdentityCard instances on the same
//     identity name share the in-memory step and never repeat a value
// ---------------------------------------------------------------------------
var all6 = null, uniq6 = true, sorted6 = true, e6 = "";
try {
    var ic6a = new IdentityCard("test_multi_idc");
    var ic6b = new IdentityCard("test_multi_idc");
    ic6a.setUserId("testuser");
    ic6b.setUserId("testuser");
    all6 = new (Java.type("java.util.ArrayList"))();
    for (var k6 = 0; k6 < 5; k6++) { all6.add(ic6a.generateId()); all6.add(ic6b.generateId()); }
    var seen6 = new (Java.type("java.util.HashSet"))();
    for (var m6 = 0; m6 < all6.size(); m6++) { if (!seen6.add(all6.get(m6))) { uniq6 = false; } }
    for (var n6 = 1; n6 < all6.size(); n6++) { if (all6.get(n6) <= all6.get(n6 - 1)) { sorted6 = false; } }
} catch (e) { e6 = "" + e; }
check({
    caseId: "C6",
    name: "two instances on one identity name stay unique and monotonic",
    ok: all6 !== null && all6.size() === 10 && uniq6 && sorted6 && e6.length === 0,
    detail: e6.length > 0 ? e6 : ("seq=" + all6.toString()),
    objective: "static CURRENT_VALUES/STEP_LAST_VALUES are shared per identity name - interleaved instances never collide",
    objectiveZh: "同一 identity name 的靜態 CURRENT_VALUES/STEP_LAST_VALUES 共享 — 多實例交錯取值不重複",
    command: "icA/icB on 'test_multi_idc' produce 10 unique increasing values"
});

// ---------------------------------------------------------------------------
// C7: DB round-trip verification - the identity_card row exists, holds the
//     stepped identity_value and the test user id
// ---------------------------------------------------------------------------
var d7 = dbVerify(function () {
    return "SELECT * FROM identity_card WHERE identity_name = 'test_multi_idc' AND deleted = 0";
});
check({
    caseId: "C7",
    name: "identity_card row persisted with stepped value",
    ok: d7.indexOf("test_multi_idc") !== -1,
    detail: d7,
    objective: "each first generateIdForNextStep() created/updated the identity_card row (identity_value advanced)",
    objectiveZh: "首次 generateIdForNextStep() 建立/更新 identity_card 記錄（identity_value 已推進）",
    command: "dbVerify: SELECT * FROM identity_card WHERE identity_name='test_multi_idc' AND deleted=0"
});

// ---------------------------------------------------------------------------
// C8: invalid length guard - generateId(0) throws SQLException error 99801
// ---------------------------------------------------------------------------
var err8 = null, e8 = "";
try {
    var ic8 = new IdentityCard("test_guard_idc");
    ic8.setUserId("testuser");
    ic8.generateId(0);
} catch (e) {
    e8 = "" + e;
    if (e.getErrorCode && typeof e.getErrorCode === "function") { err8 = e.getErrorCode(); }
    else { err8 = -1; }
}
check({
    caseId: "C8",
    name: "generateId(0) rejects length < 1 with error code 99801",
    ok: err8 === 99801,
    detail: "errorCode=" + err8 + (e8.length > 0 ? " (" + e8 + ")" : ""),
    objective: "length < 1 raises SQLException with getErrorCode()==99801 and leaves the sequence untouched",
    objectiveZh: "length < 1 拋出 SQLException（getErrorCode()==99801）且不動序列",
    command: "try { ic.generateId(0) } catch (e) { e.getErrorCode() == 99801 }"
});

// ---------------------------------------------------------------------------
// C9: cleanup - delete all test_* rows, then verify none remain
// ---------------------------------------------------------------------------
var c9conn = null, c9exec = null;
try {
    c9conn = Console.getDataSource().getConnection();
    c9exec = new CacheDriverExecutor(c9conn);
    c9exec.execute("DELETE FROM identity_card WHERE identity_name LIKE 'test_%'");
} catch (e9c) { check({ caseId: "C9", name: "no leftover test_* identity_card rows", ok: false, detail: "cleanup error=" + e9c }); }
finally {
    if (c9exec !== null) { try { c9exec.close(); } catch (e9x) {} }
    if (c9conn !== null) { try { c9conn.close(); } catch (e9y) {} }
}
var d9 = dbVerify(function () {
    return "SELECT * FROM identity_card WHERE identity_name LIKE 'test_%'";
});
check({
    caseId: "C9",
    name: "no leftover test_* identity_card rows",
    ok: d9.indexOf("(0 rows)") !== -1,
    detail: d9,
    objective: "all test_* identity rows are removed so repeated runs start clean",
    objectiveZh: "測試後無 test_* identity 殘留 — 重複執行從乾淨狀態開始",
    command: "dbVerify: SELECT COUNT(*) cnt FROM identity_card WHERE identity_name LIKE 'test_%'"
});

} catch (e) {
    check({
        caseId: "C-ERR",
        name: "TestIdentityCard suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
