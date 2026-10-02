package com.example.demo.application.shared.command.outbound;

import java.time.LocalDate;
import java.util.Set;

/**
 * <h2>SyncCalendarToEngineCommand</h2>
 * <p>
 * 專門用於與底層排程引擎 (Outbound Port) 同步日曆配置的指令。
 * 避免直接依賴領域聚合根 (ScheduleCalendar)，實現基礎設施與領域層的解耦。
 * </p>
 *
 * @param calendarKey   日曆唯一識別 key
 * @param excludedDates 排除執行的日期集合
 */
public record SyncCalendarToEngineCommand(
    String calendarKey,
    Set<LocalDate> excludedDates
) {
}

