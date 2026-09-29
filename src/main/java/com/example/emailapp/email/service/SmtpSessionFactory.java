package com.example.emailapp.email.service;

import jakarta.mail.Session;

import com.example.emailapp.email.dto.SmtpSettings;

/**
 * Creates the Jakarta Mail {@link Session} used to talk to the SMTP server.
 *
 * <p>Extracted from {@link com.example.emailapp.email.service.impl.SmtpEmailService}
 * for one reason: it is the only piece of the sending path that reaches out to
 * the network. Behind this seam a unit test can supply a session wired to a
 * stub {@code Transport} and assert on the produced message, so the test suite
 * never needs a real mail server.</p>
 */
public interface SmtpSessionFactory {

    Session create(SmtpSettings settings);
}
