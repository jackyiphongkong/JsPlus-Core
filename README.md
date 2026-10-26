# JsPlus-Core

JsPlus-Core is the **embedded console engine of JsPlus** — the core engine and utility libraries only (`com.killcoding.{jsplus,file,cache,tool,log,datasource}`), with **no web container dependency** and **no built-in H2 server process**.

> `org/*` (log replacement classes) has been split into a separate project; this repository no longer contains `src/org`.

## Quick Start (3 steps)

The toolchain is bundled with this project (no Java / Ant installation, no `JAVA_HOME` required):

| Step | Command | Output |
| --- | --- | --- |
| 1. Build | `ant.bat` (Linux/macOS: `ant.sh`) | `LibHome\jsplus\default\jsplus-core-<version>.jar` |
| 2. Test | `test\TestAll.bat` (Linux/macOS: `test\TestAll.sh`) | `test_reports\*` (HTML / MD / CI summary) |
| 3. Slim runtime | `packJdk.bat` (Linux/macOS: `packJdk.sh`) | `Java\runtime` (jlink cut-down) |

Baseline: `ant build` → BUILD SUCCESSFUL; `TestAll.bat` → **53 passed / 0 failed, exit 0** (includes H2 server TCP integration, connection-pool behaviour tests and the `CacheDriverExecutor.find(sql, Map, CacheArray)` + `globalCacheArray` queue test); cleanup releases `:3306` with no leftover process.

## Alignment with the Server Edition (adapted items)

| Item | JsPlus-Core status |
| --- | --- |
| `build.xml` | Fully consistent with the server edition's standard (single jar → `LibHome\jsplus\default`, `-Xlint:none`, clear/compile/package/build targets; the dead `org/*` include removed) |
| Versioning | `version.properties` (6.1.x) auto-increments **patch +1** on every `ant build`; jar name `jsplus-core-${jr.version}.jar`; old jar removed before build; each build appends a version history entry to `changes.log` (EN) + `changes_zh-HK.log` (繁中) (`-Dchange.en` / `-Dchange.zh` / `build\changes-pending-en.txt` / `build\changes-pending-zh-HK.txt` / defaults) (server-edition quirk: the jar name lags one patch behind — see `QUALITY-REVIEW.md`) |
| `JsPlus\bin` | 18 bat/sh scripts adapted as-is; renamed family: `StartTomcat→StartCore`, `BG_StartTomcat→BG_StartCore`, `StopTomcat→StopCore`; H2 companions keep their original names (`BG_StartH2` / `StartH2` / `StartH2Console` / `StopH2` / `Stop` / `StopAll`) |
| `JsPlus\config` | 7 files + 2 sub-directories copied (server-edition `Logger.properties`; the original Core version is backed up at `build\.logger-core-backup.properties`) |
| `dbscript\` | Rewritten: keeps only `ghost_remote_command` + 5 test tables; `tomcatdb` renamed everywhere to **`testdb`** |
| Test framework | `TestBase.js` / `TestAll.js` renderer and report style aligned with the server edition (GitHub style + dbv nested tables) |

## Directory Layout

| Path | Purpose |
| --- | --- |
| `src/` | Engine and utility sources (48 .java files) |
| `LibHome/` | Runtime dependencies (`graalvm\default` script engine, `h2\default` JDBC driver, `nashorn\default`); build artifacts go to `jsplus\default\` |
| `Java/default` | Bundled JDK (Oracle GraalVM 25.0.3, with jdeps/jlink) |
| `Java/runtime` | `packJdk`-generated cut-down runtime (can be deleted and rebuilt) |
| `ant/default` | Bundled Apache Ant 1.10.17 |
| `JsPlus/bin` | Start/stop script family (see above; `JS_PLUS_PATH` points to `JsPlus\`) |
| `JsPlus/config` | Runtime settings (`DataSource.properties` / `GhostDataSource.properties` / `Appenv.properties` / `Logger.properties`, etc.) |
| `JsPlus/ghost` | GhostConsole working directory |
| `JsPlus/script` | `StartH2.js` / `H2Server.js` (H2 server bootstrap scripts, no external dependency) |
| `dbscript/` | `fallback.sql` → `change.sql` (fixed DB-init order; `ghost_remote_command` is never DROPped and never touched by tests) |
| `test/` | Test framework (`TestBase.js` / `TestAll.js` / `TestAll.bat` / `TestAll.sh`) and the datasource/test-db suites |
| `test_reports/` | Test reports (HTML / Markdown / CI summary) |
| `logs/` | Runtime logs (`app\`, `DS\` pool status, `SqlLog\`) |
| `Database/` | H2 server database files (`testdb.mv.db`, gitignored) |
| `build/classes/` | Intermediate compilation output (build-time only) |
| `version.properties` | Build version state (auto-incremented) |

## Test Suite

**`TestAll.bat` flow (4 steps, re-inits testdb every run)**:

1. If `:3306` is not listening, run `BG_StartH2.bat %SCRIPT_ENGINE%` (→ `StartH2.bat` → `Main -Task script/StartH2.js`, separate JVM), poll 45×2s until ready;
2. `RunScript` against `jdbc:h2:tcp://localhost:3306/testdb` (`-user sa`, `-checkResults`) runs `fallback.sql` then `change.sql`;
3. Run `TestAll.js` (`JSPLUS_TEST_EXIT=1`, `< NUL` closes stdin);
4. Precisely kill the java process whose command line contains `script/StartH2.js`, then `exit /b %RC%` (`StopAll` is deliberately not used to avoid killing VS Code's Java Language Server).

**2 suites**:

| Suite | Cases | Coverage |
| --- | --- | --- |
| TestDatasource | 14 | Clock, `Console.getDataSource()` connection, DDL/DML roundtrip, pool borrow/return, **pool-full cap (MaxPoolSize overflow rejected)**, **6 pool tuning params (TestValidTimeout / RefreshPoolTimer / IdleTimeout / Timeout / TransactionTimeout / LifeCycle) config-applied + behaviour**, **`CacheDriverExecutor.find(sql, Map, CacheArray)` in the canonical `getGlobalCacheArray()` + `Console.callback()` pairing** (async queue: find fills synchronously, the CacheArrayFilter worker consumes; the JS callback body is best-effort — GraalJS forbids the worker thread from entering the JS context) |
| TestCrud | 23 | **Independent c/r/u/d cases per table** (5 tables × 4) + schema existence + soft-delete + 6-table dbVerify |

**Mandatory test conventions (enforced throughout)**:

- **All DB connections go through `Console.getDataSource()`** (runtime datasource, `config/DataSource.properties` → `jdbc:h2:tcp://localhost:3306/testdb`, user `sample_user`);
- **All CRUD goes through `CacheDriverExecutor`** (the DriverExecutor synchronous API: `execute()` / `first()` / `find()`); every phase is followed by a **re-select**;
- **dbVerify always uses `SELECT *`** (full-column nested-table rendering, incl. row num); asserts 1 row after INSERT and 0 rows after DELETE;
- **`ghost_remote_command` is not tested**: `fallback.sql` does not DROP it, `change.sql` uses `CREATE TABLE IF NOT EXISTS`, suites never touch its data — running tests while GhostConsole is up no longer hits `42104 Table not found`;
- Reports use the GitHub-style four-column table (Case Id / Test Objective / Result / Detail) + Config Snapshot + yellow-toned dbv nested tables (renderer and style aligned with the server edition).

## Build & Versioning

- `version.properties` (`jr.major` / `jr.minor` / `jr.patch`) auto-increments **patch +1** on every `ant build` (carry at 999, semver).
- The version is written into `ConsoleImpl.VERSION` (placeholder `${BuildVersion.VERSION}(${BuildVersion.BUILD_DATE})` substituted before compile, restored after).
- Each build removes the old `jsplus-core-*.jar` and outputs the versioned jar.
- Each build appends one history entry (UTF-8) to **two** changelog files: `changes.log` (English) and `changes_zh-HK.log` (Traditional Chinese), each headed `## <version> (<builddate> <buildtime>)`. Entry priority per language: `-Dchange.en="..."` / `-Dchange.zh="..."` (CI) → `build\changes-pending-en.txt` / `build\changes-pending-zh-HK.txt` (local note, consumed and deleted) → default `- build: automatic version bump` / `- build: 自動版本遞增`.
- Each build also diffs the Java sources against the sibling copy at `jr.diffroot` (default `../../Githubrepos/JsPlus-Core/src`, skipped when absent) and writes every differing file into both changelogs as `- found diffed: <path> (A|M|D)` — a temporary marker for later review/refinement (e.g. syncing the change to the Githubrepos checkout).

## Database & Configuration

- Database: **`testdb`** (H2 server `tcp://localhost:${H2_PORT}/testdb`, `H2_BASE_DIR=${JS_PLUS_PATH}../Database/`, `H2_PORT=${JsPlus_H2_PORT:3306}`).
- Account: `sample_user` / `sample_password` (created in `change.sql` via `CREATE USER IF NOT EXISTS` + ADMIN).
- `DataSource.properties` pool parameters — current values are shortened for testing (restore production values as needed):

```
TestValidTimeout=5s   RefreshPoolTimer=1s
IdleTimeout=12s       Timeout=25s
TransactionTimeout=25s  LifeCycle=30s
MinPoolSize=1         MaxPoolSize=100
```

- `ghost_remote_command` is the runtime GhostConsole queue table: idempotency is guaranteed by `IF NOT EXISTS`; init and tests only ensure it exists, never delete it.

## Quality

- `QUALITY-REVIEW.md`: 70/100 seven-dimension quality report (all P0 items done) — server-edition alignment, toolchain, test closure, versioning, known limitations (e.g. GraalJS `load()` shares the global scope, CacheArray global `forceStop`, PowerShell must use `cmd /c` for `< NUL`).
- **GraalJS thread confinement** (verified on 25.0.3): a `CacheArray` filter worker thread cannot enter the JS context (`Multi threaded access … not allowed for language(s) js`), so a JS `Console.callback(...)` body never fires on the worker thread. TestDatasource C14 uses `callbackSync=false` and asserts the observable consumption link instead (value removed, key kept, `dbVerify` independent).

## Environment Overrides (same naming convention as the server edition)

| Variable | Default | Purpose |
| --- | --- | --- |
| `JsPlus_JavaDir_JsPlus-Core` | `default` | JDK directory name under `Java\` |
| `JsPlus_JvmParams_JsPlus-Core` | `-Xms256m -Xmx1024m -Xss256k` | JVM parameters |
| `SCRIPT_ENGINE` (script arg %1) | `graalvm.js` | Script engine |

## Running

```bat
:: Build + full test (the test flow starts its own H2 server, inits testdb, then cleans up precisely)
ant.bat build
test\TestAll.bat
```

To launch the console directly: the classpath must include all dependency directories under `LibHome` (`graalvm\default`, `nashorn\default`, `h2\default`, `jsplus\default`) plus the core jar; see `JsPlus\bin\StartCore.bat`.
