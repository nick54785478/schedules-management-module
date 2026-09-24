package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "新增排程任務請求參數")
public record CreateJobResource(
		@Schema(description = "任務名稱", example = "NewReportJob") String name, 
		@Schema(description = "任務群組", example = "ReportGroup") String group, 
		@Schema(description = "Cron 表達式", example = "0 0 12 * * ?") String cron,
		@Schema(description = "任務類型 (對應 Quartz Job Bean Name)", example = "messagePrintJob") String jobType) {
}
