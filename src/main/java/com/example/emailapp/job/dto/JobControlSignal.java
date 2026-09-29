package com.example.emailapp.job.dto;

/**
 * Cooperative control signal read from the database by the worker.
 *
 * <p>This is how pause and cancel work without killing a thread: the worker
 * asks the database what to do before every single message.</p>
 */
public enum JobControlSignal {
    /** Keep going. */
    RUN,
    /** Stop picking up new recipients; the in-flight message finishes. */
    PAUSE,
    /** Stop now and mark everything still pending as cancelled. */
    CANCEL,
    /** The job is gone or already terminal. */
    FINISHED
}
