package com.example.demo.infra.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.example.demo.application.port.JobSchedulerPort;
import com.example.demo.application.shared.command.RegisterJobCommand;
import com.example.demo.application.shared.command.UpdateJobCronCommand;
import com.example.demo.application.shared.view.ScheduleJobView;
import com.example.demo.application.shared.listener.JobStatusListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>JobSchedulerAdapter</h2>
 * <p>
 * 實現 {@link JobSchedulerPort} 的技術適配器。 負責將領域層的抽象指令轉換為 Quartz
 * 框架的具體技術操作（JobDetail、Trigger 等）。
 * </p>
 * 
 * <p>
 * <b>技術特點：</b>
 * </p>
 * <ul>
 * <li><b>動態加載：</b> 透過 Spring Bean Name 尋找 Job 類型，避免資料庫與程式碼路徑強耦合。</li>
 * <li><b>原子覆蓋：</b> 利用 Quartz 內建的 {@code replace=true} 機制，確保排程更新時的數據一致性。</li>
 * <li><b>雙向轉接：</b> 透過監聽器包裝器，將 Quartz 內部的執行事件安全地回傳給業務層。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class JobSchedulerAdapter implements JobSchedulerPort {

	private final Scheduler scheduler;
	private final ApplicationContext applicationContext;

	/**
	 * 註冊或更新排程任務。
	 * <p>
	 * 此方法會先解析 Job 類型，隨後建構 Quartz 核心組件。使用原子替換機制， 若同名 Job
	 * 已存在，則會以新的配置覆蓋舊有設定，不會中斷排程器的運行。
	 * </p>
	 */
	@Override
	public void add(RegisterJobCommand cmd) throws SchedulerException {
		// 1. 動態解析 Job 類別 (基於 Spring Bean Name)
		Class<? extends Job> jobClass = lookupJobClass(cmd.jobType());

		// 2. 構建 Quartz 核心物件 (JobDetail 與 Trigger)
		JobDetail jobDetail = createJobDetail(cmd, jobClass);
		Trigger trigger = createTrigger(cmd);

		// 3. 註冊至調度器 (replace=true 代表「存在即覆蓋」)
		scheduler.scheduleJob(jobDetail, Collections.singleton(trigger), true);

		log.info("Quartz 排程處理完成 - 動作: 註冊/更新, 識別碼: {}.{}", cmd.group(), cmd.name());
	}

	@Override
	public void delete(String name, String group) throws SchedulerException {
		scheduler.deleteJob(JobKey.jobKey(name, group));
		log.info("Quartz 排程處理完成 - 動作: 刪除, 識別碼: {}.{}", group, name);
	}

	@Override
	public void pause(String name, String group) throws SchedulerException {
		scheduler.pauseJob(JobKey.jobKey(name, group));
		log.info("Quartz 排程處理完成 - 動作: 暫停, 識別碼: {}.{}", group, name);
	}

	@Override
	public void resume(String name, String group) throws SchedulerException {
		scheduler.resumeJob(JobKey.jobKey(name, group));
		log.info("Quartz 排程處理完成 - 動作: 恢復, 識別碼: {}.{}", group, name);
	}

	/**
	 * 獲取所有任務的實時運行快照。
	 * <p>
	 * 遍歷 Scheduler 中所有群組的 JobKey，並即時查詢每個 Job 綁定的 Trigger 狀態與預計執行時間。 若檢測到無 Trigger 的
	 * Job (孤兒任務)，會記錄警告日誌並跳過。
	 * </p>
	 */
	@Override
	public List<ScheduleJobView> findAll() throws SchedulerException {
		List<ScheduleJobView> jobList = new ArrayList<>();

		// 獲取所有群組中的所有 JobKey
		Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
		log.debug("掃描 Scheduler 運行狀態，當前 Job 總數: {}", jobKeys.size());

		for (JobKey jobKey : jobKeys) {
			List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);

			if (triggers.isEmpty()) {
				log.warn("檢測到孤兒 Job (無 Trigger 綁定): {}", jobKey);
				continue;
			}

			for (Trigger trigger : triggers) {
				// 構建基礎運行時資訊
				ScheduleJobView jobInfo = ScheduleJobView.builder().name(jobKey.getName()).group(jobKey.getGroup())
						.nextFireTime(trigger.getNextFireTime())
						.state(scheduler.getTriggerState(trigger.getKey()).name()).build();

				// 解析特定的 Trigger 詳細數據 (Cron 或 Interval)
				this.processTriggerData(jobInfo, trigger);
				jobList.add(jobInfo);
			}
		}
		return jobList;
	}

	/**
	 * 更新指定任務的 Cron 表達式。
	 * <p>
	 * 透過重新啟動（Reschedule）機制，僅更換 Trigger 而不影響 JobDetail。
	 * </p>
	 */
	@Override
	public Date updateCron(UpdateJobCronCommand cmd) throws SchedulerException {
		TriggerKey tk = TriggerKey.triggerKey(cmd.name() + "Trigger", cmd.group());

		// 重新構建新 Trigger 並綁定原 Job
		CronTrigger newTrigger = TriggerBuilder.newTrigger().withIdentity(tk)
				.withSchedule(CronScheduleBuilder.cronSchedule(cmd.newCron()))
				.forJob(JobKey.jobKey(cmd.name(), cmd.group())).build();

		return scheduler.rescheduleJob(tk, newTrigger);
	}

	/**
	 * <h2>lookupJobClass</h2>
	 * <p>
	 * 透過 Spring {@link ApplicationContext} 根據邏輯名稱（Bean Name）查找 Job 類別。
	 * </p>
	 * 
	 * <p>
	 * <b>設計重點：</b>
	 * </p>
	 * <ul>
	 * <li><b>重構魯棒性：</b> 避免在資料庫中儲存類別路徑，包名更動時系統依然穩定。</li>
	 * <li><b>技術解耦：</b> Domain 層僅需傳遞標識符，由 Adapter 負責尋找技術實現。</li>
	 * </ul>
	 */
	@SuppressWarnings("unchecked")
	private Class<? extends Job> lookupJobClass(String jobType) {
		try {
			return (Class<? extends Job>) applicationContext.getType(jobType);
		} catch (Exception e) {
			log.error("Job 類型解析失敗，請確認 Bean Name [{}] 是否正確註冊", jobType);
			throw new RuntimeException("系統無法在 Spring 容器中找到對應的 Job 標識: " + jobType, e);
		}
	}

	/**
	 * <h2>processTriggerData</h2>
	 * <p>
	 * 輔助方法：根據 Trigger 具體類型解析技術細節。 對於 CronTrigger，會計算相鄰執行點的差值以估算平均執行頻率。
	 * </p>
	 */
	private void processTriggerData(ScheduleJobView jobInfo, Trigger trigger) {
		String triggerType = "Unknown";
		Integer intervalInSeconds = null;

		if (trigger instanceof SimpleTrigger simpleTrigger) {
			triggerType = "SimpleTrigger";
			intervalInSeconds = (int) (simpleTrigger.getRepeatInterval() / 1000);
		} else if (trigger instanceof CronTrigger cronTrigger) {
			triggerType = "CronTrigger";
			jobInfo.setCronExpression(cronTrigger.getCronExpression());

			Date next = trigger.getNextFireTime();
			Date prev = trigger.getPreviousFireTime();
			if (next != null && prev != null) {
				long intervalMillis = next.getTime() - prev.getTime();
				intervalInSeconds = (int) (intervalMillis / 1000);
			}
		}

		jobInfo.setTriggerType(triggerType);
		jobInfo.setIntervalInSeconds(intervalInSeconds);
	}

	/**
	 * <h2>createJobDetail</h2>
	 * <p>
	 * 建構 Quartz 的 {@link JobDetail} 實例。 JobDetail 用於描述任務的「身分」與「行為類型」。
	 * </p>
	 *
	 * @param cmd      包含名稱與群組的註冊指令
	 * @param jobClass 透過 {@code lookupJobClass} 解析出的具體類別
	 * @return 配置完成的 JobDetail 物件
	 */
	private JobDetail createJobDetail(RegisterJobCommand cmd, Class<? extends Job> jobClass) {
		return JobBuilder.newJob(jobClass).withIdentity(cmd.name(), cmd.group()).build();
	}

	/**
	 * <h2>createTrigger</h2>
	 * <p>
	 * 建構基於 Cron 表達式的 {@link Trigger} 實例。 Trigger 用於描述任務的「時間規則」並綁定至特定的 Job。
	 * </p>
	 * 
	 * <p>
	 * 預設 Trigger 識別碼會自動補上 "Trigger" 字樣以與 Job 名稱區隔。
	 * </p>
	 *
	 * @param cmd 包含 Cron 資訊與任務身分的註冊指令
	 * @return 配置完成的 CronTrigger 物件
	 */
	private Trigger createTrigger(RegisterJobCommand cmd) {
		return TriggerBuilder.newTrigger().withIdentity(cmd.name() + "Trigger", cmd.group())
				.withSchedule(CronScheduleBuilder.cronSchedule(cmd.cronExpression())).forJob(cmd.name(), cmd.group())
				.build();
	}

	/**
	 * 註冊全域監聽器。
	 * <p>
	 * 使用 {@link #wrapListener(JobStatusListener)} 將領域觀察者轉換為 Quartz 監聽器，
	 * 並應用於排程器中所有的任務（EverythingMatcher）。
	 * </p>
	 */
	@Override
	public void registerGlobalListener(JobStatusListener domainListener) throws SchedulerException {
		// 呼叫統一的包裝方法，確保所有事件生命週期 (Starting, Vetoed, Executed) 都能被捕捉
		JobListener quartzListener = this.wrapListener(domainListener);

		scheduler.getListenerManager().addJobListener(quartzListener,
				org.quartz.impl.matchers.EverythingMatcher.allJobs());

		log.info("已完成全域監聽器註冊: {}", domainListener.getName());
	}

	/**
	 * 註冊特定任務監聽器。
	 * <p>
	 * 僅對與指定名稱與群組匹配的任務生效。
	 * </p>
	 */
	@Override
	public void registerJobListener(JobStatusListener listener, String jobName, String jobGroup)
			throws SchedulerException {
		JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
		JobListener quartzListener = this.wrapListener(listener);

		scheduler.getListenerManager().addJobListener(quartzListener,
				org.quartz.impl.matchers.KeyMatcher.keyEquals(jobKey));

		log.info("已完成特定任務監聽器註冊: {}.{} -> {}", jobGroup, jobName, listener.getName());
	}

	/**
	 * <h2>wrapListener</h2>
	 * <p>
	 * 適配器核心私有方法：將自定義的 {@link JobStatusListener} (Utility) 橋接至 Quartz 原生的
	 * {@link JobListener}。
	 * </p>
	 * <p>
	 * 此處實作了完整的事件對應：
	 * <ul>
	 * <li>{@code jobToBeExecuted} -> {@code onJobStarting} (可用於計時開始)</li>
	 * <li>{@code jobExecutionVetoed} -> {@code onJobVetoed} (攔截中止)</li>
	 * <li>{@code jobWasExecuted} -> {@code onJobExecuted} (執行結束與異常處理)</li>
	 * </ul>
	 * </p>
	 */
	private JobListener wrapListener(JobStatusListener domainListener) {
		return new org.quartz.JobListener() {
			@Override
			public String getName() {
				return domainListener.getName();
			}

			@Override
			public void jobToBeExecuted(JobExecutionContext context) {
				domainListener.onJobStarting(context.getJobDetail().getKey().getName(),
						context.getJobDetail().getKey().getGroup());
			}

			@Override
			public void jobExecutionVetoed(JobExecutionContext context) {
				domainListener.onJobVetoed(context.getJobDetail().getKey().getName(),
						context.getJobDetail().getKey().getGroup());
			}

			@Override
			public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
				domainListener.onJobExecuted(context.getJobDetail().getKey().getName(),
						context.getJobDetail().getKey().getGroup(), jobException);
			}
		};
	}
}