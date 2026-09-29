package com.example.emailapp.email.service;

/**
 * The seam between the job system and the outside world.
 *
 * <p>The campaign worker only ever sees this interface, so swapping SMTP for
 * SendGrid's HTTP API, Amazon SES, Mailgun or Resun means adding one more
 * implementation without touching a single job, controller or template.</p>
 */
public interface EmailService {

    /** Delivers a message. Implementations must throw on any delivery failure. */
    void send(String to, String subject, String htmlContent, String textContent);

    /**
     * Opens and immediately closes a connection to the configured provider.
     * Used by the "Test Connection" button in the settings screen.
     */
    void testConnection();
}
