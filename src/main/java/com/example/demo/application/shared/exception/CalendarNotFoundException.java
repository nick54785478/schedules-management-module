package com.example.demo.application.shared.exception;

/**
 * <h2>CalendarNotFoundException</h2>
 * <p>
 * 找不到指定日曆時拋出的應用層例外。
 * </p>
 */
public class CalendarNotFoundException extends RuntimeException {

    public CalendarNotFoundException(String message) {
        super(message);
    }
}
