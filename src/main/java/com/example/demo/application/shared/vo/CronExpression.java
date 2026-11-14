package com.example.demo.application.shared.vo;

import java.util.Objects;

/**
 * <h2>CronExpression (Value Object)</h2>
 * <p>
 * 用於封裝 Quartz Cron 格式字串的數值物件。
 * </p>
 *
 * @param value Cron 表達式字串
 */
public record CronExpression(String value) {

    public CronExpression {
        Objects.requireNonNull(value, "Cron value cannot be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron value cannot be blank");
        }
    }
}
