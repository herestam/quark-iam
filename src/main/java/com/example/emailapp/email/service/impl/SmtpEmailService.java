package com.example.emailapp.email.service.impl;


import org.jboss.logging.Logger;

import com.example.emailapp.common.exception.EmailProviderException;
import com.example.emailapp.common.validation.SecretRedactor;
import com.example.emailapp.email.dto.SmtpSettings;
import com.example.emailapp.email.entity.EmailProvider;
import com.example.emailapp.email.service.EmailConfigurationService;
import com.example.emailapp.email.service.SmtpSessionFactory;
import com.example.emailapp.email.service.EmailService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;

/**
 * SMTP implementation of {@link EmailService}.
 *
 * <p>Uses the Jakarta Mail API directly rather than the Quarkus Mailer so the
 * host, port, credentials, TLS mode and timeouts can come from the database at
 * runtime - which is what the settings screen and the SendGrid preset need.</p>
 */
@ApplicationScoped
public class SmtpEmailService implements EmailService {

    private static final Logger LOG = Logger.getLogger(SmtpEmailService.class);

    @Inject
    EmailConfigurationService configurationService;

    @Inject
    SecretRedactor redactor;

    @Inject
    SmtpSessionFactory sessionFactory;

    @Override
    public void send(String to, String subject, String htmlContent, String textContent) {
        if (to == null || to.isBlank()) {
            throw new EmailProviderException("Recipient address is missing");
        }
        try {
            SmtpSettings settings = currentSettings();
            Session session = sessionFactory.create(settings);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(from(settings));
            message.setRecipient(Message.RecipientType.TO, parseAddress(to, null));
            message.setSubject(headerSafe(subject == null ? "" : subject, "(no subject)"));
            message.setSentDate(new java.util.Date());

            String text = textContent != null && !textContent.isBlank()
                    ? textContent
                    // A plain text part is always generated so the mail is never empty.
                    : stripTags(htmlContent);
            boolean hasHtml = htmlContent != null && !htmlContent.isBlank();

            if (hasHtml) {
                // setText() followed by setContent() would discard the text part,
                // so the alternatives are assembled explicitly.
                MimeMultipart alternative = new MimeMultipart("alternative");
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(text, StandardCharsets.UTF_8.name());
                alternative.addBodyPart(textPart);
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(htmlContent, "text/html; charset=" + StandardCharsets.UTF_8.name());
                alternative.addBodyPart(htmlPart);
                message.setContent(alternative);
                message.saveChanges();
            } else {
                message.setText(text, StandardCharsets.UTF_8.name());
            }

            // Transport is not thread safe, so one is created per message and closed.
            try (Transport transport = session.getTransport()) {
                transport.connect(settings.host(), settings.port(), settings.username(), settings.password());
                transport.sendMessage(message, message.getAllRecipients());
            }
            LOG.debugf("Email handed to %s:%d for %s", settings.host(), settings.port(), redactor.redact(to));
        } catch (MessagingException e) {
            throw new EmailProviderException(describe("Could not send email to " + redactor.redact(to), e), e);
        } catch (RuntimeException e) {
            if (e instanceof EmailProviderException providerException) {
                throw providerException;
            }
            throw new EmailProviderException(describe("Could not send email to " + redactor.redact(to), e), e);
        }
    }

    private SmtpSettings currentSettings() {
        try {
            return configurationService.currentSettings();
        } catch (RuntimeException e) {
            // A missing or broken settings row is a provider problem, not a server fault.
            throw new EmailProviderException("Email settings are unusable: " + e.getMessage(), e);
        }
    }

    @Override
    public void testConnection() {
        SmtpSettings settings = currentSettings();
        Session session = sessionFactory.create(settings);
        try (Transport transport = session.getTransport()) {
            transport.connect(settings.host(), settings.port(), settings.username(), settings.password());
            LOG.infof("SMTP connection test succeeded against %s:%d", settings.host(), settings.port());
        } catch (MessagingException e) {
            throw new EmailProviderException(describe("SMTP connection to " + settings.host() + " failed", e), e);
        }
    }

    private Address from(SmtpSettings settings) throws MessagingException {
        String name = settings.fromName() == null || settings.fromName().isBlank() ? null : settings.fromName();
        if (name == null) {
            return new InternetAddress(settings.fromEmail(), true);
        }
        return withPersonal(settings.fromEmail(), name);
    }

    private Address parseAddress(String value, String personal) throws MessagingException {
        return withPersonal(value, personal);
    }

    /**
     * Builds an address that always advertises UTF-8, so non-ASCII display names
     * are transmitted correctly. The checked {@link java.io.UnsupportedEncodingException}
     * is folded into {@link MessagingException} to keep the call sites readable.
     */
    private static Address withPersonal(String address, String personal) throws MessagingException {
        if (personal == null || personal.isBlank()) {
            return new InternetAddress(address, true);
        }
        try {
            return new InternetAddress(address, personal, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new MessagingException("UTF-8 is not supported by this JVM", e);
        }
    }

    /**
     * Removes CR/LF from anything that becomes a header. Without this a subject
     * containing a newline would let an author inject arbitrary headers.
     */
    private static String headerSafe(String value, String fallback) {
        String cleaned = value.replaceAll("[\\r\\n]+", " ").trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String describe(String prefix, MessagingException e) {
        // The server reply lives in the outer message, the low level cause in the chained one.
        StringBuilder detail = new StringBuilder();
        for (Throwable current = e; current != null && detail.length() < 300; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null || message.isBlank() || detail.indexOf(message) >= 0) {
                continue;
            }
            if (detail.length() > 0) {
                detail.append(" | ");
            }
            detail.append(message);
        }
        if (detail.length() == 0) {
            detail.append(e.getClass().getSimpleName());
        }
        return redactor.redactAndTrim(prefix + ": " + detail, 400);
    }

    private String describe(String prefix, Exception e) {
        return redactor.redactAndTrim(prefix + ": " + e.getMessage(), 400);
    }

    /** Crude but dependency free plain text fallback for the text/plain part. */
    static String stripTags(String html) {
        if (html == null) {
            return "";
        }
        return html
                .replaceAll("(?i)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|h[1-6])>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll(" *\\n *", "\n")
                .replaceAll(" {2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /** SendGrid's relay preset, used when the provider is selected without explicit host. */
    public static String defaultHostFor(EmailProvider provider) {
        return provider == EmailProvider.SENDGRID ? "smtp.sendgrid.net" : null;
    }
}
