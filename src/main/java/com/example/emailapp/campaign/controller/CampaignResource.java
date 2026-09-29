package com.example.emailapp.campaign.controller;

import java.util.List;

import com.example.emailapp.campaign.dto.CampaignRequest;
import com.example.emailapp.campaign.dto.CampaignResponse;
import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.campaign.service.CampaignService;
import com.example.emailapp.common.response.ApiResponse;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.job.dto.CreateJobRequest;
import com.example.emailapp.job.dto.JobResponse;
import com.example.emailapp.job.service.JobService;
import com.example.emailapp.recipient.dto.RecipientResponse;
import com.example.emailapp.recipient.service.RecipientService;
import com.example.emailapp.template.dto.TemplateResponse;
import com.example.emailapp.template.service.TemplateService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Campaign CRUD plus the convenience endpoint that starts a background job. */
@Path("/api/campaigns")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CampaignResource {

    @Inject
    CampaignService campaignService;

    @Inject
    JobService jobService;

    @Inject
    TemplateService templateService;

    @Inject
    RecipientService recipientService;

    @GET
    public ApiResponse<PageResponse<CampaignResponse>> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return ApiResponse.ok(campaignService.list(page, size));
    }

    @GET
    @Path("/{id}")
    public ApiResponse<CampaignResponse> get(@PathParam("id") Long id) {
        return ApiResponse.ok(campaignService.get(id));
    }

    @POST
    public Response create(@Valid CampaignRequest request) {
        CampaignResponse created = campaignService.create(request);
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.ok("Campaign created", created))
                .build();
    }

    @PUT
    @Path("/{id}")
    public ApiResponse<CampaignResponse> update(@PathParam("id") Long id, @Valid CampaignRequest request) {
        return ApiResponse.ok("Campaign updated", campaignService.update(id, request));
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<Void> delete(@PathParam("id") Long id) {
        campaignService.delete(id);
        return ApiResponse.ok("Campaign deleted", null);
    }

    @PUT
    @Path("/{id}/recipients")
    public ApiResponse<CampaignResponse> setRecipients(
            @PathParam("id") Long id, List<Long> recipientIds) {
        return ApiResponse.ok("Audience updated", campaignService.setRecipients(id, recipientIds));
    }

    @PUT
    @Path("/{id}/status")
    public ApiResponse<CampaignResponse> changeStatus(
            @PathParam("id") Long id, CampaignStatus status) {
        return ApiResponse.ok("Status updated", campaignService.changeStatus(id, status));
    }

    /** Queues a background job for this campaign and returns straight away. */
    @POST
    @Path("/{id}/jobs")
    public Response startJob(@PathParam("id") Long id) {
        JobResponse job = jobService.create(new CreateJobRequest(id));
        return Response.status(Response.Status.ACCEPTED)
                .entity(ApiResponse.ok("Job queued for background processing", job))
                .build();
    }

    /** Templates the campaign form needs for its dropdown. */
    @GET
    @Path("/options/templates")
    public ApiResponse<PageResponse<TemplateResponse>> templateOptions() {
        return ApiResponse.ok(templateService.list(null, 0, 200));
    }

    /** Recipients the campaign form needs for its audience picker. */
    @GET
    @Path("/options/recipients")
    public ApiResponse<PageResponse<RecipientResponse>> recipientOptions(
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        return ApiResponse.ok(recipientService.list(search, page, size));
    }
}
