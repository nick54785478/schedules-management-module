package com.example.demo.iface.scheduler;

import java.util.Optional;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.checkpoint.repository.JobExecutionCheckpointRepository;
import com.example.demo.iface.scheduler.base.AbstractRecoverableBatchJob;

import lombok.extern.slf4j.Slf4j;

/**
 * <h2>Failover 模擬測試排程 (使用 Batch 框架)</h2>
 * <p>
 * 這個 Job 展示了如何繼承 {@link AbstractRecoverableBatchJob} 來快速開發
 * 具備 Quartz 叢集 Failover 下「接續執行」機制的長時任務。
 * </p>
 */
@Slf4j
@Component("failoverSimulationJob")
@DisallowConcurrentExecution
public class FailoverSimulationJob extends AbstractRecoverableBatchJob {

	public FailoverSimulationJob(JobExecutionCheckpointRepository checkpointRepository) {
		super(checkpointRepository);
	}

	@Override
	protected int calculateTotalSteps(JobExecutionContext context) throws Exception {
		// 回傳這個任務總共要跑多少步 (例如：總共有 10 頁的資料需要處理)
		return 10;
	}

	@Override
	protected String processStep(int currentStep, String lastCheckpoint, JobExecutionContext context) throws Exception {
		// 這裡撰寫單一步驟的實際業務邏輯，例如：
		// - 根據 currentStep 當作 Page Number 查詢資料庫
		// - 寄送這一批次的信件
		
		// 模擬耗時操作 (每步 5 秒)，讓您有時間拔插頭測試 Failover
		Thread.sleep(5000);
		
		// 回傳進度標記，這會被儲存到 Checkpoint 的 last_checkpoint 欄位
		return "STEP_" + currentStep + "_DONE";
	}
}
