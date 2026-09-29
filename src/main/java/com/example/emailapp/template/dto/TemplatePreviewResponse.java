package com.example.emailapp.template.dto;

/** Rendered preview, both as sanitised HTML and as plain text. */
public record TemplatePreviewResponse(
        String subject,
        String html,
        String text,
        java.util.List<String> variables
) {
}
