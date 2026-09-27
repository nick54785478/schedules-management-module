package com.example.demo.iface.dto.req;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "新增日曆排除日期請求參數")
public record AddExcludedDateResource(
        @Schema(description = "要排除的日期", example = "2026-01-01") LocalDate date
) {}
