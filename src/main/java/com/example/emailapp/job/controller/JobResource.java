package com.example.emailapp.job.controller;

import java.util.List;

import com.example.emailapp.common.response.ApiResponse;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.job.dto.CreateJobRequest;
import com.example.emailapp.job.dto.DashboardStatsResponse;
import com.example.emailapp.job.dto.JobRecipientResponse;
import com.example.emailapp.job.dto.JobResponse;
import com.example.emailapp.job.entity.JobRecipientStatus;
import com.example.emailapp.job.service.JobLogService;
import com.example.emailapp.job.service.JobService;
import com.example.emailapp.job.dto.JobLogResponse;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Job monitoring and control API.
 *
 * <p>{@code POST /api/jobs} only creates and queues the job, so the call
 * returns in milliseconds no matter how large the campaign is; the scheduler
 * picks the job up and sends in the background.</p>
 */
@Path("/api/jobs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JobResource {

    @Inject
    JobService jobService;

    @Inject
    JobLogService jobLogService;

    @GET
    public ApiResponse<PageResponse<JobResponse>> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return ApiResponse.ok(jobService.list(page, size));
    }

    @GET
    @Path("/{id}")
    public ApiResponse<JobResponse> get(@PathParam("id") Long id) {
        return ApiResponse.ok(jobService.get(id));
    }

    /** Aggregated counters for the dashboard tiles. */
    @GET
    @Path("/stats")
    public ApiResponse<DashboardStatsResponse> stats() {
        return ApiResponse.ok(jobService.dashboardStats());
    }

    @POST
    public Response create(@Valid CreateJobRequest request) {
        JobResponse created = jobService.create(request);
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.ok("Job queued for background processing", created))
                .build();
    }

    @POST
    @Path("/{id}/start")
    public ApiResponse<JobResponse> start(@PathParam("id") Long id) {
        return ApiResponse.ok("Job started", jobService.start(id));
    }

    @POST
    @Path("/{id}/pause")
    public ApiResponse<JobResponse> pause(@PathParam("id") Long id) {
        return ApiResponse.ok("Job paused", jobService.pause(id));
    }

    @POST
    @Path("/{id}/resume")
    public ApiResponse<JobResponse> resume(@PathParam("id") Long id) {
        return ApiResponse.ok("Job resumed", jobService.resume(id));
    }

    @POST
    @Path("/{id}/cancel")
    public ApiResponse<JobResponse> cancel(@PathParam("id") Long id) {
        return ApiResponse.ok("Job cancelled", jobService.cancel(id));
    }

    @POST
    @Path("/{id}/retry-failed")
    public ApiResponse<JobResponse> retryFailed(@PathParam("id") Long id) {
        return ApiResponse.ok("Failed recipients re-queued", jobService.retryFailed(id));
    }

    @GET
    @Path("/{id}/recipients")
    public ApiResponse<PageResponse<JobRecipientResponse>> recipients(
            @PathParam("id") Long id,
            @QueryParam("status") JobRecipientStatus status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        return ApiResponse.ok(jobService.listRecipients(id, status, page, size));
    }

    @GET
    @Path("/{id}/logs")
    public ApiResponse<List<JobLogResponse>> logs(
            @PathParam("id") Long id,
            @QueryParam("limit") @DefaultValue("500") int limit) {
        return ApiResponse.ok(jobLogService.list(id, limit));
    }
}
