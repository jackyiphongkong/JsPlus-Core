# JsPlus-Core 專案品質評估報告

- 評估日期：2026-10-24
- 評估範圍：`D:\Working\JsPlus-Core`（嵌入引擎）——構建機制、腳本、測試、CI、源碼、文件、版本控制
- 評估方式：靜態審查（結構盤點、Xlint 嚴格編譯、源碼衛生掃描、腳本一致性核對）+ 構建/測試實跑
- 樣本狀態：src 48 個 java（~22,000 行），`jsplus-core-6.1.7.jar`（LibHome/jsplus/default），version.properties = 6.1.7（機制正常自動遞增中）

---

## 一、總評

**綜合評分：70 / 100** —— 構建、腳本、CI 三層已達可用且與 JsPlus-Tomcat 對齊；**源碼可維護性與測試覆蓋是真實短板**（上帝類、告警被壓制、JDBC 套件零覆蓋），文件有兩處錯誤路徑。

| 維度 | 評分 | 一句話結論 |
| --- | --- | --- |
| 構建與版本機制 | 85 | 與 Tomcat 標準逐項對齊，版本自動遞增實跑驗證；僅餘 org include 死配置與 version.properties 髒狀態 |
| 腳本（bat/sh） | 80 | 自帶工具鏈優先、刪除白名單守衛、參數轉發齊全；.sh 未在真實 bash 驗證 |
| 測試框架與套件 | 76 | 框架結構好、斷言真實；TestDatasource 已改真實 H2 整合測試（C5-C7，7/7），總基線 65 passed / 0 failed；尚存 CI 漏報失敗問題（見 P1 新發現） |
| CI 配置 | 75 | 流程完整可跑；版本 bump 副作用與 ant 版本未固定 |
| 源碼品質 | 55 | 314 處衛生問題、100 個 Xlint 告警被壓制、多個上帝類、公共 API 拼寫錯誤 |
| 文件 | 55 | 缺根 README；兩處 LibHome 路徑錯誤（引用不存在的 `LibHome/Core/default/lib`） |
| 版本控制衛生 | 70 | .gitignore 合理；version.properties 與 test_reports 未納入管理策略 |

---

## 二、問題清單（依嚴重度）

### P0 — 應立即修正（低成本、高價值）

| # | 問題 | 證據 | 影響 |
| --- | --- | --- | --- |
| 1 | **README 控制台啟動命令路徑錯誤** | `JsPlus\README.md` 寫 `LibHome\Core\default\lib\*`，實際 LibHome 結構為 `graalvm\default`、`h2\default`、`nashorn\default`、`jsplus\default`，**無 Core 目錄** | 照文件操作必然失敗 |
| 2 | **TestDatasource scope note 理由錯誤 + 假陽性** | `test\TestDatasource.js` C5 恆過（`ok: true`），宣稱 "none is bundled in LibHome/Core/default/lib"；實測 `LibHome\h2\default\h2-2.4.240.jar` 含 `org/h2/Driver.class` | 6 個 JDBC 類（DriverDataSource/DriverConnection/ConnectionPool/DriverExecutor/CacheDriverExecutor/IdentityCard）完全未測，且「未測」被計為 PASS |
| 3 | **項目根缺 README.md** | 根目錄僅有 `JsPlus\README.md` | 新接手者無入口文件 |
| 4 | **build.xml 殘留 org include 死配置** | `package` 目標 `include name="org/**/**/*"`；src 已無 org（已拆分） | 死配置誤導後續維護者 |

> **P0 執行狀態（2026-10-24 已全部完成並實跑驗證）**：① 啟動命令已改實際 LibHome 四目錄；② 根 `README.md` 已補；③ `TestDatasource.js` 重寫為真實 H2 記憶體庫整合測試——C5 連線、C6 DDL/DML/查詢、C7 `ConnectionPool` 借出/歸還生命週期（借出移出空閒池、`close()` 歸還，鍵為原始連線哈希），套件 7/7 全過；④ build.xml org include 已移除。最新基線：`ant build` 成功、`TestAll.bat` **65 passed / 0 failed**、jar = `jsplus-core-6.1.8.jar`。

### P1 — 短中期改善

| # | 問題 | 證據 | 影響 |
| --- | --- | --- | --- |
| 5 | **Xlint 告警被 `-Xlint:none` 完全壓制** | 嚴格編譯（`-Xlint:all`）實測 100 個告警：rawtypes 47、unchecked 19、dangling-doc-comments 17、serial 8、deprecation 4、cast/static 各 2、this-escape 1；build.xml 卻用 `-Xlint:none` 遮蔽 | 型別安全問題不可見，滾雪球 |
| 6 | **Logger.java 大量複製貼上** | `Logger.java` 1588 行、**170 處** `printStackTrace`/`System.err`；15+ 個方法重複「try/catch → printStackTrace → System.err.println」模式；另有拼寫錯誤 `"Worng msg format: "` | 維護成本高、異常處理不一致 |
| 7 | **Main.java scanner 靠異常退出** | `Main.java:189` `while ((line = scanner.nextLine()) != null)`——EOF 時 `nextLine()` 拋 `NoSuchElementException`；`:329` `catch(Exception e){ System.err.println("Current scanner exit."); }` 資訊誤導（EOF ≠ scanner 退出） | 退出機制脆弱、診斷資訊錯誤 |
| 8 | **上帝類** | `RemoteFile.java` 3335 行、`ScriptConsole.java` 3193 行、`DriverExecutor.java` 1786 行、`Logger.java` 1588 行、`DriverDataSource.java` 1512 行 | 違反單一職責，難以測試與並行開發 |
| 9 | **公共 API 拼寫錯誤** | `ConfigProperties.java:220` `public ConfigProperties putAndReturnSlef(String key, Object value)`（Self→Slef），全項目 96 處調用 | 外部腳本亦受影響；改名破壞相容，需提供別名 |

> **P1 新增發現（2026-10-24，P0 驗證期間實測）**：`test/TestAll.js` 無論成功或失敗都執行 `System.exit(0)`——實測 1 failed 時 `TestAll.bat` 退出碼仍為 0，`TestAll.sh` 與 CI 工作流因此**漏報測試失敗**（CI 全綠但測試紅）。建議 `TestAll.bat/.sh` 在「存在 FAIL」時以非 0 退出碼結束（例如 `exit 1`），或在 CI 步驟加失敗判定。

### P2 — 長期 / 重構

| # | 問題 | 影響 |
| --- | --- | --- |
| 10 | version.properties 每次構建遞增 → 本地 `git status` 永久髒；CI 每次 push 也 bump | 需明確策略（gitignore 或 CI 後 checkout） |
| 11 | test_reports/ 未納入 .gitignore（測試輸出進版本庫目錄） | 產物污染提交 |
| 12 | TestDatasource 可改用 `jdbc:h2:mem:` 真實整合測試（h2 驅動已在 LibHome） | 將 P0#2 的盲區變成覆蓋 |
| 13 | .sh 腳本未在真實 bash 驗證（Windows 環境限制） | 跨平台一致性存疑 |

---

## 三、優化建議（可執行）

### 立刻（P0）
1. **修 `JsPlus\README.md`**：控制台啟動命令改為實際 LibHome 目錄：
   ```bat
   java -cp "LibHome\graalvm\default\*;LibHome\nashorn\default\*;LibHome\h2\default\*;LibHome\jsplus\default\jsplus-core-*.jar" com.killcoding.jsplus.Main -Env script -ScriptEngine graalvm.js
   ```
2. **補根 README.md**：一頁說明（專案定位、前置工具、`ant build`/`test\TestAll.bat`/`packJdk.bat` 三步、目錄結構表）。
3. **改 TestDatasource.js**：C5 換成真實 H2 記憶體庫測試——`DriverDataSource` 指向 `jdbc:h2:mem:coretest;DB_CLOSE_DELAY=-1`，驗證連線、`DriverConnection` CRUD、`ConnectionPool` 取還；刪除錯誤的 scope note。
4. **build.xml**：`package` include 移除 `org/**/**/*`（已拆分獨立項目）。

### 短中期（P1）
5. **告警治理**：build.xml 改 `-Xlint:-unchecked -Xlint:-deprecation`（保留其餘），分批清零：先修 serial（8 處補 `serialVersionUID`）與 dangling-doc-comments（17 處檔頭 `/** */` 改 `/* */`——MIT 授權註解不是 javadoc），再清 rawtypes（47 處）。
6. **Logger.java 重構**：抽私有 `log(Level, String, Throwable)`，消滅 15+ 重複 try/catch 塊；修正 `"Worng"` → `"Wrong"`。
7. **Main.java**：scanner 改用 `hasNextLine()` 判斷（`while (scanner.hasNextLine())`），EOF 走正常退出；刪除誤導的 `"Current scanner exit."`；局部變數 `havaTask`/`havaAllowAllAccess` 改名 `hasTask`/`hasAllowAllAccess`。
8. **.gitignore** 增補：`/test_reports`（測試產物）；評估 version.properties 納入忽略或採用「CI 構建後 `git checkout -- version.properties`」策略。

### 長期（P2）
9. 拆 `ScriptConsole.java`/`RemoteFile.java`（各 3000+ 行）為多職責類（Console 環境初始化 / 執行 / 渲染；RemoteFile 的連線管理 / 檔案操作 / 加密 / 表分片）。
10. `ConfigProperties` 提供 `putAndReturnSelf` 正確拼寫方法並 `@Deprecated` 舊名（相容遷移）。
11. TestAll 增加 `-Task` 完成後的顯式關閉通道，降低對 stdin EOF 的依賴。
12. CI 固定 ant 版本（如 `apache/ant` 官方 binary 或 1.10.x 快照），避免 apt 版本漂移。

---

## 四、驗證方式與覆蓋範圍

- **已實跑驗證**：`ant.bat build`（BUILD SUCCESSFUL）→ `test\TestAll.bat`（63 passed / 0 failed，exit 0）→ `packJdk.bat`（jdeps 7 模組、jlink runtime 重建）——全程 Core 自帶 GraalVM 25.0.3 + Ant 1.10.17，無 JAVA_HOME。
- **靜態審查**：Xlint:all 嚴格編譯（100 告警統計）、源碼衛生掃描（314 處）、大文件統計、測試套件逐個抽讀、CI/腳本一致性核對。
- **未覆蓋**：`.sh` 腳本真實 bash 執行（本機無 bash）；GitHub Actions 實際跑通（需 push 驗證）；Logger 非同步 flush 的壓力行為；RemoteFile/DriverExecutor 等大類的行級審查（僅統計層面）。
