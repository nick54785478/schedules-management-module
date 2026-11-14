package com.example.demo.application.port.in;

import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleJobGottenView;

/**
 * <h2>GetScheduleJobUseCase</h2>
 * <p>
 * 排程任務查詢用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責提供排程任務的分頁清單與狀態查詢功能。
 * </p>
 */
public interface GetScheduleJobUseCase {

    /**
     * 查詢任務列表 (分頁)
     *
     * @param page 頁碼 (0-based)
     * @param size 每頁筆數
     * @return 分頁任務清單視圖
     */
    PageGottenView<ScheduleJobGottenView> getJobInfoResources(int page, int size);
}
