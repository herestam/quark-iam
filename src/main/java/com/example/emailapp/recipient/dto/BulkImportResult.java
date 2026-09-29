package com.example.emailapp.recipient.dto;

import java.util.List;

/**
 * Outcome of a bulk import. Invalid and duplicate lines are reported back to
 * the administrator instead of failing the whole batch.
 */
public record BulkImportResult(
        int submitted,
        int created,
        int updated,
        int skippedDuplicates,
        List<RecipientResponse> recipients,
        List<String> errors
) {

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public static BulkImportResult empty() {
        return new BulkImportResult(0, 0, 0, 0, List.of(), List.of());
    }
}
