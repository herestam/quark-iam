package com.example.emailapp.template.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Template as returned by the API and rendered by the UI. Entities are never
 * exposed directly, so the shape can evolve without breaking clients.
 */
public record TemplateResponse(
        Long id,
        String name,
        String subject,
        String htmlContent,
        String textContent,
        Instant createdAt,
        Instant updatedAt,
        List<String> variables,
        int usageCount
) {

    public Set<String> variableSet() {
        return variables == null ? Set.of() : Set.copyOf(variables);
    }
}
