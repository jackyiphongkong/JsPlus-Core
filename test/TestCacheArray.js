// ============================================================================
// JsPlus-Core TestCacheArray - async cache list (com.killcoding.cache.CacheArray)
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat (included as a suite) or standalone:
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestCacheArray.js < NUL
// Output   : <PROJECT_ROOT>/docs/latest/report/TestCacheArray-Report.html
// Scope    : com.killcoding.cache.CacheArray - synchronized cache list with an
//            async filter worker. Covers: add/get/getCacheKey/size/remove/
//            addAll/sweepProcessed, nullIsCompleted semantics, filter
//            lifecycle (terminate), pool management, counters and the
//            canonical Console.getGlobalCacheArray() + Console.callback()
//            consumption flow (same pattern as TestFind.js).
// NOTE     : GraalVM JS forbids touching JS values from the filter worker
//            thread (PolyglotThreadAccessException). Like TestFind.js we only
//            use engine-managed GCAs: a slow GCA (5000/5000ms) without any
//            Console.callback gives a deterministic window for the synchronous
//            API assertions (the worker's execute() is skipped when callback
//            is null, so it never touches JS), and a fast GCA (1/1ms) inside a
//            dedicated console with a Java.extend(Runnable) callback exercises
//            the consumption flow. NEVER call new CacheArray().filter(...)
//            manually in the main console.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var ArrayList = Java.type("java.util.ArrayList");
var CacheArray = Java.type("com.killcoding.cache.CacheArray");

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

var REPORT_TITLE = "JsPlus-Core CacheArray Test";
var REPORT_NAME = "TestCacheArray-Report.html";

beginTest();

print("=== JsPlus-Core CacheArray Test ===");
print("JS_PLUS_PATH: " + basePath);

// ---------------------------------------------------------------------------
// Part 1 - synchronous API on a slow engine-managed GCA (5000/5000ms)
// The worker is created by the engine; no Console.callback is registered here,
// so execute()/executeBatch() are skipped and the worker never touches JS.
// 5000ms loop gives a deterministic window for add/get/remove assertions.
// ---------------------------------------------------------------------------
var GCA = Console.getGlobalCacheArray(5000, 5000);

try {
    // C1: the engine GCA is a CacheArray with nullIsCompleted=false
    Logger.systemMark("TestCacheArray.js", "C1");
    check({
        caseId: "C1",
        name: "Console.getGlobalCacheArray() returns CacheArray(nullIsCompleted=false)",
        ok: GCA !== null && GCA.isNulIsCompleted() === false,
        detail: "nullIsCompleted=" + GCA.isNulIsCompleted(),
        objective: "engine GCA is created via new CacheArray(false)",
        objectiveZh: "引擎 GCA 以 new CacheArray(false) 建立",
        command: "Console.getGlobalCacheArray(5000, 5000).isNulIsCompleted()"
    });

    // C2: the engine attached a filter with the requested timers
    Logger.systemMark("TestCacheArray.js", "C2");
    check({
        caseId: "C2",
        name: "GCA filter attached with timer=loopTimer=5000",
        ok: GCA.getFilter() !== null && GCA.getFilter().getTimer() === 5000
            && GCA.getFilter().getLoopTimer() === 5000,
        detail: "timer=" + GCA.getFilter().getTimer()
            + " loopTimer=" + GCA.getFilter().getLoopTimer(),
        objective: "getGlobalCacheArray(timer, loopTimer) configures the filter",
        objectiveZh: "getGlobalCacheArray(timer, loopTimer) 設定 filter",
        command: "GCA.getFilter().getTimer()/getLoopTimer()"
    });

    // C3: add() returns a 16-char key and grows size
    var k3 = GCA.add("value-one");
    Logger.systemMark("TestCacheArray.js", "C3");
    check({
        caseId: "C3",
        name: "add() returns a non-null key and size grows",
        ok: k3 !== null && k3.length === 16 && GCA.size() >= 1,
        detail: "key=" + k3 + " size=" + GCA.size(),
        objective: "add() stores the value and returns its cache key",
        objectiveZh: "add() 存入值並回傳快取鍵",
        command: "GCA.add('value-one')"
    });

    // C4: get(index) retrieves the stored value
    var v4 = GCA.get(0);
    Logger.systemMark("TestCacheArray.js", "C4");
    check({
        caseId: "C4",
        name: "get(0) retrieves the stored value",
        ok: v4 !== null && ("" + v4) === "value-one",
        detail: "value=" + v4,
        objective: "get(index) returns the value for the cache key at index",
        objectiveZh: "get(index) 回傳該索引的快取值",
        command: "GCA.get(0)"
    });

    // C5: getCacheKey(index) matches the key returned by add()
    var k5 = GCA.getCacheKey(0);
    Logger.systemMark("TestCacheArray.js", "C5");
    check({
        caseId: "C5",
        name: "getCacheKey(0) matches the add() key",
        ok: k5 === k3,
        detail: "key=" + k5,
        objective: "getCacheKey(index) exposes the key at the index",
        objectiveZh: "getCacheKey(index) 暴露該索引的鍵",
        command: "GCA.getCacheKey(0)"
    });

    // C6: addAll(List) appends every item
    var more = new ArrayList();
    more.add("a"); more.add("b"); more.add("c");
    var before6 = GCA.size();
    GCA.addAll(more);
    Logger.systemMark("TestCacheArray.js", "C6");
    check({
        caseId: "C6",
        name: "addAll() appends the whole list",
        ok: GCA.size() === before6 + 3,
        detail: "before=" + before6 + " after=" + GCA.size(),
        objective: "addAll(list) adds each item in order",
        objectiveZh: "addAll(list) 依序加入每一項",
        command: "GCA.addAll(list)"
    });

    // C7: add(value, sleepMs) variant returns the key after sleeping
    var k7 = null;
    var t7a = System.currentTimeMillis();
    try { k7 = GCA.add("sleepy", 20); } catch (e) { k7 = null; }
    var t7b = System.currentTimeMillis();
    Logger.systemMark("TestCacheArray.js", "C7");
    check({
        caseId: "C7",
        name: "add(value, sleepMs) returns a key and sleeps",
        ok: k7 !== null && (t7b - t7a) >= 15,
        detail: "key=" + k7 + " sleepMs=" + (t7b - t7a),
        objective: "add(value, sleepMs) sleeps before returning the key",
        objectiveZh: "add(value, sleepMs) 休眠後回傳鍵",
        command: "GCA.add('sleepy', 20)"
    });

    // C8: remove(index) returns the removed key and shrinks size
    var before8 = GCA.size();
    var r8 = GCA.remove(0);
    Logger.systemMark("TestCacheArray.js", "C8");
    check({
        caseId: "C8",
        name: "remove(0) removes the head item",
        ok: r8 !== null && GCA.size() === before8 - 1,
        detail: "removed=" + r8 + " size " + before8 + "->" + GCA.size(),
        objective: "remove(index) drops the item and returns its key",
        objectiveZh: "remove(index) 移除該項並回傳其鍵",
        command: "GCA.remove(0)"
    });

    // C9: sweepProcessed(count) reclaims head items (after 5 adds, keep tail)
    // Isolated GCA in a dedicated console: the main console's GCA is a
    // per-console singleton shared with C1-C8, so a clean slow GCA here gives
    // deterministic assertions (TestFind pattern: context.put/get bridge).
    var syncConsole9 = Console.newBuilder();
    syncConsole9.evalScript("(function(){"
        + " var g = Console.getGlobalCacheArray(5000, 5000);"
        + " Console.getContext().put('GCA_SWEEP', g);"
        + "}())");
    var gca9 = syncConsole9.getContext().get("GCA_SWEEP");
    gca9.add("s1"); gca9.add("s2"); gca9.add("s3"); gca9.add("s4"); gca9.add("s5");
    var swept9 = gca9.sweepProcessed(2);
    var head9 = gca9.get(0);
    Logger.systemMark("TestCacheArray.js", "C9");
    check({
        caseId: "C9",
        name: "sweepProcessed(2) reclaims the first 2 of 5",
        ok: swept9 === 2 && gca9.size() === 3 && head9 !== null && ("" + head9) === "s3",
        detail: "swept=" + swept9 + " size=" + gca9.size() + " head=" + head9,
        objective: "sweepProcessed(count) removes count head items, keeps tail",
        objectiveZh: "sweepProcessed(count) 移除 count 個頭部項，保留尾部",
        command: "gca9.sweepProcessed(2)"
    });
    gca9.forceTerminate();   // stop the isolated worker, keep others intact

    // C10: nullIsCompleted=false only accepts non-null values
    // Another isolated GCA (nullIsCompleted=false by default); the previous
    // forceTerminate() must not affect this instance.
    var syncConsole10 = Console.newBuilder();
    syncConsole10.evalScript("(function(){"
        + " var g = Console.getGlobalCacheArray(5000, 5000);"
        + " Console.getContext().put('GCA_NUL', g);"
        + "}())");
    var gca10 = syncConsole10.getContext().get("GCA_NUL");
    var k10null = gca10.add(null);
    var k10val = gca10.add("x");
    Logger.systemMark("TestCacheArray.js", "C10");
    check({
        caseId: "C10",
        name: "nullIsCompleted=false rejects null but accepts values",
        ok: k10null === null && k10val !== null,
        detail: "add(null)=" + k10null + " add('x')=" + k10val,
        objective: "nullIsCompleted=false stores only non-null values",
        objectiveZh: "nullIsCompleted=false 只存入非 null 值",
        command: "gca10.add(null) / gca10.add('x')"
    });
    gca10.forceTerminate();

    // C11: pool management - initPool only takes effect while the pool is shut
    //      down (shutdownPool() first, restore afterwards)
    var max11 = CacheArray.getMaxPoolSize();
    CacheArray.shutdownPool();
    CacheArray.initPool(5);
    var max11b = CacheArray.getMaxPoolSize();
    CacheArray.shutdownPool();        // restore: initPool only applies on a null pool
    CacheArray.initPool(max11);
    var usage11 = CacheArray.getUsage();
    Logger.systemMark("TestCacheArray.js", "C11");
    check({
        caseId: "C11",
        name: "shutdownPool()+initPool(5) applies the pool size",
        ok: max11 > 0 && max11b === 5 && usage11 >= 0.0,
        detail: "max=" + max11 + "->" + max11b + " usage=" + usage11,
        objective: "initPool sets MAX_POOL_SIZE only when the pool is re-created",
        objectiveZh: "池重建時 initPool 才生效（先 shutdownPool）",
        command: "CacheArray.shutdownPool(); CacheArray.initPool(5); getMaxPoolSize()"
    });

    // C12: counters before consumption - INDEX/OBJECT/SIZE/BATCH defaults
    //      (plain read of a fresh instance, no filter worker involved)
    var ca12 = new CacheArray();
    Logger.systemMark("TestCacheArray.js", "C12");
    check({
        caseId: "C12",
        name: "fresh instance counter defaults (-1/null/empty)",
        ok: ca12.getIndex() === -1 && ca12.getObject() === null && ca12.getSize() === -1
            && ca12.getBatchIndex() === -1 && ca12.getBatch() !== null,
        detail: "INDEX=" + ca12.getIndex() + " OBJECT=" + ca12.getObject()
            + " SIZE=" + ca12.getSize() + " BATCH_INDEX=" + ca12.getBatchIndex(),
        objective: "before any filter run the counters hold their initial values",
        objectiveZh: "filter 執行前計數器為初始值",
        command: "new CacheArray().getIndex()/getObject()/getSize()"
    });

    // C13: isTerminated without a filter is true (plain read)
    var ca13 = new CacheArray();
    Logger.systemMark("TestCacheArray.js", "C13");
    check({
        caseId: "C13",
        name: "isTerminated() is true when no filter is attached",
        ok: ca13.isTerminated() === true && ca13.isForceTerminated() === true,
        detail: "terminated=" + ca13.isTerminated() + " forceTerminated=" + ca13.isForceTerminated(),
        objective: "no filter -> treated as terminated",
        objectiveZh: "無 filter 視為已終止",
        command: "new CacheArray().isTerminated()"
    });

    // C14: terminate() flags the filter on an engine GCA (isolated instance)
    var syncConsole14 = Console.newBuilder();
    syncConsole14.evalScript("(function(){"
        + " var g = Console.getGlobalCacheArray(5000, 5000);"
        + " Console.getContext().put('GCA_TERM', g);"
        + "}())");
    var gca14 = syncConsole14.getContext().get("GCA_TERM");
    gca14.add("t1"); gca14.add("t2"); gca14.add("t3");
    var before14 = gca14.isTerminated();
    gca14.terminate();
    var after14 = gca14.isTerminated();
    gca14.forceTerminate();   // let the worker exit and sweep remaining items
    var size14 = 0;
    // forceTerminate() only flags the filter: the worker must first wake from
    // its current Thread.sleep(timer=5000ms), finish the batch + sweep, sleep
    // the loopTimer(5000ms), then reach finally { removeAll() } - worst case
    // ~10s plus CI scheduling jitter. The old 10s budget (200x50ms) could cut
    // off exactly mid-sweep ("terminated false->true size=2" on 2026-10-29
    // CI): the worker had swept 1 item and was still inside the loopTimer
    // sleep when the wait expired. Wait 25s (500 x 50ms).
    for (var w14 = 0; w14 < 500; w14++) {
        size14 = gca14.size();
        if (size14 === 0) { break; }
        Thread.sleep(50);
    }
    Logger.systemMark("TestCacheArray.js", "C14");
    check({
        caseId: "C14",
        name: "terminate() flags the filter; forceTerminate() sweeps all",
        ok: before14 === false && after14 === true && size14 === 0,
        detail: "terminated " + before14 + "->" + after14 + " size=" + size14,
        objective: "terminate() sets the filter flag; forceTerminate() exits the worker whose final removeAll() clears the list",
        objectiveZh: "terminate() 設定旗標；forceTerminate() 使 worker 退出並 removeAll() 清空",
        command: "gca14.terminate(); gca14.forceTerminate(); wait gca14.size()===0"
    });

} catch (e) {
    Logger.systemMark("TestCacheArray.js", "C-ERR-1");
    check({
        caseId: "C-ERR-1",
        name: "TestCacheArray sync-API section error",
        ok: false,
        detail: "" + e
    });
}

// ---------------------------------------------------------------------------
// Part 2 - canonical consumption flow: fast GCA (1/1ms) inside a dedicated
// console with a Java.extend(Runnable) callback (same pattern as TestFind.js)
// ---------------------------------------------------------------------------
try {
    var callbackConsole = Console.newBuilder();
    var consumed = new ArrayList();   // every consumed value, as a String
    var errors = new ArrayList();     // any exception inside the callback
    callbackConsole.getContext().put("consumed", consumed);
    callbackConsole.getContext().put("errors", errors);
    callbackConsole.getContext().put("Runnable", Runnable);
    callbackConsole.evalScript(`(function(){
        var gcaFast = Console.getGlobalCacheArray(1, 1);
        Console.getContext().put("GCA_FAST", gcaFast);
        var CB = Java.extend(Runnable, {
            run: function () {
                try {
                    var o = gcaFast.getObject();
                    if (o !== null && o !== undefined) {
                        consumed.add(String(o));
                    }
                } catch (e) {
                    errors.add(String(e));
                }
            }
        });
        Console.callback(new CB());
    }())`);
    var gcaFast = callbackConsole.getContext().get("GCA_FAST");

    // feed 3 values from the MAIN console (synchronized Java call - safe)
    gcaFast.add("row-1");
    gcaFast.add("row-2");
    gcaFast.add("row-3");

    // wait until the worker consumed all 3 (fast 1ms loop)
    var consumedTotal = 0;
    for (var w15 = 0; w15 < 400 && consumedTotal < 3; w15++) {
        consumedTotal = consumed.size();
        if (consumedTotal < 3) { Thread.sleep(50); }
    }
    var processedTotal15 = gcaFast.getProcessedTotal();
    var size15 = gcaFast.size();
    var ok15 = consumedTotal === 3 && errors.size() === 0
        && processedTotal15 >= 3 && size15 === 0;
    Logger.systemMark("TestCacheArray.js", "C15");
    check({
        caseId: "C15",
        name: "fast GCA + Console.callback consumes all fed rows (TestFind pattern)",
        ok: ok15,
        detail: "consumed=" + consumedTotal + " processedTotal=" + processedTotal15
            + " size=" + size15 + " errors=" + errors.size(),
        objective: "engine GCA worker feeds the registered callback; removeAll() clears the list",
        objectiveZh: "引擎 GCA worker 餵給已註冊 callback；removeAll() 清空清單",
        command: "callbackConsole.getGlobalCacheArray(1,1) + Console.callback(Runnable); feed 3"
    });
    gcaFast.forceTerminate();
} catch (e) {
    Logger.systemMark("TestCacheArray.js", "C-ERR-2");
    check({
        caseId: "C-ERR-2",
        name: "TestCacheArray callback section error",
        ok: false,
        detail: "" + e
    });
}

// Clean up
try { CacheArray.shutdownPool(); } catch (e) {}

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
