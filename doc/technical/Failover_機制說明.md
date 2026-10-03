# 排程災後重跑 (Failover Recovery) 機制說明

在分散式系統或微服務架構中，應用程式伺服器隨時可能因為硬體故障、OOM (Out of Memory)、或進行滾動更新 (Rolling Update) 而突然中斷。若此時正有耗時的批次排程在執行中，突然的中斷將導致任務執行一半而產生資料不一致。

為了應對此情況，`schedules-management-module` 結合了 **Quartz 的叢集機制 (Clustering)** 與本模組專屬的 **中斷點續傳設計 (`JobExecutionCheckpoint`)**，實現了強大的 Failover 機制。

---

## 1. 核心概念與運作原理

當一個節點 (Node) 突然崩潰時，Failover 機制的運作流程如下：

1. **心跳偵測 (Heartbeat Timeout)**：
   Quartz 透過資料庫底層的表 (如 `QRTZ_FIRED_TRIGGERS`, `QRTZ_SCHEDULER_STATE`) 進行心跳交換。當其他存活的節點發現某個節點長時間未更新心跳，即判定該節點已死 (Orphaned)。
2. **任務轉移 (Job Recovery)**：
   存活的節點會接手原本在死亡節點上執行、且標記為 `requestsRecovery = true` 的任務。
3. **重啟執行 (Re-fire)**：
   存活節點會重新觸發該任務，並在 `JobExecutionContext` 中標記 `isRecovering() == true`。
4. **中斷點續傳 (Checkpoint Resumption)**：
   由本模組開發的 `AbstractRecoverableBatchJob` 在偵測到 `isRecovering() == true` 時，會自動向資料庫查詢該任務的 `JobExecutionCheckpoint`，跳過已完成的步數，直接從失敗的斷點繼續執行。

---

## 2. 如何啟用 Failover 機制？

要在系統中啟用排程的災後重跑功能，您只需要在註冊任務時將 `requestsRecovery` 屬性設定為 `true` 即可。

### 透過 API 或 Service 註冊
```java
// 建立 Cron 排程時，最後一個 boolean 參數代表 requestsRecovery
ScheduleRule rule = ScheduleRule.cron("0 0 12 * * ?", "TAIWAN_HOLIDAY", true);
```
### 透過 Command 註冊
```java
// 在 Command 中設定 isRecovery = true
CreateCronJobCommand command = new CreateCronJobCommand(
    "SyncDataJob", 
    "SyncGroup", 
    "syncDataJobBean", 
    "0 0 12 * * ?", 
    null, 
    true // <-- 啟用災後重跑
);
```

---

## 3. 框架底層實作：中斷點的守護者

本模組透過兩個核心類別來支撐中斷點續傳的能力：

### `JobExecutionCheckpoint` (領域聚合根)
專門負責記錄排程任務執行中的「進度」與「中斷點」。它獨立於普通的歷史日誌 (`ScheduleJobLog`) 之外。
* 紀錄了 `totalSteps` (總步數) 與 `currentStep` (當前執行到第幾步)。
* 紀錄了 `lastCheckpoint` (業務標記，例如最後處理的 ID 或是分頁碼)。

### `AbstractRecoverableBatchJob` (業務基底類別)
這是一個 Template Method Pattern 的實作。當您繼承此類別來開發批次任務時，底層會自動做到：
1. **全新執行**：呼叫您的 `calculateTotalSteps` 計算總步數，並在資料庫建立新的 Checkpoint (`status = RUNNING`)。
2. **執行中**：每次呼叫您的 `processStep` 後，自動更新 Checkpoint 的 `currentStep` 與 `lastCheckpoint`。
3. **災後重跑**：當節點崩潰被 Quartz 重新拉起時，它不會呼叫 `calculateTotalSteps`，而是直接從 DB 讀取 Checkpoint，將迴圈的啟始點設定為前次的 `currentStep`，達成完美的續傳。

---

## 4. 開發者的注意事項 (Best Practices)

雖然框架幫您處理了大部分的中斷點續傳邏輯，但為了確保資料的正確性，您的 `processStep` 方法設計必須符合以下原則：

### 必須具備「冪等性」 (Idempotency)
如果伺服器在 `processStep` 執行到一半，尚未向外回傳 Checkpoint 時崩潰，該步驟的資料庫 Transaction 會被 Rollback，但如果您的步驟中有包含**呼叫外部 API (如發送 Email)** 或**不可逆的操作**，重跑時該步驟會被再次執行。
* **解法**：確保外部操作具備防呆機制（例如：打外部 API 前先確認對方系統是否已有紀錄），或利用資料庫狀態攔截重複處理。

### 步驟大小 (Step Size) 的拿捏
* **太大**：若單一步驟處理 10 萬筆資料，耗時 10 分鐘，中斷時的重跑成本極高，且 Transaction 過大容易造成 DB 鎖定問題。
* **太小**：若單一步驟只處理 1 筆資料，頻繁更新 Checkpoint 會對資料庫造成 I/O 負擔。
* **建議**：依據業務複雜度，將 `pageSize` 設定在 500 ~ 2000 之間，使得單一步驟耗時保持在數秒到數十秒之間為佳。

---

## 5. 程式碼運作機制 (Execution Flow)

為了更清晰地展示底層架構是如何處理續傳的，以下節錄並解析 `AbstractRecoverableBatchJob` 的核心執行迴圈：

```java
public abstract class AbstractRecoverableBatchJob extends QuartzJobBean {
    
    @Override
    protected final void executeInternal(JobExecutionContext context) throws JobExecutionException {
        boolean isRecovering = context.isRecovering();
        
        if (isRecovering && optionalCheckpoint.isPresent()) {
            // === 1. 災後重跑 (Recovery Mode) ===
            // 讀取前次當掉時留下的 Checkpoint
            currentStep = checkpoint.getCurrentStep();
            totalSteps = checkpoint.getTotalSteps();
            lastCheckpoint = checkpoint.getLastCheckpoint();
        } else {
            // === 2. 全新執行 (New Execution) ===
            // 呼叫子類別計算總步數，並寫入初始 Checkpoint
            totalSteps = calculateTotalSteps(context);
            checkpoint = JobExecutionCheckpoint.initialize(jobId, totalSteps, isRecovering);
            checkpointRepository.saveAndFlush(checkpoint);
        }

        // === 3. 批次迴圈執行 ===
        // 迴圈的起點是 currentStep + 1，巧妙地略過了已處理的步驟
        for (int step = currentStep + 1; step <= totalSteps; step++) {
            
            // 委派給子類別執行單步業務，並回傳標記 (如最後處理的 ID)
            lastCheckpoint = processStep(step, lastCheckpoint, context);
            
            // 🔥 關鍵：立即將進度寫入 DB (使用 saveAndFlush)
            // 這樣一旦此時發生 Crash，接手的 Node 才能精確讀取進度
            checkpoint.updateProgress(lastCheckpoint, step);
            checkpointRepository.saveAndFlush(checkpoint);
        }

        // === 4. 執行完畢 ===
        checkpoint.complete();
        checkpointRepository.saveAndFlush(checkpoint);
    }
}
```

### 機制拆解：
1. **識別恢復狀態**：透過 Quartz 傳入的 `context.isRecovering()` 判斷本次執行是否為伺服器故障後的重啟。
2. **斷點載入 vs 全新初始化**：若是重啟，直接沿用舊的 `totalSteps` 與 `currentStep`；若是全新執行，則調用 `calculateTotalSteps()` 取得總次數。
4. **進度防護 (Save And Flush)**：每次迴圈結束後，必須強制呼叫 `saveAndFlush`，確保中斷點的更新立即刷入資料庫，防止 Transaction 延遲提交導致中斷點遺失。

---

## 6. 觸發接管的先決條件 (Prerequisites)

您可能會問：「僅靠上述的 Java 程式碼，就可以自動觸發其他 Node 接管嗎？」

**答案是：不行。**

`AbstractRecoverableBatchJob` 的程式碼只負責處理**「如何從中斷點接續執行」**（應用層邏輯）。要讓 Quartz 引擎知道**「何時該派發任務給其他 Node」**，必須依賴以下兩個先決條件的配合，缺一不可：

1. **Quartz 叢集模式 (Clustering) 必須開啟**：
   在 `application.properties` 中，必須配置 Quartz 使用資料庫作為儲存 (JobStore)，並明確開啟叢集功能與心跳偵測。
   ```properties
   spring.quartz.job-store-type=jdbc
   spring.quartz.properties.org.quartz.jobStore.isClustered=true
   spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval=20000
   ```
   *原理*：每個 Node 會每隔 `clusterCheckinInterval` (例如 20 秒) 去更新一次資料庫中的心跳時間。如果某個 Node 遲遲未更新，其他 Node 才會判定它已死亡 (Orphaned)，進而準備接管它的任務。

2. **建立任務時，必須標記 `requestsRecovery = true`**：
   如果排程在註冊時沒有開啟此標籤，當節點死亡時，Quartz 會認為這個任務「不重要」或「不需要接續」，就會直接丟棄這次未完成的執行 (Misfire 處理)。
   只有當 `requestsRecovery` 為 `true` 時，存活的 Node 才會將該任務重新排入執行佇列，並帶入 `isRecovering = true` 的 Flag。
