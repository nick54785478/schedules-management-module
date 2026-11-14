package com.example.demo.application.port.in;

import java.util.Optional;
import java.util.UUID;

import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleCalendarView;

/**
 * <h2>GetCalendarUseCase</h2>
 * <p>
 * 排程日曆查詢用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責提供日曆清單與單一日曆排除日期之查詢功能。
 * </p>
 */
public interface GetCalendarUseCase {

    /**
     * 分頁查詢所有日曆
     *
     * @param page 頁碼 (0-based)
     * @param size 每頁筆數
     * @return 分頁日曆清單視圖
     */
    PageGottenView<ScheduleCalendarView> findAllCalendars(int page, int size);

    /**
     * 依 ID 查詢單一日曆
     *
     * @param id 日曆 ID
     * @return 日曆視圖 (若存在)
     */
    Optional<ScheduleCalendarView> findCalendarById(UUID id);
}
