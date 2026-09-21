package com.example.demo.application.domain.schedule.aggregate;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.example.demo.application.domain.schedule.aggregate.vo.CronExpression;
import com.example.demo.application.domain.schedule.aggregate.vo.JobId;
import com.example.demo.application.domain.schedule.aggregate.vo.JobStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "schedule_job")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduledJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Embedded
	@AttributeOverride(name = "value", column = @Column(name = "job_identifier", unique = true))
	private JobId jobId; // 唯一識別碼

	@Column(name = "job_name")
	private String name; // 業務名稱

	@Column(name = "job_group")
	private String group; // 業務分組

	@Column(name = "job_type")
	private String jobType; // 對應的 Job 標籤

	@Embedded
	@AttributeOverride(name = "value", column = @Column(name = "cron_expression"))
	private CronExpression cron; // Cron 表達式 (Value Object)

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private JobStatus status; // 狀態：NORMAL, PAUSED, STOPPED

	@CreatedDate
	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	/**
	 * 工廠類方法，用於註冊 Schedule Job
	 * 
	 * @param jobId   JobID
	 * @param name    排程名稱
	 * @param group   群組
	 * @param jobType 對應的 Job 標籤
	 * @param cron    Cron
	 */
	public static ScheduledJob register(JobId jobId, String name, String group, String jobType, CronExpression cron) {
		return new ScheduledJob(null, jobId, name, group, jobType, cron, JobStatus.NORMAL);
	}

	/**
	 * 業務行為：修改排程
	 */
	public void changeSchedule(String newCron) {
		if (this.status == JobStatus.STOPPED) {
			throw new IllegalStateException("已停止的任務無法修改排程");
		}
		this.cron = new CronExpression(newCron);
	}

	/**
	 * 業務行為：暫停
	 */
	public void pause() {
		if (this.status == JobStatus.NORMAL) {
			this.status = JobStatus.PAUSED;
		}
	}

	/**
	 * 業務行為：恢復
	 */
	public void resume() {
		if (this.status == JobStatus.PAUSED) {
			this.status = JobStatus.NORMAL;
		}
	}

	/**
	 * 更新排程的 Cron 表達式
	 * 
	 * @param newCron 新的 Cron 表達式數值物件
	 */
	public void updateCron(CronExpression newCron) {
		this.cron = newCron;
	}

	/**
	 * Private Constructor
	 */
	private ScheduledJob(Long id, JobId jobId, String name, String group, String jobType, CronExpression cron,
			JobStatus status) {
		this.id = id;
		this.jobId = jobId;
		this.name = name;
		this.group = group;
		this.jobType = jobType;
		this.cron = cron;
		this.status = status;
	}
}
