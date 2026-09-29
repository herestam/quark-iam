-- =====================================================================
-- V6 - Job logs
-- =====================================================================
CREATE TABLE job_logs (
    id         BIGSERIAL    PRIMARY KEY,
    job_id     BIGINT       NOT NULL,
    level      VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    message    VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT fk_job_logs_job
        FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE,
    CONSTRAINT ck_job_logs_level
        CHECK (level IN ('INFO', 'WARN', 'ERROR'))
);

CREATE INDEX idx_job_logs_job_id ON job_logs (job_id);
CREATE INDEX idx_job_logs_job_id_created_at ON job_logs (job_id, created_at);
