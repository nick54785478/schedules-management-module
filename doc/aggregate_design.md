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
*   **`String name`**: 領域唯一識別名稱（如：`TAIWAN_HOLIDAY_2026`），對應 Quartz 引擎內的 Calendar Name。
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
*   **設計目的**: 將排程的多型狀態（Cron 定時任務 vs. OneTime 一次性任務）以及綁定的 Calendar 名稱封裝在一起。
*   **「純粹領域」設計原則**: 
    1. 採用 Tagged Union (多型退化為屬性標籤) 設計，透過 `ScheduleType` 來區分目前存放的是 Cron 字串還是精確的 `LocalDateTime`。
    2. 結合 JPA `@Embeddable` 將屬性扁平化映射至同一張表，避免關聯表帶來的複雜度。
    3. 保留了 `calendarName` 作為與 `ScheduleCalendar` 聚合根的 Soft Link (軟連結)，實現跨聚合的鬆耦合協作。
