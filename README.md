# 分散式排程任務管理模組 (Schedules Management Module)

本模組基於 **Spring Boot 4.0.6** 與 **Quartz Scheduler 2.5.2** 構建，定位為**嵌入式技術基礎設施模組 (Embedded Technical Infrastructure Module)**。它旨在為未來的各項微服務提供一套高可用、具備事務強一致性且遵循 **Clean Architecture（六角架構 / Ports and Adapters）** 的排程調度核心基底。

---

## 一、 架構設計原則 (Clean Architecture & Hexagonal Architecture)

本模組嚴格遵循依賴反轉與關注點分離原則，各層之間透過純資料模型、Command/Query 與 Port 介面進行通訊：

```
+-----------------------------------------------------------------------------------+
|                        Presentation Layer (Inbound Adapters)                      |
|  - REST Controllers: ScheduleJobController, CalendarController                    |
|  - Quartz Inbound Jobs: BatchSendEmailJob, FailoverSimulationJob, etc.            |
+------------------------------------------+----------------------------------------+
                                           | calls UseCases (Inbound Ports)
                                           v
+-----------------------------------------------------------------------------------+
|                                 Application Layer                                 |
|  [port/in/] Inbound Ports (V + N + UseCase)                                       |
|    - CreateCronJobUseCase, CreateOneTimeJobUseCase, UpdateScheduleJobUseCase      |
|    - BindJobCalendarUseCase, PauseScheduleJobUseCase, ResumeScheduleJobUseCase    |
|    - CompleteScheduleJobUseCase, GetScheduleJobUseCase                            |
|    - CreateCalendarUseCase, AddExcludedDateUseCase                                |
|    - RemoveExcludedDateUseCase, GetCalendarUseCase                                |
|                                                                                   |
|  [service/] Application Services                                                  |
|    - ScheduledJobApplicationService, CalendarApplicationService                   |
|                                                                                   |
|  [shared/] Pure Data Contracts (零外部技術侵入)                                   |
|    - vo/: JobId, JobStatus, ScheduleType, CronExpression, ScheduleRule           |
|    - model/: ScheduledJobModel, ScheduleCalendarModel                             |
|    - command/: Inbound / Outbound Commands                                        |
|    - view/: ScheduleJobGottenView, ScheduleCalendarView, PageGottenView          |
|    - exception/: JobNotFoundException, CalendarNotFoundException, etc.            |
|                                                                                   |
|  [port/out/] Outbound Ports (...Port)                                             |
|    - ScheduledJobRepositoryPort, ScheduleCalendarRepositoryPort                   |
|    - JobSchedulerPort, CronParserPort, DistributeLockManagerPort                  |
+------------------------------------------+----------------------------------------+
                                           ^ implemented by Adapters
                                           |
+-----------------------------------------------------------------------------------+
|                      Infrastructure Layer (Outbound Adapters)                     |
|  [adapter/]                                                                       |
|    - ScheduledJobRepositoryAdapter, ScheduleCalendarRepositoryAdapter             |
|    - JobSchedulerAdapter, CronParserAdapter, DistributeLockManagerAdapter         |
|                                                                                   |
|  [persistence/] (JPA Entities & Spring Data Repositories)                         |
|    - schedule/: ScheduledJobJpaEntity, ScheduledJobJpaRepository                  |
|    - calendar/: ScheduleCalendarJpaEntity, ScheduleCalendarJpaRepository          |
|    - joblog/, distlock/, checkpoint/                                              |
|                                                                                   |
|  [quartz/]                                                                        |
|    - Configuration, AutowiringSpringBeanJobFactory, Listeners                     |
+-----------------------------------------------------------------------------------+
```

### 1. Inbound Ports (`application/port/in/`)
- **命名風格**：嚴格遵循 **`V + N + UseCase` (動詞 + 名詞 + UseCase)** 命名規範。
- **介面隔離原則 (ISP)**：拒絕龐大的 CRUD Fat Interface，依業務目標拆分為高內聚的細粒度用例合約，使表現層控制器與監聽器僅依賴其真正需要的業務方法。
- **傳參規範**：所有方法皆接收純 Command/Query 或純基本型別，並回傳純資料視圖 (View/DTO)，嚴禁傳入任何技術實體。

### 2. Outbound Ports (`application/port/out/`)
- 應用層對基礎設施層提出的契約，一律以 **`...Port`** 結尾。
- 隔離所有底層細節（如 JPA 資料庫、分散式鎖、Quartz 排程引擎、Cron 表達式解析器）。

### 3. Application Services (`application/service/`)
- 作為 Inbound Port UseCases 的實作者，協調整合 Outbound Ports（資料持久化與 Quartz 引擎調度），管理 `@Transactional` 事務邊界。

### 4. Infrastructure Layer (`infra/`)
- **持久化分離 (`infra/persistence/`)**：資料庫 ORM 實體 (`@Entity`) 與 Spring Data Repositories 完全收攏於此層，不向外層滲透。
- **適配器出口 (`infra/adapter/`)**：實作 Outbound Port 介面，一律以 **`...Adapter`** 結尾，負責 JPA Entity 與純資料模型 (`ScheduledJobModel`, `ScheduleCalendarModel`) 之間的防腐轉換。

---

## 二、 核心技術特性

### 1. 分散式叢集與高可用 (Cluster HA)
透過 Quartz 的資料庫行鎖 (`QRTZ_LOCKS`) 機制實現多節點協作，防止排程重複執行並支援自動故障轉移（Failover）：
- **動態識別**：各節點啟動時由 Spring 容器自動生成唯一的 `instanceId`。
- **心跳監測**：節點每隔 15 秒向 `QRTZ_SCHEDULER_STATE` 資料表更新運行心跳。

### 2. 事務強一致性 (Transactional Integrity)
本模組採用 **共享資料源 (Shared DataSource)** 策略：
- **原子操作**：模組內部自定義表 (`schedule_job`, `schedule_calendar` 等) 與 Quartz 系統表 (`QRTZ_*`) 共用同一個 JDBC 連線與 Spring Transaction。
- **雙向同步回滾**：更新排程或暫停/恢復時，若 Quartz 引擎拋出異常或資料庫操作失敗，兩側資料均同步回滾，杜絕狀態不一致的偏差。

### 3. 領域輸入檢核與防腐 (Validation & Anti-Corruption)
- 透過 `CronParserPort` (由 `CronParserAdapter` 實作) 於業務入口攔截語法錯誤，防止無效的 Cron 表達式進入系統模型中。
- 全面支援 Spring Validation (`@Valid`)，並由 `GlobalExceptionHandler` 統一轉換為標準錯誤回應。

### 4. 模組定位與微服務整合策略
**本專案定位為「共用技術模組」而非「獨立微服務」**：
- **作為底層基底引入**：微服務專案引入本模組時，本模組應整體視為該微服務的 **基礎設施技術庫 (Technical Infrastructure)**。
- **防止業務污染**：微服務核心商業領域不應直接耦合本模組的實體或資料表，微服務應定義自己的業務 Port，並在其 Infrastructure 層呼叫本模組公開的 UseCase 介面。

---

## 三、 開發者指南

### 建立與註冊排程任務

#### 1. 實作 Job 類別 (Inbound Adapter)
在六角架構中，Quartz Job 屬於驅動端適配器（等同於 API Controller）：
```java
@Component("batchSendEmailJob")
public class BatchSendEmailJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // 執行排程業務邏輯
    }
}
```
- **動態 Bean 解析機制**：排程註冊時僅需提供 Spring Bean Name（如 `"batchSendEmailJob"`），系統會透過 `AutowiringSpringBeanJobFactory` 自動解析與裝配，避免在資料庫硬編碼 Class Full Path，即使專案重構改動 Package 路徑也不影響排程執行。

#### 2. 註冊排程任務 (二擇一)
- **A. 系統啟動時初始化**：
  在 `ScheduleJobRegistration` 中監聽 `ApplicationReadyEvent`，注入 `CreateCronJobUseCase`：
  ```java
  @Component
  @RequiredArgsConstructor
  public class ScheduleJobRegistration {
      private final CreateCronJobUseCase createCronJobUseCase;

      @EventListener(ApplicationReadyEvent.class)
      public void init() {
          CreateCronJobCommand command = new CreateCronJobCommand(
              "BatchEmailJob", "NotificationGroup", "batchSendEmailJob",
              "0 0/5 * * * ?", null, true
          );
          createCronJobUseCase.initializeCronTask(command);
      }
  }
  ```
- **B. 透過 REST API 動態建立**：
  呼叫 `POST /jobs/create-cron` 或 `POST /jobs/create-one-time`。

#### 3. 註冊自定義排程監聽器 (Observer Pattern)
- 實作 `JobStatusListener` 介面（可監聽任務啟動、結束、耗時與異常）。
- 在啟動註冊類中透過 `JobSchedulerPort.registerJobListener()` 或 `registerGlobalListener()` 掛載。

---

## 四、 RESTful API 接口規格

所有排程管理 API 基礎路徑為 `/jobs`，日曆管理基礎路徑為 `/api/calendars`。

### 1. 排程任務管理 (`/jobs`)
| HTTP Method | Path | 依賴之 Inbound Port | 描述 |
| :--- | :--- | :--- | :--- |
| `POST` | `/jobs/create-cron` | `CreateCronJobUseCase` | 建立定時 Cron 排程任務 (具備覆寫冪等性) |
| `POST` | `/jobs/create-one-time` | `CreateOneTimeJobUseCase` | 建立單次執行排程任務 |
| `PUT` | `/jobs/{jobId}/update-cron` | `UpdateScheduleJobUseCase` | 更新指定任務之 Cron 表達式 |
| `PUT` | `/jobs/{jobId}/update-one-time` | `UpdateScheduleJobUseCase` | 更新指定任務之單次執行時間 |
| `PUT` | `/jobs/{jobId}/bind-calendar` | `BindJobCalendarUseCase` | 為任務綁定或解除特定排除日曆黑名單 |
| `POST` | `/jobs/pause/{jobId}` | `PauseScheduleJobUseCase` | 暫停指定排程任務 (原子操作同步引擎) |
| `POST` | `/jobs/resume/{jobId}` | `ResumeScheduleJobUseCase` | 恢復已被暫停的排程任務 |
| `GET` | `/jobs/status` | `GetScheduleJobUseCase` | 分頁與條件過濾查詢目前配置與即時狀態快照 (支援 status, jobType, jobGroup, scheduleType) |

### 2. 排程日曆黑名單管理 (`/api/calendars`)
| HTTP Method | Path | 依賴之 Inbound Port | 描述 |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/calendars` | `CreateCalendarUseCase` | 建立全新的排除日曆黑名單 |
| `GET` | `/api/calendars` | `GetCalendarUseCase` | 分頁查詢所有已設定的日曆清單 |
| `GET` | `/api/calendars/{id}/holidays` | `GetCalendarUseCase` | 查詢特定日曆下所有的排除日期 |
| `POST` | `/api/calendars/{id}/holidays` | `AddExcludedDateUseCase` | 為指定日曆新增單一排除日期 |
| `DELETE` | `/api/calendars/{id}/holidays/{date}` | `RemoveExcludedDateUseCase` | 自指定日曆中移除排除日期 (如: 補班日) |

---

## 五、 運維監控、日誌與例外處理

### 1. 排程執行日誌持久化 (`PersistJobLogListener`)
- 系統支援記錄每一次排程執行的狀態、耗時 (`durationMs`) 與異常堆疊至 `schedule_job_log` 資料表。
- **獨立事務設計**：日誌持久化採用 `Propagation.REQUIRES_NEW`，即使排程業務內部發生例外導致回滾，歷史日誌依然能獨立提交。
- **功能開關**：由設定檔 `app.schedule.job-log.enabled=true` 進行控制，關閉時無任何效能耗損。

### 2. 引擎離線降級機制 (Graceful Degradation)
當呼叫 `/jobs/status` 查詢任務清單時，若 Quartz 引擎離線或短暫不可用，系統將自動啟動降級策略，狀態標記為 `UNKNOWN (ENGINE_OFFLINE)`，並呈現資料庫中的配置快照，保障管理端介面不中斷。

### 3. 全域例外攔截與錯誤代碼
| 錯誤代碼 (errorCode) | HTTP Status | 原因說明 |
| :--- | :--- | :--- |
| `VALIDATION_FAILED` | `400 Bad Request` | Request Body 必填欄位或約束校驗未通過 |
| `TYPE_MISMATCH` | `400 Bad Request` | 請求參數型別轉換失敗 (如日期格式不符) |
| `BAD_REQUEST` | `400 Bad Request` | 違反基本商業規則 (如重複新增相同日期) |
| `INVALID_CRON` | `422 Unprocessable` | 輸入之 Cron 表達式語法無效 |
| `JOB_NOT_FOUND` | `404 Not Found` | 指定之 `JobId` 排程任務不存在 |
| `ENGINE_ERROR` | `500 Server Error` | 底層 Quartz 引擎操作故障 |
