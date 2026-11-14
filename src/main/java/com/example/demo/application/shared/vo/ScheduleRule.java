package com.example.demo.application.shared.vo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * <h2>ScheduleRule (Value Object)</h2>
 * <p>
 * 排程時間規則數值物件，負責封裝排程的型態（CRON 或 ONE_TIME）與執行時間設定。
 * </p>
 */
public class ScheduleRule {

    private final ScheduleType type;
    private final String cronExpression;
    private final LocalDateTime executeTime;
    private final String calendarKey;
    private final boolean requestsRecovery;

    private ScheduleRule(ScheduleType type, String cronExpression, LocalDateTime executeTime, String calendarKey, boolean requestsRecovery) {
        this.type = type;
        this.cronExpression = cronExpression;
        this.executeTime = executeTime;
        this.calendarKey = calendarKey;
        this.requestsRecovery = requestsRecovery;
    }

    public static ScheduleRule cron(String cron, boolean requestsRecovery) {
        return cron(cron, null, requestsRecovery);
    }

    public static ScheduleRule cron(String cron, String calendarKey, boolean requestsRecovery) {
        Objects.requireNonNull(cron, "Cron expression cannot be null");
        return new ScheduleRule(ScheduleType.CRON, cron, null, calendarKey, requestsRecovery);
    }

    public static ScheduleRule oneTime(LocalDateTime time, boolean requestsRecovery) {
        Objects.requireNonNull(time, "Execute time cannot be null");
        if (time.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("一次性排程的執行時間必須大於當前時間 (Execute time must be in the future)");
        }
        return new ScheduleRule(ScheduleType.ONE_TIME, null, time, null, requestsRecovery);
    }

    public static ScheduleRule of(ScheduleType type, String cronExpression, LocalDateTime executeTime, String calendarKey, boolean requestsRecovery) {
        return new ScheduleRule(type, cronExpression, executeTime, calendarKey, requestsRecovery);
    }

    public boolean isOneTime() {
        return ScheduleType.ONE_TIME.equals(this.type);
    }

    public ScheduleType getType() {
        return type;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public LocalDateTime getExecuteTime() {
        return executeTime;
    }

    public String getCalendarKey() {
        return calendarKey;
    }

    public boolean isRequestsRecovery() {
        return requestsRecovery;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScheduleRule that)) return false;
        return requestsRecovery == that.requestsRecovery &&
                type == that.type &&
                Objects.equals(cronExpression, that.cronExpression) &&
                Objects.equals(executeTime, that.executeTime) &&
                Objects.equals(calendarKey, that.calendarKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, cronExpression, executeTime, calendarKey, requestsRecovery);
    }
}
