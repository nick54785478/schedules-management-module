package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.inbound.AddExcludedDateCommand;

/**
 * <h2>AddExcludedDateUseCase</h2>
 * <p>
 * 日曆排除日期新增用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收新增排除日期至指定日曆之指令。
 * </p>
 */
public interface AddExcludedDateUseCase {

    /**
     * 新增排除日期至指定日曆
     *
     * @param command 新增排除日期指令
     */
    void addExcludedDate(AddExcludedDateCommand command);
}
