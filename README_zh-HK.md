# JsPlus-Core

**嵌入式腳本控制台引擎**—— 把 GraalVM JavaScript 執行環境內嵌進任何 Java 應用，並提供伺服器端能力庫：池化資料源、多層快取、檔案抽象、DB 同步、加密 / 剖析 / 工具集。無 Web 容器依賴（不依賴 Tomcat / Servlet）；引擎本體不持有常駐 H2 進程—— H2 資料庫伺服器由 `JsPlus\script\StartH2.js` 按需啟動（測試 / 運行時經 `StartH2.bat` 拉起、`StopH2.bat` 關閉）。



```
Java 應用 ──► ScriptConsole（嵌入 GraalVM JS 引擎）
              │
              ├── 執行 JS（eval / evalScript / evalJavaSource 動態編譯）
              ├── 資料源（DriverDataSource 池化 + CacheDriverExecutor）
              ├── 快取（Cache 靜態 API + CacheArray 非同步過濾管道）
              ├── 檔案（DiskFile 本地 / RemoteFile 資料庫映射）
              ├── DB 同步（dbsync 管道：Insert/Update/Delete/Select）
              └── 工具（CommonTools / ConfigProperties / XmlParser / CipherTools …）
```

## 1. 套件架構（src・61 個 Java 類）



| 套件                          | 核心類                                                                                                                                       | 職責                                                                     |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `com.killcoding.jsplus`     | ScriptConsole、ScriptConsoleBase、ConsoleImpl、JsPlusContext、GraalVmContext、NashornContext、JsString、Main、GhostConsole、CmdConsole、JdbcConsole | **Console 引擎核心**：腳本執行、多 console 隔離、引擎橋接（GraalJS/Nashorn）、物件橋、動態編譯、命令執行 |
| `com.killcoding.datasource` | DriverDataSource、ConnectionPool、DriverConnection、DriverExecutor、CacheDriverExecutor、Clock、IdentityCard                                    | **池化資料源與 JDBC**：連接池生命週期、同步執行器、快取陣列饋送                                   |
| `com.killcoding.cache`      | Cache、CacheArray、CacheArrayFilter、MemoryCache、DiskCache、StoredCache、AbsCacheApi、DataObject                                                | **多層快取**：靜態鍵值 API + 非同步過濾管道（producer/consumer）                         |
| `com.killcoding.file`       | DiskFile、RemoteFile、RemoteFileBase、RemoteFileSync、BaseFile、FilePart、RemoteFileSQL                                                         | **檔案抽象**：本地檔案操作、資料庫映射檔案（分片 / 同步）                                       |
| `com.killcoding.dbsync`     | Database、Table、Inserter、Updater、Deleter、Remover、Enter、SqlStatement                                                                        | **資料同步管道**：跨庫 / 跨表增量同步                                                 |
| `com.killcoding.tool`       | CommonTools、ConfigProperties、ResultMap、XmlParser、CipherTools、EscapeTools、Base64Tools、FileTools、CodeEscape\*                               | **通用工具**：設定 / 剖析 / 加密 / 轉義 / 檔案                                        |
| `com.killcoding.log`        | Logger、LoggerFactory、AlertMessage、LogRecord                                                                                               | **日誌**：非同步寫入、警報訊息                                                      |

## 2. Console API（核心入口）

`ScriptConsole` 是唯一入口，可實例化任意多個（各自獨立引擎上下文）：

### 執行與求值



| API                                                  | 說明                                                      |
| ---------------------------------------------------- | ------------------------------------------------------- |
| `evalScript(String)`                                 | 執行原始 JS 字串，返回結果                                         |
| `eval(String/File)`                                  | 執行腳本檔案（路徑或 File）                                        |
| `eval(Runnable)`                                     | 在控制台執行緒執行 Java 回呼                                       |
| `evalJavaSource(String / String[])` → `ClassLoader`  | **動態編譯 Java 原始碼**並載入類別（內容定址 jar 快取於 `JsPlus/.tmp/java`） |
| `evalJavaFile(String / File)` → `ClassLoader`        | 動態編譯 Java 檔案                                            |
| `execute(String)` / `executeBatch(String)`           | 執行 JsPlus 命令 / 批次檔案                                     |
| `executeViaGhost / executeViaCmd / executeViaScript` | 經 Ghost 控制台 / 系統命令 / 腳本執行                               |

### 回呼（callback）



| API                                        | 說明                                            |
| ------------------------------------------ | --------------------------------------------- |
| `callback(Runnable, boolean callbackSync)` | 註冊回呼（非同步 / 同步執行）                              |
| `callbackBatch(Runnable, boolean)`         | 批次回呼                                          |
| 搭配 `Java.extend(Runnable)`                 | JS 側把函數包裝為 Java Runnable 代理（GraalJS 執行緒隔離的橋樑） |

### 上下文與物件橋



| API                                                   | 說明                                                                       |
| ----------------------------------------------------- | ------------------------------------------------------------------------ |
| `getContext()` → `JsPlusContext`                      | 引擎上下文（`put/get/remove` 名值橋、`evalScript`、`getEngine/getBindings`、`close`） |
| `newBuilder()` / `newBuilder(engine, allowAllAccess)` | **建立獨立控制台**（隔離上下文，並行執行）                                                  |
| `initScriptEngineManager(boolean allowAllAccess)`     | 初始化引擎（可放寬安全存取）                                                           |
| `closeContext()`                                      | 釋放上下文                                                                    |

### 全域快取陣列



| API                                               | 說明                           |
| ------------------------------------------------- | ---------------------------- |
| `getGlobalCacheArray(long timer, long loopTimer)` | 取得全域快取陣列（filter worker 定時消費） |
| `initGlobalCacheArray(long, long)`                | 初始化全域快取陣列                    |

### 排程與任務



| API                                                                           | 說明        |
| ----------------------------------------------------------------------------- | --------- |
| `addSchedule(name, cronRules, Runnable)` / `clearSchedule` / `getScheduleMap` | cron 排程   |
| `setTask(String)` / `taskExecute()`                                           | 一次性任務模式   |
| `version(List)` / `help(List)`                                                | 版本 / 命令說明 |

### GhostConsole — 跨進程命令隊列

`GhostConsole`（`com.killcoding.jsplus.GhostConsole`）是無頭執行期：主控制台經 `executeViaGhost(commands)` 把命令寫入 `ghost_remote_command` 隊列表，GhostConsole 進程輪詢執行並寫回結果——用於跨進程 / 遠程控制（headless runtime）。

隊列表結構（`dbscript/change.sql`，`CREATE TABLE IF NOT EXISTS`）：

| 欄位 | 說明 |
| --- | --- |
| `id` | 唯一命令記錄 id（CHAR(16) PK） |
| `ghost_name` | 擁有該命令的 ghost 控制台 |
| `request_script` | ghost 要執行的腳本 |
| `response_message` | ghost 執行後返回的文字 |
| `processed` | 0 = 待處理、1 = 已處理 |
| `requested` / `responsed` | 請求 / 響應計數器 |
| `created_at` / `updated_at` / `deleted_at` / `deleted` | 時間戳 + 軟刪除（0 = active、1 = deleted） |

> 運行時規則：`ghost_remote_command` 屬於執行期（GhostConsole），測試 / 初始化永不 DROP 或重建它（`IF NOT EXISTS` 保證冪等）——否則運行中的 ghost 會撞「table not found」。

## 3. 資料層機制（com.killcoding.datasource）

### DriverDataSource 連接池（參數語義）



| 參數                            | 語義                                         |
| ----------------------------- | ------------------------------------------ |
| `MinPoolSize` / `MaxPoolSize` | 池上下限；超過 `MaxPoolSize` 的借出被拒絕（overflow 擲例外） |
| `TestValidTimeout`            | 借出前有效性檢測逾時                                 |
| `RefreshPoolTimer`            | 池刷新週期（驗證 / 回收閒置連接）                         |
| `IdleTimeout`                 | 閒置逾時回收（收斂至 `MinPoolSize`，最後 1 個閒置連接受保護）    |
| `Timeout`                     | 已借出連接（autoCommit=true）逾時中斷回收               |
| `TransactionTimeout`          | 交易中連接（autoCommit=false）逾時中斷                |
| `LifeCycle`                   | 連接總壽命回收（不受 MinPoolSize 保護）                 |

池自癒：連接被中斷後 worker 自動回補至 `MinPoolSize`；空池借出自動重載。

### CacheDriverExecutor（JDBC 執行器）



* `execute(sql, params)` / `first(sql, params)` / `find(sql, params)` — 同步 JDBC

* `find(sql, params, CacheArray)` — **逐列饋送**進全域快取陣列（串流消費，不整集物化）

### Clock / IdentityCard



* `Clock` — 時間 API（Date/SQL 時間戳 / 日曆，可設定 `Clock.properties`）

* `IdentityCard` — 身份證資料源映射

## 4. 快取層（com.killcoding.cache）

### Cache 靜態 API — life 語義（重要）



```
Cache.set(name, value, life)
life >= 0  → 存（0 = 永不過期）
life < 0   → 跳過（不寫入）
```

> **消費方約定**
>
> ：若你的應用以 
>
> `0`
>
>  表示「停用快取」，
>
> **必須跳過&#x20;**
>
> `Cache.set`
>
> **&#x20;呼叫**
>
> （不要傳 0——Core 會解釋為永不過期）。見 JsPlus-Tomcat B2 修復（D3/D4）：
>
> `cacheSeconds > 0`
>
>  才呼叫。

`set(name, value)` 無 life 版 = 永不過期（合法用途，如靜態設定）。

### CacheArray — 非同步過濾管道

`CacheArray + CacheArrayFilter`：producer 控制台把資料逐項 / 批次餵入，worker 執行緒按 `timer/loopTimer` 消費，經 `execute / executeBatch / completed / terminated` 回呼驅動（對應 RxJava/Reactor 的 onNext/onComplete/onError 語義）。

### 實現

`MemoryCache`（預設）/ `DiskCache` / `StoredCache` / `AbsCacheApi`（可換實現，`ScriptConsoleBase.setCacheApi`）。

## 5. 檔案層與 DB 同步



* **DiskFile** — 本地檔案：寫 / 讀 / 刪、目錄、大小、encode/decode

* **RemoteFile** — 資料庫映射檔案（`RemoteFileBase` 基礎 + `RemoteFileSync` 同步 + 表分片 `FilePart`）

* **dbsync** — `Database/Table` + `Inserter/Updater/Deleter/Remover`：跨庫增量同步管道

## 6. 正典模式（核心用例）



1. **雙 console 物件橋**：生產 console 執行 JS 把結果 `getContext().put(name, obj)`，獨立消費 console `getContext().get(name)` 讀取 —— 跨控制台隔離傳值。

2. **find → CacheArray 饋送**：`CacheDriverExecutor.find(sql, params, getGlobalCacheArray(...))` 逐列饋送，獨立 callback console 以 `Java.extend(Runnable)` 代理 + `CacheArrayFilter` 觀察消費 ——GraalJS 執行緒隔離下 worker 執行緒不能直接進 JS 上下文，這是唯一正典路徑（見 `TestFind` / `TestCacheArray`）。

3. **動態編譯**：`evalJavaSource(...)` 把 Java 原始碼即時編譯載入 —— 無需預先編譯即可在執行期擴充能力（見 `TestEvalJava`）。

## 7. 嵌入範例（Java）



```
import com.killcoding.jsplus.ScriptConsole;
import com.killcoding.cache.CacheArray;

ScriptConsole console = new ScriptConsole();
console.initScriptEngineManager(true);              // 啟動 GraalVM JS 引擎
console.evalScript("1 + 1");                        // → 2

// 獨立控制台：隔離上下文並行執行
ScriptConsole worker = console.newBuilder();
worker.evalScript("var x = 40 + 2; x");

// 物件橋：JS ↔ Java 傳值
console.getContext().put("greeting", "hello");
console.evalScript("greeting.toUpperCase()");       // → "HELLO"

// 全域快取陣列（非同步過濾）
CacheArray gca = console.getGlobalCacheArray(5000, 5000);
gca.add("item");

// 動態編譯 Java 原始碼
ClassLoader cl = console.evalJavaSource(
    "public class Hello { public static String hi() { return \"hi\"; } }");
```

完整啟動範例見 `JsPlus\bin\StartCore.bat`（classpath = `LibHome` 下 `graalvm/default`、`nashorn/default`、`h2/default`、`jsplus/default` + 核心 jar）。

## 8. 構建與測試



| 步驟      | 命令                                    | 產出                                                                                                                                                           |
| ------- | ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 構建      | `ant.bat build`（Linux/macOS：`ant.sh`） | `LibHome\jsplus\default\jsplus-core-<version>.jar`（版本自動 patch+1；每次構建附 changelog 條目至 `changes.log` / `changes_zh-HK.log`；重建 API javadoc 至 `docs/latest/api/`） |
| 全量測試    | `test\TestAll.bat`（`TestAll.sh`）      | **235 passed / 0 failed**（18 個 JS 套件 + LoadTest；自啟 H2 → 初始化 `testdb` → 跑套件 → 精確清理）                                                                           |
| 連接池整合測試 | `test\TestDatasource.bat`（`.sh`）      | **17 passed / 0 failed**（池參數應用 + 逾時回收 + 自癒 + 極限多執行緒壓力）                                                                                                       |

工具鏈隨專案打包（bundled JDK + Ant，無需安裝、無需 `JAVA_HOME`）。所有測試套件均為 **JS 檔案在引擎內執行、直接呼叫真實 Java API 並斷言**—— 無 JUnit。

## 9. 環境變數覆寫（與伺服器版同名約定）



| 變數                             | 預設                            | 用途                |
| ------------------------------ | ----------------------------- | ----------------- |
| `JsPlus_JavaDir_JsPlus-Core`   | `default`                     | `Java\` 下 JDK 目錄名 |
| `JsPlus_JvmParams_JsPlus-Core` | `-Xms256m -Xmx1024m -Xss256k` | JVM 參數            |
| `SCRIPT_ENGINE`                | `graalvm.js`                  | 腳本引擎（可選 Nashorn）  |
| `JsPlus_H2_PORT`               | `3306`                        | H2 TCP 連接埠        |

## 10. 品質與架構定位



* 品質評分 83/100（6.1.204）—— 構建 / 腳本 / CI / 測試四層達發布水準；已知限制：GraalJS `load()` 共用全域作用域、CacheArray 全域 `forceStop`、`-Xlint:none` 壓制告警、Main scanner 依賴例外退出。

* CacheArray 管道對標開源模式：**RxJava/Reactor**（observable 語義）、**LMAX Disruptor**（批次消費）、**Akka Actor**（多控制台分工）、**MyBatis Cursor / Spring RowCallbackHandler**（JDBC 串流饋送）。

### CacheArray 管道對標主流框架

| JsPlus 設計 | 開源對應物 | 生態位 |
| --- | --- | --- |
| `CacheArray.filter(worker)` 逐行觸發回調（execute≈onNext、completed≈onComplete、terminated≈onError） | **RxJava / Project Reactor**（Flowable/Flux） | 可觀察序列 + 背壓 + 非同步消費，語義最貼近 |
| `CacheDriverExecutor.find(sql, params, GCA)` 逐行 add | **Spring JdbcTemplate RowCallbackHandler** / **MyBatis Cursor** / **jOOQ** 串流查詢 | JDBC 結果集逐行回調，不整表駐記憶體 |
| `executeBatch + sweepProcessed`（批量消費 + 頭部回收） | **LMAX Disruptor**（無鎖環形緩衝 + 批量事件處理） | 低延遲批量消費，sweep 與其事件回收同思路 |
| insertConsole 生產 → feedConsole 餵入 → callbackConsole 消費 | **Akka Actor** / **Vert.x Verticle** / **ZeroMQ** | 多角色獨立訊息循環，每 console 一條執行緒/信箱 |
| 批量 chunk + 完成回調 | **Spring Batch**（ItemReader→Processor→Writer chunk） | 批處理管道（ETL/Job），completed≈JobCompletion |
| filter worker 輪詢 + 週期任務 | **ScheduledExecutorService** / **Quartz** / Node.js `setInterval` | 定時驅動消費循環 |
| 快取 + 淘汰/回收 | **Caffeine / Guava Cache**（非同步 refresh、淘汰 ≈ sweepProcessed/removeAll） | 快取生命週期管理 |
| Java worker 執行緒 + `Java.extend(Runnable)` 代理，規避 GraalJS 多執行緒限制 | **GraalVM Polyglot API** 宿主互操作；同思路還有 Rhino/Nashorn 時代的主機回調橋接 | 多語言嵌入的跨執行緒邊界設計 |