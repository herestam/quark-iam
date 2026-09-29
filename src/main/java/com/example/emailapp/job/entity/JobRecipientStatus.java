package com.example.emailapp.job.entity;

/** Per recipient delivery state inside a job. */
public enum JobRecipientStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SENT || this == FAILED || this == CANCELLED;
    }
}
