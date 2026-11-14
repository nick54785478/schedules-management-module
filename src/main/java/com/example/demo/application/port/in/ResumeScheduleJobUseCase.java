package com.example.demo.application.port.in;

/**
 * <h2>ResumeScheduleJobUseCase</h2>
 * <p>
 * 恢復排程任務用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責接收恢復指定排程任務之請求。
 * </p>
 */
public interface ResumeScheduleJobUseCase {

    /**
     * 恢復排程任務
     *
     * @param id 任務唯一識別碼
     */
    void resumeTask(String id);
}
