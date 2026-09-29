package com.example.emailapp.common.web;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Single place that maps a logical view name onto a Qute template file.
 *
 * <p>Templates live in {@code src/main/resources/templates/} and Quarkus
 * registers them under their path, so they are resolved through the engine at
 * request time. Centralising the names here keeps a typo in a controller from
 * turning into a blank page.</p>
 */
@ApplicationScoped
public class Views {

    public static final String DASHBOARD = "dashboard.html";
    public static final String CAMPAIGN_LIST = "campaigns/list.html";
    public static final String CAMPAIGN_FORM = "campaigns/form.html";
    public static final String CAMPAIGN_DETAIL = "campaigns/detail.html";
    public static final String TEMPLATE_LIST = "templates/list.html";
    public static final String TEMPLATE_FORM = "templates/form.html";
    public static final String TEMPLATE_PREVIEW = "templates/preview.html";
    public static final String RECIPIENT_LIST = "recipients/list.html";
    public static final String RECIPIENT_IMPORT = "recipients/import.html";
    public static final String JOB_LIST = "jobs/list.html";
    public static final String JOB_DETAIL = "jobs/detail.html";
    public static final String EMAIL_SETTINGS = "settings/email.html";
    public static final String PARTIAL_SIDEBAR = "layout/sidebar.html";
    public static final String PARTIAL_NAV = "layout/nav.html";
    public static final String ERROR = "error.html";

    @Inject
    Engine engine;

    public TemplateInstance render(String view) {
        Template template = engine.getTemplate(view);
        if (template == null) {
            // A wrong view name must say so instead of surfacing as an NPE.
            throw new NotFoundException("Template not found: " + view);
        }
        return template.instance();
    }
}
