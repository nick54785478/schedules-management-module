# 分散式排程任務管理模組 (Schedules Management Module)

## 📌 模組簡介與核心定位 (Module Overview & Positioning)

**schedules-management-module** 是一套專為企業級微服務架構與分散式系統打造的**「嵌入式排程調度基礎設施模組 (Embedded Technical Infrastructure Module)」**。

### 為什麼需要此模組？（解決什麼痛點）
在典型的 Spring Boot 應用中，開發者常依賴 `@Scheduled` 註解處理定時任務。然而在多實例、分散式或微服務架構下，原生作法往往面臨以下痛點：
1. **叢集並行衝突 (Concurrency Conflict)**：多個服務節點同時啟動時，同一排程會在每一台節點重複觸發，導致資料重複處理、庫存超扣或寄送重複郵件。
2. **缺乏動態管控 (Inflexible Operations)**：排程週期寫死於原始碼或設定檔中，任何時間調整或任務的暫停/重啟，都必須重啟服務甚至重新打包編譯。
3. **單點故障與長時批次停擺 (Failover & Recovery Gap)**：長時批次任務（如大宗資料同步、百萬用戶通知）若執行到一半時節點崩潰 (Crash)，整批任務直接中斷，且重啟後必須從第一筆從頭重跑，缺乏進度續跑機制。
4. **排程與業務事務脫節 (Data Inconsistency)**：排程引擎操作與業務資料庫不在同一個事務中，易產生「資料庫已存檔但排程未註冊成功」，或是「排程已觸發但資料庫操作拋錯」的狀態偏差。

### 本模組的核心價值與關鍵能力
本模組旨在成為各項業務微服務底層開箱即用的排程基底，引入本模組即可**零負擔獲得企業級排程能力**：
- 🔄 **全生命週期動態調度**：免重啟系統，支援透過 REST API 或應用層 Inbound Port 動態註冊、更新時間、暫停、重啟定時 (Cron) 與單次 (OneTime) 排程，並支援多維度條件篩選。
- 🛡️ **分散式叢集高可用 (HA)**：基於資料庫層級分散式行鎖機制，多節點自動搶占執行權，杜絕重複執行，並具備節點故障自動轉移 (Failover) 能力。
- ⏱️ **長時批次斷點續跑 (Checkpoint Recovery)**：提供公版抽象模板，任務分步自動保存檢查點；當執行節點崩潰時，容錯節點接管後**直接從中斷點接續執行**，無需從頭再來。
- 📅 **全域排程日曆 (Calendar Blacklist)**：獨立日曆黑名單管理，支援排除國定假日或彈性休假，排程任務可動態綁定或解綁。
- 🔒 **雙向事務強一致性 (ACID)**：採用共享資料源 (Shared DataSource) 策略，業務表與 Quartz 表共用同一個連線事務，異動失敗同步回滾，杜絕狀態不一致。
- 🏛️ **純粹六角架構 (Hexagonal Clean Architecture)**：嚴守 Inbound / Outbound Port 設計，將排程技術（Quartz）與 ORM 實體徹底隔絕在基礎設施層，業務微服務只需呼叫清晰的 UseCase 介面。

---

## 🛠️ 使用技術與框架說明 (Tech Stack & Technologies)

本模組精選現代 Java 生態系中頂尖、穩定且具備長期支援能力的技術堆疊構建：

| 領域分類 | 技術 / 框架 | 版本 | 用途與職責說明 |
| :--- | :--- | :---: | :--- |
| **開發語言** | **Java** | `21 (LTS)` | 使用最新 LTS 版本，運用 Record 純資料模型、Pattern Matching 與現代化語言特性 |
| **核心框架** | **Spring Boot** | `4.0.6` | 現代化微服務與模組基礎容器，提供自動配置、元件掃描與生命週期管理 |
| **排程引擎** | **Quartz Scheduler** | `2.5.2` | 企業級分散式排程調度核心，負責 Trigger 觸發、Job 實例調度與叢集心跳檢測 |
| **資料持久化** | **Spring Data JPA** | `4.0.6` | 基於 Hibernate 的資料庫存取層，提供 Repository 抽象與 Criteria API 動態條件過濾 |
| **ORM 實現** | **Hibernate ORM** | `7.x` | 負責底層物件關聯映射、JPA Auditing 自動時間戳與資料庫綱要維護 |
| **關聯式資料庫** | **MySQL** | `8.x` | 生產環境資料庫，儲存業務排程模型 (`schedule_*`) 與 Quartz 系統表 (`QRTZ_*`) |
| **測試資料庫** | **H2 Database** | `Runtime` | 本機輕量化快速開發與單元/整合測試使用的記憶體資料庫 |
| **併發與鎖定** | **Quartz JDBC JobStore (TX)** | - | 透過 MySQL 行級排他鎖 (`SELECT ... FOR UPDATE`) 實現叢集多節點的互斥競爭 |
| **API 文檔** | **SpringDoc OpenAPI (Swagger UI)**| `3.1.1` | 遵循 OpenAPI 3 規範，提供 `/swagger-ui.html` 互動式 API 測試與即時接口文檔 |
| **參數檢核** | **Jakarta Validation** | `3.1.x` | 表現層請求輸入防呆檢核 (`@Valid`, `@NotBlank`, `@Pattern` 等) |
| **開發輔助** | **Lombok** | `1.18.x` | 精簡 POJO 樣板程式碼、依賴注入構造器 (`@RequiredArgsConstructor`) 與日誌宣告 |
| **單元測試** | **JUnit 5 (Jupiter)** | `5.11.x` | 現代化 Java 單元測試標準框架，支援生命週期鉤子與參數化測試 |
| **Mock 框架** | **Mockito** | `5.14.x` | 支援六角架構各層 Port 的解耦 Mock 隔離測試 (啟用 inline-mock-maker) |
| **Web 測試** | **Spring Boot WebMvcTest** | `4.0.6` | 針對表現層 REST Controller 進行 HTTP 請求與 JSONPath 斷言驗證 |

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
|    - query/: GetScheduleJobQuery                                                  |
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
