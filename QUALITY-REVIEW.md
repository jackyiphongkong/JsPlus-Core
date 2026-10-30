# JsPlus-Core 專案品質評估報告

- 評估日期：2026-10-30（發布前全面檢查更新）
- 評估範圍：`D:\Working\JsPlus-Core`（嵌入引擎）——構建機制、腳本、測試、CI、源碼、文件、版本控制
- 評估方式：靜態審查（結構盤點、源碼衛生掃描、大文件統計、腳本一致性核對）+ 構建/測試實跑（發布檢查兩輪）
- 樣本狀態：src 61 個 java（~29,910 行），`jsplus-core-6.1.204.jar`（LibHome/jsplus/default），version.properties = 6.1.204（機制正常自動遞增中）
- 前版：2026-10-24 評估（6.1.7、70/100）——本版反映 6.1.8→6.1.204 期間批次 A-D 優化、TestDatasource 整合、CI 轉綠與發布檢查成果

---

## 一、總評

**綜合評分：83 / 100** —— 構建、腳本、CI、測試四層已達可發布水準（全量測試 235/0 + 連接池整合 17/0，CI run #49 全綠）；**源碼可維護性仍是主要短板**（`-Xlint:none` 壓制告警、RemoteFileBase 2720 行、Main scanner 依賴異常退出），文件與版本控制衛生良好。

| 維度 | 評分 | 一句話結論 |
| --- | --- | --- |
| 構建與版本機制 | 90 | 版本自動遞增、changelog-diff 判空 bug 已修並實證、PruneJars 歸檔策略生效；僅餘 `-Xlint:none` 死配置與 version.properties 髒狀態 |
| 腳本（bat/sh） | 85 | release 雙平台對稱、TestDatasource/TestAll 的 TESTALL_RUN 共享 H2 守衛 + StopH2.sh 到位；.sh 未在本機 bash 驗證（CI 已跑通） |
| 測試框架與套件 | 92 | 18 個套件 235/0、TestDatasource 真實 H2 整合 17/0、C17 停用 + C14 等待窗口修復、TestAll 失敗退出碼已修（CI 不再漏報） |
| CI 配置 | 88 | TestAll 全量恢復、artifact 上傳、StopH2 取代 Stop 避免誤殺；僅餘 ant 版本未固定 |
| 源碼品質 | 72 | 批次 A-D 大幅優化（CacheArray、Logger printStackTrace 170→78、RemoteFile 拆 Base/Sync、ScriptConsole 3193→1536、Slef→Self、evalJava）；仍剩 -Xlint:none 壓制、上帝類與 Main scanner |
| 文件 | 75 | 根 README 已補、LibHome 路徑已修正、中英 README 對稱；QUALITY-REVIEW 隨版本更新 |
| 版本控制衛生 | 78 | .gitignore 合理、docs/reports 10 個歸檔策略；version.properties 每次 build bump 導致 git 髒仍在 |

---

## 二、問題清單（依嚴重度）

### P0 — 應立即修正（已全部完成並實跑驗證）

| # | 問題 | 狀態 | 證據 |
| --- | --- | --- | --- |
| 1 | README 控制台啟動命令路徑錯誤（LibHome\Core\default\lib） | ✅ 已修 | 已改實際 LibHome 四目錄；根 README 已補 |
| 2 | TestDatasource C5 假陽性 + JDBC 套件零覆蓋 | ✅ 已修 | 重寫為真實 H2 整合測試，17 cases（C1-C16+C18）實跑全過 |
| 3 | 項目根缺 README.md | ✅ 已修 | 根 `README.md` + `README_zh-HK.md` 對稱 |
| 4 | build.xml 殘留 org include 死配置 | ✅ 已修 | package include 已移除 |
| 5 | TestAll 失敗仍 exit 0（CI 漏報） | ✅ 已修 | TestAll.bat/.sh 現按 FAIL 判定非 0 退出；本機 TESTALL_EXIT=0 全綠、CI 全量恢復 |

### P1 — 短中期改善

| # | 問題 | 狀態 | 說明 |
| --- | --- | --- | --- |
| 6 | Xlint 告警被 `-Xlint:none` 完全壓制 | ⚠️ 部分 | build.xml L276 已加 `-Xlint:-unchecked -Xlint:-deprecation`，但 L277 `-Xlint:none` 後者覆蓋，告警仍整體不可見；需移除 L277 |
| 7 | Logger.java 大量複製貼上 | ✅ 大幅改善 | printStackTrace/System.err 由 170 處降至 78 處（批次 B）；Logger 2002 行仍偏大 |
| 8 | Main.java scanner 靠異常退出 | ❌ 未改 | L223 仍 `while ((line = scanner.nextLine()) != null)`；L364 誤導訊息仍在 |
| 9 | 上帝類 | ⚠️ 部分 | RemoteFile 3335 行已拆 RemoteFileBase(2720)+RemoteFileSync、ScriptConsole 3193→1536；RemoteFileBase/Logger/DriverExecutor(1858)/DiskFile(1676) 仍超 1500 行 |
| 10 | 公共 API 拼寫錯誤 putAndReturnSlef | ✅ 已修 | 已改 `putAndReturnSelf`（RemoteFileBase 等 96 處調用點已更新） |
| 11 | CI run #43-#48 TestDatasource 卡死（H2 refused） | ✅ 已修 | 根因定位：`pkill -f 'script/StartH2.js'` 匹配父 sh 誤殺（run #47 證實 h2Down=false）；改 `bin/StopH2.sh`（只匹配 java/javaw 進程）；C17 按用戶決定停用（if false 包裹，保留全部修復基礎設施） |
| 12 | TestCacheArray C14 時序窗口（CI 偶發 size=2） | ✅ 已修 | forceTerminate 後最壞 ~10s+ 才清空；等待窗口 10s→25s（500×50ms），235/0 實證 |

### P2 — 長期 / 重構

| # | 問題 | 影響 |
| --- | --- | --- |
| 13 | version.properties 每次構建遞增 → 本地 git 永久髒；CI 每次 push 也 bump | 需明確策略（gitignore 或 CI 後 checkout） |
| 14 | .sh 腳本未在本機 bash 驗證（Windows 環境限制） | 跨平台一致性僅靠 CI 實證（TestAll.sh/TestDatasource.sh 已在 GitHub Actions 跑通） |
| 15 | RemoteFileBase(2720)/Logger(2002)/DriverExecutor(1858)/DiskFile(1676) 仍為大類 | 違反單一職責，需持續拆分 |
| 16 | build.xml `-Xlint:none` 壓制全部告警 | 型別安全問題不可見，滾雪球 |
| 17 | CI 未固定 ant 版本（apt 安裝） | 版本漂移風險 |

---

## 三、優化建議（可執行）

### 短中期（P1）
1. **build.xml**：刪除 L277 `<compilerarg value="-Xlint:none"/>`，僅保留 `-Xlint:-unchecked -Xlint:-deprecation`——讓 rawtypes/dangling-doc-comments 等其餘告警重新可見，分批清零。
2. **Main.java**：L223 改 `while (scanner.hasNextLine())`，EOF 走正常退出；刪除 L364 誤導的 `"Current scanner exit."`（EOF ≠ scanner 退出）。
3. **RemoteFileBase/Logger 拆分**：RemoteFileBase（2720 行）按連線管理/檔案操作/加密/表分片拆；Logger（2002 行）持續抽私有 log 方法、壓縮重複 try/catch。

### 長期（P2）
4. **CI 固定 ant 版本**：改用官方 binary 或固定版本（避免 apt 版本漂移，尤其中 `-Xlint`/`<length>` 行為差異）。
5. **version.properties 策略**：評估納入 .gitignore 或「CI 構建後 checkout」。
6. **TestDatasource C17**：如後續要恢復 H2 重啟場景，將 `if (false)` 改回 `if (true)` 即可（StopH2.sh 基礎設施已就緒）。

---

## 四、驗證方式與覆蓋範圍

- **已實跑驗證（2026-10-30 發布檢查）**：
  - `ant.bat build` 兩輪成功（6.1.203、6.1.204，BUILD_EXIT=0）
  - `test\TestAll.bat` 兩輪全綠：TestAll.js 主體 **235 passed / 0 failed**（18 個套件，69s）+ TestDatasource **17 passed / 0 failed**（63s，共享 H2 重用）
  - changelog-diff 修復實證：6.1.204 條目乾淨（無 `${jr.diff.changes}` 字面量，6.1.196-6.1.203 曾受影響）
  - CI run #49（40906105729）：TestDatasource 隔離全綠（C1-C16+C18 exit 0）
  - `node --check` 全部 JS 修改語法通過
- **靜態審查**：源碼衛生掃描、大文件統計、批次 A-D 優化成果核對、腳本一致性核對
- **未覆蓋**：`.sh` 腳本本機 bash 執行（本機無 bash，靠 CI）；GitHub Actions 最新全量 run（run #54 結果待推送後確認）；Logger 非同步 flush 壓力行為；RemoteFileBase/DriverExecutor 等大類行級審查（僅統計層面）
