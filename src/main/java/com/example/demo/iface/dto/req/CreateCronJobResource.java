package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "新增定時排程任務請求參數 (CRON)")
public record CreateCronJobResource(
		@NotBlank(message = "任務名稱不得為空") @Schema(description = "任務名稱", example = "NewReportJob") String name, 
		@NotBlank(message = "任務群組不得為空") @Schema(description = "任務群組", example = "ReportGroup") String group, 
		@NotBlank(message = "任務類型不得為空") @Schema(description = "任務類型 (對應 Quartz Job Bean Name)", example = "messagePrintJob") String jobType,
		@NotBlank(message = "Cron 表達式不得為空") @Schema(description = "Cron 表達式", example = "0 0 12 * * ?") String cron,
		@Schema(description = "綁定的日曆 key (選填，用於排除假日)", example = "TAIWAN_HOLIDAY") String calendarKey,
		@Schema(description = "是否啟用災後重跑機制", defaultValue = "true") boolean requestsRecovery) {
}
