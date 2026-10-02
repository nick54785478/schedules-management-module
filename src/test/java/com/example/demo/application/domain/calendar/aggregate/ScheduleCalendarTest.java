package com.example.demo.application.domain.calendar.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleCalendarTest {

    @Test
    @DisplayName("新增排除日期：可以成功新增")
    void addExcludedDate_ShouldSuccess() {
        ScheduleCalendar calendar = ScheduleCalendar.create("TEST_KEY", "Test Calendar");
        LocalDate date = LocalDate.of(2026, 10, 10);

        calendar.addExcludedDate(date);

        assertTrue(calendar.getExcludedDates().contains(date));
    }

    @Test
    @DisplayName("移除排除日期：當日期存在時，可以成功移除")
    void removeExcludedDate_ShouldRemove_WhenDateExists() {
        ScheduleCalendar calendar = ScheduleCalendar.create("TEST_KEY", "Test Calendar");
        LocalDate date = LocalDate.of(2026, 10, 10);
        calendar.addExcludedDate(date);

        calendar.removeExcludedDate(date);

        assertFalse(calendar.getExcludedDates().contains(date));
    }

    @Test
    @DisplayName("移除排除日期：當日期不存在時，應拋出 IllegalArgumentException")
    void removeExcludedDate_ShouldThrowException_WhenDateNotExists() {
        ScheduleCalendar calendar = ScheduleCalendar.create("TEST_KEY", "Test Calendar");
        LocalDate date = LocalDate.of(2026, 10, 10);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            calendar.removeExcludedDate(date);
        });

        assertEquals("無法移除不存在的排除日期: 2026-10-10", exception.getMessage());
    }
}
