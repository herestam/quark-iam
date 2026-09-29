package com.example.emailapp.campaign.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import com.example.emailapp.campaign.dto.CampaignRequest;
import com.example.emailapp.campaign.dto.CampaignResponse;
import com.example.emailapp.campaign.dto.DispatchContext;
import com.example.emailapp.campaign.entity.Campaign;
import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.campaign.mapper.CampaignMapper;
import com.example.emailapp.campaign.repository.CampaignRepository;
import com.example.emailapp.common.exception.BusinessRuleException;
import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.common.exception.ResourceNotFoundException;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.job.repository.EmailJobRepository;
import com.example.emailapp.recipient.entity.Recipient;
import com.example.emailapp.recipient.repository.RecipientRepository;
import com.example.emailapp.template.entity.EmailTemplate;
import com.example.emailapp.template.repository.EmailTemplateRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;

/** Campaign CRUD, audience management and the per-job dispatch context. */
@ApplicationScoped
public class CampaignServiceImpl implements CampaignService {

    private static final Logger LOG = Logger.getLogger(CampaignServiceImpl.class);
    private static final int MAX_RECIPIENTS_PER_CAMPAIGN = 100_000;

    @Inject
    CampaignRepository campaignRepository;

    @Inject
    EmailTemplateRepository templateRepository;

    @Inject
    RecipientRepository recipientRepository;

    @Inject
    EmailJobRepository jobRepository;

    @Inject
    CampaignMapper mapper;

    @Inject
    Validator validator;

    @Override
    @Transactional
    public PageResponse<CampaignResponse> list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        List<CampaignResponse> items = new ArrayList<>();
        for (Campaign campaign : campaignRepository.listWithDetails(safePage, safeSize)) {
            campaign.recipients.size(); // initialise inside the transaction
            items.add(mapper.toResponse(campaign, jobCount(campaign.id)));
        }
        return PageResponse.of(items, safePage, safeSize, campaignRepository.countAll());
    }

    @Override
    @Transactional
    public CampaignResponse get(Long id) {
        Campaign campaign = requireWithDetails(id);
        return mapper.toResponse(campaign, jobCount(campaign.id));
    }

    @Override
    @Transactional
    public CampaignResponse create(CampaignRequest request) {
        validate(request);
        if (campaignRepository.isNameTaken(request.name(), null)) {
            throw new InputValidationException("A campaign named '" + request.name() + "' already exists",
                    List.of("name: already used"));
        }
        EmailTemplate template = requireTemplate(request.templateId());
        List<Recipient> recipients = resolveRecipients(request.recipientIds());

        Campaign campaign = new Campaign();
        campaign.name = request.name();
        campaign.subject = request.subject();
        campaign.template = template;
        campaign.status = recipients.isEmpty() ? CampaignStatus.DRAFT : CampaignStatus.READY;
        campaign.recipients.addAll(recipients);

        campaignRepository.persist(campaign);
        campaignRepository.flush();
        LOG.infof("Campaign %d created with %d recipient(s)", campaign.id, recipients.size());
        return mapper.toResponse(campaign, 0L);
    }

    @Override
    @Transactional
    public CampaignResponse update(Long id, CampaignRequest request) {
        validate(request);
        Campaign campaign = requireWithDetails(id);
        if (campaign.status == CampaignStatus.RUNNING) {
            throw new BusinessRuleException("Campaign is currently sending. Pause or wait for the job to finish.");
        }
        if (campaignRepository.isNameTaken(request.name(), id)) {
            throw new InputValidationException("A campaign named '" + request.name() + "' already exists",
                    List.of("name: already used"));
        }
        campaign.name = request.name();
        campaign.subject = request.subject();
        campaign.template = requireTemplate(request.templateId());
        campaign.recipients.clear();
        campaign.recipients.addAll(resolveRecipients(request.recipientIds()));
        if (campaign.status == CampaignStatus.DRAFT && !campaign.recipients.isEmpty()) {
            campaign.status = CampaignStatus.READY;
        }
        campaignRepository.flush();
        return mapper.toResponse(campaign, jobCount(campaign.id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Campaign campaign = requireWithDetails(id);
        if (campaign.status == CampaignStatus.RUNNING) {
            throw new BusinessRuleException("Cancel the running job before deleting this campaign");
        }
        campaignRepository.delete(campaign);
        LOG.infof("Campaign %d deleted", id);
    }

    @Override
    @Transactional
    public CampaignResponse setRecipients(Long id, List<Long> recipientIds) {
        Campaign campaign = requireWithDetails(id);
        if (campaign.status == CampaignStatus.RUNNING) {
            throw new BusinessRuleException("Campaign is currently sending and its audience is locked");
        }
        campaign.recipients.clear();
        campaign.recipients.addAll(resolveRecipients(recipientIds));
        if (campaign.status == CampaignStatus.DRAFT && !campaign.recipients.isEmpty()) {
            campaign.status = CampaignStatus.READY;
        }
        campaignRepository.flush();
        return mapper.toResponse(campaign, jobCount(campaign.id));
    }

    @Override
    @Transactional
    public CampaignResponse changeStatus(Long id, CampaignStatus status) {
        if (status == null) {
            throw new InputValidationException("Target status is required");
        }
        Campaign campaign = requireWithDetails(id);
        if (campaign.status == CampaignStatus.RUNNING && status != CampaignStatus.RUNNING
                && status != CampaignStatus.CANCELLED && status != CampaignStatus.PAUSED) {
            throw new BusinessRuleException("A running campaign can only be paused or cancelled");
        }
        campaign.status = status;
        return mapper.toResponse(campaign, jobCount(campaign.id));
    }

    @Override
    @Transactional
    public DispatchContext dispatchContext(Long campaignId) {
        Campaign campaign = campaignRepository.findByIdWithDetails(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        EmailTemplate template = campaign.template;
        if (template == null) {
            throw new BusinessRuleException("Campaign '" + campaign.name + "' has no template attached");
        }
        return new DispatchContext(
                campaign.id,
                campaign.name,
                campaign.subject,
                template.htmlContent,
                template.textContent);
    }

    // --------------------------------------------------------------- internals

    private Campaign require(Long id) {
        if (id == null) {
            throw new InputValidationException("Campaign id is required");
        }
        return campaignRepository.findByIdOptional(id).orElseThrow(() -> new ResourceNotFoundException("Campaign", id));
    }

    private Campaign requireWithDetails(Long id) {
        if (id == null) {
            throw new InputValidationException("Campaign id is required");
        }
        return campaignRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", id));
    }

    private EmailTemplate requireTemplate(Long templateId) {
        if (templateId == null) {
            throw new InputValidationException("A template must be selected", List.of("templateId: required"));
        }
        return templateRepository.findByIdOptional(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", templateId));
    }

    private List<Recipient> resolveRecipients(List<Long> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return List.of();
        }
        if (recipientIds.size() > MAX_RECIPIENTS_PER_CAMPAIGN) {
            throw new InputValidationException("A campaign can target at most "
                    + MAX_RECIPIENTS_PER_CAMPAIGN + " recipients",
                    List.of("recipientIds: " + recipientIds.size() + " selected"));
        }
        Set<Long> distinct = new LinkedHashSet<>(recipientIds);
        List<Recipient> found = recipientRepository.findAllByIds(List.copyOf(distinct));
        if (found.size() != distinct.size()) {
            Set<Long> foundIds = new LinkedHashSet<>(found.stream().map(r -> r.id).toList());
            List<String> missing = distinct.stream().filter(id -> !foundIds.contains(id)).map(String::valueOf).toList();
            throw new InputValidationException("Some recipients no longer exist: " + String.join(", ", missing),
                    missing.stream().map(id -> "recipientIds: " + id + " not found").toList());
        }
        return found;
    }

    private long jobCount(Long campaignId) {
        return campaignRepository.countByCampaign(campaignId);
    }

    private void validate(CampaignRequest request) {
        if (request == null) {
            throw new InputValidationException("Request body is required");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new InputValidationException("Campaign is not valid",
                    violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().toList());
        }
    }
}
