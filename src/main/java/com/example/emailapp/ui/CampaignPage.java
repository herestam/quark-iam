package com.example.emailapp.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.emailapp.campaign.dto.CampaignRequest;
import com.example.emailapp.campaign.dto.CampaignResponse;
import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.campaign.service.CampaignService;
import com.example.emailapp.common.exception.BusinessRuleException;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.common.web.PageRenderer;
import com.example.emailapp.common.web.Params;
import com.example.emailapp.common.web.Redirects;
import com.example.emailapp.common.web.UiSupport;
import com.example.emailapp.common.web.Views;
import com.example.emailapp.recipient.dto.RecipientResponse;
import com.example.emailapp.recipient.service.RecipientService;
import com.example.emailapp.template.dto.TemplateResponse;
import com.example.emailapp.job.dto.JobResponse;
import com.example.emailapp.job.service.JobService;
import com.example.emailapp.template.service.TemplateService;

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

/** Campaign list, editor and detail pages. */
@Path("/campaigns")
public class CampaignPage {

    /** How many audience candidates are offered in the picker. */
    private static final int PICKER_LIMIT = 200;

    @Inject
    PageRenderer pages;

    @Inject
    Redirects redirects;

    @Inject
    CampaignService campaignService;

    @Inject
    TemplateService templateService;

    @Inject
    RecipientService recipientService;

    @Inject
    JobService jobService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String list(@Context UriInfo info) {
        Params params = Params.of(info);
        int size = params.size(20);
        PageResponse<CampaignResponse> campaigns = campaignService.list(params.page(), size);
        return pages.render(Views.CAMPAIGN_LIST, UiSupport.Section.CAMPAIGNS, params, data(
                "campaigns", campaigns.items(),
                "page", campaigns.page(),
                "total", campaigns.total(),
                "totalPages", campaigns.totalPages(),
                "hasPrevious", campaigns.hasPrevious(),
                "hasNext", campaigns.hasNext(),
                "self", info.getRequestUri().getPath()));
    }

    @GET
    @Path("/new")
    @Produces(MediaType.TEXT_HTML)
    public String create(@Context UriInfo info) {
        return form(Params.of(info), null);
    }

    @GET
    @Path("/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public String edit(@PathParam("id") Long id, @Context UriInfo info) {
        return form(Params.of(info), campaignService.get(id));
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.TEXT_HTML)
    public String detail(@PathParam("id") Long id, @Context UriInfo info) {
        Params params = Params.of(info);
        CampaignResponse campaign = campaignService.get(id);
        List<RecipientResponse> audience = recipientService.listForCampaign(id, "", 0, 500).items();
        List<JobResponse> jobs = jobService.listByCampaign(id, 0, 10).items();
        return pages.render(Views.CAMPAIGN_DETAIL, UiSupport.Section.CAMPAIGNS, params, data(
                "campaign", campaign,
                "audience", audience,
                "jobs", jobs,
                "hasJobs", !jobs.isEmpty(),
                "statuses", List.of(CampaignStatus.values()),
                "self", info.getRequestUri().getPath()));
    }

    // ------------------------------------------------------------------ actions

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response save(@FormParam("id") Long id, @FormParam("name") String name,
            @FormParam("subject") String subject, @FormParam("templateId") Long templateId,
            @FormParam("recipientIds") String recipientIds) {
        CampaignRequest request = new CampaignRequest(name, subject, templateId, toIds(recipientIds));
        CampaignResponse saved = id == null
                ? campaignService.create(request)
                : campaignService.update(id, request);
        return redirects.ok("/campaigns/" + saved.id(),
                id == null ? "Campaign created." : "Campaign updated.");
    }

    @POST
    @Path("/{id}/delete")
    public Response delete(@PathParam("id") Long id) {
        campaignService.delete(id);
        return redirects.ok("/campaigns", "Campaign deleted.");
    }

    @POST
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response changeStatus(@PathParam("id") Long id, @FormParam("status") String status) {
        CampaignStatus target = parseStatus(status);
        if (target == null) {
            throw new BusinessRuleException("Unknown campaign status: " + status);
        }
        campaignService.changeStatus(id, target);
        return redirects.ok("/campaigns/" + id, "Campaign status set to " + target.name() + ".");
    }

    // ------------------------------------------------------------------ helpers

    private String form(Params params, CampaignResponse campaign) {
        boolean editing = campaign != null;
        List<RecipientResponse> available = recipientService.list("", 0, PICKER_LIMIT).items();
        List<Long> selected = editing
                ? campaign.recipients().stream().map(CampaignResponse.RecipientSummary::id).toList()
                : List.of();
        return pages.render(Views.CAMPAIGN_FORM, UiSupport.Section.CAMPAIGNS, params, data(
                "editing", editing,
                "campaign", campaign,
                "action", editing ? "/campaigns" : "/campaigns",
                "templates", templateService.list("", 0, PICKER_LIMIT).items(),
                "available", available,
                "selected", selected,
                "selectedCsv", join(selected)));
    }

    private static List<Long> toIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException e) {
                        throw new BusinessRuleException("Invalid recipient id: " + value);
                    }
                })
                .toList();
    }

    private static String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static CampaignStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CampaignStatus.valueOf(value.trim().toUpperCase());
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
