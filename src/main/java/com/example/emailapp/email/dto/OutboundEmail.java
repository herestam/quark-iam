package com.example.emailapp.email.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single outbound message, ready to be rendered and handed to the provider.
 * This is the only shape the worker passes around, which keeps the job system
 * independent of the transport.
 */
public record OutboundEmail(
        String to,
        String toName,
        String subject,
        String htmlContent,
        String textContent,
        Map<String, String> variables
) {

    public OutboundEmail {
        variables = variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    public static OutboundEmail of(String to, String subject, String htmlContent, String textContent) {
        return new OutboundEmail(to, null, subject, htmlContent, textContent, Map.of());
    }

    /** Display form used in log lines, never the rendered body. */
    public String target() {
        return toName == null || toName.isBlank() ? to : toName + " <" + to + ">";
    }
}
