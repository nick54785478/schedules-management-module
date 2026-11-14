package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.inbound.BindJobCalendarCommand;

/**
 * <h2>BindJobCalendarUseCase</h2>
 * <p>
 * 排程任務日曆綁定用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收任務綁定或解除特定排除日曆黑名單之指令。
 * </p>
 */
public interface BindJobCalendarUseCase {

    /**
     * 綁定或解除綁定排除日曆
     *
     * @param command 綁定日曆指令
     */
    void bindCalendar(BindJobCalendarCommand command);
}
