package com.example.demo.iface.rest;

import com.example.demo.application.service.ScheduledJobApplicationService;
import com.example.demo.application.shared.command.BindJobCalendarCommand;
import com.example.demo.application.shared.command.CreateCronJobCommand;
import com.example.demo.application.shared.command.CreateOneTimeJobCommand;
import com.example.demo.application.shared.command.UpdateJobCronCommand;
import com.example.demo.application.shared.view.ScheduleJobView;
import com.example.demo.iface.dto.req.BindJobCalendarResource;
import com.example.demo.iface.dto.req.CreateCronJobResource;
import com.example.demo.iface.dto.req.CreateOneTimeJobResource;
import com.example.demo.iface.dto.req.UpdateJobCronResource;
import com.example.demo.iface.dto.res.JobCronUpdatedResource;
import com.example.demo.iface.dto.res.ScheduleJobCreatedResource;
import com.example.demo.iface.dto.res.ScheduleJobPausedResource;
import com.example.demo.iface.dto.res.ScheduleJobResumedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "Schedule Job Management", description = "排程任務管理 API 介面")
@RestController
@AllArgsConstructor
@RequestMapping("/jobs")
public class ScheduleJobController {

    private ScheduledJobApplicationService applicationService;

    /**
     * 新增排程任務
     */
    @Operation(summary = "新增定時排程任務 (Cron)", description = "註冊一個基於 Cron 的排程任務到系統與 Quartz 引擎中")
    @PostMapping("/create-cron")
    public ResponseEntity<ScheduleJobCreatedResource> createCronJob(@RequestBody CreateCronJobResource request) {
        CreateCronJobCommand command = new CreateCronJobCommand(request.name(), request.group(), request.jobType(), request.cron(), request.calendarKey());
        applicationService.initializeCronTask(command);
        return new ResponseEntity<>(new ScheduleJobCreatedResource("201", "定時排程任務建立成功"), HttpStatus.CREATED);
    }

    @Operation(summary = "新增一次性排程任務", description = "註冊一個單次執行的排程任務到系統與 Quartz 引擎中")
    @PostMapping("/create-one-time")
    public ResponseEntity<ScheduleJobCreatedResource> createOneTimeJob(@RequestBody CreateOneTimeJobResource request) {
        LocalDate date = LocalDate.parse(request.executeDate(), java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        LocalTime time = LocalTime.parse(request.executeTime(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalDateTime executeDateTime = LocalDateTime.of(date, time);
        
        CreateOneTimeJobCommand command = new CreateOneTimeJobCommand(request.name(), request.group(), request.jobType(), executeDateTime);
        applicationService.initializeOneTimeTask(command);
        return new ResponseEntity<>(new ScheduleJobCreatedResource("201", "一次性排程任務建立成功"), HttpStatus.CREATED);
    }

    /**
     * 綁定或解除綁定排程任務的日曆
     */
    @Operation(summary = "綁定或解除綁定排程日曆", description = "為現有的排程任務綁定指定的日曆黑名單（傳入 null 或空字串表示解除綁定）")
    @PutMapping("/bind-calendar")
    public ResponseEntity<String> bindJobCalendar(@RequestBody BindJobCalendarResource request) {
        BindJobCalendarCommand command = new BindJobCalendarCommand(request.name(), request.group(), request.calendarKey());
        applicationService.bindCalendar(command);
        return ResponseEntity.ok("排程任務日曆綁定更新成功");
    }

    /**
     * 暫停特定的排程
     */
    @Operation(summary = "暫停特定的排程", description = "根據 JobId 暫停正在運行的排程任務")
    @PostMapping("/pause/{jobId}")
    public ResponseEntity<ScheduleJobPausedResource> pauseJob(@PathVariable("jobId") String jobId) {
        applicationService.pauseTask(jobId);
        return new ResponseEntity<>(new ScheduleJobPausedResource("200", "Job paused"), HttpStatus.OK);
    }

    /**
     * 重啟特定的排程
     */
    @Operation(summary = "重啟特定的排程", description = "根據 JobId 恢復已被暫停的排程任務")
    @PostMapping("/resume/{jobId}")
    public ResponseEntity<ScheduleJobResumedResource> resumeJob(@PathVariable("jobId") String jobId) {
        applicationService.resumeTask(jobId);
        return new ResponseEntity<>(new ScheduleJobResumedResource("200", "Job resumed"), HttpStatus.OK);
    }

    /**
     * 查詢系統內所有排程狀態
     */
    @Operation(summary = "查詢系統內所有排程狀態", description = "取得目前資料庫配置與 Quartz 引擎中的排程狀態快照")
    @GetMapping("/status")
    public ResponseEntity<List<ScheduleJobView>> getJobStatus() {
        return new ResponseEntity<>(applicationService.getJobInfoResources(), HttpStatus.OK);
    }

    /**
     * 更新特定排程的 cron
     */
    @Operation(summary = "更新特定排程的 Cron", description = "修改指定排程的執行週期，並進行語法校驗")
    @PostMapping("/update-cron")
    public ResponseEntity<JobCronUpdatedResource> updateCron(@RequestBody UpdateJobCronResource request) {
        UpdateJobCronCommand command = new UpdateJobCronCommand(request.name(), request.group(), request.scheduleType(), request.newCron(), request.executeTime());
        applicationService.updateJobCron(command);
        return ResponseEntity.ok(new JobCronUpdatedResource("200", "更新 cron 成功"));
    }
}
