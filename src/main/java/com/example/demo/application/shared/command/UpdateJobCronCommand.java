package com.example.demo.application.shared.command;

/**
 * 更新排程執行週期的指令 (Command)。
 * <p>
 * 用於接收外部 API 或控制台的請求，傳遞並修改特定任務的 Cron 表達式。
 * 該指令會在 Application Service 中被攔截校驗，確保 Cron 語法合法。
 * </p>
 *
 * @param name    任務名稱 (唯一識別的一部分)
 * @param group   任務所屬群組 (唯一識別的一部分)
 * @param newCron 新的 Cron 表達式字串
 */
public record UpdateJobCronCommand(String name, String group, String newCron) {
}