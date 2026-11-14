package com.example.demo.application.port.out;

import com.example.demo.application.shared.vo.CronExpression;

/**
 * <h2>CronParserPort (防腐層校驗門神)</h2>
 * <p>
 * 定義了應用層對外要求驗證與解析 Cron 表達式的 Outbound Port 合約。
 * </p>
 */
public interface CronParserPort {

    /**
     * 解析並校驗 Cron 字串。
     *
     * @param cron 原始 Cron 表達式字串
     * @return 包含合法狀態的 {@link CronExpression} 數值物件
     */
    CronExpression parse(String cron);

    /**
     * 檢查 Cron 表達式是否合法
     *
     * @param cron Cron 表達式字串
     * @return 若合法回傳 true，反之回傳 false
     */
    boolean isValid(String cron);
}
