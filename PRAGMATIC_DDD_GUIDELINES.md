# 務實領域驅動設計 (Pragmatic DDD) 架構開發指南

本專案採用 **務實的領域驅動設計 (Pragmatic DDD)**。我們在「純粹的架構美學」與「敏捷的開發效率」之間取得平衡，允許部分框架依賴進入 Domain Layer，但同時設立了嚴格的邊界防線，確保領域邏輯（Business Logic）的純粹性不會被破壞。

為了讓團隊成員在開發時有明確的準則，特制定以下開發規範。

---

## 🟢 綠燈：允許進入 Domain Layer 的項目（節省轉換成本）

在不干擾業務邏輯判斷的前提下，我們容許「宣告式 (Declarative)」的依賴進入領域層，以避免過度設計與無謂的樣板程式碼 (Boilerplate)。

1. **Spring Data 通用介面 (`Page`, `Pageable`, `Sort`)**
   - **說明**：允許在 Domain Layer 的 Repository 介面中使用 Spring Data 的分頁物件。
   - **原因**：Spring Data 的 `org.springframework.data.domain` 原本即設計為通用的領域分頁語意。若堅持自定義 `DomainPage` 再互相轉換，會增加不必要的開發成本。
   - **例外**：但不能讓 `Pageable` 污染 Aggregate Root 的內部邏輯。

2. **JPA 的 ORM 映射註解 (`@Entity`, `@Table`, `@Column`, `@Embedded`)**
   - **說明**：允許將資料庫關聯註解直接標記在 Domain Aggregate 與 Value Object 上。
   - **原因**：避免將 Domain Model 與 Persistence Entity 切分成兩個類別再互相轉換 (Mapper)，大幅提升中小型專案的維護性。

3. **Lombok 的輕量註解 (`@Getter`, `@Builder`, `@NoArgsConstructor(access=PRIVATE)`)**
   - **說明**：允許使用 Lombok 減少程式碼量，特別是用於宣告私有建構子以滿足 JPA 反射需求。

4. **Spring 的領域事件機制**
   - **說明**：允許在 Aggregate 內部註冊 `@DomainEvents` 或是繼承 `AbstractAggregateRoot`，讓領域事件能順利拋出。

---

## 🔴 紅燈：絕對禁止進入 Domain Layer 的項目（踩線即破壞 DDD）

我們絕不容許「邏輯控制 (Control Logic)」、「基礎設施 (Infrastructure)」或「呈現層 (Presentation)」的依賴入侵 Domain。

1. **禁止在 Aggregate 上使用 Lombok 的 `@Data` 或 `@Setter`**
   - **原因**：這會直接破壞封裝，任何人都可以不經過業務規則驗證隨意修改屬性，使系統瞬間退化為「貧血模型 (Anemic Domain Model)」。
   - **規範**：屬性的變更必須透過業務方法（例如 `job.changeSchedule()`），內部若包含集合 (Collection)，應覆寫 Getter 並回傳 `Collections.unmodifiableSet(...)` 防止外部竄改。

2. **禁止 Web / API 物件進入 Domain**
   - **舉例**：`HttpServletRequest`, `ResponseEntity`, HTTP Status Code, `Session`。
   - **規範**：Domain 不能知道外層是透過 HTTP、gRPC 還是 Message Queue 呼叫的，它只能接收乾淨的 Command 物件或基本型別。

3. **禁止基礎設施的具體實作或連線物件**
   - **舉例**：Redis 的 `RedisTemplate`、Quartz 的 `JobExecutionContext` 或 `Trigger`、JDBC `Connection`。
   - **規範**：外部技術必須透過 Port（介面，如 `JobSchedulerPort`）進行隔離，Domain 絕不能直接依賴第三方技術框架的運作細節。

4. **禁止在 Aggregate 內部使用 `@Autowired`**
   - **原因**：Aggregate 應該是由 Repository 查詢出來的純記憶體物件 (POJO)，不該依賴 Spring 容器內的 Service 進行外部呼叫。
   - **規範**：若 Aggregate 需要外部資訊來完成商業邏輯判斷，應該由 Application Service 先查詢好資料後，當作「參數」傳遞給 Aggregate 的方法。

---

## 總結

**「只要這個框架依賴不會干擾到您編寫與測試純粹的商業邏輯，且能大幅減少無謂的轉換程式碼，那它就可以被容許。」**

這正是 Pragmatic DDD 在現代 Spring Boot 微服務中最舒適且主流的開發姿態。請團隊成員在維護排程模組時，嚴格遵守上述的紅綠燈規範。
