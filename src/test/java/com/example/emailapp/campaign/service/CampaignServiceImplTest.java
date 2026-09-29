package com.example.emailapp.campaign.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.emailapp.campaign.dto.CampaignRequest;
import com.example.emailapp.campaign.dto.CampaignResponse;
import com.example.emailapp.campaign.entity.Campaign;
import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.campaign.mapper.CampaignMapper;
import com.example.emailapp.campaign.repository.CampaignRepository;
import com.example.emailapp.common.exception.BusinessRuleException;
import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.common.exception.ResourceNotFoundException;
import com.example.emailapp.job.repository.EmailJobRepository;
import com.example.emailapp.recipient.entity.Recipient;
import com.example.emailapp.recipient.repository.RecipientRepository;
import com.example.emailapp.template.entity.EmailTemplate;
import com.example.emailapp.template.repository.EmailTemplateRepository;

import jakarta.validation.Validation;

/**
 * Campaign rules that are easy to get wrong: name uniqueness, the audience
 * lock while sending, and the DRAFT to READY transition.
 */
class CampaignServiceImplTest {

    private CampaignRepository campaignRepository;
    private EmailTemplateRepository templateRepository;
    private RecipientRepository recipientRepository;
    private EmailJobRepository jobRepository;
    private CampaignServiceImpl service;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        templateRepository = mock(EmailTemplateRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        jobRepository = mock(EmailJobRepository.class);
        service = new CampaignServiceImpl();
        service.campaignRepository = campaignRepository;
        service.templateRepository = templateRepository;
        service.recipientRepository = recipientRepository;
        service.jobRepository = jobRepository;
        service.mapper = new CampaignMapper();
        service.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static EmailTemplate template() {
        EmailTemplate t = new EmailTemplate();
        t.id = 1L;
        t.name = "Welcome";
        t.htmlContent = "<p>Hi {{name}}</p>";
        t.textContent = "Hi {{name}}";
        return t;
    }

    private static Recipient recipient(long id) {
        Recipient r = new Recipient();
        r.id = id;
        r.email = "r" + id + "@example.com";
        r.name = "R" + id;
        return r;
    }

    private static Campaign stored(CampaignStatus status) {
        Campaign c = new Campaign();
        c.id = 5L;
        c.name = "Spring sale";
        c.subject = "Hello";
        c.template = template();
        c.status = status;
        return c;
    }

    @Test
    @DisplayName("creates a READY campaign when an audience is selected")
    void createsReadyCampaign() {
        when(templateRepository.findByIdOptional(1L)).thenReturn(Optional.of(template()));
        when(recipientRepository.findAllByIds(List.of(1L, 2L)))
                .thenReturn(List.of(recipient(1L), recipient(2L)));
        doAnswer(call -> {
            call.<Campaign>getArgument(0).id = 5L;
            return null;
        }).when(campaignRepository).persist(any(Campaign.class));
        when(campaignRepository.countByCampaign(5L)).thenReturn(0L);

        CampaignResponse created = service.create(new CampaignRequest("Spring sale", "Hello", 1L, List.of(1L, 2L)));

        assertEquals(CampaignStatus.READY, created.status());
        assertEquals(2, created.recipientCount());
        assertEquals(0L, created.totalJobs());
    }

    @Test
    @DisplayName("keeps a campaign with no audience in DRAFT")
    void createsDraftWithoutRecipients() {
        when(templateRepository.findByIdOptional(1L)).thenReturn(Optional.of(template()));
        doAnswer(call -> {
            call.<Campaign>getArgument(0).id = 5L;
            return null;
        }).when(campaignRepository).persist(any(Campaign.class));
        when(campaignRepository.countByCampaign(5L)).thenReturn(0L);

        CampaignResponse created = service.create(new CampaignRequest("Spring sale", "Hello", 1L, List.of()));

        assertEquals(CampaignStatus.DRAFT, created.status());
        assertEquals(0, created.recipientCount());
    }

    @Test
    @DisplayName("refuses a duplicate campaign name")
    void refusesDuplicateName() {
        when(campaignRepository.isNameTaken("Spring sale", null)).thenReturn(true);

        InputValidationException thrown = assertThrows(InputValidationException.class,
                () -> service.create(new CampaignRequest("Spring sale", "Hello", 1L, List.of())));

        assertTrue(thrown.getFieldErrors().stream().anyMatch(e -> e.startsWith("name")), thrown.getFieldErrors().toString());
    }

    @Test
    @DisplayName("rejects a campaign without a template")
    void requiresTemplate() {
        assertThrows(InputValidationException.class,
                () -> service.create(new CampaignRequest("Spring sale", "Hello", null, List.of())));
    }

    @Test
    @DisplayName("fails the create when a selected recipient disappeared")
    void rejectsMissingRecipients() {
        when(templateRepository.findByIdOptional(1L)).thenReturn(Optional.of(template()));
        when(recipientRepository.findAllByIds(List.of(1L, 2L))).thenReturn(List.of(recipient(1L)));

        InputValidationException thrown = assertThrows(InputValidationException.class,
                () -> service.create(new CampaignRequest("Spring sale", "Hello", 1L, List.of(1L, 2L))));

        assertTrue(thrown.getMessage().contains("2"), thrown.getMessage());
    }

    @Test
    @DisplayName("promotes a DRAFT to READY once recipients are added")
    void promotesDraftToReady() {
        Campaign campaign = stored(CampaignStatus.DRAFT);
        campaign.recipients.addAll(new ArrayList<>(List.of()));
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(campaign));
        when(recipientRepository.findAllByIds(List.of(1L))).thenReturn(List.of(recipient(1L)));
        when(campaignRepository.countByCampaign(5L)).thenReturn(0L);

        CampaignResponse updated = service.setRecipients(5L, List.of(1L));

        assertEquals(CampaignStatus.READY, updated.status());
        assertEquals(1, updated.recipientCount());
    }

    @Test
    @DisplayName("locks the audience while the campaign is sending")
    void locksAudienceWhileRunning() {
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(stored(CampaignStatus.RUNNING)));

        BusinessRuleException thrown = assertThrows(BusinessRuleException.class,
                () -> service.setRecipients(5L, List.of(1L)));

        assertTrue(thrown.getMessage().contains("locked"), thrown.getMessage());
    }

    @Test
    @DisplayName("only allows a running campaign to be paused or cancelled")
    void restrictsRunningTransitions() {
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(stored(CampaignStatus.RUNNING)));
        when(campaignRepository.countByCampaign(5L)).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> service.changeStatus(5L, CampaignStatus.DRAFT));
        assertThrows(BusinessRuleException.class, () -> service.changeStatus(5L, CampaignStatus.READY));

        assertEquals(CampaignStatus.PAUSED, service.changeStatus(5L, CampaignStatus.PAUSED).status());
        assertEquals(CampaignStatus.CANCELLED, service.changeStatus(5L, CampaignStatus.CANCELLED).status());
    }

    @Test
    @DisplayName("refuses to edit a campaign that is currently sending")
    void refusesUpdateWhileRunning() {
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(stored(CampaignStatus.RUNNING)));

        assertThrows(BusinessRuleException.class,
                () -> service.update(5L, new CampaignRequest("Spring sale", "New subject", 1L, List.of())));
    }

    @Test
    @DisplayName("refuses to delete a campaign that is currently sending")
    void refusesDeleteWhileRunning() {
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(stored(CampaignStatus.RUNNING)));

        assertThrows(BusinessRuleException.class, () -> service.delete(5L));
    }

    @Test
    @DisplayName("reports a 404 style error for an unknown campaign")
    void reportsUnknownCampaign() {
        when(campaignRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(99L));
        assertThrows(InputValidationException.class, () -> service.get(null));
    }

    @Test
    @DisplayName("exposes the template content the worker needs")
    void exposesDispatchContext() {
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(stored(CampaignStatus.READY)));

        var context = service.dispatchContext(5L);

        assertEquals(5L, context.campaignId());
        assertEquals("Spring sale", context.campaignName());
        assertEquals("Hello", context.subject());
        assertEquals("<p>Hi {{name}}</p>", context.htmlContent());
        assertEquals("Hi {{name}}", context.textContent());
    }

    @Test
    @DisplayName("deletes a finished campaign")
    void deletesFinishedCampaign() {
        Campaign campaign = stored(CampaignStatus.COMPLETED);
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(campaign));

        service.delete(5L);

        verify(campaignRepository).delete(campaign);
    }
}
