package com.example.demo.application.port;

import com.example.demo.application.shared.command.outbound.SyncCalendarToEngineCommand;
import com.example.demo.application.shared.command.outbound.SyncJobToEngineCommand;
import com.example.demo.application.shared.exception.CalendarSyncException;
import com.example.demo.application.shared.exception.ScheduleEngineException;
import com.example.demo.application.shared.listener.JobStatusListener;
import com.example.demo.application.shared.view.ScheduleJobGottenView;

import java.util.List;

/**
 * <h2>JobSchedulerPort</h2>
 * <p>
 * 定義排程執行引擎的輸出埠（Output Port）。
 * 負責規範排程任務的生命週期管理動作，包含註冊、暫停、恢復與查詢。
 * </p>
 */
public interface JobSchedulerPort {

    /**
     * 註冊或更新排程任務。
     *
     * @param command 包含任務配置的同步指令 (Outbound Command)
     * @throws ScheduleEngineException 當執行引擎註冊或更新失敗時拋出
     */
    void add(SyncJobToEngineCommand command) throws ScheduleEngineException;

    /**
     * 刪除指定的排程任務。
     *
     * @param name  任務名稱
     * @param group 任務分組
     * @throws ScheduleEngineException 當刪除動作執行失敗時拋出
     */
    void delete(String name, String group) throws ScheduleEngineException;

    /**
     * 暫停指定的排程任務。
     *
     * @param name  任務名稱
     * @param group 任務分組
     * @throws ScheduleEngineException 當暫停動作執行失敗時拋出
     */
    void pause(String name, String group) throws ScheduleEngineException;

    /**
     * 恢復處於暫停狀態的排程任務。
     *
     * @param name  任務名稱
     * @param group 任務分組
     * @throws ScheduleEngineException 當恢復動作執行失敗時拋出
     */
    void resume(String name, String group) throws ScheduleEngineException;

    /**
     * 查詢當前排程器中所有運行時的任務狀態。
     *
     * @return 包含任務運行資訊的視圖清單
     * @throws ScheduleEngineException 當查詢運行狀態失敗時拋出
     */
    List<ScheduleJobGottenView> findAll() throws ScheduleEngineException;

    /**
     * 將領域層的日曆同步至底層 Quartz 引擎
     *
     * @param command 包含日曆配置的同步指令 (Outbound Command)
     * @throws CalendarSyncException 當日曆同步至底層引擎失敗時拋出
     */
    void syncCalendar(SyncCalendarToEngineCommand command) throws CalendarSyncException;

    /**
     * 註冊全域監聽器（監聽所有排程任務）。
     *
     * @param listener 實作領域監聽邏輯的物件
     * @throws ScheduleEngineException 當註冊全域監聽器失敗時拋出
     */
    void registerGlobalListener(JobStatusListener listener) throws ScheduleEngineException;

    /**
     * 註冊特定任務監聽器（僅監聽指定的 Job）。
     *
     * @param listener 實作領域監聽邏輯的物件
     * @param jobName  要監聽的任務名稱
     * @param jobGroup 要監聽的任務群組
     * @throws ScheduleEngineException 當註冊特定任務監聽器失敗時拋出
     */
    void registerJobListener(JobStatusListener listener, String jobName, String jobGroup) throws ScheduleEngineException;
}
