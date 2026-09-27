package com.example.demo.application.domain.calendar.aggregate;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <h2>排程日曆 (Aggregate Root)</h2>
 * <p>
 * 管理全域共用的「排除日期」黑名單（例如：國定假日、週末）。
 * 各個排程任務可透過指定 {@code name} 來選擇套用此日曆。
 * </p>
 */
@Entity
@Getter
@Table(name = "schedule_calendar")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private UUID id;

    @Column(name = "calendar_key", unique = true, nullable = false)
    private String key; // 日曆唯一識別 key (對應 Quartz 的 Calendar Name)

    @Column(name = "description")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "schedule_calendar_excluded_dates",
            joinColumns = @JoinColumn(name = "calendar_id")
    )
    @Column(name = "excluded_date")
    private Set<LocalDate> excludedDates = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 工廠類方法，用於建立全新的排程日曆
     *
     * @param key         日曆 key (如: TAIWAN_HOLIDAY_2026)
     * @param description 描述
     */
    public static ScheduleCalendar create(String key, String description) {
        return new ScheduleCalendar(null, key, description, new HashSet<>(), null, null);
    }

    /**
     * 業務行為：新增排除日期 (放假)
     */
    public void addExcludedDate(LocalDate date) {
        if (date != null) {
            this.excludedDates.add(date);
        }
    }

    /**
     * 業務行為：移除排除日期 (補班/取消放假)
     */
    public void removeExcludedDate(LocalDate date) {
        if (date != null) {
            this.excludedDates.remove(date);
        }
    }
}
