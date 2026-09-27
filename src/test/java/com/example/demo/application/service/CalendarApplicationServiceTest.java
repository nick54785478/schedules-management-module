package com.example.demo.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.application.domain.calendar.aggregate.ScheduleCalendar;
import com.example.demo.application.port.JobSchedulerPort;
import com.example.demo.application.shared.command.AddExcludedDateCommand;
import com.example.demo.application.shared.command.CreateCalendarCommand;
import com.example.demo.infra.persistence.ScheduleCalendarRepository;
import org.quartz.SchedulerException;

@ExtendWith(MockitoExtension.class)
class CalendarApplicationServiceTest {

    @Mock
    private ScheduleCalendarRepository repository;

    @Mock
    private JobSchedulerPort jobScheduler;

    @InjectMocks
    private CalendarApplicationService applicationService;

    private ScheduleCalendar testCalendar;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testCalendar = ScheduleCalendar.create("TAIWAN_HOLIDAY", "測試用假期");
    }

    @Test
    @DisplayName("建立日曆：當 key 不存在時，應成功儲存並同步至 Quartz")
    void createCalendar_ShouldSaveAndSync_WhenKeyNotExists() throws Exception {
        // Arrange
        CreateCalendarCommand command = new CreateCalendarCommand("NEW_HOLIDAY", "新的假期日曆");
        when(repository.existsByKey("NEW_HOLIDAY")).thenReturn(false);

        // Act
        applicationService.createCalendar(command);

        // Assert
        ArgumentCaptor<ScheduleCalendar> calendarCaptor = ArgumentCaptor.forClass(ScheduleCalendar.class);
        verify(repository, times(1)).save(calendarCaptor.capture());
        verify(jobScheduler, times(1)).syncCalendar(calendarCaptor.capture());

        ScheduleCalendar savedCalendar = calendarCaptor.getAllValues().get(0);
        assertEquals("NEW_HOLIDAY", savedCalendar.getKey());
        assertEquals("新的假期日曆", savedCalendar.getDescription());
    }

    @Test
    @DisplayName("建立日曆：當 key 已存在時，應拋出例外")
    void createCalendar_ShouldThrowException_WhenKeyExists() throws SchedulerException {
        // Arrange
        CreateCalendarCommand command = new CreateCalendarCommand("EXISTING_HOLIDAY", "已存在的日曆");
        when(repository.existsByKey("EXISTING_HOLIDAY")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            applicationService.createCalendar(command);
        });

        assertEquals("日曆 key 已存在: EXISTING_HOLIDAY", exception.getMessage());
        verify(repository, never()).save(any());
        verify(jobScheduler, never()).syncCalendar(any());
    }

    @Test
    @DisplayName("新增排除日期：應正確加入領域模型並同步至 Quartz")
    void addExcludedDate_ShouldUpdateAndSync_WhenCalendarExists() throws Exception {
        // Arrange
        LocalDate targetDate = LocalDate.of(2026, 10, 10);
        AddExcludedDateCommand command = new AddExcludedDateCommand(testId, targetDate);
        when(repository.findById(testId)).thenReturn(Optional.of(testCalendar));

        // Act
        applicationService.addExcludedDate(command);

        // Assert
        ArgumentCaptor<ScheduleCalendar> calendarCaptor = ArgumentCaptor.forClass(ScheduleCalendar.class);
        verify(repository, times(1)).save(calendarCaptor.capture());
        verify(jobScheduler, times(1)).syncCalendar(calendarCaptor.capture());

        ScheduleCalendar updatedCalendar = calendarCaptor.getAllValues().get(0);
        assertTrue(updatedCalendar.getExcludedDates().contains(targetDate));
    }

    @Test
    @DisplayName("移除排除日期：應正確從領域模型拔除並同步至 Quartz")
    void removeExcludedDate_ShouldUpdateAndSync_WhenCalendarExists() throws Exception {
        // Arrange
        LocalDate targetDate = LocalDate.of(2026, 1, 1);
        testCalendar.addExcludedDate(targetDate); // 先預塞一天
        when(repository.findById(testId)).thenReturn(Optional.of(testCalendar));

        // Act
        applicationService.removeExcludedDate(testId, targetDate);

        // Assert
        ArgumentCaptor<ScheduleCalendar> calendarCaptor = ArgumentCaptor.forClass(ScheduleCalendar.class);
        verify(repository, times(1)).save(calendarCaptor.capture());
        verify(jobScheduler, times(1)).syncCalendar(calendarCaptor.capture());

        ScheduleCalendar updatedCalendar = calendarCaptor.getAllValues().get(0);
        assertFalse(updatedCalendar.getExcludedDates().contains(targetDate));
    }
}
