# 領域模型設計 (Domain Model Design) - ScheduledJob 聚合

本文件詳細說明 `schedules-management-module` 中排程模組的核心領域模型（Domain Model），特別是聚合根（Aggregate Root）與數值物件（Value Object）的設計理念與邊界。

## 1. 聚合根 (Aggregate Root): `ScheduledJob`

`ScheduledJob` 是排程管理模組唯一且核心的聚合根，負責維護單個排程任務的完整生命週期與業務規則。

### 屬性定義
*   **`JobId jobId`**: 領域唯一識別碼（UUID），用於系統內部參考。
*   **`String name`**: 業務名稱，與 `group` 共同組成 Quartz 引擎中的唯一鍵 (`JobKey`)。
*   **`String group`**: 業務分組。
*   **`String jobType`**: 技術對應標籤，實際上儲存的是 Spring 容器中 `org.quartz.Job` 實作的 **Bean Name**。
*   **`CronExpression cron`**: 執行週期，以數值物件 (VO) 的形式存在。
*   **`JobStatus status`**: 業務狀態，包含 `NORMAL`（正常）、`PAUSED`（暫停）、`STOPPED`（停止）。

### 業務行為 (Business Behaviors)
所有的狀態變更都必須透過聚合根暴露的方法進行，確保狀態一致性：
*   **`register(...)` (Factory Method)**: 工廠方法，用於在建立新排程時，自動產生 `JobId` 並將狀態初始化為 `NORMAL`。
*   **`pause()`**: 暫停排程。只有當狀態為 `NORMAL` 時才允許變更為 `PAUSED`。
*   **`resume()`**: 恢復排程。只有當狀態為 `PAUSED` 時才允許變更為 `NORMAL`。
*   **`changeSchedule(String newCron)` / `updateCron(...)`**: 更新執行週期。領域規則限制：若狀態為 `STOPPED` (已停止)，則拋出例外拒絕修改。

---

## 2. 數值物件 (Value Objects)

為了避免 Primitive Obsession（基本型別偏執），領域層使用了數值物件來包裝特定的概念。

### `JobId` (Record)
*   **設計目的**: 將 UUID 字串包裝為專屬型別，增強型別安全性 (Type Safety)。在方法傳遞時，可以明確知道這是一個 Job ID 而不是普通的 String。

### `JobStatus` (Enum)
*   **狀態枚舉**: 定義了 `NORMAL`、`PAUSED`、`STOPPED`，限制了排程狀態的可能值，避免無效狀態的產生。

### `CronExpression` (Record)
*   **設計目的**: 封裝 Cron 語法字串。
*   **「純粹領域」設計原則**:
    仔細觀察會發現，`CronExpression` 內部並沒有撰寫複雜的正則表達式或呼叫 Quartz 的工具類別來檢核格式。這是因為：
    1. **避免技術污染**: 領域層 (Domain) 應該是純粹的 Java 程式碼，不應依賴外部基礎設施 (如 Quartz 函式庫)。
    2. **由 Application 層把關**: 系統設計了 `CronParserPort`。在 Application Service 接收到外部請求時，會先透過這個 Port（實作端會呼叫 Quartz 進行校驗）檢查語法。只有**合法**的字串兩者才會被轉換成 `CronExpression` 傳入領域層。
    3. **結果**: 確保了只要 `CronExpression` 被建立出來，它代表的就「一定」是一個合法的 Cron 週期，將髒資料完美阻絕於領域層之外。
