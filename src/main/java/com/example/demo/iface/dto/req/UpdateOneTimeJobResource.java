package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "更新單次排程時間請求參數")
public record UpdateOneTimeJobResource(
        @NotBlank(message = "任務名稱不得為空") @Schema(description = "任務名稱", example = "MessagePrintJob") String name,
        @NotBlank(message = "任務群組不得為空") @Schema(description = "任務群組", example = "MessagePrintGroup") String group,
        @NotBlank(message = "執行日期不得為空") @Schema(description = "新的執行日期 (yyyy/MM/dd)", example = "2026/12/31") String executeDate,
        @NotBlank(message = "執行時間不得為空") @Schema(description = "新的執行時間 (HH:mm)", example = "23:59") String executeTime) {
}
