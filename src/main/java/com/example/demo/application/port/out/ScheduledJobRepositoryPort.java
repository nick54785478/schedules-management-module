package com.example.demo.application.port.out;

import java.util.Optional;

import com.example.demo.application.shared.model.ScheduledJobModel;
import com.example.demo.application.shared.query.GetScheduleJobQuery;
import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.vo.JobId;

/**
 * <h2>ScheduledJobRepositoryPort</h2>
 * <p>
 * 應用層對外要求排程任務持久化之 Outbound Port 合約。
 * </p>
 */
public interface ScheduledJobRepositoryPort {

    ScheduledJobModel save(ScheduledJobModel job);

    Optional<ScheduledJobModel> findById(Long id);

    Optional<ScheduledJobModel> findByJobId(JobId id);

    boolean existsByNameAndGroup(String name, String group);

    Optional<ScheduledJobModel> findByNameAndGroup(String name, String group);

    PageGottenView<ScheduledJobModel> findAll(GetScheduleJobQuery query);

    default PageGottenView<ScheduledJobModel> findAll(int pageNumber, int pageSize) {
        return findAll(new GetScheduleJobQuery(pageNumber, pageSize, null, null, null, null));
    }
}
