package com.example.emailapp.email.dto;

import com.example.emailapp.email.entity.EmailProvider;

/**
 * Everything needed to open an SMTP session, resolved from the active
 * {@code email_configuration} row (or from the environment when none exists).
 *
 * <p>The password is carried in memory only; nothing in this record is ever
 * serialised to JSON.</p>
 */
public record SmtpSettings(
        EmailProvider provider,
        String host,
        int port,
        String username,
        String password,
        String fromEmail,
        String fromName,
        boolean useTls,
        boolean useSsl,
        int connectionTimeoutMs
) {

    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    /** Never includes the secret - safe to log. */
    @Override
    public String toString() {
        return "SmtpSettings{provider=" + provider + ", host=" + host + ":" + port
                + ", username=" + username + ", tls=" + useTls + ", ssl=" + useSsl
                + ", hasPassword=" + (password != null && !password.isBlank()) + "}";
    }
}
