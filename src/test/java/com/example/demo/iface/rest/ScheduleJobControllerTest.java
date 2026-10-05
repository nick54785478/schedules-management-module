package com.example.demo.iface.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.application.service.ScheduledJobApplicationService;
import com.example.demo.application.shared.command.inbound.BindJobCalendarCommand;
import com.example.demo.application.shared.command.inbound.CreateCronJobCommand;
import com.example.demo.application.shared.command.inbound.CreateOneTimeJobCommand;
import com.example.demo.application.shared.command.inbound.UpdateScheduleCommand;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleJobGottenView;

@WebMvcTest(ScheduleJobController.class)
class ScheduleJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduledJobApplicationService applicationService;

    @Test
    @DisplayName("POST /jobs/create-cron - 應成功建立 Cron 排程並回傳 201")
    void createCronJob_ShouldReturn201() throws Exception {
        mockMvc.perform(post("/jobs/create-cron")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {
                            "name": "TestCronJob",
                            "group": "TestGroup",
                            "jobType": "testJobBean",
                            "cron": "0 0 12 * * ?",
                            "calendarKey": null,
                            "requestsRecovery": true
                        }
                        """))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("定時排程任務建立成功"));

        verify(applicationService, times(1)).initializeCronTask(any(CreateCronJobCommand.class));
    }

    @Test
    @DisplayName("POST /jobs/create-one-time - 應成功建立單次排程並回傳 201")
    void createOneTimeJob_ShouldReturn201() throws Exception {
        mockMvc.perform(post("/jobs/create-one-time")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {
                            "name": "TestOneTimeJob",
                            "group": "TestGroup",
                            "jobType": "testJobBean",
                            "executeDate": "2026/12/31",
                            "executeTime": "23:59",
                            "requestsRecovery": false
                        }
                        """))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("一次性排程任務建立成功"));

        verify(applicationService, times(1)).initializeOneTimeTask(any(CreateOneTimeJobCommand.class));
    }

    @Test
    @DisplayName("PUT /jobs/{jobId}/bind-calendar - 應成功綁定日曆並回傳 200")
    void bindJobCalendar_ShouldReturn200() throws Exception {
        mockMvc.perform(put("/jobs/TestGroup.TestJob/bind-calendar")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {
                            "calendarKey": "TAIWAN_HOLIDAY"
                        }
                        """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("排程任務日曆綁定更新成功"));

        verify(applicationService, times(1)).bindCalendar(any(BindJobCalendarCommand.class));
    }

    @Test
    @DisplayName("POST /jobs/pause/{jobId} - 應成功暫停任務並回傳 200")
    void pauseJob_ShouldReturn200() throws Exception {
        mockMvc.perform(post("/jobs/pause/TestGroup.TestJob")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Job paused"));

        verify(applicationService, times(1)).pauseTask("TestGroup.TestJob");
    }

    @Test
    @DisplayName("POST /jobs/resume/{jobId} - 應成功恢復任務並回傳 200")
    void resumeJob_ShouldReturn200() throws Exception {
        mockMvc.perform(post("/jobs/resume/TestGroup.TestJob")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Job resumed"));

        verify(applicationService, times(1)).resumeTask("TestGroup.TestJob");
    }

    @Test
    @DisplayName("PUT /jobs/{jobId}/update-cron - 應成功更新 Cron 並回傳 200")
    void updateCron_ShouldReturn200() throws Exception {
        mockMvc.perform(put("/jobs/TestGroup.TestJob/update-cron")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {
                            "newCron": "0 0 10 * * ?"
                        }
                        """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("更新 Cron 排程成功"));

        verify(applicationService, times(1)).updateJobSchedule(any(UpdateScheduleCommand.class));
    }

    @Test
    @DisplayName("GET /jobs/status - 應回傳分頁任務清單")
    void searchJobStatus_ShouldReturnPagedList() throws Exception {
        ScheduleJobGottenView mockView = ScheduleJobGottenView.builder()
                .name("TestJob")
                .group("TestGroup")
                .state("NORMAL")
                .cronExpression("0 0 12 * * ?")
                .domainStatus("NONE")
                .build();

        PageGottenView<ScheduleJobGottenView> mockPage = new PageGottenView<>(
                List.of(mockView),
                0, 10, 1L, 1
        );

        when(applicationService.getJobInfoResources(0, 10)).thenReturn(mockPage);

        mockMvc.perform(get("/jobs/status")
                .param("page", "0")
                .param("size", "10")
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("TestJob"));

        verify(applicationService, times(1)).getJobInfoResources(0, 10);
    }
}
