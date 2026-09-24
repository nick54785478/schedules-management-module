package com.example.demo.infra.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.context.ApplicationContext;

import com.example.demo.application.shared.command.RegisterJobCommand;

@ExtendWith(MockitoExtension.class)
class JobSchedulerAdapterTest {

	@Mock
	private Scheduler scheduler;

	@Mock
	private ApplicationContext applicationContext;

	@InjectMocks
	private JobSchedulerAdapter adapter;

	// 模擬一個開發者自定義的 Job
	private static class DummyJob implements Job {
		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {}
	}

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("新增任務：應動態向 ApplicationContext 查詢型別並向 Scheduler 註冊")
	void add_ShouldCreateJobAndTriggerAndSchedule() throws Exception {
		// Arrange
		RegisterJobCommand cmd = new RegisterJobCommand("TestJob", "TestGroup", "0 0 12 * * ?", "dummyJobBean");
		
		// 模擬 Spring 容器回傳 Bean 的型別
		doReturn(DummyJob.class).when(applicationContext).getType("dummyJobBean");

		// Act
		adapter.add(cmd);

		// Assert
		ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
		ArgumentCaptor<Set<? extends Trigger>> triggersCaptor = ArgumentCaptor.forClass((Class) Set.class);
		
		// 驗證 Replace = true
		verify(scheduler, times(1)).scheduleJob(jobDetailCaptor.capture(), triggersCaptor.capture(), eq(true));
		
		JobDetail capturedJob = jobDetailCaptor.getValue();
		assertEquals("TestJob", capturedJob.getKey().getName());
		assertEquals("TestGroup", capturedJob.getKey().getGroup());
		assertEquals(DummyJob.class, capturedJob.getJobClass());
		
		Set<? extends Trigger> capturedTriggers = triggersCaptor.getValue();
		assertEquals(1, capturedTriggers.size());
		Trigger trigger = capturedTriggers.iterator().next();
		assertEquals("TestJobTrigger", trigger.getKey().getName());
	}
	
	@Test
	@DisplayName("例外處理：當指定的 Bean Name 不存在於 Spring 容器時，應阻斷註冊")
	void add_ShouldThrowException_WhenBeanNotFound() {
		// Arrange
		RegisterJobCommand cmd = new RegisterJobCommand("TestJob", "TestGroup", "0 0 12 * * ?", "invalidBean");
		when(applicationContext.getType("invalidBean")).thenThrow(new RuntimeException("No bean found"));

		// Act & Assert
		RuntimeException exception = assertThrows(RuntimeException.class, () -> adapter.add(cmd));
		assertTrue(exception.getMessage().contains("系統無法在 Spring 容器中找到對應的 Job 標識: invalidBean"));
	}

	@Test
	@DisplayName("暫停任務：應能正確解析為 JobKey 並呼叫 Quartz 引擎")
	void pause_ShouldCallSchedulerPause() throws Exception {
		// Act
		adapter.pause("TestJob", "TestGroup");

		// Assert
		verify(scheduler, times(1)).pauseJob(JobKey.jobKey("TestJob", "TestGroup"));
	}
}
