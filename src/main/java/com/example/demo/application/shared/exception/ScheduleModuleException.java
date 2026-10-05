package com.example.demo.application.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 基礎模組異常
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScheduleModuleException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7639623833266685193L;

	public ScheduleModuleException(String message, Throwable cause) {
		super(message, cause);
	}

	public ScheduleModuleException(String format) {
	}
}