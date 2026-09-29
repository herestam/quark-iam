package com.example.emailapp.campaign.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create / update payload for a campaign. Recipients are referenced by id so
 * the payload stays small even for a large audience.
 */
public record CampaignRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 500) String subject,
        @NotNull Long templateId,
        List<Long> recipientIds
) {

    public CampaignRequest {
        name = name == null ? null : name.trim();
        subject = subject == null ? null : subject.trim();
        recipientIds = recipientIds == null ? List.of() : List.copyOf(recipientIds);
    }
}
