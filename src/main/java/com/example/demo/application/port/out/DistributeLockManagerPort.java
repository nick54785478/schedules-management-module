package com.example.demo.application.port.out;

import java.time.Duration;

/**
 * <h2>DistributeLockManagerPort</h2>
 * <p>
 * 應用層對外要求分散式鎖管理功能之 Outbound Port 合約。
 * </p>
 */
public interface DistributeLockManagerPort {

	/**
	 * 取得鎖定鍵
	 *
	 * @param key 鍵值 (通常為事件名稱)
	 * @return 鍵
	 */
	default String getLockKey(String key) {
		return "lock:" + key;
	}

	/**
	 * 取得分布式锁
	 *
	 * @param event   任務
	 * @param ownerId 擁有者
	 * @param ttl     持續時間
	 */
	boolean acquireLock(String event, String ownerId, Duration ttl);

	/**
	 * 釋放分布式鎖
	 *
	 * @param key     鎖定鍵
	 * @param ownerId 擁有者
	 */
	void releaseLock(String key, String ownerId);

	/**
	 * 清除所有已過期的鎖
	 */
	void clearAllExpiredLocks();
}
