package com.example.emailapp.template.controller;

import java.util.List;

import com.example.emailapp.common.response.ApiResponse;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.template.dto.TemplatePreviewRequest;
import com.example.emailapp.template.dto.TemplatePreviewResponse;
import com.example.emailapp.template.dto.TemplateRequest;
import com.example.emailapp.template.dto.TemplateResponse;
import com.example.emailapp.template.service.TemplateRenderService;
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

/** Email template CRUD, duplication and preview. */
@Path("/api/templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateResource {

    @Inject
    TemplateService templateService;

    @Inject
    TemplateRenderService renderService;

    @GET
    public ApiResponse<PageResponse<TemplateResponse>> list(
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return ApiResponse.ok(templateService.list(search, page, size));
    }

    @GET
    @Path("/variables")
    public ApiResponse<List<String>> variables() {
        return ApiResponse.ok(renderService.supportedVariables());
    }

    @GET
    @Path("/{id}")
    public ApiResponse<TemplateResponse> get(@PathParam("id") Long id) {
        return ApiResponse.ok(templateService.get(id));
    }

    @POST
    public Response create(@Valid TemplateRequest request) {
        TemplateResponse created = templateService.create(request);
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.ok("Template created", created))
                .build();
    }

    @PUT
    @Path("/{id}")
    public ApiResponse<TemplateResponse> update(@PathParam("id") Long id, @Valid TemplateRequest request) {
        return ApiResponse.ok("Template updated", templateService.update(id, request));
    }

    @POST
    @Path("/{id}/duplicate")
    public ApiResponse<TemplateResponse> duplicate(@PathParam("id") Long id) {
        return ApiResponse.ok("Template duplicated", templateService.duplicate(id));
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<Void> delete(@PathParam("id") Long id) {
        templateService.delete(id);
        return ApiResponse.ok("Template deleted", null);
    }

    @POST
    @Path("/{id}/preview")
    public ApiResponse<TemplatePreviewResponse> preview(
            @PathParam("id") Long id, TemplatePreviewRequest request) {
        return ApiResponse.ok(templateService.preview(id, request));
    }

    /** Preview of unsaved content, used by the editor's "Preview" button. */
    @POST
    @Path("/preview")
    public ApiResponse<TemplatePreviewResponse> previewDraft(TemplatePreviewRequest request) {
        return ApiResponse.ok(templateService.previewDraft(request));
    }
}
