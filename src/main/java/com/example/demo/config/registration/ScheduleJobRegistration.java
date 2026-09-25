package com.example.demo.config.registration;

import com.example.demo.application.port.JobSchedulerPort;
import com.example.demo.application.service.ScheduledJobApplicationService;
import com.example.demo.application.shared.command.CreateJobCommand;
import com.example.demo.infra.quartz.listener.global.GlobalJobListener;
import com.example.demo.infra.quartz.listener.global.PersistJobLogListener;
import com.example.demo.infra.quartz.listener.impl.MessagePrintJobListener;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * <h2>JobScheduledRegistration</h2>
 *
 * <p>
 * 系統啟動時負責註冊所有 Quartz 排程 Job。
 * </p>
 *
 * <p>
 * 設計目的：
 * <ul>
 * <li>集中管理所有排程註冊邏輯</li>
 * <li>避免將排程分散在各個 Job 類別中</li>
 * <li>讓排程設定成為系統初始化的一部分</li>
 * </ul>
 * </p>
 *
 * <p>
 * 此類別不負責執行排程，只負責向 Scheduler 註冊。
 * </p>
 */
@Component
@AllArgsConstructor
public class ScheduleJobRegistration {

    private final JobSchedulerPort jobScheduler; // 執行引擎 (Quartz)
    private final GlobalJobListener globalJobListener; // 假設這是一個全域 Log 監聽器
    private final MessagePrintJobListener messagePrintJobListener;
    private final PersistJobLogListener persistJobLogListener; // 持久化日誌監聽器
    private final ScheduledJobApplicationService applicationService;

    @PostConstruct
    public void init() throws Exception {

        // --- A. 監聽器註冊區 ---

        // 1. 註冊全域監聽器 (處理日誌計時、性能監控)
        jobScheduler.registerGlobalListener(globalJobListener);
        jobScheduler.registerGlobalListener(persistJobLogListener); // 註冊持久化日誌監聽器

        // 2. 註冊特定任務監聽器 (例如 MessagePrintJob 專屬的業務處理)
        // 必須與 registerSystemJob 中的 name, group 保持一致
        jobScheduler.registerJobListener(messagePrintJobListener, "MessagePrintJob", "MessagePrintGroup");

        // --- B. 任務初始化區 (會同步至 DB 與 Quartz) ---

        // 1. 訊息列印任務 (每 1 分鐘)
        // 參數說明: 任務名稱, 群組名稱, Cron 表達式, Spring Bean 名稱
        this.registerSystemJob("MessagePrintJob", "MessagePrintGroup", "0 0/1 * * * ?", "messagePrintJob");

    }

    /**
     * 註冊排程 Job
     *
     * <p>
     * 將排程資訊封裝成 RegisterScheduleJobCommand， 並交由 ScheduleRegisterFactory 進行實際註冊。
     * </p>
     *
     * @param jobName        Job 名稱（唯一識別）
     * @param groupName      Job 所屬群組
     * @param cronExpression Cron 表達式
     * @param jobClass       Quartz Job 類型，對應 @Component 名稱
     */
    private void registerSystemJob(String jobName, String groupName, String cronExpression, String jobClass) {
        CreateJobCommand command = new CreateJobCommand(jobName, groupName, cronExpression, jobClass);
        applicationService.initializeTask(command);
    }

}