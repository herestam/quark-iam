package com.example.emailapp.email.service;

import com.example.emailapp.email.dto.SmtpSettings;

/**
 * CRUD and diagnostics for the SMTP settings.
 *
 * <p>The password is write-only: {@link #get()} returns a DTO that only carries
 * a {@code passwordSet} flag.</p>
 */
public interface EmailConfigurationService {

    /** Current configuration, or {@code null} when the administrator never saved one. */
    EmailConfigurationSnapshot get();

    /** Creates or updates the configuration. A {@code null}/blank password keeps the stored one. */
    EmailConfigurationSnapshot save(EmailConfigurationCommand command);

    /** Verifies the provider is reachable and the credentials are accepted. */
    void testConnection();

    /** Sends a real message to the given address using the stored settings. */
    void sendTestEmail(String to);

    /** Settings used by the transport, or a clear error when nothing is configured. */
    SmtpSettings currentSettings();

    /** Seeds a first configuration from environment variables on first startup. */
    void ensureDefaults();
}
