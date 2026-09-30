package com.example.demo.iface.handler;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.application.port.CronParserPort;
import com.example.demo.application.shared.exception.InvalidCronException;
import com.example.demo.application.shared.exception.JobNotFoundException;
import com.example.demo.application.shared.exception.ScheduleEngineException;
import com.example.demo.application.shared.exception.CalendarSyncException;

import lombok.extern.slf4j.Slf4j;

/**
 * <h2>GlobalExceptionHandler</h2>
 * <p>
 * 全域異常處理器。負責攔截 Controller 層噴出的各類異常，並將其轉化為標準化的 {@link ErrorResponse} 格式。
 * </p>
 * 
 * <p>
 * 設計目標：
 * </p>
 * <ul>
 * <li><b>隱藏技術細節：</b> 防止原始的 StackTrace 直接暴露給前端或終端使用者。</li>
 * <li><b>統一語義化：</b> 根據異常類型回傳對應的 HTTP 狀態碼 (404, 422, 500)。</li>
 * <li><b>集中日誌紀錄：</b> 對於嚴重的技術故障 (500) 進行統一日誌追蹤。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 處理資源不存在異常 (HTTP 404)。
	 * <p>
	 * 適用情境：當指定的 Job ID 在資料庫中找不到時。
	 * </p>
	 * 
	 * @param e {@link JobNotFoundException}
	 * @return 包含錯誤代碼與詳細訊息的物件
	 */
	@ExceptionHandler(JobNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleJobNotFound(JobNotFoundException e) {
		return new ErrorResponse("JOB_NOT_FOUND", e.getMessage());
	}

	/**
	 * 處理無效的 Cron 表達式異常 (HTTP 422)。
	 * <p>
	 * 適用情境：當 {@link CronParserPort} 驗證格式失敗時。回傳 422 Unprocessable Content
	 * 代表請求格式正確但語意錯誤。
	 * </p>
	 * 
	 * @param e {@link InvalidCronException}
	 * @return 錯誤響應
	 */
	@ExceptionHandler(InvalidCronException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
	public ErrorResponse handleInvalidCron(InvalidCronException e) {
		return new ErrorResponse("INVALID_CRON", e.getMessage());
	}

	/**
	 * 處理排程引擎技術故障 (HTTP 500)。
	 * <p>
	 * 適用情境：當 Quartz 引擎執行暫停、恢復或更新失敗時。此處會記錄 Error Level 日誌以便後續追蹤。
	 * </p>
	 * 
	 * @param e {@link ScheduleEngineException}
	 * @return 錯誤響應
	 */
	@ExceptionHandler(ScheduleEngineException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ErrorResponse handleEngineError(ScheduleEngineException e) {
		// 紀錄詳細的堆疊資訊，協助運維人員定位是資料庫鎖死還是 Quartz 內部錯誤
		log.error("[API 異常] 排程引擎操作發生非預期故障: ", e);
		return new ErrorResponse("ENGINE_ERROR", e.getMessage());
	}

	/**
	 * 處理日曆同步技術故障 (HTTP 500)。
	 * <p>
	 * 適用情境：當 Quartz 引擎註冊或更新日曆設定失敗時。
	 * </p>
	 * 
	 * @param e {@link CalendarSyncException}
	 * @return 錯誤響應
	 */
	@ExceptionHandler(CalendarSyncException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ErrorResponse handleCalendarSyncError(CalendarSyncException e) {
		log.error("[API 異常] 日曆同步發生非預期故障: ", e);
		return new ErrorResponse("CALENDAR_SYNC_ERROR", e.getMessage());
	}

	/**
	 * <h2>ErrorResponse</h2>
	 * <p>
	 * 標準化錯誤響應結構。
	 * </p>
	 * 
	 * @param errorCode 業務錯誤代碼 (供前端多國語系或判斷使用)
	 * @param message   錯誤詳細描述
	 */
	public record ErrorResponse(String errorCode, String message) {
	}
}