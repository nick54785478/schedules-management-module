# 分散式排程任務管理 - 分散式鎖與叢集架構說明

本專案在處理「多節點同時執行排程」的情境時，經歷了架構上的演進。本文件將詳細說明系統中存在的兩種分散式鎖機制，以及目前的架構標準。

---

## 1. Quartz 原生叢集分散式鎖 (目前系統主力)

在分散式環境（多台伺服器部署同一個服務）下，為了避免同一個排程任務被多個節點重複執行，系統目前全面採用 **Quartz 內建的叢集機制 (Clustering)**。

### 核心設定檔
位於 `QuartzScheduleConfiguration.java`：
```java
// 必須設為 true，開啟叢集模式與分散式鎖
properties.put("org.quartz.jobStore.isClustered", "true");
// 各節點啟動時自動產生唯一識別碼 (如: DESKTOP-NICK_171487...)
properties.put("org.quartz.scheduler.instanceId", "AUTO");
// 叢集檢查心跳間隔 (15 秒)
properties.put("org.quartz.jobStore.clusterCheckinInterval", "15000");
```

### 運作原理 (基於資料庫的悲觀鎖)
1. **依賴 `QRTZ_LOCKS` 表**：Quartz 底層透過關聯式資料庫 (RDBMS) 的行級鎖 (`SELECT ... FOR UPDATE`) 來實現分散式鎖。
2. **搶佔機制**：當任務到達觸發時間時，所有節點都會嘗試去取得該任務的鎖。最先成功在資料庫獲得鎖的節點將執行該任務，其他節點則會被阻塞或直接忽略。
3. **心跳與故障轉移 (Failover)**：
   - 每個節點會依照 `clusterCheckinInterval` (目前設為 15 秒) 寫入心跳時間到 `QRTZ_SCHEDULER_STATE` 表。
   - 如果某個節點突然當機，其他節點在檢查心跳時會發現該節點超時。Quartz 會自動將當機節點「未完成的任務」轉移 (Failover) 到健康的節點上重新執行，確保高可用性 (HA)。

---

## 2. 業務層自定義分散式鎖 (目前已棄用/僅供參考)

在專案的 `com.example.demo.application.domain.distlock` 包中，保留了自定義的 `DistributedLock` 實體與相關適配器。這個機制最初是為了在業務層面手動控制鎖而設計的，但目前**已標記為棄用**，被 Quartz 原生機制所取代。

### 自定義鎖的設計巧思 (Design Pattern)
儘管已棄用，該機制仍展示了標準的 DB 鎖實作模式，位於 `DistributeLockManagerAdapter.java`：

1. **唯一約束防重 (Unique Constraint)**：
   資料表 `distributed_lock` 的 `lock_key` 設為 Unique。當多個節點同時執行 `insert` 搶鎖時，只有一個節點能成功，其他節點會拋出 ConstraintViolationException。
2. **過期與 TTL (Time To Live) 保護**：
   - 建立鎖時必須給定 `expiresAt` (過期時間)。
   - **搶佔過期鎖**：如果持有鎖的節點崩潰導致沒有執行 `releaseLock`，其他節點在搶鎖時，會利用 `updateIfExpired` 方法（`UPDATE ... WHERE lock_key = ? AND expires_at < ?`）來搶佔已經過期的死鎖。
3. **隔離級別 (Propagation.REQUIRES_NEW)**：
   為避免外層業務邏輯的 Transaction 影響鎖的即時釋放，獲取與釋放鎖的方法都使用了獨立的事務。

### 為什麼棄用？
1. **重複造輪子**：Quartz 的 `isClustered` 本身就已經非常穩定且久經考驗，手動維護自定義的 DB 鎖會增加不必要的複雜度與維護成本。
2. **效能考量**：Quartz 的執行緒池與底層的 Trigger 狀態機綁定更深，由 Quartz 自身處理鎖與狀態遷移 (State Transition) 會比在上層業務邏輯中攔截更具效率，且不易產生資料不一致 (如 Job 表與 Quartz 表狀態脫鉤) 的問題。

---

## 3. 架構總結

本專案的高可用性 (HA) 完全交由 **Spring `@Transactional` + Quartz Cluster** 負責：
* **狀態一致性**：業務操作（如暫停、更新 Cron）與 Quartz 引擎狀態共享同一個 DataSource，達成原子性。
* **執行唯一性**：由 Quartz 的 `QRTZ_LOCKS` 與心跳機制保障，確保同一個時間點，全網只有一個節點執行特定排程。
