// ============================================================================
// JsPlus-Core TestBase - shared test harness for the console-mode test suites
// ----------------------------------------------------------------------------
// Provides the common plumbing used by the console-mode API test suites:
//   - Java type bindings (DiskFile, HashMap, ...)
//   - pass/fail counters + beginTest() reset
//   - check({name, ok, detail, objective, objectiveZh, command}) helper
//   - dbVerify(sqlFn[, url, username, password]) — run a native SQL SELECT
//     through the project's standard data path (DriverDataSource ->
//     CacheDriverExecutor -> CacheArray) and append the result to the detail
//     line (JsPlus-Tomcat's dbVerify, adapted to Core's data-access shape)
//   - writeReport() -> <PROJECT_ROOT>/docs/latest/report/<REPORT_NAME>.html via DiskFile
//   - writeMd()     -> <PROJECT_ROOT>/docs/latest/report/<REPORT_NAME>.md via DiskFile
//   - writeCiSummary() -> <PROJECT_ROOT>/docs/latest/report/<REPORT_NAME>.md (CI-facing)
//   - configSnapshot() -> reads <JS_PLUS_PATH>/config/Logger.properties
// Loaded by each suite (TestConsole.js / TestCache.js / TestFile.js / ...):
//   load(System.getProperty("JS_PLUS_PATH") + "../test/TestBase.js")
// NOTE: Re-loading this file is harmless (var redeclaration is legal in JS);
// always call beginTest() at the start of each run to reset counters.
// The Tomcat-only helpers of JsPlus-Tomcat (callTomcat/curlStatus) are
// intentionally NOT carried over: JsPlus-Core has no server, no web layer
// and no global `tomcat` object.
// ============================================================================

var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var StringBuilder = Java.type("java.lang.StringBuilder");
var DiskFile = Java.type("com.killcoding.file.DiskFile");

// Console mode = the suite is running inside the JsPlus console (global Console
// object exists). Standalone (-Env script -Task) runs have the same Console
// binding, so this flag is informational only (used in the report meta line).
var CONSOLE_MODE = (typeof Console !== "undefined" && Console !== null);

var suiteStart = Date.now();
var passCount = 0;
var failCount = 0;
var failures = [];
var results = [];
// Structured dbVerify outcome {sql, columns, rows} filled by dbVerify() and
// consumed by check() into the current result row (for the report render).
var dbvResult = null;

// Reset counters - call at the start of every test run (from each suite).
function beginTest() {
    suiteStart = Date.now();
    passCount = 0;
    failCount = 0;
    failures = [];
    results = [];
    dbvResult = null;
}

function escapeHtml(s) {
    if (s === null || s === undefined) {
        return "";
    }
    return Java.type("java.lang.String").valueOf(s)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

// Markdown escaping for table cells: escape pipes (column separator) and
// backslashes so embedded values never break the report table; newlines
// become " / " (raw HTML like <br> is avoided - GitHub's table renderer is
// unreliable with inline HTML inside cells).
function escapeMd(s) {
    if (s === null || s === undefined) {
        return "";
    }
    return Java.type("java.lang.String").valueOf(s)
        .replaceAll("\\", "\\\\")
        .replaceAll("|", "\\|")
        .replaceAll("\r\n", " / ")
        .replaceAll("\r", " / ")
        .replaceAll("\n", " / ");
}

function check(params) {
    var name = params.name;
    var ok = params.ok;
    var detail = params.detail;
    var objective = params.objective;
    var objectiveZh = params.objectiveZh;
    var command = params.command;
    var caseId = params.caseId;
    var d = (detail === null || detail === undefined) ? (ok ? "" : "unexpected result") : ("" + detail);
    // Long messages would flood the console; truncate the console line but
    // keep the full detail in the HTML report (results[] stores d untruncated).
    var line = (ok ? "[PASS] " : "[FAIL] ") + name + (d.length > 0 ? " -> " + d : "");
    if (line.length > 500) {
        line = line.substring(0, 500) + " ... (truncated, see HTML report)";
    }
    if (ok) {
        passCount++;
    } else {
        failCount++;
        failures.push(name);
    }
    print(line);
    results.push({
        caseId: (caseId === null || caseId === undefined) ? "" : ("" + caseId),
        name: name,
        ok: ok,
        detail: d,
        objective: (objective === null || objective === undefined) ? "" : ("" + objective),
        objectiveZh: (objectiveZh === null || objectiveZh === undefined) ? "" : ("" + objectiveZh),
        command: (command === null || command === undefined) ? "" : ("" + command),
        dbv: dbvResult
    });
    dbvResult = null;
}

// Optional DB verification: run a native SQL SELECT against the database so
// testers can verify the persisted table state directly (JsPlus-Tomcat's
// dbVerify, adapted to Core). sqlFn must return a SELECT statement.
// The connection MUST come from Console.getDataSource() - the runtime's
// configured project datasource (config/DataSource.properties ->
// jdbc:h2:tcp://localhost:3306/testdb, sample_user), i.e. the same source
// the runtime uses (ScriptConsole.getDataSource() reads DataSource.properties
// into a DriverDataSource). The structured outcome {sql, columns, rows}
// (max 30 rows) is stored in dbvResult for writeReport to render as a
// nested table; the returned inline text keeps the console output readable.
// NOTE: the query runs over plain JDBC (Connection.createStatement) instead
// of CacheDriverExecutor.find(): Core's find() writes results into a
// CacheArray whose worker-thread/filter design is async and is not suited to
// the suite's synchronous assertion read (the GraalJS context forbids
// multi-threaded access, and the filter thread removes entries / clears the
// cache as it processes them). A plain JDBC query is deterministic here
// while checking the same persisted state; the connection itself still
// comes from the project datasource via Console.getDataSource().
function dbVerify(sqlFn) {
    var conn = null;
    var stmt = null;
    var rs = null;
    var sql;
    var result = { sql: "", columns: [], rows: [] };
    try {
        sql = sqlFn();
        conn = Console.getDataSource().getConnection();
        stmt = conn.createStatement();
        rs = stmt.executeQuery(sql);
        var rsmd = rs.getMetaData();
        var c = rsmd.getColumnCount();
        for (var k = 0; k < c; k++) {
            result.columns.push(rsmd.getColumnLabel(k + 1) + "");
        }
        while (rs.next() && result.rows.length < 30) {
            var row = [];
            for (var k = 0; k < c; k++) {
                var v = rs.getObject(k + 1);
                row.push(v === null ? "null" : ("" + v));
            }
            result.rows.push(row);
        }
        result.sql = sql;
        dbvResult = result;
        var sb = new StringBuilder();
        for (var i = 0; i < result.rows.length; i++) {
            sb.append((i > 0 ? "\n" : "") + "[" + result.rows[i].join(" ") + "]");
        }
        return "\n | dbVerify: " + sql + "\n   -> " + (result.rows.length > 0 ? sb.toString() : "(0 rows)");
    } catch (e) {
        result.sql = (sql !== undefined ? sql : "<sqlFn failed>");
        result.error = "" + e;
        dbvResult = result;
        return "\n | dbVerify: " + result.sql + "\n   -> (query failed) " + e;
    } finally {
        if (rs !== null && rs !== undefined) {
            try { rs.close(); } catch (e2) {}
        }
        if (stmt !== null && stmt !== undefined) {
            try { stmt.close(); } catch (e3) {}
        }
        if (conn !== null && conn !== undefined) {
            try { conn.close(); } catch (e4) {}
        }
    }
}

// Read the runtime log config lines of <JS_PLUS_PATH>/config/Logger.properties
// so every report carries a config snapshot - environment drift (a missing
// config or a wrong Level) becomes visible instead of silently changing the
// logging suite's expectations.
function configSnapshot() {
    var lines = [];
    try {
        var Files = Java.type("java.nio.file.Files");
        var Paths = Java.type("java.nio.file.Paths");
        var propsPath = System.getProperty("JS_PLUS_PATH") + "config/Logger.properties";
        var content = Files.readString(Paths.get(propsPath));
        var arr = content.split("\n");
        for (var i = 0; i < arr.length; i++) {
            var ln = arr[i].trim();
            if (ln.length === 0 || ln.charAt(0) === "#") {
                continue;
            }
            lines.push(ln);
        }
    } catch (e) {
        lines.push("(cannot read Logger.properties: " + e + ")");
    }
    return lines;
}

function writeReport() {
    var durationMs = Date.now() - suiteStart;
    var summaryClass = failCount === 0 ? "summary-pass" : "summary-fail";
    // REPORT_TITLE may be set by the test suite (e.g. TestCache.js);
    // fall back to a generic title so TestBase works standalone too.
    var reportTitle = (typeof REPORT_TITLE !== "undefined" && REPORT_TITLE !== null && REPORT_TITLE.length > 0)
        ? REPORT_TITLE : "JsPlus-Core Test Report";
    var rows = "";
    for (var i = 0; i < results.length; i++) {
        var cls = results[i].ok ? "pass" : "fail";
        var badge = results[i].ok ? "PASS" : "FAIL";
        var detail = escapeHtml(results[i].detail);
        var objective = escapeHtml(results[i].objective);
        var objectiveZh = escapeHtml(results[i].objectiveZh);
        var command = escapeHtml(results[i].command);
        // dbVerify outcome rendered the JsPlus-Tomcat way: a yellow-tinted
        // nested table (row num + columns) under the detail cell.
        var dbvHtml = "";
        var dbv = results[i].dbv;
        if (dbv !== null && dbv !== undefined) {
            var dbvSql = escapeHtml(dbv.sql);
            if (dbv.error !== undefined && dbv.error !== null && ("" + dbv.error).length > 0) {
                dbvHtml = "<span class=\"dbv\">dbVerify: " + dbvSql
                    + "<br>(query failed) " + escapeHtml(dbv.error) + "</span>";
            } else if (dbv.rows.length === 0) {
                dbvHtml = "<span class=\"dbv\">dbVerify: " + dbvSql + "<br>(0 rows)</span>";
            } else {
                dbvHtml = "<span class=\"dbv\">dbVerify: " + dbvSql
                    + "<br><table class=\"dbv-table\"><tr><th>row num</th>";
                for (var ci = 0; ci < dbv.columns.length; ci++) {
                    dbvHtml += "<th>" + escapeHtml(dbv.columns[ci]) + "</th>";
                }
                dbvHtml += "</tr>";
                for (var ri = 0; ri < dbv.rows.length; ri++) {
                    dbvHtml += "<tr><td>" + (ri + 1) + "</td>";
                    for (var cj = 0; cj < dbv.columns.length; cj++) {
                        dbvHtml += "<td>" + escapeHtml(dbv.rows[ri][cj]) + "</td>";
                    }
                    dbvHtml += "</tr>";
                }
                dbvHtml += "</table></span>";
            }
        }
        // Sequential, per-row case id: first row = C1, second = C2, ...
        var caseCell = "C" + (i + 1);
        rows += "<tr class=\"" + cls + "\"><td>" + caseCell + "</td><td class=\"name\">" + escapeHtml(results[i].name)
              + (objective.length > 0 ? "<br><span class=\"obj\">" + objective + "</span>" : "")
              + (objectiveZh.length > 0 ? "<br><span class=\"objZh\">" + objectiveZh + "</span>" : "")
              + (command.length > 0 ? "<br><span class=\"cmd\">" + command + "</span>" : "")
              + "</td><td class=\"badge\">" + badge + "</td><td>" + detail + dbvHtml + "</td></tr>\n";
    }
    var cfgLines = configSnapshot();
    var cfgSection = "";
    if (cfgLines.length > 0) {
        cfgSection = "<h2>Config Snapshot (Logger.properties)</h2>\n<pre class=\"cfg\">" + escapeHtml(cfgLines.join("\n")) + "</pre>\n";
    }
    var html = "<!DOCTYPE html>\n"
        + "<html lang=\"en\">\n"
        + "<head>\n"
        + "<meta charset=\"UTF-8\">\n"
        + "<title>" + escapeHtml(reportTitle) + "</title>\n"
        + "<style>\n"
        + "  body { font-family: Consolas, 'Courier New', monospace; background: #f5f6f8; margin: 24px; }\n"
        + "  h1 { font-size: 20px; color: #24292f; }\n"
        + "  .meta { color: #57606a; margin-bottom: 12px; }\n"
        + "  .summary-pass { display: inline-block; padding: 8px 16px; border-radius: 6px; background: #e6ffec; color: #1a7f37; font-weight: bold; margin-bottom: 16px; }\n"
        + "  .summary-fail { display: inline-block; padding: 8px 16px; border-radius: 6px; background: #ffebe9; color: #b42318; font-weight: bold; margin-bottom: 16px; }\n"
        + "  table { border-collapse: collapse; width: 100%; background: #fff; }\n"
        + "  th { background: #eaeef2; text-align: left; padding: 8px 10px; border: 1px solid #d0d7de; }\n"
        + "  td { padding: 6px 10px; border: 1px solid #d0d7de; vertical-align: top; }\n"
        + "  tr.pass td { color: #24292f; }\n"
        + "  tr.pass td.badge { color: #1a7f37; font-weight: bold; }\n"
        + "  tr.fail { background: #ffebe9; }\n"
        + "  tr.fail td.name { color: #b42318; font-weight: bold; }\n"
        + "  tr.fail td.badge { color: #b42318; font-weight: bold; }\n"
        + "  td.name { width: 45%; }\n"
        + "  td.name span.obj { display: block; color: #57606a; font-size: 12px; margin-top: 2px; }\n"
        + "  td.name span.objZh { display: block; color: #8a5a00; font-size: 12px; margin-top: 2px; }\n"
        + "  td.name span.cmd { display: block; color: #8c959f; font-size: 11px; margin-top: 2px; font-family: Consolas, 'Courier New', monospace; word-break: break-all; }\n"
        + "  td.badge { width: 70px; }\n"
        + "  pre.cfg { background: #fff; border: 1px solid #d0d7de; padding: 10px; font-size: 12px; overflow-x: auto; }\n"
        + "  td .dbv { display: block; color: #9a6700; font-size: 12px; margin-top: 8px; word-break: break-all; }\n"
        + "  td .dbv table.dbv-table { border-collapse: collapse; width: auto; margin-top: 4px; font-size: 11px; }\n"
        + "  td .dbv table.dbv-table th { background: #fef3c7; color: #9a6700; padding: 2px 8px; border: 1px solid #d0d7de; text-align: left; font-weight: normal; }\n"
        + "  td .dbv table.dbv-table td { background: #fffdf5; color: #24292f; padding: 2px 8px; border: 1px solid #d0d7de; }\n"
        + "</style>\n"
        + "</head>\n"
        + "<body>\n"
        + "<h1>" + escapeHtml(reportTitle) + "</h1>\n"
        + "<div class=\"meta\">Generated: " + escapeHtml(new Date().toString()) + " &nbsp;|&nbsp; Duration: " + durationMs
        + " ms &nbsp;|&nbsp; Mode: " + (CONSOLE_MODE ? "console" : "standalone") + "</div>\n"
        + "<div class=\"" + summaryClass + "\">" + passCount + " passed / " + failCount + " failed</div>\n"
        + cfgSection
        + "<table>\n"
        + "<tr><th>Case Id</th><th>Test Objective</th><th>Result</th><th>Detail</th></tr>\n"
        + rows
        + "</table>\n"
        + "</body>\n"
        + "</html>\n";
    try {
        // REPORT_NAME may be set by the test suite (e.g. TestCache.js);
        // fall back to a generic name so TestBase works standalone too.
        var reportName = (typeof REPORT_NAME !== "undefined" && REPORT_NAME !== null && REPORT_NAME.length > 0)
            ? REPORT_NAME : "TestAll-Report.html";
        var File = Java.type("java.io.File");
        var reportDir = new File(System.getProperty("JS_PLUS_PATH") + "../docs/latest/report");
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }
        var reportPath = System.getProperty("JS_PLUS_PATH") + "../docs/latest/report/" + reportName;
        var report = new DiskFile(reportPath);
        report.write(html, "UTF-8");
        print("Test report written to " + reportPath);
    } catch (e) {
        print("[WARN] Could not write test report: " + e);
    }
}

// Markdown test report - mirrors writeReport() but emits a GitHub-friendly
// Markdown file into <PROJECT_ROOT>/docs/latest/report/<REPORT_NAME>.md via DiskFile.
function writeMd() {
    var durationMs = Date.now() - suiteStart;
    var reportTitle = (typeof REPORT_TITLE !== "undefined" && REPORT_TITLE !== null && REPORT_TITLE.length > 0)
        ? REPORT_TITLE : "JsPlus-Core Test Report";
    var md = "# " + escapeMd(reportTitle) + "\n\n";
    md += "Generated: " + escapeMd(new Date().toString()) + " | Duration: " + durationMs
        + " ms | Mode: " + (CONSOLE_MODE ? "console" : "standalone") + "\n\n";
    md += "**" + passCount + " passed / " + failCount + " failed**\n\n";
    var cfgLines = configSnapshot();
    if (cfgLines.length > 0) {
        md += "## Config Snapshot (Logger.properties)\n\n```\n" + cfgLines.join("\n") + "\n```\n\n";
    }
    md += "| Case Id | Test Objective | Result | Detail |\n";
    md += "| --- | --- | --- | --- |\n";
    for (var i = 0; i < results.length; i++) {
        var badge = results[i].ok ? "**PASS**" : "**FAIL**";
        var detail = escapeMd(results[i].detail);
        var dbv = results[i].dbv;
        if (dbv !== null && dbv !== undefined) {
            var dbvRowsTxt = dbv.rows.length === 0 ? "(0 rows)"
                : dbv.rows.map(function (r) { return "[" + r.join(" ") + "]"; }).join(" ");
            detail += " / dbVerify: `" + escapeMd(dbv.sql) + "` -> "
                + ((dbv.error !== undefined && dbv.error !== null && ("" + dbv.error).length > 0)
                    ? "(query failed) " + escapeMd(dbv.error) : dbvRowsTxt);
        }
        var nameCell = escapeMd(results[i].name)
            + (results[i].objective.length > 0 ? " — " + escapeMd(results[i].objective) : "")
            + (results[i].objectiveZh.length > 0 ? " — " + escapeMd(results[i].objectiveZh) : "")
            + (results[i].command.length > 0 ? " / `" + escapeMd(results[i].command) + "`" : "");
        md += "| C" + (i + 1) + " | " + nameCell + " | " + badge + " | " + detail + " |\n";
    }
    try {
        var reportName = (typeof REPORT_NAME !== "undefined" && REPORT_NAME !== null && REPORT_NAME.length > 0)
            ? REPORT_NAME : "TestAll-Report.html";
        if (reportName.indexOf(".html") >= 0) {
            reportName = reportName.substring(0, reportName.length - 5) + ".md";
        }
        var File = Java.type("java.io.File");
        var reportDir = new File(System.getProperty("JS_PLUS_PATH") + "../docs/latest/report");
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }
        var reportPath = System.getProperty("JS_PLUS_PATH") + "../docs/latest/report/" + reportName;
        var report = new DiskFile(reportPath);
        report.write(md, "UTF-8");
        print("Markdown report written to " + reportPath);
    } catch (e) {
        print("[WARN] Could not write markdown report: " + e);
    }
}

// CI-oriented summary - writes a compact Markdown file into
// <PROJECT_ROOT>/docs/latest/report/ so the CI job can surface it after a run:
//   1) it is included in the uploaded docs/latest/report/ artifact (downloadable),
//   2) the workflow can append it to $GITHUB_STEP_SUMMARY so the GitHub
//      Actions run page shows the result without opening the artifact.
// Params (all optional):
//   title          - report heading (default "JsPlus-Core CI Summary")
//   reportName     - output file name (default "TestAll-CI-Summary.md")
//   pass / fail    - counters (default: current passCount / failCount)
//   failures       - array of failed check names (default: current failures)
//   suiteTable     - [{title, pass, fail}] rendered as a Suites table
//   configOk       - true/false for the Config Self-Check section
//   configMissing  - array of missing keys (shown when configOk is false)
//   configLines    - config snapshot lines (default: configSnapshot())
//   note           - optional extra paragraph (e.g. environment notes)
//   durationMs     - override the duration (default: since suiteStart)
// Returns the Markdown text so callers can also print / post it (GitHub).
function writeCiSummary(params) {
    var p = params || {};
    var reportTitle = (p.title !== undefined && p.title !== null && p.title.length > 0)
        ? p.title : "JsPlus-Core CI Summary";
    var pass = (p.pass !== undefined && p.pass !== null) ? p.pass : passCount;
    var fail = (p.fail !== undefined && p.fail !== null) ? p.fail : failCount;
    var failList = p.failures || failures;
    var cfgLines = p.configLines || configSnapshot();
    var note = p.note || "";
    var durationMs = (p.durationMs !== undefined && p.durationMs !== null)
        ? p.durationMs : (Date.now() - suiteStart);

    var md = "# " + escapeMd(reportTitle) + "\n\n";
    md += "Generated: " + escapeMd(new Date().toString()) + " | Duration: " + durationMs
        + " ms | Mode: " + (CONSOLE_MODE ? "console" : "standalone") + "\n\n";
    md += "## Result\n\n" + (fail === 0 ? ":white_check_mark:" : ":x:")
        + " **" + pass + " passed / " + fail + " failed**\n\n";
    if (p.configOk !== undefined && p.configOk !== null) {
        md += "## Config Self-Check\n\n" + (p.configOk ? ":white_check_mark: PASS"
            : ":x: FAIL - " + escapeMd((p.configMissing || []).join(", "))) + "\n\n";
    }
    if (p.suiteTable !== undefined && p.suiteTable !== null && p.suiteTable.length > 0) {
        md += "## Suites\n\n| Suite | Passed | Failed |\n| --- | --- | --- |\n";
        for (var i = 0; i < p.suiteTable.length; i++) {
            md += "| " + escapeMd("" + p.suiteTable[i].title) + " | " + p.suiteTable[i].pass
                + " | " + p.suiteTable[i].fail + " |\n";
        }
        md += "\n";
    }
    if (fail > 0 && failList.length > 0) {
        md += "## Failed Checks\n\n";
        for (var j = 0; j < failList.length; j++) {
            md += "- " + escapeMd("" + failList[j]) + "\n";
        }
        md += "\n";
    }
    if (note.length > 0) {
        md += "## Note\n\n" + escapeMd(note) + "\n\n";
    }
    if ((cfgLines || []).length > 0) {
        md += "## Config Snapshot (Logger.properties)\n\n```\n" + (cfgLines || []).join("\n") + "\n```\n";
    }
    try {
        var reportName = (p.reportName !== undefined && p.reportName !== null && p.reportName.length > 0)
            ? p.reportName : "TestAll-CI-Summary.md";
        var File = Java.type("java.io.File");
        var reportDir = new File(System.getProperty("JS_PLUS_PATH") + "../docs/latest/report");
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }
        var reportPath = System.getProperty("JS_PLUS_PATH") + "../docs/latest/report/" + reportName;
        var report = new DiskFile(reportPath);
        report.write(md, "UTF-8");
        print("CI summary written to " + reportPath);
    } catch (e) {
        print("[WARN] Could not write CI summary: " + e);
    }
    return md;
}
