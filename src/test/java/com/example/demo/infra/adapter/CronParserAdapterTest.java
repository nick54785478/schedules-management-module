package com.example.demo.infra.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.demo.application.domain.schedule.aggregate.vo.CronExpression;
import com.example.demo.application.shared.exception.InvalidCronException;

class CronParserAdapterTest {

	private final CronParserAdapter adapter = new CronParserAdapter();

	@Test
	@DisplayName("解析正確的 Cron 表達式，應成功回傳領域 VO")
	void parse_ShouldReturnCronExpression_WhenValid() {
		String validCron = "0 0 12 * * ?";
		CronExpression result = adapter.parse(validCron);
		assertEquals(validCron, result.value());
	}

	@Test
	@DisplayName("解析錯誤的 Cron 表達式，應拋出 InvalidCronException 阻止污染領域層")
	void parse_ShouldThrowException_WhenInvalid() {
		// "abc" 絕對是不合法的 Quartz Cron
		String obviouslyWrongCron = "abc";
		
		assertThrows(InvalidCronException.class, () -> adapter.parse(obviouslyWrongCron));
	}
}
