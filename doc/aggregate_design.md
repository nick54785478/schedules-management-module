# 領域模型設計 (Domain Model Design) - ScheduledJob 聚合

本文件詳細說明 `schedules-management-module` 中排程模組的核心領域模型（Domain Model），特別是聚合根（Aggregate Root）與數值物件（Value Object）的設計理念與邊界。

## 1. 聚合根 (Aggregate Root): `ScheduledJob`

`ScheduledJob` 是排程管理模組唯一且核心的聚合根，負責維護單個排程任務的完整生命週期與業務規則。

### 屬性定義
*   **`JobId jobId`**: 領域唯一識別碼（UUID），用於系統內部參考。
*   **`String name`**: 業務名稱，與 `group` 共同組成 Quartz 引擎中的唯一鍵 (`JobKey`)。
*   **`String group`**: 業務分組。
*   **`String jobType`**: 技術對應標籤，實際上儲存的是 Spring 容器中 `org.quartz.Job` 實作的 **Bean Name**。
*   **`ScheduleRule scheduleRule`**: 排程規則 (VO)，採用 Tagged Union 模式封裝了 Cron 或是 OneTime 排程型態，並可選配綁定排程日曆 (Calendar)。
*   **`JobStatus status`**: 業務狀態，包含 `NORMAL`（正常）、`PAUSED`（暫停）、`STOPPED`（停止）與 `COMPLETED`（已完成，專用於一次性任務）。

### 業務行為 (Business Behaviors)
所有的狀態變更都必須透過聚合根暴露的方法進行，確保狀態一致性：
*   **`register(...)` (Factory Method)**: 工廠方法，用於在建立新排程時，自動產生 `JobId` 並將狀態初始化為 `NORMAL`。
*   **`pause()`**: 暫停排程。只有當狀態為 `NORMAL` 時才允許變更為 `PAUSED`。
*   **`resume()`**: 恢復排程。只有當狀態為 `PAUSED` 時才允許變更為 `NORMAL`。
*   **`changeSchedule(String newCron)` / `updateCron(...)`**: 更新執行週期。領域規則限制：若狀態為 `STOPPED` (已停止)，則拋出例外拒絕修改。

---

## 2. 聚合根 (Aggregate Root): `ScheduleCalendar`

`ScheduleCalendar` 是專門管理「排程日曆」的聚合根。它負責維護全域的黑名單例外日期 (如：國定假日、週末)，並且能被多個 `ScheduledJob` 共用。

### 屬性定義
*   **`String key`**: 領域唯一識別鍵（如：`TAIWAN_HOLIDAY_2026`），對應 Quartz 引擎內的 Calendar Name (calendar_key)。
*   **`String description`**: 人類可讀的日曆描述。
*   **`Set<LocalDate> excludedDates`**: 具體要排除的日期集合（黑名單），以此阻擋排程在這些日子執行。

### 業務行為 (Business Behaviors)
*   **`create(...)` (Factory Method)**: 建立一個全新的空白日曆。
*   **`addExcludedDate(LocalDate date)`**: 將特定日期加入排除清單（例如：新增國定假日）。
*   **`removeExcludedDate(LocalDate date)`**: 將特定日期移出排除清單（例如：補班日需正常執行）。

---

## 3. 數值物件 (Value Objects)

為了避免 Primitive Obsession（基本型別偏執），領域層使用了數值物件來包裝特定的概念。

### `JobId` (Record)
*   **設計目的**: 將 UUID 字串包裝為專屬型別，增強型別安全性 (Type Safety)。在方法傳遞時，可以明確知道這是一個 Job ID 而不是普通的 String。

### `JobStatus` (Enum)
*   **狀態枚舉**: 定義了 `NORMAL`、`PAUSED`、`STOPPED`，限制了排程狀態的可能值，避免無效狀態的產生。

### `ScheduleRule` (Embeddable VO)
*   **設計目的**: 將排程的多型狀態（Cron 定時任務 vs. OneTime 一次性任務）、災後重建標記 (`requestsRecovery`) 以及綁定的 Calendar 鍵封裝在一起。
*   **「純粹領域」設計原則**: 
    1. 採用 Tagged Union (多型退化為屬性標籤) 設計，透過 `ScheduleType` 來區分目前存放的是 Cron 字串還是精確的 `LocalDateTime`。
    2. 結合 JPA `@Embeddable` 將屬性扁平化映射至同一張表，避免關聯表帶來的複雜度。
    3. 保留了 `calendarKey` 作為與 `ScheduleCalendar` 聚合根的 Soft Link (軟連結)，實現跨聚合的鬆耦合協作。

---

## 4. 聚合根 (Aggregate Root): `ScheduleJobLog` (Audit Entity)

`ScheduleJobLog` 是用於記錄 Quartz 引擎中每一個 Job 執行歷史軌跡的聚合根。

### 領域設計理念
此聚合與 `ScheduledJob` 完全解耦，負責單純的稽核紀錄 (Audit Logging)，不包含排程控制的業務邏輯。這確保了系統查詢日誌時，不會因為載入巨大的日誌集合而拖垮排程配置聚合的效能。

### 屬性定義
*   **`Long id`**: 資料庫自增主鍵。
*   **`String jobName` & `String jobGroup`**: 任務的名稱與分組（對應 Quartz 的 `JobKey`）。
*   **`String status`**: 執行結果狀態 (`SUCCESS`, `FAILED`, `VETOED`)。
*   **`Long durationMs`**: 執行耗時 (毫秒)。
*   **`String errorMessage`**: 失敗時的錯誤訊息。
*   **`Boolean isRecovery`**: 是否為災後重試。
*   **`LocalDateTime executedAt`**: 紀錄產生時間。

### 業務行為 (Business Behaviors)
利用 Factory Methods 限制外部只能建立特定語意的實體：
*   **`createSuccessLog(...)`**: 建立「執行成功」的日誌實體。
*   **`createFailedLog(...)`**: 建立「執行失敗」的日誌實體。
*   **`createVetoedLog(...)`**: 建立「被強制中止 (Vetoed)」的日誌實體。

---

## 5. 聚合根 (Aggregate Root): `JobExecutionCheckpoint`

`JobExecutionCheckpoint` 是專門負責記錄排程任務執行中的「進度」與「中斷點」的聚合根。

### 領域設計理念
這是為了支援 Quartz Failover Recovery (災後重跑) 機制而設計，避免與 Append-Only 的日誌 (`ScheduleJobLog`) 混用。透過記錄任務執行的狀態與檢查點，讓中斷的批次任務可以在系統重啟後從失敗的節點繼續執行。

### 屬性定義
*   **`String jobId`**: 對應排程的唯一識別碼（通常為 `group.name`）。
*   **`String status`**: 目前狀態 (`RUNNING`, `SUSPENDED`, `COMPLETED`, `FAILED`)。
*   **`String lastCheckpoint`**: 業務中斷點 (例如：最後處理的 User ID, 或 Page Number)。
*   **`Integer totalSteps` & `Integer currentStep`**: 總步數與當前步數。
*   **`Boolean isRecovery`**: 是否為災後重試狀態。

### 業務行為 (Business Behaviors)
*   **`initialize(...)`**: 初始化中斷點記錄，狀態設為 `RUNNING`。
*   **`updateProgress(...)`**: 更新執行進度（當前步數與檢查點資訊）。
*   **`complete()`**: 將狀態標記為 `COMPLETED`。
*   **`fail()`**: 將狀態標記為 `FAILED`。
