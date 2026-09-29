package com.example.emailapp.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.common.web.PageRenderer;
import com.example.emailapp.common.web.Params;
import com.example.emailapp.common.web.UiSupport;
import com.example.emailapp.common.web.Views;
import com.example.emailapp.job.dto.DashboardStatsResponse;
import com.example.emailapp.job.dto.JobResponse;
import com.example.emailapp.job.service.JobService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

/**
 * Landing page: portfolio level counters plus a live view of the most recent
 * jobs.
 *
 * <p>The job rows are refreshed by {@code /static/js/jobs.js}, which polls the
 * JSON API. The page itself therefore renders instantly and never holds a long
 * lived request open, which is what makes it usable behind a proxy.</p>
 */
@Path("/")
public class DashboardPage {

    @Inject
    PageRenderer pages;

    @Inject
    JobService jobService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index(@Context UriInfo info) {
        Params params = Params.of(info);
        int size = params.size(8);
        DashboardStatsResponse stats = jobService.dashboardStats();
        PageResponse<JobResponse> recent = jobService.list(0, size);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats);
        data.put("jobs", recent.items());
        data.put("jobsTotal", recent.total());
        data.put("hasJobs", !recent.items().isEmpty());
        data.put("self", info.getRequestUri().getPath());
        data.put("view", Views.DASHBOARD);
        return pages.render(Views.DASHBOARD, UiSupport.Section.DASHBOARD, params, data);
    }
}
