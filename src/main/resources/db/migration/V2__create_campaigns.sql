-- =====================================================================
-- V2 - Campaigns
-- =====================================================================
CREATE TABLE campaign (
    id          BIGSERIAL     PRIMARY KEY,
    name        VARCHAR(200)  NOT NULL,
    subject     VARCHAR(500)  NOT NULL,
    template_id BIGINT        NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    CONSTRAINT fk_campaign_template
        FOREIGN KEY (template_id) REFERENCES email_template (id) ON DELETE RESTRICT,
    CONSTRAINT ck_campaign_status
        CHECK (status IN ('DRAFT', 'READY', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'))
);

CREATE INDEX idx_campaign_status ON campaign (status);
CREATE INDEX idx_campaign_template_id ON campaign (template_id);
