// ============================================================================
// _RunOne.js — run a single test suite in THIS JVM, then hard-halt so the
// non-daemon ScriptConsoleBase DetectSystemExit / ConfigProperties AutoLoad /
// ScriptConsole pool threads cannot keep the JVM alive after main() returns
// (the suites themselves never call System.exit). Mirrors JsPlus-Tomcat
// test/_RunOne.js; the Tomcat-bootstrap part is Core-irrelevant, and suites
// are loaded with load() exactly like TestAll.js does.
// Usage: set env RUN_SUITE=<TestName>, then:
//   java ... com.killcoding.jsplus.Main -Task .../test/_RunOne.js < NUL
// Exit code: 0 = suite passed (failCount == 0), 1 = failed or load error.
// ============================================================================

var System = Java.type("java.lang.System");
var Runtime = java.lang.Runtime;
var basePath = System.getProperty("JS_PLUS_PATH");
var suiteName = System.getenv("RUN_SUITE");

if (!suiteName) {
    System.err.println("[_RunOne] ERROR: RUN_SUITE env var not set.");
    Runtime.getRuntime().halt(1);
}
print("[_RunOne] Running " + suiteName + ".js ...");

try {
    load(basePath + '/../test/' + suiteName + '.js');
} catch (e) {
    System.err.println("[_RunOne] ERROR: " + suiteName + " failed: " + e);
    if (e !== null && e !== undefined && e.stack !== undefined && e.stack !== null) {
        System.err.println(e.stack);
    }
    Runtime.getRuntime().halt(1);
}

// The suite loaded TestBase.js, whose failCount is a global - capture it the
// same way TestAll.js does right after the suite finishes. Hard-halt so the
// non-daemon background threads (DetectSystemExit, AutoLoad-Appenv.properties,
// ScriptConsole pool) that keep the JVM alive after main() returns cannot hang
// this launcher.
var fail = 0;
try {
    fail = (typeof failCount !== "undefined" && failCount !== null) ? failCount : 0;
} catch (e) {
    fail = 1;
}
Runtime.getRuntime().halt(fail > 0 ? 1 : 0);
