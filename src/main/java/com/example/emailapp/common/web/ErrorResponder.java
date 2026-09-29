package com.example.emailapp.common.web;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.example.emailapp.common.response.ApiResponse;

/**
 * Turns a failure into a response that matches what the caller asked for.
 *
 * <p>One controller tree serves both the JSON API and the Qute pages, so every
 * mapper delegates here: a browser navigation (which sends {@code text/html})
 * gets the HTML error page, everything else gets the JSON error envelope.
 * Content negotiation is used instead of a URL prefix check so that a plain
 * {@code curl} against a page URL still receives machine readable output.</p>
 */
@Singleton
public class ErrorResponder {

    @Inject
    Ui ui;

    public Response respond(HttpHeaders headers, Response.Status status, String code,
            String message, List<String> details) {
        if (wantsHtml(headers)) {
            return Response.status(status)
                    .type(MediaType.TEXT_HTML)
                    .entity(ui.renderError(status.getStatusCode(), code, message))
                    .build();
        }
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiResponse.error(code, message, details == null ? List.of() : details))
                .build();
    }

    /** True when the caller explicitly prefers HTML over JSON. */
    public static boolean wantsHtml(HttpHeaders headers) {
        if (headers == null) {
            return false;
        }
        List<String> accept = headers.getRequestHeader(HttpHeaders.ACCEPT);
        if (accept == null) {
            return false;
        }
        for (String value : accept) {
            for (String candidate : value.split(",")) {
                String type = candidate.trim();
                int parameters = type.indexOf(';');
                if (parameters >= 0) {
                    // Honour an explicit "q=0" rejection, otherwise fall back to the
                    // first media type which is what HTTP prescribes.
                    String params = type.substring(parameters);
                    type = type.substring(0, parameters);
                    if (params.contains("q=0") || params.contains("q=0.0")) {
                        continue;
                    }
                }
                if (type.equalsIgnoreCase(MediaType.TEXT_HTML) || type.equalsIgnoreCase("text/*")) {
                    return true;
                }
            }
        }
        return false;
    }

}
