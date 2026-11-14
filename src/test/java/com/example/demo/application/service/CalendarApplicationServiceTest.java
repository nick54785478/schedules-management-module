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

import com.example.demo.application.port.out.JobSchedulerPort;
import com.example.demo.application.port.out.ScheduleCalendarRepositoryPort;
import com.example.demo.application.shared.command.inbound.AddExcludedDateCommand;
import com.example.demo.application.shared.command.inbound.CreateCalendarCommand;
import com.example.demo.application.shared.command.outbound.SyncCalendarToEngineCommand;
import com.example.demo.application.shared.model.ScheduleCalendarModel;
import org.quartz.SchedulerException;

@ExtendWith(MockitoExtension.class)
class CalendarApplicationServiceTest {

    @Mock
    private ScheduleCalendarRepositoryPort repository;

    @Mock
    private JobSchedulerPort jobScheduler;

    @InjectMocks
    private CalendarApplicationService applicationService;

    private ScheduleCalendarModel testCalendar;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testCalendar = ScheduleCalendarModel.create("TAIWAN_HOLIDAY", "測試用假期");
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
        ArgumentCaptor<ScheduleCalendarModel> calendarCaptor = ArgumentCaptor.forClass(ScheduleCalendarModel.class);
        verify(repository, times(1)).save(calendarCaptor.capture());
        verify(jobScheduler, times(1)).syncCalendar(any(SyncCalendarToEngineCommand.class));

        ScheduleCalendarModel savedCalendar = calendarCaptor.getValue();
        assertEquals("NEW_HOLIDAY", savedCalendar.key());
        assertEquals("新的假期日曆", savedCalendar.description());
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
    @DisplayName("新增排除日期：應正確加入模型並同步至 Quartz")
    void addExcludedDate_ShouldUpdateAndSync_WhenCalendarExists() throws Exception {
        // Arrange
        LocalDate targetDate = LocalDate.of(2026, 10, 10);
        AddExcludedDateCommand command = new AddExcludedDateCommand(testId, targetDate);
        when(repository.findById(testId)).thenReturn(Optional.of(testCalendar));

        // Act
        applicationService.addExcludedDate(command);

        // Assert
        ArgumentCaptor<ScheduleCalendarModel> calendarCaptor = ArgumentCaptor.forClass(ScheduleCalendarModel.class);
        verify(repository, times(1)).save(calendarCaptor.capture());
        verify(jobScheduler, times(1)).syncCalendar(any(SyncCalendarToEngineCommand.class));

        ScheduleCalendarModel updatedCalendar = calendarCaptor.getValue();
        assertTrue(updatedCalendar.excludedDates().contains(targetDate));
    }

    @Test
    @DisplayName("移除排除日期：應正確從模型拔除並同步至 Quartz")
    void removeExcludedDate_ShouldUpdateAndSync_WhenCalendarExists() throws Exception {
        // Arrange
        LocalDate targetDate = LocalDate.of(2026, 1, 1);
        testCalendar = testCalendar.addExcludedDate(targetDate); // 先預塞一天
        when(repository.findById(testId)).thenReturn(Optional.of(testCalendar));

        // Act
        applicationService.removeExcludedDate(testId, targetDate);

        // Assert
        ArgumentCaptor<ScheduleCalendarModel> calendarCaptor = ArgumentCaptor.forClass(ScheduleCalendarModel.class);
        verify(repository, times(1)).save(calendarCaptor.capture());
        verify(jobScheduler, times(1)).syncCalendar(any(SyncCalendarToEngineCommand.class));

        ScheduleCalendarModel updatedCalendar = calendarCaptor.getValue();
        assertFalse(updatedCalendar.excludedDates().contains(targetDate));
    }

    @Test
    @DisplayName("移除排除日期：當日期不存在時，應拋出例外且不同步")
    void removeExcludedDate_ShouldThrowException_WhenDateNotExists() throws Exception {
        // Arrange
        LocalDate targetDate = LocalDate.of(2026, 1, 1);
        when(repository.findById(testId)).thenReturn(Optional.of(testCalendar));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            applicationService.removeExcludedDate(testId, targetDate);
        });

        assertEquals("無法移除不存在的排除日期: 2026-01-01", exception.getMessage());
        verify(repository, never()).save(any());
        verify(jobScheduler, never()).syncCalendar(any());
    }
}
