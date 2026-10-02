package com.example.demo.iface.dto.req;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

@Schema(description = "新增日曆排除日期請求參數")
public record AddExcludedDateResource(
        @NotNull(message = "排除日期不得為空") @Schema(description = "要排除的日期", example = "2026-01-01") LocalDate date
) {}
