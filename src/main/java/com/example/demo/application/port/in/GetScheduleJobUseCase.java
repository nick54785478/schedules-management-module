package com.example.demo.application.port.in;

import com.example.demo.application.shared.query.GetScheduleJobQuery;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleJobGottenView;

/**
 * <h2>GetScheduleJobUseCase</h2>
 * <p>
 * 排程任務查詢用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責提供排程任務的分頁清單與動態條件過濾查詢功能。
 * </p>
 */
public interface GetScheduleJobUseCase {

    /**
     * 依條件查詢任務列表 (分頁與多維度過濾)
     *
     * @param query 查詢條件物件 (包含 page, size, status, jobType, jobGroup, scheduleType)
     * @return 分頁任務清單視圖
     */
    PageGottenView<ScheduleJobGottenView> getJobInfoResources(GetScheduleJobQuery query);

    /**
     * 查詢任務列表 (簡易分頁，無額外過濾條件)
     *
     * @param page 頁碼 (0-based)
     * @param size 每頁筆數
     * @return 分頁任務清單視圖
     */
    default PageGottenView<ScheduleJobGottenView> getJobInfoResources(int page, int size) {
        return getJobInfoResources(new GetScheduleJobQuery(page, size, null, null, null, null));
    }
}
