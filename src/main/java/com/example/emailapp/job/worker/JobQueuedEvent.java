package com.example.emailapp.job.worker;

/**
 * Fired after the transaction that queued a job has committed.
 *
 * <p>Using a CDI event instead of calling the worker directly keeps
 * {@code JobService} free of any dependency on the execution machinery, so the
 * local worker can later be replaced by a message queue consumer without
 * touching the service layer.</p>
 */
public record JobQueuedEvent(Long jobId) {
}
