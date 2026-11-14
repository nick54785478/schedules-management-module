package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.inbound.CreateCronJobCommand;

/**
 * <h2>CreateCronJobUseCase</h2>
 * <p>
 * 定時排程任務建立用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收建立或初始化 Cron 任務之指令。
 * </p>
 */
public interface CreateCronJobUseCase {

    /**
     * 建立或初始化定時排程任務 (Cron)
     *
     * @param command 建立/覆寫 Cron 任務指令
     */
    void initializeCronTask(CreateCronJobCommand command);
}
