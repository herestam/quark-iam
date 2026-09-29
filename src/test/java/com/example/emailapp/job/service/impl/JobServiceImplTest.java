package com.example.emailapp.job.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.emailapp.campaign.entity.Campaign;
import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.campaign.repository.CampaignRepository;
import com.example.emailapp.config.EmailConfig;
import com.example.emailapp.common.exception.BusinessRuleException;
import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.common.exception.ResourceNotFoundException;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.job.dto.CreateJobRequest;
import com.example.emailapp.job.dto.JobResponse;
import com.example.emailapp.job.entity.EmailJob;
import com.example.emailapp.job.entity.JobStatus;
import com.example.emailapp.job.repository.EmailJobRepository;
import com.example.emailapp.job.repository.JobRecipientRepository;
import com.example.emailapp.job.service.JobLogService;
import com.example.emailapp.recipient.entity.Recipient;
import com.example.emailapp.recipient.repository.RecipientRepository;
import com.example.emailapp.template.repository.EmailTemplateRepository;

import jakarta.enterprise.event.Event;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * The job state machine. Every transition is driven from here, so these tests
 * pin down the rules rather than the wiring: the worker hooks are stubbed out.
 */
class JobServiceImplTest {

    private EmailJobRepository jobRepository;
    private JobRecipientRepository jobRecipientRepository;
    private CampaignRepository campaignRepository;
    private JobLogService jobLogService;
    private JobServiceImpl service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(EmailJobRepository.class);
        jobRecipientRepository = mock(JobRecipientRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        jobLogService = mock(JobLogService.class);
        EmailConfig config = mock(EmailConfig.class);
        when(config.maxRetries()).thenReturn(3);

        TransactionSynchronizationRegistry registry = mock(TransactionSynchronizationRegistry.class);
        doAnswer(call -> null).when(registry).registerInterposedSynchronization(any(Synchronization.class));
        service = new JobServiceImpl();
        service.jobRepository = jobRepository;
        service.jobRecipientRepository = jobRecipientRepository;
        service.campaignRepository = campaignRepository;
        service.recipientRepository = mock(RecipientRepository.class);
        service.templateRepository = mock(EmailTemplateRepository.class);
        service.emailConfig = config;
        service.jobLogService = jobLogService;
        service.jobQueued = mock(Event.class);
        service.transactionRegistry = registry;
    }

    private static Recipient recipient(long id) {
        Recipient r = new Recipient();
        r.id = id;
        r.email = "r" + id + "@example.com";
        return r;
    }

    private static Campaign campaign(int audience) {
        Campaign c = new Campaign();
        c.id = 5L;
        c.name = "Spring sale";
        c.subject = "Hello";
        c.status = CampaignStatus.READY;
        for (int i = 1; i <= audience; i++) {
            c.recipients.add(recipient(i));
        }
        return c;
    }

    private static EmailJob job(JobStatus status) {
        EmailJob job = new EmailJob();
        job.id = 9L;
        job.campaign = campaign(2);
        job.campaign.status = CampaignStatus.RUNNING;
        job.status = status;
        job.totalCount = 2;
        return job;
    }

    private void stubJob(EmailJob job) {
        when(jobRepository.findByIdOptional(9L)).thenReturn(Optional.of(job));
        when(jobRepository.findByIdWithCampaign(9L)).thenReturn(Optional.of(job));
    }

    @Test
    @DisplayName("queues a job and snapshots the audience")
    void createsJobWithSnapshot() {
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(campaign(3)));
        doAnswer(call -> {
            call.<EmailJob>getArgument(0).id = 9L;
            return null;
        }).when(jobRepository).persist(any(EmailJob.class));

        JobResponse created = service.create(new CreateJobRequest(5L));

        assertEquals(JobStatus.PENDING, created.status());
        assertEquals(3, created.totalCount());
        assertEquals(3, created.pendingCount());
        assertEquals(0, created.processedCount());
        assertEquals("Spring sale", created.campaignName());
        assertTrue(created.canPause(), "a pending job can still be paused");
        verify(jobRecipientRepository).persist(anyList());
        // The id is needed before the audience rows can reference the job.
        verify(jobRepository).flush();
    }

    @Test
    @DisplayName("marks the campaign as running as soon as a job is queued")
    void marksCampaignRunning() {
        Campaign c = campaign(1);
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(c));
        doAnswer(call -> {
            call.<EmailJob>getArgument(0).id = 9L;
            return null;
        }).when(jobRepository).persist(any(EmailJob.class));

        service.create(new CreateJobRequest(5L));

        assertEquals(CampaignStatus.RUNNING, c.status);
        verify(jobLogService).info(anyLong(), org.mockito.ArgumentMatchers.contains("queued"));
    }

    @Test
    @DisplayName("refuses to queue a job for a campaign with no audience")
    void refusesEmptyCampaign() {
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(campaign(0)));

        BusinessRuleException thrown = assertThrows(BusinessRuleException.class,
                () -> service.create(new CreateJobRequest(5L)));

        assertTrue(thrown.getMessage().contains("no recipients"), thrown.getMessage());
    }

    @Test
    @DisplayName("reports an unknown campaign instead of creating an orphan job")
    void reportsUnknownCampaign() {
        when(campaignRepository.findByIdWithDetails(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(new CreateJobRequest(5L)));
        assertThrows(InputValidationException.class, () -> service.create(new CreateJobRequest(null)));
    }

    @Test
    @DisplayName("stamps the start time on the first start request")
    void startStampsFirstRun() {
        EmailJob job = job(JobStatus.PENDING);
        stubJob(job);

        service.start(9L);

        assertNotNull(job.startedAt);
        verify(jobLogService).info(9L, "Start requested");
    }

    @Test
    @DisplayName("is idempotent for an already running job")
    void startIsIdempotent() {
        EmailJob job = job(JobStatus.RUNNING);
        job.startedAt = java.time.Instant.now();
        stubJob(job);

        JobResponse response = service.start(9L);

        assertEquals(JobStatus.RUNNING, response.status());
        verify(jobLogService, never()).info(anyLong(), any());
    }

    @Test
    @DisplayName("tells a paused job to be resumed instead of started")
    void startRejectsPaused() {
        stubJob(job(JobStatus.PAUSED));

        assertThrows(BusinessRuleException.class, () -> service.start(9L));
    }

    @Test
    @DisplayName("refuses to restart a finished job")
    void startRejectsTerminal() {
        stubJob(job(JobStatus.COMPLETED));

        assertThrows(BusinessRuleException.class, () -> service.start(9L));
    }

    @Test
    @DisplayName("pausing propagates to the campaign and is idempotent")
    void pausePropagates() {
        EmailJob job = job(JobStatus.RUNNING);
        stubJob(job);

        assertEquals(JobStatus.PAUSED, service.pause(9L).status());
        assertEquals(CampaignStatus.PAUSED, job.campaign.status);

        assertEquals(JobStatus.PAUSED, service.pause(9L).status());
    }

    @Test
    @DisplayName("refuses to pause a job that already finished")
    void pauseRejectsTerminal() {
        stubJob(job(JobStatus.COMPLETED));

        assertThrows(BusinessRuleException.class, () -> service.pause(9L));
    }

    @Test
    @DisplayName("resuming puts the job back in the queue")
    void resumeRequeues() {
        EmailJob job = job(JobStatus.PAUSED);
        stubJob(job);

        JobResponse response = service.resume(9L);

        assertEquals(JobStatus.PENDING, response.status());
        assertEquals(CampaignStatus.RUNNING, job.campaign.status);
    }

    @Test
    @DisplayName("refuses to resume a job that was never paused")
    void resumeRejectsOtherStates() {
        stubJob(job(JobStatus.CANCELLED));

        assertThrows(BusinessRuleException.class, () -> service.resume(9L));
    }

    @Test
    @DisplayName("cancelling drops the recipients that were never sent")
    void cancelDropsPendingRecipients() {
        EmailJob job = job(JobStatus.RUNNING);
        stubJob(job);
        when(jobRecipientRepository.cancelPending(9L)).thenReturn(2);

        JobResponse response = service.cancel(9L);

        assertEquals(JobStatus.CANCELLED, response.status());
        assertEquals(CampaignStatus.CANCELLED, job.campaign.status);
        assertNotNull(job.cancelledAt);
        assertNotNull(job.completedAt);
        verify(jobLogService).warn(anyLong(), org.mockito.ArgumentMatchers.contains("2 recipient"));
    }

    @Test
    @DisplayName("cancelling a cancelled job changes nothing")
    void cancelIsIdempotent() {
        stubJob(job(JobStatus.CANCELLED));

        assertEquals(JobStatus.CANCELLED, service.cancel(9L).status());
        verify(jobRecipientRepository, never()).cancelPending(anyLong());
    }

    @Test
    @DisplayName("retries the failed recipients below the attempt limit")
    void retryRequeuesFailed() {
        EmailJob job = job(JobStatus.FAILED);
        job.failedCount = 2;
        job.processedCount = 2;
        job.completedAt = java.time.Instant.now();
        job.errorMessage = "boom";
        stubJob(job);
        when(jobRecipientRepository.countExhausted(9L, 3)).thenReturn(0L);
        when(jobRecipientRepository.requeueFailed(9L, 3)).thenReturn(2);

        JobResponse response = service.retryFailed(9L);

        assertEquals(JobStatus.PENDING, response.status());
        assertEquals(0, response.failedCount());
        assertEquals(0, response.processedCount());
        assertNull(job.errorMessage);
        assertNull(job.completedAt);
    }

    @Test
    @DisplayName("refuses to retry when every failed recipient is out of attempts")
    void retryRejectsExhausted() {
        stubJob(job(JobStatus.FAILED));
        when(jobRecipientRepository.countExhausted(9L, 3)).thenReturn(4L);
        when(jobRecipientRepository.requeueFailed(9L, 3)).thenReturn(0);

        BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> service.retryFailed(9L));

        assertTrue(thrown.getMessage().contains("attempt"), thrown.getMessage());
    }

    @Test
    @DisplayName("refuses to retry while the job is still running")
    void retryRejectsActive() {
        stubJob(job(JobStatus.RUNNING));

        assertThrows(BusinessRuleException.class, () -> service.retryFailed(9L));
        verify(jobRecipientRepository, never()).requeueFailed(anyLong(), any(Integer.class));
    }

    @Test
    @DisplayName("derives the remaining count and the progress percentage")
    void derivesProgress() {
        EmailJob job = job(JobStatus.RUNNING);
        job.totalCount = 4;
        job.processedCount = 3;
        job.successCount = 2;
        job.failedCount = 1;
        stubJob(job);

        JobResponse response = service.get(9L);

        assertEquals(1, response.pendingCount());
        assertEquals(75, response.progressPercent());
        assertTrue(response.canPause());
        assertTrue(response.canCancel());
        // A running job must not offer a retry, that would race the worker.
        org.junit.jupiter.api.Assertions.assertFalse(response.canRetry());
    }
}
