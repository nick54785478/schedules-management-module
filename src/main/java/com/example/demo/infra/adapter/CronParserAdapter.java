package com.example.demo.infra.adapter;

import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

import com.example.demo.application.port.out.CronParserPort;
import com.example.demo.application.shared.exception.InvalidCronException;

import lombok.extern.slf4j.Slf4j;

/**
 * <h2>CronParserAdapter (防腐層實作)</h2>
 * <p>
 * 實作 {@link CronParserPort} 介面，封裝 Quartz 專屬的 {@link CronExpression} 語法檢查機制。
 * </p>
 */
@Slf4j
@Component
public class CronParserAdapter implements CronParserPort {

	@Override
	public com.example.demo.application.shared.vo.CronExpression parse(String cron) {
		if (!isValid(cron)) {
			log.warn("Cron 表達式校驗失敗: {}", cron);
			throw new InvalidCronException("不合法的 Quartz Cron 表達式: " + cron);
		}
		return new com.example.demo.application.shared.vo.CronExpression(cron);
	}

	@Override
	public boolean isValid(String cron) {
		if (cron == null || cron.trim().isEmpty()) {
			return false;
		}
		return CronExpression.isValidExpression(cron);
	}
}
