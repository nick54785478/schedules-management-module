package com.example.demo.infra.persistence.calendar.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.calendar.entity.ScheduleCalendarJpaEntity;

/**
 * <h2>ScheduleCalendarJpaRepository</h2>
 * <p>
 * 排程日曆 Spring Data JPA Repository。
 * </p>
 */
@Repository
public interface ScheduleCalendarJpaRepository extends JpaRepository<ScheduleCalendarJpaEntity, UUID> {

    Optional<ScheduleCalendarJpaEntity> findByKey(String key);

    boolean existsByKey(String key);
}
