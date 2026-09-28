package com.example.demo.iface.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "建立日曆回應")
public record CalendarCreatedResource(
		@Schema(description = "狀態碼", example = "201") String code, 
		@Schema(description = "回應訊息", example = "日曆建立成功") String message) {
}
