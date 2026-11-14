package com.example.demo.infra.persistence.schedule.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.schedule.entity.ScheduledJobJpaEntity;

/**
 * <h2>ScheduledJobJpaRepository</h2>
 * <p>
 * 排程任務 Spring Data JPA Repository，擴展 {@link JpaSpecificationExecutor} 支援動態條件過濾。
 * </p>
 */
@Repository
public interface ScheduledJobJpaRepository extends JpaRepository<ScheduledJobJpaEntity, Long>, JpaSpecificationExecutor<ScheduledJobJpaEntity> {

    Optional<ScheduledJobJpaEntity> findByJobIdentifier(String jobIdentifier);

    boolean existsByNameAndGroup(String name, String group);

    Optional<ScheduledJobJpaEntity> findByNameAndGroup(String name, String group);
}
