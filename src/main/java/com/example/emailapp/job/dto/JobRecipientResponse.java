package com.example.emailapp.job.dto;

import java.time.Instant;

import com.example.emailapp.job.entity.JobRecipientStatus;

/** One row of the job recipient table: exactly what happened to one address. */
public record JobRecipientResponse(
        Long id,
        Long recipientId,
        String email,
        String name,
        String company,
        JobRecipientStatus status,
        int attemptCount,
        boolean retryable,
        Instant sentAt,
        Instant failedAt,
        String errorMessage
) {
}
