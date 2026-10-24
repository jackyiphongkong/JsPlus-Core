# JsPlus-Core

JsPlus-Core 是 **JsPlus 的嵌入式控制台引擎**——只含核心引擎與工具庫（`com.killcoding.{jsplus,file,cache,tool,log,datasource}`），不依賴 Web 容器、無內建 H2 服務端進程。

> `org/*`（日誌替換類）已拆分為獨立專案，本倉庫不再包含 `src/org`。

## 快速開始（三步）

本專案自帶工具鏈（無需安裝 Java / Ant，無需設 `JAVA_HOME`）：

| 步驟 | 命令 | 產出 |
| --- | --- | --- |
| 1. 構建 | `ant.bat`（Linux/macOS：`ant.sh`） | `LibHome\jsplus\default\jsplus-core-<version>.jar` |
| 2. 測試 | `test\TestAll.bat`（Linux/macOS：`test\TestAll.sh`） | `test_reports\*`（HTML / MD / CI 摘要） |
| 3. 精簡執行期 | `packJdk.bat`（Linux/macOS：`packJdk.sh`） | `Java\runtime`（jlink 裁剪） |

驗證基線：`ant build` → BUILD SUCCESSFUL；`TestAll.bat` → **89 passed / 0 failed、exit 0**（含 H2 服務端 tcp 整合、連接池參數行為測試）；收尾 `:3306` 釋放、無殘留進程。

## 與伺服器版的對標（已適配項目）

| 項目 | JsPlus-Core 狀態 |
| --- | --- |
| `build.xml` | 與伺服器版標準完全一致（單 jar → `LibHome\jsplus\default`、`-Xlint:none`、clear/compile/package/build；`org/*` include 為死配置已刪） |
| 版本機制 | `version.properties`（6.1.x）每次 `ant build` 自動 patch+1，jar 名 `jsplus-core-${jr.version}.jar`，構建前刪舊 jar（伺服器版怪癖：jar 名慢一個 patch，見 `QUALITY-REVIEW.md`） |
| `JsPlus\bin` | 18 個 bat/sh 原樣適配；改名族：`StartTomcat→StartCore`、`BG_StartTomcat→BG_StartCore`、`StopTomcat→StopCore`；H2 配套原名保留（`BG_StartH2` / `StartH2` / `StartH2Console` / `StopH2` / `Stop` / `StopAll`） |
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
| `test/` | 測試框架（`TestBase.js` / `TestAll.js` / `TestAll.bat` / `TestAll.sh`）與 7 個套件 |
| `test_reports/` | 測試報告（HTML / Markdown / CI 摘要） |
| `logs/` | 執行期日誌（`app\`、`DS\` 池狀態、`SqlLog\`） |
| `Database/` | H2 服務端資料庫檔案（`testdb.mv.db`，`.gitignore` 忽略） |
| `build/classes/` | 中間編譯產物（僅構建時存在） |
| `version.properties` | 構建版本狀態（自動遞增） |

## 測試體系

**`TestAll.bat` 流程（4 步，每次 init testdb）**：

1. 查 `:3306` 未監聽則 `BG_StartH2.bat %SCRIPT_ENGINE%`（→ `StartH2.bat` → `Main -Task script/StartH2.js`，獨立 JVM），輪詢 45×2s 就緒；
2. `RunScript` 以 `jdbc:h2:tcp://localhost:3306/testdb`（`-user sa`、`-checkResults`）先 `fallback.sql` 再 `change.sql`；
3. 跑 `TestAll.js`（`JSPLUS_TEST_EXIT=1`，`< NUL` 關 stdin）；
4. 精準殺含 `script/StartH2.js` 的 java 進程後 `exit /b %RC%`（不用 `StopAll`，避免誤殺 VS Code Java Language Server）。

**7 個套件**：

| 套件 | Case 數 | 覆蓋 |
| --- | --- | --- |
| TestConsole | 9 | 控制台環境、DiskFile 讀寫 |
| TestCache | 12 | Cache / StoredCache / CacheArray 過濾器 |
| TestFile | 13 | 檔案工具 |
| TestTool | 18 | 通用工具 |
| TestLog | 5 | 日誌框架 |
| TestDatasource | 13 | Clock、`Console.getDataSource()` 連接、DDL/DML roundtrip、池借還、**池滿上限（MaxPoolSize 溢出拒絕）**、**6 池參數（TestValidTimeout / RefreshPoolTimer / IdleTimeout / Timeout / TransactionTimeout / LifeCycle）配置生效 + 行為** |
| TestTables | 23 | **每表獨立 c/r/u/d 四個 case**（5 表 × 4）+ schema 存在 + 軟刪除 + 6 表 dbVerify |

**測試硬性約定（全程強制）**：

- **DB 連接一律 `Console.getDataSource()`**（運行時資料源，`config/DataSource.properties` → `jdbc:h2:tcp://localhost:3306/testdb`、`sample_user`）；
- **CRUD 全部經 `CacheDriverExecutor`**（DriverExecutor 同步 API：`execute()` / `first()` / `find()`）；每階段後 **re-select**；
- **dbVerify 一律 `SELECT *`**（全列嵌套表渲染，含 row num）；INSERT 後斷言 1 行、DELETE 後斷言 0 行；
- **`ghost_remote_command` 測試不碰**：`fallback.sql` 不 DROP 它、`change.sql` 用 `CREATE TABLE IF NOT EXISTS`、套件不清其資料——GhostConsole 運行中跑測試不再報 `42104 Table not found`；
- 報告為 GitHub 風格四列表（Case Id / Test Objective / Result / Detail）+ Config Snapshot + 黃色調 dbv 嵌套表（渲染器與樣式對齊伺服器版）。

## 構建版本機制

- `version.properties`（`jr.major` / `jr.minor` / `jr.patch`）每次 `ant build` 自動 **patch +1**（999 進位，semver）。
- 版本寫入 `ConsoleImpl.VERSION`（占位符 `${BuildVersion.VERSION}(${BuildVersion.BUILD_DATE})` 編譯前替換、編譯後還原）。
- 每次構建刪除舊 `jsplus-core-*.jar`，輸出帶版本 jar。

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
