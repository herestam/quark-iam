package com.example.emailapp.email.service.impl;


import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.example.emailapp.common.exception.BusinessRuleException;
import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.common.validation.SecretRedactor;
import com.example.emailapp.config.EmailConfig;
import com.example.emailapp.email.dto.SmtpSettings;
import com.example.emailapp.email.entity.EmailConfiguration;
import com.example.emailapp.email.entity.EmailProvider;
import com.example.emailapp.email.repository.EmailConfigurationRepository;
import com.example.emailapp.email.service.EmailConfigurationCommand;
import com.example.emailapp.email.service.EmailConfigurationService;
import com.example.emailapp.email.service.EmailConfigurationSnapshot;
import com.example.emailapp.email.service.EmailService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import java.util.Optional;

/**
 * Owns the single {@code email_configuration} row.
 *
 * <p>Two invariants are enforced here: the secret is never handed out again,
 * and the value that is currently in the database (or the environment fallback)
 * is registered with {@link SecretRedactor} so it can never appear in a log
 * line or an exception message.</p>
 */
@ApplicationScoped
public class EmailConfigurationServiceImpl implements EmailConfigurationService {

    private static final Logger LOG = Logger.getLogger(EmailConfigurationServiceImpl.class);
    private static final int ERROR_LIMIT = 4000;

    @Inject
    EmailConfigurationRepository repository;

    @Inject
    EmailService emailService;

    @Inject
    EmailConfig emailConfig;

    @Inject
    SecretRedactor redactor;

    @Inject
    Validator validator;

    @ConfigProperty(name = "smtp.host")
    Optional<String> envHost;

    @ConfigProperty(name = "smtp.port")
    Optional<Integer> envPort;

    @ConfigProperty(name = "smtp.username")
    Optional<String> envUsername;

    @ConfigProperty(name = "smtp.password")
    Optional<String> envPassword;

    @ConfigProperty(name = "smtp.from.email")
    Optional<String> envFromEmail;

    @ConfigProperty(name = "smtp.from.name")
    Optional<String> envFromName;

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional
    public EmailConfigurationSnapshot get() {
        return repository.findActive()
                .map(EmailConfigurationServiceImpl::toSnapshot)
                .orElseGet(this::environmentSnapshot);
    }

    @Override
    @Transactional
    public SmtpSettings currentSettings() {
        Optional<EmailConfiguration> stored = repository.findActive();
        if (stored.isPresent()) {
            EmailConfiguration config = stored.get();
            redactor.register(config.password);
            return new SmtpSettings(
                    config.provider,
                    config.smtpHost,
                    config.smtpPort,
                    config.username,
                    config.password,
                    config.fromEmail,
                    config.fromName,
                    config.useTls,
                    config.useSsl,
                    config.connectionTimeoutMs);
        }
        SmtpSettings fallback = environmentSettings();
        if (fallback == null) {
            throw new BusinessRuleException(
                    "No email provider is configured. Add SMTP settings under Settings / Email first.");
        }
        return fallback;
    }

    // ----------------------------------------------------------------- writes

    @Override
    @Transactional
    public EmailConfigurationSnapshot save(EmailConfigurationCommand command) {
        validate(command);

        EmailConfiguration config = repository.findActive().orElseGet(EmailConfiguration::new);
        boolean passwordChanged = command.changesPassword();

        config.provider = command.provider();
        config.smtpHost = normaliseHost(command.provider(), command.smtpHost());
        config.smtpPort = command.smtpPort();
        config.username = trimToNull(command.username());
        if (passwordChanged) {
            config.password = command.password();
            config.passwordSet = true;
            redactor.register(config.password);
        }
        config.fromEmail = command.fromEmail().trim();
        config.fromName = trimToNull(command.fromName());
        config.useTls = command.useTls();
        config.useSsl = command.useSsl();
        config.connectionTimeoutMs = command.connectionTimeoutMs();
        config.active = command.active();

        if (passwordChanged && config.username == null) {
            throw new InputValidationException("A username is required when a password is set",
                    java.util.List.of("username: required together with password"));
        }

        if (config.id == null) {
            repository.persist(config);
        } else {
        }
        repository.flush();

        LOG.infof("Email configuration saved: provider=%s host=%s:%d tls=%s ssl=%s passwordUpdated=%s",
                config.provider, config.smtpHost, config.smtpPort, config.useTls, config.useSsl, passwordChanged);
        return toSnapshot(config);
    }

    // ------------------------------------------------------------ diagnostics

    @Override
    public void testConnection() {
        emailService.testConnection();
    }

    @Override
    public void sendTestEmail(String to) {
        if (to == null || to.isBlank()) {
            throw new InputValidationException("A test recipient address is required",
                    java.util.List.of("to: must not be blank"));
        }
        String subject = "Test email from Email Campaign Manager";
        String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>Test email</title></head>
                <body style="font-family:Arial,Helvetica,sans-serif;background:#f4f5f7;padding:24px">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:24px">
                    <h2 style="margin-top:0">SMTP configuration works</h2>
                    <p>This message was sent to <strong>%s</strong> at %s.</p>
                    <p style="color:#6b7280">If you received it, the provider, credentials and sender address
                    are configured correctly.</p>
                  </div>
                </body>
                </html>
                """.formatted(escapeHtml(to), java.time.Instant.now());
        emailService.send(to, subject, html, "Test email. If you received it, the SMTP configuration is correct.");
        LOG.infof("Test email sent to %s", redactor.redact(to));
    }

    /**
     * Creates a first configuration from {@code SMTP_*} environment variables so
     * a Docker deployment works without opening the settings screen first.
     */
    @Override
    @Transactional
    public void ensureDefaults() {
        if (repository.countAll() > 0) {
            repository.findActive().ifPresent(c -> redactor.register(c.password));
            return;
        }
        SmtpSettings env = environmentSettings();
        if (env == null) {
            LOG.info("No email configuration stored and no SMTP_* environment variables set - "
                    + "configure the provider under Settings / Email before starting a campaign.");
            return;
        }
        EmailConfiguration config = new EmailConfiguration();
        config.provider = env.provider();
        config.smtpHost = env.host();
        config.smtpPort = env.port();
        config.username = env.username();
        config.password = env.password();
        config.passwordSet = env.password() != null && !env.password().isBlank();
        config.fromEmail = env.fromEmail();
        config.fromName = env.fromName();
        config.useTls = env.useTls();
        config.useSsl = env.useSsl();
        config.connectionTimeoutMs = env.connectionTimeoutMs();
        config.active = true;
        repository.persist(config);
        redactor.register(env.password());
        LOG.infof("Seeded email configuration from environment: %s:%d", env.host(), env.port());
    }

    // --------------------------------------------------------------- internals

    private void validate(EmailConfigurationCommand command) {
        if (command == null) {
            throw new InputValidationException("Request body is required");
        }
        var violations = validator.validate(command);
        if (!violations.isEmpty()) {
            throw new InputValidationException("Email configuration is not valid",
                    violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().toList());
        }
        if (command.useTls() && command.useSsl() && command.smtpPort() == 465) {
            throw new InputValidationException(
                    "STARTTLS and SSL cannot both be enabled on port 465",
                    java.util.List.of("useTls: disable TLS when SSL is enabled on port 465"));
        }
    }

    /**
     * Applies provider defaults. Selecting SendGrid without typing anything still
     * yields a working relay configuration.
     */
    private String normaliseHost(EmailProvider provider, String host) {
        String trimmed = host == null ? "" : host.trim();
        if (trimmed.isEmpty() && provider == EmailProvider.SENDGRID) {
            return "smtp.sendgrid.net";
        }
        return trimmed;
    }

    private static EmailConfigurationSnapshot toSnapshot(EmailConfiguration config) {
        return new EmailConfigurationSnapshot(
                config.id,
                config.provider,
                config.smtpHost,
                config.smtpPort,
                config.username,
                config.fromEmail,
                config.fromName,
                config.useTls,
                config.useSsl,
                config.connectionTimeoutMs,
                config.active,
                config.passwordSet && config.password != null && !config.password.isBlank());
    }

    private EmailConfigurationSnapshot environmentSnapshot() {
        SmtpSettings env = environmentSettings();
        if (env == null) {
            return new EmailConfigurationSnapshot(null, EmailProvider.SMTP, null, 587, null,
                    emailConfig.fromEmail(), emailConfig.fromName(), true, false,
                    emailConfig.connectionTimeoutMs(), false, false);
        }
        return EmailConfigurationSnapshot.from(env, true);
    }

    private SmtpSettings environmentSettings() {
        String host = envHost.map(String::trim).filter(h -> !h.isEmpty()).orElse(null);
        boolean sendGrid = "smtp.sendgrid.net".equalsIgnoreCase(String.valueOf(host));
        EmailProvider provider = sendGrid ? EmailProvider.SENDGRID : EmailProvider.SMTP;
        if (host == null) {
            return null;
        }
        int port = envPort.orElse(provider == EmailProvider.SENDGRID ? 587 : 587);
        String username = envUsername.map(String::trim).filter(s -> !s.isEmpty())
                .orElse(provider == EmailProvider.SENDGRID ? "apikey" : null);
        String password = envPassword.filter(s -> !s.isBlank()).orElse(null);
        String fromEmail = envFromEmail.map(String::trim).filter(s -> !s.isEmpty())
                .orElseGet(emailConfig::fromEmail);
        String fromName = envFromName.map(String::trim).filter(s -> !s.isEmpty())
                .orElseGet(emailConfig::fromName);
        redactor.register(password);
        return new SmtpSettings(provider, host, port, username, password, fromEmail, fromName,
                port != 465, port == 465, emailConfig.connectionTimeoutMs());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
