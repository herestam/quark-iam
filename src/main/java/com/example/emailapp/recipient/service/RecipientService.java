package com.example.emailapp.recipient.service;

import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.recipient.dto.BulkImportRequest;
import com.example.emailapp.recipient.dto.BulkImportResult;
import com.example.emailapp.recipient.dto.RecipientRequest;
import com.example.emailapp.recipient.dto.RecipientResponse;

public interface RecipientService {

    PageResponse<RecipientResponse> list(String search, int page, int size);

    /** Audience of one campaign, used by the campaign recipient picker. */
    PageResponse<RecipientResponse> listForCampaign(Long campaignId, String search, int page, int size);

    RecipientResponse get(Long id);

    /** Fails with a validation error when the address already exists. */
    RecipientResponse create(RecipientRequest request);

    RecipientResponse update(Long id, RecipientRequest request);

    void delete(Long id);

    /** Parses and stores many addresses, reporting duplicates and invalid lines. */
    BulkImportResult importBulk(BulkImportRequest request);
}
