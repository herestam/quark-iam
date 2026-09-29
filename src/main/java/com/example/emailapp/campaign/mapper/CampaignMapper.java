package com.example.emailapp.campaign.mapper;

import java.util.List;

import com.example.emailapp.campaign.dto.CampaignRequest;
import com.example.emailapp.campaign.dto.CampaignResponse;
import com.example.emailapp.campaign.entity.Campaign;
import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.recipient.entity.Recipient;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CampaignMapper {

    public CampaignResponse toResponse(Campaign entity, long totalJobs) {
        if (entity == null) {
            return null;
        }
        List<CampaignResponse.RecipientSummary> recipients = entity.recipients.stream()
                .map(r -> new CampaignResponse.RecipientSummary(r.id, r.email, r.name, r.company))
                .sorted((a, b) -> a.email().compareToIgnoreCase(b.email()))
                .toList();
        return new CampaignResponse(
                entity.id,
                entity.name,
                entity.subject,
                entity.template == null ? null : entity.template.id,
                entity.template == null ? null : entity.template.name,
                entity.status == null ? CampaignStatus.DRAFT : entity.status,
                recipients.size(),
                recipients,
                totalJobs,
                entity.createdAt,
                entity.updatedAt);
    }

    public void apply(CampaignRequest request, Campaign entity, List<Recipient> recipients) {
        entity.name = request.name();
        entity.subject = request.subject();
        entity.recipients.clear();
        if (recipients != null) {
            entity.recipients.addAll(recipients);
        }
    }
}
