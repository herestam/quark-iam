-- =====================================================================
-- V1 - Email templates
-- =====================================================================
CREATE TABLE email_template (
    id            BIGSERIAL     PRIMARY KEY,
    name          VARCHAR(200)  NOT NULL,
    subject       VARCHAR(500)  NOT NULL,
    html_content  VARCHAR(32000) NOT NULL,
    text_content  VARCHAR(32000),
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL
);

CREATE INDEX idx_email_template_name ON email_template (name);
