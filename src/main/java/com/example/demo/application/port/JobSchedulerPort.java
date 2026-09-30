package com.example.demo.application.port;

import java.util.Date;
import java.util.List;

import com.example.demo.application.shared.exception.CalendarSyncException;
import com.example.demo.application.shared.exception.ScheduleEngineException;

import com.example.demo.application.shared.command.RegisterJobCommand;
import com.example.demo.application.shared.command.UpdateJobCronCommand;
import com.example.demo.application.shared.view.ScheduleJobView;
import com.example.demo.application.shared.listener.JobStatusListener;

/**
 * <h2>JobSchedulerPort</h2>
 * <p>
 * 定義排程執行引擎的輸出埠（Output Port）。 負責規範排程任務的生命週期管理動作，包含註冊、暫停、恢復與查詢。
 * </p>
 *
 */
public interface JobSchedulerPort {

	/**
	 * 註冊或更新排程任務。
	 *
	 * @param command 包含任務名稱、群組、Cron 表達式與 Job 標識的命令物件
	 * @throws ScheduleEngineException 當執行引擎註冊或更新失敗時拋出
	 */
	void add(RegisterJobCommand command) throws ScheduleEngineException;

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
	List<ScheduleJobView> findAll() throws ScheduleEngineException;

	/**
	 * 將領域層的日曆同步至底層 Quartz 引擎
	 * 
	 * @param calendar 領域層的日曆聚合根
	 * @throws CalendarSyncException 當日曆同步至底層引擎失敗時拋出
	 */
	void syncCalendar(com.example.demo.application.domain.calendar.aggregate.ScheduleCalendar calendar) throws CalendarSyncException;

	/**
	 * 更新現有排程的 Cron 表達式。
	 *
	 * @param command 包含新的 Cron 資訊與任務標識的命令物件
	 * @return 下一次預計觸發的時間點
	 * @throws ScheduleEngineException 當更新 Cron 或重新排程失敗時拋出
	 */
	Date updateCron(UpdateJobCronCommand command) throws ScheduleEngineException;

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