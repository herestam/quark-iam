package com.example.emailapp.job.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Append-only audit trail for a job. Rendered on the job detail page and
 * exposed through {@code GET /api/jobs/{id}/logs}.
 */
@Entity
@Table(name = "job_logs")
public class JobLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_job_logs_job"))
    public EmailJob job;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 10)
    public JobLogLevel level = JobLogLevel.INFO;

    @Column(name = "message", nullable = false, length = 4000)
    public String message;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static JobLog of(EmailJob job, JobLogLevel level, String message) {
        JobLog log = new JobLog();
        log.job = job;
        log.level = level;
        log.message = message;
        log.createdAt = Instant.now();
        return log;
    }

    @Override
    public String toString() {
        return "JobLog{id=" + id + ", level=" + level + "}";
    }
}
