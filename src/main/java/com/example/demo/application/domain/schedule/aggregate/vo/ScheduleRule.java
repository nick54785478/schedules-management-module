package com.example.demo.application.domain.schedule.aggregate.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * <h2>ScheduleRule (排程規則)</h2>
 * <p>
 * 此為領域層 (Domain Layer) 中代表「排程時間規則」的 Value Object (值物件)。<br>
 * 負責封裝排程的型態（常態性 Cron 或 一次性排程），並保證其內部資料的合法性。
 * </p>
 * 
 * <p>
 * <b>設計考量：</b><br>
 * 採用 Tagged Union (單一類別包含狀態標籤與多型欄位) 的設計手法。
 * 這麼做既能保留領域模型的封裝與多型語意，又能與 Spring Data JPA 的 {@code @Embeddable} 完美結合，
 * 將屬性扁平化映射到同一張資料表中，避免了複雜的 JSON 序列化或額外的關聯表。
 * </p>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 供 JPA 反射實例化使用
public class ScheduleRule {

    /**
     * 排程型態標籤，用以區分目前是 CRON 還是 ONE_TIME 任務。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type")
    private ScheduleType type;

    /**
     * Cron 表達式字串。<br>
     * 當 {@code type == ScheduleType.CRON} 時才具備意義。
     */
    @Column(name = "cron_expression")
    private String cronExpression;

    /**
     * 預計執行的精確時間。<br>
     * 當 {@code type == ScheduleType.ONE_TIME} 時才具備意義。
     */
    @Column(name = "execute_time")
    private LocalDateTime executeTime;

    /**
     * 綁定的日曆 key (用於排除假日等)。<br>
     * 若為 null，表示不綁定任何日曆。
     */
    @Column(name = "calendar_key")
    private String calendarKey;

    /**
     * 靜態工廠方法：建立一個基於 Cron 表達式的排程規則，不綁定日曆。
     * 
     * @param cron 合法的 Cron 字串
     * @return 封裝了 Cron 的排程規則
     * @throws NullPointerException 若 cron 為 null
     */
    public static ScheduleRule cron(String cron) {
        return cron(cron, null);
    }

    /**
     * 靜態工廠方法：建立一個基於 Cron 表達式的排程規則，並綁定指定的日曆。
     * 
     * @param cron 合法的 Cron 字串
     * @param calendarKey 日曆 key (允許為 null)
     * @return 封裝了 Cron 與日曆的排程規則
     * @throws NullPointerException 若 cron 為 null
     */
    public static ScheduleRule cron(String cron, String calendarKey) {
        Objects.requireNonNull(cron, "Cron expression cannot be null");
        ScheduleRule rule = new ScheduleRule();
        rule.type = ScheduleType.CRON;
        rule.cronExpression = cron;
        rule.calendarKey = calendarKey;
        return rule;
    }

    /**
     * 靜態工廠方法：建立一個一次性的排程規則。
     * 
     * @param time 預計執行的精確未來時間
     * @return 封裝了一次性時間的排程規則
     * @throws NullPointerException 若 time 為 null
     * @throws IllegalArgumentException 若指定的時間早於系統當前時間
     */
    public static ScheduleRule oneTime(LocalDateTime time) {
        Objects.requireNonNull(time, "Execute time cannot be null");
        if (time.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("一次性排程的執行時間必須大於當前時間 (Execute time must be in the future)");
        }
        ScheduleRule rule = new ScheduleRule();
        rule.type = ScheduleType.ONE_TIME;
        rule.executeTime = time;
        return rule;
    }

    /**
     * 領域判斷行為：判斷當前規則是否為「一次性任務」。
     * 
     * @return 若為一次性排程則回傳 true，反之回傳 false
     */
    public boolean isOneTime() {
        return ScheduleType.ONE_TIME.equals(this.type);
    }
}
