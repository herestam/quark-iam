package com.example.emailapp.template.dto;

import java.util.Map;

/** Request body for the live preview endpoint and the preview page. */
public record TemplatePreviewRequest(
        String subject,
        String htmlContent,
        String textContent,
        Map<String, String> variables
) {

    public TemplatePreviewRequest {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
