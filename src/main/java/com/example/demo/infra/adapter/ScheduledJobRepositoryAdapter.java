package com.example.demo.infra.adapter;

import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.example.demo.application.port.out.ScheduledJobRepositoryPort;
import com.example.demo.application.shared.model.ScheduledJobModel;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.vo.JobId;
import com.example.demo.application.shared.vo.ScheduleRule;
import com.example.demo.infra.persistence.schedule.entity.ScheduledJobJpaEntity;
import com.example.demo.infra.persistence.schedule.repository.ScheduledJobJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * <h2>ScheduledJobRepositoryAdapter</h2>
 * <p>
 * 實作應用層 {@link ScheduledJobRepositoryPort} 的 Outbound Adapter，
 * 協調 JPA Repository 並執行 Application Model 與 JPA Entity 間的轉換。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ScheduledJobRepositoryAdapter implements ScheduledJobRepositoryPort {

    private final ScheduledJobJpaRepository jpaRepository;

    @Override
    public ScheduledJobModel save(ScheduledJobModel model) {
        ScheduledJobJpaEntity entity = toEntity(model);
        ScheduledJobJpaEntity saved = jpaRepository.save(entity);
        return toModel(saved);
    }

    @Override
    public Optional<ScheduledJobModel> findById(Long id) {
        return jpaRepository.findById(id).map(this::toModel);
    }

    @Override
    public Optional<ScheduledJobModel> findByJobId(JobId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findByJobIdentifier(id.value()).map(this::toModel);
    }

    @Override
    public boolean existsByNameAndGroup(String name, String group) {
        return jpaRepository.existsByNameAndGroup(name, group);
    }

    @Override
    public Optional<ScheduledJobModel> findByNameAndGroup(String name, String group) {
        return jpaRepository.findByNameAndGroup(name, group).map(this::toModel);
    }

    @Override
    public PageGottenView<ScheduledJobModel> findAll(int pageNumber, int pageSize) {
        Page<ScheduledJobJpaEntity> page = jpaRepository.findAll(PageRequest.of(pageNumber, pageSize));
        return new PageGottenView<>(
                page.getContent().stream().map(this::toModel).collect(Collectors.toList()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private ScheduledJobJpaEntity toEntity(ScheduledJobModel model) {
        ScheduleRule rule = model.scheduleRule();
        return new ScheduledJobJpaEntity(
                model.id(),
                model.jobId().value(),
                model.name(),
                model.group(),
                model.jobType(),
                rule.getType(),
                rule.getCronExpression(),
                rule.getExecuteTime(),
                rule.getCalendarKey(),
                rule.isRequestsRecovery(),
                model.status(),
                model.createdAt(),
                model.updatedAt()
        );
    }

    private ScheduledJobModel toModel(ScheduledJobJpaEntity entity) {
        ScheduleRule rule = ScheduleRule.of(
                entity.getScheduleType(),
                entity.getCronExpression(),
                entity.getExecuteTime(),
                entity.getCalendarKey(),
                entity.isRequestsRecovery()
        );
        return new ScheduledJobModel(
                entity.getId(),
                new JobId(entity.getJobIdentifier()),
                entity.getName(),
                entity.getGroup(),
                entity.getJobType(),
                rule,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
