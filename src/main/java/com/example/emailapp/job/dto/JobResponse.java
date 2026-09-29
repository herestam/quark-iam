package com.example.emailapp.job.dto;

import java.time.Instant;
import java.util.List;

import com.example.emailapp.job.entity.JobStatus;

/**
 * A job as seen by the monitoring UI and the REST API.
 *
 * <p>{@code pendingCount} and {@code progressPercent} are derived; the
 * {@code can*} flags let the UI decide which buttons to render without
 * duplicating the state machine in the template.</p>
 */
public record JobResponse(
        Long id,
        Long campaignId,
        String campaignName,
        JobStatus status,
        int totalCount,
        int successCount,
        int failedCount,
        int processedCount,
        int pendingCount,
        int progressPercent,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        String duration,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        boolean canPause,
        boolean canResume,
        boolean canCancel,
        boolean canRetry,
        List<String> availableFilters
) {

    public int pendingRecipients() {
        return Math.max(0, totalCount - processedCount);
    }
}
