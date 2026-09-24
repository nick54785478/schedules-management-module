package com.example.demo.application.shared.view;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 用於展示的視圖 (原本的 ScheduleJob)
 */
@Data
@Builder
@Schema(description = "排程任務視圖 (包含領域狀態與運行狀態)")
public class ScheduleJobView {

	// ### 來自 DB 的資料 ###
	@Schema(description = "領域唯一識別碼 (JobId)")
	private String jobId;

	@Schema(description = "任務類型 (對應 Bean Name)")
	private String jobType;

	@Schema(description = "任務描述")
	private String description;

	// ### 來自 Quartz 與 DB 共有 ###
	@Schema(description = "任務名稱")
	private String name;

	@Schema(description = "任務群組")
	private String group;

	// ### 來自 Quartz 運行時 ###
	@Schema(description = "目前執行的 Cron 表達式")
	private String cronExpression;

	@Schema(description = "觸發器類型")
	private String triggerType;

	@Schema(description = "執行間隔時間(秒)，視 trigger 類型而定")
	private Integer intervalInSeconds;

	@Schema(description = "下次執行時間")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private Date nextFireTime;

	@Schema(description = "Quartz 引擎中的狀態 (NORMAL, PAUSED, ERROR 等)")
	private String state;

	@Schema(description = "資料庫中記錄的業務狀態")
	private String domainStatus;
}