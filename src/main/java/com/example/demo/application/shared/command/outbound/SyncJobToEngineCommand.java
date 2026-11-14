package com.example.demo.application.shared.command.outbound;

import com.example.demo.application.shared.vo.ScheduleRule;

/**
 * <h2>SyncJobToEngineCommand</h2>
 * <p>
 * 應用層發送至基礎設施層 (Quartz Adapter) 的同步命令。
 * 負責將應用層資料與排程規則封裝為統一指令，傳遞給排程引擎註冊或更新。
 * </p>
 *
 * @param name         任務名稱
 * @param group        任務群組
 * @param jobType      對應的 Spring Bean Name
 * @param scheduleRule 彈性排程規則
 */
public record SyncJobToEngineCommand(
        String name,
        String group,
        String jobType,
        ScheduleRule scheduleRule
) {
}
