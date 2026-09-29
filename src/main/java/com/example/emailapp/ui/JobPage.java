package com.example.emailapp.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.emailapp.common.exception.BusinessRuleException;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.common.web.PageRenderer;
import com.example.emailapp.common.web.Params;
import com.example.emailapp.common.web.Redirects;
import com.example.emailapp.common.web.UiSupport;
import com.example.emailapp.common.web.Views;
import com.example.emailapp.job.dto.JobLogResponse;
import com.example.emailapp.job.dto.JobRecipientResponse;
import com.example.emailapp.job.dto.JobResponse;
import com.example.emailapp.job.entity.JobRecipientStatus;
import com.example.emailapp.job.service.JobLogService;
import com.example.emailapp.job.service.JobService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Job list and job detail pages.
 *
 * <p>Every lifecycle action is a plain form POST that redirects, so the browser
 * history stays sane and a refresh cannot re-trigger a send. The detail page
 * polls {@code /api/jobs/{id}} for live progress.</p>
 */
@Path("/jobs")
public class JobPage {

    private static final int RECIPIENT_PAGE_SIZE = 25;

    @Inject
    PageRenderer pages;

    @Inject
    Redirects redirects;

    @Inject
    JobService jobService;

    @Inject
    JobLogService jobLogService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String list(@Context UriInfo info) {
        Params params = Params.of(info);
        int size = params.size(20);
        PageResponse<JobResponse> jobs = jobService.list(params.page(), size);
        return pages.render(Views.JOB_LIST, UiSupport.Section.JOBS, params, data(
                "jobs", jobs.items(),
                "page", jobs.page(),
                "size", jobs.size(),
                "total", jobs.total(),
                "totalPages", jobs.totalPages(),
                "hasPrevious", jobs.hasPrevious(),
                "hasNext", jobs.hasNext(),
                "self", info.getRequestUri().getPath()));
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.TEXT_HTML)
    public String detail(@PathParam("id") Long id, @Context UriInfo info) {
        Params params = Params.of(info);
        JobResponse job = jobService.get(id);
        JobRecipientStatus filter = parseStatus(params.get("status", null));

        int size = params.size(RECIPIENT_PAGE_SIZE);
        PageResponse<JobRecipientResponse> recipients = jobService.listRecipients(id, filter, params.page(), size);
        java.util.List<JobLogResponse> logs = jobLogService.list(id, 200);
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String candidate : job.availableFilters()) {
            JobRecipientStatus status = parseStatus(candidate);
            counts.put(status.name(),
                    jobService.listRecipients(id, status, 0, 1).total());
        }

        Map<String, Object> data = data(
                "job", job,
                "recipients", recipients.items(),
                "recipientPage", recipients.page(),
                "recipientTotal", recipients.total(),
                "recipientTotalPages", recipients.totalPages(),
                "activeFilter", filter == null ? "" : filter.name(),
                "logs", logs,
                "counts", counts,
                "self", info.getRequestUri().getPath());
        return pages.render(Views.JOB_DETAIL, UiSupport.Section.JOBS, params, data);
    }

    // ------------------------------------------------------------------ actions

    @POST
    @Path("/{id}/pause")
    public Response pause(@PathParam("id") Long id) {
        jobService.pause(id);
        return redirects.ok("/jobs/" + id, "Job paused. The worker stops after the current message.");
    }

    @POST
    @Path("/{id}/resume")
    public Response resume(@PathParam("id") Long id) {
        jobService.resume(id);
        return redirects.ok("/jobs/" + id, "Job resumed and queued for the worker.");
    }

    @POST
    @Path("/{id}/cancel")
    public Response cancel(@PathParam("id") Long id) {
        jobService.cancel(id);
        return redirects.ok("/jobs/" + id, "Job cancelled. In-flight messages are allowed to finish.");
    }

    @POST
    @Path("/{id}/retry")
    public Response retry(@PathParam("id") Long id) {
        JobResponse job = jobService.retryFailed(id);
        return redirects.ok("/jobs/" + id,
                "Queued " + job.pendingCount() + " failed recipient(s) for another attempt.");
    }

    @POST
    @Path("/start")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response start(@FormParam("campaignId") Long campaignId) {
        if (campaignId == null) {
            throw new BusinessRuleException("Pick a campaign before starting a job");
        }
        JobResponse job = jobService.create(new com.example.emailapp.job.dto.CreateJobRequest(campaignId));
        return redirects.ok("/jobs/" + job.id(), "Job " + job.id() + " queued for background processing.");
    }

    private static JobRecipientStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return JobRecipientStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
