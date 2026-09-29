package com.example.emailapp.job.entity;

/** Lifecycle of a background send. */
public enum JobStatus {
    /** Queued: created by an HTTP request, waiting for a worker. */
    PENDING,
    /** A worker is actively dispatching emails. */
    RUNNING,
    /** Stopped by the user; resumes from where it stopped. */
    PAUSED,
    /** Every recipient reached a terminal state. */
    COMPLETED,
    /** Stopped by the user on request. */
    CANCELLED,
    /** The worker itself failed, not the individual recipients. */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
