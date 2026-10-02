package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "新增一次性排程任務請求參數 (ONE_TIME)")
public record CreateOneTimeJobResource(
		@NotBlank(message = "任務名稱不得為空") @Schema(description = "任務名稱", example = "OneTimeEventJob") String name, 
		@NotBlank(message = "任務群組不得為空") @Schema(description = "任務群組", example = "EventGroup") String group, 
		@NotBlank(message = "任務類型不得為空") @Schema(description = "任務類型 (對應 Quartz Job Bean Name)", example = "messagePrintJob") String jobType,
		@NotBlank(message = "執行日期不得為空") @Schema(description = "執行日期 (格式: yyyy/MM/dd)", example = "2026/12/31") String executeDate,
		@NotBlank(message = "執行時間不得為空") @Schema(description = "執行時間 (時分，格式: HH:mm)", example = "23:59") String executeTime,
		@Schema(description = "是否啟用災後重跑機制", defaultValue = "true") boolean requestsRecovery) {
}
