-- =====================================================================
-- V5 - Email / SMTP configuration
-- The password column is write-only: it is never returned by the API,
-- never rendered in the UI and never written to the log.
-- =====================================================================
CREATE TABLE email_configuration (
    id                    BIGSERIAL     PRIMARY KEY,
    provider              VARCHAR(20)   NOT NULL DEFAULT 'SMTP',
    smtp_host             VARCHAR(255)  NOT NULL,
    smtp_port             INTEGER       NOT NULL DEFAULT 587,
    username              VARCHAR(255),
    password              VARCHAR(1000),
    from_email            VARCHAR(320)  NOT NULL,
    from_name             VARCHAR(200),
    use_tls               BOOLEAN       NOT NULL DEFAULT TRUE,
    use_ssl               BOOLEAN       NOT NULL DEFAULT FALSE,
    connection_timeout_ms INTEGER       NOT NULL DEFAULT 10000,
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Lets the UI show "a password is stored" without ever returning it.
    password_set          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP     NOT NULL,
    updated_at            TIMESTAMP     NOT NULL,
    CONSTRAINT ck_email_configuration_provider
        CHECK (provider IN ('SMTP', 'SENDGRID')),
    CONSTRAINT ck_email_configuration_port
        CHECK (smtp_port > 0)
);

-- Only a single configuration may be active at a time.
CREATE UNIQUE INDEX uq_email_configuration_active
    ON email_configuration (active)
    WHERE active;
