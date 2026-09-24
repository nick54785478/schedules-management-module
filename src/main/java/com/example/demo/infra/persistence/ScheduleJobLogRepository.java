package com.example.demo.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.application.domain.joblog.aggregate.ScheduleJobLog;

@Repository
public interface ScheduleJobLogRepository extends JpaRepository<ScheduleJobLog, Long> {
}
