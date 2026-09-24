package com.example.demo.application.shared.listener;

/**
 * <h2>JobStatusListener (Infrastructure Utility)</h2>
 * <p>
 * 定義排程執行狀態的觀察者規範。 用於技術層面的監控，例如計算耗時、紀錄日誌或技術告警。
 * </p>
 */
public interface JobStatusListener {

	/**
	 * 監聽器唯一名稱
	 */
	String getName();

	/**
	 * 任務準備執行前觸發。
	 * <p>
	 * 可用於紀錄執行開始時間點，以便後續計算總耗時。
	 * </p>
	 */
	void onJobStarting(String jobName, String jobGroup);

	/**
	 * 任務執行被中止時觸發。
	 * <p>
	 * 當 TriggerListener 決定否決此次執行時（例如重複觸發攔截）會呼叫此方法。
	 * </p>
	 */
	void onJobVetoed(String jobName, String jobGroup);

	/**
	 * 任務執行完畢後觸發（無論成功或失敗）。
	 */
	void onJobExecuted(String jobName, String jobGroup, Exception exception);
}