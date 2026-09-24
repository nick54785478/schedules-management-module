package com.example.demo.application.service;

import com.example.demo.application.domain.schedule.aggregate.ScheduledJob;
import com.example.demo.application.domain.schedule.aggregate.vo.CronExpression;
import com.example.demo.application.domain.schedule.aggregate.vo.JobId;
import com.example.demo.application.port.CronParserPort;
import com.example.demo.application.port.JobSchedulerPort;
import com.example.demo.application.shared.command.CreateJobCommand;
import com.example.demo.application.shared.command.RegisterJobCommand;
import com.example.demo.application.shared.command.UpdateJobCronCommand;
import com.example.demo.application.shared.exception.InvalidCronException;
import com.example.demo.application.shared.exception.JobNotFoundException;
import com.example.demo.application.shared.exception.ScheduleEngineException;
import com.example.demo.application.shared.view.ScheduleJobView;
import com.example.demo.infra.persistence.ScheduledJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <h2>ScheduledJobApplicationService</h2>
 * <p>
 * 排程任務應用層調度服務。作為領域模型 (Domain Model) 與外部執行引擎 (Quartz) 之間的協調者。
 * </p>
 *
 * <p>
 * <b>架構特性：</b>
 * </p>
 * <ul>
 * <li><b>事務原子性：</b> 依賴共享 DataSource，確保業務資料表與 Quartz 系統表在同一交易內更新。</li>
 * <li><b>領域保護：</b> 透過 {@link CronParserPort} 確保只有合法的 Cron 表達式能進入領域層。</li>
 * <li><b>容錯設計：</b> 具備服務降級機制，當 Quartz 引擎異常時，仍能提供基礎配置查詢。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobApplicationService {

    private final ScheduledJobRepository repository; // 持久化 (DB)
    private final JobSchedulerPort jobScheduler; // 執行引擎 (Quartz Adapter)
    private final CronParserPort cronParser; // 領域門神 (Cron 校驗與解析)

    /**
     * <h2>初始化排程任務</h2>
     * <p>
     * 系統啟動或手動觸發時，同步排程定義至資料庫與 Quartz 引擎。
     * </p>
     *
     * <p>
     * <b>執行邏輯：</b>
     * </p>
     * <ol>
     * <li>檢查資料庫，若無紀錄則建立新的 {@link ScheduledJob} 聚合根。</li>
     * <li>調用引擎適配器進行註冊，利用 Replace 模式確保 Cron 設定與程式碼同步。</li>
     * </ol>
     *
     * @param command 包含任務名稱、群組及初始配置的指令
     * @throws RuntimeException 若 Quartz 引擎註冊失敗且無法復原時拋出
     */
    @Transactional
    public void initializeTask(CreateJobCommand command) {
        Optional<ScheduledJob> existingJob = repository.findByNameAndGroup(command.name(), command.group());

        if (existingJob.isEmpty()) {
            log.info("初始化新排程紀錄: {} - {}", command.name(), command.group());

            // 這裡亦建議使用 parser 確保初始 Cron 的合法性
            CronExpression cron = cronParser.parse(command.cronExpression());
            ScheduledJob newJob = ScheduledJob.register(command.name(), command.group(), command.jobType(),
                    cron);
            repository.save(newJob);
        } else {
            log.info("排程配置已存在，準備執行引擎同步: {}", command.name());
        }

        try {
            RegisterJobCommand registerJobCommand = new RegisterJobCommand(command.name(),
                    command.group(), command.cronExpression(), command.jobType());
            jobScheduler.add(registerJobCommand);
        } catch (Exception e) {
            log.error("Quartz 引擎註冊失敗: {}.{}", command.group(), command.name(), e);
            throw new RuntimeException("系統排程初始化失敗", e);
        }
    }

    /**
     * <h2>更新任務排程週期 (Cron Expression)</h2>
     * <p>
     * 修改現有任務的執行頻率。此操作具備「強校驗」特性，無效的 Cron 將被攔截於領域層之外。
     * </p>
     *
     * @param command 包含任務標識與新 Cron 字串的指令
     * @throws JobNotFoundException    若找不到對應排程
     * @throws InvalidCronException    若 Cron 格式不符合引擎規範
     * @throws ScheduleEngineException 若引擎重新調度失敗
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateJobCron(UpdateJobCronCommand command) {
        // 1. 載入聚合根
        ScheduledJob job = repository.findByNameAndGroup(command.name(), command.group())
                .orElseThrow(() -> new JobNotFoundException(command.name()));

        // 2. 透過門神 (Parser) 取得合法的 VO。若格式錯誤，此處會拋出 InvalidCronException 並回滾。
        CronExpression newCron = cronParser.parse(command.newCron());

        // 3. 領域聚合根更新狀態
        job.updateCron(newCron);

        // 4. 同步至執行引擎
        try {
            // 注意：此處應調用 jobScheduler.updateCron 以確保引擎同步
            jobScheduler.updateCron(command);
        } catch (Exception e) {
            throw new ScheduleEngineException("UPDATE_CRON", job.getName(), e);
        }

        // 5. 保存業務狀態
        repository.save(job);
        log.info("任務 Cron 更新成功: {}.{}", job.getGroup(), job.getName());
    }

    /**
     * <h2>暫停排程任務</h2>
     * <p>
     * 變更任務狀態為暫停。若引擎同步失敗，將拋出異常並回滾資料庫修改。
     * </p>
     *
     * @param id 任務領域標識碼
     * @throws JobNotFoundException    當 ID 無效時
     * @throws ScheduleEngineException 當引擎操作異常時
     */
    @Transactional(rollbackFor = Exception.class)
    public void pauseTask(String id) {
        ScheduledJob job = repository.findByJobId(new JobId(id)).orElseThrow(() -> new JobNotFoundException(id));

        job.pause();

        try {
            jobScheduler.pause(job.getName(), job.getGroup());
        } catch (Exception e) {
            throw new ScheduleEngineException("PAUSE", job.getName(), e);
        }

        repository.save(job);
        log.info("任務暫停成功: {}.{}", job.getGroup(), job.getName());
    }

    /**
     * <h2>獲取排程監控資源清單 (整合視圖)</h2>
     * <p>
     * 執行「內聯聚合（In-memory Join）」，將 DB 的靜態配置與 Quartz 的動態運行快照結合。
     * </p>
     *
     * <p>
     * <b>服務降級：</b> 若 Quartz 引擎連線失敗，執行狀態將標示為 {@code UNKNOWN}，確保頁面不崩潰。
     * </p>
     *
     * @return 整合後的視圖清單，包含領域狀態與引擎即時狀態
     */
    public List<ScheduleJobView> getJobInfoResources() {
        // 1. 取得 Master Data (DB)
        List<ScheduledJob> dbJobs = repository.findAll();

        // 2. 取得 Runtime Data (Quartz) - 具備 Try-Catch 降級保護
        List<ScheduleJobView> quartzJobs = getQuartzJobsSafe();

        // 3. 建立快取地圖
        Map<String, ScheduleJobView> quartzMap = quartzJobs.stream()
                .collect(Collectors.toMap(j -> j.getName() + "-" + j.getGroup(), j -> j, (exist, replace) -> exist));

        // 4. 數據聚合
        return dbJobs.stream().map(dbJob -> {
            String key = dbJob.getName() + "-" + dbJob.getGroup();
            ScheduleJobView qView = quartzMap.get(key);

            ScheduleJobView.ScheduleJobViewBuilder builder = ScheduleJobView.builder().jobId(dbJob.getJobId().value())
                    .name(dbJob.getName()).group(dbJob.getGroup()).jobType(dbJob.getJobType())
                    .domainStatus(dbJob.getStatus().name()).cronExpression(dbJob.getCron().value());

            if (qView != null) {
                builder.state(qView.getState()).nextFireTime(qView.getNextFireTime())
                        .triggerType(qView.getTriggerType()).intervalInSeconds(qView.getIntervalInSeconds());
            } else if (quartzJobs.isEmpty() && !dbJobs.isEmpty()) {
                // 降級分支：引擎離線
                builder.state("UNKNOWN (ENGINE_OFFLINE)");
            } else {
                // 異常分支：配置存在但引擎中無此任務
                builder.state("NOT_REGISTERED");
            }

            return builder.build();
        }).collect(Collectors.toList());
    }

    /**
     * <h2>恢復排程任務</h2>
     * <p>
     * 將暫停的任務重新排入執行隊列。
     * </p>
     *
     * @param id 任務領域標識碼
     */
    @Transactional(rollbackFor = Exception.class)
    public void resumeTask(String id) {
        ScheduledJob job = repository.findByJobId(new JobId(id)).orElseThrow(() -> new JobNotFoundException(id));

        job.resume();

        try {
            jobScheduler.resume(job.getName(), job.getGroup());
        } catch (Exception e) {
            throw new ScheduleEngineException("RESUME", job.getName(), e);
        }

        repository.save(job);
        log.info("任務恢復成功: {}", job.getName());
    }

    /**
     * 安全獲取 Quartz 運行數據。 當引擎發生通訊異常或資料庫鎖定時，回傳空清單以觸發降級邏輯。
     */
    private List<ScheduleJobView> getQuartzJobsSafe() {
        try {
            return jobScheduler.findAll();
        } catch (Exception e) {
            log.error("無法從 Quartz 取得狀態，啟動降級方案", e);
            return Collections.emptyList();
        }
    }
}