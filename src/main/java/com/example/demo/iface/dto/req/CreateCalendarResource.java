package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "建立日曆請求參數")
public record CreateCalendarResource(
        @NotBlank(message = "日曆 key 不得為空") @Schema(description = "日曆唯一識別 key", example = "TAIWAN_HOLIDAY") String key,
        @NotBlank(message = "日曆描述不得為空") @Schema(description = "日曆描述", example = "台灣國定假日與補班日") String description
) {}
