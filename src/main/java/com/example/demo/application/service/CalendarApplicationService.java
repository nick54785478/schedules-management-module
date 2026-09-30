package com.example.demo.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.calendar.aggregate.ScheduleCalendar;
import com.example.demo.application.port.JobSchedulerPort;
import com.example.demo.application.shared.command.AddExcludedDateCommand;
import com.example.demo.application.shared.command.CreateCalendarCommand;
import com.example.demo.application.domain.calendar.repository.ScheduleCalendarRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.example.demo.application.shared.view.PageGottenView;

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

        jobScheduler.syncCalendar(calendar);
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

        jobScheduler.syncCalendar(calendar); // 更新 Quartz 內的日曆
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

        jobScheduler.syncCalendar(calendar); // 更新 Quartz 內的日曆
    }

    /**
     * 查詢所有日曆
     */
    @Transactional(readOnly = true)
    public PageGottenView<ScheduleCalendar> findAllCalendars(int page, int size) {
        Page<ScheduleCalendar> calendarPage = repository.findAll(PageRequest.of(page, size));
        return new PageGottenView<>(
                calendarPage.getContent(),
                calendarPage.getNumber(),
                calendarPage.getSize(),
                calendarPage.getTotalElements(),
                calendarPage.getTotalPages()
        );
    }

    /**
     * 查詢單一日曆
     */
    @Transactional(readOnly = true)
    public Optional<ScheduleCalendar> findCalendarById(UUID id) {
        return repository.findById(id);
    }
}
