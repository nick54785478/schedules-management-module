package com.example.demo.application.domain.distlock.aggregate;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 分布式鎖實作
 * 目前已棄用，由 Quartz 的分布式鎖來取代
 * */
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "distributed_lock", uniqueConstraints = { @UniqueConstraint(columnNames = { "lockKey" }) })
@EntityListeners(AuditingEntityListener.class)
public class DistributedLock {

	@Id
	@Column(name = "lock_key", nullable = false, length = 100)
	private String lockKey;

	@Column(name = "owner_id", nullable = false, length = 100)
	private String ownerId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public DistributedLock(String lockKey, String ownerId, Instant expiresAt) {
		this.lockKey = lockKey;
		this.ownerId = ownerId;
		this.expiresAt = expiresAt;
	}

}