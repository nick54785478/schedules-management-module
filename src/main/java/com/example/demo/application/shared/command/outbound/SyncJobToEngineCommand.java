package com.example.demo.application.shared.command.outbound;

import com.example.demo.application.domain.schedule.aggregate.vo.ScheduleRule;

/**
 * <h2>SyncJobToEngineCommand</h2>
 * <p>
 * 專門用於與底層排程引擎 (Outbound Port) 同步任務配置的指令。
 * 避免依賴外部傳入的 Inbound Command 或直接依賴領域聚合根 (ScheduledJob)，實現基礎設施與應用層/領域層的解耦。
 * </p>
 *
 * @param name         排程名稱
 * @param group        排程群組
 * @param jobType      任務的類型標籤 (對應具體的 Quartz Job 實作)
 * @param scheduleRule 彈性的排程規則 (CRON 或 ONE_TIME)
 */
public record SyncJobToEngineCommand(
    String name,
    String group,
    String jobType,
    ScheduleRule scheduleRule
) {
}

