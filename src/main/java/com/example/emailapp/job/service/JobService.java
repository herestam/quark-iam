package com.example.emailapp.job.service;

import java.util.List;

import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.job.dto.CreateJobRequest;
import com.example.emailapp.job.dto.DashboardStatsResponse;
import com.example.emailapp.job.dto.JobControlSignal;
import com.example.emailapp.job.dto.JobExecutionContext;
import com.example.emailapp.job.dto.JobRecipientResponse;
import com.example.emailapp.job.dto.JobRecipientWork;
import com.example.emailapp.job.dto.JobResponse;
import com.example.emailapp.job.entity.JobRecipientStatus;

/**
 * The whole background job contract.
 *
 * <p>Read methods are used by the REST API and the Qute pages; the write
 * methods are the only way {@code JobWorker} is allowed to change state. The
 * worker never touches an entity or a table directly, which is what keeps a
 * future swap to a real queue (SQS, RabbitMQ) a transport level change only.</p>
 */
public interface JobService {

    // ------------------------------------------------------------- monitoring

    PageResponse<JobResponse> list(int page, int size);

    PageResponse<JobResponse> listByCampaign(Long campaignId, int page, int size);

    JobResponse get(Long jobId);

    PageResponse<JobRecipientResponse> listRecipients(
            Long jobId, JobRecipientStatus status, int page, int size);

    DashboardStatsResponse dashboardStats();

    JobExecutionContext executionContext(Long jobId);

    // --------------------------------------------------------------- commands

    /** Snapshots the campaign audience and queues the job. Returns immediately. */
    JobResponse create(CreateJobRequest request);

    /** PENDING to RUNNING, then hands the job to the background worker. */
    JobResponse start(Long jobId);

    JobResponse pause(Long jobId);

    /** PAUSED back to the queue, resuming from the first unprocessed recipient. */
    JobResponse resume(Long jobId);

    JobResponse cancel(Long jobId);

    /** Re-queues failed recipients that are still under the retry limit. */
    JobResponse retryFailed(Long jobId);

    // ----------------------------------------------------------- worker hooks

    /**
     * Ids of jobs that are queued and waiting for a worker, oldest first.
     * Used by the scheduler poll loop.
     */
    List<Long> findQueuedJobIds(int limit);

    /**
     * Atomically moves a queued job to {@code RUNNING}. Returns {@code false} if
     * another worker already claimed it.
     */
    boolean claim(Long jobId);

    /** Reads pause/cancel intent from the database. */
    JobControlSignal controlSignal(Long jobId);

    /** Locks and takes the next batch of pending recipients. */
    List<JobRecipientWork> claimBatch(Long jobId, int batchSize);

    void recordSuccess(Long jobId, Long jobRecipientId);

    void recordFailure(Long jobId, Long jobRecipientId, String error);

    /** Puts a claimed recipient back into the queue after a pause or cancel. */
    void release(Long jobId, Long jobRecipientId);

    boolean hasOutstanding(Long jobId);

    /** Marks the job terminal when nothing is left to send. */
    void complete(Long jobId);

    /** Marks the job as broken because the worker itself failed. */
    void fail(Long jobId, String error);

    void cancelOutstanding(Long jobId);

    long pendingCount(Long jobId);

    /** Re-queues jobs interrupted by an application restart. Startup only. */
    int recoverInterrupted();
}
