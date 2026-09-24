package com.example.demo.iface.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "暫停排程任務回應")
public record ScheduleJobPausedResource(
		@Schema(description = "狀態碼", example = "200") String code, 
		@Schema(description = "回應訊息", example = "Job paused") String message) {

}
