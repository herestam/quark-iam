package com.example.emailapp.job.service.impl;

import java.util.List;

import org.jboss.logging.Logger;

import com.example.emailapp.common.exception.ResourceNotFoundException;
import com.example.emailapp.common.validation.SecretRedactor;
import com.example.emailapp.job.dto.JobLogResponse;
import com.example.emailapp.job.entity.EmailJob;
import com.example.emailapp.job.entity.JobLog;
import com.example.emailapp.job.entity.JobLogLevel;
import com.example.emailapp.job.repository.EmailJobRepository;
import com.example.emailapp.job.repository.JobLogRepository;
import com.example.emailapp.job.service.JobLogService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Writes the audit trail shown on the job detail page.
 *
 * <p>Logging must never be able to break a running job, so failures here are
 * swallowed and reported to the application log only.</p>
 */
@ApplicationScoped
public class JobLogServiceImpl implements JobLogService {

    private static final Logger LOG = Logger.getLogger(JobLogServiceImpl.class);
    private static final int MESSAGE_LIMIT = 4000;
    private static final int DEFAULT_LIMIT = 500;

    @Inject
    JobLogRepository repository;

    @Inject
    EmailJobRepository jobRepository;

    @Inject
    SecretRedactor redactor;

    @Override
    public void info(Long jobId, String message) {
        append(jobId, JobLogLevel.INFO, message);
    }

    @Override
    public void warn(Long jobId, String message) {
        append(jobId, JobLogLevel.WARN, message);
    }

    @Override
    public void error(Long jobId, String message) {
        append(jobId, JobLogLevel.ERROR, message);
    }

    /**
     * Participates in the caller's transaction (REQUIRED) on purpose: the job
     * row must already be visible when the first log line is written, and
     * "log then state" or "state then log" should never disagree.
     */
    @Override
    @Transactional
    public void append(Long jobId, JobLogLevel level, String message) {
        if (jobId == null) {
            return;
        }
        try {
            EmailJob job = jobRepository.findByIdOptional(jobId).orElse(null);
            if (job == null) {
                return;
            }
            String safe = redactor.redactAndTrim(message == null ? "" : message, MESSAGE_LIMIT);
            repository.persist(JobLog.of(job, level, safe == null ? "" : safe));
        } catch (RuntimeException e) {
            LOG.warnf("Could not persist job log for job %d: %s", jobId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public List<JobLogResponse> list(Long jobId, int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 2000);
        return repository.listForJobAscending(jobId, safeLimit).stream()
                .map(JobLogServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public long count(Long jobId, JobLogLevel level) {
        return repository.countByLevel(jobId, level);
    }

    static JobLogResponse toResponse(JobLog log) {
        return new JobLogResponse(log.id, log.job == null ? null : log.job.id, log.level, log.message, log.createdAt);
    }

    /** Convenience for the default page size. */
    public List<JobLogResponse> list(Long jobId) {
        return list(jobId, DEFAULT_LIMIT);
    }

    static void requireJob(Long jobId) {
        if (jobId == null) {
            throw new ResourceNotFoundException("Job id is required");
        }
    }
}
