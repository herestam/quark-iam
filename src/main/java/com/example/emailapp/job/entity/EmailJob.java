package com.example.emailapp.job.entity;

import com.example.emailapp.campaign.entity.Campaign;
import com.example.emailapp.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;

/**
 * A single background send of a campaign.
 *
 * <p>Creating a job is cheap and happens inside the HTTP request; the actual
 * sending happens in {@code JobWorker}. The counters are denormalised on
 * purpose: the monitoring UI polls them several times per second and must not
 * have to aggregate thousands of {@code job_recipients} rows on every request.</p>
 */
@Entity
@Table(name = "job")
public class EmailJob extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false, foreignKey = @ForeignKey(name = "fk_job_campaign"))
    public Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public JobStatus status = JobStatus.PENDING;

    @Column(name = "total_count", nullable = false)
    public int totalCount;

    @Column(name = "success_count", nullable = false)
    public int successCount;

    @Column(name = "failed_count", nullable = false)
    public int failedCount;

    /** success + failed, i.e. everything that reached a terminal state. */
    @Column(name = "processed_count", nullable = false)
    public int processedCount;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "cancelled_at")
    public Instant cancelledAt;

    @Column(name = "error_message", length = 2000)
    public String errorMessage;

    /** Recipients still waiting to be attempted. Derived value, refreshed on read. */
    @Transient
    public int pendingCount;

    public int progressPercent() {
        if (totalCount <= 0) {
            return 0;
        }
        return Math.min(100, (int) Math.round((processedCount * 100.0) / totalCount));
    }

    /** Bean-style accessor, required by the {@code Optional.map(...)} chains in the service layer. */
    public JobStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "EmailJob{id=" + id + ", status=" + status + ", total=" + totalCount + "}";
    }
}
