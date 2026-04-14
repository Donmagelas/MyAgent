CREATE TABLE IF NOT EXISTS workflow_instance (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL,
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB NULL,
    error_message TEXT NULL,
    fail_fast BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS task_record (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    parent_task_id BIGINT NULL REFERENCES task_record(id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    client_task_key VARCHAR(128) NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB NULL,
    error_message TEXT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 0,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    source_type VARCHAR(64) NULL,
    source_ref VARCHAR(128) NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_task_progress CHECK (progress >= 0 AND progress <= 100),
    CONSTRAINT chk_task_retry CHECK (retry_count >= 0 AND max_retries >= 0)
);

CREATE TABLE IF NOT EXISTS task_dependency (
    task_id BIGINT NOT NULL REFERENCES task_record(id) ON DELETE CASCADE,
    blocked_by_task_id BIGINT NOT NULL REFERENCES task_record(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, blocked_by_task_id),
    CONSTRAINT chk_task_dependency_not_self CHECK (task_id <> blocked_by_task_id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_instance_user_status
    ON workflow_instance (user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_record_workflow_status
    ON task_record (workflow_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_record_user_status
    ON task_record (user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_record_parent
    ON task_record (parent_task_id);

CREATE INDEX IF NOT EXISTS idx_task_dependency_blocked_by
    ON task_dependency (blocked_by_task_id);
