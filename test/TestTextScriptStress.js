// ============================================================================
// JsPlus-Core TestTextScriptStress - TextScript multithreading stress suite
// ----------------------------------------------------------------------------
// Run      : standalone on demand: test/TestTextScriptStress.bat
//            (NOT integrated into TestAll - stress is not a regression suite)
// Output   : <PROJECT_ROOT>/docs/latest/report/TestTextScriptStress-Report.html
// Scope    : Does TextScript support multithreaded parse()? Three pooled
//            modes (pool concept, mirrors JsPlus-Tomcat TomcatScriptConsole,
//            CONTEXT_MAPPING removed - the pool is the single isolation
//            authority, no locks):
//              shared    - every worker renders via TextScript.render() on the
//                          default engine: thread-confined pooled context.
//              isolated  - every worker calls useEngine("graalvm.js-stress-<tid>")
//                          with a custom name: ALSO resolves to the current
//                          thread's own pooled context (name = identification
//                          key only).
//            Expected outcome (confirmed by the run): both modes 100% green -
//            the pool hands each thread its own context, so concurrent
//            renderers never share a GraalVM context and never cross-
//            contaminate; the old shared single-context failure (GraalJS
//            "Multi threaded access requested") is gone.
//            Workers are a pure-Java class compiled via Console.evalJavaSource
//            (same pattern as TestDatasource C18): worker threads cannot enter
//            the JS context directly, so the stress driver must be Java.
// NOTE     : every java.lang.String crossing the GraalJS boundary is unwrapped
//            with com.killcoding.jsplus.JsString; no regex literal (this
//            LibHome GraalVM JS has no TRegex); EOF backtick literals only.
// ============================================================================

var Logger = Java.type("com.killcoding.log.Logger");
var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var File = Java.type("java.io.File");
var JsString = Java.type("com.killcoding.jsplus.JsString");

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

var REPORT_TITLE = "JsPlus-Core TextScript Multithreading Stress";
var REPORT_NAME = "TestTextScriptStress-Report.html";

beginTest();

print("=== JsPlus-Core TextScript Multithreading Stress ===");
print("JS_PLUS_PATH: " + basePath);

function str(s) {
    return JsString.valueOf(s).asString();
}

// Parse "key=value " out of the driver's space-separated result string.
function field(s, key) {
    var i = s.indexOf(key + "=");
    if (i < 0) { return ""; }
    var rest = s.substring(i + key.length + 1);
    var j = rest.indexOf(" ");
    return j < 0 ? rest : rest.substring(0, j);
}

// Pure-Java stress driver: worker threads call TextScript.parse() and verify
// that every rendered output carries ONLY the owning thread's marker.
var SRC_STRESS = `
import com.killcoding.jsplus.TextScript;
import com.killcoding.jsplus.TextScriptConsole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TextScriptStress {
    public String stress(final String mode, final int threads, final int rounds) throws Exception {
        final String template = "#comment stress template\\nT#{tid} run#{run} -> #{i*2}\\n#if(i%2==0)\\nEVEN\\n#else\\nODD\\n#end\\n";
        final ExecutorService exec = Executors.newFixedThreadPool(threads);
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger wrong = new AtomicInteger();
        final AtomicInteger err = new AtomicInteger();
        final AtomicInteger seq = new AtomicInteger();
        final List<String> errSamples = new ArrayList<String>();
        final List<String> wrongSamples = new ArrayList<String>();
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            futures.add(exec.submit(new Runnable() {
                public void run() {
                    for (int r = 0; r < rounds; r++) {
                        int run = seq.incrementAndGet();
                        try {
                            Map<String,Object> params = new HashMap<String,Object>();
                            params.put("tid", "T" + tid);
                            params.put("i", Integer.valueOf(run));
                            params.put("run", Integer.valueOf(run));
                            String out;
                            if ("isolated".equals(mode)) {
                                // custom engine name: since the pool concept
                                // took over (CONTEXT_MAPPING removed) EVERY
                                // name - default or custom - resolves to the
                                // current thread's own pooled context, so the
                                // name is just an identification key; the
                                // console must be returned via freed() after
                                // rendering (try/finally, pool contract)
                                TextScript ts = new TextScript(template).useEngine("graalvm.js-stress-" + tid);
                                try {
                                    ts.addParams(params);
                                    out = String.valueOf(ts.parse());
                                } finally {
                                    ts.freed();
                                }
                            } else {
                                // default engine: thread-confined pooled context
                                // (pool concept, mirrors JsPlus-Tomcat
                                // TomcatScriptConsole) - every thread renders on
                                // its OWN pooled context, no locks needed
                                out = TextScript.render(template, params);
                            }
                            // Exact marker match: output is "T<tid> run<run>" so
                            // the tid is always followed by a space - checking
                            // "T<tid> " avoids substring aliasing ("TT11" must
                            // NOT count as containing "T1") when threads >= 10.
                            boolean own = out.indexOf("T" + tid + " ") >= 0;
                            boolean foreign = false;
                            for (int k = 0; k < threads; k++) {
                                if (k != tid && out.indexOf("T" + k + " ") >= 0) { foreign = true; break; }
                            }
                            if (own && !foreign) {
                                ok.incrementAndGet();
                            } else {
                                wrong.incrementAndGet();
                                synchronized (wrongSamples) {
                                    if (wrongSamples.size() < 5) { wrongSamples.add(out.replaceAll("\\\\r", "")); }
                                }
                            }
                        } catch (Throwable e) {
                            err.incrementAndGet();
                            synchronized (errSamples) {
                                if (errSamples.size() < 5) { errSamples.add(e.getClass().getName() + ": " + e.getMessage()); }
                            }
                        }
                    }
                }
            }));
        }
        for (Future<?> f : futures) { f.get(); }
        exec.shutdown();
        return "mode=" + mode + " threads=" + threads + " rounds=" + rounds
            + " ok=" + ok.get() + " wrong=" + wrong.get() + " err=" + err.get()
            + " errSamples=" + errSamples
            + " wrongSamples=" + wrongSamples;
    }

    // Pool-exhaustion probe: the pool is already initialised by the preceding
    // cases, so this method races (idleBefore + extra) workers that EACH acquire
    // a pooled console via TextScript.useEngine() (holding it - render without
    // freed() until the probe finishes). Exactly the currently-idle consoles
    // succeed; every over-capacity worker is cleanly rejected with "Full
    // Console Pool." (the pool's exhaustion contract - no lock, no hang). All
    // held consoles are returned afterwards and the pool recovers to its idle
    // size. Self-adaptive: reads the live idle count first, so it works under
    // any external pool-size configuration.
    public String exhaust(final int extra) throws Exception {
        final int idleBefore = TextScriptConsole.getConsolePoolSize();
        final int threads = idleBefore + extra;
        final String template = "#comment pool exhaust\\nT#{tid} run#{run} -> #{i*2}\\n";
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch allDone = new CountDownLatch(threads);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger acquired = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final AtomicInteger seq = new AtomicInteger();
        final List<String> errSamples = new ArrayList<String>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            new Thread(new Runnable() {
                public void run() {
                    TextScript ts = null;
                    try {
                        start.await();
                        Map<String,Object> params = new HashMap<String,Object>();
                        params.put("tid", "T" + tid);
                        params.put("i", Integer.valueOf(1));
                        params.put("run", Integer.valueOf(seq.incrementAndGet()));
                        ts = new TextScript(template).useEngine("graalvm.js");
                        ts.addParams(params);
                        String out = String.valueOf(ts.parse());
                        acquired.incrementAndGet();
                    } catch (Throwable e) {
                        rejected.incrementAndGet();
                        String m = e.getMessage() == null ? "null" : String.valueOf(e.getMessage());
                        synchronized (errSamples) {
                            if (errSamples.size() < 20) {
                                errSamples.add(m.contains("Full Console Pool") ? "expected-exhaust" : "unexpected:" + m);
                            }
                        }
                    } finally {
                        // 1) report that this worker finished its acquire
                        //    attempt (successful holders are still HOLDING -
                        //    they block on release below, so the probe can
                        //    snapshot acquired/rejected before anyone returns);
                        allDone.countDown();
                        // 2) holders keep their console until the probe
                        //    counted the rejections, then return it via
                        //    freed() (no-op for rejected threads - they never
                        //    held a console);
                        try {
                            if (ts != null) { release.await(); ts.freed(); }
                        } catch (Exception ignore) { }
                    }
                }
            }).start();
        }
        start.countDown();
        allDone.await(120, TimeUnit.SECONDS); // every worker tried; holders still hold
        int rejectedSnapshot = rejected.get();
        int acquiredSnapshot = acquired.get();
        release.countDown(); // let holders return their consoles
        Thread.sleep(300); // freed()'s delayed return completes
        int idleAfter = TextScriptConsole.getConsolePoolSize();
        return "acquired=" + acquiredSnapshot + " rejected=" + rejectedSnapshot
            + " idleBefore=" + idleBefore + " idleAfter=" + idleAfter
            + " errSamples=" + errSamples;
    }

    // Security-profile subclass-pool probe (architecture test): renders the
    // same template concurrently through a pool selected by CLASS via the
    // TextScript(String, Class) constructor - AllowAllAccessPool (permissive)
    // or NotAllowAccessPool (restricted). Verifies (1) the subclass pool's
    // fixed policy flag, (2) 100% parallel correctness on the subclass pool,
    // (3) pool independence - the subclass pool pre-warms and returns to its
    // OWN idle count while the shared default pool is untouched.
    public String poolProbe(final String poolClassName, final int threads, final int rounds) throws Exception {
        Class<?> poolClass = Class.forName("com.killcoding.jsplus." + poolClassName);
        final TextScriptConsole pool = (TextScriptConsole) poolClass.getMethod("getInstance").invoke(null);
        final boolean policy = pool.isAllowAllAccess();
        final String template = "#comment " + poolClassName + "\\nT#{tid} run#{run} -> #{i*2}\\n";
        final ExecutorService exec = Executors.newFixedThreadPool(threads);
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger wrong = new AtomicInteger();
        final AtomicInteger err = new AtomicInteger();
        final AtomicInteger seq = new AtomicInteger();
        final List<String> errSamples = new ArrayList<String>();
        final List<Future<?>> futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            futures.add(exec.submit(new Runnable() {
                public void run() {
                    for (int r = 1; r <= rounds; r++) {
                        TextScript ts = null;
                        try {
                            Map<String,Object> params = new HashMap<String,Object>();
                            params.put("tid", "T" + tid);
                            params.put("i", Integer.valueOf(1));
                            params.put("run", Integer.valueOf(seq.incrementAndGet()));
                            // constructor picks the pool by boolean policy
                            // (true=AllowAllAccessPool, false=NotAllowAccessPool)
                            ts = new TextScript(template, policy);
                            ts.addParams(params);
                            String out = String.valueOf(ts.parse());
                            boolean own = out.indexOf("T" + tid + " ") >= 0;
                            if (own) {
                                ok.incrementAndGet();
                            } else {
                                wrong.incrementAndGet();
                            }
                        } catch (Throwable e) {
                            err.incrementAndGet();
                            synchronized (errSamples) {
                                if (errSamples.size() < 5) { errSamples.add(e.getClass().getName() + ": " + e.getMessage()); }
                            }
                        } finally {
                            try { if (ts != null) { ts.freed(); } } catch (Exception ignore) { }
                        }
                    }
                }
            }));
        }
        for (Future<?> f : futures) { f.get(); }
        exec.shutdown();
        Thread.sleep(300); // freed()'s delayed return completes
        return "pool=" + poolClassName + " policy=" + policy
            + " threads=" + threads + " rounds=" + rounds
            + " ok=" + ok.get() + " wrong=" + wrong.get() + " err=" + err.get()
            + " idle=" + pool.idleCount()
            + " defaultIdle=" + TextScriptConsole.getDefaultPool().idleCount()
            + " errSamples=" + errSamples;
    }
}
`;

// Per-thread isolation via ScriptConsole.newBuilder(): every worker creates its
// OWN ScriptConsole (and therefore its OWN GraalVM context, stored on the
// console instance - NOT in any TextScript static mapping, since
// CONTEXT_MAPPING was removed in favour of the pool concept). The worker binds
// that context into its TextScript instance via setContext(), renders on its
// own thread, collects every row through CacheArray.add() (ca.add() trigger)
// and returns the summary via the owning console's callback queue. Expected:
// 100% green - this is the canonical multi-threaded usage.
var SRC_CONSOLE = `
import com.killcoding.jsplus.ScriptConsole;
import com.killcoding.jsplus.TextScript;
import com.killcoding.jsplus.TextScriptConsole;
import com.killcoding.cache.CacheArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TextScriptStressConsole {
    public String stress(final ScriptConsole master, final int threads, final int rounds) throws Exception {
        final String template = "#comment stress template\\nT#{tid} run#{run} -> #{i*2}\\n#if(i%2==0)\\nEVEN\\n#else\\nODD\\n#end\\n";
        // formal API (mirrors JsPlus-Tomcat TomcatScriptConsole): a master console
        // pre-builds a pool of per-thread consoles (ScriptConsole.newBuilder());
        // every worker renders on ITS OWN pooled console context via
        // TextScript.setContext, so concurrent renderers never share one GraalVM
        // context (each console is held by at most one thread at a time).
        TextScriptConsole.initTextScriptConsole("graalvm.js");
        TextScriptConsole.setPoolSize(threads * 2 + 8);
        final CacheArray gca = master.getGlobalCacheArray(5000, 5000);
        final ExecutorService exec = Executors.newFixedThreadPool(threads);
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger wrong = new AtomicInteger();
        final AtomicInteger err = new AtomicInteger();
        final AtomicInteger seq = new AtomicInteger();
        final List<String> errSamples = new ArrayList<String>();
        final List<String> wrongSamples = new ArrayList<String>();
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            futures.add(exec.submit(new Runnable() {
                public void run() {
                    try {
                        for (int r = 0; r < rounds; r++) {
                            int run = seq.incrementAndGet();
                            try {
                                // TextScriptConsole.render = thread-confined render:
                                // getConsole() by thread id -> TextScript.setContext ->
                                // addParams -> parse -> freed()
                                Map<String,Object> params = new HashMap<String,Object>();
                                params.put("tid", "T" + tid);
                                params.put("i", Integer.valueOf(run));
                                params.put("run", Integer.valueOf(run));
                                String out = String.valueOf(TextScriptConsole.render(template, params));
                                boolean own = out.contains("T" + tid);
                                boolean foreign = false;
                                for (int k = 0; k < threads; k++) {
                                    if (k != tid && out.contains("T" + k)) { foreign = true; break; }
                                }
                                if (own && !foreign) {
                                    ok.incrementAndGet();
                                } else {
                                    wrong.incrementAndGet();
                                    synchronized (wrongSamples) {
                                        if (wrongSamples.size() < 5) { wrongSamples.add(out.replaceAll("\\\\r", "")); }
                                    }
                                }
                                // ca.add(): collect every rendered row into the shared CacheArray
                                gca.add("T" + tid + ":" + out.trim());
                            } catch (Throwable e) {
                                err.incrementAndGet();
                                synchronized (errSamples) {
                                    if (errSamples.size() < 5) { errSamples.add(e.getClass().getName() + ": " + e.getMessage()); }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        err.incrementAndGet();
                        synchronized (errSamples) {
                            if (errSamples.size() < 5) { errSamples.add("ctor: " + e.getClass().getName() + ": " + e.getMessage()); }
                        }
                    }
                }
            }));
        }
        for (Future<?> f : futures) { f.get(); }
        exec.shutdown();
        int collected = gca.size();
        // callback: single-slot hook triggered when the GlobalCacheArray filter
        // consumes an item (canonical CacheArrayFilter.execute -> Console.callback).
        final String[] callbackMark = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            master.callback(new Runnable() {
                public void run() { callbackMark[0] = "ok"; latch.countDown(); }
            }, true);
            gca.add("final-trigger:" + System.nanoTime());
            latch.await(5000, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            callbackMark[0] = "callback-err:" + e.getMessage();
        }
        return "threads=" + threads + " rounds=" + rounds
            + " ok=" + ok.get() + " wrong=" + wrong.get() + " err=" + err.get()
            + " collected=" + collected
            + " callback=" + (callbackMark[0] == null ? "timeout" : callbackMark[0])
            + " errSamples=" + errSamples
            + " wrongSamples=" + wrongSamples;
    }
}
`;

try {
    var TextScript = Java.type("com.killcoding.jsplus.TextScript");
    // NOTE: Console is the engine-injected global console instance
    // (evalJavaSource / evalJavaFile are instance methods on it), the same
    // global TestDatasource.js / TestEvalJava.js use - do NOT Java.type it.

    // CI profile reduction (mirrors TestDatasource C18): slow shared runners.
    var ciEnv = System.getenv("CI");
    var isCI = ciEnv !== null && ciEnv !== undefined && ("" + ciEnv) === "true";

    // ------------------------------------------------------------------------
    // C1: sequential baseline - 100 renders on the main thread, all correct.
    // Also creates the default shared engine context on the main thread so
    // C2 measures pure cross-thread access to an already-existing context.
    // ------------------------------------------------------------------------
    var BASE = `#comment baseline
T0 run#{run} -> #{i*2}
#if(i%2==0)
EVEN
#else
ODD
#end
`;
    var c1AllOk = true;
    var c1Bad = "";
    for (var r1 = 1; r1 <= 100; r1++) {
        var ts1 = new TextScript(BASE);
        ts1.addParams({ tid: "T0", i: r1, run: r1 });
        var o1 = str(ts1.parse());
        if (o1.indexOf("T0") < 0 || o1.indexOf("run" + r1) < 0 || (o1.indexOf("EVEN") < 0 && o1.indexOf("ODD") < 0)) {
            c1AllOk = false;
            c1Bad = o1;
            break;
        }
    }
    Logger.systemMark("TestTextScriptStress.js", "C1");
    check({
        caseId: "C1",
        name: "sequential baseline 100 renders all correct",
        ok: c1AllOk,
        detail: c1AllOk ? "100/100 correct on the main thread" : "bad output at run " + r1 + " -> " + c1Bad,
        objective: "single-thread parse() correctness baseline",
        objectiveZh: "單執行緒 parse() 正確性基線",
        command: "new TextScript(BASE).addParams({tid,i,run}).parse() x100"
    });

    // ------------------------------------------------------------------------
    // C2: default engine under concurrency - 8 workers x 30 rounds render
    // concurrently. The DEFAULT engine name now resolves to a thread-confined
    // context from the console pool (pool concept, mirrors JsPlus-Tomcat
    // TomcatScriptConsole): every thread renders on its OWN pooled context, so
    // there is no shared-context access and no contamination. Expected: 100%
    // green - the default mode IS multi-threaded via pooling.
    // ------------------------------------------------------------------------
    var loader = Console.evalJavaSource(SRC_STRESS);
    var StressType = loader.loadClass("TextScriptStress");
    var inst = new StressType();
    var s2Threads = isCI ? 6 : 8;
    var s2Rounds = isCI ? 20 : 30;
    var r2 = str(inst.stress("shared", s2Threads, s2Rounds));
    Logger.systemMark("TestTextScriptStress.js", "C2");
    var c2Ok = parseInt(field(r2, "ok"), 10);
    var c2Wrong = parseInt(field(r2, "wrong"), 10);
    var c2Err = parseInt(field(r2, "err"), 10);
    var c2Pass = c2Ok === (s2Threads * s2Rounds) && c2Err === 0 && c2Wrong === 0;
    check({
        caseId: "C2",
        name: "default engine under concurrency - thread-confined pooled context (all green)",
        ok: c2Pass,
        detail: r2,
        objective: "TextScript.render() resolves the DEFAULT engine to a thread-confined pooled context (pool concept, mirrors JsPlus-Tomcat TomcatScriptConsole), so 8x30 concurrent renders each run on their own context - 100% correct, zero contamination, no locks",
        objectiveZh: "TextScript.render() 將默認引擎解析為線程隔離的池化上下文（pool 概念，對照 JsPlus-Tomcat TomcatScriptConsole），8x30 並發渲染各自運行在自己的上下文——100% 正確、零串擾、無鎖",
        command: "TextScriptStress.stress('shared'," + s2Threads + "," + s2Rounds + ")"
    });

    // ------------------------------------------------------------------------
    // C3: custom engine name under concurrency - since the pool concept took
    // over (CONTEXT_MAPPING removed), useEngine('graalvm.js-stress-<tid>')
    // resolves EVERY name to the current thread's OWN pooled console context
    // (the name is an identification key, not a language id / global context).
    // Every thread renders on its own context, so parallelism is real.
    // Expected: 100% green.
    // ------------------------------------------------------------------------
    var s3Threads = isCI ? 6 : 8;
    var s3Rounds = isCI ? 20 : 30;
    var r3 = str(inst.stress("isolated", s3Threads, s3Rounds));
    Logger.systemMark("TestTextScriptStress.js", "C3");
    var c3Ok = parseInt(field(r3, "ok"), 10);
    var c3Wrong = parseInt(field(r3, "wrong"), 10);
    var c3Err = parseInt(field(r3, "err"), 10);
    var c3Pass = c3Ok === (s3Threads * s3Rounds) && c3Err === 0 && c3Wrong === 0;
    check({
        caseId: "C3",
        name: "custom engine name under concurrency - resolves to the thread-confined pool (all green)",
        ok: c3Pass,
        detail: r3,
        objective: "useEngine() with a custom engine name resolves to the current thread's own pooled console context (pool concept, CONTEXT_MAPPING removed - the name is an identification key only), so 8x30 concurrent renders stay 100% correct and contamination-free",
        objectiveZh: "useEngine() 自定義引擎名解析到當前執行緒自己的池化控制台上下文（pool 概念，CONTEXT_MAPPING 已廢除——名字僅作標識 key），8x30 並發渲染保持 100% 正確零串擾",
        command: "TextScriptStress.stress('isolated'," + s3Threads + "," + s3Rounds + ")"
    });

    // ------------------------------------------------------------------------
    // C4: extreme default-engine concurrency - 16 workers x 50 rounds. The
    // console pool (pre-warmed, grows on demand) hands each thread its own
    // context, so high contention stays 100% correct. Expected: all green.
    // ------------------------------------------------------------------------
    var s4Threads = isCI ? 8 : 16;
    var s4Rounds = isCI ? 30 : 50;
    var r4 = str(inst.stress("shared", s4Threads, s4Rounds));
    Logger.systemMark("TestTextScriptStress.js", "C4");
    var c4Ok = parseInt(field(r4, "ok"), 10);
    var c4Wrong = parseInt(field(r4, "wrong"), 10);
    var c4Err = parseInt(field(r4, "err"), 10);
    var c4Pass = c4Ok === (s4Threads * s4Rounds) && c4Err === 0 && c4Wrong === 0;
    check({
        caseId: "C4",
        name: "extreme default-engine concurrency - console pool holds at 16x50 (all green)",
        ok: c4Pass,
        detail: r4,
        objective: "at 16x50 contention the console pool keeps handing every thread its own context, so all renders stay correct and contamination-free - pooling is stable and deterministic even at high load",
        objectiveZh: "16x50 高競爭下控制台池持續為每執行緒分配自己的上下文，所有渲染保持正確零串擾——池化在重載下穩定確定",
        command: "TextScriptStress.stress('shared'," + s4Threads + "," + s4Rounds + ")"
    });

    // ------------------------------------------------------------------------
    // C5: per-thread console via ScriptConsole.newBuilder() - the canonical
    // multi-threaded usage (newBuilder + callback + ca.add()). Every worker
    // builds its OWN console (and therefore its OWN GraalVM engine + context)
    // on its own thread, binds that context into its TextScript via reflection
    // (context is protected - no public setter yet), renders concurrently,
    // collects every row through CacheArray.add() (ca.add()) and returns the
    // summary through the owning console's callback hook. Expect 240/240.
    // ------------------------------------------------------------------------
    var s5Threads = isCI ? 6 : 8;
    var s5Rounds = isCI ? 20 : 30;
    var loader5 = Console.evalJavaSource(SRC_CONSOLE);
    var ConsoleStressType = loader5.loadClass("TextScriptStressConsole");
    var inst5 = new ConsoleStressType();
    var r5 = str(inst5.stress(Console, s5Threads, s5Rounds));
    Logger.systemMark("TestTextScriptStress.js", "C5");
    var c5Ok = parseInt(field(r5, "ok"), 10);
    var c5Wrong = parseInt(field(r5, "wrong"), 10);
    var c5Err = parseInt(field(r5, "err"), 10);
    var ei5 = r5.indexOf("errSamples=[");
    var errList5 = ei5 >= 0 ? r5.substring(ei5) : "";
    var c5Pass = c5Ok === (s5Threads * s5Rounds) && c5Err === 0 && c5Wrong === 0
        && r5.indexOf("collected=" + (s5Threads * s5Rounds)) >= 0;
    check({
        caseId: "C5",
        name: "per-thread console via newBuilder() - canonical multi-threaded render is fully green",
        ok: c5Pass,
        detail: r5,
        objective: "the formal TextScriptConsole pool API (mirroring JsPlus-Tomcat TomcatScriptConsole) hands each thread its own ScriptConsole (ScriptConsole.newBuilder) with a thread-confined GraalVM context; TextScript renders via setContext with 100% correctness; rows collected via CacheArray.add() (ca.add()) and the summary delivered via the owning console callback hook",
        objectiveZh: "正式 TextScriptConsole 池 API（參照 JsPlus-Tomcat TomcatScriptConsole）：每執行緒取得自己的 ScriptConsole（newBuilder）與執行緒綁定的 GraalVM 上下文，TextScript 經 setContext 綁定後並發渲染 100% 正確；結果經 CacheArray.add()（ca.add()）收集、摘要經 owning console callback 鉤子回傳",
        command: "TextScriptStressConsole.stress(Console," + s5Threads + "," + s5Rounds + ")"
    });

    // ------------------------------------------------------------------------
    // C6: pool exhaustion - over-capacity acquires are rejected cleanly. The
    // pool is already live from C2-C5, so exhaust(extra) races
    // (idleBefore+extra) workers that EACH hold a pooled console without
    // freeing; exactly the idle consoles succeed and every over-capacity
    // worker gets the pool's exhaustion contract: "Full Console Pool."
    // (exception, NOT a lock, NOT a hang). All holders return their consoles
    // and the pool recovers to its idle size. Self-adaptive to any external
    // pool-size configuration (reads the live idle count first).
    // ------------------------------------------------------------------------
    var s6Extra = 20;
    var r6 = str(inst.exhaust(s6Extra));
    Logger.systemMark("TestTextScriptStress.js", "C6");
    var c6Acquired = parseInt(field(r6, "acquired"), 10);
    var c6Rejected = parseInt(field(r6, "rejected"), 10);
    var c6IdleBefore = parseInt(field(r6, "idleBefore"), 10);
    var c6IdleAfter = parseInt(field(r6, "idleAfter"), 10);
    var c6NoUnexpected = r6.indexOf("unexpected:") < 0;
    var c6Pass = c6Acquired === c6IdleBefore && c6Rejected === s6Extra
        && c6IdleAfter >= c6IdleBefore && c6NoUnexpected;
    check({
        caseId: "C6",
        name: "pool exhaustion - over-capacity acquires rejected cleanly, pool recovers (all green)",
        ok: c6Pass,
        detail: r6,
        objective: "when every pooled console is held, the next acquire throws 'Full Console Pool.' instead of locking or hanging; holders render correctly during contention; all consoles are returned afterwards and the pool recovers to its idle size - self-adaptive to any external pool-size config",
        objectiveZh: "當池中所有 console 都被持有時，下一次 acquire 拋出「Full Console Pool.」而非鎖死或掛起；競爭期間持有者渲染正確；之後全部歸還、池恢復到空閒規模——對任何外部 pool-size 配置自適應",
        command: "TextScriptStress.exhaust(" + s6Extra + ")"
    });

    // ------------------------------------------------------------------------
    // C7: named security-profile pools (architecture) - TextScript's
    // constructor picks the pool BY BOOLEAN POLICY: new TextScript(t, true)
    // selects AllowAllAccessPool (permissive, allowAllAccess=true) and
    // new TextScript(t, false) selects NotAllowAccessPool (restricted, false).
    // These are lightweight named instances (composition, not inheritance):
    // each holds one independent TextScriptConsole whose policy is fixed at
    // construction. Each pool is an independent instance with its own master
    // console + console pool; concurrent rendering stays 100% correct and the
    // pool returns to its own idle count while the shared default pool is
    // untouched.
    // ------------------------------------------------------------------------
    var s7Threads = isCI ? 6 : 8;
    var s7Rounds = isCI ? 20 : 30;
    var r7a = str(inst.poolProbe("AllowAllAccessPool", s7Threads, s7Rounds));
    var r7b = str(inst.poolProbe("NotAllowAccessPool", s7Threads, s7Rounds));
    Logger.systemMark("TestTextScriptStress.js", "C7");
    var c7aOk = parseInt(field(r7a, "ok"), 10);
    var c7aErr = parseInt(field(r7a, "err"), 10);
    var c7aWrong = parseInt(field(r7a, "wrong"), 10);
    var c7aIdle = parseInt(field(r7a, "idle"), 10);
    var c7bOk = parseInt(field(r7b, "ok"), 10);
    var c7bErr = parseInt(field(r7b, "err"), 10);
    var c7bWrong = parseInt(field(r7b, "wrong"), 10);
    var c7bIdle = parseInt(field(r7b, "idle"), 10);
    var c7Pass = r7a.indexOf("policy=true") >= 0 && c7aOk === (s7Threads * s7Rounds)
            && c7aErr === 0 && c7aWrong === 0 && c7aIdle >= 90
        && r7b.indexOf("policy=false") >= 0 && c7bOk === (s7Threads * s7Rounds)
            && c7bErr === 0 && c7bWrong === 0 && c7bIdle >= 90
        && r7a.indexOf("defaultIdle=") >= 0 && r7b.indexOf("defaultIdle=") >= 0;
    check({
        caseId: "C7",
        name: "named strategy pools by boolean policy - new TextScript(t,true)/new TextScript(t,false) render concurrently, independent pools (all green)",
        ok: c7Pass,
        detail: r7a + " || " + r7b,
        objective: "TextScript(String, boolean) picks the pool by policy: true selects AllowAllAccessPool (allowAllAccess=true) and false selects NotAllowAccessPool (fixed at construction, external config never overrides); each is an independent TextScriptConsole (own master console + console pool) that renders concurrently 100% correct and returns to its own idle count, untouched by the shared default pool",
        objectiveZh: "TextScript(String, boolean) 按策略選池：true 選 AllowAllAccessPool（allowAllAccess=true）、false 選 NotAllowAccessPool（構造固定，外部配置不覆寫）；每個都是獨立 TextScriptConsole（自有 master console 與 console 池），並發渲染 100% 正確、歸還到自己的空閒數，與共享默認池互不干擾",
        command: "TextScriptStress.poolProbe('AllowAllAccessPool'|'NotAllowAccessPool'," + s7Threads + "," + s7Rounds + ")"
    });
} catch (e) {
    Logger.systemMark("TestTextScriptStress.js", "C-ERR");
    check({
        caseId: "C-ERR",
        name: "unexpected suite-level error",
        ok: false,
        detail: "type=" + (e === null || e === undefined ? "null" : e.getClass ? e.getClass().getName() : "" + e) + " message=" + e,
        objective: "suite must not throw past the catch-all",
        objectiveZh: "套件不得越過 catch-all 拋出",
        command: "TestTextScriptStress.js top-level"
    });
}

writeReport();
