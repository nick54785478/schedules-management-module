package com.example.demo.application.shared.command.inbound;

import java.time.LocalDateTime;

/**
 * 建立「一次性排程任務」的指令 (Command)。
 *
 * @param name         任務名稱 (唯一識別的一部分)
 * @param group        任務所屬群組 (唯一識別的一部分)
 * @param jobType      任務的類型標籤 (如: 對應具體的 Quartz Job 實作名稱)
 * @param executeTime  精確執行時間
 * @param requestsRecovery 是否啟用災後重建
 */
public record CreateOneTimeJobCommand(String name, String group, String jobType, LocalDateTime executeTime, boolean requestsRecovery) {
}

