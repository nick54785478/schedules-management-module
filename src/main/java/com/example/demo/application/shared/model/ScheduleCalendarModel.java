package com.example.demo.application.shared.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <h2>ScheduleCalendarModel</h2>
 * <p>
 * 排程日曆資料模型 (Application Layer Model)。
 * 作為 UseCase、RepositoryPort 與 Adapter 之間傳遞日曆資料的載體。
 * </p>
 */
public record ScheduleCalendarModel(
        UUID id,
        String key,
        String description,
        Set<LocalDate> excludedDates,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public ScheduleCalendarModel {
        excludedDates = excludedDates != null ? Collections.unmodifiableSet(new HashSet<>(excludedDates)) : Collections.emptySet();
    }

    public static ScheduleCalendarModel create(String key, String description) {
        return new ScheduleCalendarModel(null, key, description, Collections.emptySet(), null, null);
    }

    public ScheduleCalendarModel addExcludedDate(LocalDate date) {
        Set<LocalDate> updated = new HashSet<>(this.excludedDates);
        if (date != null) {
            updated.add(date);
        }
        return new ScheduleCalendarModel(id, key, description, updated, createdAt, updatedAt);
    }

    public ScheduleCalendarModel removeExcludedDate(LocalDate date) {
        if (date != null && !this.excludedDates.contains(date)) {
            throw new IllegalArgumentException("無法移除不存在的排除日期: " + date);
        }
        Set<LocalDate> updated = new HashSet<>(this.excludedDates);
        if (date != null) {
            updated.remove(date);
        }
        return new ScheduleCalendarModel(id, key, description, updated, createdAt, updatedAt);
    }
}
