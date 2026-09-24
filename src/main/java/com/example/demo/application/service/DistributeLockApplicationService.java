package com.example.demo.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.DistributeLockManagerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>DistributeLockApplicationService</h2>
 * <p>
 * 分散式鎖的應用服務層。
 * 負責掌控事務邊界與具體的業務使用案例 (Use Case)，如清理過期鎖。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributeLockApplicationService {

	private final DistributeLockManagerPort distributeLockManager;

	/**
	 * 清理過期的分散式鎖
	 */
	@Transactional(rollbackFor = Exception.class)
	public void clearExpiredLocks() {
		log.debug("開始執行應用服務: 清理過期的分散式鎖");
		distributeLockManager.clearAllExpiredLocks();
	}
}
