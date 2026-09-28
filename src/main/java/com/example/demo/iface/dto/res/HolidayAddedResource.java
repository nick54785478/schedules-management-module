package com.example.demo.iface.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "新增假日回應")
public record HolidayAddedResource(
		@Schema(description = "狀態碼", example = "200") String code, 
		@Schema(description = "回應訊息", example = "假日新增成功") String message) {
}
