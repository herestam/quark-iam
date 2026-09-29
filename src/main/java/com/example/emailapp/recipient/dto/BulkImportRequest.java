package com.example.emailapp.recipient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Bulk import payload. Accepts either raw text (one address per line) or CSV.
 *
 * <p>CSV lines may be {@code email}, {@code email,name} or
 * {@code email,name,company} with optional quotes and a header row.</p>
 */
public record BulkImportRequest(
        @NotBlank @Size(max = 5_000_000) String content,
        boolean hasHeader,
        boolean skipInvalid
) {

    public static final String USAGE_HINT =
            "One per line: email | email,name | email,name,company  (CSV with optional header row)";
}
