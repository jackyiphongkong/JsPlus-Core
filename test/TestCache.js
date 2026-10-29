// ============================================================================
// JsPlus-Core TestCache - static cache API suite (Cache + StoredCache)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestCache.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestCache-Report.html (TestBase
//            format, same as the other suites)
// Scope    : Cache.set(name,value[,life]) / get / included / remove / removeAll
//            / getNames + StoredCache.set/get/remove. Covers the 2026-10-28
//            cache fixes: life=0 means never-expire (3-arg overload, bug B),
//            life>24.8d no longer overflows int math into immediate expiry
//            (bug A), removeAll/getNames lazy-init the api (bug C), null value
//            removes, negative life skips caching.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");

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

var REPORT_TITLE = "JsPlus-Core Cache Test";
var REPORT_NAME = "TestCache-Report.html";

beginTest();

print("=== JsPlus-Core Cache Test ===");
print("JS_PLUS_PATH: " + basePath);

var Cache = Java.type("com.killcoding.cache.Cache");
var StoredCache = Java.type("com.killcoding.cache.StoredCache");

try {
    // Clean the shared static cache before asserting (other suites may have used it).
    Cache.removeAll();

    // C1: set(name, value) + get roundtrip (2-arg = never expire)
    Cache.set("tc-key1", "cache-v1");
    var v1 = Cache.get("tc-key1");
    Logger.systemMark("TestCache.js", "C1");
    check({
        caseId: "C1",
        name: "Cache.set(name,value) + get roundtrip",
        ok: v1 === "cache-v1",
        detail: "value=" + v1,
        objective: "2-arg set stores the value and get returns it (life=0, never expire)",
        objectiveZh: "兩參 set 儲存值且 get 可取回（life=0 永久）",
        command: "Cache.set('tc-key1','cache-v1'); Cache.get('tc-key1')"
    });

    // C2: set(name, value, 0) - 3-arg overload keeps life=0 = never expire (bug B fix)
    Cache.set("tc-key2", "cache-v2", 0);
    var v2 = Cache.get("tc-key2");
    Logger.systemMark("TestCache.js", "C2");
    check({
        caseId: "C2",
        name: "Cache.set(name,value,0) means never expire",
        ok: v2 === "cache-v2",
        detail: "value=" + v2,
        objective: "3-arg set with life=0 must behave like the 2-arg overload (never expire), not silently drop",
        objectiveZh: "三參 set 的 life=0 應等同兩參（永久），而非靜默丟棄",
        command: "Cache.set('tc-key2','cache-v2',0); Cache.get('tc-key2')"
    });

    // C3: included true -> false
    var in1 = Cache.included("tc-key1");
    var in2 = Cache.included("tc-no-such-key");
    Logger.systemMark("TestCache.js", "C3");
    check({
        caseId: "C3",
        name: "Cache.included detects presence",
        ok: in1 === true && in2 === false,
        detail: "included(existing)=" + in1 + " included(missing)=" + in2,
        objective: "included returns true for a stored key and false for an absent one",
        objectiveZh: "included 對已存鍵回 true、對缺鍵回 false",
        command: "Cache.included(name)"
    });

    // C4: set with a short life actually expires (cleaner thread + get check)
    Cache.set("tc-key4", "cache-v4", 1);
    var v4a = Cache.get("tc-key4");
    Thread.sleep(1300);
    var v4b = Cache.get("tc-key4");
    Logger.systemMark("TestCache.js", "C4");
    check({
        caseId: "C4",
        name: "Cache.set(name,value,1) expires after ~1s",
        ok: v4a === "cache-v4" && v4b === null,
        detail: "beforeExpiry=" + v4a + " after1300ms=" + v4b,
        objective: "a positive life in seconds expires the entry (checked via get after sleep)",
        objectiveZh: "正數 life 秒數令條目過期（sleep 後 get 驗證）",
        command: "Cache.set('tc-key4','cache-v4',1); sleep(1300); Cache.get('tc-key4')"
    });

    // C5: large life (> 24.8 days) must NOT overflow int math into immediate expiry (bug A fix)
    Cache.set("tc-key5", "cache-v5", 31536000); // 365 days
    var v5 = Cache.get("tc-key5");
    Logger.systemMark("TestCache.js", "C5");
    check({
        caseId: "C5",
        name: "Cache.set with 365-day life does not expire immediately",
        ok: v5 === "cache-v5",
        detail: "value=" + v5,
        objective: "lifeSeconds*1000L long-multiply keeps large life values valid (was int overflow -> instant expiry)",
        objectiveZh: "lifeSeconds*1000L 長乘法令大值 life 有效（原 int 溢位會即時過期）",
        command: "Cache.set('tc-key5','cache-v5',31536000); Cache.get('tc-key5')"
    });

    // C6: negative life skips caching
    Cache.set("tc-key6", "cache-v6", -1);
    var v6 = Cache.get("tc-key6");
    Logger.systemMark("TestCache.js", "C6");
    check({
        caseId: "C6",
        name: "Cache.set with negative life skips caching",
        ok: v6 === null,
        detail: "value=" + v6,
        objective: "life < 0 must not store anything (skip-use-cache semantics)",
        objectiveZh: "life<0 不儲存任何內容（skip-use-cache 語義）",
        command: "Cache.set('tc-key6','cache-v6',-1); Cache.get('tc-key6')"
    });

    // C7: null value removes the entry
    Cache.set("tc-key7", "cache-v7");
    Cache.set("tc-key7", null);
    var v7 = Cache.get("tc-key7");
    var in7 = Cache.included("tc-key7");
    Logger.systemMark("TestCache.js", "C7");
    check({
        caseId: "C7",
        name: "Cache.set(name, null) removes the entry",
        ok: v7 === null && in7 === false,
        detail: "value=" + v7 + " included=" + in7,
        objective: "a null value is treated as remove (delete semantics)",
        objectiveZh: "null 值視為刪除（remove 語義）",
        command: "Cache.set('tc-key7',null); Cache.get('tc-key7')"
    });

    // C8: remove deletes the entry
    Cache.set("tc-key8", "cache-v8");
    Cache.remove("tc-key8");
    var v8 = Cache.get("tc-key8");
    var in8 = Cache.included("tc-key8");
    Logger.systemMark("TestCache.js", "C8");
    check({
        caseId: "C8",
        name: "Cache.remove deletes the entry",
        ok: v8 === null && in8 === false,
        detail: "value=" + v8 + " included=" + in8,
        objective: "remove drops the stored value and the life record",
        objectiveZh: "remove 移除已存值與生命記錄",
        command: "Cache.remove('tc-key8')"
    });

    // C9: getNames lists stored keys (also exercises the lazy-init path, bug C fix)
    Cache.set("tc-key9", "cache-v9");
    var names = Cache.getNames();
    var has9 = false;
    for (var ni = 0; ni < names.size(); ni++) {
        if (names.get(ni) === "tc-key9") { has9 = true; }
    }
    Logger.systemMark("TestCache.js", "C9");
    check({
        caseId: "C9",
        name: "Cache.getNames returns the stored keys",
        ok: has9 === true,
        detail: "names=" + names,
        objective: "getNames exposes the key set without NPE (lazy api init)",
        objectiveZh: "getNames 返回鍵集合且不 NPE（api 懶初始化）",
        command: "Cache.getNames()"
    });

    // C10: removeAll clears every entry
    Cache.removeAll();
    var v10 = Cache.get("tc-key9");
    var names10 = Cache.getNames();
    Logger.systemMark("TestCache.js", "C10");
    check({
        caseId: "C10",
        name: "Cache.removeAll clears every entry",
        ok: v10 === null && names10.size() === 0,
        detail: "value=" + v10 + " names.size=" + names10.size(),
        objective: "removeAll wipes all cached values and keys",
        objectiveZh: "removeAll 清空所有快取值與鍵",
        command: "Cache.removeAll()"
    });

    // C11: StoredCache set/get/remove roundtrip (permanent storage mode)
    StoredCache.removeAll();
    StoredCache.set("sc-key1", "stored-v1");
    var sv1 = StoredCache.get("sc-key1");
    StoredCache.remove("sc-key1");
    var sv2 = StoredCache.get("sc-key1");
    Logger.systemMark("TestCache.js", "C11");
    check({
        caseId: "C11",
        name: "StoredCache.set/get/remove roundtrip",
        ok: sv1 === "stored-v1" && sv2 === null,
        detail: "beforeRemove=" + sv1 + " afterRemove=" + sv2,
        objective: "StoredCache persists until manual remove (stored mode, Integer.MAX_VALUE life path)",
        objectiveZh: "StoredCache 持久化至手動移除（stored 模式、Integer.MAX_VALUE life 路徑）",
        command: "StoredCache.set('sc-key1','stored-v1'); StoredCache.get('sc-key1'); StoredCache.remove('sc-key1')"
    });

    StoredCache.removeAll();
    Cache.removeAll();

} catch (e) {
    Logger.systemMark("TestCache.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestCache suite error",
        ok: false,
        detail: "" + e
    });
}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
