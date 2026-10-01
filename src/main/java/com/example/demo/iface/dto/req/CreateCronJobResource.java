package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "新增定時排程任務請求參數 (CRON)")
public record CreateCronJobResource(
		@Schema(description = "任務名稱", example = "NewReportJob") String name, 
		@Schema(description = "任務群組", example = "ReportGroup") String group, 
		@Schema(description = "任務類型 (對應 Quartz Job Bean Name)", example = "messagePrintJob") String jobType,
		@Schema(description = "Cron 表達式", example = "0 0 12 * * ?") String cron,
		@Schema(description = "綁定的日曆 key (選填，用於排除假日)", example = "TAIWAN_HOLIDAY") String calendarKey,
		@Schema(description = "是否啟用災後重跑機制", defaultValue = "true") boolean requestsRecovery) {
}
