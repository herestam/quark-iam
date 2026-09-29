package com.example.emailapp.recipient.dto;

import com.example.emailapp.common.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Single recipient payload. */
public record RecipientRequest(
        @NotBlank @ValidEmail @Size(max = 320) String email,
        @Size(max = 200) String name,
        @Size(max = 200) String company
) {

    public RecipientRequest {
        email = email == null ? null : email.trim();
        name = trim(name);
        company = trim(company);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
