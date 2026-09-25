# Quartz 的分散式鎖機制

Quartz 的分散式鎖機制與現代常見基於 Redis（Redlock）或 ZooKeeper 的實作不同，其本質是：**完全依賴關聯式資料庫（RDBMS）的行級悲觀鎖（Pessimistic Row Lock）與交易（Transaction）隔離性**，將資料庫當作全域分散式協調中心。

整個分散式鎖體系的核心落在 `QRTZ_LOCKS` 這張表，以及兩個最高頻運作的鎖名稱。

## 一、核心鎖載體：QRTZ_LOCKS 表

在資料庫初始化時，`QRTZ_LOCKS` 內建兩筆靜態資料。Quartz 並不是動態去為每一個 Job 或 Trigger 建立一張鎖表或一筆鎖紀錄，而是收斂在固定的全域行級鎖上：

1. **TRIGGER_ACCESS（排程調度互斥鎖）**：
   保護 Trigger 狀態流轉（如搶任務、指派執行緒、修改下次觸發時間）。只要涉及排程主迴圈的狀態讀寫，都必須先取得此鎖。
2. **STATE_ACCESS（叢集狀態管理鎖）**：
   保護叢集節點心跳與 Failover 救援邏輯。當有節點要登記心跳、檢查其他節點是否死亡、或接管清理孤兒任務時，必須先取得此鎖。

## 二、悲觀鎖的底層語法（SELECT ... FOR UPDATE）

在 Java 程式碼中，由 `DBSemaphore`（主要實作是 `StdRowLockSemaphore`）負責向資料庫索取鎖，其執行的關鍵 SQL 如下：

```sql
SELECT * FROM QRTZ_LOCKS 
WHERE SCHED_NAME = 'MyScheduler' 
  AND LOCK_NAME = 'TRIGGER_ACCESS' 
FOR UPDATE;
```

### 運作機制：
- **持有鎖（Lock Acquisition）**： 節點開啟一個資料庫事務（`BEGIN TRANSACTION`），執行上述 SQL。關聯式資料庫（如 MySQL InnoDB）會對該行加上互斥排他鎖（X Lock）。
- **阻擋其他節點（Contention）**： 此時其他平行節點的排程執行緒若也執行相同的 SQL，會在資料庫層被掛起阻塞（Blocked），等待該行的事務結束。
- **釋放鎖（Lock Release）**： 當持有鎖的節點完成狀態更新後，執行 `COMMIT` 或 `ROLLBACK`，該行級鎖自動釋放，資料庫隨後喚醒下一個排隊的節點。

## 三、關鍵設計：為什麼不用 Trigger 行鎖，而是單一鎖表？

很多人直覺會問：「為什麼不直接在 `QRTZ_TRIGGERS` 表的特定任務行上加鎖，而要大家一起擠在 `TRIGGER_ACCESS` 這一行？」這體現了 Quartz 在分散式鎖設計上的權衡：

1. **徹底根除分散式死鎖（Deadlock Prevention）**：
   排程器每次需要掃描「多筆」到達時間的 Triggers。若各節點直接在 `QRTZ_TRIGGERS` 透過複合條件加行鎖，由於各節點掃描順序、更新順序不同，極易引發多行交叉鎖定的資料庫死鎖（Deadlock）。使用單一的 `TRIGGER_ACCESS` 作為入口守門人，將臨界區存取嚴格序列化（Serialize），從根源消除了交叉死鎖。
2. **粗粒度鎖與細粒度更新的平衡**：
   為了不讓全域鎖成為嚴重效能黑洞，Quartz 採取「短平快」的事務設計：
   `搶到 TRIGGER_ACCESS 鎖` ➔ `查詢並將狀態由 WAITING 改為 ACQUIRED` ➔ `立刻 Commit 釋放鎖`。
   鎖的持有時間僅僅是幾次簡單的 SQL 讀寫（通常在數毫秒內），隨後的「業務邏輯執行」完全是在釋放鎖之後由 Worker 執行緒池非同步非阻塞運作。

## 四、任務並發控制：@DisallowConcurrentExecution

上述的 `TRIGGER_ACCESS` 是為了解決「多節點同時爭搶同一任務」的瞬時衝突；而業務上常見的「同一個任務在前一次還沒跑完前，下一次即使時間到了也不准跑」，Quartz 是如何透過鎖狀態實現的？

當 Job 加上 `@DisallowConcurrentExecution` 註解時：
- **觸發當下加鎖標記**： 節點在觸發任務並將狀態改為 `EXECUTING` 時，會同時將該 Job 對應的所有其他 Trigger 狀態更新為 `BLOCKED`。
- **排程迴圈略過**： 其他節點在定期輪詢可執行的 Triggers 時，查詢條件只鎖定 `TRIGGER_STATE = 'WAITING'`，因此處於 `BLOCKED` 的任務會被自動略過，不會被拉取執行。
- **完成後解鎖還原**： 前一次任務執行完畢後，執行節點再次取得 `TRIGGER_ACCESS` 鎖，將狀態從 `BLOCKED` 改回 `WAITING`，並依照 Misfire 策略校正下次執行時間。

## 五、與現代分散式鎖（Redis / Redlock）的架構對比

| 特性 | Quartz (RDBMS 悲觀鎖) | Redis 等現代分散式鎖 |
|---|---|---|
| **基礎設施相依** | 極低 (僅需關聯式資料庫) | 高 (需外掛 Redis 叢集) |
| **資料強一致性** | 極高 (具備 ACID 交易保證) | 較高 (極端情況下可能有腦裂風險) |
| **併發吞吐量效能** | 較低 (受限於 DB 連線與 I/O) | 極高 (純記憶體操作) |

## 六、架構選型總結

Quartz 的分散式鎖機制屬於典型的「資料庫悲觀併發控制」：如果系統的排程任務屬於**分鐘級、小時級或自訂定時任務**，這套機制成熟穩定，能在不增加任何基礎設施成本的情況下，提供極強的資料一致性保證。

但若系統存在大量**秒級或毫秒級的高頻調度**，單行 `FOR UPDATE` 的高頻競爭會迅速壓垮資料庫連線池與交易日誌，此時則應改用基於 Redis 延遲佇列（Delay Queue）或專屬的分散式任務調度平台（如 XXL-JOB、PowerJob）。
