package com.example.demo.application.domain.checkpoint.aggregate;

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
 * <h2>JobExecutionCheckpoint 聚合根</h2>
 * <p>
 * 專門負責記錄排程任務執行中的「進度」與「中斷點」。
 * 這是為了支援 Quartz Failover Recovery 機制而設計，避免與 Append-Only 的日誌混用。
 * </p>
 */
@Entity
@Getter
@Table(name = "job_execution_checkpoint")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobExecutionCheckpoint {

	@Id
	@Column(name = "job_id", nullable = false)
	private String jobId; // 對應排程的唯一識別碼 (或 group_name)

	/**
	 * 目前狀態 (RUNNING, SUSPENDED, COMPLETED, FAILED)
	 */
	@Column(name = "status")
	private String status;

	/**
	 * 業務中斷點 (例如：最後處理的 User ID, 或 Page Number)
	 */
	@Column(name = "last_checkpoint")
	private String lastCheckpoint;

	/**
	 * 總步數
	 */
	@Column(name = "total_steps")
	private Integer totalSteps;

	/**
	 * 當前步數
	 */
	@Column(name = "current_step")
	private Integer currentStep;

	/**
	 * 是否為災後重試狀態
	 */
	@Column(name = "is_recovery")
	private Boolean isRecovery;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	private JobExecutionCheckpoint(String jobId, String status, String lastCheckpoint, Integer totalSteps, Integer currentStep, Boolean isRecovery) {
		this.jobId = jobId;
		this.status = status;
		this.lastCheckpoint = lastCheckpoint;
		this.totalSteps = totalSteps;
		this.currentStep = currentStep;
		this.isRecovery = isRecovery;
	}

	public static JobExecutionCheckpoint initialize(String jobId, Integer totalSteps, Boolean isRecovery) {
		return new JobExecutionCheckpoint(jobId, "RUNNING", null, totalSteps, 0, isRecovery);
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
