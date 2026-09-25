# JDBCJobStore 的 JobDataMap

JobDataMap 是 Quartz 中用來在「任務定義期」與「任務運行期」之間傳遞狀態與參數的資料載體。
由於 Quartz 的設計哲學是每次觸發時重新 new 一個 Job 實體（無狀態設計），一般類別屬性無法跨週期保留資料。JobDataMap 正是解決「如何將外部參數、業務識別碼安全帶入 Job 內部執行」的核心介面。

## 一、底層架構與本質

JobDataMap 的繼承體系非常直觀：
- 它繼承自 Quartz 的 `StringKeyMetaData`，底層本質上就是一個 `java.util.Map<String, Object>`（精確來說是封裝了 HashMap）。
- 它額外擴充了大量強型別的便捷取值方法，避免在業務程式碼中充斥著不安全的強制型別轉換（Cast）：
  - `getString(String key)`
  - `getLong(String key)` / `getInt(String key)`
  - `getBoolean(String key)`
  - `getDouble(String key)`

## 二、雙層架構：JobDetail 級別 vs Trigger 級別

在 Quartz 架構中，JobDetail 與 Trigger 各自擁有獨立的 JobDataMap。這是一對多排程中極為強大的特性：

- **JobDetail 的 JobDataMap**：
  適合存放該任務通用的靜態設定或全域預設值（例如：重試次數、系統環境標記）。
- **Trigger 的 JobDataMap**：
  適合存放本次觸發專屬的客製化參數。同一個 Job 實體被不同的 Trigger 觸發時，可以帶入不同情境的資料。

### 執行時的合併機制（MergedJobDataMap）
當進入 `Job.execute(JobExecutionContext context)` 時，Quartz 會將兩者的 Map 合併為一個 `context.getMergedJobDataMap()`。
**覆蓋規則**：如果 Trigger 與 JobDetail 擁有同名的 Key，Trigger 的數值會直接覆蓋 JobDetail 的數值。

## 三、狀態延續：@PersistJobDataAfterExecution

預設情況下，JobDataMap 存入資料庫後就是「唯讀的快照」。即使在 `execute()` 期間修改了 Map 的內容：
```java
context.getJobDetail().getJobDataMap().put("counter", currentCount + 1);
```
任務執行完畢後，資料庫中的 `JOB_DATA` 不會更新，下次執行依然會讀取到最初的初始值。

若業務情境需要「跨觸發週期累加狀態」（例如：記錄該任務總共累計處理了幾次、失敗了幾次）：
1. 必須在 Job 類別加上 `@PersistJobDataAfterExecution`。
2. Quartz 會在該次任務成功執行完畢後，將修改後的 JobDataMap 自動序列化並更新回資料庫（`QRTZ_JOB_DETAILS` 表）。

> [!WARNING]
> **強烈建議搭配 @DisallowConcurrentExecution**：因為多節點若平行修改同一份 JobDataMap，會產生嚴重的資料覆寫與 Race Condition。

## 四、Spring Boot 整合下的參數注入模式

在 Spring 生態中，讀取 JobDataMap 的參數主要有兩種主流寫法：

### 寫法 A：透過 JobExecutionContext 手動安全讀取（推薦）
```java
@Component
public class NotificationJob implements Job {
    @Autowired
    private NotificationService notificationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // 取得合併後的 Map (Trigger 優先覆蓋 JobDetail)
        JobDataMap dataMap = context.getMergedJobDataMap();
        
        String orderId = dataMap.getString("orderId");
        int retryCount = dataMap.getIntValue("retryCount"); // 原生安全轉型
        
        notificationService.send(orderId, retryCount);
    }
}
```

### 寫法 B：繼承 QuartzJobBean 使用 Setter 自動對映注入
Spring 提供的 `QuartzJobBean` 會在任務觸發時，反射掃描 JobDataMap 中的 Key，若 Job 類別有同名的 Setter 方法，Spring 會自動呼叫注入：
```java
@Component
public class OrderTimeoutJob extends QuartzJobBean {
    // 當 JobDataMap 內存在 key = "orderId" 時，Spring 自動調用此 setter
    private String orderId;
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    @Override
    protected void executeInternal(JobExecutionContext context) {
        // 此處可以直接使用 this.orderId，無須再從 context 撈取
        System.out.println("Processing order: " + this.orderId);
    }
}
```

## 五、實務開發的四大防坑原則

1. **僅使用 Primitive / String / 業務識別碼（ID）**：
   切勿把完整實體物件（如 `User`、`Order`）放入 Map，必須遵循「只傳 ID，執行當下再拿 ID 查 DB 最新狀態」原則，防範資料過期與反序列化版本地雷。
2. **啟用 useProperties=true**：
   在 `quartz.properties` 設定 `org.quartz.jobStore.useProperties = true`，強制檢查只允許純文字字串存入，消除 Java 二進位序列化風險。
3. **小心並行競爭**：
   如果啟用了 `@PersistJobDataAfterExecution`，務必加上 `@DisallowConcurrentExecution`，避免多個節點在同一瞬間試圖回寫狀態造成 DB 死鎖或資料覆寫。
4. **清理過期 Trigger 遺留資料**：
   若是動態生成、只跑一次的一次性 Trigger（如使用者設定 30 分鐘後提醒），需確保該 Trigger 執行完畢後會被 Quartz 清理，避免 `QRTZ_TRIGGERS` 殘留大量無用的 `JOB_DATA` 垃圾紀錄。

## 六、架構師的建議

**Q: 在 JDBCJobStore 下，JobDataMap 存放複雜物件會有什麼序列化陷阱？有何最佳實踐？**

在 JDBCJobStore 模式下，JobDataMap 會被持久化到資料庫的 `QRTZ_JOB_DETAILS` 或 `QRTZ_TRIGGERS` 表的 `JOB_DATA`（BLOB 型別）欄位中。
若不了解底層運作機制而直接在 JobDataMap 塞入自訂 Java 實體或複雜巢狀物件，上線後會踩到嚴重的維運與相容性地雷。

### 存放複雜物件的四大序列化陷阱
1. **Java 原生二進位序列化的版本地雷**：
   Quartz 預設使用標準 Java 序列化。若任務建立時將 DTO 存入，日後類別欄位或結構更動，反序列化時會拋出 `InvalidClassException`，導致任務永久卡死。
2. **跨服務與多模組下的類別載入隔離**：
   在微服務架構中，若排程器節點的 Classpath 內沒有該複雜物件的 Class 定義，反序列化時會噴 `ClassNotFoundException`。
3. **意外連帶序列化不可序列化的相依物件**：
   若複雜物件持有 Spring Bean（如 `OrderService`）或未實作 `Serializable` 的類別，會直接觸發 `NotSerializableException`，或是造成 BLOB 欄位急遽膨脹。
4. **資料庫內呈現二進位黑盒子**：
   存入 BLOB 的位元組無法透過 SQL Client 直觀閱讀或修改。維運排查時只能看到十六進位亂碼，維運成本極高。

### 核心最佳實踐（Best Practices）

1. **「只傳識別碼（ID），不傳實體資料」（黃金法則）**
   不要把整包資料或業務狀態當作 Snapshot 塞進排程中，JobDataMap 應僅充當「觸發指示指針」。在 `execute` 方法中拿著 ID 即時查詢當前最新狀態。
2. **強制僅使用原生基礎型別或字串（String-Only 策略）**
   開啟 `useProperties=true`，徹底消除 Java 原生序列化的版本相容性風險。
3. **複雜結構改採 JSON 字串儲存（JSON Payload）**
   若非得傳遞結構化配置，請透過 Jackson 或 Gson 將物件轉成 JSON String 再存入，容錯性極高且高度透明。