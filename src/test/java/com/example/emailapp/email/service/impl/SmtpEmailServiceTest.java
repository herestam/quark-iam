package com.example.emailapp.email.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.emailapp.common.exception.EmailProviderException;
import com.example.emailapp.email.dto.SmtpSettings;
import com.example.emailapp.email.entity.EmailProvider;
import com.example.emailapp.email.service.EmailConfigurationService;
import com.example.emailapp.email.service.SmtpSessionFactory;
import com.example.emailapp.common.validation.SecretRedactor;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import jakarta.mail.internet.MimeMessage;

/**
 * Every test here runs fully offline: the injected factory hands back a real
 * Session whose transport protocol is registered to {@link RecordingTransport},
 * so no socket is ever opened. The class lives in the production package because
 * the service exposes its collaborators as package-private fields.
 */
class SmtpEmailServiceTest {

    private static final String STUB_PROTOCOL = "recording";

    /**
     * A {@link Transport} that records what it was asked to deliver. Registered
     * per session through {@link #stubSession(SmtpSettings)}.
     */
    public static class RecordingTransport extends Transport {

        static MimeMessage lastMessage;
        static Address[] lastRecipients;
        static String connectedHost;
        static int connectedPort;
        static boolean closed;
        static MessagingException failWith;

        public RecordingTransport(Session session, URLName url) {
            super(session, url);
        }

        @Override
        public void connect(String host, int port, String user, String password) throws MessagingException {
            connectedHost = host;
            connectedPort = port;
            if (failWith != null) {
                throw failWith;
            }
        }

        @Override
        public void sendMessage(Message message, Address[] recipients) throws MessagingException {
            if (failWith != null) {
                throw failWith;
            }
            lastMessage = (MimeMessage) message;
            lastRecipients = recipients;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private EmailConfigurationService configurationService;
    private AtomicInteger sessionCreations;
    private SmtpEmailService service;

    @BeforeEach
    void setUp() {
        RecordingTransport.lastMessage = null;
        RecordingTransport.lastRecipients = null;
        RecordingTransport.connectedHost = null;
        RecordingTransport.connectedPort = 0;
        RecordingTransport.closed = false;
        RecordingTransport.failWith = null;
        sessionCreations = new AtomicInteger();

        configurationService = mock(EmailConfigurationService.class);
        SmtpSessionFactory sessionFactory = settings -> {
            sessionCreations.incrementAndGet();
            return stubSession(settings);
        };
        service = new SmtpEmailService();
        service.configurationService = configurationService;
        service.sessionFactory = sessionFactory;
        service.redactor = new SecretRedactor();
    }

    /**
     * A real Session bound to the stub transport. {@code mail.transport.class}
     * is ignored by Jakarta Mail 2, so the transport protocol is resolved through
     * a provider registered on the session instead.
     */
    private static Session stubSession(SmtpSettings settings) {
        Properties props = new Properties();
        props.put("mail.smtp.host", settings.host());
        props.put("mail.smtp.port", String.valueOf(settings.port()));
        props.put("mail.transport.protocol", STUB_PROTOCOL);
        Session session = Session.getInstance(props);
        session.addProvider(new Provider(Provider.Type.TRANSPORT, STUB_PROTOCOL,
                RecordingTransport.class.getName(), "app", "Recording transport for tests"));
        return session;
    }

    private static SmtpSettings settings() {
        return new SmtpSettings(EmailProvider.SMTP, "smtp.example.com", 587, "mailer", "s3cret-value",
                "no-reply@example.com", "Campaign Manager", true, false, 10_000);
    }

    @Test
    @DisplayName("delivers an HTML message with a generated plain text alternative")
    void sendsHtmlWithTextAlternative() throws Exception {
        when(configurationService.currentSettings()).thenReturn(settings());

        service.send("jane@example.com", "Hello", "<p>Hello Jane</p><p>Second line</p>", null);

        MimeMessage sent = RecordingTransport.lastMessage;
        assertNotNull(sent, "nothing was handed to the transport");
        assertEquals("Hello", sent.getSubject());
        assertEquals("jane@example.com", sent.getAllRecipients()[0].toString());
        assertEquals("Campaign Manager <no-reply@example.com>", sent.getFrom()[0].toString());
        assertEquals("smtp.example.com", RecordingTransport.connectedHost);
        assertEquals(587, RecordingTransport.connectedPort);
        assertTrue(RecordingTransport.closed, "the transport must be closed again");

        Multipart body = (Multipart) sent.getContent();
        assertEquals(2, body.getCount(), "expected an alternative text part plus the HTML part");
        String plain = body.getBodyPart(0).getContent().toString();
        assertEquals("Hello Jane\nSecond line", plain);
        assertTrue(body.getBodyPart(1).getContentType().startsWith("text/html"));
    }

    @Test
    @DisplayName("keeps a caller supplied text part instead of deriving one")
    void prefersSuppliedTextPart() throws Exception {
        when(configurationService.currentSettings()).thenReturn(settings());

        service.send("jane@example.com", "Hello", "<p>ignored</p>", "the explicit text");

        Multipart body = (Multipart) RecordingTransport.lastMessage.getContent();
        assertEquals("the explicit text", body.getBodyPart(0).getContent().toString());
    }

    @Test
    @DisplayName("strips a header injection attempt out of the subject")
    void sanitisesSubject() throws Exception {
        when(configurationService.currentSettings()).thenReturn(settings());

        service.send("jane@example.com", "Hello\r\nBcc: attacker@evil.test", "<p>body</p>", null);

        assertEquals("Hello Bcc: attacker@evil.test", RecordingTransport.lastMessage.getSubject());
    }

    @Test
    @DisplayName("rejects a missing recipient without building a session")
    void rejectsMissingRecipient() {
        assertThrows(EmailProviderException.class,
                () -> service.send("  ", "Hello", "<p>body</p>", null));

        assertEquals(0, sessionCreations.get());
    }

    @Test
    @DisplayName("turns HTML into a readable text alternative")
    void stripsMarkupForTextPart() {
        assertEquals("Hello Jane\nSecond line",
                SmtpEmailService.stripTags("<p>Hello Jane</p><p>Second line</p>"));
        assertEquals("a b", SmtpEmailService.stripTags("a <strong>b</strong>"));
        assertEquals("", SmtpEmailService.stripTags(null));
        assertFalse(SmtpEmailService.stripTags("<script>alert(1)</script>visible").contains("alert"));
    }

    @Test
    @DisplayName("wraps a transport failure and keeps the server reply")
    void wrapsProviderFailure() {
        when(configurationService.currentSettings()).thenReturn(settings());
        RecordingTransport.failWith = new MessagingException("535 authentication failed", new IOException("nope"));

        EmailProviderException thrown = assertThrows(EmailProviderException.class,
                () -> service.send("jane@example.com", "Hello", "<p>body</p>", null));

        assertTrue(thrown.getMessage().contains("535"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("nope"), thrown.getMessage());
    }

    @Test
    @DisplayName("reports unusable settings as a provider error rather than a 500")
    void handlesAuthFailure() {
        doThrow(new IllegalStateException("SMTP is not configured"))
                .when(configurationService).currentSettings();

        EmailProviderException thrown = assertThrows(EmailProviderException.class,
                () -> service.send("jane@example.com", "Hello", "<p>body</p>", null));

        assertTrue(thrown.getMessage().contains("not configured"), thrown.getMessage());
    }

    @Test
    @DisplayName("never prints the password in an error message")
    void doesNotLeakCredentials() {
        when(configurationService.currentSettings()).thenReturn(settings());
        service.redactor.register("s3cret-value");
        RecordingTransport.failWith = new MessagingException("535 bad password s3cret-value");

        EmailProviderException thrown = assertThrows(EmailProviderException.class,
                () -> service.send("jane@example.com", "Hello", "<p>body</p>", null));

        assertFalse(thrown.getMessage().contains("s3cret-value"), thrown.getMessage());
    }

    @Test
    @DisplayName("reuses the injected session factory for every send")
    void alwaysUsesTheInjectedFactory() {
        when(configurationService.currentSettings()).thenReturn(settings());

        service.send("jane@example.com", "One", "<p>a</p>", null);
        service.send("john@example.com", "Two", "<p>b</p>", null);

        assertEquals(2, sessionCreations.get());
        verify(configurationService, times(2)).currentSettings();
    }

    @Test
    @DisplayName("sends one message per call so transports are never shared")
    void usesAFreshTransportPerMessage() throws Exception {
        when(configurationService.currentSettings()).thenReturn(settings());

        service.send("jane@example.com", "One", "<p>a</p>", null);
        service.send("jane@example.com", "Two", "<p>b</p>", null);

        // A closed transport would fail the second send if it had been reused.
        assertNotNull(RecordingTransport.lastMessage);
        assertEquals("Two", RecordingTransport.lastMessage.getSubject());
    }

    @Test
    @DisplayName("reports a successful connection test")
    void reportsSuccessfulConnectionTest() {
        when(configurationService.currentSettings()).thenReturn(settings());

        service.testConnection();

        assertEquals("smtp.example.com", RecordingTransport.connectedHost);
        assertTrue(RecordingTransport.closed);
    }

    @Test
    @DisplayName("surfaces a connection failure as a provider error")
    void testConnectionSurfacesProviderError() {
        when(configurationService.currentSettings()).thenReturn(settings());
        RecordingTransport.failWith = new MessagingException("Connection refused");

        EmailProviderException thrown = assertThrows(EmailProviderException.class, service::testConnection);

        assertTrue(thrown.getMessage().contains("Connection refused"), thrown.getMessage());
    }

    @Test
    @DisplayName("does not request authentication when no credentials are stored")
    void skipsAuthWithoutCredentials() {
        Properties props = DefaultSmtpSessionFactory
                .baseProperties(new SmtpSettings(EmailProvider.SMTP, "localhost", 25, null, null,
                        "no-reply@example.com", null, false, false, 5_000));

        assertNull(props.get("mail.smtp.auth"));
    }

    @Test
    @DisplayName("enables STARTTLS and auth once credentials are stored")
    void enablesAuthWithCredentials() {
        Properties props = DefaultSmtpSessionFactory.baseProperties(settings());

        assertEquals("true", props.get("mail.smtp.auth"));
        assertEquals("true", props.get("mail.smtp.starttls.enable"));
        assertEquals("10000", props.get("mail.smtp.connectiontimeout"));
    }

    @Test
    @DisplayName("picks the SendGrid relay when the provider is SendGrid")
    void resolvesSendGridHost() {
        assertEquals("smtp.sendgrid.net", SmtpEmailService.defaultHostFor(EmailProvider.SENDGRID));
        org.junit.jupiter.api.Assertions.assertNull(SmtpEmailService.defaultHostFor(EmailProvider.SMTP));
    }

}
