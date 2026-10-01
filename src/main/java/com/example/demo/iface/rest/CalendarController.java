package com.example.demo.iface.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.demo.application.shared.view.PageGottenView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.application.service.CalendarApplicationService;
import com.example.demo.application.shared.command.AddExcludedDateCommand;
import com.example.demo.application.shared.command.CreateCalendarCommand;
import com.example.demo.iface.dto.req.AddExcludedDateResource;
import com.example.demo.iface.dto.req.CreateCalendarResource;
import com.example.demo.iface.dto.res.CalendarCreatedResource;
import com.example.demo.iface.dto.res.HolidayAddedResource;
import com.example.demo.iface.dto.res.HolidayRemovedResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Tag(name = "Calendar APIs", description = "排程日曆 (例外黑名單) 管理介面")
@RestController
@RequestMapping("/api/calendars")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarApplicationService applicationService;

    @Operation(summary = "建立新日曆", description = "建立一個全新的全域日曆黑名單")
    @PostMapping
    public ResponseEntity<CalendarCreatedResource> createCalendar(@RequestBody CreateCalendarResource request) {
        CreateCalendarCommand command = new CreateCalendarCommand(request.key(), request.description());
        applicationService.createCalendar(command);
        return new ResponseEntity<>(new CalendarCreatedResource("201", "日曆建立成功"), HttpStatus.CREATED);
    }

    @Operation(summary = "查詢所有日曆", description = "列出系統內所有的日曆設定")
    @GetMapping
    public ResponseEntity<PageGottenView<com.example.demo.application.shared.view.ScheduleCalendarView>> getAllCalendars(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageGottenView<com.example.demo.application.shared.view.ScheduleCalendarView> result = applicationService.findAllCalendars(page, size);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "查詢單一日曆的所有排除日期", description = "根據日曆 ID 查詢其下所有設定的排除日期")
    @GetMapping("/{id}/holidays")
    public ResponseEntity<List<LocalDate>> getCalendarHolidays(@PathVariable("id") UUID id) {
        List<LocalDate> dates = applicationService.findCalendarById(id)
                .map(cal -> new ArrayList<>(cal.excludedDates()))
                .orElseThrow(() -> new IllegalArgumentException("找不到該日曆 ID: " + id));
        return ResponseEntity.ok(dates);
    }

    @Operation(summary = "新增排除日期", description = "為指定的日曆新增一天例外假日")
    @PostMapping("/{id}/holidays")
    public ResponseEntity<HolidayAddedResource> addHoliday(@PathVariable("id") UUID id, @RequestBody AddExcludedDateResource request) {
        AddExcludedDateCommand command = new AddExcludedDateCommand(id, request.date());
        applicationService.addExcludedDate(command);
        return ResponseEntity.ok(new HolidayAddedResource("200", "假日新增成功"));
    }

    @Operation(summary = "刪除排除日期", description = "將特定日期從黑名單中移除 (例如: 補班日)")
    @DeleteMapping("/{id}/holidays/{date}")
    public ResponseEntity<HolidayRemovedResource> removeHoliday(@PathVariable("id") UUID id, @PathVariable("date") LocalDate date) {
        applicationService.removeExcludedDate(id, date);
        return ResponseEntity.ok(new HolidayRemovedResource("200", "假日移除成功"));
    }
}
