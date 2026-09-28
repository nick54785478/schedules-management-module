package com.example.demo.iface.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "移除假日回應")
public record HolidayRemovedResource(
		@Schema(description = "狀態碼", example = "200") String code, 
		@Schema(description = "回應訊息", example = "假日移除成功") String message) {
}
