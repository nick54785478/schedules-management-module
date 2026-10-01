package com.example.demo.iface.scheduler.base;

import java.util.Optional;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.example.demo.application.domain.checkpoint.aggregate.JobExecutionCheckpoint;
import com.example.demo.application.domain.checkpoint.repository.JobExecutionCheckpointRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * <h2>可災後重跑的批次任務公版 (Template Method Pattern)</h2>
 * <p>
 * 將 `JobExecutionCheckpoint` 的繁瑣讀寫邏輯封裝起來，提供給各個系統開發長時任務 (如：批次寄信、發送通知) 時直接繼承使用。
 * 繼承此類別的 Job 只要實作「計算總步數」與「執行單一步驟」的邏輯，即自動具備 Quartz Failover 接續執行的能力。
 * </p>
 */
@Slf4j
public abstract class AbstractRecoverableBatchJob extends QuartzJobBean {

	private final JobExecutionCheckpointRepository checkpointRepository;

	protected AbstractRecoverableBatchJob(JobExecutionCheckpointRepository checkpointRepository) {
		this.checkpointRepository = checkpointRepository;
	}

	/**
	 * 1. 計算此次任務的總步數 (例如：共有幾頁資料需要處理)
	 * <p>
	 * 此方法僅會在「全新執行」時被呼叫一次，災後重跑時不會再次呼叫。
	 * </p>
	 *
	 * @param context Quartz 執行上下文，可從中取得 JobDataMap 等參數
	 * @return 總步數
	 */
	protected abstract int calculateTotalSteps(JobExecutionContext context) throws Exception;

	/**
	 * 2. 執行單一步驟的業務邏輯
	 *
	 * @param currentStep    目前執行到第幾步
	 * @param lastCheckpoint 上一步完成後留下的進度標記 (若為第 1 步則為 null)
	 * @param context        Quartz 執行上下文
	 * @return 執行完成後，請回傳這一步的進度標記 (例如：最後一筆處理的 User ID)，將會存入 Checkpoint，並在下一步當作參數傳入
	 * @throws Exception 若拋出例外，將會中斷執行，並標記 Checkpoint 為 FAILED
	 */
	protected abstract String processStep(int currentStep, String lastCheckpoint, JobExecutionContext context) throws Exception;

	@Override
	protected final void executeInternal(JobExecutionContext context) throws JobExecutionException {
		String jobName = context.getJobDetail().getKey().getName();
		String groupName = context.getJobDetail().getKey().getGroup();
		String jobId = groupName + "." + jobName;

		boolean isRecovering = context.isRecovering();
		JobExecutionCheckpoint checkpoint;
		Optional<JobExecutionCheckpoint> optionalCheckpoint = checkpointRepository.findByJobId(jobId);

		int currentStep = 0;
		int totalSteps = 0;
		String lastCheckpoint = null;

		try {
			if (isRecovering && optionalCheckpoint.isPresent()) {
				// === 災後重跑 (Recovery Mode) ===
				checkpoint = optionalCheckpoint.get();
				currentStep = checkpoint.getCurrentStep();
				totalSteps = checkpoint.getTotalSteps();
				lastCheckpoint = checkpoint.getLastCheckpoint();
				log.warn("【Batch 框架】⚠️ 偵測到災後重跑 (Recovery Mode)！任務 {} 由中斷點 (第 {}/{} 步) 接續執行", jobId, currentStep, totalSteps);
			} else {
				// === 全新執行 (New Execution) ===
				log.info("【Batch 框架】🚀 全新任務 {} 開始執行", jobId);
				optionalCheckpoint.ifPresent(checkpointRepository::delete);
				
				totalSteps = calculateTotalSteps(context);
				checkpoint = JobExecutionCheckpoint.initialize(jobId, totalSteps, isRecovering);
				checkpointRepository.saveAndFlush(checkpoint);
			}

			// === 批次迴圈執行 ===
			for (int step = currentStep + 1; step <= totalSteps; step++) {
				log.info("【Batch 框架】⏳ 任務 {} 執行中... (第 {}/{} 步)", jobId, step, totalSteps);
				
				// 呼叫子類別的實際業務邏輯
				lastCheckpoint = processStep(step, lastCheckpoint, context);
				
				// 立即將進度寫入 DB。這樣一旦 Crash，下一個 Node 才知道進度
				checkpoint.updateProgress(lastCheckpoint, step);
				checkpointRepository.saveAndFlush(checkpoint);
			}

			// === 執行完畢 ===
			checkpoint.complete();
			checkpointRepository.saveAndFlush(checkpoint);
			log.info("【Batch 框架】✅ 任務 {} 執行完畢！", jobId);

		} catch (InterruptedException e) {
			log.warn("【Batch 框架】任務 {} 被中斷", jobId, e);
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.error("【Batch 框架】❌ 任務 {} 發生例外異常", jobId, e);
			if (optionalCheckpoint.isPresent() || !isRecovering) { 
				// 確保 checkpoint 物件有被初始化再儲存狀態
				JobExecutionCheckpoint failCheckpoint = checkpointRepository.findByJobId(jobId).orElse(null);
				if (failCheckpoint != null) {
					failCheckpoint.fail();
					checkpointRepository.saveAndFlush(failCheckpoint);
				}
			}
			throw new JobExecutionException(e);
		}
	}
}
