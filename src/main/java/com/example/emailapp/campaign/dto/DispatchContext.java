package com.example.emailapp.campaign.dto;

/**
 * Everything the worker needs to send a campaign, resolved once per job so the
 * template is not re-read for every single recipient.
 */
public record DispatchContext(
        Long campaignId,
        String campaignName,
        String subject,
        String htmlContent,
        String textContent
) {
}
