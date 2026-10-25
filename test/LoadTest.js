// ============================================================================
// JsPlus-Core LoadTest - database CRUD load test (console mode)
// ----------------------------------------------------------------------------
// Run      : test/LoadTest.bat / test/LoadTest.sh (or inside the JsPlus console:
//              Console.eval(<PROJECT_ROOT>/test/LoadTest.js))
// Harness  : Shared plumbing (escapeHtml, DiskFile, System) from TestBase.js.
// Output   : <PROJECT_ROOT>/test_reports/LoadTest-Report.html
//            Dashboard format follows JsPlus-Tomcat's LoadTest.js (dark cards +
//            SVG bars + percentile table), adapted from HTTP load to embedded
//            database CRUD load: every round runs the full row lifecycle
//            INSERT -> SELECT(verify) -> UPDATE -> SELECT(verify) -> DELETE ->
//            SELECT(0 rows) through the CacheDriverExecutor over the runtime
//            project datasource (Console.getDataSource()).
// DB       : testdb (config/DataSource.properties -> jdbc:h2:tcp://localhost:
//            3306/testdb). Schema must be initialized first (LoadTest.bat runs
//            fallback.sql -> change.sql exactly like TestAll.bat).
// Concurrency: serial by design - GraalVM JS forbids worker threads from
//            entering the JS context, so throughput here is single-threaded
//            engine + JDBC latency (the honest number for the embedded core).
// Config via environment (same convention as JsPlus-Tomcat LoadTest.js):
//   LOADTEST_ROUNDS   default 100  rounds per table (each round = 6 ops)
//   LOADTEST_TABLES   default 5    tables to load (1..5)
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

var REPORT_NAME = "LoadTest-Report.html";
var CacheDriverExecutor = Java.type("com.killcoding.datasource.CacheDriverExecutor");

function envOr(name, def) {
    var v = System.getenv(name);
    return (v !== null && v !== undefined && v !== "") ? v : String(def);
}
function pad2(n) { return n < 10 ? "0" + n : String(n); }
function round1(x) { return Math.round(x * 10.0) / 10.0; }
function percentile(sorted, p) {
    if (sorted.length === 0) return 0;
    var idx = Math.ceil(sorted.length * p / 100.0) - 1;
    if (idx < 0) idx = 0;
    if (idx >= sorted.length) idx = sorted.length - 1;
    return sorted[idx];
}
function mapGetIgnoreCase(map, key) {
    if (map === null || map === undefined) return undefined;
    var target = ("" + key).toLowerCase();
    var it = map.keySet().iterator();
    while (it.hasNext()) {
        var javaKey = it.next();
        if (("" + javaKey).toLowerCase() === target) return map.get(javaKey);
    }
    return undefined;
}
// GraalVM JS has no regex literals on this build - pad with string math.
function pad15(n) {
    var s = String(n);
    while (s.length < 15) s = "0" + s;
    return s;
}

print("=== JsPlus-Core Load Test (DB CRUD) ===");
print("JS_PLUS_PATH: " + basePath);

var rounds = parseInt(envOr("LOADTEST_ROUNDS", 100), 10);
var tableCount = parseInt(envOr("LOADTEST_TABLES", 5), 10);
if (rounds < 1) rounds = 1;
if (tableCount < 1) tableCount = 1;
if (tableCount > 5) tableCount = 5;

// ----------------------------------------------------------------------------
// Per-table CRUD specs (same shape as TestTables.js, dynamic ids per round).
// id prefix + pad15(round) -> CHAR(16) primary key.
// ----------------------------------------------------------------------------
var SPECS = [
    { table: "test_user",     prefix: "u",
      insert: function (n) { return "INSERT INTO test_user (id, username, email, age, status, created_at, updated_at, deleted) "
          + "VALUES ('u" + pad15(n) + "', 'load_u" + n + "', 'load_u" + n + "@example.com', 25, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)"; },
      sel1: function (n) { return "SELECT username FROM test_user WHERE id='u" + pad15(n) + "'"; },
      chk1: function (n) { return "load_u" + n; },
      update: function (n) { return "UPDATE test_user SET age=26, status='disabled' WHERE id='u" + pad15(n) + "'"; },
      sel2: function (n) { return "SELECT status FROM test_user WHERE id='u" + pad15(n) + "'"; },
      chk2: function () { return "disabled"; },
      del: function (n) { return "DELETE FROM test_user WHERE id='u" + pad15(n) + "'"; },
      sel0: function (n) { return "SELECT COUNT(*) AS CNT FROM test_user WHERE id='u" + pad15(n) + "'"; },
      cleanup: "DELETE FROM test_user WHERE username LIKE 'load_u%'" },
    { table: "test_product",  prefix: "p",
      insert: function (n) { return "INSERT INTO test_product (id, product_name, category, price, stock, created_at, updated_at, deleted) "
          + "VALUES ('p" + pad15(n) + "', 'load_p" + n + "', 'Load', 9.99, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)"; },
      sel1: function (n) { return "SELECT product_name FROM test_product WHERE id='p" + pad15(n) + "'"; },
      chk1: function (n) { return "load_p" + n; },
      update: function (n) { return "UPDATE test_product SET price=10.99, stock=9 WHERE id='p" + pad15(n) + "'"; },
      sel2: function (n) { return "SELECT stock FROM test_product WHERE id='p" + pad15(n) + "'"; },
      chk2: function () { return "9"; },
      del: function (n) { return "DELETE FROM test_product WHERE id='p" + pad15(n) + "'"; },
      sel0: function (n) { return "SELECT COUNT(*) AS CNT FROM test_product WHERE id='p" + pad15(n) + "'"; },
      cleanup: "DELETE FROM test_product WHERE product_name LIKE 'load_p%'" },
    { table: "test_order",    prefix: "o",
      insert: function (n) { return "INSERT INTO test_order (id, order_no, user_id, product_id, quantity, amount, status, created_at, updated_at, deleted) "
          + "VALUES ('o" + pad15(n) + "', 'LOAD-ORD-" + n + "', 'u000000000000001', 'p000000000000001', 1, 5.00, 'pending', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)"; },
      sel1: function (n) { return "SELECT order_no FROM test_order WHERE id='o" + pad15(n) + "'"; },
      chk1: function (n) { return "LOAD-ORD-" + n; },
      update: function (n) { return "UPDATE test_order SET quantity=2, amount=10.00, status='paid' WHERE id='o" + pad15(n) + "'"; },
      sel2: function (n) { return "SELECT status FROM test_order WHERE id='o" + pad15(n) + "'"; },
      chk2: function () { return "paid"; },
      del: function (n) { return "DELETE FROM test_order WHERE id='o" + pad15(n) + "'"; },
      sel0: function (n) { return "SELECT COUNT(*) AS CNT FROM test_order WHERE id='o" + pad15(n) + "'"; },
      cleanup: "DELETE FROM test_order WHERE order_no LIKE 'LOAD-ORD-%'" },
    { table: "test_log",      prefix: "l",
      insert: function (n) { return "INSERT INTO test_log (id, level, source, message, log_time, created_at, updated_at, deleted) "
          + "VALUES ('l" + pad15(n) + "', 'INFO', 'LoadTest.js', 'load msg " + n + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)"; },
      sel1: function (n) { return "SELECT level FROM test_log WHERE id='l" + pad15(n) + "'"; },
      chk1: function () { return "INFO"; },
      update: function (n) { return "UPDATE test_log SET level='WARN', message='load msg " + n + " updated' WHERE id='l" + pad15(n) + "'"; },
      sel2: function (n) { return "SELECT level FROM test_log WHERE id='l" + pad15(n) + "'"; },
      chk2: function () { return "WARN"; },
      del: function (n) { return "DELETE FROM test_log WHERE id='l" + pad15(n) + "'"; },
      sel0: function (n) { return "SELECT COUNT(*) AS CNT FROM test_log WHERE id='l" + pad15(n) + "'"; },
      cleanup: "DELETE FROM test_log WHERE source='LoadTest.js'" },
    { table: "test_kv",       prefix: "k",
      insert: function (n) { return "INSERT INTO test_kv (id, kv_key, kv_value, remark, created_at, updated_at, deleted) "
          + "VALUES ('k" + pad15(n) + "', 'load_k" + n + "', 'v" + n + "', 'load', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)"; },
      sel1: function (n) { return "SELECT kv_value FROM test_kv WHERE id='k" + pad15(n) + "'"; },
      chk1: function (n) { return "v" + n; },
      update: function (n) { return "UPDATE test_kv SET kv_value='v" + n + "b', remark='load-updated' WHERE id='k" + pad15(n) + "'"; },
      sel2: function (n) { return "SELECT kv_value FROM test_kv WHERE id='k" + pad15(n) + "'"; },
      chk2: function (n) { return "v" + n + "b"; },
      del: function (n) { return "DELETE FROM test_kv WHERE id='k" + pad15(n) + "'"; },
      sel0: function (n) { return "SELECT COUNT(*) AS CNT FROM test_kv WHERE id='k" + pad15(n) + "'"; },
      cleanup: "DELETE FROM test_kv WHERE kv_key LIKE 'load_k%'" }
];

// ----------------------------------------------------------------------------
// Measure & assertion helpers over the shared CacheDriverExecutor.
// Every op is timed (latencyMs) and recorded into the global + per-table
// latency arrays. A check mismatch or SQL exception counts as a failure.
// ----------------------------------------------------------------------------
var conn = null;
var cde = null;
var loadErr = "";
try {
    conn = Console.getDataSource().getConnection();
    cde = new CacheDriverExecutor(conn);
    var t = cde.first(
        "SELECT COUNT(*) AS CNT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'");
    if (t === null || t === undefined) throw "testdb not reachable via Console.getDataSource()";
} catch (e) {
    loadErr = "" + e;
}

var globalLat = [];           // latency ms of every op
var failedOps = 0;
var totalOps = 0;
var errors = [];
var tables = [];              // per-table stats {table, ops, pass, fail, lat[], ms[]}

function recordLat(lat) {
    globalLat.push(lat);
    if (globalLat.length > 1000000) globalLat.shift(); // memory guard
}
function recordOp(tableStat, lat, ok) {
    tableStat.ops++;
    tableStat.lat.push(lat);
    tableStat.ms.push(lat);
    recordLat(lat);
    if (ok) tableStat.pass++;
    else {
        tableStat.fail++;
        failedOps++;
    }
}

function runPhase(tableStat, spec, n, label, sqlFn, expectFn) {
    var sql = "";
    var ok = false;
    var detail = "";
    var t0 = System.nanoTime();
    try {
        sql = sqlFn();
        if (sql.indexOf("SELECT") === 0) {
            var m = cde.first(sql);
            var got = null;
            if (m !== null && m !== undefined) {
                var keys = m.keySet().iterator();
                if (keys.hasNext()) got = m.get(keys.next());
            }
            var exp = expectFn();
            ok = (got !== null && got !== undefined && ("" + got) === ("" + exp));
            detail = ok ? "expected=" + exp + " got=" + got
                        : "expected=" + exp + " got=" + (got === null ? "null" : got);
        } else {
            cde.execute(sql);
            ok = true;
            detail = "rows affected";
        }
    } catch (e) {
        ok = false;
        detail = "" + e;
        if (errors.length < 20) errors.push(label + " " + spec.table + " r" + n + ": " + e);
    }
    var lat = (System.nanoTime() - t0) / 1000000.0;
    recordOp(tableStat, lat, ok);
    if (!ok) print("[FAIL] " + label + " " + spec.table + " r" + n + " -> " + detail);
}

function countFrom(m) {
    if (m === null || m === undefined) return -1;
    var v = mapGetIgnoreCase(m, "CNT");
    return (v === undefined || v === null) ? -1 : parseInt("" + v);
}

var startNanos = System.nanoTime();
var doneTables = 0;
if (loadErr.length === 0) {
    for (var s = 0; s < tableCount && s < SPECS.length; s++) {
        var spec = SPECS[s];
        var tableStat = { table: spec.table, ops: 0, pass: 0, fail: 0, lat: [], ms: [] };
        tables.push(tableStat);
        for (var n = 1; n <= rounds; n++) {
            runPhase(tableStat, spec, n, "INSERT", spec.insert, null);
            runPhase(tableStat, spec, n, "SELECT1", spec.sel1, spec.chk1);
            runPhase(tableStat, spec, n, "UPDATE", spec.update, null);
            runPhase(tableStat, spec, n, "SELECT2", spec.sel2, spec.chk2);
            runPhase(tableStat, spec, n, "DELETE", spec.del, null);
            runPhase(tableStat, spec, n, "SELECT0", spec.sel0, function () { return 0; });
        }
        // safety cleanup for any residue (idempotent)
        try { cde.execute(spec.cleanup); } catch (e2) {}
        doneTables++;
        print("[LoadTest] " + spec.table + ": " + tableStat.ops + " ops, "
            + tableStat.pass + " pass, " + tableStat.fail + " fail");
    }
} else {
    print("[LoadTest] DB not reachable: " + loadErr);
}

var elapsedMs = (System.nanoTime() - startNanos) / 1000000.0;

// ----------------------------------------------------------------------------
// Aggregate (global latency = every op across all tables).
// ----------------------------------------------------------------------------
function statsOf(arr) {
    var sorted = arr.slice(0).sort(function (a, b) { return a - b; });
    var avg = sorted.length === 0 ? 0 : sorted.reduce(function (a, b) { return a + b; }, 0) / sorted.length;
    return {
        n: sorted.length,
        avg: avg,
        p50: percentile(sorted, 50),
        p90: percentile(sorted, 90),
        p95: percentile(sorted, 95),
        p99: percentile(sorted, 99),
        max: sorted.length === 0 ? 0 : sorted[sorted.length - 1]
    };
}
var g = statsOf(globalLat);
totalOps = globalLat.length;
var elapsedSec = Math.max(elapsedMs / 1000.0, 0.001);
var qps = totalOps / elapsedSec;

var now = new Date();
var ts = now.getFullYear() + "-" + pad2(now.getMonth() + 1) + "-" + pad2(now.getDate())
       + " " + pad2(now.getHours()) + ":" + pad2(now.getMinutes()) + ":" + pad2(now.getSeconds());

// --- HTML dashboard (static, offline; format follows JsPlus-Tomcat LoadTest) ---
var html = buildDashboard(rounds, tableCount, ts, Math.round(elapsedMs),
    totalOps, totalOps - failedOps, failedOps, round1(qps),
    Math.round(g.avg), Math.round(g.p50), Math.round(g.p90), Math.round(g.p95),
    Math.round(g.p99), Math.round(g.max), tables, globalLat, errors);

try {
    var File = Java.type("java.io.File");
    var reportDir = new File(basePath + "../test_reports");
    if (!reportDir.exists()) reportDir.mkdirs();
    var reportPath = basePath + "../test_reports/" + REPORT_NAME;
    var report = new DiskFile(reportPath);
    report.write(html, "UTF-8");
    print("Dashboard written to " + reportPath);
} catch (e) {
    print("[WARN] Could not write load test report: " + e);
}

print("Result: success=" + (totalOps - failedOps) + " failed=" + failedOps
    + " qps=" + round1(qps) + " avg=" + Math.round(g.avg) + "ms p50=" + Math.round(g.p50)
    + "ms p90=" + Math.round(g.p90) + "ms p99=" + Math.round(g.p99) + "ms max=" + Math.round(g.max) + "ms");

// Exit this JVM directly WITHOUT writing the shared exit.tmp signal file
// (ScriptConsole.exit() would write <JS_PLUS_PATH>/.tmp/exit.tmp, which every
// other JsPlus JVM watching it - including the H2 ghost started by
// BG_StartH2 - would detect and shut down together with this JVM). Same
// convention as TestAll.js: only in launcher mode (JSPLUS_TEST_EXIT=1), so a
// Console.eval() session is never killed.
if (System.getenv("JSPLUS_TEST_EXIT") === "1") {
    System.exit(0);
}

// ============================================================================
// Dashboard builder - static HTML/CSS + inline SVG only (no JS, no CDN).
// ============================================================================
function buildDashboard(tRounds, tTables, tTs, tElapsedMs, tTotal, tSuccess, tFailed,
                        tQps, tAvg, tP50, tP90, tP95, tP99, tMax, tTablesArr,
                        tGlobalLat, tErrors) {
    var sb = "";
    sb += "<!DOCTYPE html>\n<html lang=\"zh-HK\">\n<head>\n<meta charset=\"UTF-8\">\n";
    sb += "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n";
    sb += "<title>JsPlus-Core Load Test Dashboard</title>\n";
    sb += "<style>\n";
    sb += "body{font-family:Segoe UI,Arial,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:24px;}\n";
    sb += "h1{font-size:22px;margin:0 0 4px;color:#f8fafc;}\n";
    sb += ".sub{color:#94a3b8;font-size:13px;margin-bottom:20px;}\n";
    sb += ".cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-bottom:20px;}\n";
    sb += ".card{background:#1e293b;border:1px solid #334155;border-radius:10px;padding:14px;}\n";
    sb += ".card .k{font-size:11px;color:#94a3b8;text-transform:uppercase;letter-spacing:.5px;}\n";
    sb += ".card .v{font-size:24px;font-weight:600;margin-top:4px;color:#f8fafc;}\n";
    sb += ".card.ok .v{color:#34d399;}\n.card.bad .v{color:#f87171;}\n";
    sb += ".panel{background:#1e293b;border:1px solid #334155;border-radius:10px;padding:16px;margin-bottom:16px;}\n";
    sb += ".panel h2{font-size:15px;margin:0 0 12px;color:#cbd5e1;}\n";
    sb += "table{border-collapse:collapse;width:100%;font-size:13px;}\n";
    sb += "th,td{text-align:left;padding:6px 10px;border-bottom:1px solid #334155;}\n";
    sb += "th{color:#94a3b8;font-weight:500;}\n";
    sb += ".err{color:#f87171;font-family:Consolas,monospace;font-size:12px;}\n";
    sb += ".bar-row{display:flex;align-items:center;gap:8px;margin-bottom:4px;}\n";
    sb += ".bar-label{width:70px;font-size:11px;color:#94a3b8;text-align:right;}\n";
    sb += ".bar-track{flex:1;background:#0f172a;border-radius:4px;height:16px;overflow:hidden;}\n";
    sb += ".bar-fill{height:100%;background:#38bdf8;border-radius:4px;}\n";
    sb += "</style>\n</head>\n<body>\n";

    sb += "<h1>JsPlus-Core Load Test Dashboard</h1>\n";
    sb += "<div class=\"sub\">DB: <b>testdb</b> (H2 tcp) &nbsp;|&nbsp; Rounds: <b>" + tRounds
        + "</b>/table &nbsp;|&nbsp; Tables: <b>" + tTables + "</b> &nbsp;|&nbsp; "
        + "Elapsed: <b>" + round1(tElapsedMs / 1000.0) + "s</b> &nbsp;|&nbsp; "
        + "Run at: <b>" + tTs + "</b></div>\n";

    var errRate = tTotal === 0 ? 0 : tFailed * 100.0 / tTotal;
    sb += "<div class=\"cards\">\n";
    sb += card("Total Ops", String(tTotal), "");
    sb += card("Success", String(tSuccess), "");
    sb += card("Failed", String(tFailed), tFailed > 0 ? "bad" : "");
    sb += card("Error Rate", round1(errRate) + "%", tFailed > 0 ? "bad" : "");
    sb += card("OPS", String(round1(tQps)), "");
    sb += card("Avg", tAvg + " ms", "");
    sb += card("P50", tP50 + " ms", "");
    sb += card("P90", tP90 + " ms", "");
    sb += card("P99", tP99 + " ms", "");
    sb += card("Max", tMax + " ms", "");
    sb += "</div>\n";

    // Per-table panel
    sb += "<div class=\"panel\"><h2>Per-Table CRUD Result</h2>\n";
    sb += "<table><tr><th>Table</th><th>Ops</th><th>Pass</th><th>Fail</th>"
        + "<th>Avg (ms)</th><th>P50</th><th>P90</th><th>Max</th></tr>\n";
    for (var i = 0; i < tTablesArr.length; i++) {
        var ts_ = tTablesArr[i];
        var st = statsOf(ts_.ms);
        sb += "<tr><td>" + escapeHtml(ts_.table) + "</td><td>" + ts_.ops + "</td><td>"
            + ts_.pass + "</td><td>" + (ts_.fail > 0 ? "<span class=\"err\">" : "") + ts_.fail
            + (ts_.fail > 0 ? "</span>" : "") + "</td><td>" + Math.round(st.avg)
            + "</td><td>" + Math.round(st.p50) + "</td><td>" + Math.round(st.p90)
            + "</td><td>" + Math.round(st.max) + "</td></tr>\n";
    }
    sb += "</table>\n</div>\n";

    // Global latency histogram (50 ms buckets, horizontal SVG-free bars)
    sb += "<div class=\"panel\"><h2>Latency Distribution (50 ms buckets, all ops)</h2>\n";
    sb += histBars(tGlobalLat, 50);
    sb += "</div>\n";

    // Percentile table
    sb += "<div class=\"panel\"><h2>Latency Percentiles</h2>\n";
    sb += "<table><tr><th>Metric</th><th>Value</th></tr>\n";
    sb += "<tr><td>Average</td><td>" + tAvg + " ms</td></tr>\n";
    sb += "<tr><td>P50</td><td>" + tP50 + " ms</td></tr>\n";
    sb += "<tr><td>P90</td><td>" + tP90 + " ms</td></tr>\n";
    sb += "<tr><td>P95</td><td>" + tP95 + " ms</td></tr>\n";
    sb += "<tr><td>P99</td><td>" + tP99 + " ms</td></tr>\n";
    sb += "<tr><td>Max</td><td>" + tMax + " ms</td></tr>\n";
    sb += "</table>\n</div>\n";

    // Errors
    if (tErrors.length > 0) {
        sb += "<div class=\"panel\"><h2>Errors (first 20)</h2>\n";
        sb += "<div class=\"err\">" + tErrors.map(function (e) { return escapeHtml(e); }).join("<br>") + "</div>\n";
        sb += "</div>\n";
    }

    // Config snapshot (DataSource.properties, pool-related lines)
    sb += "<div class=\"panel\"><h2>Config Snapshot (DataSource.properties)</h2>\n";
    var cfg = dsConfigSnapshot();
    sb += "<div style=\"font-family:Consolas,monospace;font-size:12px;color:#94a3b8\">"
        + cfg.map(function (l) { return escapeHtml(l); }).join("<br>") + "</div>\n";
    sb += "</div>\n";

    sb += "</body>\n</html>\n";
    return sb;
}

function card(k, v, cls) {
    return "<div class=\"card " + cls + "\"><div class=\"k\">" + k
        + "</div><div class=\"v\">" + v + "</div></div>\n";
}

// Horizontal bars for the latency histogram.
function histBars(lats, bucketMs) {
    if (lats.length === 0) return "<div class=\"err\">No latency samples.</div>";
    var hist = {};
    for (var i = 0; i < lats.length; i++) {
        var b = Math.floor(lats[i] / bucketMs);
        hist[b] = (hist[b] || 0) + 1;
    }
    var buckets = Object.keys(hist).sort(function (a, b2) { return parseInt(a, 10) - parseInt(b2, 10); });
    var maxV = 0;
    for (var j = 0; j < buckets.length; j++) maxV = Math.max(maxV, hist[buckets[j]]);
    var out = "<div style=\"max-height:260px;overflow-y:auto\">\n";
    for (var k = 0; k < buckets.length; k++) {
        var v = hist[buckets[k]];
        var pct = maxV === 0 ? 0 : Math.round(v * 100.0 / maxV);
        out += "<div class=\"bar-row\"><div class=\"bar-label\">"
            + (parseInt(buckets[k], 10) * bucketMs) + "+ms</div><div class=\"bar-track\">"
            + "<div class=\"bar-fill\" style=\"width:" + pct + "%\"></div></div>"
            + "<div style=\"font-size:12px\">" + v + "</div></div>\n";
    }
    out += "</div>\n";
    return out;
}

// Read key pool/connection lines of <JS_PLUS_PATH>/config/DataSource.properties.
function dsConfigSnapshot() {
    var lines = [];
    try {
        var Files = Java.type("java.nio.file.Files");
        var Paths = Java.type("java.nio.file.Paths");
        var content = Files.readString(Paths.get(basePath + "config/DataSource.properties"));
        var arr = content.split("\n");
        for (var i = 0; i < arr.length; i++) {
            var ln = arr[i].trim();
            if (ln.length === 0 || ln.charAt(0) === "#") continue;
            lines.push(ln);
        }
    } catch (e) {
        lines.push("(cannot read DataSource.properties: " + e + ")");
    }
    return lines;
}
