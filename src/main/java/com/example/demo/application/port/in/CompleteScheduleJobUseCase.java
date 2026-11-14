package com.example.demo.application.port.in;

/**
 * <h2>CompleteScheduleJobUseCase</h2>
 * <p>
 * 完成一次性排程任務用例合約 (Inbound Port)。
 * 遵循六角架構與 Clean Architecture 規範 (V + N + UseCase 命名風格)，負責標記一次性任務執行完畢並轉為 COMPLETED 狀態。
 * </p>
 */
public interface CompleteScheduleJobUseCase {

    /**
     * 完成一次性排程任務
     *
     * @param name  任務名稱
     * @param group 任務群組
     */
    void completeOneTimeJob(String name, String group);
}
