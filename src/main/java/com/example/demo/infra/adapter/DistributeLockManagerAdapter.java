package com.example.demo.infra.adapter;

import com.example.demo.application.port.DistributeLockManagerPort;
import com.example.demo.infra.persistence.DistributedLockRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@AllArgsConstructor
class DistributeLockManagerAdapter implements DistributeLockManagerPort {

    private DistributedLockRepository distributedLockRepository;

    /**
     * 取得分布式锁
     *
     * @param task    任務
     * @param ownerId 擁有者
     * @param ttl     持續時間
     * @return 是否取得分布式鎖
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Override
    public boolean acquireLock(String task, String ownerId, Duration ttl) {
        Instant now = Instant.now(); // 取得當前時間
        Instant expiresAt = now.plus(ttl);
        boolean result = false;
        // 建立 Lock Key
        String lockKey = this.getLockKey(task);
        if (distributedLockRepository.findByLockKeyAndOwnerId(lockKey, ownerId) == null) {
            // 設置分布式鎖
            distributedLockRepository.insert(lockKey, ownerId, expiresAt);
            result = true;
        }

        // 嘗試搶佔過期的鎖
        int updated = distributedLockRepository.updateIfExpired(lockKey, ownerId, expiresAt, now);
        if (updated > 0) {
            log.info("成功搶佔過期鎖: {}", lockKey);
            result = true;
        }

        return result;
    }

    /**
     * 釋放分布式鎖
     *
     * @param task    任務
     * @param ownerId 擁有者
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Override
    public void releaseLock(String task, String ownerId) {
        String lockKey = this.getLockKey(task);
        distributedLockRepository.deleteByLockKeyAndOwnerId(lockKey, ownerId);
    }

    /**
     * 清除所有已過期的鎖
     */
    @Transactional
    public void clearAllExpiredLocks() {
        // 過期 2 小時
        Instant threshold = Instant.now().minus(Duration.ofHours(2));
        int deletedCount = distributedLockRepository.deleteExpiredLocks(threshold);

        if (deletedCount > 0) {
            log.info("[DistributedLockCleaner] 已清除 {} 筆過期超過兩小時的鎖紀錄", deletedCount);
        }
    }

}