package com.example.emailapp.template.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.emailapp.template.service.TemplateRenderService;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default {@link TemplateRenderService}.
 *
 * <p>Safety rules, in order of importance:</p>
 * <ol>
 *   <li>Unknown placeholders are left untouched instead of being blanked out, so
 *       a typo in a template is visible rather than silently swallowed.</li>
 *   <li>Values are HTML escaped in HTML bodies, so recipient supplied data can
 *       never inject markup or a script tag into a message.</li>
 *   <li>Replacement uses {@link Matcher#quoteReplacement} so a {@code $} or a
 *       backslash in a value cannot trigger a {@code NoSuchElementException}
 *       from the regex engine.</li>
 * </ol>
 */
@ApplicationScoped
public class TemplateRenderServiceImpl implements TemplateRenderService {

    /** {@code {{name}}}, tolerating inner whitespace: {{ name }}. */
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]{1,64})\\s*}}");

    private static final List<String> SUPPORTED = List.of("name", "email", "company");

    @Override
    public String renderHtml(String content, Map<String, String> variables) {
        return render(content, variables, true);
    }

    @Override
    public String renderText(String content, Map<String, String> variables) {
        return render(content, variables, false);
    }

    @Override
    public Set<String> extractVariables(String content) {
        Set<String> found = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return found;
        }
        Matcher matcher = PLACEHOLDER.matcher(content);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Override
    public List<String> supportedVariables() {
        return SUPPORTED;
    }

    @Override
    public Map<String, String> previewVariables() {
        return Map.of(
                "name", "Alex Morgan",
                "email", "alex@example.com",
                "company", "Example Ltd");
    }

    private String render(String content, Map<String, String> variables, boolean escape) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        Map<String, String> values = variables == null ? Map.of() : variables;
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuilder out = new StringBuilder(content.length() + 64);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null) {
                // Unknown placeholder: keep the original text verbatim.
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String replacement = escape ? escapeHtml(value) : value;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static String escapeHtml(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
