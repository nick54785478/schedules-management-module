package com.example.demo.application.domain.schedule.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.application.domain.schedule.aggregate.ScheduledJob;
import com.example.demo.application.domain.schedule.aggregate.vo.JobId;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {

	Optional<ScheduledJob> findByJobId(JobId id);

	boolean existsByNameAndGroup(String name, String group);

	Optional<ScheduledJob> findByNameAndGroup(String name, String group);
}
