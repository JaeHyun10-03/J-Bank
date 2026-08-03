CREATE TABLE audit_log (
    log_id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id VARCHAR(50),
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(50) NOT NULL,
    detail JSONB,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id);
