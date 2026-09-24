package com.example.demo.application.port;

import com.example.demo.application.domain.schedule.aggregate.vo.CronExpression;

/**
 * Cron 轉換器 Port
 */
public interface CronParserPort {

    /**
     * 解析並驗證 Cron 字串
     *
     * @param expression Cron 字串
     * @return 合法的 CronExpression 值物件
     */
    CronExpression parse(String expression);
}
