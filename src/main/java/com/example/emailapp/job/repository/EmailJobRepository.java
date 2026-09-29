package com.example.emailapp.job.repository;

import java.util.List;
import java.util.Optional;

import com.example.emailapp.job.entity.EmailJob;
import com.example.emailapp.job.entity.JobStatus;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class EmailJobRepository implements PanacheRepositoryBase<EmailJob, Long> {

    public Optional<EmailJob> findByIdOptional(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return find("id", id).firstResultOptional();
    }

    /** The job plus its campaign name, which every monitoring screen needs. */
    public Optional<EmailJob> findByIdWithCampaign(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return getEntityManager()
                .createQuery("select j from EmailJob j left join fetch j.campaign where j.id = :id", EmailJob.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    public List<EmailJob> listWithCampaign(int page, int size) {
        return getEntityManager()
                .createQuery("select j from EmailJob j left join fetch j.campaign order by j.createdAt desc", EmailJob.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /** Jobs created for one campaign, newest first, for the campaign detail page. */
    public List<EmailJob> listByCampaign(Long campaignId, int page, int size) {
        if (campaignId == null) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("select j from EmailJob j left join fetch j.campaign"
                        + " where j.campaign.id = :campaignId order by j.createdAt desc", EmailJob.class)
                .setParameter("campaignId", campaignId)
                .setFirstResult(Math.max(0, page) * Math.max(1, size))
                .setMaxResults(Math.max(1, size))
                .getResultList();
    }

    public long countByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return 0L;
        }
        return getEntityManager()
                .createQuery("select count(j) from EmailJob j where j.campaign.id = :campaignId", Long.class)
                .setParameter("campaignId", campaignId)
                .getSingleResult();
    }

    public List<EmailJob> listRecent(int limit) {
        return getEntityManager()
                .createQuery("select j from EmailJob j left join fetch j.campaign order by j.createdAt desc", EmailJob.class)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    public List<EmailJob> findByStatus(JobStatus status, int limit) {
        return getEntityManager()
                .createQuery("select j from EmailJob j left join fetch j.campaign where j.status = :status"
                        + " order by j.createdAt asc", EmailJob.class)
                .setParameter("status", status)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    /**
     * Queued jobs waiting for a worker, oldest first.
     *
     * <p>{@code SKIP LOCKED} means two instances of the application can run the
     * same scheduler without ever handing the same job to both of them, which
     * is the first step towards a queue based deployment.</p>
     */
    public List<EmailJob> findQueued(int limit) {
        return getEntityManager()
                .createQuery("select j from EmailJob j where j.status = :status order by j.createdAt asc", EmailJob.class)
                .setParameter("status", JobStatus.PENDING)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    /**
     * Atomically moves a queued job to {@code RUNNING}. Returns {@code false}
     * when another worker already claimed it, which makes the whole operation
     * safe without an application level lock.
     */
    public boolean claimForRunning(Long jobId) {
        int updated = getEntityManager()
                .createQuery("update EmailJob j set j.status = :running, j.updatedAt = :now"
                        + " where j.id = :id and j.status = :pending")
                .setParameter("running", JobStatus.RUNNING)
                .setParameter("pending", JobStatus.PENDING)
                .setParameter("id", jobId)
                .setParameter("now", java.time.Instant.now())
                .executeUpdate();
        // A bulk JPQL update bypasses the persistence context: drop it so a
        // following find() reads the new status instead of a cached entity.
        getEntityManager().clear();
        return updated == 1;
    }

    /** Total number of emails ever delivered, across every job. */
    public long sumSuccessCount() {
        return getEntityManager()
                .createQuery("select coalesce(sum(j.successCount), 0) from EmailJob j", Long.class)
                .getSingleResult();
    }

    /** Total number of failed deliveries across every job. */
    public long sumFailedCount() {
        return getEntityManager()
                .createQuery("select coalesce(sum(j.failedCount), 0) from EmailJob j", Long.class)
                .getSingleResult();
    }

    /** Deliveries still owed to recipients of active jobs. */
    public long sumPendingCount() {
        return getEntityManager()
                .createQuery("select coalesce(sum(case when j.status in :active"
                        + " then (j.totalCount - j.processedCount) else 0 end), 0) from EmailJob j", Long.class)
                .setParameter("active", List.of(JobStatus.PENDING, JobStatus.RUNNING, JobStatus.PAUSED))
                .getSingleResult();
    }

    public long countByStatus(JobStatus status) {
        return count("status", status);
    }

    public long countAll() {
        return count();
    }

    public List<EmailJob> listByStatus(JobStatus status) {
        return list("status = ?1 order by updatedAt desc", status);
    }
}
