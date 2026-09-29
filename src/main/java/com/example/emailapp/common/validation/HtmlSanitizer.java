package com.example.emailapp.common.validation;

import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Defensive HTML sanitiser applied to template content before it is previewed
 * or injected into an outgoing message.
 *
 * <p>Template authors are trusted administrators, but a stored XSS payload in a
 * template would execute inside the preview iframe of the admin UI and inside
 * the in-app mail viewer, so anything script-capable is removed.</p>
 */
@ApplicationScoped
public class HtmlSanitizer {

    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "<\\s*(script|iframe|object|embed|applet|frame|frameset|meta|link|form|base)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SCRIPT_SELF_CLOSING = Pattern.compile(
            "<\\s*(script|iframe|object|embed|applet|frame|frameset|meta|link|form|base)\\b[^>]*/?>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "\\son[a-z]{3,20}\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DANGEROUS_URL = Pattern.compile(
            "(href|src|action|formaction|xlink:href)\\s*=\\s*(\"|')?\\s*(javascript|vbscript|data)\\s*:",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    /**
     * Removes script capable markup while leaving presentational HTML intact.
     * Never throws: on any unexpected input the original (truncated) value is
     * returned so a rendering bug can not silently destroy a template.
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html == null ? "" : html;
        }
        try {
            String result = SCRIPT_BLOCK.matcher(html).replaceAll("");
            result = SCRIPT_SELF_CLOSING.matcher(result).replaceAll("");
            result = EVENT_HANDLER.matcher(result).replaceAll("");
            result = DANGEROUS_URL.matcher(result).replaceAll("$1=\"#\"");
            result = HTML_COMMENT.matcher(result).replaceAll("");
            return result;
        } catch (RuntimeException e) {
            return html.length() > 32000 ? html.substring(0, 32000) : html;
        }
    }
}
