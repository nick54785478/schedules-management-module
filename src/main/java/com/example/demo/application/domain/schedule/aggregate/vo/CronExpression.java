package com.example.demo.application.domain.schedule.aggregate.vo;

import java.util.Objects;

import jakarta.persistence.Embeddable;

/**
 * <h2>排程週期表達式 (Value Object)</h2>
 * <p>
 * 領域層內用於封裝 Quartz Cron 格式字串的數值物件。
 * 保證傳入的值域基本不為 null，將格式驗證的職責交由外部的防腐層 (CronParserPort) 處理。
 * </p>
 * 
 * @param value Cron 表達式字串
 */
@Embeddable
public record CronExpression(String value) {

	/**
	 * 建構子：僅做最基礎的非空檢查，複雜邏輯交由防腐層校驗。
	 */
	public CronExpression {
		Objects.requireNonNull(value, "Cron value cannot be null");
	}
}