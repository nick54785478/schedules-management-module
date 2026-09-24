# 分散式排程任務管理模組

本模組基於 Spring Boot 4.0.6 與 Quartz Scheduler 構建，旨在提供一個高可用、強一致性且具備領域驅動設計（DDD）特性的排程管理系統。

## 核心架構設計

本模組採用 六角架構（Ports and Adapters），確保業務邏輯與技術實現（Quartz）完全解耦：

**Domain Layer**: 
> 包含 ScheduledJob 聚合根與 CronExpression 值物件。

**Application Layer**:
>* ScheduledJobApplicationService 負責協調資料庫持久化與 Quartz 引擎狀態。
>* 透過 CronParserPort 確保進入領域層的數據絕對合法。

**Infrastructure Layer**:
>* QuartzAdapter: 實現 JobSchedulerPort，負責具體的 Quartz 操作。
>* QuartzCronParser: 實現 CronParserPort，利用 Quartz 內建邏輯進行語法檢核。

## 關鍵技術特性

**1. 分散式叢集與高可用 (HA)**
透過資料庫鎖機制實現多節點協作，防止任務重複執行並支援故障轉移（Failover）。
>* 動態識別：各節點啟動時自動生成 instanceId。
>* 心跳監測：每 15 秒向 QRTZ_SCHEDULER_STATE 表更新狀態。

**2. 事務強一致性 (Transactional Integrity)**
本模組採用 共享資料源 (Shared DataSource) 策略：
>* 原子操作：業務資料表（自定義 Job 表）與 Quartz 系統表（QRTZ_）共用同一個 JDBC 連線。
>* 同步回滾：當更新排程（如 pauseTask 或 updateCron）時，若任一環節失敗，兩邊的資料將同步回滾，防止狀態偏差。

**3. 領域保護機制 (The Gatekeeper)**
> 採用 純粹派 DDD 設計，透過 CronParserPort 攔截無效的 Cron 表達式，確保 ScheduledJob 聚合根在記憶體中始終處於合法狀態。

## 開發者指南

### 建立一支排程工作的步驟

1. 在 Scheduler 內建置相關排程工作
>* **實作 Job 類別**：建立一個實作 `org.quartz.Job` 的類別，並標註 `@Component` 由 Spring 託管。
>* **架構定位 (Inbound Adapter)**：在六角架構中，Quartz 的 `Job` 扮演的是 **驅動端適配器 (Inbound Adapter)**，職責等同於 API Controller。
>  * **✅ 允許介接**：只能注入並呼叫 **Application Service (Use Case / Inbound Port)**，將參數轉換為 Command，並由應用層統一管理事務邊界 (`@Transactional`)。
>  * **❌ 嚴禁介接**：絕對禁止直接注入 Outbound Port (如 DB Repository)、禁止在 `execute` 方法內直接編寫業務邏輯，也禁止由 Job 自行管理 Transaction。
>* **動態解析機制**：根據 `JobSchedulerAdapter.lookupJobClass` 的設計，系統會直接透過 **Bean Name** 來建置對應的 Job 類別。這避免了在資料庫硬編碼類別路徑 (Class Path)，包名更動時系統依然穩定，達到 Domain 與技術實作解耦。

2. 註冊該排程至系統中 (二擇一)
>* **系統啟動時註冊**：透過 `ScheduleJobRegistration` 內的 `initializeTask` 方法註冊（此方法具備冪等性 Replace 模式），並將 `jobType` 參數指定為您的 Bean Name。
>* **透過 API 動態新增**：呼叫 `POST /jobs/create`，並在請求的 `jobType` 傳入您的 Bean Name。

3. 自定義排程監聽器 (Observer Pattern)
>* 實作介面：建立類別並實作 `com.example.demo.application.shared.listener.JobStatusListener` 介面。
>* 業務隔離：純技術用途的監聽器（如耗時統計）可放於 `infra`，若牽涉業務邏輯（如失敗寄信、更新業務表）可放於 `application/service` 或 `iface`。
>* 集中註冊：在 `ScheduleJobRegistration.java` (設定層) 中，利用 `jobScheduler.registerJobListener()` 將自定義監聽器掛載至對應的 Job 上，完成解耦。

## API 接口說明

所有 API 基礎路徑為 /jobs。

**1. 查詢所有排程狀態**
>* Method: GET
>* Path: /status
>* 描述: 聚合資料庫配置與 Quartz 即時運行狀態。
>* 降級行為: 若引擎離線，state 欄位將顯示 UNKNOWN (ENGINE_OFFLINE)。
>* 響應: List<ScheduleJobView>

**2. 暫停排程任務**
>* Method: POST
>* Path: /pause/{jobId}
>* 描述: 根據 JobId 暫停任務。
>* 一致性: 採原子操作，若引擎同步失敗則回滾資料庫狀態。

**3. 重啟排程任務**
>* Method: POST
>* Path: /resume/{jobId}
>* 描述: 恢復已暫停的任務進入等待執行狀態。

**4. 更新 Cron 表達式**
>* Method: POST
>* Path: /update-cron
>* 描述: 修改特定任務的執行週期。

**5. 新增排程任務**
>* Method: POST
>* Path: /create
>* 描述: 註冊一個新的排程任務到系統與 Quartz 引擎中。需傳遞任務名稱、群組、Cron 表達式以及對應的 Bean Name (`jobType`)。
>* 冪等性: 採用 Replace 模式，若相同 `name` 與 `group` 的排程已存在則會進行覆寫。

## 運維監控與執行日誌

**1. 排程執行日誌持久化 (Job Execution Logging)**
> 系統內建全域的排程歷史紀錄功能，可將每一次排程的成功、失敗、中止事件記錄於資料庫 `schedule_job_log` 表中。
>* **隔離的事務設計**: 核心採用 `REQUIRES_NEW` 開啟獨立事務，確保即使您的業務邏輯發生嚴重例外導致 Transaction Rollback，日誌依然能成功保存，精準記錄失敗原因。
>* **執行緒安全 (Thread-Safety)**: 底層使用 `ThreadLocal` 綁定 Quartz Worker Thread 記錄任務起始時間，在高併發執行下依然保證耗時計算 (`durationMs`) 的絕對正確。
>* **功能開關 (Feature Toggle)**: 預設關閉。可透過配置檔 `app.schedule.job-log.enabled=true` 開啟。關閉狀態下會直接 Return，達成真正的零效能負擔 (Zero Overhead)。
**2. 分散式叢集下的 Listener 行為 (Distributed Listener Behavior)**
> 在多節點的叢集環境中，Quartz 的 Listener 具備以下特性：
>* **叢集唯一觸發保證**: 依賴資料庫 `QRTZ_LOCKS` 行鎖機制，同一個排程任務在同一時間點只會被「一台」機器搶得執行權。因此，全域 Listener (如 `GlobalJobListener`, `PersistJobLogListener`) **只會在那一台執行的機器上被觸發一次**，不會產生併發重複寫入日誌的問題。
>* **業務冪等性需自行控制 (重要)**: 若執行任務的機器中途崩潰 (Crash) 且排程配置了 `RequestsRecovery`，Quartz 會將任務轉交由另一台機器重新執行，此時 Listener 會被**再次觸發**。Quartz 原生不保證 Listener 內部業務的絕對冪等，若於 Listener 內實作敏感業務 (如發信、扣款)，必須自行實作冪等控制 (如 Unique Key 或 Redis Check)。

**3. 異常代碼定義**
>* INVALID_CRON (422): 使用者輸入的 Cron 格式錯誤（如：日與週同時指定）。
>* JOB_NOT_FOUND (404): 操作了不存在的任務識別碼。
>* ENGINE_ERROR (500): Quartz 引擎發生底層技術故障（如：資料庫連線中斷）。

**降級方案說明**
> 當調用 getJobInfoResources 獲取監控清單時，若 Quartz 引擎離線，系統會自動切換為 UNKNOWN (ENGINE_OFFLINE) 狀態，此時僅顯示資料庫中的靜態配置，確保管理介面不因技術故障而崩潰。
