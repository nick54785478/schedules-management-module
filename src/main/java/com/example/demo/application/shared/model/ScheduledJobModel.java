package com.example.demo.application.shared.model;

import java.time.LocalDateTime;

import com.example.demo.application.shared.vo.JobId;
import com.example.demo.application.shared.vo.JobStatus;
import com.example.demo.application.shared.vo.ScheduleRule;

/**
 * <h2>ScheduledJobModel</h2>
 * <p>
 * 排程任務資料模型 (Application Layer Domain-agnostic Model)。
 * 作為 UseCase、RepositoryPort 與 Adapter 之間傳遞任務資料的載體。
 * </p>
 */
public record ScheduledJobModel(
        Long id,
        JobId jobId,
        String name,
        String group,
        String jobType,
        ScheduleRule scheduleRule,
        JobStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ScheduledJobModel create(String name, String group, String jobType, ScheduleRule scheduleRule) {
        JobId jobId = new JobId(java.util.UUID.randomUUID().toString());
        return new ScheduledJobModel(null, jobId, name, group, jobType, scheduleRule, JobStatus.NORMAL, null, null);
    }

    public ScheduledJobModel withScheduleRule(ScheduleRule newRule) {
        return new ScheduledJobModel(id, jobId, name, group, jobType, newRule, status, createdAt, updatedAt);
    }

    public ScheduledJobModel withStatus(JobStatus newStatus) {
        return new ScheduledJobModel(id, jobId, name, group, jobType, scheduleRule, newStatus, createdAt, updatedAt);
    }
}
