package com.example.demo.infra.persistence.distlock.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.distlock.entity.DistributedLockJpaEntity;

import jakarta.persistence.LockModeType;

/**
 * 分布式鎖 Spring Data JPA Repository
 */
@Repository
public interface DistributedLockJpaRepository extends JpaRepository<DistributedLockJpaEntity, String> {

	@Lock(value = LockModeType.PESSIMISTIC_WRITE)
	DistributedLockJpaEntity findByLockKeyAndOwnerId(String lockKey, String ownerId);

	@Modifying
	@Query(value = """
			    INSERT INTO distributed_lock (lock_key, owner_id, expires_at, created_at)
			    VALUES (:lockKey, :ownerId, :expiresAt, UTC_TIMESTAMP())
			""", nativeQuery = true)
	int insert(@Param("lockKey") String lockKey, @Param("ownerId") String ownerId,
			@Param("expiresAt") Instant expiresAt);

	@Modifying
	@Query("DELETE FROM DistributedLockJpaEntity l WHERE l.lockKey = :lockKey AND l.ownerId = :ownerId")
	int deleteByLockKeyAndOwnerId(@Param("lockKey") String lockKey, @Param("ownerId") String ownerId);

	@Modifying
	@Query("DELETE FROM DistributedLockJpaEntity l WHERE l.expiresAt < :now")
	void deleteByExpiresAtBefore(@Param("now") Instant now);

	@Modifying
	@Query("UPDATE DistributedLockJpaEntity l SET l.ownerId = :ownerId, l.expiresAt = :expiresAt "
			+ "WHERE l.lockKey = :lockKey AND l.expiresAt <= :now")
	int updateIfExpired(@Param("lockKey") String lockKey, @Param("ownerId") String ownerId,
			@Param("expiresAt") Instant expiresAt, @Param("now") Instant now);

	@Modifying
	@Query(value = "DELETE FROM distributed_lock WHERE EXPIRES_AT < :threshold", nativeQuery = true)
	int deleteExpiredLocks(@Param("threshold") Instant threshold);
}
