package com.example.demo.application.domain.schedule.aggregate.vo;

/**
 * <h2>ScheduleType (排程型態列舉)</h2>
 * <p>
 * 定義了系統目前支援的排程觸發模式。
 * 這是 {@link ScheduleRule} 內部用來識別多型行為與驗證條件的標籤。
 * </p>
 */
public enum ScheduleType {

    /**
     * 基於 Cron 表達式的常態性週期任務。<br>
     * 適用場景：每日報表生成、每小時快取同步等固定頻率任務。
     */
    CRON,

    /**
     * 精確指定未來某個時間點的一次性任務。<br>
     * 適用場景：針對特定業務觸發的延遲事件 (如：指定在明天早上 9 點發送推播)。
     */
    ONE_TIME
}
