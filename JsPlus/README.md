# JsPlus-Core runtime anchor (`JsPlus/`)

This directory is the **JS_PLUS_PATH runtime anchor** for JsPlus-Core, mirroring
the layout of the sibling project `JsPlus-Tomcat` (which ships a full runtime
under `JsPlus/` with `bin/`, `script/`, `Database/`, ...).

JsPlus-Core is the embeddable engine only — there is no server, H2 or
`bin/` launcher set here. What this anchor provides:

| Path | Purpose |
| --- | --- |
| `config/Logger.properties` | Log configuration loaded by `ScriptConsole.initEnv()`; `FileOutFolder=${JS_PLUS_PATH}/../logs/app/` writes logs to `<PROJECT_ROOT>/logs/app/`. |
| `.tmp/` | Created automatically by `ScriptConsole.initPool()` (exit-signal / temp work area). |

The automated test launchers (`test/TestAll.bat` / `test/TestAll.sh`) set
`JS_PLUS_PATH=<PROJECT_ROOT>/JsPlus/`, so the test suites resolve
`<PROJECT_ROOT>/test/` for the JS harness and write reports to
`<PROJECT_ROOT>/test_reports/`.

To run the JsPlus console directly against this core (build first, see below):

```bat
:: Windows (from a checkout root; requires a JDK on JAVA_HOME or PATH)
java -cp "LibHome\Core\default\lib\*;LibHome\jsplus\default\jsplus-core-*.jar" com.killcoding.jsplus.Main -Env script -ScriptEngine graalvm.js
```

```sh
# Linux / macOS
java -cp 'LibHome/Core/default/lib/*:LibHome/jsplus/default/jsplus-core-*.jar' com.killcoding.jsplus.Main -Env script -ScriptEngine graalvm.js
```

> Build: `ant.bat` / `ant.sh` (Tomcat-standard build.xml — version bump →
> `clear` → `compile` → `package`) produces a single JAR:
> `<PROJECT_ROOT>/LibHome/jsplus/default/jsplus-core-<version>.jar`
> (contains `com.killcoding.*` + the `org.*` logging replacements, exactly the
> `LibHome\jsplus\default\*.jar` layout `JsPlus-Tomcat` uses for its runtime JAR).
> `<PROJECT_ROOT>/build/classes/` holds the intermediate compiled classes only.
>
> Build version (Tomcat-standard, same as JsPlus-Tomcat): `<PROJECT_ROOT>/version.properties`
> (keys `jr.major` / `jr.minor` / `jr.patch`) is auto-bumped by **+1 patch on every
> `ant build`** with semver carry at 999; the version is stamped into
> `ConsoleImpl.VERSION` (`${BuildVersion.VERSION}(${BuildVersion.BUILD_DATE})`
> placeholder → `<version>(<build date>)`) before compilation and the placeholder
> is restored right after. Each build also deletes the previous
> `jsplus-core-*.jar` so the runtime classpath never holds two core JARs.
