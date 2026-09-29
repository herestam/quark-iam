package com.example.emailapp.job.dto;

/**
 * One claimed recipient handed from the service layer to the worker.
 * A plain immutable value, so it survives being processed off the request thread.
 */
public record JobRecipientWork(
        Long jobRecipientId,
        Long recipientId,
        String email,
        String name,
        String company,
        int attemptCount
) {
}
