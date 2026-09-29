package com.example.emailapp.job.dto;

import java.util.List;

/** Counters for the dashboard tiles. */
public record DashboardStatsResponse(
        long totalCampaigns,
        long totalTemplates,
        long totalRecipients,
        long totalJobs,
        long pendingJobs,
        long runningJobs,
        long pausedJobs,
        long completedJobs,
        long failedJobs,
        long cancelledJobs,
        long sentEmails,
        long failedEmails,
        long pendingEmails,
        List<JobResponse> recentJobs
) {
}
