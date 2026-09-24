package com.example.demo.application.shared.command;

/**
 * 建立排程任務的指令 (Command)。
 * <p>
 * 用於在系統初始化或手動新增時，攜帶所需的排程參數，
 * 並將請求從外部或設定層傳遞至 Application Service 進行領域物件的建立與持久化。
 * </p>
 *
 * @param name           任務名稱 (唯一識別的一部分)
 * @param group          任務所屬群組 (唯一識別的一部分)
 * @param cronExpression 執行週期的 Cron 表達式字串
 * @param jobType        任務的類型標籤 (如: 對應具體的 Quartz Job 實作名稱)
 */
public record CreateJobCommand(String name, String group, String cronExpression, String jobType) {
}
