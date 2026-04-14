CREATE TABLE IF NOT EXISTS tool_definition (
    id BIGSERIAL PRIMARY KEY,
    tool_name VARCHAR(128) NOT NULL UNIQUE,
    implementation_key VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    input_schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    read_only BOOLEAN NOT NULL DEFAULT TRUE,
    mutates_state BOOLEAN NOT NULL DEFAULT FALSE,
    dangerous BOOLEAN NOT NULL DEFAULT FALSE,
    return_direct BOOLEAN NOT NULL DEFAULT FALSE,
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
    timeout_ms BIGINT NOT NULL DEFAULT 15000,
    risk_level VARCHAR(32) NOT NULL,
    allowed_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_definition_enabled
    ON tool_definition (enabled);

CREATE INDEX IF NOT EXISTS idx_tool_definition_updated_at
    ON tool_definition (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_tool_definition_roles_gin
    ON tool_definition
    USING GIN (allowed_roles);

CREATE INDEX IF NOT EXISTS idx_tool_definition_tags_gin
    ON tool_definition
    USING GIN (tags);
