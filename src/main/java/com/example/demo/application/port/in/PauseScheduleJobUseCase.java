package com.example.demo.application.port.in;

/**
 * <h2>PauseScheduleJobUseCase</h2>
 * <p>
 * 暫停排程任務用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收暫停指定排程任務之請求。
 * </p>
 */
public interface PauseScheduleJobUseCase {

    /**
     * 暫停排程任務
     *
     * @param id 任務唯一識別碼
     */
    void pauseTask(String id);
}
