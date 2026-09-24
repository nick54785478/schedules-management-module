package com.example.demo.iface.schedule;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.demo.application.service.DistributeLockApplicationService;

import lombok.extern.slf4j.Slf4j;

/**
 * 清理過期鎖的排程任務(可停用)
 * 目前由 Quartz 的分布式鎖來取代
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class ExpiredLocksCleanupJob implements Job {

	@Autowired
	private DistributeLockApplicationService distributeLockApplicationService;

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		log.info("這是一支清理過期超過 2 小時鎖的排程任務");
		distributeLockApplicationService.clearExpiredLocks();
	}

}