package com.example.emailapp.job.worker;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import com.example.emailapp.campaign.dto.DispatchContext;
import com.example.emailapp.campaign.service.CampaignService;
import com.example.emailapp.config.AppConfig;
import com.example.emailapp.config.EmailConfig;
import com.example.emailapp.email.dto.OutboundEmail;
import com.example.emailapp.email.service.EmailDispatchService;
import com.example.emailapp.job.dto.JobControlSignal;
import com.example.emailapp.job.dto.JobExecutionContext;
import com.example.emailapp.job.dto.JobRecipientWork;
import com.example.emailapp.job.service.JobLogService;
import com.example.emailapp.job.service.JobService;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs queued jobs in the background.
 *
 * <p>Two mechanisms cooperate:</p>
 * <ul>
 *   <li>{@link #pollQueuedJobs()} runs on the Quarkus scheduler and claims any
 *       {@code PENDING} job. It is the safety net: it also picks up jobs left
 *       behind by a previous process, or a user action that failed to notify.</li>
 *   <li>{@link #onJobQueued(JobQueuedEvent)} reacts immediately to a start /
 *       resume / retry, so the common case has no scheduler latency.</li>
 * </ul>
 *
 * <p>The actual send loop lives in {@link #executeJob(Long)} and is written so
 * it can be unit tested with mocked collaborators: no HTTP, no database access
 * and no real email.</p>
 *
 * <p>Sending is rate limited by {@code app.email.batch-size} and
 * {@code app.email.delay-ms}: a batch of N messages, then a pause. A single
 * failing recipient is caught and recorded, never propagated, so one bad
 * address can not abort a campaign.</p>
 */
@ApplicationScoped
public class JobWorker {

    private static final Logger LOG = Logger.getLogger(JobWorker.class);
    private static final int ERROR_LIMIT = 500;

    @Inject
    JobService jobService;

    @Inject
    JobLogService jobLogService;

    @Inject
    CampaignService campaignService;

    @Inject
    EmailDispatchService emailDispatchService;

    @Inject
    EmailConfig emailConfig;

    @Inject
    AppConfig appConfig;

    @Inject
    Vertx vertx;

    /** Jobs currently being processed by this instance, so the scheduler skips them. */
    private final Set<Long> active = ConcurrentHashMap.newKeySet();
    private final AtomicInteger running = new AtomicInteger();

    // ------------------------------------------------------------- scheduling

    @Scheduled(every = "{app.job.poll-interval-ms}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void pollQueuedJobs() {
        if (!appConfig.workerEnabled()) {
            return;
        }
        int capacity = Math.max(0, appConfig.maxConcurrentJobs() - running.get());
        if (capacity <= 0) {
            LOG.debugf("Worker at capacity (%d running), skipping this poll", running.get());
            return;
        }
        try {
            List<Long> queued = jobService.findQueuedJobIds(capacity);
            for (Long jobId : queued) {
                if (active.size() >= appConfig.maxConcurrentJobs()) {
                    break;
                }
                schedule(jobId);
            }
        } catch (RuntimeException e) {
            // Never let a scheduler exception bubble up: it would disable the job.
            LOG.error("Job poll failed", e);
        }
    }

    /** Immediate hand-off after a start / resume / retry request. */
    void onJobQueued(@Observes JobQueuedEvent event) {
        if (!appConfig.workerEnabled() || event.jobId() == null) {
            return;
        }
        schedule(event.jobId());
    }

    void onStart(@Observes StartupEvent event) {
        try {
            int recovered = jobService.recoverInterrupted();
            if (recovered > 0) {
                LOG.infof("Re-queued %d job(s) interrupted by the previous shutdown", recovered);
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not reconcile interrupted jobs at startup: " + e.getMessage());
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (!active.isEmpty()) {
            LOG.infof("Shutting down with %d job(s) still in flight; they will resume on the next start", active.size());
        }
    }

    /**
     * Offloads the job onto a Vert.x worker thread. The request thread returns
     * immediately, which is the whole point of the background job system.
     */
    private void schedule(Long jobId) {
        if (jobId == null || !active.add(jobId)) {
            return;
        }
        running.incrementAndGet();
        vertx.executeBlocking(() -> {
            try {
                executeJob(jobId);
            } catch (RuntimeException e) {
                LOG.errorf("Job %d terminated unexpectedly", jobId, e);
                safeFail(jobId, e);
            } finally {
                active.remove(jobId);
                running.decrementAndGet();
            }
            return null;
        }, /* ordered = */ false);
    }

    // ------------------------------------------------------------- send loop

    /**
     * Sends one job to completion, or until it is paused or cancelled.
     *
     * <p>Package visible for the unit tests: the test drives this method
     * synchronously with mocked services.</p>
     */
    void executeJob(Long jobId) {
        if (jobId == null) {
            return;
        }
        if (!jobService.claim(jobId)) {
            LOG.debugf("Job %d was not claimable, another worker owns it", jobId);
            return;
        }

        JobExecutionContext context = safeExecutionContext(jobId);
        if (context == null) {
            return;
        }

        DispatchContext dispatch = safeDispatchContext(context);
        if (dispatch == null) {
            return;
        }

        int batchSize = Math.max(1, emailConfig.batchSize());
        long delayMs = Math.max(0L, emailConfig.delayMs());

        jobLogService.info(jobId, "Job started for campaign '" + context.campaignName() + "' ("
                + context.totalCount() + " recipient(s), batch size " + batchSize + ")");

        try {
            while (true) {
                JobControlSignal signal = jobService.controlSignal(jobId);
                if (signal == JobControlSignal.CANCEL) {
                    stopCancelled(jobId);
                    return;
                }
                if (signal == JobControlSignal.PAUSE) {
                    jobLogService.info(jobId, "Job paused");
                    return;
                }
                if (signal == JobControlSignal.FINISHED) {
                    return;
                }

                List<JobRecipientWork> batch = jobService.claimBatch(jobId, batchSize);
                if (batch.isEmpty()) {
                    if (!jobService.hasOutstanding(jobId)) {
                        jobService.complete(jobId);
                        return;
                    }
                    // Nothing claimable but work is outstanding: the remaining
                    // rows are held by another worker, so wait and look again.
                    sleep(delayMs);
                    continue;
                }

                jobLogService.info(jobId, "Processing batch of " + batch.size());
                for (int index = 0; index < batch.size(); index++) {
                    // Cooperative control: checked before each message, never in
                    // the middle of one, so an in-flight send always ends.
                    JobControlSignal midSignal = jobService.controlSignal(jobId);
                    if (midSignal != JobControlSignal.RUN) {
                        // Put back everything this batch claimed but did not send.
                        for (int pending = index; pending < batch.size(); pending++) {
                            jobService.release(jobId, batch.get(pending).jobRecipientId());
                        }
                        if (midSignal == JobControlSignal.CANCEL) {
                            stopCancelled(jobId);
                        } else if (midSignal == JobControlSignal.PAUSE) {
                            jobLogService.info(jobId, "Job paused after " + index + " message(s) in this batch");
                        }
                        return;
                    }
                    sendOne(jobId, dispatch, batch.get(index));
                }

                // Rate limit: sleep between batches, never between recipients.
                sleep(delayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jobLogService.warn(jobId, "Job interrupted; it will be picked up again");
        } catch (RuntimeException e) {
            LOG.errorf("Job %d failed", jobId, e);
            jobService.fail(jobId, describe(e));
            jobLogService.error(jobId, "Job failed: " + describe(e));
        }
    }

    /**
     * Sends one message and records the outcome. Never throws: one bad
     * recipient must not stop the campaign.
     */
    private void sendOne(Long jobId, DispatchContext dispatch, JobRecipientWork recipient) {
        try {
            emailDispatchService.dispatch(
                    new OutboundEmail(
                            recipient.email(),
                            recipient.name(),
                            dispatch.subject(),
                            dispatch.htmlContent(),
                            dispatch.textContent(),
                            Map.of("company", recipient.company() == null ? "" : recipient.company(),
                                    "name", recipient.name() == null ? "" : recipient.name(),
                                    "email", recipient.email() == null ? "" : recipient.email())));
            jobService.recordSuccess(jobId, recipient.jobRecipientId());
            jobLogService.info(jobId, "Email sent to " + recipient.email());
        } catch (RuntimeException e) {
            String reason = describe(e);
            try {
                jobService.recordFailure(jobId, recipient.jobRecipientId(), reason);
            } catch (RuntimeException nested) {
                LOG.errorf("Could not record the failure of recipient %s in job %d",
                        recipient.email(), jobId, nested);
            }
            jobLogService.error(jobId, "Failed to send email to " + recipient.email() + ": " + reason);
        }
    }

    // --------------------------------------------------------------- internals

    private void stopCancelled(Long jobId) {
        jobService.cancelOutstanding(jobId);
        jobLogService.warn(jobId, "Job cancelled - remaining recipients were marked CANCELLED");
        LOG.infof("Job %d observed a cancel request and stopped", jobId);
    }

    private JobExecutionContext safeExecutionContext(Long jobId) {
        try {
            return jobService.executionContext(jobId);
        } catch (RuntimeException e) {
            jobService.fail(jobId, "Could not load the job: " + describe(e));
            return null;
        }
    }

    private DispatchContext safeDispatchContext(JobExecutionContext context) {
        try {
            return campaignService.dispatchContext(context.campaignId());
        } catch (RuntimeException e) {
            jobService.fail(context.jobId(), "Could not load the template: " + describe(e));
            jobLogService.error(context.jobId(), "Job failed: could not load the template for campaign '"
                    + context.campaignName() + "'");
            return null;
        }
    }

    private void safeFail(Long jobId, Throwable e) {
        try {
            jobService.fail(jobId, describe(e));
        } catch (RuntimeException nested) {
            LOG.errorf("Could not mark job %d as failed", jobId, nested);
        }
    }

    private void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        String text = message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
        return text.length() <= ERROR_LIMIT ? text : text.substring(0, ERROR_LIMIT);
    }

    /** Test hook: whether a job is currently being processed by this instance. */
    boolean isActive(Long jobId) {
        return active.contains(jobId);
    }
}
