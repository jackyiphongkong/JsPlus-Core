# JsPlus-Core

**標準的 GraalVM JS Java API 調用引擎。** 於任何 Java 應用內嵌 GraalVM JavaScript 執行環境，輕量級依賴引用讓 JS 直接呼叫真實 Java API：開發人員快捷開發、即時驗證邏輯；運維人員腳本自動化、定時任務。內置 Script / JDBC / Cmd 三種 Console，適合各類技術人員。引用池的優勢讓 GraalVM JS 支持多線程調用。

## 主要功能

| 功能 | 說明 | 對應類 |
| --- | --- | --- |
| **快速調試** | 以 JavaScript 調用 Java 原生類庫，啟動控制台即可調試程式 | `ScriptConsole` |
| **高效數據源** | 自動管理連線、壞連線自癒；資料可直接饋入快取，也可通過簡單配置同步到多個數據源 | `DriverDataSource` |
| **快取層** | 鍵值快取 + 非同步過濾管線：大量資料先過濾再交給處理者，毋須自行編寫執行緒 | `Cache` / `CacheArray` |
| **遠程檔案** | 把檔案存入資料庫、分塊管理、跨機器同步讀寫，也可用作多機熱備份 | `RemoteFile` / `RemoteFileSync` |
| **資料庫異步同步** | 兩個資料庫之間增量同步，支援新增 / 更新 / 刪除 | `dbsync` |
| **模板引擎** | 報表、信件、HTML 等文字模板內嵌 `#` 指令，模板自帶邏輯、自動渲染 | `TextScript` |
| **遠端控制其他程序** | 主程序下達指令，另一台機器上的程序輪詢執行並回傳結果 | `GhostConsole` |
| **日常工具箱** | 設定讀取、XML、加解密、轉義、檔案處理、JS / CSS / HTML 壓縮、HTTP 客戶端 | `CommonTools` 等 |
| **時間同步獲取器** | 簡單設定即可統一應用程序與數據庫時間的一致性 | `Clock` |
| **編號生成器** | 產生定長流水號 / 單據號，可帶前綴後綴，跨機器不重複 | `IdentityCard` |

## 快速啟動

| 步驟 | 指令 | 作用 |
| --- | --- | --- |
| 1 | `JsPlus\bin\StopAll.bat` | 停止所有服務 |
| 2 | `JsPlus\bin\StartH2Console.bat init testdb` | 初始化資料庫（`fallback.sql` → `change.sql`） |
| 3 | `JsPlus\bin\BG_StartH2.bat` | 後台啟動 H2 資料庫（隱藏窗口） |
| 4 | `JsPlus\bin\StartCore.bat` | 啟動 ScriptConsole 互動控制台 |

於控制台提示符直接輸入 JavaScript，以 `/` 結束指令；
```
js> print('Hello World.');/
Hello World.
```

輸入 `jdbc/` 切換至 JdbcConsole，以 `;` 結束 SQL：
```
js> jdbc/                 // 切換至 JdbcConsole
jdbc> select * from test_user;   // 執行 SQL（以 ; 結束）

0 rows selected (10ms)
Current QueryMaxRows=1000
```

插入測試數據
```
jdbc> insert into test_user (id, username, email, age, status, created_at, updated_at, deleted) values ('u000000000000001', 'Alice', 'alice@example.com', 30, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false);

1 row executed (8ms)

jdbc> select id,username,email from test_user;
 id        username           email
u000000000000001             Alice      alice@example.com

1 rows selected (523ms)
Current QueryMaxRows=1000
```

輸入 `script;` 切換至 ScriptConsole，以 `/` 結束 SQL：
```
jdbc>script;
js> var TextScript = Java.type('com.killcoding.jsplus.TextScript');/
var emailBodyTpl = `
To: #{email}\n

Dear #{username},\n

Wellcom to use Jsplus.\n

Thanks\n

Regards
Jack
`;/

var ts = new TextScript(emailBodyTpl,false);/
ts.addParams({email:'test@gmail.com',username: 'Alice'});
var emailBody = ts.parse();/
```