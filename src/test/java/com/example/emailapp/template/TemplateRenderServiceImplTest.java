package com.example.emailapp.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.emailapp.template.service.impl.TemplateRenderServiceImpl;

/**
 * The render engine is the one place user supplied data is interpolated into an
 * email body, so these tests pin down both the substitution rules and the
 * escaping guarantee.
 */
class TemplateRenderServiceImplTest {

    private final TemplateRenderServiceImpl renderer = new TemplateRenderServiceImpl();

    @Test
    @DisplayName("substitutes the three supported placeholders")
    void substitutesSupportedPlaceholders() {
        String html = "<p>Hi {{name}}, you are receiving this at {{email}} from {{company}}.</p>";

        String rendered = renderer.renderHtml(html,
                Map.of("name", "Jane Doe", "email", "jane@example.com", "company", "Acme"));

        assertEquals("<p>Hi Jane Doe, you are receiving this at jane@example.com from Acme.</p>", rendered);
    }

    @Test
    @DisplayName("tolerates whitespace inside the braces")
    void toleratesWhitespace() {
        assertEquals("<p>Hello Jane</p>",
                renderer.renderHtml("<p>Hello {{ name }}</p>", Map.of("name", "Jane")));
    }

    @Test
    @DisplayName("leaves an unknown placeholder untouched instead of blanking it")
    void leavesUnknownPlaceholdersAlone() {
        String html = "<p>{{name}} / {{unsupported}} / {{company}}</p>";

        String rendered = renderer.renderHtml(html, Map.of("name", "Jane", "company", "Acme"));

        assertTrue(rendered.contains("{{unsupported}}"), rendered);
        assertFalse(rendered.contains("null"), rendered);
    }

    @Test
    @DisplayName("escapes substituted values in the HTML body")
    void escapesHtmlValues() {
        String rendered = renderer.renderHtml("<p>{{name}}</p>",
                Map.of("name", "<script>alert(1)</script>"));

        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", rendered);
        assertFalse(rendered.contains("<script>"), rendered);
    }

    @Test
    @DisplayName("does not escape the plain text body")
    void leavesTextBodyUnescaped() {
        assertEquals("5 < 6 & 7 > 2",
                renderer.renderText("{{name}}", Map.of("name", "5 < 6 & 7 > 2")));
    }

    @Test
    @DisplayName("keeps the template's own ampersand entity intact and escapes the value")
    void doesNotDoubleEscapeTheTemplate() {
        assertEquals("<p>Tom &amp; Jerry Jane</p>",
                renderer.renderHtml("<p>Tom &amp; Jerry {{name}}</p>", Map.of("name", "Jane")));
        // The stored template is already escaped HTML, so it is left untouched.
        assertEquals("<p>Tom &amp; Jerry</p>",
                renderer.renderHtml("<p>Tom &amp; Jerry</p>", Map.of()));
    }

    @Test
    @DisplayName("reports the variables a template actually uses")
    void extractsVariablesInOrder() {
        assertEquals(java.util.List.of("name", "company"),
                java.util.List.copyOf(renderer.extractVariables("{{name}} {{company}} {{name}}")));
        assertTrue(renderer.extractVariables("no placeholders here").isEmpty());
        assertTrue(renderer.extractVariables(null).isEmpty());
    }

    @Test
    @DisplayName("returns an empty string for null or empty content")
    void handlesEmptyContent() {
        assertEquals("", renderer.renderHtml(null, Map.of()));
        assertEquals("", renderer.renderText("", Map.of()));
    }

    @Test
    @DisplayName("survives a value containing the replacement syntax")
    void handlesDollarAndBackslashInValues() {
        assertEquals("<p>cost: $5 \\ done</p>",
                renderer.renderHtml("<p>cost: {{name}}</p>", Map.of("name", "$5 \\ done")));
    }
}
