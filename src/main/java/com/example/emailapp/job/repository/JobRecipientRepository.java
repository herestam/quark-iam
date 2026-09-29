package com.example.emailapp.job.repository;

import java.util.List;
import java.util.Optional;

import com.example.emailapp.job.entity.JobRecipient;
import com.example.emailapp.job.entity.JobRecipientStatus;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class JobRecipientRepository implements PanacheRepositoryBase<JobRecipient, Long> {

    public Optional<JobRecipient> findByIdOptional(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return find("id", id).firstResultOptional();
    }

    public long countByStatus(Long jobId, JobRecipientStatus status) {
        return count("job.id = ?1 and status = ?2", jobId, status);
    }

    public long countByStatuses(Long jobId, List<JobRecipientStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0L;
        }
        return count("job.id = ?1 and status in ?2", jobId, statuses);
    }

    public long countForJob(Long jobId) {
        return count("job.id = ?1", jobId);
    }

    /**
     * Locks and takes the next batch of pending recipients.
     *
     * <p>The pessimistic write lock plus the {@code PROCESSING} status make a
     * batch the unit of work: if the worker dies mid-batch the rows are left in
     * {@code PROCESSING} and can be recovered by a maintenance query instead of
     * being silently lost.</p>
     */
    public List<JobRecipient> claimNextBatch(Long jobId, int batchSize) {
        return getEntityManager()
                .createQuery("select j from JobRecipient j join fetch j.recipient"
                        + " where j.job.id = :jobId and j.status = :pending"
                        + " order by j.id", JobRecipient.class)
                .setParameter("jobId", jobId)
                .setParameter("pending", JobRecipientStatus.PENDING)
                .setMaxResults(Math.max(1, batchSize))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    public List<JobRecipient> listForJob(Long jobId, JobRecipientStatus status, int page, int size) {
        if (status == null) {
            return getEntityManager()
                    .createQuery("select j from JobRecipient j join fetch j.recipient where j.job.id = :jobId"
                            + " order by j.id", JobRecipient.class)
                    .setParameter("jobId", jobId)
                    .setFirstResult(page * size)
                    .setMaxResults(size)
                    .getResultList();
        }
        return getEntityManager()
                .createQuery("select j from JobRecipient j join fetch j.recipient"
                        + " where j.job.id = :jobId and j.status = :status order by j.id", JobRecipient.class)
                .setParameter("jobId", jobId)
                .setParameter("status", status)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public long countForJob(Long jobId, JobRecipientStatus status) {
        if (status == null) {
            return countForJob(jobId);
        }
        return countByStatus(jobId, status);
    }

    /** Re-queues failed recipients that still have attempts left. */
    public int requeueFailed(Long jobId, int maxRetries) {
        return getEntityManager()
                .createQuery("update JobRecipient j set j.status = :pending, j.errorMessage = null,"
                        + " j.failedAt = null, j.updatedAt = :now"
                        + " where j.job.id = :jobId and j.status = :failed and j.attemptCount < :maxRetries")
                .setParameter("pending", JobRecipientStatus.PENDING)
                .setParameter("failed", JobRecipientStatus.FAILED)
                .setParameter("jobId", jobId)
                .setParameter("maxRetries", maxRetries)
                .setParameter("now", java.time.Instant.now())
                .executeUpdate();
    }

    /** How many failed recipients may never be retried again. */
    public long countExhausted(Long jobId, int maxRetries) {
        return getEntityManager()
                .createQuery("select count(j) from JobRecipient j"
                        + " where j.job.id = :jobId and j.status = :failed and j.attemptCount >= :maxRetries", Long.class)
                .setParameter("jobId", jobId)
                .setParameter("failed", JobRecipientStatus.FAILED)
                .setParameter("maxRetries", maxRetries)
                .getSingleResult();
    }

    /** Cancels everything still waiting so a cancelled job leaves no orphan rows. */
    public int cancelPending(Long jobId) {
        return getEntityManager()
                .createQuery("update JobRecipient j set j.status = :cancelled, j.updatedAt = :now"
                        + " where j.job.id = :jobId and j.status in :open")
                .setParameter("cancelled", JobRecipientStatus.CANCELLED)
                .setParameter("open", List.of(JobRecipientStatus.PENDING, JobRecipientStatus.PROCESSING))
                .setParameter("jobId", jobId)
                .setParameter("now", java.time.Instant.now())
                .executeUpdate();
    }

    public long countSucceeded(Long jobId) {
        return countByStatus(jobId, JobRecipientStatus.SENT);
    }

    /**
     * Frees rows left in {@code PROCESSING} by a worker that died mid-batch.
     * Only used by {@code recoverInterrupted()} at startup.
     */
    public int releaseStuckProcessing(Long jobId) {
        return getEntityManager()
                .createQuery("update JobRecipient j set j.status = :pending, j.updatedAt = :now"
                        + " where j.job.id = :jobId and j.status = :processing")
                .setParameter("pending", JobRecipientStatus.PENDING)
                .setParameter("processing", JobRecipientStatus.PROCESSING)
                .setParameter("jobId", jobId)
                .setParameter("now", java.time.Instant.now())
                .executeUpdate();
    }

    public long countFailed(Long jobId) {
        return countByStatus(jobId, JobRecipientStatus.FAILED);
    }

    public long countCancelled(Long jobId) {
        return countByStatus(jobId, JobRecipientStatus.CANCELLED);
    }
}
