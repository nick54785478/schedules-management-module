package com.example.demo.infra.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.application.domain.calendar.aggregate.ScheduleCalendar;

public interface ScheduleCalendarRepository extends JpaRepository<ScheduleCalendar, UUID> {
    
    Optional<ScheduleCalendar> findByKey(String key);
    
    boolean existsByKey(String key);
}
