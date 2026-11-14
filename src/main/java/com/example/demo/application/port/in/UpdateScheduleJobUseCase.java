package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.inbound.UpdateScheduleCommand;

/**
 * <h2>UpdateScheduleJobUseCase</h2>
 * <p>
 * 排程任務規則更新用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收變更任務排程週期或單次執行時間之指令。
 * </p>
 */
public interface UpdateScheduleJobUseCase {

    /**
     * 更新任務排程規則
     *
     * @param command 更新排程指令
     */
    void updateJobSchedule(UpdateScheduleCommand command);
}
