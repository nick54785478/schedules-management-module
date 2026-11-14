package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.inbound.CreateCalendarCommand;

/**
 * <h2>CreateCalendarUseCase</h2>
 * <p>
 * 排程日曆建立用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收建立全新日曆黑名單之指令。
 * </p>
 */
public interface CreateCalendarUseCase {

    /**
     * 建立新的排程日曆 (黑名單)
     *
     * @param command 建立日曆指令
     */
    void createCalendar(CreateCalendarCommand command);
}
