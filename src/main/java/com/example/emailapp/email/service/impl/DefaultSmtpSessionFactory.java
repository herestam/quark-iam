package com.example.emailapp.email.service.impl;

import java.util.Properties;

import com.example.emailapp.email.dto.SmtpSettings;
import com.example.emailapp.email.service.SmtpSessionFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

/**
 * Production {@link SmtpSessionFactory}: translates the stored settings into
 * JavaMail properties.
 *
 * <p>When the provider needs credentials they are supplied through an
 * {@link Authenticator} rather than a {@code mail.smtp.password} property, so
 * the secret never lands in a map that could be logged or serialised.</p>
 */
@ApplicationScoped
public class DefaultSmtpSessionFactory implements SmtpSessionFactory {

    @Override
    public Session create(SmtpSettings settings) {
        Properties props = baseProperties(settings);
        return settings.hasCredentials()
                ? Session.getInstance(props, authenticator(settings))
                : Session.getInstance(props);
    }

    /** Visible for tests: the property set a session is built from. */
    public static Properties baseProperties(SmtpSettings settings) {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", settings.host());
        props.put("mail.smtp.port", String.valueOf(settings.port()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(settings.connectionTimeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(settings.connectionTimeoutMs()));
        props.put("mail.smtp.writetimeout", String.valueOf(settings.connectionTimeoutMs()));
        props.put("mail.smtp.starttls.enable", String.valueOf(settings.useTls()));
        props.put("mail.smtp.ssl.enable", String.valueOf(settings.useSsl()));
        // Without this the JDK would happily accept a certificate for another host.
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.localhost", "localhost");
        if (settings.hasCredentials()) {
            props.put("mail.smtp.auth", "true");
        }
        if (settings.useSsl()) {
            props.put("mail.smtp.socketFactory.port", String.valueOf(settings.port()));
        }
        return props;
    }

    /** Visible for tests: the authenticator that hands the password to the server. */
    public static Authenticator authenticator(SmtpSettings settings) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(settings.username(),
                        settings.password() == null ? "" : settings.password());
            }
        };
    }
}
