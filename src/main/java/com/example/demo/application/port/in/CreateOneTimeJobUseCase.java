package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.inbound.CreateOneTimeJobCommand;

/**
 * <h2>CreateOneTimeJobUseCase</h2>
 * <p>
 * 一次性排程任務建立用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收建立單次執行排程任務之指令。
 * </p>
 */
public interface CreateOneTimeJobUseCase {

    /**
     * 建立或初始化一次性排程任務 (OneTime)
     *
     * @param command 建立一次性任務指令
     */
    void initializeOneTimeTask(CreateOneTimeJobCommand command);
}
