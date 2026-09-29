package com.example.emailapp.template.service.impl;

import java.util.List;

import org.jboss.logging.Logger;

import com.example.emailapp.campaign.repository.CampaignRepository;
import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.common.exception.ResourceNotFoundException;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.common.validation.HtmlSanitizer;
import com.example.emailapp.template.dto.TemplatePreviewRequest;
import com.example.emailapp.template.dto.TemplatePreviewResponse;
import com.example.emailapp.template.dto.TemplateRequest;
import com.example.emailapp.template.dto.TemplateResponse;
import com.example.emailapp.template.entity.EmailTemplate;
import com.example.emailapp.template.mapper.TemplateMapper;
import com.example.emailapp.template.repository.EmailTemplateRepository;
import com.example.emailapp.template.service.TemplateRenderService;
import com.example.emailapp.template.service.TemplateService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Template CRUD plus preview.
 *
 * <p>Content is sanitised on write as well as on read: a stored template is
 * always safe to render in the preview iframe.</p>
 */
@ApplicationScoped
public class TemplateServiceImpl implements TemplateService {

    private static final Logger LOG = Logger.getLogger(TemplateServiceImpl.class);
    private static final int NAME_LIMIT = 200;

    @Inject
    EmailTemplateRepository repository;

    @Inject
    CampaignRepository campaignRepository;

    @Inject
    TemplateMapper mapper;

    @Inject
    TemplateRenderService renderService;

    @Inject
    HtmlSanitizer sanitizer;

    @Inject
    Validator validator;

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public PageResponse<TemplateResponse> list(String search, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        List<TemplateResponse> items = repository.search(search, safePage, safeSize).stream()
                .map(entity -> mapper.toResponse(entity, usageCount(entity.id)))
                .toList();
        return PageResponse.of(items, safePage, safeSize, repository.countMatching(search));
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public TemplateResponse get(Long id) {
        return mapper.toResponse(require(id), usageCount(id));
    }

    @Override
    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        validate(request);
        EmailTemplate entity = new EmailTemplate();
        mapper.apply(request, entity);
        sanitize(entity);
        repository.persist(entity);
        repository.flush();
        LOG.infof("Template %d created (%s)", entity.id, entity.name);
        return mapper.toResponse(entity, 0L);
    }

    @Override
    @Transactional
    public TemplateResponse update(Long id, TemplateRequest request) {
        validate(request);
        EmailTemplate entity = require(id);
        mapper.apply(request, entity);
        sanitize(entity);
        repository.flush();
        LOG.infof("Template %d updated", entity.id);
        return mapper.toResponse(entity, usageCount(entity.id));
    }

    @Override
    @Transactional
    public TemplateResponse duplicate(Long id) {
        EmailTemplate source = require(id);
        EmailTemplate copy = new EmailTemplate();
        copy.name = nextCopyName(source.name);
        copy.subject = source.subject;
        copy.htmlContent = source.htmlContent;
        copy.textContent = source.textContent;
        repository.persist(copy);
        repository.flush();
        LOG.infof("Template %d duplicated into %d", source.id, copy.id);
        return mapper.toResponse(copy, 0L);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        EmailTemplate entity = require(id);
        long usage = usageCount(id);
        if (usage > 0) {
            throw new InputValidationException(
                    "Template is used by " + usage + " campaign(s) and cannot be deleted. Duplicate it instead.",
                    java.util.List.of("template: referenced by " + usage + " campaign(s)"));
        }
        repository.delete(entity);
        LOG.infof("Template %d deleted", id);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public TemplatePreviewResponse preview(Long id, TemplatePreviewRequest request) {
        EmailTemplate entity = require(id);
        String subject = request != null && notBlank(request.subject()) ? request.subject() : entity.subject;
        String html = request != null && notBlank(request.htmlContent()) ? request.htmlContent() : entity.htmlContent;
        String text = request != null && notBlank(request.textContent()) ? request.textContent() : entity.textContent;
        return render(subject, html, text, request == null ? null : request.variables());
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public TemplatePreviewResponse previewDraft(TemplatePreviewRequest request) {
        if (request == null) {
            return new TemplatePreviewResponse("(no subject)", "", "", List.of());
        }
        return render(request.subject(), request.htmlContent(), request.textContent(), request.variables());
    }

    // --------------------------------------------------------------- internals

    private TemplatePreviewResponse render(String subject, String html, String text, Map<String, String> overrides) {
        Map<String, String> variables = new LinkedHashMap<>(renderService.previewVariables());
        if (overrides != null) {
            variables.putAll(overrides);
        }
        String renderedHtml = sanitizer.sanitize(renderService.renderHtml(html, variables));
        String renderedText = renderService.renderText(text, variables);
        return new TemplatePreviewResponse(
                subject == null ? "" : subject,
                renderedHtml,
                renderedText,
                List.copyOf(renderService.extractVariables(
                        (html == null ? "" : html) + " " + (text == null ? "" : text))));
    }

    private EmailTemplate require(Long id) {
        if (id == null) {
            throw new InputValidationException("Template id is required");
        }
        return repository.findByIdOptional(id).orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", id));
    }

    private long usageCount(Long templateId) {
        if (templateId == null) {
            return 0L;
        }
        return campaignRepository.countByTemplate(templateId);
    }

    private void validate(TemplateRequest request) {
        if (request == null) {
            throw new InputValidationException("Request body is required");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new InputValidationException("Template is not valid",
                    violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().toList());
        }
    }

    private void sanitize(EmailTemplate entity) {
        entity.htmlContent = sanitizer.sanitize(entity.htmlContent);
    }

    private String nextCopyName(String base) {
        String stem = base.length() > NAME_LIMIT - 6 ? base.substring(0, NAME_LIMIT - 6) : base;
        String candidate = stem + " (copy)";
        int counter = 2;
        while (repository.existsByName(candidate, null)) {
            String suffix = " (copy " + counter + ")";
            candidate = stem.substring(0, Math.min(stem.length(), NAME_LIMIT - suffix.length())) + suffix;
            counter++;
        }
        return candidate;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
