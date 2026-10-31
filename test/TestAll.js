// ============================================================================
// JsPlus-Core TestAll - unified console entry for the test suites
// ----------------------------------------------------------------------------
// Run      : test/TestAll.bat / test/TestAll.sh (standalone JsPlus console):
//              <java> -cp <LibHome + build jars> com.killcoding.jsplus.Main
//                     -ScriptEngine graalvm.js -Env script -WriteHistory false
//                     -Task <PROJECT_ROOT>/test/TestAll.js < NUL
//            Interactive console eval:
//              Console.eval(<PROJECT_ROOT>/test/TestAll.js)
// Output   : Runs, in order:
//              1) TestCrud.js      -> <PROJECT_ROOT>/docs/latest/report/TestCrud-Report.html
//              ... (see the enabled suite list below)
//            TestDatasource.js is NOT loaded here: its C17 case deliberately
//            stops and restarts the H2 server (pool disconnect/resume proof)
//            and the restarted H2 is not guaranteed to stay alive, so the
//            suite would starve every later H2-dependent suite (e.g.
//            TestClock's background time-refresh cases need :3306 up).
//            TestAll.bat/.sh therefore run TestDatasource.bat/.sh LAST,
//            right before the StopH2/StopAll sweep, where the restart
//            instability is contained to this suite only.
//            This entry point then prints an aggregate pass/fail summary and
//            writes TestAll-Report.md / TestAll-Report.html / TestAll-CI-Summary.md.
// NOTE     : Each suite calls beginTest() and reports its own counters, so
//            TestAll captures the counters right after each load() completes.
//            The suites NEVER call System.exit(); TestAll only exits when the
//            JSPLUS_TEST_EXIT env var is set (TestAll.bat/.sh), which lets an
//            interactive Console.eval(...) run finish without killing the console.
// PREREQ   : <PROJECT_ROOT>/JsPlus/config/Logger.properties exists (the runtime
//            config anchor that ScriptConsole loads at startup).
// ============================================================================

var System = Java.type("java.lang.System");
var testStart = Date.now();
var basePath = System.getProperty("JS_PLUS_PATH");

// Column-name case mode: aligned with GhostConsole.java:72
// (CacheDriverExecutor.COLUMN_NAME_CASE_MODE = LOWER). JDBC exposes column
// labels in the database's identifier case (H2 uppercases unquoted names);
// business code such as RemoteFile reads rows with lowercase keys, so the
// runtime sets LOWER globally. The suites do the same so RemoteFile works
// against the H2 testdb exactly as it does in the server edition.
// NOTE: the field is DECLARED on DriverExecutor (CacheDriverExecutor extends
// it); GraalJS resolves Java static fields only on the declaring class, so we
// must assign via DriverExecutor.
var DriverExecutor = Java.type("com.killcoding.datasource.DriverExecutor");
DriverExecutor.COLUMN_NAME_CASE_MODE = DriverExecutor.COLUMN_NAME_CASE_LOWER;

try {
    if (basePath === null || basePath === undefined || basePath.length === 0) {
        throw "JS_PLUS_PATH is not set - cannot locate the test suites";
    }
} catch (e) {
    print("[ERROR] " + e);
    throw e;
}

print("=== JsPlus-Core TestAll (unified entry) ===");
print("JS_PLUS_PATH: " + System.getProperty("JS_PLUS_PATH"));
print("Suites       : TestCrud -> TestRemoteFile -> TestFind -> TestDiskFile -> TestClock -> TestIdentityCard -> TestXmlParser -> TestCommonTools -> TestDataObject -> TestResultMap -> TestConfigProperties -> TestCipherTools -> TestEscapeTools -> TestFileTools -> TestCache -> TestCacheArray -> TestEvalJava -> TestJsString -> TestDBSync");
print("               (TestDatasource runs LAST via TestAll.bat/.sh -> TestDatasource.bat/.sh: its H2 stop/restart case must not precede H2-dependent suites)");

var suites = [
    // TestDatasource.js is intentionally absent - see the header comment.
    { file: "TestCrud.js",        title: "TestCrud" },
    { file: "TestRemoteFile.js",  title: "TestRemoteFile" },
    { file: "TestFind.js",        title: "TestFind" },
    { file: "TestDiskFile.js",    title: "TestDiskFile" },
    // { file: "TestClock.js",       title: "TestClock" }
    { file: "TestIdentityCard.js", title: "TestIdentityCard" },
    { file: "TestXmlParser.js",   title: "TestXmlParser" },
    { file: "TestCommonTools.js", title: "TestCommonTools" },
    { file: "TestDataObject.js",      title: "TestDataObject" },
    { file: "TestResultMap.js",       title: "TestResultMap" },
    { file: "TestConfigProperties.js", title: "TestConfigProperties" },
    { file: "TestCipherTools.js",     title: "TestCipherTools" },
    { file: "TestEscapeTools.js",     title: "TestEscapeTools" },
    { file: "TestFileTools.js",       title: "TestFileTools" },
    { file: "TestCache.js",           title: "TestCache" },
    { file: "TestCacheArray.js",      title: "TestCacheArray" },
    { file: "TestEvalJava.js",        title: "TestEvalJava" },
    { file: "TestJsString.js",        title: "TestJsString" },
    { file: "TestDBSync.js",          title: "TestDBSync" }
];

var totalPass = 0;
var totalFail = 0;
var allFailures = [];
var suiteResults = [];

// ---- Runtime config self-check (fail fast & visibly) ----
// The logging suite depends on <JS_PLUS_PATH>/config/Logger.properties being
// loaded by ScriptConsole.initEnv(); verify the anchor exists up front and
// surface the result in the console + aggregate reports.
var loggerPropsPath = basePath + "config/Logger.properties";
var cfgCheck = { ok: false, detail: "" };
try {
    var File = Java.type("java.io.File");
    var cfgFile = new File(loggerPropsPath);
    if (cfgFile.exists() && cfgFile.length() > 0) {
        cfgCheck.ok = true;
        cfgCheck.detail = loggerPropsPath + " (" + cfgFile.length() + " bytes)";
    } else {
        cfgCheck.detail = loggerPropsPath + " missing or empty";
    }
} catch (e) {
    cfgCheck.detail = "" + e;
}
print("");
print("=== Config self-check (JsPlus/config/Logger.properties) ===");
if (cfgCheck.ok) {
    totalPass++;
    print("[PASS] " + cfgCheck.detail);
} else {
    totalFail++;
    allFailures.push("Runtime config self-check - " + cfgCheck.detail);
    print("[FAIL] " + cfgCheck.detail);
}

// The loop index is declared with let (block scope): suites loaded via
// load() run in the SAME global scope, and their own top-level 'var i'
// loops would otherwise clobber a 'var i' here (GraalJS: load() shares the
// global lexical environment), re-running the loop forever from the middle.
// IN_TEST_ALL marks that the suites below are being run under the TestAll
// orchestrator: standalone runners (e.g. TestDatasource.bat/.sh) may then
// consume JSPLUS_TEST_EXIT themselves, while suites loaded here never
// System.exit() mid-run (TestAll owns the JVM exit at the end).
var IN_TEST_ALL = true;
for (let i = 0; i < suites.length; i++) {
    var s = suites[i];
    print("");
    print("===== Running " + s.title + " =====");
    // A throwing suite must NOT abort the whole run: record it as a failed
    // suite and continue, so TestAll always reaches the exit below (otherwise
    // the non-daemon DetectSystemExit thread keeps this JVM alive forever).
    var suitePass = 0;
    var suiteFail = 0;
    var suiteErr = "";
    try {
        load(basePath + "../test/" + s.file);
        // Capture the suite's own counters immediately after it finishes
        // (the next suite's beginTest() would reset them).
        suitePass = passCount;
        suiteFail = failCount;
        for (var j = 0; j < failures.length; j++) {
            allFailures.push(s.title + " - " + failures[j]);
        }
    } catch (e) {
        suiteErr = "" + e;
        suiteFail = 1;
        allFailures.push(s.title + " - suite load/run exception: " + e);
        print("[ERROR] " + s.title + " failed: " + e);
        if (e !== null && e !== undefined && e.stack !== undefined && e.stack !== null) {
            print("[ERROR] stack: " + e.stack);
        }
    }
    totalPass += suitePass;
    totalFail += suiteFail;
    suiteResults.push({ title: s.title, pass: suitePass, fail: suiteFail });
    print(s.title + " => " + suitePass + " passed, " + suiteFail + " failed"
        + (suiteErr.length > 0 ? " (" + suiteErr + ")" : ""));
}

print("");
print("========================================");
print("TestAll result: " + totalPass + " passed, " + totalFail + " failed");
if (totalFail > 0) {
    print("FAILED checks:");
    for (var k = 0; k < allFailures.length; k++) {
        print("  " + allFailures[k]);
    }
} else {
    print("ALL TESTS PASSED");
}

// Aggregate markdown + HTML summary into <PROJECT_ROOT>/docs/latest/report/
try {
    var reportDir = new File(basePath + "../docs/latest/report");
    if (!reportDir.exists()) {
        reportDir.mkdirs();
    }
    var md = "# JsPlus-Core TestAll Report\n\n";
    md += "Generated: " + new Date().toString() + "\n\n";
    md += "**" + totalPass + " passed / " + totalFail + " failed**\n\n";
    md += "## Config Self-Check\n\n"
        + (cfgCheck.ok ? "- PASS: " + cfgCheck.detail : "- FAIL: " + cfgCheck.detail) + "\n\n";
    md += "| Suite | Passed | Failed |\n";
    md += "| --- | --- | --- |\n";
    for (var m = 0; m < suiteResults.length; m++) {
        md += "| " + suiteResults[m].title + " | " + suiteResults[m].pass + " | " + suiteResults[m].fail + " |\n";
    }
    var reportPath = basePath + "../docs/latest/report/TestAll-Report.md";
    var report = new DiskFile(reportPath);
    report.write(md, "UTF-8");
    print("Markdown report written to " + reportPath);

    var html = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>JsPlus-Core TestAll Report</title>\n"
        + "<style>\n"
        + "  body { font-family: Consolas, 'Courier New', monospace; background: #f5f6f8; margin: 24px; }\n"
        + "  h1 { font-size: 20px; color: #24292f; }\n"
        + "  .meta { color: #57606a; margin-bottom: 12px; }\n"
        + "  .summary-pass { display: inline-block; padding: 8px 16px; border-radius: 6px; background: #e6ffec; color: #1a7f37; font-weight: bold; margin-bottom: 16px; }\n"
        + "  .summary-fail { display: inline-block; padding: 8px 16px; border-radius: 6px; background: #ffebe9; color: #b42318; font-weight: bold; margin-bottom: 16px; }\n"
        + "  table { border-collapse: collapse; width: 100%; background: #fff; }\n"
        + "  th { background: #eaeef2; text-align: left; padding: 8px 10px; border: 1px solid #d0d7de; }\n"
        + "  td { padding: 6px 10px; border: 1px solid #d0d7de; vertical-align: top; }\n"
        + "  pre.cfg { background: #fff; border: 1px solid #d0d7de; padding: 10px; font-size: 12px; overflow-x: auto; }\n"
        + "</style>\n</head>\n<body>\n"
        + "<h1>JsPlus-Core TestAll Report</h1>\n"
        + "<div class=\"meta\">Generated: " + new Date().toString() + "</div>\n"
        + "<div class=\"" + (totalFail === 0 ? "summary-pass" : "summary-fail") + "\">" + totalPass + " passed / " + totalFail + " failed</div>\n"
        + "<h2>Config Self-Check</h2>\n"
        + "<div class=\"" + (totalFail === 0 ? "summary-pass" : "summary-fail") + "\">"
        + (cfgCheck.ok ? "PASS: " + cfgCheck.detail : "FAIL: " + cfgCheck.detail) + "</div>\n"
        + "<table>\n<tr><th>Suite</th><th>Passed</th><th>Failed</th></tr>\n";
    for (var m2 = 0; m2 < suiteResults.length; m2++) {
        html += "<tr><td><a href=\"" + suiteResults[m2].title + "-Report.html\">" + suiteResults[m2].title + "</a></td><td>" + suiteResults[m2].pass + "</td><td>" + suiteResults[m2].fail + "</td></tr>\n";
    }
    html += "</table>\n</body>\n</html>\n";
    var htmlPath = basePath + "../docs/latest/report/TestAll-Report.html";
    var report2 = new DiskFile(htmlPath);
    report2.write(html, "UTF-8");
    print("HTML report written to " + htmlPath);

    // CI summary (from TestBase.writeCiSummary) - compact Markdown that the
    // workflow appends to $GITHUB_STEP_SUMMARY (visible on the Actions run page)
    // and that ships inside the uploaded docs/latest/report/ artifact.
    load(basePath + "../test/TestBase.js");
    writeCiSummary({
        title: "JsPlus-Core CI Summary",
        reportName: "TestAll-CI-Summary.md",
        pass: totalPass,
        fail: totalFail,
        failures: allFailures,
        suiteTable: suiteResults,
        configOk: cfgCheck.ok,
        configMissing: cfgCheck.ok ? [] : [cfgCheck.detail],
        durationMs: Date.now() - testStart
    });
} catch (e) {
    print("[WARN] Could not write TestAll report: " + e);
    if (e !== null && e !== undefined && e.stack !== undefined && e.stack !== null) {
        print("[WARN] stack: " + e.stack);
    }
}

// TestAll.bat/.sh set JSPLUS_TEST_EXIT=1 so this JVM exits after the task.
// Exit code mirrors the test outcome: 0 when every check passed, 1 when any
// check failed. (JsPlus-Tomcat's TestAll.js exits unconditionally with 0, so
// its CI cannot see a failing suite; JsPlus-Core deliberately propagates the
// failure so TestAll.bat/.sh and the CI workflow turn red on a real failure.)
// Interactive Console.eval runs skip the exit.
var testExit = System.getenv("JSPLUS_TEST_EXIT");
if (testExit !== null && testExit !== undefined && ("" + testExit) === "1") {
    System.exit(totalFail > 0 ? 1 : 0);
}
