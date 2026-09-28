package com.example.demo.iface.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "綁定排程日期黑名單回應")
public record JobCalendarBoundResource(
        @Schema(description = "狀態碼", example = "200")
        String code,
        @Schema(description = "訊息", example = "排程任務日曆綁定更新成功")
        String message) {
}
