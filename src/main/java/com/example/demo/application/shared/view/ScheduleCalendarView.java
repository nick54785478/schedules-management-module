package com.example.demo.application.shared.view;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Set;

@Schema(description = "日曆視圖")
public record ScheduleCalendarView(
        @Schema(description = "日曆 ID") UUID id,
        @Schema(description = "日曆識別 Key (例如 TAIWAN_HOLIDAY)") String key,
        @Schema(description = "日曆描述") String description,
        @Schema(description = "排除日期清單") Set<LocalDate> excludedDates
) {}
