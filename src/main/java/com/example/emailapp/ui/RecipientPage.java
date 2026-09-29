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
import com.example.emailapp.recipient.dto.BulkImportRequest;
import com.example.emailapp.recipient.dto.BulkImportResult;
import com.example.emailapp.recipient.dto.RecipientRequest;
import com.example.emailapp.recipient.dto.RecipientResponse;
import com.example.emailapp.recipient.service.RecipientService;

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

/** Recipient directory with search, single create/delete and CSV import. */
@Path("/recipients")
public class RecipientPage {

    @Inject
    PageRenderer pages;

    @Inject
    Redirects redirects;

    @Inject
    RecipientService recipientService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String list(@Context UriInfo info) {
        Params params = Params.of(info);
        String search = params.get("search", "");
        int size = params.size(25);
        PageResponse<RecipientResponse> recipients = recipientService.list(search, params.page(), size);
        return pages.render(Views.RECIPIENT_LIST, UiSupport.Section.RECIPIENTS, params, data(
                "recipients", recipients.items(),
                "search", search,
                "page", recipients.page(),
                "size", recipients.size(),
                "total", recipients.total(),
                "totalPages", recipients.totalPages(),
                "hasPrevious", recipients.hasPrevious(),
                "hasNext", recipients.hasNext(),
                "self", info.getRequestUri().getPath()));
    }

    @GET
    @Path("/import")
    @Produces(MediaType.TEXT_HTML)
    public String importPage(@Context UriInfo info) {
        Params params = Params.of(info);
        return pages.render(Views.RECIPIENT_IMPORT, UiSupport.Section.RECIPIENTS, params, data(
                "usage", BulkImportRequest.USAGE_HINT,
                "content", "",
                "hasHeader", true,
                "skipInvalid", true,
                "result", null,
                "self", "/recipients/import"));
    }

    // ------------------------------------------------------------------ actions

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(@FormParam("email") String email, @FormParam("name") String name,
            @FormParam("company") String company) {
        RecipientResponse created = recipientService.create(new RecipientRequest(email, name, company));
        return redirects.ok("/recipients?search=" + Redirects.encode(email), "Added " + created.email() + ".");
    }

    @POST
    @Path("/{id}/delete")
    public Response delete(@PathParam("id") Long id) {
        recipientService.delete(id);
        return redirects.ok("/recipients", "Recipient removed.");
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String importBulk(@FormParam("content") String content,
            @FormParam("hasHeader") boolean hasHeader,
            @FormParam("skipInvalid") boolean skipInvalid,
            @Context UriInfo info) {
        Params params = Params.of(info);
        BulkImportResult result = recipientService.importBulk(
                new BulkImportRequest(content == null ? "" : content, hasHeader, skipInvalid));
        return pages.render(Views.RECIPIENT_IMPORT, UiSupport.Section.RECIPIENTS, params, data(
                "usage", BulkImportRequest.USAGE_HINT,
                "content", content == null ? "" : content,
                "hasHeader", hasHeader,
                "skipInvalid", skipInvalid,
                "result", result,
                "self", "/recipients/import"));
    }

    /** Used by the "add recipient" row so the service can be unit tested directly. */
    static List<RecipientResponse> require(List<RecipientResponse> recipients) {
        if (recipients == null) {
            throw new BusinessRuleException("No recipients supplied");
        }
        return recipients;
    }

    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
