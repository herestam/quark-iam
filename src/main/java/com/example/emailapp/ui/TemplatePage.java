package com.example.emailapp.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.emailapp.common.exception.BusinessRuleException;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.common.web.PageRenderer;
import com.example.emailapp.common.web.Params;
import com.example.emailapp.common.web.Redirects;
import com.example.emailapp.common.web.UiSupport;
import com.example.emailapp.common.web.Views;
import com.example.emailapp.template.dto.TemplatePreviewRequest;
import com.example.emailapp.template.dto.TemplatePreviewResponse;
import com.example.emailapp.template.dto.TemplateRequest;
import com.example.emailapp.template.dto.TemplateResponse;
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

/** Template list, editor, preview and delete pages. */
@Path("/templates")
public class TemplatePage {

    /** Placeholder shown by the preview when an author has not supplied a value. */
    private static final String SAMPLE_EMAIL = "jane.doe@example.com";

    @Inject
    PageRenderer pages;

    @Inject
    Redirects redirects;

    @Inject
    TemplateService templateService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String list(@Context UriInfo info) {
        Params params = Params.of(info);
        String search = params.get("search", "");
        int size = params.size(20);
        PageResponse<TemplateResponse> templates = templateService.list(search, 0, size);
        return pages.render(Views.TEMPLATE_LIST, UiSupport.Section.TEMPLATES, params, data(
                "templates", templates.items(),
                "search", search,
                "total", templates.total(),
                "self", info.getRequestUri().getPath()));
    }

    @GET
    @Path("/new")
    @Produces(MediaType.TEXT_HTML)
    public String create(@Context UriInfo info) {
        return editor(Params.of(info), null);
    }

    @GET
    @Path("/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public String edit(@PathParam("id") Long id, @Context UriInfo info) {
        return editor(Params.of(info), templateService.get(id));
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.TEXT_HTML)
    public String preview(@PathParam("id") Long id, @Context UriInfo info) {
        Params params = Params.of(info);
        TemplateResponse template = templateService.get(id);
        String name = params.get("name", template.name());
        String company = params.get("company", "Acme Corp");
        TemplatePreviewResponse preview = templateService.preview(id, new TemplatePreviewRequest(
                template.subject(),
                template.htmlContent(),
                template.textContent(),
                Map.of("email", SAMPLE_EMAIL, "name", name == null ? "Jane Doe" : name,
                        "company", company)));
        return pages.render(Views.TEMPLATE_PREVIEW, UiSupport.Section.TEMPLATES, params, data(
                "template", template,
                "preview", preview,
                "name", name == null ? "Jane Doe" : name,
                "company", company,
                "self", info.getRequestUri().getPath()));
    }

    // ------------------------------------------------------------------ actions

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response save(@FormParam("id") Long id, @FormParam("name") String name,
            @FormParam("subject") String subject, @FormParam("htmlContent") String htmlContent,
            @FormParam("textContent") String textContent) {
        TemplateRequest request = new TemplateRequest(name, subject, htmlContent, textContent);
        TemplateResponse saved = id == null
                ? templateService.create(request)
                : templateService.update(id, request);
        return redirects.ok("/templates/" + saved.id(),
                id == null ? "Template created." : "Template updated.");
    }

    @POST
    @Path("/{id}/delete")
    public Response delete(@PathParam("id") Long id) {
        templateService.delete(id);
        return redirects.ok("/templates", "Template deleted.");
    }

    @POST
    @Path("/{id}/duplicate")
    public Response duplicate(@PathParam("id") Long id) {
        TemplateResponse copy = templateService.duplicate(id);
        return redirects.ok("/templates/" + copy.id() + "/edit", "Template duplicated as \"" + copy.name() + "\".");
    }

    /** Live preview straight from the editor, before the template is saved. */
    @POST
    @Path("/preview-draft")
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String previewDraft(@FormParam("subject") String subject,
            @FormParam("htmlContent") String htmlContent, @FormParam("textContent") String textContent,
            @FormParam("name") String name, @FormParam("company") String company,
            @Context UriInfo info) {
        Params params = Params.of(info);
        if (company == null || company.isBlank()) {
            throw new BusinessRuleException("Company is required to render a preview");
        }
        TemplatePreviewResponse preview = templateService.previewDraft(new TemplatePreviewRequest(
                subject, htmlContent, textContent,
                Map.of("email", SAMPLE_EMAIL, "name", name == null ? "Jane Doe" : name, "company", company)));
        return pages.render(Views.TEMPLATE_PREVIEW, UiSupport.Section.TEMPLATES, params, data(
                "template", null,
                "editing", true,
                "preview", preview,
                "name", name,
                "company", company,
                "self", "/templates"));
    }

    private String editor(Params params, TemplateResponse template) {
        boolean editing = template != null;
        return pages.render(Views.TEMPLATE_FORM, UiSupport.Section.TEMPLATES, params, data(
                "editing", editing,
                "template", template,
                "name", editing ? template.name() : "",
                "subject", editing ? template.subject() : "",
                "htmlContent", editing ? template.htmlContent() : starterHtml(),
                "textContent", editing ? template.textContent() : "",
                "variables", List.of("{{name}}", "{{email}}", "{{company}}"),
                "company", "Acme Corp",
                "self", "/templates"));
    }

    private static String starterHtml() {
        return """
                <h1>Hello {{name}}</h1>
                <p>Thanks for your time, {{name}}.</p>
                <p>Best regards,<br/>The {{company}} team</p>
                """;
    }

    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
