package com.example.demo.application.service;

import com.example.demo.application.domain.schedule.aggregate.ScheduledJob;
import com.example.demo.application.domain.schedule.aggregate.vo.ScheduleRule;
import com.example.demo.application.domain.schedule.aggregate.vo.ScheduleType;
import com.example.demo.application.domain.schedule.aggregate.vo.JobId;
import com.example.demo.application.port.CronParserPort;
import com.example.demo.application.port.JobSchedulerPort;
import com.example.demo.application.shared.command.inbound.BindJobCalendarCommand;
import com.example.demo.application.shared.command.inbound.CreateCronJobCommand;
import com.example.demo.application.shared.command.inbound.CreateOneTimeJobCommand;
import com.example.demo.application.shared.command.inbound.UpdateScheduleCommand;
import com.example.demo.application.shared.command.outbound.SyncJobToEngineCommand;
import com.example.demo.application.shared.exception.InvalidCronException;
import com.example.demo.application.shared.exception.JobNotFoundException;
import com.example.demo.application.shared.exception.ScheduleEngineException;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleJobGottenView;
import com.example.demo.application.domain.schedule.repository.ScheduledJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
     * <h2>初始化定時排程任務 (Cron)</h2>
     */
    @Transactional
    public void initializeCronTask(CreateCronJobCommand command) {
        ScheduledJob job = repository.findByNameAndGroup(command.name(), command.group()).orElse(null);

        if (job == null) {
            log.info("初始化新排程紀錄: {} - {}", command.name(), command.group());
            ScheduleRule rule = createScheduleRule(ScheduleType.CRON.name(), command.cronExpression(), null, command.calendarKey(), command.requestsRecovery());
            job = ScheduledJob.register(command.name(), command.group(), command.jobType(), rule);
            job = repository.save(job);
        } else {
            log.info("排程配置已存在，準備執行引擎同步: {}", command.name());
        }

        jobScheduler.add(new SyncJobToEngineCommand(job.getName(), job.getGroup(), job.getJobType(), job.getScheduleRule()));
    }

    /**
     * <h2>初始化一次性排程任務 (OneTime)</h2>
     */
    @Transactional
    public void initializeOneTimeTask(CreateOneTimeJobCommand command) {
        ScheduledJob job = repository.findByNameAndGroup(command.name(), command.group()).orElse(null);

        if (job == null) {
            log.info("初始化新一次性排程紀錄: {} - {}", command.name(), command.group());
            ScheduleRule rule = createScheduleRule(ScheduleType.ONE_TIME.name(), null, command.executeTime(), null, command.requestsRecovery());
            job = ScheduledJob.register(command.name(), command.group(), command.jobType(), rule);
            job = repository.save(job);
        } else {
            log.info("排程配置已存在，準備執行引擎同步: {}", command.name());
        }

        jobScheduler.add(new SyncJobToEngineCommand(job.getName(), job.getGroup(), job.getJobType(), job.getScheduleRule()));
    }

    /**
     * <h2>更新任務排程週期</h2>
     * <p>
     * 修改現有任務的執行頻率。此操作具備「強校驗」特性，無效的 Cron 將被攔截於領域層之外。
     * </p>
     *
     * @param command 包含任務標識碼與新排程設定的指令
     * @throws JobNotFoundException    若找不到對應排程
     * @throws InvalidCronException    若 Cron 格式不符合引擎規範
     * @throws ScheduleEngineException 若引擎重新調度失敗
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateJobSchedule(UpdateScheduleCommand command) {
        // 1. 載入聚合根
        ScheduledJob job = repository.findByJobId(new JobId(command.jobId()))
                .orElseThrow(() -> new JobNotFoundException(command.jobId()));

        // 2. 建立新規則，並繼承既有的 calendarKey。若格式錯誤，會拋出對應 Exception。
        String existingCalendarKey = job.getScheduleRule().getCalendarKey();
        ScheduleRule newRule = createScheduleRule(command.scheduleType(), command.newCron(), command.executeTime(), existingCalendarKey, job.getScheduleRule().isRequestsRecovery());

        // 3. 領域聚合根更新狀態
        job.changeSchedule(newRule);

        // 4. 同步至執行引擎
        // 由於 JobSchedulerAdapter.add() 的 replace=true 會覆寫 Trigger，也能正確帶入最新的 rule
        jobScheduler.add(new SyncJobToEngineCommand(job.getName(), job.getGroup(), job.getJobType(), job.getScheduleRule()));

        // 5. 保存業務狀態
        repository.save(job);
        log.info("任務 Cron 更新成功: {}.{}", job.getGroup(), job.getName());
    }
    /**
     * <h2>綁定/解除綁定日曆</h2>
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindCalendar(BindJobCalendarCommand command) {
        ScheduledJob job = repository.findByJobId(new JobId(command.jobId()))
                .orElseThrow(() -> new JobNotFoundException(command.jobId()));

        ScheduleRule currentRule = job.getScheduleRule();
        ScheduleRule newRule = createScheduleRule(
                currentRule.getType().name(), 
                currentRule.getCronExpression(), 
                currentRule.getExecuteTime(), 
                command.calendarKey(),
                currentRule.isRequestsRecovery());

        job.changeSchedule(newRule);

        // 將更新後的 Trigger 重新註冊到 Quartz
        jobScheduler.add(new SyncJobToEngineCommand(job.getName(), job.getGroup(), job.getJobType(), job.getScheduleRule()));

        repository.save(job);
        log.info("任務日曆綁定更新成功: {}.{}", job.getGroup(), job.getName());
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

        jobScheduler.pause(job.getName(), job.getGroup());

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
    public PageGottenView<ScheduleJobGottenView> getJobInfoResources(int page, int size) {
        // 1. 取得 Master Data (DB) - 使用分頁
        Page<ScheduledJob> dbJobsPage = repository.findAll(PageRequest.of(page, size));
        List<ScheduledJob> dbJobs = dbJobsPage.getContent();

        // 2. 取得 Runtime Data (Quartz) - 具備 Try-Catch 降級保護
        List<ScheduleJobGottenView> quartzJobs = getQuartzJobsSafe();

        // 3. 建立快取地圖
        Map<String, ScheduleJobGottenView> quartzMap = quartzJobs.stream()
                .collect(Collectors.toMap(j -> j.getName() + "-" + j.getGroup(), j -> j, (exist, replace) -> exist));

        // 4. 數據聚合
        List<ScheduleJobGottenView> viewList = dbJobs.stream().map(dbJob -> {
            String key = dbJob.getName() + "-" + dbJob.getGroup();
            ScheduleJobGottenView qView = quartzMap.get(key);

            ScheduleJobGottenView.ScheduleJobGottenViewBuilder builder = ScheduleJobGottenView.builder().jobId(dbJob.getJobId().value())
                    .name(dbJob.getName()).group(dbJob.getGroup()).jobType(dbJob.getJobType())
                    .domainStatus(dbJob.getStatus().name())
                    .cronExpression(dbJob.getScheduleRule().getType() == ScheduleType.CRON ? dbJob.getScheduleRule().getCronExpression() : dbJob.getScheduleRule().getExecuteTime().toString());

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

        return new com.example.demo.application.shared.view.PageGottenView<>(
                viewList,
                dbJobsPage.getNumber(),
                dbJobsPage.getSize(),
                dbJobsPage.getTotalElements(),
                dbJobsPage.getTotalPages()
        );
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

        jobScheduler.resume(job.getName(), job.getGroup());

        repository.save(job);
        log.info("任務恢復成功: {}", job.getName());
    }

    /**
     * <h2>完成一次性排程任務</h2>
     * <p>
     * 當一次性任務執行完畢時，由全域監聽器呼叫，更新資料庫狀態為已完成。
     * </p>
     */
    @Transactional
    public void completeOneTimeJob(String name, String group) {
        repository.findByNameAndGroup(name, group).ifPresent(job -> {
            if (job.getScheduleRule() != null && job.getScheduleRule().isOneTime()) {
                job.complete();
                repository.save(job);
                log.info("一次性任務執行完畢，狀態更新為 COMPLETED: {}.{}", group, name);
            }
        });
    }

    /**
     * 安全獲取 Quartz 運行數據。 當引擎發生通訊異常或資料庫鎖定時，回傳空清單以觸發降級邏輯。
     */
    private List<ScheduleJobGottenView> getQuartzJobsSafe() {
        try {
            return jobScheduler.findAll();
        } catch (ScheduleEngineException e) {
            log.error("無法從 Quartz 取得狀態，啟動降級方案", e);
            return Collections.emptyList();
        }
    }

    /**
     * 建立排程規則
     */
    private ScheduleRule createScheduleRule(String scheduleTypeStr, String cronExpression, java.time.LocalDateTime executeTime, String calendarKey, boolean requestsRecovery) {
        ScheduleType type = ScheduleType.valueOf(scheduleTypeStr);
        if (type == ScheduleType.CRON) {
            // 保留原本透過 Port 解析校驗的邏輯
            cronParser.parse(cronExpression);
            return ScheduleRule.cron(cronExpression, calendarKey, requestsRecovery);
        } else if (type == ScheduleType.ONE_TIME) {
            return ScheduleRule.oneTime(executeTime, requestsRecovery);
        }
        throw new IllegalArgumentException("不支援的排程類型: " + scheduleTypeStr);
    }
}
