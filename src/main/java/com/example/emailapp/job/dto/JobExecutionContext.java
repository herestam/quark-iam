package com.example.emailapp.job.dto;

/**
 * What the worker needs to identify the job it is running.
 * Deliberately a DTO: the worker must not hold a managed entity across
 * thread boundaries.
 */
public record JobExecutionContext(
        Long jobId,
        Long campaignId,
        String campaignName,
        int totalCount
) {
}
