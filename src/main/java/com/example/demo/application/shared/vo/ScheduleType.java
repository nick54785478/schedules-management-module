package com.example.demo.application.shared.vo;

/**
 * <h2>ScheduleType (Value Object)</h2>
 * <p>
 * 定義了系統目前支援的排程觸發模式。
 * </p>
 */
public enum ScheduleType {

    /**
     * 基於 Cron 表達式的常態性週期任務。
     */
    CRON,

    /**
     * 精確指定未來某個時間點的一次性任務。
     */
    ONE_TIME
}
