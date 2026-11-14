package com.example.demo.infra.persistence.calendar.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <h2>ScheduleCalendarJpaEntity</h2>
 * <p>
 * 排程日曆 JPA ORM 實體，隸屬於基礎設施層持久化模組。
 * </p>
 */
@Entity
@Getter
@Table(name = "schedule_calendar")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleCalendarJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private UUID id;

    @Column(name = "calendar_key", unique = true, nullable = false)
    private String key;

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

    public void updateDetails(String description, Set<LocalDate> excludedDates) {
        this.description = description;
        this.excludedDates = excludedDates != null ? new HashSet<>(excludedDates) : new HashSet<>();
    }
}
