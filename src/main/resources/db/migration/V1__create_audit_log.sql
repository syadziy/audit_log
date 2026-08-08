CREATE TABLE audit_log (
    event_id UUID PRIMARY KEY,
    source_system VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    actor_id VARCHAR(150) NOT NULL,
    actor_name VARCHAR(200),
    action VARCHAR(150) NOT NULL,
    resource_type VARCHAR(150) NOT NULL,
    resource_id VARCHAR(200),
    outcome VARCHAR(30) NOT NULL,
    trace_id VARCHAR(150),
    client_ip VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_audit_log_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED', 'UNKNOWN'))
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_actor_time ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_log_resource ON audit_log (resource_type, resource_id, occurred_at DESC);
CREATE INDEX idx_audit_log_action_time ON audit_log (action, occurred_at DESC);
