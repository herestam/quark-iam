package com.example.emailapp.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.emailapp.common.web.PageRenderer;
import com.example.emailapp.common.web.Params;
import com.example.emailapp.common.web.Redirects;
import com.example.emailapp.common.web.UiSupport;
import com.example.emailapp.common.web.Views;
import com.example.emailapp.email.entity.EmailProvider;
import com.example.emailapp.email.service.EmailConfigurationCommand;
import com.example.emailapp.email.service.EmailConfigurationService;
import com.example.emailapp.email.service.EmailConfigurationSnapshot;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * SMTP / SendGrid settings page.
 *
 * <p>The stored password is never sent back to the browser. The form therefore
 * always renders an empty password field plus a "password on file" hint, and an
 * empty submission keeps the existing secret instead of clearing it.</p>
 */
@Path("/settings")
public class SettingsPage {

    @Inject
    PageRenderer pages;

    @Inject
    Redirects redirects;

    @Inject
    EmailConfigurationService configurationService;

    @GET
    @Path("/email")
    @Produces(MediaType.TEXT_HTML)
    public String email(@Context UriInfo info) {
        return render(Params.of(info), info.getRequestUri().getPath());
    }

    @POST
    @Path("/email")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response save(@FormParam("provider") String provider,
            @FormParam("smtpHost") String smtpHost,
            @FormParam("smtpPort") int smtpPort,
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("fromEmail") String fromEmail,
            @FormParam("fromName") String fromName,
            @FormParam("useTls") boolean useTls,
            @FormParam("useSsl") boolean useSsl,
            @FormParam("connectionTimeoutMs") int connectionTimeoutMs,
            @FormParam("active") boolean active) {
        EmailConfigurationCommand command = new EmailConfigurationCommand(
                parseProvider(provider), smtpHost, smtpPort, blankToNull(username), password,
                fromEmail, blankToNull(fromName), useTls, useSsl,
                connectionTimeoutMs <= 0 ? 10_000 : connectionTimeoutMs, active);
        configurationService.save(command);
        return redirects.ok("/settings/email", "Email settings saved.");
    }

    @POST
    @Path("/email/test-connection")
    public Response testConnection() {
        configurationService.testConnection();
        return redirects.ok("/settings/email", "SMTP connection succeeded.");
    }

    @POST
    @Path("/email/test-send")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response testSend(@FormParam("to") String to) {
        configurationService.sendTestEmail(to);
        return redirects.ok("/settings/email", "Test message sent to " + to + ".");
    }

    private String render(Params params, String self) {
        EmailConfigurationSnapshot snapshot = configurationService.get();
        return pages.render(Views.EMAIL_SETTINGS, UiSupport.Section.SETTINGS, params, data(
                "config", snapshot,
                "providers", List.of(EmailProvider.values()),
                "self", self,
                "testEmail", params.get("to", "")));
    }

    private static EmailProvider parseProvider(String value) {
        if (value == null || value.isBlank()) {
            return EmailProvider.SMTP;
        }
        try {
            return EmailProvider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EmailProvider.SMTP;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
