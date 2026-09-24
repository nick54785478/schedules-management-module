package com.example.demo.iface.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "新增排程任務回應")
public record ScheduleJobCreatedResource(
		@Schema(description = "狀態碼", example = "201") String code, 
		@Schema(description = "回應訊息", example = "排程任務建立成功") String message) {
}
