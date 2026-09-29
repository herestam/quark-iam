package com.example.emailapp.email.entity;

import com.example.emailapp.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * SMTP settings for the outbound mail provider.
 *
 * <p>{@code password} is write-only. It is never returned by a DTO, never
 * rendered by a template and never logged; {@link #passwordSet} is what the UI
 * uses to tell the administrator that a secret is stored.</p>
 */
@Entity
@Table(name = "email_configuration")
public class EmailConfiguration extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    public EmailProvider provider = EmailProvider.SMTP;

    @Column(name = "smtp_host", nullable = false, length = 255)
    public String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    public int smtpPort = 587;

    @Column(name = "username", length = 255)
    public String username;

    @Column(name = "password", length = 1000)
    public String password;

    @Column(name = "from_email", nullable = false, length = 320)
    public String fromEmail;

    @Column(name = "from_name", length = 200)
    public String fromName;

    @Column(name = "use_tls", nullable = false)
    public boolean useTls = true;

    @Column(name = "use_ssl", nullable = false)
    public boolean useSsl;

    @Column(name = "connection_timeout_ms", nullable = false)
    public int connectionTimeoutMs = 10_000;

    @Column(name = "active", nullable = false)
    public boolean active = true;

    /** True when a secret is stored. Replaces exposing the secret itself. */
    @Column(name = "password_set", nullable = false)
    public boolean passwordSet;

    @Override
    public String toString() {
        // Deliberately never includes the password.
        return "EmailConfiguration{id=" + id + ", provider=" + provider
                + ", host=" + smtpHost + ":" + smtpPort + ", active=" + active + "}";
    }
}
