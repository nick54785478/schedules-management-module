package com.example.demo.infra.persistence.joblog.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <h2>ScheduleJobLogJpaEntity (Audit Entity)</h2>
 * <p>
 * 用於記錄 Quartz 引擎中每一個 Job 執行的歷史軌跡。
 * </p>
 */
@Entity
@Getter
@Table(name = "schedule_job_log")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleJobLogJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 任務名稱 (對應 Quartz JobKey.name)
	 */
	@Column(name = "job_name")
	private String jobName;

	/**
	 * 任務群組 (對應 Quartz JobKey.group)
	 */
	@Column(name = "job_group")
	private String jobGroup;

	/**
	 * 執行結果狀態 (SUCCESS, FAILED, VETOED)
	 */
	@Column(name = "status")
	private String status;

	/**
	 * 執行耗時 (毫秒)
	 */
	@Column(name = "duration_ms")
	private Long durationMs;

	/**
	 * 失敗時的錯誤訊息
	 */
	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	/**
	 * 紀錄產生時間
	 */
	@CreatedDate
	@Column(name = "executed_at", updatable = false)
	private LocalDateTime executedAt;

	/**
	 * 是否為災後重試
	 */
	@Column(name = "is_recovery")
	private Boolean isRecovery;

	private ScheduleJobLogJpaEntity(String jobName, String jobGroup, String status, Long durationMs, String errorMessage, Boolean isRecovery) {
		this.jobName = jobName;
		this.jobGroup = jobGroup;
		this.status = status;
		this.durationMs = durationMs;
		this.errorMessage = errorMessage;
		this.isRecovery = isRecovery;
	}

	public static ScheduleJobLogJpaEntity createSuccessLog(String jobName, String jobGroup, Long durationMs, Boolean isRecovery) {
		return new ScheduleJobLogJpaEntity(jobName, jobGroup, "SUCCESS", durationMs, null, isRecovery);
	}

	public static ScheduleJobLogJpaEntity createFailedLog(String jobName, String jobGroup, Long durationMs, String errorMessage, Boolean isRecovery) {
		return new ScheduleJobLogJpaEntity(jobName, jobGroup, "FAILED", durationMs, errorMessage, isRecovery);
	}

	public static ScheduleJobLogJpaEntity createVetoedLog(String jobName, String jobGroup, Boolean isRecovery) {
		return new ScheduleJobLogJpaEntity(jobName, jobGroup, "VETOED", 0L, null, isRecovery);
	}
}
