package com.example.emailapp.job.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import com.example.emailapp.campaign.entity.Campaign;
import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.campaign.repository.CampaignRepository;
import com.example.emailapp.common.exception.BusinessRuleException;
import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.common.exception.ResourceNotFoundException;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.common.web.UiSupport;
import com.example.emailapp.config.EmailConfig;
import com.example.emailapp.job.dto.CreateJobRequest;
import com.example.emailapp.job.dto.DashboardStatsResponse;
import com.example.emailapp.job.dto.JobControlSignal;
import com.example.emailapp.job.dto.JobExecutionContext;
import com.example.emailapp.job.dto.JobRecipientResponse;
import com.example.emailapp.job.dto.JobRecipientWork;
import com.example.emailapp.job.dto.JobResponse;
import com.example.emailapp.job.entity.EmailJob;
import com.example.emailapp.job.worker.JobQueuedEvent;
import com.example.emailapp.job.entity.JobRecipient;
import com.example.emailapp.job.entity.JobRecipientStatus;
import com.example.emailapp.job.entity.JobStatus;
import com.example.emailapp.job.repository.EmailJobRepository;
import com.example.emailapp.job.repository.JobRecipientRepository;
import com.example.emailapp.job.service.JobLogService;
import com.example.emailapp.job.service.JobService;
import com.example.emailapp.recipient.entity.Recipient;
import com.example.emailapp.recipient.repository.RecipientRepository;
import com.example.emailapp.template.repository.EmailTemplateRepository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

/**
 * The single place where a job changes state.
 *
 * <p>Two callers share this class: the HTTP layer issues commands ({@code start},
 * {@code pause}, ...) and {@code JobWorker} drives the send loop through the
 * worker hooks. Neither touches an entity directly, which is what keeps a future
 * swap to a real queue a transport level change only.</p>
 */
@ApplicationScoped
public class JobServiceImpl implements JobService {

    private static final Logger LOG = Logger.getLogger(JobServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 200;

    @Inject
    EmailJobRepository jobRepository;

    @Inject
    JobRecipientRepository jobRecipientRepository;

    @Inject
    CampaignRepository campaignRepository;

    @Inject
    RecipientRepository recipientRepository;

    @Inject
    EmailTemplateRepository templateRepository;

    @Inject
    EmailConfig emailConfig;

    @Inject
    JobLogService jobLogService;

    @Inject
    Event<JobQueuedEvent> jobQueued;

    @Inject
    TransactionSynchronizationRegistry transactionRegistry;

    // ------------------------------------------------------------- monitoring

    @Override
    @Transactional
    public PageResponse<JobResponse> list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = clampSize(size);
        List<JobResponse> items = new ArrayList<>();
        for (EmailJob job : jobRepository.listWithCampaign(safePage, safeSize)) {
            items.add(toResponse(job));
        }
        return PageResponse.of(items, safePage, safeSize, jobRepository.countAll());
    }

    @Override
    @Transactional
    public PageResponse<JobResponse> listByCampaign(Long campaignId, int page, int size) {
        if (campaignId == null) {
            return list(page, size);
        }
        int safePage = Math.max(0, page);
        int safeSize = clampSize(size);
        List<JobResponse> items = new ArrayList<>();
        for (EmailJob job : jobRepository.listByCampaign(campaignId, safePage, safeSize)) {
            items.add(toResponse(job));
        }
        return PageResponse.of(items, safePage, safeSize, jobRepository.countByCampaignId(campaignId));
    }

    @Override
    @Transactional
    public JobResponse get(Long jobId) {
        return toResponse(requireWithCampaign(jobId));
    }

    @Override
    @Transactional
    public PageResponse<JobRecipientResponse> listRecipients(
            Long jobId, JobRecipientStatus status, int page, int size) {
        require(jobId);
        int safePage = Math.max(0, page);
        int safeSize = clampSize(size);
        int maxRetries = Math.max(1, emailConfig.maxRetries());
        List<JobRecipientResponse> items = new ArrayList<>();
        for (JobRecipient row : jobRecipientRepository.listForJob(jobId, status, safePage, safeSize)) {
            items.add(new JobRecipientResponse(
                    row.id,
                    row.recipient == null ? null : row.recipient.id,
                    row.recipient == null ? null : row.recipient.email,
                    row.recipient == null ? null : row.recipient.name,
                    row.recipient == null ? null : row.recipient.company,
                    row.status,
                    row.attemptCount,
                    row.isRetryable(maxRetries),
                    row.sentAt,
                    row.failedAt,
                    row.errorMessage));
        }
        return PageResponse.of(items, safePage, safeSize,
                jobRecipientRepository.countForJob(jobId, status));
    }

    @Override
    @Transactional
    public DashboardStatsResponse dashboardStats() {
        List<JobResponse> recent = new ArrayList<>();
        for (EmailJob job : jobRepository.listRecent(8)) {
            recent.add(toResponse(job));
        }
        return new DashboardStatsResponse(
                campaignRepository.countAll(),
                templateRepository.countAll(),
                recipientRepository.countAll(),
                jobRepository.countAll(),
                jobRepository.countByStatus(JobStatus.PENDING),
                jobRepository.countByStatus(JobStatus.RUNNING),
                jobRepository.countByStatus(JobStatus.PAUSED),
                jobRepository.countByStatus(JobStatus.COMPLETED),
                jobRepository.countByStatus(JobStatus.FAILED),
                jobRepository.countByStatus(JobStatus.CANCELLED),
                jobRepository.sumSuccessCount(),
                jobRepository.sumFailedCount(),
                jobRepository.sumPendingCount(),
                recent);
    }

    @Override
    @Transactional
    public JobExecutionContext executionContext(Long jobId) {
        EmailJob job = requireWithCampaign(jobId);
        return new JobExecutionContext(
                job.id,
                job.campaign == null ? null : job.campaign.id,
                job.campaign == null ? "(unknown campaign)" : job.campaign.name,
                job.totalCount);
    }

    // --------------------------------------------------------------- commands

    @Override
    @Transactional
    public JobResponse create(CreateJobRequest request) {
        if (request == null || request.campaignId() == null) {
            throw new InputValidationException("campaignId is required", List.of("campaignId: must be provided"));
        }
        Campaign campaign = campaignRepository.findByIdWithDetails(request.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", request.campaignId()));

        List<Recipient> audience = List.copyOf(campaign.recipients);
        if (audience.isEmpty()) {
            throw new BusinessRuleException(
                    "Campaign '" + campaign.name + "' has no recipients. Add recipients before starting a job.");
        }

        EmailJob job = new EmailJob();
        job.campaign = campaign;
        job.status = JobStatus.PENDING;
        job.totalCount = audience.size();
        jobRepository.persist(job);
        jobRepository.flush();

        // Snapshot the audience: later edits to the campaign must not change this job.
        List<JobRecipient> rows = new ArrayList<>(audience.size());
        for (Recipient recipient : audience) {
            JobRecipient row = new JobRecipient();
            row.job = job;
            row.recipient = recipient;
            row.status = JobRecipientStatus.PENDING;
            rows.add(row);
        }
        jobRecipientRepository.persist(rows);
        jobRecipientRepository.flush();

        setCampaignStatus(campaign, CampaignStatus.RUNNING);
        jobLogService.info(job.id, "Job queued for campaign '" + campaign.name + "' with "
                + audience.size() + " recipient(s)");
        LOG.infof("Job %d queued for campaign %d (%d recipients)", job.id, campaign.id, audience.size());

        dispatchAfterCommit(job.id);
        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse start(Long jobId) {
        EmailJob job = require(jobId);
        if (job.status == JobStatus.RUNNING) {
            return toResponse(job);
        }
        if (job.status == JobStatus.PAUSED) {
            throw new BusinessRuleException("Job is paused. Use Resume to continue it.");
        }
        if (job.status.isTerminal()) {
            throw new BusinessRuleException("Job is " + job.status + " and cannot be started again");
        }
        if (job.startedAt == null) {
            job.startedAt = Instant.now();
        }
        job.errorMessage = null;
        jobLogService.info(job.id, "Start requested");
        dispatchAfterCommit(job.id);
        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse pause(Long jobId) {
        EmailJob job = require(jobId);
        if (job.status == JobStatus.PAUSED) {
            return toResponse(job);
        }
        if (!job.status.isActive()) {
            throw new BusinessRuleException(
                    "Only a pending or running job can be paused (this one is " + job.status + ")");
        }
        job.status = JobStatus.PAUSED;
        setCampaignStatus(job.campaign, CampaignStatus.PAUSED);
        jobLogService.info(job.id, "Pause requested - the current message will finish first");
        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse resume(Long jobId) {
        EmailJob job = require(jobId);
        if (job.status == JobStatus.RUNNING || job.status == JobStatus.PENDING) {
            return toResponse(job);
        }
        if (job.status != JobStatus.PAUSED) {
            throw new BusinessRuleException("Only a paused job can be resumed (this one is " + job.status + ")");
        }
        job.status = JobStatus.PENDING;
        setCampaignStatus(job.campaign, CampaignStatus.RUNNING);
        jobLogService.info(job.id, "Resume requested");
        dispatchAfterCommit(job.id);
        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse cancel(Long jobId) {
        EmailJob job = require(jobId);
        if (job.status == JobStatus.CANCELLED) {
            return toResponse(job);
        }
        if (job.status.isTerminal()) {
            throw new BusinessRuleException("Job is already " + job.status);
        }
        Instant now = Instant.now();
        job.status = JobStatus.CANCELLED;
        job.cancelledAt = now;
        job.completedAt = now;

        int cancelled = jobRecipientRepository.cancelPending(job.id);
        setCampaignStatus(job.campaign, CampaignStatus.CANCELLED);
        jobLogService.warn(job.id, "Cancelled by user - " + cancelled + " recipient(s) were not sent");
        LOG.infof("Job %d cancelled (%d recipients dropped)", job.id, cancelled);
        return toResponse(job);
    }

    @Override
    @Transactional
    public JobResponse retryFailed(Long jobId) {
        EmailJob job = require(jobId);
        if (job.status.isActive() || job.status == JobStatus.PAUSED) {
            throw new BusinessRuleException("Stop the job before retrying failed recipients");
        }
        int maxRetries = Math.max(1, emailConfig.maxRetries());
        long exhausted = jobRecipientRepository.countExhausted(job.id, maxRetries);
        int requeued = jobRecipientRepository.requeueFailed(job.id, maxRetries);
        if (requeued == 0) {
            throw new BusinessRuleException(exhausted == 0
                    ? "This job has no failed recipients to retry"
                    : "All " + exhausted + " failed recipient(s) already used their " + maxRetries + " attempt(s)");
        }

        job.failedCount = Math.max(0, job.failedCount - requeued);
        job.processedCount = Math.max(0, job.processedCount - requeued);
        job.status = JobStatus.PENDING;
        job.completedAt = null;
        job.cancelledAt = null;
        job.errorMessage = null;

        setCampaignStatus(job.campaign, CampaignStatus.RUNNING);
        jobLogService.warn(job.id, "Retrying " + requeued + " failed recipient(s)"
                + (exhausted > 0 ? "; " + exhausted + " exceeded the " + maxRetries + " attempt limit" : ""));
        dispatchAfterCommit(job.id);
        return toResponse(job);
    }

    // ----------------------------------------------------------- worker hooks

    @Override
    @Transactional
    public List<Long> findQueuedJobIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jobRepository.findQueued(limit).stream()
                .filter(job -> job.status == JobStatus.PENDING)
                .map(job -> job.id)
                .toList();
    }

    @Override
    @Transactional
    public boolean claim(Long jobId) {
        if (jobId == null || !jobRepository.claimForRunning(jobId)) {
            return false;
        }
        EmailJob job = require(jobId);
        if (job.startedAt == null) {
            job.startedAt = Instant.now();
        }
        return true;
    }

    @Override
    @Transactional
    public JobControlSignal controlSignal(Long jobId) {
        EmailJob job = require(jobId);
        if (job.status == JobStatus.CANCELLED) {
            return JobControlSignal.CANCEL;
        }
        if (job.status == JobStatus.PAUSED) {
            return JobControlSignal.PAUSE;
        }
        if (job.status == JobStatus.RUNNING) {
            return JobControlSignal.RUN;
        }
        return JobControlSignal.FINISHED;
    }

    @Override
    @Transactional
    public List<JobRecipientWork> claimBatch(Long jobId, int batchSize) {
        if (jobId == null || batchSize <= 0) {
            return List.of();
        }
        require(jobId);
        List<JobRecipientWork> work = new ArrayList<>();
        for (JobRecipient row : jobRecipientRepository.claimNextBatch(jobId, batchSize)) {
            row.status = JobRecipientStatus.PROCESSING;
            row.attemptCount++;
            Recipient recipient = row.recipient;
            work.add(new JobRecipientWork(
                    row.id,
                    recipient == null ? null : recipient.id,
                    recipient == null ? null : recipient.email,
                    recipient == null ? null : recipient.name,
                    recipient == null ? null : recipient.company,
                    row.attemptCount));
        }
        return work;
    }

    @Override
    @Transactional
    public void recordSuccess(Long jobId, Long jobRecipientId) {
        EmailJob job = require(jobId);
        JobRecipient row = requireRecipient(jobRecipientId);
        if (row.status == JobRecipientStatus.SENT) {
            return;
        }
        row.status = JobRecipientStatus.SENT;
        row.sentAt = Instant.now();
        row.failedAt = null;
        row.errorMessage = null;
        job.successCount++;
        job.processedCount++;
    }

    @Override
    @Transactional
    public void recordFailure(Long jobId, Long jobRecipientId, String error) {
        EmailJob job = require(jobId);
        JobRecipient row = requireRecipient(jobRecipientId);
        if (row.status == JobRecipientStatus.SENT) {
            return;
        }
        int maxRetries = Math.max(1, emailConfig.maxRetries());
        row.status = JobRecipientStatus.FAILED;
        row.failedAt = Instant.now();
        row.errorMessage = truncate(error, 2000);
        job.failedCount++;
        job.processedCount++;
        if (row.attemptCount >= maxRetries) {
            LOG.warnf("Job %d gave up on %s after %d attempt(s)", jobId, row.recipient == null ? "?" : row.recipient.email,
                    row.attemptCount);
        }
    }

    @Override
    @Transactional
    public void release(Long jobId, Long jobRecipientId) {
        JobRecipient row = requireRecipient(jobRecipientId);
        if (row.status != JobRecipientStatus.PROCESSING) {
            return;
        }
        // A pause or cancel happened while this row was claimed.
        row.status = JobRecipientStatus.PENDING;
        row.attemptCount = Math.max(0, row.attemptCount - 1);
    }

    @Override
    @Transactional
    public boolean hasOutstanding(Long jobId) {
        EmailJob job = require(jobId);
        long outstanding = jobRecipientRepository.countByStatuses(jobId,
                List.of(JobRecipientStatus.PENDING, JobRecipientStatus.PROCESSING));
        return outstanding > 0;
    }

    @Override
    @Transactional
    public void complete(Long jobId) {
        EmailJob job = require(jobId);
        if (job.status.isTerminal()) {
            return;
        }
        Instant now = Instant.now();
        job.status = job.failedCount > 0 ? JobStatus.FAILED : JobStatus.COMPLETED;
        job.completedAt = now;
        setCampaignStatus(job.campaign, job.status == JobStatus.FAILED ? CampaignStatus.FAILED : CampaignStatus.COMPLETED);
        jobLogService.info(job.id, "Finished: " + job.successCount + " sent, " + job.failedCount + " failed");
        LOG.infof("Job %d finished (%d sent, %d failed)", jobId, job.successCount, job.failedCount);
    }

    @Override
    @Transactional
    public void fail(Long jobId, String error) {
        EmailJob job = require(jobId);
        if (job.status.isTerminal()) {
            return;
        }
        job.status = JobStatus.FAILED;
        job.completedAt = Instant.now();
        job.errorMessage = truncate(error, 2000);
        setCampaignStatus(job.campaign, CampaignStatus.FAILED);
        jobLogService.error(job.id, "Worker failed: " + job.errorMessage);
        LOG.errorf("Job %d failed: %s", jobId, job.errorMessage);
    }

    @Override
    @Transactional
    public void cancelOutstanding(Long jobId) {
        int cancelled = jobRecipientRepository.cancelPending(jobId);
        if (cancelled > 0) {
            jobLogService.warn(jobId, "Cancelled " + cancelled + " recipient(s) that had not been sent");
        }
    }

    @Override
    @Transactional
    public long pendingCount(Long jobId) {
        return jobRecipientRepository.countByStatus(jobId, JobRecipientStatus.PENDING);
    }

    @Override
    @Transactional
    public int recoverInterrupted() {
        List<EmailJob> interrupted = jobRepository.listByStatus(JobStatus.RUNNING);
        if (interrupted.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (EmailJob job : interrupted) {
            int released = jobRecipientRepository.releaseStuckProcessing(job.id);
            job.status = JobStatus.PENDING;
            job.completedAt = null;
            job.cancelledAt = null;
            jobLogService.warn(job.id, "Re-queued after an application restart ("
                    + released + " recipient(s) released)");
            recovered++;
        }
        LOG.infof("Recovered %d interrupted job(s)", recovered);
        return recovered;
    }

    // --------------------------------------------------------------- internals

    private EmailJob require(Long jobId) {
        if (jobId == null) {
            throw new InputValidationException("Job id is required");
        }
        return jobRepository.findByIdOptional(jobId).orElseThrow(() -> new ResourceNotFoundException("EmailJob", jobId));
    }

    private EmailJob requireWithCampaign(Long jobId) {
        if (jobId == null) {
            throw new InputValidationException("Job id is required");
        }
        return jobRepository.findByIdWithCampaign(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailJob", jobId));
    }

    private JobRecipient requireRecipient(Long jobRecipientId) {
        if (jobRecipientId == null) {
            throw new InputValidationException("Job recipient id is required");
        }
        return jobRecipientRepository.findByIdOptional(jobRecipientId)
                .orElseThrow(() -> new ResourceNotFoundException("JobRecipient", jobRecipientId));
    }

    private void setCampaignStatus(Campaign campaign, CampaignStatus status) {
        if (campaign != null) {
            campaign.status = status;
        }
    }

    /**
     * The worker must only see committed rows, so the hand-off is deferred to
     * {@code afterCompletion}. Outside a transaction (unit tests) it fires at once.
     */
    private void dispatchAfterCommit(Long jobId) {
        if (!QuarkusTransaction.isActive()) {
            jobQueued.fire(new JobQueuedEvent(jobId));
            return;
        }
        transactionRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // nothing to do: the worker must only see committed rows
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    jobQueued.fire(new JobQueuedEvent(jobId));
                }
            }
        });
    }

    private JobResponse toResponse(EmailJob job) {
        int total = job.totalCount;
        return new JobResponse(
                job.id,
                job.campaign == null ? null : job.campaign.id,
                job.campaign == null ? "(unknown campaign)" : job.campaign.name,
                job.status,
                total,
                job.successCount,
                job.failedCount,
                job.processedCount,
                Math.max(0, total - job.processedCount),
                UiSupport.percent(job.processedCount, total),
                job.startedAt,
                job.completedAt,
                job.cancelledAt,
                UiSupport.elapsed(job.startedAt, job.completedAt != null ? job.completedAt : job.cancelledAt),
                job.errorMessage,
                job.createdAt,
                job.updatedAt,
                // Mirrors the rules enforced by pause/resume/cancel/retryFailed below.
                job.status.isActive(),
                job.status == JobStatus.PAUSED,
                job.status.isActive() || job.status == JobStatus.PAUSED,
                (job.status.isTerminal()) && job.failedCount > 0,
                List.of("ALL", "PENDING", "PROCESSING", "SENT", "FAILED", "CANCELLED"));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
