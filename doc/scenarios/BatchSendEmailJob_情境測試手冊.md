# 批次寄信任務 (BatchSendEmailJob) 情境測試手冊

本手冊用於演示如何測試基於 `AbstractRecoverableBatchJob` 框架所開發的「批次寄信任務」，並實際驗證其分頁處理與災後重跑 (Failover) 的能力。

## 1. 測試情境說明

我們在系統中實作了一支模擬排程 `batchSendEmailJob`。
- **總處理人數**：10 人 (`user01@example.com` ~ `user10@example.com`)
- **每批次處理 (Batch Size)**：3 人
- **預計總步數 (分頁數)**：4 步 (前 3 步各寄 3 封，第 4 步寄 1 封)
- **模擬耗時**：每寄送一封信，系統會刻意停頓 (Sleep) 1.5 秒，以提供充裕的時間讓您手動中斷伺服器來模擬當機。

---

## 2. 準備工作 (開啟雙節點)

為了避免 Spring Boot 開發環境的 `ddl-auto=create` 導致重啟時資料庫被清空，請在 IntelliJ 中同時啟動兩個節點 (Node)：

1. 打開 IntelliJ 的 **Run/Debug Configurations**。
2. 勾選 **"Allow multiple instances"**。
3. 啟動 **Node 1** (預設 Port 8080)。
4. 修改啟動參數或 `application.properties` (例如加上 `-Dserver.port=8081`)，接著啟動 **Node 2**。

此時您已經擁有了一個小型的 Quartz 叢集。

---

## 3. 測試步驟

### 步驟 1：觸發寄信排程
請透過 Postman 或 API 測試工具，發送以下 Request 給 **Node 1 (Port 8080)**：

`POST /api/v1/schedule/create-one-time`
```json
{
  "name": "EmailJob",
  "group": "TestGroup",
  "jobType": "batchSendEmailJob",
  "executeDate": "2026/10/01",
  "executeTime": "23:59",
  "requestsRecovery": true
}
```
> *註：時間請視情況調整為當下時間之後的幾秒鐘。*

### 步驟 2：觀察第一階段執行
切換至 **Node 1 的 Console**，您將會看到任務被觸發，並開始寄信：

```text
【Batch 框架】🚀 全新任務 TestGroup.EmailJob 開始執行
【Batch 框架】⏳ 任務 TestGroup.EmailJob 執行中... (第 1/4 步)
📧 開始寄送第 1 批次信件，共 3 封...
[寄信作業] To: user01@example.com   | Subject: 【系統通知】公版測試信件 | Content: ...
[寄信作業] To: user02@example.com   | Subject: 【系統通知】公版測試信件 | Content: ...
[寄信作業] To: user03@example.com   | Subject: 【系統通知】公版測試信件 | Content: ...
✅ 第 1 批次寄送完成，最後處理的 User: user03@example.com
【Batch 框架】⏳ 任務 TestGroup.EmailJob 執行中... (第 2/4 步)
📧 開始寄送第 2 批次信件，共 3 封...
[寄信作業] To: user04@example.com   | Subject: 【系統通知】公版測試信件 | Content: ...
...
```

### 步驟 3：模擬災難 (Kill Node 1)
當您看到 Console 印出正在處理 **第 2 步 (第 2 批次)** 或 **第 3 步** 的中途：
👉 **請立刻點擊 IntelliJ 的紅色 Stop 按鈕，強制中斷 Node 1！**

此時寄信作業會瞬間停止，任務尚未完成。

### 步驟 4：見證 Node 2 無縫接手
將視窗切換至 **Node 2 (Port 8081) 的 Console**。
等待約 15 秒 (Quartz ClusterManager 的掃描週期) 後，您會看到 Node 2 自動接手了這個爛攤子，而且**完美避開了已經寄完的第 1 批次**，直接從剛才中斷的步驟接續寄送：

```text
【Batch 框架】⚠️ 偵測到災後重跑 (Recovery Mode)！任務 TestGroup.EmailJob 由中斷點 (第 1/4 步) 接續執行
【Batch 框架】⏳ 任務 TestGroup.EmailJob 執行中... (第 2/4 步)
📧 開始寄送第 2 批次信件，共 3 封...
[寄信作業] To: user04@example.com   | Subject: 【系統通知】公版測試信件 | Content: ...
[寄信作業] To: user05@example.com   | Subject: 【系統通知】公版測試信件 | Content: ...
[寄信作業] To: user06@example.com   | Subject: 【系統通知】公版測試信件 | Content: ...
✅ 第 2 批次寄送完成，最後處理的 User: user06@example.com
...
【Batch 框架】✅ 任務 TestGroup.EmailJob 執行完畢！
```

---

## 4. 預期結果與效益評估

透過這個情境測試，我們證明了：
1. **分頁邏輯生效**：10 筆資料被完美切割為每批次 3 筆，並分 4 步執行。
2. **斷點續傳生效**：在第 2 步中斷時，`user01` ~ `user03` 不會因為當機而重複收到信件 (不會從頭重跑)。
3. **框架簡化開發**：開發者 (即 `BatchSendEmailJob` 的作者) 完全不需要處理 Transaction、Cluster 管理或讀寫 Checkpoint 的邏輯，只需專心寫「寄信迴圈」即可。
