package com.example.demo.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleJobIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Feature: 排程任務建立 (Schedule Job Creation)
     * Scenario: 建立一次性排程時，若該排程名稱與群組已存在，應被拒絕以保持領域狀態乾淨
     * <p>
     * Given 系統中尚無名為 "IntegrationOneTimeJob" 且群組為 "IntGroup" 的任務
     * When 客戶端首次發送 POST /jobs/create-one-time 請求建立該任務
     * Then 系統應回傳 201 Created，且成功將任務寫入資料庫與 Quartz 引擎
     * <p>
     * When 客戶端再次發送完全相同的 POST 請求嘗試建立同名任務
     * Then 系統應攔截此操作，回傳 409 Conflict，並且錯誤代碼為 "JOB_ALREADY_EXISTS"
     */
    @Test
    @DisplayName("整合測試：建立一次性排程，並驗證重複建立會報 409 Conflict")
    void testCreateOneTimeJobAndConflict() throws Exception {
        String requestJson = """
                {
                    "name": "IntegrationOneTimeJob",
                    "group": "IntGroup",
                    "jobType": "messagePrintJob",
                    "executeDate": "2026/12/31",
                    "executeTime": "23:59",
                    "requestsRecovery": false
                }
                """;

        // 1. 第一次建立，預期成功 201
        mockMvc.perform(post("/jobs/create-one-time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isCreated());

        // 2. 第二次建立，預期被擋下並回傳 409 CONFLICT
        mockMvc.perform(post("/jobs/create-one-time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_ALREADY_EXISTS"));
    }

    /**
     * Feature: 排程任務生命週期管理 (Schedule Job Lifecycle Management)
     * Scenario: 建立 Cron 定時任務後，可以透過暫停 API 成功將 Quartz 引擎中的排程狀態改為暫停
     * <p>
     * Given 系統中尚無名為 "IntegrationCronJob" 且群組為 "IntGroup" 的任務
     * When 客戶端發送 POST /jobs/create-cron 請求建立該定時任務
     * Then 系統應回傳 201 Created
     * <p>
     * Given 透過 GET /jobs/status 取得剛剛建立的任務之真實 jobId (領域 UUID)
     * When 客戶端針對該 jobId 發送 POST /jobs/pause/{jobId} 請求暫停任務
     * Then 系統應回傳 200 OK，代表暫停操作成功
     * <p>
     * When 客戶端再次發送 GET /jobs/status 查詢系統內所有任務狀態
     * Then 該任務在 Quartz 引擎中的即時狀態 (state) 應準確變更為 "PAUSED"
     */
    @Test
    @DisplayName("整合測試：建立 Cron 排程 -> 暫停 -> 查詢狀態")
    void testCreateCronJobAndPause() throws Exception {
        String cronRequestJson = """
                {
                    "name": "IntegrationCronJob",
                    "group": "IntGroup",
                    "jobType": "messagePrintJob",
                    "cron": "0 0 12 * * ?",
                    "calendarKey": null,
                    "requestsRecovery": false
                }
                """;

        // 1. 建立 Cron 排程
        mockMvc.perform(post("/jobs/create-cron")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(cronRequestJson))
                .andExpect(status().isCreated());

        // 2. 查詢列表，獲取由 DB 產生的 UUID (jobId)
        String responseBody = mockMvc.perform(get("/jobs/status")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 這裡我們用正則表達式或直接擷取測試用例產生的任務 jobId
        // 但為了簡化，直接使用 JsonPath 讀取
        String extractedJobId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.content[?(@.name == 'IntegrationCronJob')].jobId").toString().replaceAll("[\\[\\]\"]", "");

        // 3. 呼叫暫停 API (使用真正的 UUID jobId)
        mockMvc.perform(post("/jobs/pause/" + extractedJobId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());

        // 4. 再次查詢列表，驗證有出現該任務且狀態為 PAUSED
        mockMvc.perform(get("/jobs/status")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.name == 'IntegrationCronJob')].state").value("PAUSED"));
    }
}
