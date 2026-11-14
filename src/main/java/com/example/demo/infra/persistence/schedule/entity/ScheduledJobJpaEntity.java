package com.example.demo.infra.persistence.schedule.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.example.demo.application.shared.vo.JobStatus;
import com.example.demo.application.shared.vo.ScheduleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <h2>ScheduledJobJpaEntity</h2>
 * <p>
 * 排程任務 JPA ORM 實體，隸屬於基礎設施層持久化模組。
 * </p>
 */
@Entity
@Getter
@Table(name = "schedule_job", uniqueConstraints = {@UniqueConstraint(columnNames = {"job_name", "job_group"})})
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledJobJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_identifier", unique = true, nullable = false)
    private String jobIdentifier;

    @Column(name = "job_name", nullable = false)
    private String name;

    @Column(name = "job_group", nullable = false)
    private String group;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    private ScheduleType scheduleType;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "execute_time")
    private LocalDateTime executeTime;

    @Column(name = "calendar_key")
    private String calendarKey;

    @Column(name = "requests_recovery", nullable = false)
    private boolean requestsRecovery;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateDetails(ScheduleType scheduleType, String cronExpression, LocalDateTime executeTime,
                              String calendarKey, boolean requestsRecovery, JobStatus status) {
        this.scheduleType = scheduleType;
        this.cronExpression = cronExpression;
        this.executeTime = executeTime;
        this.calendarKey = calendarKey;
        this.requestsRecovery = requestsRecovery;
        this.status = status;
    }
}
