# 可災後重跑批次任務框架 (Batch Framework) 開發指南

## 1. 簡介與設計初衷

在企業級應用中，我們經常遇到需要「長時間執行」的排程任務，例如：
* 批次寄送數萬封行銷信件
* 呼叫外部 API 同步百萬筆使用者資料
* 每月產生極度耗時的財務報表

這些任務如果直接寫在一個迴圈內，一旦遭遇網路中斷 (Timeout) 或是伺服器無預警重啟 (Crash)，往往只能從第 1 筆「從頭重跑」，耗時且浪費資源。

為了完美結合 Quartz 叢集的 `RequestsRecovery` 機制，我們設計了 **`AbstractRecoverableBatchJob`** 抽象框架。這個「公版框架」已經幫您處理掉所有繁雜的資料庫進度讀寫 (`JobExecutionCheckpoint`) 以及災後狀態判斷，讓您能專注在業務邏輯上。

---

## 2. 如何使用

要開發一個具備「接續執行」能力的長時批次任務，只需要兩個步驟：

### 步驟 1：繼承 `AbstractRecoverableBatchJob`
建立您的 Job 類別，並繼承 `AbstractRecoverableBatchJob`。別忘了掛上 `@DisallowConcurrentExecution` 避免排程併發重疊。

### 步驟 2：實作兩個核心方法

您只需要實作框架要求的兩個抽象方法：

```java
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import com.example.demo.iface.scheduler.base.AbstractRecoverableBatchJob;

@Component("myExternalSyncJob")
@DisallowConcurrentExecution
public class MyExternalSyncJob extends AbstractRecoverableBatchJob {

    /**
     * 1. 決定總步數
     * 此方法只會在「全新執行」時被呼叫。框架會根據這個數字來決定迴圈要跑幾次。
     */
    @Override
    protected int calculateTotalSteps(JobExecutionContext context) throws Exception {
        // 範例：計算外部 API 需要打多少頁 (Page)
        int totalRecords = 50000;
        int pageSize = 1000;
        return totalRecords / pageSize; 
    }

    /**
     * 2. 撰寫單一步驟的實際業務
     * 框架會幫您處理迴圈，您只需專注在「目前這一步要幹嘛」。
     */
    @Override
    protected String processStep(int currentStep, String lastCheckpoint, JobExecutionContext context) throws Exception {
        // 1. 根據 currentStep (目前第幾頁) 撈取資料庫
        // 2. 呼叫外部 API 拋轉資料
        
        // 3. 回傳您的進度標記 (例如：最後處理的 ID，或單純的頁碼字串)
        // 框架會自動將此標記存入 Checkpoint 資料表中
        return "PAGE_" + currentStep + "_DONE";
    }
}
```

這就是全部了！框架會在背景為您完成所有的 Failover 接續邏輯。

---

## 3. 開發實務與注意事項 (必讀 ⚠️)

### 3.1 關於 Transaction (@Transactional) 的使用

這是一個非常重要的概念：**千萬不要把 `@Transactional` 掛在 Job 類別或是 `processStep` 的宣告上**。

* **錯誤做法**：如果整個 Job 都在同一個 Transaction 內，當您在第 49 步伺服器當機時，這 49 步的 `checkpoint` 更新都會跟著 Rollback。新接手的伺服器會因為讀不到 Checkpoint，又從第 1 步開始重跑。
* **正確做法**：框架已經設計成「每執行完一次 `processStep` 就立刻 Commit Checkpoint」。如果您在單一步驟中需要寫入自己的業務資料 (例如標記某筆訂單為已發送)，請在內部使用獨立的 Transaction (例如另一個有 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 的 Service 參與)，確保每步都能「落袋為安」。

### 3.2 外部系統的「冪等性 (Idempotency)」

在分散式系統中，沒有絕對的 "Exactly-Once" (剛好一次)，我們只能做到 "At-Least-Once" (至少一次)。

想像以下情境：
1. 您在 `processStep` 成功呼叫了外部 API 寄信。
2. 剛寄完，**尚未回傳 String 讓框架寫入 Checkpoint 時**，您的伺服器被拔插頭了。
3. 另一台伺服器接手，因為 Checkpoint 沒更新，它會**重跑這一步**。

**解決方案**：
這個公版框架完美適用於任何需要大量拋轉的場景，但您必須確保接收方（外部系統）的 API 是**冪等的 (Idempotent)**。
例如：外部系統能根據 `RequestId` 或 `Data ID` 判斷資料是否已經存在，若是重複呼叫則自動略過或採用 `UPSERT` 覆蓋。只要滿足這點，框架就能為您帶來完美無痛的斷點續傳體驗！
