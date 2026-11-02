# JsPlus-Core

**嵌入式腳本控制台引擎 + 伺服器端能力庫。** 於任何 Java 應用內嵌 GraalVM JavaScript 執行環境，並在 Java 與 JS 之間閉環：一個控制台、一個執行環境、一個 jar——JS 腳本直接呼叫真實 Java API，同一控制台執行、斷言及輸出測試報告。

## 生態閉環與輕量級定位

**JsPlus-Core 是閉環、輕量級的能力引擎：**



| 閉環          | 閉環內容                                                                                                                              |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------- |
| **能力閉環**    | Java 核心庫（資料源 / 快取 / 檔案 / 同步 / 工具箱）→ 統一 `ScriptConsole` → JS 腳本直接呼叫真實 Java API → 同一控制台執行、斷言及報告（測試套件為 JS 檔案、直接斷言真實 Java—— 無需 JUnit） |
| **動態編譯閉環**  | `evalJavaSource()` 於執行期編譯 Java 源碼 → 載入類別 → JS 即時呼叫 —— 無需預編譯、無需重啟即可擴充能力                                                            |
| **數據閉環**    | 按需 H2 + 池化 `DriverDataSource` + 多層 `Cache` + `CacheArray` 非同步管線 + `RemoteFile` 資料庫映射檔案 + `dbsync`—— 儲存 / 快取 / 串流 / 同步完整數據路徑內建     |
| **跨程序閉環**   | `GhostConsole` 佇列 —— 主控制台下達指令 → 幽靈程序輪詢、執行並寫回結果 —— 無需共享程序狀態即可遠端控制                                                                  |
| **伺服器生態閉環** | 與 **JsPlus-Tomcat**（伺服器版）相同的環境變數慣例、控制台 API 及能力庫 ——Core 提供能力，伺服器版提供 HTTP/TCP 服務層                                                   |

**輕量級設計：** 無 Web 容器依賴（無 Tomcat/Servlet）・單一能力 jar・隨附工具鏈（JDK + Ant，無需 `JAVA_HOME`）・按需 H2（無常駐資料庫程序）・以 JS 測試套件取代 JUnit・佔用空間細小（`-Xms256m -Xmx1024m`）。

## 主要功能

開箱即用，一個 jar 放入 Java 專案即可：

- **友好的調試入口** — 內嵌 GraalVM 引擎：改邏輯毋須重啟、毋須重編譯；甚至可於執行期即時編譯一段 Java 源碼並立即呼叫
- **資料庫連接池** — 即用的 JDBC 池：自動管理連線、壞連線自癒；資料可直接饋入快取，也可通過簡單配置同步到多個數據源
- **快取層** — 鍵值快取 + 非同步過濾管線：大量資料先過濾再交給處理者，毋須自行編寫執行緒
- **遠程檔案** — 把檔案存入資料庫、分塊管理、跨機器同步讀寫，也可用作多機熱備份
- **資料庫異步同步** — 兩個資料庫之間增量同步，支援新增 / 更新 / 刪除
- **模板引擎** — 報表、信件、HTML 等文字模板內嵌 `#` 指令，模板自帶邏輯、自動渲染
- **遠端控制其他程序** — 主程序下達指令，另一台機器上的程序輪詢執行並回傳結果
- **日常工具箱** — 設定讀取、XML、加解密、轉義、檔案處理、JS / CSS / HTML 壓縮、HTTP 客戶端
- **時間同步獲取器** — 簡單設定即可統一應用程序與數據庫時間的一致性
- **編號生成器** — 產生定長流水號 / 單據號，可帶前綴後綴，跨機器不重複

## 各關聯項目



| 項目                   | 角色                                                                       |
| -------------------- | ------------------------------------------------------------------------ |
| **JsPlus**（根）        | 隨附工具鏈與腳本：JDK/Ant、H2 啟動（`StartH2.js` / `StopH2.bat`）、統一 bat/sh 入口         |
| **JsPlus-Core**（本項目） | 能力引擎：腳本控制台 + 資料源 / 快取 / 檔案 / 同步 / 工具箱於單一 jar                             |
| **JsPlus-Tomcat**    | 伺服器版：HTTP/TCP 反向代理，同一控制台 API 與能力庫（`TomcatScriptConsole`—— 本 Core 池概念之參照） |

## 套件架構

每個測試套件均為**引擎內執行的 JS 檔案、直接呼叫真實 Java API 並斷言**—— 無 JUnit、無外部框架。建置與全量回歸：



| 步驟     | 指令                                      | 產物 / 結果                                                                                                                                                                        |
| ------ | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 建置     | `ant.bat build`（Linux/macOS：`ant.sh`）   | `LibHome\jsplus\default\jsplus-core-<version>.jar`；版本自動 +1；變更記錄寫入 `changes.log` / `changes_zh-HK.log`；重新產生 API javadoc                                                         |
| 全量測試套件 | `test\TestAll.bat`（`TestAll.sh`）        | **294 通過 / 0 失敗**（24 個 JS 套件 + TestDatasource 池整合；自動啟動 H2 → 初始化 `testdb` → 執行 → 清理）                                                                                            |
| 壓力套件   | `test\_RunOne.bat TestTextScriptStress` | **7 個案例全綠** — C1 順序・C2/C3/C4 並發池化渲染（240/240/800）・C5 每執行緒 `newBuilder()` + callback + `ca.add()`・C6 池耗盡（acquired=99 rejected=20，池完全恢復）・C7 命名策略池 `new TextScript(t, true/false)` |

套件結構：`com.killcoding.jsplus`（控制台引擎核心）・`com.killcoding.datasource`（池化 JDBC）・`com.killcoding.cache`（多層快取）・`com.killcoding.file`（本機 + 資料庫映射檔案）・`com.killcoding.dbsync`（同步管線）・`com.killcoding.tool`（工具箱）・`com.killcoding.log`（日誌）。

## Console API（核心入口）— hello world

`ScriptConsole` 是單一入口；可建立任意多個實例（各自獨立引擎上下文）：



```
import com.killcoding.jsplus.ScriptConsole;

ScriptConsole console = new ScriptConsole();
console.initScriptEngineManager(true);          // 啟動 GraalVM JS 引擎

console.evalScript("print('Hello, world!')");   // → Hello, world!   (JS)
console.evalScript("'Hello from JS: ' + (40 + 2)"); // → Hello from JS: 42

// 物件橋接：於 JS 與 Java 之間傳值
console.getContext().put("greeting", "hello");
console.evalScript("greeting.toUpperCase()");   // → HELLO

// 隔離控制台：獨立上下文、平行執行
ScriptConsole worker = console.newBuilder();
worker.evalScript("var x = 40 + 2; x");

// 執行期動態編譯 Java 源碼
ClassLoader cl = console.evalJavaSource(
    "public class Hello { public static String hi() { return \"hi\"; } }");
```

主要 API：`evalScript(String)` / `eval(File|Runnable)` / `evalJavaSource(String[])` / `newBuilder()` / `getContext()`（`put/get/remove` 橋接）/ `callback(Runnable, sync)` / `getGlobalCacheArray(timer, loopTimer)` / `addSchedule(...)`（下文）/ `executeViaGhost(...)`。

## 排程與工作 — sample



```
// cron 式排程：控制台每分鐘檢查一次。
// 規則："HH:mm"（如 "08:00"）、"E HH:mm"（如 "MON 08:00"）、
//        "mm"（分鐘），或 "*N" = 每 N 分鐘（如 "*5" = 每 5 分鐘）。
console.addSchedule("daily-report", "08:00", () ->
    console.evalScript("print('daily report generated')"));

console.addSchedule("heartbeat", "*5", () ->
    console.evalScript("print('tick')"));

console.getScheduleMap();   // → { name -> { cronRules, runnable } }
console.clearSchedule("daily-report");

// 一次性工作模式
console.setTask("print('one-shot task')");
console.taskExecute();
```

## TextScript — 可腳本化文字模板引擎 — sample

`TextScript` 將含 `#` 指令的文字模板編譯成 JS 函數並於控制台內執行：普通行原樣輸出、`#{expr}` 內聯求值，`#if / #else / #end / #for / #code / #block / #end` 驅動控制流程。



```
#comment greeting template - directive lines are dropped
Hello #{name}, welcome to JsPlus-Core!
#if(release)
Build: STABLE
#else
Build: DEV
#end
```



```
import com.killcoding.jsplus.TextScript;
import java.util.HashMap;
import java.util.Map;

TextScript ts = new TextScript(template);       // 上述模板文字
Map<String,Object> params = new HashMap<String,Object>();
params.put("name", "World");
params.put("release", true);
ts.addParams(params);
String out = ts.parse();                        // 渲染結果
String src = ts.parse(true);                    // 編譯後 JS 函數源碼
```

池與安全策略 — `TextScript` 於執行緒限定控制台池上渲染（每執行緒一個控制台，參照 JsPlus-Tomcat `TomcatScriptConsole`）。於構造函數選擇安全策略：



```
new TextScript(template)            // 默認池（TextScriptConsole）
new TextScript(template, true)      // AllowAllAccessPool  （全權存取）
new TextScript(template, false)     // NotAllowAccessPool  （受限）
```

> 注意：一次 
>
> `parse()`
>
>  會耗盡掃描器 —— 每次渲染使用新的 
>
> `TextScript`
>
>  實例。可執行 demo：
>
> `examples/TextScript-demo.js`
>
> 。

## 環境變數覆寫（與伺服器版同一慣例）



| 變數                             | 預設值                           | 用途                  |
| ------------------------------ | ----------------------------- | ------------------- |
| `JsPlus_JavaDir_JsPlus-Core`   | `default`                     | `Java\` 下的 JDK 目錄名稱 |
| `JsPlus_JvmParams_JsPlus-Core` | `-Xms256m -Xmx1024m -Xss256k` | JVM 參數              |
| `SCRIPT_ENGINE`                | `graalvm.js`                  | 腳本引擎（可選 Nashorn）    |
| `JsPlus_H2_PORT`               | `3306`                        | H2 TCP 埠            |

控制台池外部配置（env 優先、其次 `-D`）：`jsplus.textscript.poolsize` · `jsplus.textscript.engine` · `jsplus.textscript.allowallaccess`。僅應用於默認池；策略池（`true`/`false`）於構造時固定。