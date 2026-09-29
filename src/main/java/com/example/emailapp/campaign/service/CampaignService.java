package com.example.emailapp.campaign.service;

import java.util.List;

import com.example.emailapp.campaign.dto.CampaignRequest;
import com.example.emailapp.campaign.dto.CampaignResponse;
import com.example.emailapp.campaign.dto.DispatchContext;
import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.common.response.PageResponse;

public interface CampaignService {

    PageResponse<CampaignResponse> list(int page, int size);

    CampaignResponse get(Long id);

    CampaignResponse create(CampaignRequest request);

    CampaignResponse update(Long id, CampaignRequest request);

    void delete(Long id);

    /** Replaces the campaign audience without touching its history. */
    CampaignResponse setRecipients(Long id, List<Long> recipientIds);

    CampaignResponse changeStatus(Long id, CampaignStatus status);

    /**
     * Template and subject resolved for sending. Called once per job so the
     * body is read a single time instead of once per recipient.
     */
    DispatchContext dispatchContext(Long campaignId);
}
