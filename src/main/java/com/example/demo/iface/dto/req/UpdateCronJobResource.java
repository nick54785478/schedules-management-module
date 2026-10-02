package com.example.demo.iface.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "更新 Cron 排程時間請求參數")
public record UpdateCronJobResource(
		@NotBlank(message = "新的 Cron 表達式不得為空") @Schema(description = "新的 Cron 表達式", example = "0/30 * * * * ?") String newCron) {
}