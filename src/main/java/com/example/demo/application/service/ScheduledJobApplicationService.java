package com.example.demo.application.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.in.BindJobCalendarUseCase;
import com.example.demo.application.port.in.CompleteScheduleJobUseCase;
import com.example.demo.application.port.in.CreateCronJobUseCase;
import com.example.demo.application.port.in.CreateOneTimeJobUseCase;
import com.example.demo.application.port.in.GetScheduleJobUseCase;
import com.example.demo.application.port.in.PauseScheduleJobUseCase;
import com.example.demo.application.port.in.ResumeScheduleJobUseCase;
import com.example.demo.application.port.in.UpdateScheduleJobUseCase;
import com.example.demo.application.port.out.CronParserPort;
import com.example.demo.application.port.out.JobSchedulerPort;
import com.example.demo.application.port.out.ScheduledJobRepositoryPort;
import com.example.demo.application.shared.command.inbound.BindJobCalendarCommand;
import com.example.demo.application.shared.command.inbound.CreateCronJobCommand;
import com.example.demo.application.shared.command.inbound.CreateOneTimeJobCommand;
import com.example.demo.application.shared.command.inbound.UpdateScheduleCommand;
import com.example.demo.application.shared.command.outbound.SyncJobToEngineCommand;
import com.example.demo.application.shared.exception.InvalidCronException;
import com.example.demo.application.shared.exception.JobAlreadyExistsException;
import com.example.demo.application.shared.exception.JobNotFoundException;
import com.example.demo.application.shared.exception.ScheduleEngineException;
import com.example.demo.application.shared.model.ScheduledJobModel;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleJobGottenView;
import com.example.demo.application.shared.vo.JobId;
import com.example.demo.application.shared.vo.JobStatus;
import com.example.demo.application.shared.vo.ScheduleRule;
import com.example.demo.application.shared.vo.ScheduleType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>ScheduledJobApplicationService</h2>
 * <p>
 * 排程任務應用層調度服務，實作多項 {@code ...UseCase} Inbound Port 合約。
 * 作為用例協調者，負責協調資料持久化與外部執行引擎 (Quartz)。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobApplicationService implements
        CreateCronJobUseCase,
        CreateOneTimeJobUseCase,
        UpdateScheduleJobUseCase,
        BindJobCalendarUseCase,
        PauseScheduleJobUseCase,
        ResumeScheduleJobUseCase,
        CompleteScheduleJobUseCase,
        GetScheduleJobUseCase {

    private final ScheduledJobRepositoryPort repository;
    private final JobSchedulerPort jobScheduler;
    private final CronParserPort cronParser;

    /**
     * <h2>初始化定時排程任務 (Cron)</h2>
     */
    @Override
    @Transactional
    public void initializeCronTask(CreateCronJobCommand command) {
        ScheduledJobModel job = repository.findByNameAndGroup(command.name(), command.group()).orElse(null);

        if (job == null) {
            log.info("初始化新排程紀錄: {} - {}", command.name(), command.group());
            ScheduleRule rule = createScheduleRule(ScheduleType.CRON.name(), command.cronExpression(), null, command.calendarKey(), command.requestsRecovery());
            job = ScheduledJobModel.create(command.name(), command.group(), command.jobType(), rule);
            job = repository.save(job);
        } else {
            log.info("排程配置已存在，準備執行引擎同步: {}", command.name());
        }

        jobScheduler.add(new SyncJobToEngineCommand(job.name(), job.group(), job.jobType(), job.scheduleRule()));
    }

    /**
     * <h2>初始化一次性排程任務 (OneTime)</h2>
     */
    @Override
    @Transactional
    public void initializeOneTimeTask(CreateOneTimeJobCommand command) {
        ScheduledJobModel job = repository.findByNameAndGroup(command.name(), command.group()).orElse(null);

        if (job != null) {
            throw new JobAlreadyExistsException(command.name(), command.group());
        }

        log.info("初始化新一次性排程紀錄: {} - {}", command.name(), command.group());
        ScheduleRule rule = createScheduleRule(ScheduleType.ONE_TIME.name(), null, command.executeTime(), null, command.requestsRecovery());
        job = ScheduledJobModel.create(command.name(), command.group(), command.jobType(), rule);
        job = repository.save(job);

        jobScheduler.add(new SyncJobToEngineCommand(job.name(), job.group(), job.jobType(), job.scheduleRule()));
    }

    /**
     * <h2>更新任務排程週期</h2>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateJobSchedule(UpdateScheduleCommand command) {
        ScheduledJobModel job = repository.findByJobId(new JobId(command.jobId()))
                .orElseThrow(() -> new JobNotFoundException(command.jobId()));

        String existingCalendarKey = job.scheduleRule().getCalendarKey();
        ScheduleRule newRule = createScheduleRule(command.scheduleType(), command.newCron(), command.executeTime(), existingCalendarKey, job.scheduleRule().isRequestsRecovery());

        job = job.withScheduleRule(newRule);
        jobScheduler.add(new SyncJobToEngineCommand(job.name(), job.group(), job.jobType(), job.scheduleRule()));

        repository.save(job);
        log.info("任務 Cron 更新成功: {}.{}", job.group(), job.name());
    }

    /**
     * <h2>綁定/解除綁定日曆</h2>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindCalendar(BindJobCalendarCommand command) {
        ScheduledJobModel job = repository.findByJobId(new JobId(command.jobId()))
                .orElseThrow(() -> new JobNotFoundException(command.jobId()));

        ScheduleRule currentRule = job.scheduleRule();
        ScheduleRule newRule = createScheduleRule(
                currentRule.getType().name(), 
                currentRule.getCronExpression(), 
                currentRule.getExecuteTime(), 
                command.calendarKey(),
                currentRule.isRequestsRecovery());

        job = job.withScheduleRule(newRule);

        jobScheduler.add(new SyncJobToEngineCommand(job.name(), job.group(), job.jobType(), job.scheduleRule()));
        repository.save(job);
        log.info("任務日曆綁定更新成功: {}.{}", job.group(), job.name());
    }

    /**
     * <h2>暫停排程任務</h2>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseTask(String id) {
        ScheduledJobModel job = repository.findByJobId(new JobId(id)).orElseThrow(() -> new JobNotFoundException(id));

        job = job.withStatus(JobStatus.PAUSED);
        jobScheduler.pause(job.name(), job.group());

        repository.save(job);
        log.info("任務暫停成功: {}", job.name());
    }

    /**
     * <h2>查詢任務列表 (分頁)</h2>
     */
    @Override
    public PageGottenView<ScheduleJobGottenView> getJobInfoResources(int page, int size) {
        PageGottenView<ScheduledJobModel> dbJobsPage = repository.findAll(page, size);
        List<ScheduledJobModel> dbJobs = dbJobsPage.content();

        List<ScheduleJobGottenView> quartzJobs = getQuartzJobsSafe();

        Map<String, ScheduleJobGottenView> quartzMap = quartzJobs.stream()
                .collect(Collectors.toMap(j -> j.getName() + "-" + j.getGroup(), j -> j, (exist, replace) -> exist));

        List<ScheduleJobGottenView> viewList = dbJobs.stream().map(dbJob -> {
            String key = dbJob.name() + "-" + dbJob.group();
            ScheduleJobGottenView qView = quartzMap.get(key);

            ScheduleJobGottenView.ScheduleJobGottenViewBuilder builder = ScheduleJobGottenView.builder()
                    .jobId(dbJob.jobId().value())
                    .name(dbJob.name())
                    .group(dbJob.group())
                    .jobType(dbJob.jobType())
                    .domainStatus(dbJob.status().name())
                    .cronExpression(dbJob.scheduleRule().getType() == ScheduleType.CRON ? dbJob.scheduleRule().getCronExpression() : dbJob.scheduleRule().getExecuteTime().toString());

            if (qView != null) {
                builder.state(qView.getState()).nextFireTime(qView.getNextFireTime())
                        .triggerType(qView.getTriggerType()).intervalInSeconds(qView.getIntervalInSeconds());
            } else if (quartzJobs.isEmpty() && !dbJobs.isEmpty()) {
                builder.state("UNKNOWN (ENGINE_OFFLINE)");
            } else {
                builder.state("NOT_REGISTERED");
            }

            return builder.build();
        }).toList();

        return new PageGottenView<>(
                viewList,
                dbJobsPage.pageNumber(),
                dbJobsPage.pageSize(),
                dbJobsPage.totalElements(),
                dbJobsPage.totalPages()
        );
    }

    /**
     * <h2>恢復排程任務</h2>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeTask(String id) {
        ScheduledJobModel job = repository.findByJobId(new JobId(id)).orElseThrow(() -> new JobNotFoundException(id));

        job = job.withStatus(JobStatus.NORMAL);
        jobScheduler.resume(job.name(), job.group());

        repository.save(job);
        log.info("任務恢復成功: {}", job.name());
    }

    /**
     * <h2>完成一次性排程任務</h2>
     */
    @Override
    @Transactional
    public void completeOneTimeJob(String name, String group) {
        repository.findByNameAndGroup(name, group).ifPresent(job -> {
            if (job.scheduleRule() != null && job.scheduleRule().isOneTime()) {
                ScheduledJobModel completedJob = job.withStatus(JobStatus.COMPLETED);
                repository.save(completedJob);
                log.info("一次性任務執行完畢，狀態更新為 COMPLETED: {}.{}", group, name);
            }
        });
    }

    private List<ScheduleJobGottenView> getQuartzJobsSafe() {
        try {
            return jobScheduler.findAll();
        } catch (ScheduleEngineException e) {
            log.error("無法從 Quartz 取得狀態，啟動降級方案", e);
            return Collections.emptyList();
        }
    }

    private ScheduleRule createScheduleRule(String scheduleTypeStr, String cronExpression, LocalDateTime executeTime, String calendarKey, boolean requestsRecovery) {
        ScheduleType type = ScheduleType.valueOf(scheduleTypeStr);
        if (type == ScheduleType.CRON) {
            cronParser.parse(cronExpression);
            return ScheduleRule.cron(cronExpression, calendarKey, requestsRecovery);
        } else if (type == ScheduleType.ONE_TIME) {
            return ScheduleRule.oneTime(executeTime, requestsRecovery);
        }
        throw new IllegalArgumentException("不支援的排程類型: " + scheduleTypeStr);
    }
}
