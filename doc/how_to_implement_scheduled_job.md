# 排程任務開發與實作指南 (Scheduled Job Implementation Guide)

本文件提供完整的排程任務開發指南，說明如何在 **schedules-management-module** 模組中建立、配置、註冊並監控一個排程任務。

---

## 目錄
1. [架構定位與職責邊界](#一-架構定位與職責邊界)
2. [排程任務實作模式](#二-排程任務實作模式)
   - [模式 A：標準排程任務 (Standard Job)](#模式-a標準排程任務-standard-job)
   - [模式 B：具斷點續跑能力的批次任務 (Recoverable Batch Job)](#模式-b具斷點續跑能力的批次任務-recoverable-batch-job)
3. [排程任務註冊方式](#三-排程任務註冊方式)
   - [方式一：程式碼啟動初始化 (系統排程)](#方式一程式碼啟動初始化-系統排程)
   - [方式二：REST API 動態註冊 (動態排程)](#方式二rest-api-動態註冊-動態排程)
4. [排除日曆黑名單綁定 (Calendar)](#四-排除日曆黑名單綁定-calendar)
5. [自定義任務監聽器 (Job Listener)](#五-自定義任務監聽器-job-listener)
6. [最佳實踐與避坑指南 (Best Practices)](#六-最佳實踐與避坑指南-best-practices)

---

## 一、 架構定位與職責邊界

在 **六角架構 (Ports & Adapters / Clean Architecture)** 中，Quartz Job 的定位為 **表現層之驅動端適配器 (Presentation Layer / Inbound Adapter)**，其職責等同於 REST Controller：

```
[外部觸發: Quartz 引擎定時觸發]
               │
               ▼
[Inbound Adapter: 您的 Quartz Job 類別]
               │
               │ (1) 取得排程上下文 (JobExecutionContext)
               │ (2) 組裝 Command/Query 物件
               │ (3) 呼叫 Application UseCase (Inbound Port)
               ▼
[Application Layer: UseCase / Service (@Transactional)]
               │
               ▼
[Outbound Port / Infrastructure: DB, MQ, 外部 API]
```

### 職責守則：
- ✅ **允許呼叫**：透過 Spring 依賴注入應用層的 **UseCase (Inbound Port)** 或業務服務，由應用層統一管理 `@Transactional` 事務邊界。
- ❌ **嚴禁越級**：絕對不可直接注入底層持久化 Repository (Outbound Port) 或 ORM Entity，嚴禁在 Job 內直接寫入資料庫或自行控制 Transaction。
- ❌ **避免過重業務**：Job 本身僅作為「排程觸發與調度入口」，具體業務運算與資料異動應收斂在領域/應用層。

---

## 二、 排程任務實作模式

系統提供兩種排程實作方式，依據任務特性選擇：

### 模式 A：標準排程任務 (Standard Job)
適用於一般輕量任務、定時同步或發送單次觸發訊號（如每分鐘印出訊息、定時重整快取等）。

#### 實作步驟：
1. 實作 `org.quartz.Job` 介面。
2. 加上 `@Component("<jobBeanName>")`，指定 Spring Bean 名稱（供後續排程動態解析）。
3. 建議加上 `@DisallowConcurrentExecution`，防止上一次執行尚未結束時再次重複觸發。
4. 注入業務 UseCase 並於 `execute()` 內呼叫。

#### 範例程式碼：
```java
package com.example.demo.iface.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 每日資料同步排程範例
 */
@Slf4j
@Component("dailySyncDataJob") // 務必指定 Bean Name
@DisallowConcurrentExecution  // 避免前次尚未跑完時重疊執行
@RequiredArgsConstructor
public class DailySyncDataJob implements Job {

    // 注入應用層 UseCase
    // private final SyncDataUseCase syncDataUseCase;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("[DailySyncDataJob] 開始執行定時資料同步作業...");
        try {
            // 呼叫應用層業務邏輯
            // syncDataUseCase.sync();
            log.info("[DailySyncDataJob] 資料同步完成！");
        } catch (Exception ex) {
            log.error("[DailySyncDataJob] 同步過程發生嚴重錯誤: {}", ex.getMessage(), ex);
            // 拋出 JobExecutionException 通知 Quartz
            throw new JobExecutionException(ex);
        }
    }
}
```

---

### 模式 B：具斷點續跑能力的批次任務 (Recoverable Batch Job)
適用於**長時執行、大批量處理**的關鍵任務（如：批次發送數萬封 Email、大宗報表產製）。
當執行節點中途 Crash（當機、重啟、網路斷線）時，Quartz 叢集會進行 Failover，其他活著的節點能**從上次中斷的步驟直接接續執行**，無需從頭重來！

#### 實作步驟：
1. 繼承抽象公版類別 `AbstractRecoverableBatchJob`。
2. 實作兩大核心方法：
   - `calculateTotalSteps(JobExecutionContext context)`：計算此次任務總步數（僅在全新執行時呼叫一次）。
   - `processStep(int currentStep, String lastCheckpoint, JobExecutionContext context)`：執行單一步驟，並回傳該步驟的進度標記（Checkpoint）。

#### 範例程式碼 (參考 `BatchSendEmailJob`)：
```java
package com.example.demo.iface.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import com.example.demo.iface.scheduler.base.AbstractRecoverableBatchJob;
import com.example.demo.infra.persistence.checkpoint.repository.JobExecutionCheckpointJpaRepository;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
@Component("batchSendEmailJob")
@DisallowConcurrentExecution
public class BatchSendEmailJob extends AbstractRecoverableBatchJob {

    // 建構子需注入 Checkpoint Repository
    public BatchSendEmailJob(JobExecutionCheckpointJpaRepository checkpointRepository) {
        super(checkpointRepository);
    }

    private static final int BATCH_SIZE = 50; // 每批處理 50 筆

    @Override
    protected int calculateTotalSteps(JobExecutionContext context) throws Exception {
        int totalRecords = 1000; // 查詢待處理總筆數
        return (int) Math.ceil((double) totalRecords / BATCH_SIZE);
    }

    @Override
    protected String processStep(int currentStep, String lastCheckpoint, JobExecutionContext context) throws Exception {
        log.info("處理第 {} 步，上一步中斷點為: {}", currentStep, lastCheckpoint);

        // 依據 lastCheckpoint 撈取該批次資料進行處理
        // List<Data> records = fetchDataAfter(lastCheckpoint, BATCH_SIZE);
        // processRecords(records);

        String currentCheckpoint = "RECORD_ID_50"; // 記錄本步驟最後處理成功的 ID
        return currentCheckpoint; // 回傳後，框架會自動將進度持久化至資料庫
    }
}
```

---

## 三、 排程任務註冊方式

在建立好 Job 類別後，必須將其註冊至系統中以生成 Trigger 並載入 Quartz 引擎。

### 方式一：程式碼啟動初始化 (系統排程)
適用於系統內建、隨應用程式啟動即必須存在的核心排程。

在 `ScheduleJobRegistration.java` 中註冊：
```java
@Component
@AllArgsConstructor
public class ScheduleJobRegistration {

    private final CreateCronJobUseCase createCronJobUseCase;
    private final CreateOneTimeJobUseCase createOneTimeJobUseCase;

    @EventListener(ApplicationReadyEvent.class)
    public void init() throws Exception {
        // 註冊 Cron 定時排程 (每 5 分鐘執行一次)
        CreateCronJobCommand cronCmd = new CreateCronJobCommand(
            "DailySyncJob",           // 任務名稱 (唯一識別)
            "SyncGroup",              // 任務群組
            "dailySyncDataJob",       // 對應 Job 類別的 Spring Bean Name
            "0 0/5 * * * ?",          // 6 位 Cron 表達式
            null,                     // 排除日曆 key (若無則填 null)
            true                      // requestsRecovery: 支援故障重啟恢復
        );
        createCronJobUseCase.initializeCronTask(cronCmd);

        // 若要註冊一次性排程：
        // CreateOneTimeJobCommand oneTimeCmd = new CreateOneTimeJobCommand(
        //     "OneTimeReportJob", "ReportGroup", "reportGenerateJob",
        //     LocalDateTime.now().plusHours(1), false
        // );
        // createOneTimeJobUseCase.initializeOneTimeTask(oneTimeCmd);
    }
}
```
> **特性**：`initializeCronTask` 具備**覆寫冪等性 (Replace 模式)**，每次重啟都會檢查並更新為最新的 Cron 與配置，避免配置不同步。

---

### 方式二：REST API 動態註冊 (動態排程)
適用於透過後台管理介面、外部系統微服務動態新增排程。

#### 1. 新增 Cron 定時排程
- **HTTP Method**：`POST`
- **Path**：`/jobs/create-cron`
- **Request Body (`CreateCronJobResource`)**：
```json
{
  "name": "MemberReportJob",
  "group": "ReportGroup",
  "jobType": "dailySyncDataJob",
  "cron": "0 0 2 * * ?",
  "calendarKey": "TAIWAN_HOLIDAY",
  "requestsRecovery": true
}
```
- **Response (201 Created)**：
```json
{
  "code": "201",
  "message": "定時排程任務建立成功"
}
```

#### 2. 新增單次執行排程 (OneTime)
- **HTTP Method**：`POST`
- **Path**：`/jobs/create-one-time`
- **Request Body (`CreateOneTimeJobResource`)**：
```json
{
  "name": "NotificationOneTimeJob",
  "group": "NotificationGroup",
  "jobType": "messagePrintJob",
  "executeDate": "2026/12/31",
  "executeTime": "23:59",
  "requestsRecovery": false
}
```

---

## 四、 排除日曆黑名單綁定 (Calendar)

若任務在特定節假日（如國定假日、例假日）不應執行，可綁定日曆黑名單。

### 步驟：
1. **建立日曆**：
   - 呼叫 `POST /api/calendars` 建立名稱為 `TAIWAN_HOLIDAY` 的日曆。
2. **新增排除日期**：
   - 呼叫 `POST /api/calendars/{calendarId}/holidays` 加入國定假日（如 `2026-10-10`）。
3. **綁定任務與日曆**：
   - 於建立任務時傳入 `calendarKey: "TAIWAN_HOLIDAY"`。
   - 或呼叫 `PUT /jobs/{jobId}/bind-calendar` 動態更新綁定：
   ```json
   {
     "calendarKey": "TAIWAN_HOLIDAY"
   }
   ```
   > 傳入 `null` 或空字串可解除日曆綁定。

---

## 五、 自定義任務監聽器 (Job Listener)

模組已內建全域日誌記錄監聽器 (`GlobalJobListener` 與 `PersistJobLogListener`)。若您需要針對**特定任務**進行自定義業務攔截（例如：執行前通知、執行後寄送報表）：

### 1. 實作 `JobStatusListener`
```java
package com.example.demo.infra.quartz.listener.impl;

import org.springframework.stereotype.Component;
import com.example.demo.application.shared.listener.JobStatusListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ReportJobListener implements JobStatusListener {

    @Override
    public String getName() {
        return "ReportJobListener";
    }

    @Override
    public void onJobStarting(String jobName, String jobGroup, boolean isRecovery) {
        log.info("📢 報表產製任務啟動: {}.{}", jobGroup, jobName);
    }

    @Override
    public void onJobVetoed(String jobName, String jobGroup, boolean isRecovery) {
        log.warn("⚠️ 報表任務被取消！");
    }

    @Override
    public void onJobExecuted(String jobName, String jobGroup, boolean isRecovery, Exception exception) {
        if (exception == null) {
            log.info("🎉 報表產製成功！開始寄送通知郵件...");
        } else {
            log.error("❌ 報表產製失敗: {}", exception.getMessage());
        }
    }
}
```

### 2. 掛載至指定 Job
在 `ScheduleJobRegistration.java` 中掛載：
```java
// 僅掛載至特定 name 與 group 的 Job
jobScheduler.registerJobListener(reportJobListener, "MemberReportJob", "ReportGroup");
```

---

## 六、 最佳實踐與避坑指南 (Best Practices)

1. **一律使用 Spring Bean Name 作為 `jobType`**：
   - 系統透過 `AutowiringSpringBeanJobFactory` 依據 Bean Name 動態尋找實體。
   - **優點**：資料庫僅儲存單純識別碼，未來若更動 Java 類別的 package 路徑，資料庫舊排程依然能正常執行，徹底實現技術解耦。

2. **必須加上 `@DisallowConcurrentExecution`**：
   - 除非刻意設計為允許重疊執行的無狀態任務，否則務必加上此註解，防止同一排程因前一次執行過久導致並行衝突。

3. **`requestsRecovery` 參數配置建議**：
   - 對於**絕對不能漏跑**的重要業務，建議設為 `true`。當機器重開或 Crash 時，Quartz 會自動由另一台機器重新執行。
   - 若設為 `true`，**業務邏輯必須具備冪等性 (Idempotency)**，或繼承 `AbstractRecoverableBatchJob` 進行斷點控制。

4. **異常處理原則**：
   - 若任務發生業務失敗且希望 Quartz 觸發錯誤監聽器並記錄至歷史日誌 (`schedule_job_log`)，請務必將例外拋出或包裝為 `JobExecutionException` 拋出，切勿使用空白的 `try-catch` 默默吞掉錯誤。
