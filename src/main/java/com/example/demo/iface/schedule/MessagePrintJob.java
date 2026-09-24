package com.example.demo.iface.schedule;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@DisallowConcurrentExecution
public class MessagePrintJob implements Job {

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		
		// 視情況定義執行流程
		log.info("這是一支印出訊息的排程任務");
	}
}
