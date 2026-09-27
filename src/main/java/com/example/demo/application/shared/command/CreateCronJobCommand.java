package com.example.demo.application.shared.command;

/**
 * 建立「定時排程任務 (Cron)」的指令 (Command)。
 *
 * @param name           任務名稱 (唯一識別的一部分)
 * @param group          任務所屬群組 (唯一識別的一部分)
 * @param jobType        任務的類型標籤 (如: 對應具體的 Quartz Job 實作名稱)
 * @param cronExpression 執行週期的 Cron 表達式字串
 * @param calendarKey   綁定的日曆 key (選填)
 */
public record CreateCronJobCommand(String name, String group, String jobType, String cronExpression, String calendarKey) {
}
