-- =====================================================================
-- V3 - Recipients and the campaign/recipient association
-- =====================================================================
CREATE TABLE recipient (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(320) NOT NULL,
    name       VARCHAR(200),
    company    VARCHAR(200),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    -- The unique constraint doubles as the lookup index on recipient.email
    CONSTRAINT uq_recipient_email UNIQUE (email)
);

-- A campaign targets many recipients.
CREATE TABLE campaign_recipient (
    campaign_id  BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    CONSTRAINT pk_campaign_recipient PRIMARY KEY (campaign_id, recipient_id),
    CONSTRAINT fk_campaign_recipient_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_recipient_recipient
        FOREIGN KEY (recipient_id) REFERENCES recipient (id) ON DELETE CASCADE
);

CREATE INDEX idx_campaign_recipient_recipient_id ON campaign_recipient (recipient_id);
