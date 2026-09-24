package com.example.demo.iface.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新 Cron 表達式回應")
public record JobCronUpdatedResource(
		@Schema(description = "狀態碼", example = "200") String code, 
		@Schema(description = "回應訊息", example = "更新 cron 成功") String message) {

}
