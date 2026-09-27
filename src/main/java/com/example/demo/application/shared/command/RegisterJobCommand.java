package com.example.demo.application.shared.command;

import com.example.demo.application.domain.schedule.aggregate.vo.ScheduleRule;

/**
 * 註冊排程任務至執行引擎的指令 (Command)。
 * <p>
 * 用於將已確認的排程配置 (通常來自資料庫或領域物件) 同步註冊到底層的 Quartz 執行引擎。
 * </p>
 *
 * @param name           任務名稱 (唯一識別的一部分)
 * @param group          任務所屬群組 (唯一識別的一部分)
 * @param scheduleRule   彈性的排程規則 (CRON 或 ONE_TIME)
 * @param jobType        任務的類型標籤 (對應具體的 Quartz Job 實作)
 */
public record RegisterJobCommand(String name, String group, ScheduleRule scheduleRule, String jobType) {
}
