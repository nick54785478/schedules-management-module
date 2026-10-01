package com.example.demo.iface.scheduler;

import java.util.Arrays;
import java.util.List;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.checkpoint.repository.JobExecutionCheckpointRepository;
import com.example.demo.iface.scheduler.base.AbstractRecoverableBatchJob;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>批次寄信測試排程</h2>
 * <p>
 * 展示如何繼承 {@link AbstractRecoverableBatchJob} 實作具有災後重跑能力的寄信功能。
 * 將 10 名收件人分批寄送，每批處理固定數量。
 * </p>
 */
@Slf4j
@Component("batchSendEmailJob")
@DisallowConcurrentExecution
public class BatchSendEmailJob extends AbstractRecoverableBatchJob {

	public BatchSendEmailJob(JobExecutionCheckpointRepository checkpointRepository) {
		super(checkpointRepository);
	}

	// 1. 模擬要寄信的清單 (寫死 10 人)
	private static final List<String> USER_EMAILS = Arrays.asList(
			"user01@example.com", "user02@example.com", "user03@example.com",
			"user04@example.com", "user05@example.com", "user06@example.com",
			"user07@example.com", "user08@example.com", "user09@example.com",
			"user10@example.com"
	);

	// 模擬每次批次處理的筆數 (一次寄 3 封)
	private static final int BATCH_SIZE = 3;

	// 2. 定義 Command，用於封裝排程所需的參數 (未來可從 API 的 JobDataMap 傳入)
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SendEmailCommand {
		private String subject;
		private String content;
	}

	@Override
	protected int calculateTotalSteps(JobExecutionContext context) throws Exception {
		// 總步數 = 總人數 / 每批筆數 (無條件進位)
		// 10 筆 / 每批 3 筆 = 4 步 (前 3 批各 3 筆，最後 1 批 1 筆)
		return (int) Math.ceil((double) USER_EMAILS.size() / BATCH_SIZE);
	}

	@Override
	protected String processStep(int currentStep, String lastCheckpoint, JobExecutionContext context) throws Exception {
		// 從 Quartz 的 JobDataMap 取出參數 (若無則給定測試預設值)
		String subject = context.getMergedJobDataMap().getString("subject");
		if (subject == null) subject = "【系統通知】公版測試信件";

		String content = context.getMergedJobDataMap().getString("content");
		if (content == null) content = "親愛的用戶您好，這是一封測試信件。";

		SendEmailCommand command = new SendEmailCommand(subject, content);

		// 計算分頁的起訖索引 (currentStep 是從 1 開始)
		int startIndex = (currentStep - 1) * BATCH_SIZE;
		int endIndex = Math.min(startIndex + BATCH_SIZE, USER_EMAILS.size());
		List<String> batchEmails = USER_EMAILS.subList(startIndex, endIndex);

		log.info("📧 開始寄送第 {} 批次信件，共 {} 封...", currentStep, batchEmails.size());

		// 3. 執行單步驟的業務邏輯 (寄信)
		for (String email : batchEmails) {
			// 用 System.out.printf 模擬呼叫外部 API 寄信
			System.out.printf("[寄信作業] To: %-20s | Subject: %s | Content: %s%n", email, command.getSubject(), command.getContent());
			
			// 模擬網路傳輸的耗時 (1.5 秒/封)，讓您有時間中斷程式測試 Failover
			Thread.sleep(1500); 
		}

		String lastEmailProcessed = batchEmails.get(batchEmails.size() - 1);
		log.info("✅ 第 {} 批次寄送完成，最後處理的 User: {}", currentStep, lastEmailProcessed);
		
		// 4. 回傳進度標記 (例如最後一個處理的 email)
		// 框架會自動將此字串寫入 job_execution_checkpoint 的 last_checkpoint 欄位
		return lastEmailProcessed;
	}
}
