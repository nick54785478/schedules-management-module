package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "更新排程時間請求參數")
public record UpdateJobCronResource(
		@Schema(description = "任務名稱", example = "MessagePrintJob") String name, 
		@Schema(description = "任務群組", example = "MessagePrintGroup") String group, 
		@Schema(description = "排程類型 (CRON 或 ONE_TIME)", example = "CRON") String scheduleType,
		@Schema(description = "新的 Cron 表達式", example = "0/30 * * * * ?") String newCron,
		@Schema(description = "新的執行時間", example = "2026-12-31T23:59:59") LocalDateTime executeTime) {
}