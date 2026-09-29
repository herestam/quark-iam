package com.example.emailapp.job.dto;

/** Body of {@code POST /api/jobs}: which campaign to send. */
public record CreateJobRequest(Long campaignId) {
}
