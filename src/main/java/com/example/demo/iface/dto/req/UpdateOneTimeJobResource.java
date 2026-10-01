package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新單次排程時間請求參數")
public record UpdateOneTimeJobResource(
        @Schema(description = "任務名稱", example = "MessagePrintJob") String name,
        @Schema(description = "任務群組", example = "MessagePrintGroup") String group,
        @Schema(description = "新的執行日期 (yyyy/MM/dd)", example = "2026/12/31") String executeDate,
        @Schema(description = "新的執行時間 (HH:mm)", example = "23:59") String executeTime) {
}
