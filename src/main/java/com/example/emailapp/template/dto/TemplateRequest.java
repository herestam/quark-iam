package com.example.emailapp.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create / update payload for an email template. */
public record TemplateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 500) String subject,
        @NotBlank @Size(max = 32000) String htmlContent,
        @Size(max = 32000) String textContent
) {

    public TemplateRequest {
        name = trim(name);
        subject = trim(subject);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
