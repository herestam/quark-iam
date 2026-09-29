package com.example.emailapp.email.service;

import com.example.emailapp.email.entity.EmailProvider;
import com.example.emailapp.email.dto.SmtpSettings;

/**
 * The API-facing view of the SMTP configuration.
 *
 * <p>Note the absence of a password field: the secret is only ever written,
 * never read back. {@link #passwordSet()} tells the UI whether to render an
 * empty "leave blank to keep current" input or a fresh one.</p>
 */
public record EmailConfigurationSnapshot(
        Long id,
        EmailProvider provider,
        String smtpHost,
        int smtpPort,
        String username,
        String fromEmail,
        String fromName,
        boolean useTls,
        boolean useSsl,
        int connectionTimeoutMs,
        boolean active,
        boolean passwordSet
) {

    public static EmailConfigurationSnapshot from(SmtpSettings settings, boolean active) {
        return new EmailConfigurationSnapshot(
                null,
                settings.provider(),
                settings.host(),
                settings.port(),
                settings.username(),
                settings.fromEmail(),
                settings.fromName(),
                settings.useTls(),
                settings.useSsl(),
                settings.connectionTimeoutMs(),
                active,
                settings.password() != null && !settings.password().isBlank());
    }

    public boolean isConfigured() {
        return id != null || (smtpHost != null && !smtpHost.isBlank());
    }
}
