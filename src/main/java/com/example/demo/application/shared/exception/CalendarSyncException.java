package com.example.demo.application.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日曆同步異常
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CalendarSyncException extends ScheduleModuleException {

	private static final long serialVersionUID = 1L;

	public CalendarSyncException(String calendarKey, Throwable cause) {
		super(String.format("日曆 [%s] 同步至執行引擎失敗", calendarKey), cause);
	}
}
