package com.example.emailapp.job.dto;

import java.time.Instant;

import com.example.emailapp.job.entity.JobLogLevel;

public record JobLogResponse(
        Long id,
        Long jobId,
        JobLogLevel level,
        String message,
        Instant createdAt
) {
}
