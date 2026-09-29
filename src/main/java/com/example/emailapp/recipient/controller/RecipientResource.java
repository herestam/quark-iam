package com.example.emailapp.recipient.controller;

import com.example.emailapp.common.response.ApiResponse;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.recipient.dto.BulkImportRequest;
import com.example.emailapp.recipient.dto.BulkImportResult;
import com.example.emailapp.recipient.dto.RecipientRequest;
import com.example.emailapp.recipient.dto.RecipientResponse;
import com.example.emailapp.recipient.service.RecipientService;

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

/** Recipient CRUD and bulk import. */
@Path("/api/recipients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecipientResource {

    @Inject
    RecipientService recipientService;

    @GET
    public ApiResponse<PageResponse<RecipientResponse>> list(
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        return ApiResponse.ok(recipientService.list(search, page, size));
    }

    @GET
    @Path("/{id}")
    public ApiResponse<RecipientResponse> get(@PathParam("id") Long id) {
        return ApiResponse.ok(recipientService.get(id));
    }

    @POST
    public Response create(@Valid RecipientRequest request) {
        RecipientResponse created = recipientService.create(request);
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.ok("Recipient created", created))
                .build();
    }

    @PUT
    @Path("/{id}")
    public ApiResponse<RecipientResponse> update(@PathParam("id") Long id, @Valid RecipientRequest request) {
        return ApiResponse.ok("Recipient updated", recipientService.update(id, request));
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<Void> delete(@PathParam("id") Long id) {
        recipientService.delete(id);
        return ApiResponse.ok("Recipient deleted", null);
    }

    /**
     * Bulk import. Returns 200 with a per-row report rather than failing the
     * whole batch when a few lines are invalid.
     */
    @POST
    @Path("/import")
    public ApiResponse<BulkImportResult> importBulk(@Valid BulkImportRequest request) {
        BulkImportResult result = recipientService.importBulk(request);
        return ApiResponse.ok(
                "Imported " + result.created() + " new and enriched " + result.updated() + " recipient(s)",
                result);
    }
}
