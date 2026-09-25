# Quartz 的 JDBCJobStore 模式

JDBCJobStore 是 Quartz 將任務排程資料由「記憶體（RAM）」推進到「企業級分散式架構」的核心儲存實作。
其本質是：利用關聯式資料庫（RDBMS）的 ACID 特性、行級鎖（Row Lock）與持久化機制，充當所有排程節點的「全域狀態協調中心」。

## 一、核心實作類別：JobStoreTX vs JobStoreCMT

在設定 Quartz 的 JDBC 模式時，主要會接觸到以下實作類別，其核心差異在於「誰來管理資料庫交易（Transaction）」：

- **JobStoreTX（Quartz 自行管理交易）**
  - **機制**：Quartz 自己在每次對資料表進行查詢、改狀態、釋放鎖時呼叫 `connection.commit()` 或 `connection.rollback()`。
  - **適用場景**：獨立執行的 Java 應用程式，或不依賴外部 JTA/全域交易管理員的單純環境。

- **JobStoreCMT（容器管理交易 / Container-Managed Transactions）**
  - **機制**：Quartz 允許參與外部的 JTA/全域交易。此時通常需要設定兩個資料來源：一個參與外部交易（`dataSource`），另一個則用於內部非交易排程鎖操作（`nonTxDataSource`）。

- **LocalDataSourceJobStore（Spring Boot 預設整合）**
  - Spring Boot `spring-boot-starter-quartz` 預設使用的實作。它是 JobStoreCMT 的衍生版本，能無縫整合 Spring 的 `PlatformTransactionManager`，讓排程任務與業務邏輯共用 Spring 管理的 DataSource。

## 二、核心資料表結構與職責劃分

在資料庫中，Quartz 標準腳本會建立 11 張以 `QRTZ_` 為前綴的資料表，可依職責歸納為四大模組：

### 1. 定義中繼資料
- `QRTZ_JOB_DETAILS` : 儲存任務基本資訊（Job 名稱、群組、Job 實作類別、是否可恢復等）。
- `QRTZ_TRIGGERS` : 觸發器核心中繼資料（狀態、下次/上次觸發時間、優先順序等）。
- `QRTZ_CRON_TRIGGERS` : 儲存 CronTrigger 的 Cron 表示式與時區。
- `QRTZ_SIMPLE_TRIGGERS` : 儲存 SimpleTrigger 的重複次數、重複間隔時間。
- `QRTZ_BLOB_TRIGGERS` / `QRTZ_SIMPROP_TRIGGERS` : 儲存自訂或擴充型態的觸發器資料。

### 2. 狀態與執行紀錄
- `QRTZ_FIRED_TRIGGERS` : 記錄正在執行中（或剛觸發）的 Trigger 狀態，用來追蹤哪個節點正在跑什麼任務。
- `QRTZ_PAUSED_TRIGGER_GRPS` : 記錄哪些 Trigger 群組處於手動暫停狀態。

### 3. 叢集與分散式鎖
- `QRTZ_LOCKS` : 分散式悲觀鎖的核心表。預設存有 `TRIGGER_ACCESS` 與 `STATE_ACCESS`，透過 `SELECT ... FOR UPDATE` 達成節點互斥。
- `QRTZ_SCHEDULER_STATE` : 記錄叢集內各節點的心跳時間（`LAST_CHECKIN_TIME`）與存活狀態。

### 4. 月曆排除表
- `QRTZ_CALENDARS` : 儲存排除特定日期（如國定假日、非營業日）的月曆物件。

## 三、JDBCJobStore 的核心運作迴圈

在 JDBC 模式下，每個節點的 `QuartzSchedulerThread` 均以固定的迴圈節奏與資料庫互動，主要步驟如下:

1. **搶佔調度鎖**：
   執行緒開啟事務，執行 `SELECT * FROM QRTZ_LOCKS WHERE LOCK_NAME = 'TRIGGER_ACCESS' FOR UPDATE`。未搶到鎖的節點在此排隊等待。
2. **拉取待觸發任務**：
   查詢 `QRTZ_TRIGGERS`，找出條件符合且到達觸發窗口（`NEXT_FIRE_TIME <= now + idleWaitTime`）且狀態為 `WAITING` 的觸發器。
3. **推進狀態防重複**：
   將選中的 Trigger 狀態改為 `ACQUIRED`，隨即提交交易並釋放 `TRIGGER_ACCESS` 鎖。此時其他節點進來查詢時，看到的已經不是 `WAITING`，因而不會重複選取。
4. **準備執行（Fired）**：
   到達預定觸發瞬間，執行緒再次取得鎖，在 `QRTZ_FIRED_TRIGGERS` 寫入一筆代表「該節點正在執行此任務」的紀錄，將 Trigger 狀態更新為 `EXECUTING`，計算下一次執行時間回寫至 `NEXT_FIRE_TIME`，然後提交事務、交付內部 Worker 執行緒池執行業務邏輯。
5. **執行完成清理**：
   Worker 執行緒跑完業務邏輯後，刪除 `QRTZ_FIRED_TRIGGERS` 的對應紀錄；若為不重複任務則刪除 Trigger，若為定期任務則將狀態重設回 `WAITING`。

## 四、優點與代價（Trade-offs）

### 優點
- **零外部相依**：只要有關聯式資料庫，無需額外架設與維運 Redis、Zookeeper 或獨立調度中心。
- **強一致性保證**：依賴資料庫的 ACID 與行鎖，在排程獲取與狀態移轉上具有高度可靠性，不會因為記憶體快取不一致而產生任務漏跑。
- **天生具備持久與容災**：支援系統冷重啟、滾動更新（Rolling Update）以及無縫的節點崩潰接管（Failover）。

### 代價與局限
- **資料庫 I/O 與鎖競爭（Lock Contention）**：
  每個節點每隔數秒都在執行 `SELECT ... FOR UPDATE`。當節點數增加或任務密度極高（例如秒級排程）時，資料庫的 CPU 與連線池會承受顯著壓力。
- **資料表膨脹**：
  若歷史任務頻繁新增與刪除，或大量任務頻繁執行，`QRTZ_FIRED_TRIGGERS` 與 `QRTZ_TRIGGERS` 的資料表索引可能因頻繁寫入產生碎片，需定期維護。
- **非分散式計算框架**：
  JDBCJobStore 解決的是「誰來執行」的互斥與調度問題，並不支援將單一大任務自動拆分到多台機器做分片運算（Sharding）。

## 五、關鍵組態建議（Spring Boot / quartz.properties）

```properties
# 指定使用 JDBC 持久化
spring.quartz.job-store-type = jdbc

# 啟用叢集模式
spring.quartz.properties.org.quartz.jobStore.isClustered = true

# 叢集節點心跳頻率（毫秒）
spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval = 7500

# 判定錯過觸發的容忍閾值（毫秒）
spring.quartz.properties.org.quartz.jobStore.misfireThreshold = 60000

# 資料庫驅動委派實作（針對 MySQL InnoDB 優化鎖語法）
spring.quartz.properties.org.quartz.jobStore.driverDelegateClass = org.quartz.impl.jdbcjobstore.StdJDBCDelegate

# 每次取得觸發器的批次上限（預設為 1，調大可減少鎖競爭頻率，但需搭配執行緒池評估）
spring.quartz.properties.org.quartz.scheduler.batchTriggerAcquisitionMaxCount = 1
```
