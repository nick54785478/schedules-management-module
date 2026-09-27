package com.example.demo.iface.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.application.domain.calendar.aggregate.ScheduleCalendar;
import com.example.demo.application.service.CalendarApplicationService;
import com.example.demo.application.shared.command.AddExcludedDateCommand;
import com.example.demo.application.shared.command.CreateCalendarCommand;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarApplicationService applicationService;

    @Test
    @DisplayName("POST /api/calendars - 應成功建立日曆並回傳 201")
    void createCalendar_ShouldReturn201() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/calendars")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "key": "TAIWAN_HOLIDAY",
                            "description": "台灣國定假日"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(content().string("日曆建立成功"));

        verify(applicationService, times(1)).createCalendar(any(CreateCalendarCommand.class));
    }

    @Test
    @DisplayName("GET /api/calendars/{id}/holidays - 應成功回傳該日曆的排除日期清單")
    void getCalendarHolidays_ShouldReturnList() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        ScheduleCalendar mockCalendar = ScheduleCalendar.create("TAIWAN_HOLIDAY", "desc");
        mockCalendar.addExcludedDate(LocalDate.of(2026, 1, 1));
        mockCalendar.addExcludedDate(LocalDate.of(2026, 2, 28));

        when(applicationService.findCalendarById(id)).thenReturn(Optional.of(mockCalendar));

        // Act & Assert
        mockMvc.perform(get("/api/calendars/" + id + "/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("2026-01-01"))
                .andExpect(jsonPath("$[1]").value("2026-02-28"));
    }

    @Test
    @DisplayName("POST /api/calendars/{id}/holidays - 應成功新增日期並回傳 200")
    void addHoliday_ShouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        // Act & Assert
        mockMvc.perform(post("/api/calendars/" + id + "/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "date": "2026-10-10"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(content().string("假日新增成功"));

        verify(applicationService, times(1)).addExcludedDate(any(AddExcludedDateCommand.class));
    }

    @Test
    @DisplayName("DELETE /api/calendars/{id}/holidays/{date} - 應成功移除日期並回傳 200")
    void removeHoliday_ShouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        // Act & Assert
        mockMvc.perform(delete("/api/calendars/" + id + "/holidays/2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(content().string("假日移除成功"));

        verify(applicationService, times(1)).removeExcludedDate(eq(id), eq(LocalDate.of(2026, 10, 10)));
    }
}
