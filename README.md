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

1. 新增一個 Job
>* 實作 Job 類別：建立一個繼承 org.quartz.Job 的類別。
>* 註冊 JobType：在 JobType 列舉中新增定義。
>* 適配器配置：在 QuartzAdapter 的 createJobDetail 中加入對應邏輯。

2. 透過 initializeTask 方法在 ScheduleJobRegistration 內註冊該排程  
註. 此方法具備冪等性 (Replace 模式)

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

## 運維監控與異常處理

**異常代碼定義**
>* INVALID_CRON (422): 使用者輸入的 Cron 格式錯誤（如：日與週同時指定）。
>* JOB_NOT_FOUND (404): 操作了不存在的任務識別碼。
>* ENGINE_ERROR (500): Quartz 引擎發生底層技術故障（如：資料庫連線中斷）。

**降級方案說明**
> 當調用 getJobInfoResources 獲取監控清單時，若 Quartz 引擎離線，系統會自動切換為 UNKNOWN (ENGINE_OFFLINE) 狀態，此時僅顯示資料庫中的靜態配置，確保管理介面不因技術故障而崩潰。
