# JsPlus-Core

JsPlus-Core 是 **JsPlus 的嵌入式控制台引擎**——只含核心引擎與工具庫（`com.killcoding.{jsplus,file,cache,tool,log,datasource}`），不依賴 Web 容器、無內建 H2 服務端進程。

> `org/*`（日誌替換類）已拆分為獨立專案，本倉庫不再包含 `src/org`。

## 快速開始（三步）

本專案自帶工具鏈（無需安裝 Java / Ant，無需設 `JAVA_HOME`）：

| 步驟 | 命令 | 產出 |
| --- | --- | --- |
| 1. 構建 | `ant.bat`（Linux/macOS：`ant.sh`） | `LibHome\jsplus\default\jsplus-core-<version>.jar` |
| 2. 測試 | `test\TestAll.bat`（Linux/macOS：`test\TestAll.sh`） | `docs\latest\reports\*`（HTML / MD / CI 摘要） |
| 3. 精簡執行期 | `packJdk.bat`（Linux/macOS：`packJdk.sh`） | `Java\runtime`（jlink 裁剪） |

驗證基線：`ant build` → BUILD SUCCESSFUL；`TestAll.bat` → **237 passed / 0 failed、exit 0**（19 個 JS 套件 + LoadTest.js；含 H2 服務端 tcp 整合、連接池參數行為測試、`CacheDriverExecutor.find(sql, Map, CacheArray)` + `globalCacheArray`、Cache 靜態 API、`evalJavaSource` 動態編譯、`JsString` 互操作）；收尾 `:3306` 釋放、無殘留進程。

## 與伺服器版的對標（已適配項目）

| 項目 | JsPlus-Core 狀態 |
| --- | --- |
| `build.xml` | 與伺服器版標準完全一致（單 jar → `LibHome\jsplus\default`、`-Xlint:none`、clear/compile/package/build；`org/*` include 為死配置已刪） |
| 版本機制 | `version.properties`（6.1.x）每次 `ant build` 自動 patch+1，jar 名 `jsplus-core-${jr.version}.jar`，構建前刪舊 jar；每次構建向 `changes.log`（英）與 `changes_zh-HK.log`（繁中）各追加版本歷史（`-Dchange.en` / `-Dchange.zh` / `build\changes-pending-en.txt` / `build\changes-pending-zh-HK.txt` / 預設）（伺服器版怪癖：jar 名慢一個 patch，見 `QUALITY-REVIEW.md`） |
| `JsPlus\config` | 7 檔 + 2 子目錄複製（伺服器版 `Logger.properties`；Core 原版備份於 `build\.logger-core-backup.properties`） |
| `dbscript\` | 重寫：只保留 `ghost_remote_command` + 5 張測試表；`tomcatdb` 全域改名 **`testdb`** |
| 測試體系 | `TestBase.js` / `TestAll.js` 渲染器與報告風格對齊伺服器版（GitHub 風格 + dbv 嵌套表） |

## 目錄結構

| 路徑 | 用途 |
| --- | --- |
| `src/` | 引擎與工具源碼（48 個 .java） |
| `LibHome/` | 執行期依賴（`graalvm\default` 腳本引擎、`h2\default` JDBC 驅動、`nashorn\default`）；構建產物輸出至 `jsplus\default\` |
| `Java/default` | 自帶 JDK（Oracle GraalVM 25.0.3，含 jdeps/jlink） |
| `Java/runtime` | `packJdk` 生成的裁剪執行期（可刪重建） |
| `ant/default` | 自帶 Apache Ant 1.10.17 |
| `JsPlus/bin` | 啟動/停止腳本族（見上，`JS_PLUS_PATH` 指向 `JsPlus\`） |
| `JsPlus/config` | 執行期設定（`DataSource.properties` / `GhostDataSource.properties` / `Appenv.properties` / `Logger.properties` 等） |
| `JsPlus/ghost` | GhostConsole 工作目錄 |
| `JsPlus/script` | `StartH2.js` / `H2Server.js`（H2 服務端啟動腳本，無外部依賴） |
| `dbscript/` | `fallback.sql` → `change.sql`（建庫順序固定；`ghost_remote_command` 永不 DROP、測試不碰） |
| `test/` | 測試框架（`TestBase.js` / `TestAll.js` / `TestAll.bat` / `TestAll.sh`）與 19 個 JS 套件 + `LoadTest.js` |
| `docs/latest/api/` | 最新版本的 API javadoc 站點，每次 `ant build` 重新生成（可用 `-Djavadoc.skip=true` 略過）；開啟 `docs/latest/api/index.html` |
| `docs/apis/<version>.jar` | 每個發佈版本的 API javadoc 歸檔 jar（一版本一 jar） |
| `docs/latest/report/` | 最新一次執行的測試報告——套件直接寫入此處，每次 `TestAll.bat` / `TestAll.sh` 覆蓋更新；開啟 `docs/latest/report/TestAll-Report.html` |
| `docs/reports/<version>.jar` | 每次執行的測試報告歸檔 jar（一版本一 jar） |
| `logs/` | 執行期日誌（`app\`、`DS\` 池狀態、`SqlLog\`） |
| `Database/` | H2 服務端資料庫檔案（`testdb.mv.db`，`.gitignore` 忽略） |
| `build/classes/` | 中間編譯產物（僅構建時存在） |
| `version.properties` | 構建版本狀態（自動遞增） |

## 測試體系

### 框架特色：用 JavaScript 驗證 Java

JsPlus-Core 的標誌性特點是——**每個測試套件都是一個 JavaScript 檔，運行在嵌入式控制台引擎內**，也就是生產代碼所嵌入的同一個引擎。套件直接呼叫真實 Java API（`Console`、`Cache`、`CacheDriverExecutor`、`DiskFile`、JDBC），對即時結果做斷言，並生成 GitHub 風格 HTML/MD 報告。沒有 JUnit、沒有獨立測試框架：`test/*.js` + `test/TestBase.js` 就是整套測試體系。

### 怎樣執行

| 想做什麼 | 命令 |
| --- | --- |
| 全量執行：init H2 + testdb、19 套件 + 負載測試、清理、歸檔報告 | `test\TestAll.bat`（Linux/macOS：`test\TestAll.sh`） |
| 只跑資料源（專用 `TestDatasource.properties` 池參數調校） | `test\TestDatasource.bat`（`.sh`） |
| 只跑負載測試（DB CRUD 壓力，`LOADTEST_ROUNDS` / `LOADTEST_TABLES`） | `test\LoadTest.bat`（`.sh`） |
| 任意單一套件 | 抄 `TestAll.bat` 內 `-Task test/TestXxx.js` 一行即可；每個套件都是普通 console task，可獨立執行 |

報告輸出至 `docs\latest\reports\*`（HTML + MD + CI 摘要），每次執行自動歸檔至 `docs/reports/<version>.jar`。

### `TestAll.bat` 流程（5 步，每次 init testdb）

1. 查 `:3306` 未監聽則 `BG_StartH2.bat %SCRIPT_ENGINE%`（→ `StartH2.bat` → `Main -Task script/StartH2.js`，獨立 JVM），輪詢 45×2s 就緒；
2. `RunScript` 以 `jdbc:h2:tcp://localhost:3306/testdb`（`-user sa`、`-checkResults`）先 `fallback.sql` 再 `change.sql`；
3. 跑 `TestAll.js`（`JSPLUS_TEST_EXIT=1`，`< NUL` 關 stdin），再跑 `LoadTest.js`（同一 testdb，`docs/latest/report/LoadTest-Report.html`；負載失敗即整輪失敗）；
4. 精準殺含 `script/StartH2.js` 的 java 進程，再執行完整 `StopAll.bat` 全面清掃（專案慣例：不留任何 JsPlus/H2 JVM）；
5. 將 `docs/latest/report/` 歸檔至 **`docs/reports/<version>.jar`**（版本取自 core jar 檔名）——歸檔失敗僅 WARN、不影響退出碼；然後 `exit /b %RC%`。

### 套件清單

| 套件 | Case 數 | 重點 |
| --- | --- | --- |
| TestDatasource | 13 | Clock、`Console.getDataSource()` 連接、DDL/DML roundtrip、池借還、**池滿上限（MaxPoolSize 溢出拒絕）**、**6 池參數（TestValidTimeout / RefreshPoolTimer / IdleTimeout / Timeout / TransactionTimeout / LifeCycle）配置生效 + 行為** |
| TestCrud | 23 | **每表獨立 c/r/u/d 四個 case**（5 表 × 4）+ schema 存在 + 軟刪除 + 6 表 dbVerify |
| TestRemoteFile | 7 | RemoteFile 資料源：list / write / read / delete + mkdirs（對映表） |
| TestFind | 5 | `CacheDriverExecutor.find` 餵入全域 `CacheArray`（正典非同步配合） |
| TestDiskFile | 18 | DiskFile write/read/delete、目錄、大小、編碼解碼 |
| TestClock | 11 | `Clock` 時間 API + `AppClock.properties` |
| TestIdentityCard | 9 | IdentityCard 資料源 + 對映表 roundtrip |
| TestXmlParser | 14 | XML 解析 + `toMap()` |
| TestCommonTools | 17 | CommonTools 工具函式 |
| TestDataObject | 7 | DataObject 儲存/讀取 |
| TestResultMap | 12 | ResultMap 存取 |
| TestConfigProperties | 17 | ConfigProperties parse / get / env |
| TestCipherTools | 11 | 加解密編碼解碼 |
| TestEscapeTools | 16 | escape/unescape |
| TestFileTools | 10 | 檔案工具 |
| TestCache | 11 | Cache 靜態 API：set/get/included/remove/removeAll/getNames + StoredCache（覆蓋 life 溢位 / 0=永久 / 懶初始化修正） |
| TestCacheArray | 15 | CacheArray add/remove/sweep + filter worker（execute / executeBatch / completed） |
| TestEvalJava | 7 | `Console.evalJavaSource` 動態編譯 + 載入（內容定址 jar 快取於 `JsPlus/.tmp/java`） |
| TestJsString | 13 | JsString 互操作層（應對 GraalJS 字串） |
| LoadTest | — | 同一 testdb 重負載 DB CRUD 壓力（`TestAll.bat` 步驟 3b 執行） |

### 測試硬性約定（全程強制）

- **DB 連接一律 `Console.getDataSource()`**（運行時資料源，`config/DataSource.properties` → `jdbc:h2:tcp://localhost:3306/testdb`、`sample_user`）；
- **CRUD 全部經 `CacheDriverExecutor`**（DriverExecutor 同步 API：`execute()` / `first()` / `find()`）；每階段後 **re-select**；
- **dbVerify 一律 `SELECT *`**（全列嵌套表渲染，含 row num）；INSERT 後斷言 1 行、DELETE 後斷言 0 行；
- **`ghost_remote_command` 測試不碰**：`fallback.sql` 不 DROP 它、`change.sql` 用 `CREATE TABLE IF NOT EXISTS`、套件不清其資料——GhostConsole 運行中跑測試不再報 `42104 Table not found`；
- 報告為 GitHub 風格四列表（Case Id / Test Objective / Result / Detail）+ Config Snapshot + 黃色調 dbv 嵌套表（渲染器與樣式對齊伺服器版）。

### 怎樣寫一個新套件

1. 複製現有套件的骨架（例如 `test/TestClock.js`）。
2. 載入測試框架——`load(System.getProperty("JS_PLUS_PATH") + "../test/TestBase.js")`——並設定 `REPORT_TITLE` / `REPORT_NAME`。
3. 呼叫 `beginTest()` 重置計數，然後每個斷言用一個 `check({caseId, name, ok, detail, objective, objectiveZh, command})`；報告自動編號 C1…Cn。
4. DB 斷言：用 `Console.getDataSource()` 取連接、CRUD 用 `CacheDriverExecutor`、用 `dbVerify(function () { return "SELECT * FROM ..."; })` 驗證持久化資料。
5. 最後 `writeReport()`；**永不呼叫 `System.exit()`**——`TestAll.js` 統一決定 JVM 退出，避免單一套件失敗中止整輪。
6. 在 `test/TestAll.js` 註冊套件（`suites` 陣列 **及** 一行式套件列印）；LoadTest 與執行順序均依此陣列。

## 構建版本機制

- `version.properties`（`jr.major` / `jr.minor` / `jr.patch`）每次 `ant build` 自動 **patch +1**（999 進位，semver）。
- 版本寫入 `ConsoleImpl.VERSION`（占位符 `${BuildVersion.VERSION}(${BuildVersion.BUILD_DATE})` 編譯前替換、編譯後還原）。
- 每次構建刪除舊 `jsplus-core-*.jar`，輸出帶版本 jar。
- 每次構建向 **兩個** changelog 檔（UTF-8）各追加一筆版本歷史：`changes.log`（英文）與 `changes_zh-HK.log`（繁體中文），標題 `## <版本> (<建構日期> <建構時間>)`。每語言之說明優先序：`-Dchange.en="..."` / `-Dchange.zh="..."`（CI）→ `build\changes-pending-en.txt` / `build\changes-pending-zh-HK.txt`（本地註記，消費後刪除）→ 預設 `- build: automatic version bump` / `- build: 自動版本遞增`。
- 每次構建亦會對比 Java 源碼與 `jr.diffroot`（預設 `../../Githubrepos/JsPlus-Core/src`，不存在時略過）的姊妹副本，將差異檔案以 `- found diffed: <path> (A|M|D)` 寫入兩個 changelog——作為稍後檢視/完善的臨時標記（例如將變更同步至 Githubrepos 工作副本）。
- 每次構建重新生成 API 站點至 **`docs/latest/api/`**（window title 帶版本號、`-Xdoclint:none`、附原始碼連結）並歸檔為 **`docs/apis/<version>.jar`**（一版本一 jar）；可用 `-Djavadoc.skip=true` 略過。
- 每次 `TestAll.bat` / `TestAll.sh` 執行後將 `docs/latest/report/` 歸檔至 **`docs/reports/<version>.jar`**（與 javadoc 站點同一 docs 架構）。

## 資料庫與配置

- 資料庫：**`testdb`**（H2 服務端 `tcp://localhost:${H2_PORT}/testdb`，`H2_BASE_DIR=${JS_PLUS_PATH}../Database/`，`H2_PORT=${JsPlus_H2_PORT:3306}`）。
- 帳號：`sample_user` / `sample_password`（`change.sql` 內 `CREATE USER IF NOT EXISTS` + ADMIN）。
- `DataSource.properties` 池參數當前值（為測試縮短，生產可調回）：

```
TestValidTimeout=5s   RefreshPoolTimer=1s
IdleTimeout=12s       Timeout=25s
TransactionTimeout=25s  LifeCycle=30s
MinPoolSize=1         MaxPoolSize=100
```

- `ghost_remote_command` 為運行時 GhostConsole 佇列表：**幂等語義由 `IF NOT EXISTS` 保證**，測試與 init 只確保其存在、永不刪。

## 品質

- `QUALITY-REVIEW.md`：70 分七維質量報告（P0 已全部完成）——含伺服器版對標、工具鏈、測試閉環、版本機制、已知限制（如 GraalJS `load()` 共享全域作用域、CacheArray 全域 forceStop、PowerShell 須 `cmd /c` 傳 `< NUL`）。
- **GraalJS 執行緒限定**（25.0.3 實測）：`CacheArray` 的 filter worker 執行緒無法進入 JS context（`Multi threaded access … not allowed for language(s) js`），因此 JS `Console.callback(...)` 的回調體在 worker 執行緒上不會觸發。正典做法（見 TestFind / TestCacheArray）：餵入 console 以 `find(...)` 寫入 `getGlobalCacheArray()`，再由另一 callback console 透過 `CacheArrayFilter` worker（`execute` / `executeBatch` / `completed`）觀察消費。

## 架構參照：開源對應生態

CacheArray 管線（異步 filter worker + 生產／餵入／消費三 console + 回調代理）對應一組成熟的開源模式，對照表如下：

| JsPlus-Core 設計 | 開源對應物 | 定位 |
| --- | --- | --- |
| `CacheArray.filter(worker)` 逐行觸發 callback（`execute`≈`onNext`、`completed`≈`onComplete`、`terminated`≈`onError`／取消） | **RxJava / Project Reactor**（Flowable/Flux） | 可觀察序列 + 背壓 + 異步消費——語義最貼近 |
| `CacheDriverExecutor.find(sql, params, GCA)` 逐行 add | **Spring JdbcTemplate RowCallbackHandler** / **MyBatis Cursor** / **jOOQ** 流式查詢 | JDBC 結果集逐行處理，不整表載入記憶體 |
| `executeBatch` + `sweepProcessed`（批量消費 + 頭部回收） | **LMAX Disruptor**（無鎖環形緩衝 + 批量事件處理） | 低延遲批量消費 |
| insertConsole → feedConsole → callbackConsole（多角色 console） | **Akka Actor** / **Vert.x Verticle** / **ZeroMQ** | 多角色獨立消息循環 |
| 批量 chunk + 完成回調 | **Spring Batch**（ItemReader→Processor→Writer chunk） | 批處理管線（ETL／Job） |
| filter worker 輪詢 + 週期任務 | **ScheduledExecutorService** / **Quartz** / Node.js `setInterval` | 定時驅動消費循環 |
| 快取 + 淘汰／回收 | **Caffeine / Guava Cache**（異步 refresh、淘汰 ≈ `sweepProcessed`／`removeAll`） | 快取生命週期管理 |
| Java worker 執行緒 + `Java.extend(Runnable)` 代理（GraalJS 執行緒限定） | **GraalVM Polyglot API** 宿主互操作 | 多語言嵌入的跨執行緒邊界 |

整體最貼近：**RxJava／Reactor**（可觀察語義）、**Disruptor**（批量消費）、**Akka Actor**（多 console 分工）、**MyBatis Cursor／Spring RowCallbackHandler**（JDBC 逐行流式）。

## 環境變數覆蓋（與伺服器版同名慣例）

| 變數 | 預設 | 用途 |
| --- | --- | --- |
| `JsPlus_JavaDir_JsPlus-Core` | `default` | `Java\` 下的 JDK 目錄名 |
| `JsPlus_JvmParams_JsPlus-Core` | `-Xms256m -Xmx1024m -Xss256k` | JVM 參數 |
| `SCRIPT_ENGINE`（腳本參數 %1） | `graalvm.js` | 腳本引擎 |

## 執行模式

```bat
:: 構建 + 全量測試（測試流程自起 H2 服務端、init testdb、跑完精準清理）
ant.bat build
test\TestAll.bat
```

直接啟動控制台：classpath 需包含 `LibHome` 下全部依賴目錄（`graalvm\default`、`nashorn\default`、`h2\default`、`jsplus\default`）與核心 jar；詳見 `JsPlus\bin\StartCore.bat`。
