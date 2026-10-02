package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "綁定日曆請求參數")
public record BindJobCalendarResource(
		@Schema(description = "綁定的日曆 key (為空字串或 null 時表示解除綁定)", example = "TAIWAN_HOLIDAY") String calendarKey) {
}
