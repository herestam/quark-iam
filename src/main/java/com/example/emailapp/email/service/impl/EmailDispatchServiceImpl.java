package com.example.emailapp.email.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.common.validation.HtmlSanitizer;
import com.example.emailapp.email.dto.OutboundEmail;
import com.example.emailapp.email.service.EmailDispatchService;
import com.example.emailapp.email.service.EmailService;
import com.example.emailapp.template.service.TemplateRenderService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Renders a template for one recipient and hands the result to the provider.
 *
 * <p>This is the only class that knows both about placeholders and about
 * {@link EmailService}, so the worker stays free of rendering concerns.</p>
 */
@ApplicationScoped
public class EmailDispatchServiceImpl implements EmailDispatchService {

    private static final Logger LOG = Logger.getLogger(EmailDispatchServiceImpl.class);

    @Inject
    EmailService emailService;

    @Inject
    TemplateRenderService renderService;

    @Inject
    HtmlSanitizer sanitizer;

    @Override
    public void dispatch(OutboundEmail email) {
        dispatch(email, Map.of());
    }

    @Override
    public void dispatch(OutboundEmail email, Map<String, String> extraVariables) {
        if (email == null) {
            throw new InputValidationException("Nothing to send");
        }
        if (email.to() == null || email.to().isBlank()) {
            throw new InputValidationException("Recipient address is missing");
        }
        Map<String, String> variables = buildVariables(email, extraVariables);

        String html = renderService.renderHtml(email.htmlContent(), variables);
        String text = renderService.renderText(email.textContent(), variables);

        // Defensive: a stored template is sanitised right before it leaves the app.
        emailService.send(email.to(), email.subject(), sanitizer.sanitize(html), text);
    }

    private Map<String, String> buildVariables(OutboundEmail email, Map<String, String> extraVariables) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("name", orEmpty(email.toName()));
        variables.put("email", orEmpty(email.to()));
        Map<String, String> provided = email.variables() == null ? Map.of() : email.variables();
        variables.put("company", orEmpty(provided.get("company")));
        variables.putAll(provided);
        if (extraVariables != null) {
            variables.putAll(extraVariables);
        }
        variables.keySet().removeIf(key -> key == null);
        LOG.debugf("Rendering message for %d variables", variables.size());
        return variables;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
