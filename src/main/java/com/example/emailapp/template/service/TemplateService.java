package com.example.emailapp.template.service;

import java.util.List;

import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.template.dto.TemplatePreviewRequest;
import com.example.emailapp.template.dto.TemplatePreviewResponse;
import com.example.emailapp.template.dto.TemplateRequest;
import com.example.emailapp.template.dto.TemplateResponse;

public interface TemplateService {

    PageResponse<TemplateResponse> list(String search, int page, int size);

    TemplateResponse get(Long id);

    TemplateResponse create(TemplateRequest request);

    TemplateResponse update(Long id, TemplateRequest request);

    /** Copies a template (and its content) under a new name. */
    TemplateResponse duplicate(Long id);

    void delete(Long id);

    /** Renders sanitised HTML/text so an administrator can review before sending. */
    TemplatePreviewResponse preview(Long id, TemplatePreviewRequest request);

    /** Preview for content that has not been saved yet. */
    TemplatePreviewResponse previewDraft(TemplatePreviewRequest request);
}
