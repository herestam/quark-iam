package com.example.emailapp.common.web;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

/**
 * Post/Redirect/Get helpers for the server rendered pages.
 *
 * <p>A form POST never renders a response directly: it redirects so a browser
 * refresh cannot re-submit the form, and so the user can bookmark the result.
 * The message rides along in the query string, which is why every redirect goes
 * through {@link #withFlash}.</p>
 */
@ApplicationScoped
public class Redirects {

    /** 303 to {@code path} with a success message. */
    public Response ok(String path, String message) {
        return seeOther(path, Flash.ok(message));
    }

    /** 303 to {@code path} with a failure message. */
    public Response fail(String path, String message) {
        return seeOther(path, Flash.error(message));
    }

    public Response seeOther(String path, Flash flash) {
        return Response.seeOther(URI.create(withFlash(path, flash))).build();
    }

    public Response seeOther(String path) {
        return seeOther(path, null);
    }

    public static String withFlash(String path, Flash flash) {
        if (flash == null) {
            return path;
        }
        String separator = path.contains("?") ? "&" : "?";
        return path + separator + "flash=" + encode(flash.type()) + "&msg=" + encode(flash.text());
    }

    public static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
