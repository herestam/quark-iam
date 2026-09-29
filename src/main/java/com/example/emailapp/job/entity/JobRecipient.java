package com.example.emailapp.job.entity;

import com.example.emailapp.common.entity.AuditableEntity;
import com.example.emailapp.recipient.entity.Recipient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Delivery state of one recipient inside one job.
 *
 * <p>This is the row that answers "exactly which addresses were delivered and
 * which ones failed", and it is also what {@code Retry Failed} resets.</p>
 */
@Entity
@Table(name = "job_recipients")
public class JobRecipient extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_job_recipients_job"))
    public EmailJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_job_recipients_recipient"))
    public Recipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public JobRecipientStatus status = JobRecipientStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    public int attemptCount;

    @Column(name = "sent_at")
    public Instant sentAt;

    @Column(name = "failed_at")
    public Instant failedAt;

    @Column(name = "error_message", length = 2000)
    public String errorMessage;

    /** The provider already refused this address more times than allowed. */
    public boolean isRetryable(int maxRetries) {
        return status == JobRecipientStatus.FAILED && attemptCount < maxRetries;
    }

    @Override
    public String toString() {
        return "JobRecipient{id=" + id + ", status=" + status + ", attempts=" + attemptCount + "}";
    }
}
