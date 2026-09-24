package com.example.demo.iface.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "恢復排程任務回應")
public record ScheduleJobResumedResource(
		@Schema(description = "狀態碼", example = "200") String code, 
		@Schema(description = "回應訊息", example = "Job resumed") String message) {

}
