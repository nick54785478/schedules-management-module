package com.example.demo.infra.quartz.listener.global;

import org.springframework.stereotype.Component;

import com.example.demo.application.shared.listener.JobStatusListener;

import lombok.extern.slf4j.Slf4j;

/**
 * <h2>全域排程任務監控監聽器</h2>
 * <p>
 * 這是一個註冊在全域層級的 {@link JobStatusListener} 實作，負責在系統 Console
 * 中印出每一個 Quartz 排程任務的生命週期狀態 (開始、中止、結束) 以及耗時統計。
 * </p>
 * <p>
 * <b>執行緒安全設計 (Thread-Safety)：</b>
 * 由於這是一個 Spring 的 Singleton Bean，且會被 Quartz 底層的多個 Worker Thread 併發呼叫，
 * 故內部使用 {@code ThreadLocal} 來儲存每條 Thread 各自的執行起始時間，
 * 保證在併發情境下，不同任務間的耗時統計彼此隔離，不會互相干擾。
 * </p>
 */
@Slf4j
@Component
public class GlobalJobListener implements JobStatusListener {

	// 用於追蹤起始時間的線程安全容器，確保 Quartz 多執行緒併發執行時不會互相污染變數
	private final ThreadLocal<Long> startTime = new ThreadLocal<>();

	@Override
	public String getName() {
		return "GlobalJobListener";
	}

	/**
	 * 當任務準備開始執行時觸發
	 * 
	 * @param jobName  任務名稱
	 * @param jobGroup 任務群組
	 */
	@Override
	public void onJobStarting(String jobName, String jobGroup) {
		startTime.set(System.currentTimeMillis());
		log.info("[排程監控] >>> 準備執行任務: {}.{}", jobGroup, jobName);
	}

	/**
	 * 當任務被攔截器或其它機制強制中止 (Vetoed) 而未執行時觸發
	 * 
	 * @param jobName  任務名稱
	 * @param jobGroup 任務群組
	 */
	@Override
	public void onJobVetoed(String jobName, String jobGroup) {
		log.warn("[排程監控] !!! 任務執行被中止 (Vetoed): {}.{}", jobGroup, jobName);
		startTime.remove(); // 執行被取消，務必清除計時器避免 Memory Leak
	}

	/**
	 * 當任務執行完畢 (無論成功或發生例外) 時觸發
	 * 
	 * @param jobName   任務名稱
	 * @param jobGroup  任務群組
	 * @param exception 若執行過程中發生業務報錯，則會傳入此例外物件；若順利執行完畢則為 null
	 */
	@Override
	public void onJobExecuted(String jobName, String jobGroup, Exception exception) {
		Long start = startTime.get();
		long duration = (start != null) ? (System.currentTimeMillis() - start) : 0;

		if (exception != null) {
			log.error("[排程監控] <<< 任務執行失敗: {}.{}, 耗時: {}ms, 錯誤: {}", jobGroup, jobName, duration,
					exception.getMessage());
		} else {
			log.info("[排程監控] <<< 任務執行成功: {}.{}, 總耗時: {}ms", jobGroup, jobName, duration);
		}

		startTime.remove(); // 執行結束，務必清理 ThreadLocal 避免 Memory Leak
	}
}