# JsPlus-Core

**Embedded Script Console Engine** — embeds a GraalVM JavaScript runtime into any Java application, with a server-side capability library: pooled datasource, multi-layer cache, file abstraction, DB sync, cipher/parse/toolkit. No web-container dependency (no Tomcat/Servlet); the engine itself does not host a resident H2 process — the H2 database server is started on demand by `JsPlus\script\StartH2.js` (tests/runtime start it via `StartH2.bat` and stop it via `StopH2.bat`).

```
Java Application ──► ScriptConsole (embeds GraalVM JS engine)
              │
              ├── Script execution (eval / evalScript / evalJavaSource dynamic compilation)
              ├── Datasource (DriverDataSource pooling + CacheDriverExecutor)
              ├── Cache (Cache static API + CacheArray async filter pipeline)
              ├── File (DiskFile local / RemoteFile DB-mapped)
              ├── DB sync (dbsync pipeline: Insert/Update/Delete/Select)
              └── Toolkit (CommonTools / ConfigProperties / XmlParser / CipherTools …)
```

## 1. Package layout (src · 61 Java classes)

| Package | Core classes | Responsibility |
| --- | --- | --- |
| `com.killcoding.jsplus` | ScriptConsole, ScriptConsoleBase, ConsoleImpl, JsPlusContext, GraalVmContext, NashornContext, JsString, Main, GhostConsole, CmdConsole, JdbcConsole | **Console engine core**: script execution, multi-console isolation, engine bridging (GraalJS/Nashorn), object bridge, dynamic compilation, command execution |
| `com.killcoding.datasource` | DriverDataSource, ConnectionPool, DriverConnection, DriverExecutor, CacheDriverExecutor, Clock, IdentityCard | **Pooled datasource & JDBC**: connection pool lifecycle, sync executor, cache-array feeding |
| `com.killcoding.cache` | Cache, CacheArray, CacheArrayFilter, MemoryCache, DiskCache, StoredCache, AbsCacheApi, DataObject | **Multi-layer cache**: static key-value API + async filter pipeline (producer/consumer) |
| `com.killcoding.file` | DiskFile, RemoteFile, RemoteFileBase, RemoteFileSync, BaseFile, FilePart, RemoteFileSQL | **File abstraction**: local file operations, DB-mapped files (chunking/sync) |
| `com.killcoding.dbsync` | Database, Table, Inserter, Updater, Deleter, Remover, Enter, SqlStatement | **Data sync pipeline**: cross-db/cross-table incremental sync |
| `com.killcoding.tool` | CommonTools, ConfigProperties, ResultMap, XmlParser, CipherTools, EscapeTools, Base64Tools, FileTools, CodeEscape* | **General toolkit**: config/parse/cipher/escape/file |
| `com.killcoding.log` | Logger, LoggerFactory, AlertMessage, LogRecord | **Logging**: async write, alert messages |

## 2. Console API (core entry)

`ScriptConsole` is the single entry point; any number of instances can be created (each with its own isolated engine context):

### Execution & evaluation
| API | Description |
| --- | --- |
| `evalScript(String)` | Executes a raw JS string and returns the result |
| `eval(String/File)` | Executes a script file (path or File) |
| `eval(Runnable)` | Executes a Java callback on the console thread |
| `evalJavaSource(String / String[])` → `ClassLoader` | **Dynamically compiles Java source** and loads the classes (content-addressed jar cached in `JsPlus/.tmp/java`) |
| `evalJavaFile(String / File)` → `ClassLoader` | Dynamically compiles a Java file |
| `execute(String)` / `executeBatch(String)` | Executes JsPlus commands / batch files |
| `executeViaGhost / executeViaCmd / executeViaScript` | Executes via the Ghost console / system command / script |

### Callbacks
| API | Description |
| --- | --- |
| `callback(Runnable, boolean callbackSync)` | Registers a callback (async/sync execution) |
| `callbackBatch(Runnable, boolean)` | Batch callbacks |
| With `Java.extend(Runnable)` | Wraps a JS function as a Java Runnable proxy (bridge for GraalJS thread isolation) |

### Context & object bridge
| API | Description |
| --- | --- |
| `getContext()` → `JsPlusContext` | Engine context (`put/get/remove` name-value bridge, `evalScript`, `getEngine/getBindings`, `close`) |
| `newBuilder()` / `newBuilder(engine, allowAllAccess)` | **Creates an isolated console** (isolated context, parallel execution) |
| `initScriptEngineManager(boolean allowAllAccess)` | Initializes the engine (may relax security access) |
| `closeContext()` | Releases the context |

### Global cache array
| API | Description |
| --- | --- |
| `getGlobalCacheArray(long timer, long loopTimer)` | Gets the global cache array (filter worker consumes on a timer) |
| `initGlobalCacheArray(long, long)` | Initializes the global cache array |

### Scheduling & tasks
| API | Description |
| --- | --- |
| `addSchedule(name, cronRules, Runnable)` / `clearSchedule` / `getScheduleMap` | cron scheduling |
| `setTask(String)` / `taskExecute()` | One-shot task mode |
| `version(List)` / `help(List)` | Version / command help |

### GhostConsole — cross-process command queue

`GhostConsole` (`com.killcoding.jsplus.GhostConsole`) is the headless runtime: the main console writes commands into the `ghost_remote_command` queue table via `executeViaGhost(commands)`, the GhostConsole process polls, executes and writes the result back — enabling cross-process / remote control without sharing process state.

Queue-table schema (`dbscript/change.sql`, created with `CREATE TABLE IF NOT EXISTS`):

| Column | Meaning |
| --- | --- |
| `id` | unique command record id (CHAR(16) PK) |
| `ghost_name` | which ghost console owns the command |
| `request_script` | script to be executed by the ghost |
| `response_message` | text returned by the ghost after execution |
| `processed` | 0 = pending, 1 = processed |
| `requested` / `responsed` | request / response counters |
| `created_at` / `updated_at` / `deleted_at` / `deleted` | timestamps + soft-delete (0 = active, 1 = deleted) |

> Runtime rule: `ghost_remote_command` belongs to the runtime (GhostConsole); tests/init never DROP or recreate it (`IF NOT EXISTS` keeps it idempotent) — otherwise a running ghost hits "table not found" mid-run.

## 3. Data layer mechanics (com.killcoding.datasource)

### DriverDataSource connection pool (parameter semantics)
| Parameter | Semantics |
| --- | --- |
| `MinPoolSize` / `MaxPoolSize` | Pool lower/upper bounds; borrows beyond `MaxPoolSize` are rejected (overflow throws) |
| `TestValidTimeout` | Validity-check timeout before borrow |
| `RefreshPoolTimer` | Pool refresh cycle (validates/reclaims idle connections) |
| `IdleTimeout` | Idle timeout reclamation (converges to `MinPoolSize`; the last idle connection is protected) |
| `Timeout` | Interrupts borrowed connections (autoCommit=true) on timeout |
| `TransactionTimeout` | Interrupts in-transaction connections (autoCommit=false) |
| `LifeCycle` | Reclaims connections past total lifetime (not protected by MinPoolSize) |

Pool self-healing: after a connection is interrupted the worker automatically replenishes up to `MinPoolSize`; borrowing from an empty pool auto-reloads.

### CacheDriverExecutor (JDBC executor)
- `execute(sql, params)` / `first(sql, params)` / `find(sql, params)` — synchronous JDBC
- `find(sql, params, CacheArray)` — **row-by-row feeding** into the global cache array (streaming consumption, no full materialization)

### Clock / IdentityCard
- `Clock` — time API (Date/SQL timestamp/calendar, configurable via `Clock.properties`)
- `IdentityCard` — identity-card datasource mapping

## 4. Cache layer (com.killcoding.cache)

### Cache static API — `life` semantics (important)
```
Cache.set(name, value, life)
life >= 0  → store (0 = never expire)
life < 0   → skip (do not write)
```
> **Consumer contract**: if your application uses `0` to mean "cache disabled", you **must skip the `Cache.set` call** (do not pass 0 — Core interprets it as never-expire). See JsPlus-Tomcat B2 fix (D3/D4): call only when `cacheSeconds > 0`.

`set(name, value)` without `life` = never expire (legitimate use, e.g. static config).

### CacheArray — async filter pipeline
`CacheArray + CacheArrayFilter`: a producer console feeds data item-by-item or in batches, a worker thread consumes on `timer/loopTimer`, driven by the `execute / executeBatch / completed / terminated` callbacks (onNext/onComplete/onError semantics of RxJava/Reactor).

### Implementations
`MemoryCache` (default) / `DiskCache` / `StoredCache` / `AbsCacheApi` (swappable implementation via `ScriptConsoleBase.setCacheApi`).

## 5. File layer & DB sync

- **DiskFile** — local files: write/read/delete, directory, size, encode/decode
- **RemoteFile** — DB-mapped files (`RemoteFileBase` base + `RemoteFileSync` sync + `FilePart` table chunking)
- **dbsync** — `Database/Table` + `Inserter/Updater/Deleter/Remover`: cross-db incremental sync pipeline

## 6. Canonical patterns (core use cases)

1. **Dual-console object bridge**: a producer console runs JS and puts the result via `getContext().put(name, obj)`; an independent consumer console reads it via `getContext().get(name)` — isolated value passing across consoles.
2. **find → CacheArray feeding**: `CacheDriverExecutor.find(sql, params, getGlobalCacheArray(...))` feeds row-by-row; a dedicated callback console observes consumption via a `Java.extend(Runnable)` proxy + `CacheArrayFilter` — under GraalJS thread isolation, worker threads cannot enter the JS context directly, making this the only canonical path (see `TestFind` / `TestCacheArray`).
3. **Dynamic compilation**: `evalJavaSource(...)` compiles and loads Java source on the fly — extends capabilities at runtime without precompilation (see `TestEvalJava`).

## 7. Embedding example (Java)

```java
import com.killcoding.jsplus.ScriptConsole;
import com.killcoding.cache.CacheArray;

ScriptConsole console = new ScriptConsole();
console.initScriptEngineManager(true);              // start the GraalVM JS engine
console.evalScript("1 + 1");                        // → 2

// Isolated console: parallel execution in a separate context
ScriptConsole worker = console.newBuilder();
worker.evalScript("var x = 40 + 2; x");

// Object bridge: pass values between JS and Java
console.getContext().put("greeting", "hello");
console.evalScript("greeting.toUpperCase()");       // → "HELLO"

// Global cache array (async filtering)
CacheArray gca = console.getGlobalCacheArray(5000, 5000);
gca.add("item");

// Dynamic compilation of Java source
ClassLoader cl = console.evalJavaSource(
    "public class Hello { public static String hi() { return \"hi\"; } }");
```

Full bootstrap example: `JsPlus\bin\StartCore.bat` (classpath = `graalvm/default`, `nashorn/default`, `h2/default`, `jsplus/default` under `LibHome` + the core jar).

## 8. Build & test

| Step | Command | Artifact |
| --- | --- | --- |
| Build | `ant.bat build` (Linux/macOS: `ant.sh`) | `LibHome\jsplus\default\jsplus-core-<version>.jar` (version auto patch+1; each build appends a changelog entry to `changes.log` / `changes_zh-HK.log`; regenerates API javadoc into `docs/latest/api/`) |
| Full test suite | `test\TestAll.bat` (`TestAll.sh`) | **235 passed / 0 failed** (18 JS suites + LoadTest; auto-starts H2 → initializes `testdb` → runs suites → cleans up precisely) |
| Pool integration tests | `test\TestDatasource.bat` (`.sh`) | **19 passed / 0 failed** (pool param application + timeout reclamation + self-heal + extreme multi-thread stress + Console export/import roundtrip) |

The toolchain is bundled with the project (JDK + Ant, no installation, no `JAVA_HOME` required). Every test suite is a **JS file running inside the engine that calls the real Java API directly and asserts** — no JUnit.

## 9. Environment variable overrides (same naming convention as the server edition)

| Variable | Default | Purpose |
| --- | --- | --- |
| `JsPlus_JavaDir_JsPlus-Core` | `default` | JDK directory name under `Java\` |
| `JsPlus_JvmParams_JsPlus-Core` | `-Xms256m -Xmx1024m -Xss256k` | JVM parameters |
| `SCRIPT_ENGINE` | `graalvm.js` | Script engine (Nashorn optional) |
| `JsPlus_H2_PORT` | `3306` | H2 TCP port |

## 10. Quality & architecture positioning

- Quality score 83/100 (6.1.204) — build/scripts/CI/tests reach release level; known limitations: GraalJS `load()` shares the global scope, CacheArray global `forceStop`, `-Xlint:none` suppresses warnings, Main scanner relies on abnormal exit.
- The CacheArray pipeline maps to open-source patterns: **RxJava/Reactor** (observable semantics), **LMAX Disruptor** (batch consumption), **Akka Actor** (multi-console division of labor), **MyBatis Cursor / Spring RowCallbackHandler** (JDBC streaming feeding).

### CacheArray pipeline vs. mainstream frameworks

| JsPlus design | Open-source counterpart | Niche |
| --- | --- | --- |
| `CacheArray.filter(worker)` row-by-row callbacks (`execute`≈onNext, `completed`≈onComplete, `terminated`≈onError) | **RxJava / Project Reactor** (Flowable/Flux) | Observable sequence + backpressure + async consumption — closest semantics |
| `CacheDriverExecutor.find(sql, params, GCA)` row-by-row `add` | **Spring JdbcTemplate RowCallbackHandler** / **MyBatis Cursor** / **jOOQ** streaming queries | JDBC row-by-row callbacks, no full-table materialization in memory |
| `executeBatch + sweepProcessed` (batch consumption + head reclamation) | **LMAX Disruptor** (lock-free ring buffer + batch event processing) | Low-latency batch consumption; `sweep` mirrors its event reclamation |
| insertConsole produces → feedConsole feeds → callbackConsole consumes | **Akka Actor** / **Vert.x Verticle** / **ZeroMQ** | Multi-role independent message loops, one thread/mailbox per console |
| Batch chunk + completion callback | **Spring Batch** (ItemReader→Processor→Writer chunk) | Batch pipeline (ETL/Job); `completed` ≈ JobCompletion |
| Filter worker polling + periodic tasks | **ScheduledExecutorService** / **Quartz** / Node.js `setInterval` | Timer-driven consumption loop |
| Cache + eviction/reclamation | **Caffeine / Guava Cache** (async refresh, eviction ≈ `sweepProcessed`/`removeAll`) | Cache lifecycle management |
| Java worker thread + `Java.extend(Runnable)` proxy to bypass GraalJS multi-threading limits | **GraalVM Polyglot API** host interop; same idea as Rhino/Nashorn-era host-callback bridges | Cross-thread boundary design for multi-language embedding |
