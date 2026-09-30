package com.example.demo.application.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 引擎操作異常
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScheduleEngineException extends ScheduleModuleException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3534602597978424370L;

	public ScheduleEngineException(String action, String jobGroup, String jobName, Throwable cause) {
		super(String.format("執行引擎操作 [%s] 失敗: %s.%s", action, jobGroup, jobName), cause);
	}
}