package com.example.demo.infra.persistence.checkpoint.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.checkpoint.entity.JobExecutionCheckpointJpaEntity;

/**
 * 任務中斷點 Spring Data JPA Repository
 */
@Repository
public interface JobExecutionCheckpointJpaRepository extends JpaRepository<JobExecutionCheckpointJpaEntity, String> {

	Optional<JobExecutionCheckpointJpaEntity> findByJobId(String jobId);
}
