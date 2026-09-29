package com.example.emailapp.common.web;

/**
 * One-shot user feedback carried across a redirect.
 *
 * <p>The UI uses the Post/Redirect/Get pattern, so the message travels in the
 * query string of the redirect target and is rendered once by the layout.</p>
 */
public record Flash(String type, String text) {

    public static final String OK = "ok";
    public static final String ERROR = "error";

    public static Flash ok(String text) {
        return new Flash(OK, text);
    }

    public static Flash error(String text) {
        return new Flash(ERROR, text);
    }

    public static Flash of(String type, String text) {
        return text == null || text.isBlank() ? null : new Flash(type, text);
    }

    public boolean isError() {
        return ERROR.equals(type);
    }
}
