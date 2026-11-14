package com.example.demo.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * <h2>RemoveExcludedDateUseCase</h2>
 * <p>
 * 日曆排除日期移除用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收自指定日曆移除排除日期之請求。
 * </p>
 */
public interface RemoveExcludedDateUseCase {

    /**
     * 移除日曆中指定的排除日期
     *
     * @param calendarId 日曆 ID
     * @param date       要移除的排除日期
     */
    void removeExcludedDate(UUID calendarId, LocalDate date);
}
