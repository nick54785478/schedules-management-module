package com.example.demo.application.domain.schedule.aggregate.vo;

/**
 * <h2>任務狀態列舉 (Value Object)</h2>
 * <p>
 * 定義系統中排程任務的各個生命週期狀態。
 * </p>
 */
public enum JobStatus {
    
    /** 正常運行中 (已排程且正在等待觸發) */
    NORMAL, 
    
    /** 暫停狀態 (任務依然存在於引擎中，但暫時不會被觸發) */
    PAUSED,
    
    /** 停止狀態 (任務已停止，不可被修改排程) */
    STOPPED,
    
    /** 已完成 (專用於一次性任務，執行完畢後即標記為完成) */
    COMPLETED
}