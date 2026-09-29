package com.example.emailapp.campaign.dto;

import java.time.Instant;
import java.util.List;

import com.example.emailapp.campaign.entity.CampaignStatus;

public record CampaignResponse(
        Long id,
        String name,
        String subject,
        Long templateId,
        String templateName,
        CampaignStatus status,
        int recipientCount,
        List<RecipientSummary> recipients,
        long totalJobs,
        Instant createdAt,
        Instant updatedAt
) {

    public record RecipientSummary(Long id, String email, String name, String company) {
    }
}
