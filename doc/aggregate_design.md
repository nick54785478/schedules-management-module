# JPA Entity 作為領域模型 (Domain Model) 設計指南

本文件旨在詳細說明若在系統架構中選擇採用**「務實派領域驅動設計 (Pragmatic DDD)」**——即**直接以 JPA Entity 作為領域模型（Aggregate Root / Entity）**的做法、設計原則與具體實踐指引。

---

## 目錄
1. [架構思維：務實派 DDD vs 純粹六角架構](#一-架構思維務實派-ddd-vs-純粹六角架構)
2. [實踐原則：如何避免 JPA 實體退化為貧血模型](#二-實踐原則如何避免-jpa-實體退化為貧血模型)
3. [核心實體之領域化設計詳解](#三-核心實體之領域化設計詳解)
   - [3.1 ScheduledJobJpaEntity (排程任務聚合根)](#31-scheduledjobjpaentity-排程任務聚合根)
   - [3.2 ScheduleCalendarJpaEntity (排程日曆聚合根)](#32-schedulecalendarjpaentity-排程日曆聚合根)
   - [3.3 ScheduleJobLogJpaEntity (執行歷史審計實體)](#33-schedulejoblogjpaentity-執行歷史審計實體)
   - [3.4 JobExecutionCheckpointJpaEntity (中斷點聚合根)](#34-jobexecutioncheckpointjpaentity-中斷點聚合根)
4. [數值物件 (Value Objects) 與 JPA 映射機制](#四-數值物件-value-objects-與-jpa-映射機制)
5. [兩種架構風格決策矩陣 (Decision Matrix)](#五-兩種架構風格決策矩陣-decision-matrix)

---

## 一、 架構思維：務實派 DDD vs 純粹六角架構

在後端架構演進中，對於 Domain 與持久化實體的邊界通常有兩種主流取捨：

```
【純粹六角架構 (Pure Hexagonal)】                 【務實派 DDD (JPA Entity as Domain)】
+------------------------------------+           +------------------------------------+
| Domain Model (純 Java, 無框架侵入)  |           | Rich JPA Entity (聚合根兼 ORM 實體)  |
+-----------------+------------------+           |  - 帶有 JPA 註解 (@Entity, @Table)  |
                  │ Assembler / Mapper           |  - 同時封裝業務規則與狀態變更方法     |
                  ▼                              +-----------------+------------------+
+------------------------------------+                             │ Spring Data JPA
| JPA Entity (純 DB 欄位映射載體)     |                             ▼
+------------------------------------+           +------------------------------------+
                                                 | Database Table                     |
                                                 +------------------------------------+
```

### 為什麼考慮讓 JPA Entity 兼任 Domain Model？
- **降低維護成本**：省去在 `Domain Model` 與 `JpaEntity` 之間頻繁轉換的 Assembler/Mapper 程式碼，消除大量的膠水代碼（Boilerplate）。
- **開發敏捷**：在中小型專案、技術基礎模組或業務變動頻繁的微服務中，能夠以極高的效率落實物件導向封裝，同時享有 Spring Data JPA 的強大功能（如 Dirty Checking、自動樂觀鎖、關聯載入）。
- **關鍵挑戰**：必須嚴格防範 JPA 實體淪為只有 Getter/Setter 的**貧血模型 (Anemic Domain Model)**。

---

## 二、 實踐原則：如何避免 JPA 實體退化為貧血模型

當 JPA Entity 充當領域模型時，必須落實以下四大防護手段：

### 1. 嚴格封閉 Setter (Encapsulation)
- **禁止公開 `@Setter`**：嚴禁在 Class 層級直接標註無約束的 `@Setter` 或 `@Data`。
- **保護無參建構子**：JPA 規範要求必須有預設建構子，應將其可見度限縮為 `protected`，防止外部隨意 `new` 出狀態不合法的空實體：
  ```java
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  ```

### 2. 工廠方法確保聚合完整性 (Invariant Enforcement)
- 外部建立新實體一律強制透過靜態工廠方法（如 `register()`, `create()`），確保初始狀態（如 UUID、初始狀態 `NORMAL`、預設時間戳記）在誕生時即處於合法狀態。

### 3. 業務語意方法替代技術操作 (Rich Behaviors)
- 實體內部只暴露具備業務語意的行為方法（如 `pause()`, `resume()`, `updateSchedule()`），並在方法內部實施前置條件檢核（Preconditions）。

### 4. 防禦性複製與集合保護 (Defensive Copying)
- 若聚合根內部包含集合屬性（如排除日期 `Set<LocalDate>`），對外 Getter 必須回傳 `Collections.unmodifiableSet(...)`，異動集合必須呼叫聚合根專屬的領域方法，防止外部繞過聚合根直接竄改內部狀態。

---

## 三、 核心實體之領域化設計詳解

### 3.1 `ScheduledJobJpaEntity` (排程任務聚合根)
負責管理單一排程任務的完整生命週期與狀態機轉換。

```java
@Entity
@Table(name = "schedule_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledJobJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_identifier", unique = true, nullable = false)
    private String jobIdentifier; // 業務唯一識別碼 (UUID)

    @Column(name = "job_name", nullable = false)
    private String name;

    @Column(name = "job_group", nullable = false)
    private String group;

    @Column(name = "job_type", nullable = false)
    private String jobType; // 對應 Spring Bean Name

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status; // NORMAL, PAUSED, STOPPED, COMPLETED

    @Embedded
    private ScheduleRuleJpaEmbeddable rule; // 封裝 Cron / OneTime 與 Calendar 規則

    // --- 工廠方法 (Factory Method) ---
    public static ScheduledJobJpaEntity register(String name, String group, String jobType, 
                                                 ScheduleRuleJpaEmbeddable rule) {
        ScheduledJobJpaEntity entity = new ScheduledJobJpaEntity();
        entity.jobIdentifier = UUID.randomUUID().toString();
        entity.name = Objects.requireNonNull(name, "任務名稱不可為空");
        entity.group = Objects.requireNonNull(group, "任務群組不可為空");
        entity.jobType = Objects.requireNonNull(jobType, "Job Bean 名稱不可為空");
        entity.rule = Objects.requireNonNull(rule, "排程規則不可為空");
        entity.status = JobStatus.NORMAL; // 初始狀態
        return entity;
    }

    // --- 業務行為方法 (Business Behaviors) ---

    /** 暫停排程：只有 NORMAL 狀態允許暫停 */
    public void pause() {
        if (this.status != JobStatus.NORMAL) {
            throw new IllegalStateException("只有 NORMAL 狀態之排程方可暫停，目前狀態為: " + this.status);
        }
        this.status = JobStatus.PAUSED;
    }

    /** 恢復排程：只有 PAUSED 狀態允許恢復 */
    public void resume() {
        if (this.status != JobStatus.PAUSED) {
            throw new IllegalStateException("只有 PAUSED 狀態之排程方可恢復，目前狀態為: " + this.status);
        }
        this.status = JobStatus.NORMAL;
    }

    /** 更新排程規則：已停止或已完成之排程拒絕異動 */
    public void updateSchedule(ScheduleRuleJpaEmbeddable newRule) {
        if (this.status == JobStatus.STOPPED || this.status == JobStatus.COMPLETED) {
            throw new IllegalStateException("已停止或已完成之任務無法變更排程時間");
        }
        this.rule = Objects.requireNonNull(newRule, "新排程規則不可為空");
    }

    /** 綁定或解除排除日曆 */
    public void bindCalendar(String calendarKey) {
        this.rule = this.rule.withCalendarKey(calendarKey);
    }
}
```

---

### 3.2 `ScheduleCalendarJpaEntity` (排程日曆聚合根)
負責維護全域黑名單例外日期（如國定假日），並能被多個任務共用。

```java
@Entity
@Table(name = "schedule_calendar")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleCalendarJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "calendar_key", unique = true, nullable = false)
    private String key;

    @Column(name = "description")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "schedule_calendar_excluded_dates", joinColumns = @JoinColumn(name = "calendar_id"))
    @Column(name = "excluded_date")
    private Set<LocalDate> excludedDates = new HashSet<>();

    // --- 工廠方法 ---
    public static ScheduleCalendarJpaEntity create(String key, String description) {
        ScheduleCalendarJpaEntity entity = new ScheduleCalendarJpaEntity();
        entity.id = UUID.randomUUID();
        entity.key = Objects.requireNonNull(key, "日曆 key 不可為空");
        entity.description = description;
        return entity;
    }

    // --- 防禦性讀取 (Defensive Read) ---
    public Set<LocalDate> getExcludedDates() {
        return Collections.unmodifiableSet(this.excludedDates);
    }

    // --- 業務行為方法 ---
    public void addExcludedDate(LocalDate date) {
        Objects.requireNonNull(date, "排除日期不可為空");
        if (this.excludedDates.contains(date)) {
            throw new IllegalArgumentException("日期已存在於黑名單中: " + date);
        }
        this.excludedDates.add(date);
    }

    public void removeExcludedDate(LocalDate date) {
        Objects.requireNonNull(date, "欲移除的日期不可為空");
        this.excludedDates.remove(date);
    }
}
```

---

### 3.3 `ScheduleJobLogJpaEntity` (執行歷史審計實體)
專注於記錄 Quartz 引擎的執行軌跡（Append-Only），不包含複雜狀態變更。

- **設計要點**：
  - 完全由靜態工廠方法驅動：`createSuccessLog()`, `createFailedLog()`, `createVetoedLog()`。
  - 欄位完全不可變（無任何 Setter），建立後直接持久化至 `schedule_job_log` 表。
  - 與 `ScheduledJobJpaEntity` 不建立關聯外鍵，完全以純字串 (`jobName`, `jobGroup`) 解耦，確保日誌膨脹時不影響主排程的載入效能。

---

### 3.4 `JobExecutionCheckpointJpaEntity` (中斷點聚合根)
專門支援長時批次任務在節點崩潰後能進行 **Failover 斷點續跑**。

- **業務職責**：
  - `initialize(jobId, totalSteps)`：初始化步驟計數器。
  - `updateProgress(lastCheckpoint, currentStep)`：每次處理完一個批次即更新檢查點標記。
  - `complete()` / `fail()`：記錄最終結束狀態。

---

## 四、 數值物件 (Value Objects) 與 JPA 映射機制

在 JPA Entity 當作領域模型的做法中，數值物件（Value Objects）可以透過兩種方式優雅映射：

### 1. `@Embeddable` 嵌入式實體 (以 `ScheduleRule` 為例)
採用 **Tagged Union (標記聯集)** 設計，將 Cron 與 OneTime 狀態扁平化存放於同一張資料表中：

```java
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleRuleJpaEmbeddable {

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    private ScheduleType scheduleType; // CRON 或 ONE_TIME

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "execute_time")
    private LocalDateTime executeTime;

    @Column(name = "calendar_key")
    private String calendarKey; // Soft Link (軟連結)

    @Column(name = "requests_recovery", nullable = false)
    private boolean requestsRecovery;

    public static ScheduleRuleJpaEmbeddable cron(String cron, String calendarKey, boolean recovery) {
        return new ScheduleRuleJpaEmbeddable(ScheduleType.CRON, cron, null, calendarKey, recovery);
    }

    public static ScheduleRuleJpaEmbeddable oneTime(LocalDateTime time, boolean recovery) {
        return new ScheduleRuleJpaEmbeddable(ScheduleType.ONE_TIME, null, time, null, recovery);
    }

    public ScheduleRuleJpaEmbeddable withCalendarKey(String newCalendarKey) {
        return new ScheduleRuleJpaEmbeddable(this.scheduleType, this.cronExpression, this.executeTime, newCalendarKey, this.requestsRecovery);
    }
}
```

### 2. 跨聚合軟連結 (Soft Link vs Hard Foreign Key)
- **原則**：在 `ScheduledJobJpaEntity` 中，不要使用 `@ManyToOne ScheduleCalendarJpaEntity` 進行強關聯映射。
- **作法**：僅記錄 `calendar_key` (String)，透過軟連結進行邏輯關聯。
- **優勢**：各自擁有獨立的資料生命週期，刪除或修改日曆時不會引發複雜的 Cascade 級聯鎖定問題。

---

## 五、 兩種架構風格決策矩陣 (Decision Matrix)

| 評估維度 | 純粹六角架構 (目前主專案結構) | 務實派 DDD (JPA Entity 即 Domain) |
| :--- | :--- | :--- |
| **技術依賴** | 領域層為純 Java，完全零框架侵入 | 領域實體依賴 `@Entity`, `@Id`, Hibernate 等技術註解 |
| **物件轉換成本** | 高 (需透過 Adapter/Assembler 與 DTO 轉換) | 極低 (Entity 直通 Service，無冗餘轉換層) |
| **封裝難度** | 高 (需維持兩套模型的一致性) | 中 (需自覺封閉 Setter，依賴規範約束) |
| **適用專案規模** | 大型企業級核心領域、多技術棧移植系統 | 中小型專案、技術基礎模組、獨立微服務 |
| **持久化靈活性** | 極高 (未來更換 Mongo、Redis 時 Domain 零異動) | 較低 (深度綁定關聯式資料庫與 JPA) |

### 總結建議
若團隊希望在中小型專案或微服務內部採用 **JPA Entity 兼做 Domain** 的模式，務必恪守**「封閉 Setter、工廠建置、業務語意方法、防禦性集合複製」**四大原則，如此便能兼得「領域模型的高內聚封裝」與「Spring Data JPA 的高開發效率」。
