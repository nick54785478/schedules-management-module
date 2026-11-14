package com.example.demo.infra.persistence.joblog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.joblog.entity.ScheduleJobLogJpaEntity;

/**
 * 排程任務日誌 Spring Data JPA Repository
 */
@Repository
public interface ScheduleJobLogJpaRepository extends JpaRepository<ScheduleJobLogJpaEntity, Long> {
}
