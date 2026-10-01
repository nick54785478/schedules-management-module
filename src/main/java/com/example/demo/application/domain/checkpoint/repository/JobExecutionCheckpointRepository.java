package com.example.demo.application.domain.checkpoint.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.application.domain.checkpoint.aggregate.JobExecutionCheckpoint;

@Repository
public interface JobExecutionCheckpointRepository extends JpaRepository<JobExecutionCheckpoint, String> {

	Optional<JobExecutionCheckpoint> findByJobId(String jobId);

}
