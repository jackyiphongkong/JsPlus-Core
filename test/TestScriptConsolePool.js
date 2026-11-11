// ============================================================================
// JsPlus-Core TestScriptConsolePool - thread-confined ScriptConsole pool
// ----------------------------------------------------------------------------
// Scope    : ScriptConsolePool - works around GraalVM's single-entry-per-context
//            rule by handing every thread its own ScriptConsole
//            (ScriptConsole.newBuilder -> own context):
//              getInstance()          one shared default pool
//              getConsole()           per-thread console (thread-id bound)
//              freed()                delayed return to the idle set
//              evalScriptOn(String)   acquire + eval + finally return
//              init() / idleCount()   pre-warm (>= 10) / diagnostics
//            Concurrency cases run through a dynamically compiled Java probe
//            (Console.evalJavaSource): a JS function may not be entered from a
//            worker thread, pure Java threads are.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var StringType = Java.type("java.lang.String");
var ScriptConsolePool = Java.type("com.killcoding.jsplus.ScriptConsolePool");
var ThreadCls = Java.type("java.lang.Thread");
var FileCls = Java.type("java.io.File");
var FileWriterCls = Java.type("java.io.FileWriter");

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

// Regex-literal-free report parsers: the bundled graalvm.js engine ships
// without TRegex, so /.../ literals fail at parse time for the whole file.
function readNumber(report, key) {
    var i = report.indexOf(key + "=");
    if (i < 0) { return -1; }
    var s = i + key.length + 1;
    var e = s;
    while (e < report.length && "0123456789".indexOf(report.charAt(e)) >= 0) { e++; }
    if (e === s) { return -1; }
    return parseInt(report.substring(s, e), 10);
}

function readPair(report, key) {
    var i = report.indexOf(key + "=");
    if (i < 0) { return null; }
    var s = i + key.length + 1;
    var arrow = report.indexOf("->", s);
    if (arrow < 0) { return null; }
    var e = arrow + 2;
    var f = e;
    while (f < report.length && "0123456789".indexOf(report.charAt(f)) >= 0) { f++; }
    return [report.substring(s, arrow), report.substring(e, f)];
}

var REPORT_TITLE = "JsPlus-Core ScriptConsolePool Test";
var REPORT_NAME = "TestScriptConsolePool-Report.html";

beginTest();

print("=== JsPlus-Core ScriptConsolePool Test ===");
print("JS_PLUS_PATH: " + basePath);

// Java probe: worker threads may enter a ScriptConsole, a JS callback may not.
var SRC_PROBE = `
import com.killcoding.jsplus.ScriptConsole;
import com.killcoding.jsplus.ScriptConsolePool;
import com.killcoding.tool.ConfigProperties;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScriptConsolePoolProbe {

    public static String asyncProbe(String scriptPath, String key) throws Exception {
        ScriptConsolePool pool = ScriptConsolePool.getInstance();
        pool.init();
        int idleBefore = pool.idleCount();
        ConfigProperties p = new ConfigProperties();
        p.putAndReturnSelf("EAID", "ASYNC-OK");
        long t0 = System.currentTimeMillis();
        Thread t = pool.runAsync(new File(scriptPath), p);
        long returnMs = System.currentTimeMillis() - t0;
        t.join(60000);
        long totalMs = System.currentTimeMillis() - t0;
        Thread.sleep(250);
        String seen = System.getProperty(key, "null");
        System.clearProperty(key);
        int idleAfter = pool.idleCount();
        return "returnMs=" + returnMs + " totalMs=" + totalMs
             + " alive=" + t.isAlive() + " seen=" + seen
             + " idle=" + idleBefore + "->" + idleAfter;
    }

    public static String asyncBatchProbe(String scriptPath, String key, int items) throws Exception {
        ScriptConsolePool pool = ScriptConsolePool.getInstance();
        pool.init();
        int idleBefore = pool.idleCount();
        ConfigProperties p = new ConfigProperties();
        p.putAndReturnSelf("EAID", "BATCH");
        p.putAndReturnSelf("n", String.valueOf(items));
        long t0 = System.currentTimeMillis();
        Thread t = pool.runAsync(new File(scriptPath), p);
        long returnMs = System.currentTimeMillis() - t0;
        t.join(120000);
        long totalMs = System.currentTimeMillis() - t0;
        Thread.sleep(250);
        String seen = System.getProperty(key, "null");
        System.clearProperty(key);
        int idleAfter = pool.idleCount();
        return "returnMs=" + returnMs + " totalMs=" + totalMs
             + " alive=" + t.isAlive() + " items=" + seen
             + " idle=" + idleBefore + "->" + idleAfter;
    }

    public static String asyncKeepAliveProbe(String scriptPath, String key,
                                             int rounds, int intervalMs) throws Exception {
        ScriptConsolePool pool = ScriptConsolePool.getInstance();
        pool.init();
        int idleBefore = pool.idleCount();
        System.clearProperty(key);
        final java.util.concurrent.atomic.AtomicBoolean stop =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        ConfigProperties p = new ConfigProperties();
        p.putObjectAndReturnSelf("stopFlag", stop);
        p.putAndReturnSelf("intervalMs", String.valueOf(intervalMs));
        long t0 = System.currentTimeMillis();
        Thread t = pool.runAsync(new File(scriptPath), p);
        long returnMs = System.currentTimeMillis() - t0;
        int ticks = 0;
        for (int i = 0; i < 120; i++) {
            String v = System.getProperty(key);
            if (v != null) {
                try { ticks = Integer.parseInt(v); } catch (NumberFormatException ignore) { }
            }
            if (ticks >= rounds) { break; }
            Thread.sleep(100);
        }
        System.setProperty(key + ".stop", "1");
        stop.set(true);
        t.join(8000);
        long totalMs = System.currentTimeMillis() - t0;
        Thread.sleep(250);
        System.clearProperty(key);
        System.clearProperty(key + ".stop");
        int idleAfter = pool.idleCount();
        return "returnMs=" + returnMs + " ticks=" + ticks + " totalMs=" + totalMs
             + " alive=" + t.isAlive() + " idle=" + idleBefore + "->" + idleAfter;
    }

    public static String parallelHash(int threads) throws Exception {
        final ScriptConsolePool pool = ScriptConsolePool.getInstance();
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final Set<Integer> hashes = Collections.synchronizedSet(new LinkedHashSet<Integer>());
        final AtomicInteger ok = new AtomicInteger();
        final List<String> errs = Collections.synchronizedList(new ArrayList<String>());
        for (int t = 0; t < threads; t++) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        start.await();
                        ScriptConsole sc = pool.getConsole();
                        hashes.add(Integer.valueOf(sc.hashCode()));
                        Object r = sc.evalScript("1 + 1");
                        if (r != null && String.valueOf(r).equals("2")) {
                            ok.incrementAndGet();
                        }
                    } catch (Throwable e) {
                        errs.add(String.valueOf(e.getMessage()));
                    } finally {
                        done.countDown();
                        pool.freed();
                    }
                }
            }).start();
        }
        start.countDown();
        done.await(120, TimeUnit.SECONDS);
        Thread.sleep(300);
        return "threads=" + threads + " distinct=" + hashes.size()
             + " ok=" + ok.get() + " errs=" + errs.size()
             + (errs.isEmpty() ? "" : " first=" + errs.get(0));
    }

    public static String exhaustHash(int size, int threads) throws Exception {
        ScriptConsolePool pool = new ScriptConsolePool();
        pool.setMaxPoolSize(size);
        pool.init();
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger full = new AtomicInteger();
        final AtomicInteger other = new AtomicInteger();
        final List<String> msgs = Collections.synchronizedList(new ArrayList<String>());
        // Phase 1: occupy every console and HOLD it, so the pool is genuinely
        // exhausted while the extra thread probes it. Racing 11 threads at once
        // is not deterministic: freed() returns consoles through FREED_SCHEDULER
        // after getFreedDelayMs() (10 ms), so the 11th thread could arrive after
        // an earlier one had already given its console back.
        final CountDownLatch held = new CountDownLatch(size);
        final CountDownLatch release = new CountDownLatch(1);
        List<Thread> holders = new ArrayList<Thread>();
        for (int t = 0; t < size; t++) {
            Thread th = new Thread(new Runnable() {
                public void run() {
                    try {
                        pool.getConsole();
                        held.countDown();
                        release.await(120, TimeUnit.SECONDS);
                    } catch (Throwable e) {
                        msgs.add(String.valueOf(e.getMessage()));
                    } finally {
                        pool.freed();
                    }
                }
            });
            holders.add(th);
            th.start();
        }
        if (!held.await(120, TimeUnit.SECONDS)) {
            release.countDown();
            return "size=" + size + " threads=" + threads + " ok=-1 full=-1 other=-1 hold-timeout";
        }
        ok.addAndGet(size);
        // Phase 2: with every console held, the extra thread must be rejected.
        Thread extra = new Thread(new Runnable() {
            public void run() {
                try {
                    pool.getConsole();
                    ok.incrementAndGet();
                } catch (Throwable e) {
                    String m = String.valueOf(e.getMessage());
                    if (m != null && m.indexOf("Full ScriptConsole Pool") >= 0) {
                        full.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                        if (msgs.size() < 3) {
                            msgs.add(m);
                        }
                    }
                } finally {
                    pool.freed();
                }
            }
        });
        extra.start();
        extra.join(120000);
        release.countDown();
        for (Thread th : holders) {
            th.join(120000);
        }
        // freed() puts the consoles back asynchronously after getFreedDelayMs()
        Thread.sleep(500);
        return "size=" + size + " threads=" + threads + " ok=" + ok.get()
             + " full=" + full.get() + " other=" + other.get()
             + " idle=" + pool.idleCount()
             + (msgs.isEmpty() ? "" : " msgs=" + msgs);
    }
}`;

var probeLoader = null;

try {
    // C1: one shared default pool instance
    var p1 = ScriptConsolePool.getInstance();
    var p2 = ScriptConsolePool.getInstance();
    var ok1 = p1 !== null && p2 !== null && p1.equals(p2);
    Logger.systemMark("TestScriptConsolePool.js", "C1");
    check({
        caseId: "C1",
        name: "getInstance() returns one shared pool",
        ok: ok1,
        detail: "same=" + (p1 === null || p2 === null ? "null" : p1.equals(p2)),
        objective: "ScriptConsolePool exposes a process-wide default pool (AllowAllAccessPool-style singleton)",
        objectiveZh: "ScriptConsolePool 提供進程級默認池（AllowAllAccessPool 式單例）",
        command: "ScriptConsolePool.getInstance()"
    });

    // C2: the console is bound to the calling thread and taken out of the idle set
    // init() first: getConsole() lazily initialises the pool, so an idle count
    // taken before that would read 0 instead of the pre-warmed size.
    var pool = ScriptConsolePool.getInstance();
    pool.init();
    var idleBefore = pool.idleCount();
    var sc1 = pool.getConsole();
    var sc2 = pool.getConsole();
    var idleHeld = pool.idleCount();
    var ok2 = !pool.isEngineLoaded() ? false : (sc1.equals(sc2) && idleHeld === idleBefore - 1);
    Logger.systemMark("TestScriptConsolePool.js", "C2");
    check({
        caseId: "C2",
        name: "getConsole() is thread-bound and removes one console from the idle set",
        ok: ok2,
        detail: "same console=" + sc1.equals(sc2) + " idle " + idleBefore + " -> " + idleHeld,
        objective: "a thread reuses its own console; a held console is no longer idle",
        objectiveZh: "同一線程復用自己的 console；借出的 console 不再計入空閒",
        command: "ScriptConsolePool.getInstance().getConsole() (twice, same thread)"
    });
    pool.freed();
    ThreadCls.sleep(200);

    // C3: evalScriptOn acquires, evaluates, and returns the console in finally
    var idleBeforeEval = pool.idleCount();
    var value = pool.evalScriptOn("1 + 1");
    ThreadCls.sleep(200);
    var idleAfterEval = pool.idleCount();
    var ok3 = ("" + value) === "2" && idleAfterEval === idleBeforeEval;
    Logger.systemMark("TestScriptConsolePool.js", "C3");
    check({
        caseId: "C3",
        name: "evalScriptOn() returns the script value and gives the console back",
        ok: ok3,
        detail: "value=" + value + " idle " + idleBeforeEval + " -> " + idleAfterEval,
        objective: "one-call entry point wraps evalScript with a finally freed()",
        objectiveZh: "一步式入口以 finally freed() 包裹 evalScript",
        command: "ScriptConsolePool.getInstance().evalScriptOn('1 + 1')"
    });

    // C4/C5: concurrency cases need Java worker threads (JS cannot be entered
    // from them) - compiled on the fly and invoked by reflection.
    probeLoader = Console.evalJavaSource(SRC_PROBE);
    var probeClass = probeLoader.loadClass("ScriptConsolePoolProbe");
    var intType = Java.type("int");

    var parallelReport = "" + probeClass.getMethod("parallelHash", intType).invoke(null, 4);
    var ok4 = parallelReport.indexOf("distinct=4") >= 0
        && parallelReport.indexOf("ok=4") >= 0
        && parallelReport.indexOf("errs=0") >= 0;
    Logger.systemMark("TestScriptConsolePool.js", "C4");
    check({
        caseId: "C4",
        name: "4 worker threads get 4 distinct consoles and all evaluate concurrently",
        ok: ok4,
        detail: parallelReport,
        objective: "each thread enters its own GraalVM context - no cross-thread access error",
        objectiveZh: "每線程進入自己的 GraalVM Context——無跨線程訪問錯誤",
        command: "probe.parallelHash(4) -> getConsole()/evalScript('1 + 1') per thread"
    });

    var exhaustReport = "" + probeClass.getMethod("exhaustHash", intType, intType).invoke(null, 10, 11);
    var ok5 = exhaustReport.indexOf("ok=10") >= 0
        && exhaustReport.indexOf("full=1") >= 0
        && exhaustReport.indexOf("other=0") >= 0
        && exhaustReport.indexOf("idle=10") >= 0;
    Logger.systemMark("TestScriptConsolePool.js", "C5");
    check({
        caseId: "C5",
        name: "an exhausted pool rejects the extra thread with 'Full ScriptConsole Pool.'",
        ok: ok5,
        detail: exhaustReport,
        objective: "10 pooled consoles let 10 threads in; the 11th fails with the documented message; after freed() settles every console is back in the pool (idle=10)",
        objectiveZh: "10 個池化 console 容納 10 個線程；第 11 個以文檔化訊息失敗；freed() 的非同步歸還落定後 10 個 console 全部回到池中（idle=10）",
        command: "probe.exhaustHash(size=10, threads=11)"
    });

    // C6: runAsync posts a long-running script to a background thread and
    // returns immediately (the backtest-style blocking start() use case).
    var tmpDir = System.getProperty("java.io.tmpdir");
    var asyncScript = new FileCls(tmpDir, "jsplus-scpool-async.js");
    var fw = new FileWriterCls(asyncScript);
    fw.write("var __p = Console.getContext().get();\n");
    fw.write("Java.type('java.lang.System').setProperty('jsplus.scpool.probe', String(__p.getString('EAID')));\n");
    fw.write("Java.type('java.lang.Thread').sleep(1200);\n");
    fw.close();

    var asyncReport = "" + probeClass.getMethod("asyncProbe", StringType, StringType)
        .invoke(null, asyncScript.getAbsolutePath(), "jsplus.scpool.probe");
    // NOTE: no regex literals anywhere in this suite - the bundled graalvm.js
    // engine ships without TRegex, so parsing a /.../ literal fails the whole
    // file with "No language for id regex found. Supported languages are: [js]".
    var returnMs = readNumber(asyncReport, "returnMs");
    var totalMs = readNumber(asyncReport, "totalMs");
    var idlePair = readPair(asyncReport, "idle");
    var ok6 = returnMs >= 0 && returnMs < 1000
        && totalMs >= 1100
        && asyncReport.indexOf("alive=false") >= 0
        && asyncReport.indexOf("seen=ASYNC-OK") >= 0
        && idlePair !== null && idlePair[0] === idlePair[1];
    Logger.systemMark("TestScriptConsolePool.js", "C6");
    check({
        caseId: "C6",
        name: "runAsync() returns at once, runs the script on its own thread, passes context params and gives the console back",
        ok: ok6,
        detail: asyncReport,
        objective: "a blocking script (Thread.sleep 1200ms) is dispatched without blocking the caller; params reach the worker context",
        objectiveZh: "阻塞腳本（Thread.sleep 1200ms）被派發而不阻塞呼叫者；參數送達工作線程 context",
        command: "ScriptConsolePool.getInstance().runAsync(file, params)"
    });
    if (asyncScript !== null && asyncScript.exists()) { asyncScript.delete(); }

    // C7: runAsync driving a batch loop - every item must run to completion.
    // Regression guard for the real-world failure "only the first item executed":
    // a loop that stops after item 0 (blocked inside item 0, or a while(true)
    // keep-alive), a swallowed per-item exception, or a non-returning console all
    // show up here as a short / out-of-order item list.
    var batchScript = new FileCls(tmpDir, "jsplus-scpool-batch.js");
    var bw = new FileWriterCls(batchScript);
    bw.write("var __p = Console.getContext().get();\n");
    bw.write("var __n = parseInt(__p.getString('n'), 10);\n");
    bw.write("var __seen = '';\n");
    bw.write("for (var __i = 0; __i < __n; __i++) {\n");
    bw.write("    __seen = __seen + __i + ',';\n");
    bw.write("    Java.type('java.lang.Thread').sleep(300);\n");
    bw.write("}\n");
    bw.write("Java.type('java.lang.System').setProperty('jsplus.scpool.batch', __seen);\n");
    bw.close();

    var batchReport = "" + probeClass.getMethod("asyncBatchProbe", StringType, StringType, intType)
        .invoke(null, batchScript.getAbsolutePath(), "jsplus.scpool.batch", 3);
    var batchReturnMs = readNumber(batchReport, "returnMs");
    var batchTotalMs = readNumber(batchReport, "totalMs");
    var batchIdle = readPair(batchReport, "idle");
    var ok7 = batchReturnMs >= 0 && batchReturnMs < 1000
        && batchTotalMs >= 900
        && batchReport.indexOf("alive=false") >= 0
        && batchReport.indexOf("items=0,1,2,") >= 0
        && batchIdle !== null && batchIdle[0] === batchIdle[1];
    Logger.systemMark("TestScriptConsolePool.js", "C7");
    check({
        caseId: "C7",
        name: "runAsync() batch loop executes every item, in order, then returns the console",
        ok: ok7,
        detail: batchReport,
        objective: "3 sequential 300ms items all run (0,1,2) inside one background console - the loop is not cut short after item 0",
        objectiveZh: "3 個各 300ms 的項目全部執行（0,1,2），批次迴圈不會停在第一項",
        command: "ScriptConsolePool.getInstance().runAsync(batchScript, params) -> for(i<n) sleep(300)"
    });
    if (batchScript !== null && batchScript.exists()) { batchScript.delete(); }

    // C8: runAsync carrying a stay-alive loop that never returns on its own.
    // The loop must be stoppable from the caller (Java AtomicBoolean passed
    // through ConfigProperties - a JS object cannot cross a thread/context),
    // and once stopped the console must come back to the idle set.
    var keepScript = new FileCls(tmpDir, "jsplus-scpool-keepalive.js");
    var kw = new FileWriterCls(keepScript);
    kw.write("var __p = Console.getContext().get();\n");
    kw.write("var __stop = __p.getObject('stopFlag');\n");
    kw.write("var __ms = parseInt(__p.getString('intervalMs'), 10);\n");
    kw.write("var __sys = Java.type('java.lang.System');\n");
    kw.write("var __stopKey = 'jsplus.scpool.keepalive.stop';\n");
    kw.write("var __ticks = 0;\n");
    kw.write("while (!__stop.get() && __sys.getProperty(__stopKey) === null) {\n");
    kw.write("    __ticks++;\n");
    kw.write("    __sys.setProperty('jsplus.scpool.keepalive', String(__ticks));\n");
    kw.write("    try { Java.type('java.lang.Thread').sleep(__ms); } catch (e) { break; }\n");
    kw.write("}\n");
    kw.close();

    var keepReport = "" + probeClass.getMethod("asyncKeepAliveProbe", StringType, StringType, intType, intType)
        .invoke(null, keepScript.getAbsolutePath(), "jsplus.scpool.keepalive", 3, 250);
    var keepReturnMs = readNumber(keepReport, "returnMs");
    var keepTicks = readNumber(keepReport, "ticks");
    var keepTotalMs = readNumber(keepReport, "totalMs");
    var keepIdle = readPair(keepReport, "idle");
    var ok8 = keepReturnMs >= 0 && keepReturnMs < 1000
        && keepTicks >= 3
        && keepTotalMs >= 600
        && keepReport.indexOf("alive=false") >= 0
        && keepIdle !== null && keepIdle[0] === keepIdle[1];
    Logger.systemMark("TestScriptConsolePool.js", "C8");
    check({
        caseId: "C8",
        name: "runAsync() carries a stay-alive loop that stops on an external flag and returns the console",
        ok: ok8,
        detail: keepReport,
        objective: "an endless while loop runs in the background, keeps ticking (>=3), stops on Java AtomicBoolean and frees the console",
        objectiveZh: "無盡 while 迴圈在後台持續運行（>=3 tick），由 Java AtomicBoolean 停止並歸還 console",
        command: "probe.asyncKeepAliveProbe(script, key, rounds=3, intervalMs=250) -> stopFlag.set(true)"
    });
    if (keepScript !== null && keepScript.exists()) { keepScript.delete(); }

} catch (e) {
    Logger.systemMark("TestScriptConsolePool.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "TestScriptConsolePool suite error",
        ok: false,
        detail: "" + e
    });
}

try {
    ScriptConsolePool.getInstance().freed();
} catch (e) { }

writeReport();

// NOTE: This suite NEVER calls System.exit() - same convention as the other
// suites: TestAll.js decides the JVM exit (JSPLUS_TEST_EXIT=1).
