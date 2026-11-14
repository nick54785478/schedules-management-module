package com.example.demo.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.example.demo.application.shared.model.ScheduleCalendarModel;
import com.example.demo.application.shared.view.PageGottenView;

/**
 * <h2>ScheduleCalendarRepositoryPort</h2>
 * <p>
 * 應用層對外要求排程日曆持久化之 Outbound Port 合約。
 * </p>
 */
public interface ScheduleCalendarRepositoryPort {

    ScheduleCalendarModel save(ScheduleCalendarModel calendar);

    Optional<ScheduleCalendarModel> findById(UUID id);

    Optional<ScheduleCalendarModel> findByKey(String key);

    boolean existsByKey(String key);

    PageGottenView<ScheduleCalendarModel> findAll(int pageNumber, int pageSize);
}
