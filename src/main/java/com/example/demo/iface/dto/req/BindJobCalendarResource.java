package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "綁定日曆請求參數")
public record BindJobCalendarResource(
		@Schema(description = "任務名稱", example = "NewReportJob") String name, 
		@Schema(description = "任務群組", example = "ReportGroup") String group, 
		@Schema(description = "綁定的日曆 key (為空字串或 null 時表示解除綁定)", example = "TAIWAN_HOLIDAY") String calendarKey) {
}
