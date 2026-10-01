package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "更新 Cron 排程時間請求參數")
public record UpdateCronJobResource(
		@Schema(description = "任務名稱", example = "MessagePrintJob") String name, 
		@Schema(description = "任務群組", example = "MessagePrintGroup") String group, 
		@Schema(description = "新的 Cron 表達式", example = "0/30 * * * * ?") String newCron) {
}