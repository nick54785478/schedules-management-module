# 架構更新評估書：Quartz Failover Recovery 機制引入

## 1. 背景與目的
根據 Quartz 叢集的 Failover 特性，當叢集中某個節點異常崩潰（如 OOM、斷電）時，正在執行的任務會被中斷。為了確保關鍵任務不會因此遺漏，必須啟用 Quartz 的 **Recovery 機制**（`RequestsRecovery`）。

本文件旨在盤點引入 Recovery 機制時，系統架構需要進行的更新範圍與潛在影響。

---

## 2. 影響範圍評估

此次架構升級主要橫跨三個層級：**基礎設施層 (Infrastructure)**、**領域層 (Domain)** 以及 **介面層 (Interfaces)**。

### 2.1 基礎設施層 (Infrastructure Layer)
**影響檔案：**
- `JobSchedulerAdapter.java`

**變更內容：**
1. **啟用 Job Recovery**：
   在建立 Quartz 的 `JobDetail` 時，需要將 `requestRecovery` 屬性開啟（根據指令決定）。
   ```java
   private JobDetail createJobDetail(RegisterJobCommand cmd, Class<? extends Job> jobClass) {
       return JobBuilder.newJob(jobClass)
               .withIdentity(cmd.name(), cmd.group())
               .requestRecovery(cmd.isRecoveryEnabled()) // 新增此行
               .build();
   }
   ```
2. **傳遞 Recovery 狀態至監聽器**：
   在 `wrapListener` 實作中，當任務被觸發或執行完畢時，需要從 Quartz 的 `JobExecutionContext` 取出 `isRecovering()` 狀態，並傳遞給我們的領域層 Listener。
   ```java
   boolean isRecovery = context.isRecovering();
   domainListener.onJobExecuted(..., isRecovery, jobException);
   ```

### 2.2 領域層 (Domain Layer)

**核心設計決策：保持 JobLog 單純，另建 Checkpoint 機制**
由於 `ScheduleJobLog` 定位為單純的「歷史稽核紀錄（Audit Log）」，屬於 Append-Only 且不可變（Immutable）的性質。而任務在執行過程中的「進度（Progress）」或「中斷點（Checkpoint）」需要頻繁被更新，若與 Log 混在一起會導致領域邊界模糊，且拖慢寫入效能。
因此，我們決定**另開一個全新的 Aggregate 來專門處理任務進度與接手**。

**變更內容：**
1. **新增 Aggregate: `JobExecutionCheckpoint` (任務執行檢查點)**：
   - **目的**：為了讓後續接手的 Node 知道前一個崩潰的 Node 執行到哪裡，必須將「進度」持久化。由於 Quartz 的 `@PersistJobDataAfterExecution` 在 JVM 崩潰時來不及觸發寫入，我們必須透過自定義的 Checkpoint 機制實作。
   - **結構**：
     ```java
     @Entity
     public class JobExecutionCheckpoint {
         private String jobId;          // 綁定排程 ID
         private String status;         // 目前狀態 (RUNNING, SUSPENDED, COMPLETED)
         private String lastCheckpoint; // 業務中斷點 (例如：最後處理的 User ID, 或 Page Number)
         private Integer totalSteps;    // 總步數
         private Integer currentStep;   // 當前步數
         private Boolean isRecovery;    // 是否為災後重試狀態
     }
     ```
   - **運作機制**：
     - **Node A 執行中**：每處理完一批資料（Batch），就更新 `JobExecutionCheckpoint` 的 `lastCheckpoint` 與 `currentStep`。
     - **Node A 崩潰，Node B 接管**：Node B 的 Quartz 透過 `requestRecovery` 觸發重跑（`context.isRecovering() == true`）。Node B 的 Job 邏輯第一步就是去資料庫讀取 `JobExecutionCheckpoint`，從 `lastCheckpoint` 接續執行，避免從頭重跑造成的重複與浪費。

2. **擴充 Command 指令**：
   在建立任務的命令中新增 `boolean isRecoveryEnabled` 屬性，讓上層業務可以決定該任務是否具備災後重建能力。

3. **擴充 Listener 介面與 JobLog**：
   `JobStatusListener` 的 `onJobStarting` 與 `onJobExecuted` 需新增 `boolean isRecovery` 參數。
   雖然進度拆給了 `JobExecutionCheckpoint`，但 `ScheduleJobLog` 依然可以新增一個 `boolean isRecovery` 的稽核標籤，用於在日誌中標示該次執行是「正常觸發」還是「災後補跑」。

### 2.3 介面層 (Interface Layer)
**影響檔案：**
- `ScheduleJobController.java`
- 相關 DTOs (`CreateCronJobResource`, `CreateOneTimeJobResource`)

**變更內容：**
1. **擴充 API 請求參數**：
   在前端呼叫建立排程的 API 時，允許傳入 `requestsRecovery` 參數。
   ```java
   public record CreateCronJobResource(
       // ... 其他屬性
       @Schema(description = "是否啟用災後重試", defaultValue = "true") 
       boolean requestsRecovery
   ) {}
   ```
2. **Controller 封裝轉換**：
   在 Controller 中將收到的 `requestsRecovery` 欄位傳遞給 Application Service 的 Command 中。

---

## 3. 風險評估與注意事項

1. **資料庫 Schema 異動**：
   - 需新增 `job_execution_checkpoint` 表格。
   - 需在 `schedule_job_log` 表格中新增 `is_recovery` 欄位。
2. **任務的進度儲存成本 (Checkpointing Overhead)**：頻繁寫入資料庫記錄進度會帶來效能開銷。建議業務邏輯採取「批次更新（Batch Processing）」模式，例如每處理 100 筆更新一次 Checkpoint，而非逐筆更新。
3. **任務的等冪性 (Idempotency) 要求**：雖然有了 Checkpoint 機制，但由於從「寫入 Checkpoint」到「JVM 崩潰」之間存在時間差，接手的 Node B 仍然可能重複執行最後一批次的一小段資料。因此，所有宣告為 `requestsRecovery = true` 的 Job 實作，**底層的資料操作依然必須具備等冪性設計**（例如：使用 DB `UPSERT` 語法、Unique Key 或狀態機檢查）。
