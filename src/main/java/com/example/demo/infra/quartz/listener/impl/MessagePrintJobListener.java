package com.example.demo.infra.quartz.listener.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.shared.listener.JobStatusListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MessagePrintJobListener implements JobStatusListener {

	// 用於追蹤起始時間的線程安全容器（若有併發執行需求）
	private final ThreadLocal<Long> startTime = new ThreadLocal<>();

	@Override
	public String getName() {
		return "MessagePrintListener";
	}

	@Override
	public void onJobStarting(String jobName, String jobGroup) {
		startTime.set(System.currentTimeMillis());
		log.info("[排程監控] >>> 準備執行任務: {}.{}", jobGroup, jobName);
	}

	@Override
	public void onJobVetoed(String jobName, String jobGroup) {
		log.warn("[排程監控] !!! 任務執行被中止 (Vetoed): {}.{}", jobGroup, jobName);
		startTime.remove(); // 執行被取消，清除計時器
	}

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

		startTime.remove(); // 清理 ThreadLocal
	}
}