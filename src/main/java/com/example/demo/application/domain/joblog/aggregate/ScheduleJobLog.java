package com.example.demo.application.domain.joblog.aggregate;

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
 * <h2>ScheduleJobLog 聚合根 (Audit Entity)</h2>
 * <p>
 * 用於記錄 Quartz 引擎中每一個 Job 執行的歷史軌跡。
 * </p>
 * <p>
 * <b>領域設計理念：</b>
 * 此聚合與 {@link com.example.demo.application.domain.schedule.aggregate.ScheduledJob} 完全解耦，
 * 負責單純的稽核紀錄 (Audit Logging)，不包含排程控制的業務邏輯。這確保了系統查詢日誌時，
 * 不會因為載入巨大的日誌集合而拖垮排程配置聚合的效能。
 * </p>
 */
@Entity
@Getter
@Table(name = "schedule_job_log")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleJobLog {

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
	 * 失敗時的錯誤訊息 (長度經限制以免資料庫欄位溢位)
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
	 * Private Constructor，限制外部只能透過 Factory Method 建立實體
	 */
	private ScheduleJobLog(String jobName, String jobGroup, String status, Long durationMs, String errorMessage) {
		this.jobName = jobName;
		this.jobGroup = jobGroup;
		this.status = status;
		this.durationMs = durationMs;
		this.errorMessage = errorMessage;
	}

	/**
	 * 建立「執行成功」的日誌實體
	 *
	 * @param jobName    任務名稱
	 * @param jobGroup   任務群組
	 * @param durationMs 執行耗時 (毫秒)
	 * @return ScheduleJobLog 實體
	 */
	public static ScheduleJobLog createSuccessLog(String jobName, String jobGroup, Long durationMs) {
		return new ScheduleJobLog(jobName, jobGroup, "SUCCESS", durationMs, null);
	}

	/**
	 * 建立「執行失敗」的日誌實體
	 *
	 * @param jobName      任務名稱
	 * @param jobGroup     任務群組
	 * @param durationMs   執行耗時 (毫秒)
	 * @param errorMessage 錯誤細節
	 * @return ScheduleJobLog 實體
	 */
	public static ScheduleJobLog createFailedLog(String jobName, String jobGroup, Long durationMs, String errorMessage) {
		return new ScheduleJobLog(jobName, jobGroup, "FAILED", durationMs, errorMessage);
	}

	/**
	 * 建立「被強制中止 (Vetoed)」的日誌實體
	 * <p>
	 * 當任務在執行前被 Listener 擋下時會觸發此紀錄。
	 * </p>
	 *
	 * @param jobName  任務名稱
	 * @param jobGroup 任務群組
	 * @return ScheduleJobLog 實體
	 */
	public static ScheduleJobLog createVetoedLog(String jobName, String jobGroup) {
		return new ScheduleJobLog(jobName, jobGroup, "VETOED", 0L, null);
	}
}
