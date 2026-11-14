package com.example.demo.infra.adapter;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.example.demo.application.port.out.ScheduleCalendarRepositoryPort;
import com.example.demo.application.shared.model.ScheduleCalendarModel;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.infra.persistence.calendar.entity.ScheduleCalendarJpaEntity;
import com.example.demo.infra.persistence.calendar.repository.ScheduleCalendarJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * <h2>ScheduleCalendarRepositoryAdapter</h2>
 * <p>
 * 實作應用層 {@link ScheduleCalendarRepositoryPort} 的 Outbound Adapter，
 * 協調 JPA Repository 並執行 Application Model 與 JPA Entity 間的轉換。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ScheduleCalendarRepositoryAdapter implements ScheduleCalendarRepositoryPort {

    private final ScheduleCalendarJpaRepository jpaRepository;

    @Override
    public ScheduleCalendarModel save(ScheduleCalendarModel model) {
        ScheduleCalendarJpaEntity entity = toEntity(model);
        ScheduleCalendarJpaEntity saved = jpaRepository.save(entity);
        return toModel(saved);
    }

    @Override
    public Optional<ScheduleCalendarModel> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toModel);
    }

    @Override
    public Optional<ScheduleCalendarModel> findByKey(String key) {
        return jpaRepository.findByKey(key).map(this::toModel);
    }

    @Override
    public boolean existsByKey(String key) {
        return jpaRepository.existsByKey(key);
    }

    @Override
    public PageGottenView<ScheduleCalendarModel> findAll(int pageNumber, int pageSize) {
        Page<ScheduleCalendarJpaEntity> page = jpaRepository.findAll(PageRequest.of(pageNumber, pageSize));
        return new PageGottenView<>(
                page.getContent().stream().map(this::toModel).collect(Collectors.toList()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private ScheduleCalendarJpaEntity toEntity(ScheduleCalendarModel model) {
        return new ScheduleCalendarJpaEntity(
                model.id(),
                model.key(),
                model.description(),
                model.excludedDates(),
                model.createdAt(),
                model.updatedAt()
        );
    }

    private ScheduleCalendarModel toModel(ScheduleCalendarJpaEntity entity) {
        return new ScheduleCalendarModel(
                entity.getId(),
                entity.getKey(),
                entity.getDescription(),
                entity.getExcludedDates(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
