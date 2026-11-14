package com.example.demo.iface.rest;

import com.example.demo.application.port.in.BindJobCalendarUseCase;
import com.example.demo.application.port.in.CreateCronJobUseCase;
import com.example.demo.application.port.in.CreateOneTimeJobUseCase;
import com.example.demo.application.port.in.GetScheduleJobUseCase;
import com.example.demo.application.port.in.PauseScheduleJobUseCase;
import com.example.demo.application.port.in.ResumeScheduleJobUseCase;
import com.example.demo.application.port.in.UpdateScheduleJobUseCase;
import com.example.demo.application.shared.command.inbound.BindJobCalendarCommand;
import com.example.demo.application.shared.command.inbound.CreateCronJobCommand;
import com.example.demo.application.shared.command.inbound.CreateOneTimeJobCommand;
import com.example.demo.application.shared.command.inbound.UpdateScheduleCommand;
import com.example.demo.application.shared.query.GetScheduleJobQuery;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleJobGottenView;
import com.example.demo.iface.dto.req.BindJobCalendarResource;
import com.example.demo.iface.dto.req.CreateCronJobResource;
import com.example.demo.iface.dto.req.CreateOneTimeJobResource;
import com.example.demo.iface.dto.req.UpdateCronJobResource;
import com.example.demo.iface.dto.req.UpdateOneTimeJobResource;
import com.example.demo.iface.dto.res.JobCalendarBoundResource;
import com.example.demo.iface.dto.res.JobCronUpdatedResource;
import com.example.demo.iface.dto.res.PagedJobStatusSearchedResource;
import com.example.demo.iface.dto.res.ScheduleJobCreatedResource;
import com.example.demo.iface.dto.res.ScheduleJobPausedResource;
import com.example.demo.iface.dto.res.ScheduleJobResumedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Tag(name = "Schedule Job Management", description = "排程任務管理 API 介面")
@RestController
@AllArgsConstructor
@RequestMapping("/jobs")
public class ScheduleJobController {

    private final CreateCronJobUseCase createCronJobUseCase;
    private final CreateOneTimeJobUseCase createOneTimeJobUseCase;
    private final BindJobCalendarUseCase bindJobCalendarUseCase;
    private final PauseScheduleJobUseCase pauseScheduleJobUseCase;
    private final ResumeScheduleJobUseCase resumeScheduleJobUseCase;
    private final GetScheduleJobUseCase getScheduleJobUseCase;
    private final UpdateScheduleJobUseCase updateScheduleJobUseCase;

    /**
     * 新增排程任務
     */
    @Operation(summary = "新增定時排程任務 (Cron)", description = "註冊一個基於 Cron 的排程任務到系統與 Quartz 引擎中")
    @PostMapping("/create-cron")
    public ResponseEntity<ScheduleJobCreatedResource> createCronJob(@Valid @RequestBody CreateCronJobResource request) {
        CreateCronJobCommand command = new CreateCronJobCommand(request.name(), request.group(), request.jobType(), request.cron(), request.calendarKey(), request.requestsRecovery());
        createCronJobUseCase.initializeCronTask(command);
        return new ResponseEntity<>(new ScheduleJobCreatedResource("201", "定時排程任務建立成功"), HttpStatus.CREATED);
    }

    @Operation(summary = "新增一次性排程任務", description = "註冊一個單次執行的排程任務到系統與 Quartz 引擎中")
    @PostMapping("/create-one-time")
    public ResponseEntity<ScheduleJobCreatedResource> createOneTimeJob(@Valid @RequestBody CreateOneTimeJobResource request) {
        LocalDate date = LocalDate.parse(request.executeDate(), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        LocalTime time = LocalTime.parse(request.executeTime(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalDateTime executeDateTime = LocalDateTime.of(date, time);

        CreateOneTimeJobCommand command = new CreateOneTimeJobCommand(request.name(), request.group(), request.jobType(), executeDateTime, request.requestsRecovery());
        createOneTimeJobUseCase.initializeOneTimeTask(command);
        return new ResponseEntity<>(new ScheduleJobCreatedResource("201", "一次性排程任務建立成功"), HttpStatus.CREATED);
    }

    /**
     * 綁定或解除綁定排程任務的日曆
     */
    @Operation(summary = "綁定或解除綁定排程日曆", description = "為現有的排程任務綁定指定的日曆黑名單（傳入 null 或空字串表示解除綁定）")
    @PutMapping("/{jobId}/bind-calendar")
    public ResponseEntity<JobCalendarBoundResource> bindJobCalendar(@PathVariable("jobId") String jobId, @Valid @RequestBody BindJobCalendarResource request) {
        BindJobCalendarCommand command = new BindJobCalendarCommand(jobId, request.calendarKey());
        bindJobCalendarUseCase.bindCalendar(command);
        return ResponseEntity.ok(new JobCalendarBoundResource("200", "排程任務日曆綁定更新成功"));
    }

    /**
     * 暫停特定的排程
     */
    @Operation(summary = "暫停特定的排程", description = "根據 JobId 暫停正在運行的排程任務")
    @PostMapping("/pause/{jobId}")
    public ResponseEntity<ScheduleJobPausedResource> pauseJob(@PathVariable("jobId") String jobId) {
        pauseScheduleJobUseCase.pauseTask(jobId);
        return new ResponseEntity<>(new ScheduleJobPausedResource("200", "Job paused"), HttpStatus.OK);
    }

    /**
     * 重啟特定的排程
     */
    @Operation(summary = "重啟特定的排程", description = "根據 JobId 恢復已被暫停的排程任務")
    @PostMapping("/resume/{jobId}")
    public ResponseEntity<ScheduleJobResumedResource> resumeJob(@PathVariable("jobId") String jobId) {
        resumeScheduleJobUseCase.resumeTask(jobId);
        return new ResponseEntity<>(new ScheduleJobResumedResource("200", "Job resumed"), HttpStatus.OK);
    }

    /**
     * <h2>查詢與篩選排程任務狀態清單</h2>
     * <p>
     * 取得目前資料庫配置與 Quartz 引擎中的排程狀態快照。
     * 支援多維度動態條件過濾（狀態、Job Type、群組、排程類型）與分頁機制。
     * </p>
     *
     * @param page         頁碼 (0-based，預設為 0)
     * @param size         每頁筆數 (預設為 10)
     * @param status       排程狀態 (如: NORMAL, PAUSED, STOPPED, COMPLETED)
     * @param jobType      Job Bean 名稱 (如: batchSendEmailJob)
     * @param jobGroup     任務所屬群組 (與 group 參數互通)
     * @param group        任務所屬群組 (別名)
     * @param scheduleType 排程類型 (如: CRON, ONE_TIME)
     * @return 分頁任務清單視圖與執行引擎狀態快照
     */
    @Operation(
            summary = "查詢與篩選排程任務狀態清單",
            description = "分頁查詢排程任務清單並取得 Quartz 即時狀態快照。可選配多維度條件過濾：status (任務狀態)、jobType (Bean 名稱)、jobGroup (所屬群組) 與 scheduleType (CRON 或 ONE_TIME)。若 Quartz 引擎離線，系統會自動降級標記為 UNKNOWN (ENGINE_OFFLINE) 並呈現資料庫靜態配置。"
    )
    @GetMapping("/status")
    public ResponseEntity<PagedJobStatusSearchedResource> searchJobStatus(
            @Parameter(description = "頁碼 (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每頁筆數") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排程狀態 (如: NORMAL, PAUSED, STOPPED, COMPLETED)") @RequestParam(required = false) String status,
            @Parameter(description = "Job Bean 名稱 (技術標籤)") @RequestParam(required = false) String jobType,
            @Parameter(description = "任務所屬群組") @RequestParam(required = false) String jobGroup,
            @Parameter(description = "任務所屬群組 (別名)") @RequestParam(required = false) String group,
            @Parameter(description = "排程類型 (如: CRON, ONE_TIME)") @RequestParam(required = false) String scheduleType) {
        String effectiveGroup = (jobGroup != null && !jobGroup.isBlank()) ? jobGroup : group;
        GetScheduleJobQuery query = GetScheduleJobQuery.of(page, size, status, jobType, effectiveGroup, scheduleType);
        PageGottenView<ScheduleJobGottenView> data = getScheduleJobUseCase.getJobInfoResources(query);
        return new ResponseEntity<>(new PagedJobStatusSearchedResource("200",
                "Success", data), HttpStatus.OK);
    }

    /**
     * 更新 Cron 排程執行時間
     */
    @Operation(summary = "更新 Cron 排程執行時間", description = "修改指定 Cron 排程的執行週期")
    @PutMapping("/{jobId}/update-cron")
    public ResponseEntity<JobCronUpdatedResource> updateCron(@PathVariable("jobId") String jobId, @Valid @RequestBody UpdateCronJobResource request) {
        UpdateScheduleCommand command = new UpdateScheduleCommand(jobId, "CRON", request.newCron(), null);
        updateScheduleJobUseCase.updateJobSchedule(command);
        return ResponseEntity.ok(new JobCronUpdatedResource("200", "更新 Cron 排程成功"));
    }

    /**
     * 更新單次排程執行時間
     */
    @Operation(summary = "更新單次排程執行時間", description = "修改指定單次排程的執行時間")
    @PutMapping("/{jobId}/update-one-time")
    public ResponseEntity<JobCronUpdatedResource> updateOneTime(@PathVariable("jobId") String jobId, @Valid @RequestBody UpdateOneTimeJobResource request) {
        LocalDate date = LocalDate.parse(request.executeDate(), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        LocalTime time = LocalTime.parse(request.executeTime(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalDateTime executeDateTime = LocalDateTime.of(date, time);
        
        UpdateScheduleCommand command = new UpdateScheduleCommand(jobId, "ONE_TIME", null, executeDateTime);
        updateScheduleJobUseCase.updateJobSchedule(command);
        return ResponseEntity.ok(new JobCronUpdatedResource("200", "更新單次排程成功"));
    }
}

