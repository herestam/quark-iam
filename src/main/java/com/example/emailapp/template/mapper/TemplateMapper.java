package com.example.emailapp.template.mapper;

import java.util.List;

import com.example.emailapp.template.dto.TemplateRequest;
import com.example.emailapp.template.dto.TemplateResponse;
import com.example.emailapp.template.entity.EmailTemplate;
import com.example.emailapp.template.service.TemplateRenderService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Entity to DTO conversion for templates. */
@ApplicationScoped
public class TemplateMapper {

    @Inject
    TemplateRenderService renderService;

    /** @param usageCount number of campaigns referencing this template. */
    public TemplateResponse toResponse(EmailTemplate entity, long usageCount) {
        if (entity == null) {
            return null;
        }
        return new TemplateResponse(
                entity.id,
                entity.name,
                entity.subject,
                entity.htmlContent,
                entity.textContent,
                entity.createdAt,
                entity.updatedAt,
                List.copyOf(renderService.extractVariables(
                        (entity.htmlContent == null ? "" : entity.htmlContent) + " "
                                + (entity.textContent == null ? "" : entity.textContent))),
                (int) usageCount);
    }

    public List<TemplateResponse> toResponses(List<EmailTemplate> entities, java.util.function.Function<Long, Long> usageLookup) {
        return entities.stream()
                .map(entity -> toResponse(entity, usageLookup == null ? 0L : usageLookup.apply(entity.id)))
                .toList();
    }

    /** Copies validated input onto an existing entity. */
    public void apply(TemplateRequest request, EmailTemplate entity) {
        entity.name = request.name();
        entity.subject = request.subject();
        entity.htmlContent = request.htmlContent();
        entity.textContent = request.textContent() == null || request.textContent().isBlank()
                ? null
                : request.textContent();
    }
}
