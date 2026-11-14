package com.example.demo.application.shared.query;

import com.example.demo.application.shared.vo.JobStatus;
import com.example.demo.application.shared.vo.ScheduleType;

/**
 * <h2>GetScheduleJobQuery</h2>
 * <p>
 * 查詢排程任務清單之 Query 物件，封裝分頁參數與動態過濾條件。
 * 遵循 <code>AGENTS.md</code> 規範（V + N + Query 命名風格），作為應用層與表現層之間傳遞查詢條件的純資料載體。
 * </p>
 *
 * @param page         頁碼 (0-based)
 * @param size         每頁筆數
 * @param status       排程狀態 (如 NORMAL, PAUSED, STOPPED, COMPLETED)
 * @param jobType      技術標籤 (對應 Spring Bean 名稱)
 * @param jobGroup     任務所屬群組
 * @param scheduleType 排程類型 (CRON 或 ONE_TIME)
 */
public record GetScheduleJobQuery(
        int page,
        int size,
        JobStatus status,
        String jobType,
        String jobGroup,
        ScheduleType scheduleType
) {

    /**
     * 便捷靜態工廠方法，支援字串型別的安全解析與轉換
     *
     * @param page         頁碼 (0-based)
     * @param size         每頁筆數
     * @param status       狀態字串 (忽略大小寫)
     * @param jobType      Job Bean 名稱
     * @param jobGroup     群組名稱
     * @param scheduleType 排程類型字串 (忽略大小寫)
     * @return 解析完成的 {@link GetScheduleJobQuery}
     */
    public static GetScheduleJobQuery of(int page, int size, String status, String jobType, String jobGroup, String scheduleType) {
        JobStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            parsedStatus = JobStatus.valueOf(status.toUpperCase().trim());
        }

        ScheduleType parsedScheduleType = null;
        if (scheduleType != null && !scheduleType.isBlank()) {
            parsedScheduleType = ScheduleType.valueOf(scheduleType.toUpperCase().trim());
        }

        return new GetScheduleJobQuery(page, size, parsedStatus, jobType, jobGroup, parsedScheduleType);
    }
}
