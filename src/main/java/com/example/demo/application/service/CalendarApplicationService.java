package com.example.demo.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.calendar.aggregate.ScheduleCalendar;
import com.example.demo.application.port.JobSchedulerPort;
import com.example.demo.application.shared.command.AddExcludedDateCommand;
import com.example.demo.application.shared.command.CreateCalendarCommand;
import com.example.demo.infra.persistence.ScheduleCalendarRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarApplicationService {

    private final ScheduleCalendarRepository repository;
    private final JobSchedulerPort jobScheduler;

    /**
     * 建立新的排程日曆 (黑名單)
     */
    @Transactional
    public void createCalendar(CreateCalendarCommand command) {
        if (repository.existsByKey(command.key())) {
            throw new IllegalArgumentException("日曆 key 已存在: " + command.key());
        }

        ScheduleCalendar calendar = ScheduleCalendar.create(command.key(), command.description());
        repository.save(calendar);

        try {
            jobScheduler.syncCalendar(calendar);
        } catch (Exception e) {
            log.error("同步日曆至 Quartz 失敗: {}", command.key(), e);
            throw new RuntimeException("系統日曆建立失敗", e);
        }
    }

    /**
     * 新增排除日期至指定日曆
     */
    @Transactional
    public void addExcludedDate(AddExcludedDateCommand command) {
        ScheduleCalendar calendar = repository.findById(command.calendarId())
                .orElseThrow(() -> new IllegalArgumentException("找不到日曆 ID: " + command.calendarId()));

        calendar.addExcludedDate(command.excludedDate());
        repository.save(calendar);

        try {
            jobScheduler.syncCalendar(calendar); // 更新 Quartz 內的日曆
        } catch (Exception e) {
            log.error("同步更新日曆至 Quartz 失敗: {}", calendar.getKey(), e);
            throw new RuntimeException("系統日曆更新失敗", e);
        }
    }

    /**
     * 移除日曆中指定的排除日期
     */
    @Transactional
    public void removeExcludedDate(UUID calendarId, java.time.LocalDate date) {
        ScheduleCalendar calendar = repository.findById(calendarId)
                .orElseThrow(() -> new IllegalArgumentException("找不到日曆 ID: " + calendarId));

        calendar.removeExcludedDate(date);
        repository.save(calendar);

        try {
            jobScheduler.syncCalendar(calendar); // 更新 Quartz 內的日曆
        } catch (Exception e) {
            log.error("同步更新日曆至 Quartz 失敗: {}", calendar.getKey(), e);
            throw new RuntimeException("系統日曆更新失敗", e);
        }
    }

    /**
     * 查詢所有日曆
     */
    @Transactional(readOnly = true)
    public List<ScheduleCalendar> findAllCalendars() {
        return repository.findAll();
    }

    /**
     * 查詢單一日曆
     */
    @Transactional(readOnly = true)
    public Optional<ScheduleCalendar> findCalendarById(UUID id) {
        return repository.findById(id);
    }
}
