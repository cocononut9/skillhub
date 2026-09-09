CREATE TABLE skill_usage_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL,
    client VARCHAR(32) NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_usage_event_event_id UNIQUE (event_id),
    CONSTRAINT ck_skill_usage_event_client CHECK (client = 'CODEX'),
    CONSTRAINT ck_skill_usage_event_evidence
        CHECK (evidence_type IN ('EXPLICIT_INVOCATION', 'SCRIPT_EXECUTED'))
);

CREATE INDEX idx_skill_usage_event_skill_occurred
    ON skill_usage_event (skill_id, occurred_at DESC);

CREATE INDEX idx_skill_usage_event_skill_user_occurred
    ON skill_usage_event (skill_id, user_id, occurred_at DESC);
