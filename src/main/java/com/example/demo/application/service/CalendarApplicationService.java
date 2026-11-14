package com.example.demo.application.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.in.AddExcludedDateUseCase;
import com.example.demo.application.port.in.CreateCalendarUseCase;
import com.example.demo.application.port.in.GetCalendarUseCase;
import com.example.demo.application.port.in.RemoveExcludedDateUseCase;
import com.example.demo.application.port.out.JobSchedulerPort;
import com.example.demo.application.port.out.ScheduleCalendarRepositoryPort;
import com.example.demo.application.shared.command.inbound.AddExcludedDateCommand;
import com.example.demo.application.shared.command.inbound.CreateCalendarCommand;
import com.example.demo.application.shared.command.outbound.SyncCalendarToEngineCommand;
import com.example.demo.application.shared.exception.CalendarNotFoundException;
import com.example.demo.application.shared.model.ScheduleCalendarModel;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleCalendarView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>CalendarApplicationService</h2>
 * <p>
 * 排程日曆應用層服務，實作多項 {@code ...UseCase} Inbound Port 合約。
 * 負責協調排程日曆持久化與 Quartz 引擎。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarApplicationService implements
        CreateCalendarUseCase,
        AddExcludedDateUseCase,
        RemoveExcludedDateUseCase,
        GetCalendarUseCase {

    private final ScheduleCalendarRepositoryPort repository;
    private final JobSchedulerPort jobScheduler;

    /**
     * 建立新的排程日曆 (黑名單)
     */
    @Override
    @Transactional
    public void createCalendar(CreateCalendarCommand command) {
        if (repository.existsByKey(command.key())) {
            throw new IllegalArgumentException("日曆 key 已存在: " + command.key());
        }

        ScheduleCalendarModel calendar = ScheduleCalendarModel.create(command.key(), command.description());
        repository.save(calendar);

        jobScheduler.syncCalendar(new SyncCalendarToEngineCommand(calendar.key(), calendar.excludedDates()));
    }

    /**
     * 新增排除日期至指定日曆
     */
    @Override
    @Transactional
    public void addExcludedDate(AddExcludedDateCommand command) {
        ScheduleCalendarModel calendar = repository.findById(command.calendarId())
                .orElseThrow(() -> new CalendarNotFoundException("找不到日曆 ID: " + command.calendarId()));

        calendar = calendar.addExcludedDate(command.excludedDate());
        repository.save(calendar);

        jobScheduler.syncCalendar(new SyncCalendarToEngineCommand(calendar.key(), calendar.excludedDates()));
    }

    /**
     * 移除日曆中指定的排除日期
     */
    @Override
    @Transactional
    public void removeExcludedDate(UUID calendarId, LocalDate date) {
        ScheduleCalendarModel calendar = repository.findById(calendarId)
                .orElseThrow(() -> new CalendarNotFoundException("找不到日曆 ID: " + calendarId));

        calendar = calendar.removeExcludedDate(date);
        repository.save(calendar);

        jobScheduler.syncCalendar(new SyncCalendarToEngineCommand(calendar.key(), calendar.excludedDates()));
    }

    /**
     * 查詢所有日曆
     */
    @Override
    @Transactional(readOnly = true)
    public PageGottenView<ScheduleCalendarView> findAllCalendars(int page, int size) {
        PageGottenView<ScheduleCalendarModel> calendarPage = repository.findAll(page, size);
        var viewList = calendarPage.content().stream()
                .map(cal -> new ScheduleCalendarView(cal.id(), cal.key(), cal.description(), cal.excludedDates()))
                .collect(Collectors.toList());
                
        return new PageGottenView<>(
                viewList,
                calendarPage.pageNumber(),
                calendarPage.pageSize(),
                calendarPage.totalElements(),
                calendarPage.totalPages()
        );
    }

    /**
     * 查詢單一日曆
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ScheduleCalendarView> findCalendarById(UUID id) {
        return repository.findById(id)
                .map(cal -> new ScheduleCalendarView(cal.id(), cal.key(), cal.description(), cal.excludedDates()));
    }
}
