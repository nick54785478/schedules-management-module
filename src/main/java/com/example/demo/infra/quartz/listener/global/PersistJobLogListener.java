package com.example.demo.infra.quartz.listener.global;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.joblog.aggregate.ScheduleJobLog;
import com.example.demo.application.shared.listener.JobStatusListener;
import com.example.demo.infra.persistence.ScheduleJobLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>持久化日誌監聽器</h2>
 * <p>
 * 將排程任務的執行結果（成功、失敗、中止）與耗時記錄到資料庫中。
 * </p>
 * <p>
 * <b>事務設計 (Transaction Design):</b>
 * 使用 {@code Propagation.REQUIRES_NEW} 確保日誌寫入的獨立性。
 * 即使 Job 內部的業務邏輯拋出 Exception 導致業務事務回滾，這個日誌依然能夠被成功寫入資料庫。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistJobLogListener implements JobStatusListener {

	private final ScheduleJobLogRepository logRepository;
	
	@Value("${app.schedule.job-log.enabled:false}")
	private boolean isLogEnabled;
	
	// 使用 ThreadLocal 記錄任務開始時間。適用於 Quartz 的同步與併發任務
	private final ThreadLocal<Long> startTime = new ThreadLocal<>();

	@Override
	public String getName() {
		return "PersistJobLogListener";
	}

	@Override
	public void onJobStarting(String jobName, String jobGroup) {
		if (!isLogEnabled) return;
		startTime.set(System.currentTimeMillis());
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onJobVetoed(String jobName, String jobGroup) {
		if (!isLogEnabled) return;
		
		startTime.remove();
		try {
			ScheduleJobLog logEntity = ScheduleJobLog.createVetoedLog(jobName, jobGroup);
			logRepository.save(logEntity);
		} catch (Exception e) {
			log.error("寫入任務中止日誌失敗", e);
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onJobExecuted(String jobName, String jobGroup, Exception exception) {
		if (!isLogEnabled) return;
		
		Long start = startTime.get();
		long duration = (start != null) ? (System.currentTimeMillis() - start) : 0;
		startTime.remove();

		try {
			ScheduleJobLog logEntity;
			if (exception != null) {
				String errorMsg = exception.getMessage();
				if (errorMsg != null && errorMsg.length() > 500) {
					errorMsg = errorMsg.substring(0, 500); // 截斷避免欄位溢位
				}
				logEntity = ScheduleJobLog.createFailedLog(jobName, jobGroup, duration, errorMsg);
			} else {
				logEntity = ScheduleJobLog.createSuccessLog(jobName, jobGroup, duration);
			}
			logRepository.save(logEntity);
		} catch (Exception e) {
			log.error("寫入任務執行日誌失敗", e);
		}
	}
}
