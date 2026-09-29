-- =====================================================================
-- V4 - Background jobs and per-recipient delivery tracking
-- =====================================================================
CREATE TABLE job (
    id              BIGSERIAL      PRIMARY KEY,
    campaign_id     BIGINT         NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    total_count     INTEGER        NOT NULL DEFAULT 0,
    success_count   INTEGER        NOT NULL DEFAULT 0,
    failed_count    INTEGER        NOT NULL DEFAULT 0,
    processed_count INTEGER        NOT NULL DEFAULT 0,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    cancelled_at    TIMESTAMP,
    error_message   VARCHAR(2000),
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL,
    CONSTRAINT fk_job_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign (id) ON DELETE CASCADE,
    CONSTRAINT ck_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'))
);

CREATE INDEX idx_job_status ON job (status);
CREATE INDEX idx_job_campaign_id ON job (campaign_id);

-- One row per recipient of a job: this is what makes it possible to know
-- exactly which address succeeded and which one failed.
CREATE TABLE job_recipients (
    id            BIGSERIAL      PRIMARY KEY,
    job_id        BIGINT         NOT NULL,
    recipient_id  BIGINT         NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER        NOT NULL DEFAULT 0,
    sent_at       TIMESTAMP,
    failed_at     TIMESTAMP,
    error_message VARCHAR(2000),
    created_at    TIMESTAMP      NOT NULL,
    updated_at    TIMESTAMP      NOT NULL,
    CONSTRAINT fk_job_recipients_job
        FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_recipients_recipient
        FOREIGN KEY (recipient_id) REFERENCES recipient (id) ON DELETE CASCADE,
    CONSTRAINT uq_job_recipients_job_recipient UNIQUE (job_id, recipient_id),
    CONSTRAINT ck_job_recipients_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_job_recipients_job_id ON job_recipients (job_id);
CREATE INDEX idx_job_recipients_status ON job_recipients (status);
CREATE INDEX idx_job_recipients_recipient_id ON job_recipients (recipient_id);
