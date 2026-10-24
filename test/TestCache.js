// ============================================================================
// JsPlus-Core TestCache - cache package test suite (console mode)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/TestCache.js))
// Harness  : Shared plumbing lives in TestBase.js and is loaded below.
// Output   : <PROJECT_ROOT>/test_reports/TestCache-Report.html / .md
// Coverage : com.killcoding.cache
//            - Cache (static facade, default MemoryCache): set/get/included/
//              getNames/remove/removeAll + TTL expiry
//            - StoredCache (permanent storage facade): set/get/included/remove
//            - CacheArray (async processing queue): key management semantics.
//              NOTE: by design ("Need class CacheArrayFilter to use") add()
//              stores into StoredCache AND the attached filter worker consumes
//              items (removes them from StoredCache) shortly after enqueue.
//              So value-level get() assertions are non-deterministic here; the
//              suite deterministically verifies key management (add returns a
//              key, size, getCacheKey/getCacheKeys, remove, removeAll). A
//              60s timer is attached purely to satisfy the filter precondition.
// NOTE     : Keys are prefixed with a random suffix so repeated runs never
//            collide with leftover entries from a previous run.
// ============================================================================

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
beginTest();

var REPORT_NAME = "TestCache-Report.html";
var REPORT_TITLE = "JsPlus-Core Cache Test Report";

var Cache = Java.type("com.killcoding.cache.Cache");
var StoredCache = Java.type("com.killcoding.cache.StoredCache");
var CacheArray = Java.type("com.killcoding.cache.CacheArray");
var CacheArrayFilter = Java.type("com.killcoding.cache.CacheArrayFilter");
var CommonTools = Java.type("com.killcoding.tool.CommonTools");

print("=== JsPlus-Core Cache Test ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));

var uid = CommonTools.generateId(8);
var k1 = "tc_k1_" + uid;
var k2 = "tc_k2_" + uid;

// Case C1
Cache.set(k1, "value-1");
Cache.set(k2, "value-2");
check({
    name: "Cache.set/get roundtrip",
    ok: ("value-1" === ("" + Cache.get(k1))) && ("value-2" === ("" + Cache.get(k2))),
    detail: "get(" + k1 + ")=" + Cache.get(k1) + ", get(" + k2 + ")=" + Cache.get(k2),
    objective: "Verify the static Cache facade stores and retrieves values (MemoryCache)",
    objectiveZh: "驗證靜態 Cache 外觀（MemoryCache）可存入並取出值",
    command: "Cache.set(name, value); Cache.get(name)"
});

// Case C2
check({
    name: "Cache.included true after set / false for unknown key",
    ok: Cache.included(k1) && !Cache.included("tc_missing_" + uid),
    detail: "included(" + k1 + ")=" + Cache.included(k1) + ", included(missing)=" + Cache.included("tc_missing_" + uid),
    objective: "Verify included() reflects existing keys and rejects unknown keys",
    objectiveZh: "驗證 included() 正確反映已存在與不存在的鍵",
    command: "Cache.included(name)"
});

// Case C3
var names = Cache.getNames();
var found = false;
for (var ni = 0; ni < names.size(); ni++) {
    if (("" + names.get(ni)) === k1) {
        found = true;
        break;
    }
}
check({
    name: "Cache.getNames contains key",
    ok: found,
    detail: "names.size()=" + names.size() + ", contains " + k1 + "=" + found,
    objective: "Verify getNames() lists stored cache keys",
    objectiveZh: "驗證 getNames() 列出已存取的快取鍵",
    command: "Cache.getNames()"
});

// Case C4
Cache.remove(k1);
check({
    name: "Cache.remove",
    ok: !Cache.included(k1),
    detail: "included after remove=" + Cache.included(k1),
    objective: "Verify remove() deletes a single key",
    objectiveZh: "驗證 remove() 刪除單一鍵",
    command: "Cache.remove(name)"
});

// Case C5
var kExp = "tc_exp_" + uid;
Cache.set(kExp, "expiring", 1);
var stillThere = Cache.included(kExp);
Thread.sleep(1600);
var expired = !Cache.included(kExp);
check({
    name: "Cache TTL expiry (life=1s, re-checked after 1.6s)",
    ok: stillThere && expired,
    detail: "immediately=" + stillThere + ", after 1.6s=" + !expired,
    objective: "Verify TTL expiry removes a cache entry after its life seconds",
    objectiveZh: "驗證 TTL 到期後（life 秒）快取項目被移除",
    command: "Cache.set(name, value, 1); Thread.sleep(1600); Cache.included(name)"
});

// Case C6
Cache.set("tc_ra1_" + uid, "a");
Cache.set("tc_ra2_" + uid, "b");
Cache.removeAll();
check({
    name: "Cache.removeAll",
    ok: !Cache.included("tc_ra1_" + uid) && !Cache.included("tc_ra2_" + uid),
    detail: "ra1=" + Cache.included("tc_ra1_" + uid) + ", ra2=" + Cache.included("tc_ra2_" + uid),
    objective: "Verify removeAll() clears every cache key",
    objectiveZh: "驗證 removeAll() 清除全部快取鍵",
    command: "Cache.removeAll()"
});

// Case C7
var sk = "tsc_" + uid;
StoredCache.set(sk, "stored-v");
check({
    name: "StoredCache.set/get roundtrip",
    ok: ("stored-v" === ("" + StoredCache.get(sk))) && StoredCache.included(sk),
    detail: "get=" + StoredCache.get(sk) + ", included=" + StoredCache.included(sk),
    objective: "Verify the permanent StoredCache facade stores/retrieves values",
    objectiveZh: "驗證永久 StoredCache 外觀可存入並取出值",
    command: "StoredCache.set(name, value); StoredCache.get(name)"
});

// Case C8
StoredCache.remove(sk);
StoredCache.set("tsc_ra_" + uid, "x");
StoredCache.removeAll();
check({
    name: "StoredCache.remove / removeAll",
    ok: !StoredCache.included(sk) && !StoredCache.included("tsc_ra_" + uid),
    detail: "removed=" + !StoredCache.included(sk) + ", cleared=" + !StoredCache.included("tsc_ra_" + uid),
    objective: "Verify StoredCache.remove and removeAll",
    objectiveZh: "驗證 StoredCache.remove 與 removeAll",
    command: "StoredCache.remove(name); StoredCache.removeAll()"
});

// Case C9
var ca = new CacheArray();
// Precondition: add() requires an attached CacheArrayFilter. A 60s timer keeps
// the async worker from consuming the enqueued items during this run, so the
// key-management assertions below are deterministic.
var caf = new CacheArrayFilter(60000);
ca.filter(caf);
var ck1 = ca.add("a");
var ck2 = ca.add("b");
var ck3 = ca.add("c");
check({
    name: "CacheArray.add x3 -> non-null keys, size 3",
    ok: ck1 !== null && ck2 !== null && ck3 !== null && ca.size() === 3,
    detail: "size=" + ca.size() + ", keys=" + ck1 + "," + ck2 + "," + ck3,
    objective: "Verify CacheArray.add() (with attached filter) returns keys and tracks size",
    objectiveZh: "驗證 CacheArray.add()（掛載 filter 後）回傳鍵並統計大小",
    command: "ca.filter(new CacheArrayFilter(60000)); ca.add(value)"
});

// Case C10
var keysDistinct = (("" + ck1) !== ("" + ck2)) && (("" + ck2) !== ("" + ck3)) && (("" + ck1) !== ("" + ck3));
var key0 = "" + ca.getCacheKey(0);
check({
    name: "CacheArray.getCacheKey / getCacheKeys",
    ok: keysDistinct && key0.length > 0 && ca.getCacheKeys().size() === 3,
    detail: "key(0)=" + ca.getCacheKey(0) + ", keys.size=" + ca.getCacheKeys().size(),
    objective: "Verify key listing is distinct and matches size",
    objectiveZh: "驗證鍵清單彼此不同且大小一致",
    command: "ca.getCacheKey(index); ca.getCacheKeys()"
});

// Case C11
ca.remove(1);
check({
    name: "CacheArray.remove(1) -> size 2",
    ok: ca.size() === 2,
    detail: "size after remove(1)=" + ca.size(),
    objective: "Verify CacheArray.remove(index) removes a key",
    objectiveZh: "驗證 CacheArray.remove(index) 移除鍵",
    command: "ca.remove(1)"
});

// Case C12
ca.removeAll();
check({
    name: "CacheArray.removeAll -> size 0",
    ok: ca.size() === 0,
    detail: "size after removeAll=" + ca.size(),
    objective: "Verify CacheArray.removeAll() clears the key list",
    objectiveZh: "驗證 CacheArray.removeAll() 清空鍵清單",
    command: "ca.removeAll()"
});
// Stop the global CacheArray worker (single user in this JVM); the 60s timer
// means the worker is still asleep - System.exit in TestAll ends the JVM.
CacheArray.forceStop();

writeReport();
writeMd();
