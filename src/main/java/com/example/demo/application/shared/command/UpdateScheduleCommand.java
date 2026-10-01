package com.example.demo.application.shared.command;

import java.time.LocalDateTime;

/**
 * 更新排程執行週期的指令 (Command)。
 * <p>
 * 用於接收外部 API 或控制台的請求，傳遞並修改特定任務的排程設定。
 * </p>
 *
 * @param name         任務名稱 (唯一識別的一部分)
 * @param group        任務所屬群組 (唯一識別的一部分)
 * @param scheduleType 排程類型 (CRON 或是 ONE_TIME)
 * @param newCron      新的 Cron 表達式字串
 * @param executeTime  精確執行時間
 */
public record UpdateScheduleCommand(String name, String group, String scheduleType, String newCron, LocalDateTime executeTime) {
}