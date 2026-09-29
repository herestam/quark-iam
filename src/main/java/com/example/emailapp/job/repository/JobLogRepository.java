package com.example.emailapp.job.repository;

import java.util.List;

import com.example.emailapp.job.entity.JobLog;
import com.example.emailapp.job.entity.JobLogLevel;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JobLogRepository implements PanacheRepositoryBase<JobLog, Long> {

    public List<JobLog> listForJob(Long jobId, int limit) {
        return find("job.id = ?1 order by id desc", jobId)
                .page(io.quarkus.panache.common.Page.of(0, Math.max(1, limit)))
                .list();
    }

    public List<JobLog> listForJobAscending(Long jobId, int limit) {
        return find("job.id = ?1 order by id asc", jobId)
                .page(io.quarkus.panache.common.Page.of(0, Math.max(1, limit)))
                .list();
    }

    public long countByLevel(Long jobId, JobLogLevel level) {
        return count("job.id = ?1 and level = ?2", jobId, level);
    }

    public long countForJob(Long jobId) {
        return count("job.id = ?1", jobId);
    }
}
