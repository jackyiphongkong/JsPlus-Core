# JsPlus-Core

**Embedded script-console engine + server-side capability library.** Embeds a GraalVM JavaScript runtime in any Java application and closes the loop between Java and JS: one console, one runtime, one jar — the JS scripts call the real Java API directly, and the same console executes, asserts and reports the tests.

## Ecosystem loop & lightweight positioning

**JsPlus-Core is a closed-loop, lightweight capability engine:**

| Loop | What closes the loop |
| --- | --- |
| **Capability loop** | Java core library (datasource / cache / file / sync / toolkit) → unified `ScriptConsole` → JS scripts call the real Java API → the same console executes, asserts and reports (test suites are JS files asserting against real Java — no JUnit) |
| **Dynamic-compile loop** | `evalJavaSource()` compiles Java source at runtime → loads the classes → JS invokes them immediately — extend capabilities on the fly, no precompilation, no restart |
| **Data loop** | On-demand H2 + pooled `DriverDataSource` + multi-layer `Cache` + `CacheArray` async pipeline + `RemoteFile` DB-mapped files + `dbsync` — the full data path (store / cache / stream / sync) is built in |
| **Cross-process loop** | `GhostConsole` queue — the main console issues commands → a ghost process polls, executes and writes the result back — remote control without sharing process state |
| **Server-ecosystem loop** | Same env-var conventions, same console API and the same capability library as **JsPlus-Tomcat** (the server edition) — Core supplies the capabilities, the server edition supplies HTTP/TCP serving |

**Lightweight by design:** no web-container dependency (no Tomcat/Servlet) · one capability jar · bundled toolchain (JDK + Ant, no `JAVA_HOME`) · on-demand H2 (no resident database process) · JS test suites instead of JUnit · small footprint (`-Xms256m -Xmx1024m`).

## Capability overview

| Capability | What it does |
| --- | --- |
| **Script console** | GraalVM JS (or Nashorn) embedded engine: `evalScript` / `eval(File)` / callbacks / multi-console isolation via `newBuilder()` / dynamic compilation `evalJavaSource()` |
| **Database connection pool** | `DriverDataSource` pooled JDBC (min/max pool, validity check, idle/lifetime reclamation, self-healing) + `CacheDriverExecutor` sync executor & row-by-row cache-array feeding |
| **Cache** | `Cache` static key-value API (life semantics) + `CacheArray` async filter pipeline (producer → filter worker → consumer callbacks) |
| **Remote files** | `DiskFile` local abstraction; `RemoteFile` DB-mapped files (chunking via `FilePart` + `RemoteFileSync`) |
| **Database sync** | `dbsync` pipeline: `Database/Table` + `Inserter/Updater/Deleter` — cross-db/cross-table incremental sync |
| **Toolkit** | `CommonTools` / `ConfigProperties` / `XmlParser` / `CipherTools` / `EscapeTools` / `FileTools` / minifiers (`SimpleJsCompressor` / `SimpleCssCompressor` / `SimpleHtmlScanner`) / `WebClient` (HTTP client) |
| **TextScript** | Scriptable text-template engine: `#` directives compiled into a JS function — see below |
| **GhostConsole** | Cross-process command queue (`ghost_remote_command` table): issue commands from the main console, a ghost process executes them |

## Related projects

| Project | Role |
| --- | --- |
| **JsPlus** (root) | Bundled toolchain & scripts: JDK/Ant, H2 startup (`StartH2.js` / `StopH2.bat`), unified bat/sh entry points |
| **JsPlus-Core** (this) | The capability engine: script console + datasource/cache/file/sync/toolkit in one jar |
| **JsPlus-Tomcat** | The server edition: HTTP/TCP reverse proxy with the same console API & capability library (`TomcatScriptConsole` — the pool concept this Core pool mirrors) |

## Suite architecture

Every test suite is a **JS file running inside the engine that calls the real Java API directly and asserts** — no JUnit, no external framework. Build & full regression:

| Step | Command | Artifact / result |
| --- | --- | --- |
| Build | `ant.bat build` (Linux/macOS: `ant.sh`) | `LibHome\jsplus\default\jsplus-core-<version>.jar`; version auto patch+1; changelog appended to `changes.log` / `changes_zh-HK.log`; API javadoc regenerated |
| Full test suite | `test\TestAll.bat` (`TestAll.sh`) | **294 passed / 0 failed** (24 JS suites + TestDatasource pool integration; auto-starts H2 → initializes `testdb` → runs → cleans up) |
| Stress suite | `test\_RunOne.bat TestTextScriptStress` | **7 cases all green** — C1 sequential · C2/C3/C4 concurrent pooled render (240/240/800) · C5 per-thread `newBuilder()` + callback + `ca.add()` · C6 pool exhaustion (acquired=99 rejected=20, pool recovers) · C7 named strategy pools `new TextScript(t, true/false)` |

Packages: `com.killcoding.jsplus` (console engine core) · `com.killcoding.datasource` (pooled JDBC) · `com.killcoding.cache` (multi-layer cache) · `com.killcoding.file` (local + DB-mapped files) · `com.killcoding.dbsync` (sync pipeline) · `com.killcoding.tool` (toolkit) · `com.killcoding.log` (logging).

## Console API (core entry) — hello world

`ScriptConsole` is the single entry point; any number of instances can be created (each with its own isolated engine context):

```java
import com.killcoding.jsplus.ScriptConsole;

ScriptConsole console = new ScriptConsole();
console.initScriptEngineManager(true);          // start the GraalVM JS engine

console.evalScript("print('Hello, world!')");   // → Hello, world!   (JS)
console.evalScript("'Hello from JS: ' + (40 + 2)"); // → Hello from JS: 42

// Object bridge: pass values between JS and Java
console.getContext().put("greeting", "hello");
console.evalScript("greeting.toUpperCase()");   // → HELLO

// Isolated console for parallel execution (own context)
ScriptConsole worker = console.newBuilder();
worker.evalScript("var x = 40 + 2; x");

// Dynamic compilation of Java source at runtime
ClassLoader cl = console.evalJavaSource(
    "public class Hello { public static String hi() { return \"hi\"; } }");
```

Key API surface: `evalScript(String)` / `eval(File|Runnable)` / `evalJavaSource(String[])` / `newBuilder()` / `getContext()` (`put/get/remove` bridge) / `callback(Runnable, sync)` / `getGlobalCacheArray(timer, loopTimer)` / `addSchedule(...)` (below) / `executeViaGhost(...)`.

## Scheduling & tasks — sample

```java
// cron-style schedule: the console checks every minute.
// Rules: "HH:mm" (e.g. "08:00"), "E HH:mm" (e.g. "MON 08:00"),
//        "mm" (minute), or "*N" = every N minutes (e.g. "*5" = every 5 min).
console.addSchedule("daily-report", "08:00", () ->
    console.evalScript("print('daily report generated')"));

console.addSchedule("heartbeat", "*5", () ->
    console.evalScript("print('tick')"));

console.getScheduleMap();   // → { name -> { cronRules, runnable } }
console.clearSchedule("daily-report");

// One-shot task mode
console.setTask("print('one-shot task')");
console.taskExecute();
```

## TextScript — scriptable text template engine — sample

`TextScript` compiles a text template with `#` directives into a JS function and runs it inside the console: plain lines are emitted literally, `#{expr}` is evaluated inline, and `#if / #else / #end / #for / #code / #block / #end` drive control flow.

```text
#comment greeting template - directive lines are dropped
Hello #{name}, welcome to JsPlus-Core!
#if(release)
Build: STABLE
#else
Build: DEV
#end
```

```java
import com.killcoding.jsplus.TextScript;
import java.util.HashMap;
import java.util.Map;

TextScript ts = new TextScript(template);       // template text above
Map<String,Object> params = new HashMap<String,Object>();
params.put("name", "World");
params.put("release", true);
ts.addParams(params);
String out = ts.parse();                        // rendered text
String src = ts.parse(true);                    // compiled JS function source
```

Pool & security policy — `TextScript` renders on a thread-confined console pool (one console per thread, mirrors JsPlus-Tomcat `TomcatScriptConsole`). Pick the security policy at the constructor:

```java
new TextScript(template)            // default pool (TextScriptConsole)
new TextScript(template, true)      // AllowAllAccessPool  (permissive)
new TextScript(template, false)     // NotAllowAccessPool  (restricted)
```

> Note: one `parse()` call consumes the scanner — use a fresh `TextScript` instance per render. Runnable demo: `examples/TextScript-demo.js`.

## Environment variable overrides (same convention as the server edition)

| Variable | Default | Purpose |
| --- | --- | --- |
| `JsPlus_JavaDir_JsPlus-Core` | `default` | JDK directory name under `Java\` |
| `JsPlus_JvmParams_JsPlus-Core` | `-Xms256m -Xmx1024m -Xss256k` | JVM parameters |
| `SCRIPT_ENGINE` | `graalvm.js` | Script engine (Nashorn optional) |
| `JsPlus_H2_PORT` | `3306` | H2 TCP port |

Console-pool external config (env first, then `-D`): `jsplus.textscript.poolsize` · `jsplus.textscript.engine` · `jsplus.textscript.allowallaccess`. Applied only to the default pool; strategy pools (`true`/`false`) are fixed at construction.
