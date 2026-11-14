package com.example.demo.infra.persistence.checkpoint.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <h2>JobExecutionCheckpointJpaEntity</h2>
 * <p>
 * 專門負責記錄排程任務執行中的「進度」與「中斷點」，支援 Quartz Failover Recovery 機制。
 * </p>
 */
@Entity
@Getter
@Table(name = "job_execution_checkpoint")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobExecutionCheckpointJpaEntity {

	@Id
	@Column(name = "job_id", nullable = false)
	private String jobId;

	@Column(name = "status")
	private String status;

	@Column(name = "last_checkpoint")
	private String lastCheckpoint;

	@Column(name = "total_steps")
	private Integer totalSteps;

	@Column(name = "current_step")
	private Integer currentStep;

	@Column(name = "is_recovery")
	private Boolean isRecovery;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	private JobExecutionCheckpointJpaEntity(String jobId, String status, String lastCheckpoint, Integer totalSteps, Integer currentStep, Boolean isRecovery) {
		this.jobId = jobId;
		this.status = status;
		this.lastCheckpoint = lastCheckpoint;
		this.totalSteps = totalSteps;
		this.currentStep = currentStep;
		this.isRecovery = isRecovery;
	}

	public static JobExecutionCheckpointJpaEntity initialize(String jobId, Integer totalSteps, Boolean isRecovery) {
		return new JobExecutionCheckpointJpaEntity(jobId, "RUNNING", null, totalSteps, 0, isRecovery);
	}

	public void updateProgress(String lastCheckpoint, Integer currentStep) {
		this.lastCheckpoint = lastCheckpoint;
		this.currentStep = currentStep;
	}

	public void complete() {
		this.status = "COMPLETED";
	}

	public void fail() {
		this.status = "FAILED";
	}
}
