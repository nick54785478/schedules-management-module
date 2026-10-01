# Quartz Failover 叢集災難復原測試操作手冊

本手冊旨在引導開發者與測試人員，如何實際驗證我們設計的 `JobExecutionCheckpoint` 與 Quartz `requestsRecovery` 結合後的容錯備援 (Failover) 機制。

透過以下步驟，您將能親眼見證：**當執行排程的 Node (JVM) 突然崩潰 (Crash) 時，Quartz 如何自動由另一個 Node 接手，並且「準確地從中斷點繼續執行」，而不是從頭重跑。**

---

## 準備工作

1. **確認資料庫已連線且叢集已啟動**：確保您的 Quartz 設定中，已正確指向共用的關聯式資料庫 (如 MySQL / PostgreSQL)，且 `org.quartz.jobStore.isClustered` 設定為 `true`。
2. **準備模擬環境 (開啟 IntelliJ 多實例執行)**：由於開發階段可能開啟了 DDL 自動建表 (`spring.jpa.hibernate.ddl-auto=create`)，若採「重啟」方式，資料庫會被清空。因此，我們需要在 IntelliJ 同時開啟兩個 Node (Pod)：
   - 請在 IntelliJ 點擊上方的 Run/Debug Configurations。
   - 找到您的 Spring Boot 啟動設定，勾選 **"Allow multiple instances"** (或在 Modify options 內勾選)。
   - 啟動第一個 Node (例如預設 Port 8080)。
   - 在 `application.properties` (或啟動參數) 更改 Port 號 (例如 `server.port=8081`) 後，啟動第二個 Node。
   - 現在，您同時有兩個 Node 連接到同一個資料庫，形成了一個小型的 Quartz Cluster！

---

## 測試流程

### 步驟 1：註冊並觸發長時測試任務

透過 API 工具 (如 Postman 或 Swagger)，發送請求建立並觸發一次性的測試排程，請指定我們專為此設計的 `failoverSimulationJob`。

**POST API (建立一次性排程)**：
`POST /api/v1/schedule/create-one-time`

**Request Body 範例**：
```json
{
  "name": "TestRecoveryJob",
  "group": "TestGroup",
  "jobType": "failoverSimulationJob",
  "executeDate": "2026/10/1",
  "executeTime": "23:31",
  "requestsRecovery": true
}
```
> **注意**：如果為了馬上測試，建議您可以先手動寫死觸發時間為發出 API 後的幾秒鐘。`requestsRecovery` 必須為 `true`。

---

### 步驟 2：觀察任務正常啟動

當排程被觸發後，請緊盯 IDE 或終端機的 Console 日誌。
您應該會看到 `FailoverSimulationJob` 開始運作，印出如下資訊：

```text
【Failover 測試】🚀 全新執行！任務 TestGroup.TestRecoveryJob 開始
【Failover 測試】⏳ 任務 TestGroup.TestRecoveryJob 執行中... (第 1/10 步)
【Failover 測試】⏳ 任務 TestGroup.TestRecoveryJob 執行中... (第 2/10 步)
【Failover 測試】⏳ 任務 TestGroup.TestRecoveryJob 執行中... (第 3/10 步)
...
```
*(每個步驟會模擬耗時等待 5 秒)*

---

### 步驟 3：模擬災難發生 (拔插頭)

當您看到任務印到一半時 (例如第 3 步或第 4 步)，**不要讓他跑完！**

請「**強力終止 (Kill)**」正在執行任務的那個 Spring Boot Node (例如 Node 1)。
* **在 IntelliJ IDEA**：在下方的 Run 或 Services 視窗，選取正在印出日誌的 Node 1，點擊紅色的正方形 Stop 按鈕強制結束。

這個動作是在模擬 Node 1 伺服器瞬間斷電或當機，導致任務意外中斷，無法回報完成。

---

### 步驟 4：檢視資料庫 (選作)

在重啟前，您可以到資料庫觀察一下 `job_execution_checkpoint` 表格，您可以看見該筆任務紀錄的狀態：
* `job_id`: TestGroup.TestRecoveryJob
* `status`: RUNNING
* `current_step`: 3 (停留在您剛剛強制結束的步驟)

這證明了我們的進度有確實在每步被獨立 Transaction 寫入資料庫，不會因為 JVM 崩潰而遺失。

---

### 步驟 5：觀察 Node 2 接手 (模擬新 Node 發現斷線)

因為 Node 2 **原本就已經在運行中**，它會避開 DDL 重建資料庫的問題。
Quartz 引擎的叢集管理機制 (ClusterManager) 每隔一段時間 (通常是 15-20 秒) 就會掃描資料庫。不久後，Node 2 就會發現剛才的 Node 1 已經斷線，且遺留了一個標記有 `requestsRecovery=true` 且尚未執行完畢的任務。

---

### 步驟 6：見證奇蹟的時刻 (Recovery 接手)

請將目光切換到 **Node 2 (Port 8081)** 的 Console，您將在短時間內自動看到 Node 2 將該任務接手過去，並且印出如下日誌：

```text
【Failover 測試】⚠️ 偵測到災後重跑 (Recovery Mode)！任務 TestGroup.TestRecoveryJob 由中斷點 (第 3 步) 接續執行
【Failover 測試】⏳ 任務 TestGroup.TestRecoveryJob 執行中... (第 4/10 步)
【Failover 測試】⏳ 任務 TestGroup.TestRecoveryJob 執行中... (第 5/10 步)
...
【Failover 測試】✅ 任務 TestGroup.TestRecoveryJob 執行完畢！
```

恭喜！這代表 Quartz 成功觸發了 `Recovery`，且我們的 `JobExecutionCheckpoint` 也成功引導程式避開了已經做完的前 3 步，準確地由第 4 步繼續執行直到任務全部完成。

---

## 結論與開發建議

透過這個機制，未來在撰寫真實的長時任務（如：發送百萬封行銷信件、月底產生財務報表、同步大量使用者資料庫）時：

1. **務必將參數 `requestsRecovery` 設為 `true`**。
2. **在 `QuartzJobBean` 中注入 `JobExecutionCheckpointRepository`**。
3. **在耗時迴圈中，每一批次處理完畢後，即時呼叫 `updateProgress` 與 `saveAndFlush`**。
4. **絕對避免在 `executeInternal` 上掛載整包的 `@Transactional`**，以防 Transaction 在 JVM 當機時回滾，導致丟失所有的中斷點進度。對於資料庫的更新，應採用每步/每批次開啟新的小 Transaction 來提交。
