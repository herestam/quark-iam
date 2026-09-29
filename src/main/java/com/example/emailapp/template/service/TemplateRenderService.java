package com.example.emailapp.template.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the simple {@code {{variable}}} placeholders used by email bodies.
 *
 * <p>Qute is deliberately not reused here: the syntax has to stay
 * {@code {{name}}} because that is what administrators type, and because a
 * template is stored in the database and rendered outside of any HTTP
 * request.</p>
 */
public interface TemplateRenderService {

    /** Replaces placeholders in HTML content; values are HTML escaped. */
    String renderHtml(String content, Map<String, String> variables);

    /** Replaces placeholders in plain text content; values are not escaped. */
    String renderText(String content, Map<String, String> variables);

    /** Every placeholder name found in the content, in order of appearance. */
    Set<String> extractVariables(String content);

    /** Placeholder names the application knows how to fill. */
    List<String> supportedVariables();

    /** Values used to render the preview shown before a campaign is started. */
    Map<String, String> previewVariables();
}
