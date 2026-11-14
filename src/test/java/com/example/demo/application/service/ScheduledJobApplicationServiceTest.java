package com.example.demo.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.application.port.out.CronParserPort;
import com.example.demo.application.port.out.JobSchedulerPort;
import com.example.demo.application.port.out.ScheduledJobRepositoryPort;
import com.example.demo.application.shared.command.inbound.CreateCronJobCommand;
import com.example.demo.application.shared.command.inbound.CreateOneTimeJobCommand;
import com.example.demo.application.shared.command.inbound.UpdateScheduleCommand;
import com.example.demo.application.shared.command.outbound.SyncJobToEngineCommand;
import com.example.demo.application.shared.exception.JobNotFoundException;
import com.example.demo.application.shared.exception.ScheduleEngineException;
import com.example.demo.application.shared.model.ScheduledJobModel;
import com.example.demo.application.shared.query.GetScheduleJobQuery;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleJobGottenView;
import com.example.demo.application.shared.vo.JobId;
import com.example.demo.application.shared.vo.JobStatus;
import com.example.demo.application.shared.vo.ScheduleRule;
import com.example.demo.application.shared.vo.ScheduleType;

@ExtendWith(MockitoExtension.class)
class ScheduledJobApplicationServiceTest {

	@Mock
	private ScheduledJobRepositoryPort repository;

	@Mock
	private JobSchedulerPort jobScheduler;

	@Mock
	private CronParserPort cronParser;

	@InjectMocks
	private ScheduledJobApplicationService applicationService;

	@Test
	@DisplayName("初始化任務：當資料庫中無此任務時，應建立新模型並同步至 Quartz 引擎")
	void initializeTask_ShouldRegisterNewJob_WhenJobDoesNotExist() throws Exception {
		// Arrange
		CreateCronJobCommand command = new CreateCronJobCommand("TestJob", "TestGroup", "testJobBean", "0 0 12 * * ?", null, true);
		when(repository.findByNameAndGroup("TestJob", "TestGroup")).thenReturn(Optional.empty());
		when(repository.save(any(ScheduledJobModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		applicationService.initializeCronTask(command);

		// Assert
		verify(repository, times(1)).save(any(ScheduledJobModel.class));
		verify(jobScheduler, times(1)).add(any(SyncJobToEngineCommand.class));
	}

	@Test
	@DisplayName("更新 Cron：當任務存在時，應更新模型狀態並同步至 Quartz，最後進行持久化")
	void updateJobSchedule_ShouldUpdateAndSave_WhenJobExists() throws Exception {
		// Arrange
		ScheduledJobModel job = ScheduledJobModel.create("TestJob", "TestGroup", "testJobBean", ScheduleRule.cron("0 0 12 * * ?", true));
		UpdateScheduleCommand command = new UpdateScheduleCommand(job.jobId().value(), "CRON", "0/5 * * * * ?", null);
		
		when(repository.findByJobId(job.jobId())).thenReturn(Optional.of(job));

		// Act
		applicationService.updateJobSchedule(command);

		// Assert
		ArgumentCaptor<ScheduledJobModel> captor = ArgumentCaptor.forClass(ScheduledJobModel.class);
		verify(jobScheduler, times(1)).add(any(SyncJobToEngineCommand.class));
		verify(repository, times(1)).save(captor.capture());
		assertEquals("0/5 * * * * ?", captor.getValue().scheduleRule().getCronExpression());
	}

	@Test
	@DisplayName("更新 Cron：當 Quartz 同步失敗時，應拋出 ScheduleEngineException，並避免持久化")
	void updateJobSchedule_ShouldThrowScheduleEngineException_WhenQuartzFails() throws Exception {
		// Arrange
		ScheduledJobModel job = ScheduledJobModel.create("TestJob", "TestGroup", "testJobBean", ScheduleRule.cron("0 0 12 * * ?", true));
		UpdateScheduleCommand command = new UpdateScheduleCommand(job.jobId().value(), "CRON", "0/5 * * * * ?", null);
		
		when(repository.findByJobId(job.jobId())).thenReturn(Optional.of(job));
		doThrow(new ScheduleEngineException("Update", "TestGroup", "TestJob", new RuntimeException("Quartz Error"))).when(jobScheduler).add(any(SyncJobToEngineCommand.class));

		// Act & Assert
		assertThrows(ScheduleEngineException.class, () -> applicationService.updateJobSchedule(command));
		
		// 驗證 Transaction 的完整性 (不會呼叫 save)
		verify(repository, never()).save(any());
	}

	@Test
	@DisplayName("暫停任務：狀態應變更為 PAUSED，並同步至 Quartz 引擎")
	void pauseTask_ShouldCallEngineAndSave_WhenJobExists() throws Exception {
		// Arrange
		ScheduledJobModel job = ScheduledJobModel.create("TestJob", "TestGroup", "testJobBean", ScheduleRule.cron("0 0 12 * * ?", true));
		String jobIdValue = job.jobId().value();
		
		when(repository.findByJobId(new JobId(jobIdValue))).thenReturn(Optional.of(job));

		// Act
		applicationService.pauseTask(jobIdValue);

		// Assert
		ArgumentCaptor<ScheduledJobModel> captor = ArgumentCaptor.forClass(ScheduledJobModel.class);
		verify(jobScheduler, times(1)).pause("TestJob", "TestGroup");
		verify(repository, times(1)).save(captor.capture());
		assertEquals(JobStatus.PAUSED, captor.getValue().status());
	}

	@Test
	@DisplayName("恢復任務：狀態應變更為 NORMAL，並同步至 Quartz 引擎")
	void resumeTask_ShouldCallEngineAndSave_WhenJobExists() throws Exception {
		// Arrange
		ScheduledJobModel job = ScheduledJobModel.create("TestJob", "TestGroup", "testJobBean", ScheduleRule.cron("0 0 12 * * ?", true))
				.withStatus(JobStatus.PAUSED);
		String jobIdValue = job.jobId().value();
		
		when(repository.findByJobId(new JobId(jobIdValue))).thenReturn(Optional.of(job));

		// Act
		applicationService.resumeTask(jobIdValue);

		// Assert
		ArgumentCaptor<ScheduledJobModel> captor = ArgumentCaptor.forClass(ScheduledJobModel.class);
		verify(jobScheduler, times(1)).resume("TestJob", "TestGroup");
		verify(repository, times(1)).save(captor.capture());
		assertEquals(JobStatus.NORMAL, captor.getValue().status());
	}
	
	@Test
	@DisplayName("防呆測試：找不到任務時應拋出 JobNotFoundException")
	void pauseTask_ShouldThrowException_WhenJobNotFound() {
		// Arrange
		when(repository.findByJobId(any())).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(JobNotFoundException.class, () -> applicationService.pauseTask("invalid-id"));
		
		// 不可呼叫引擎，也不可存檔
		try {
			verify(jobScheduler, never()).pause(anyString(), anyString());
		} catch (Exception e) {}
		verify(repository, never()).save(any());
	}

	/**
	 * Feature: 依條件查詢排程任務列表
	 * Scenario: 當傳入 GetScheduleJobQuery 條件時，應協調 Repository 與 Quartz 引擎合成視圖
	 * Given 資料庫與引擎中存在符合條件之排程任務
	 * When 調用 getJobInfoResources(query)
	 * Then 正確回傳分頁視圖且包含對應狀態
	 */
	@Test
	@DisplayName("查詢任務列表：應依 GetScheduleJobQuery 條件撈取資料庫與 Quartz 狀態並組裝回傳")
	void getJobInfoResources_ShouldReturnPageWithEngineStatus() throws Exception {
		// Arrange
		GetScheduleJobQuery query = new GetScheduleJobQuery(0, 10, JobStatus.NORMAL, "testJobBean", "TestGroup", ScheduleType.CRON);
		ScheduledJobModel model = ScheduledJobModel.create("TestJob", "TestGroup", "testJobBean", ScheduleRule.cron("0 0 12 * * ?", true));
		PageGottenView<ScheduledJobModel> dbPage = new PageGottenView<>(List.of(model), 0, 10, 1L, 1);
		
		when(repository.findAll(query)).thenReturn(dbPage);
		ScheduleJobGottenView quartzJobView = ScheduleJobGottenView.builder()
				.name("TestJob")
				.group("TestGroup")
				.state("NORMAL")
				.triggerType("CRON")
				.build();
		when(jobScheduler.findAll()).thenReturn(List.of(quartzJobView));

		// Act
		PageGottenView<ScheduleJobGottenView> result = applicationService.getJobInfoResources(query);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.content().size());
		assertEquals("TestJob", result.content().get(0).getName());
		assertEquals("NORMAL", result.content().get(0).getState());
		verify(repository, times(1)).findAll(query);
	}
}
