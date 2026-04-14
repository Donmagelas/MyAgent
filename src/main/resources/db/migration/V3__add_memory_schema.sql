ALTER TABLE conversation
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES app_user (id);

CREATE INDEX IF NOT EXISTS idx_conversation_user_created_at
    ON conversation (user_id, created_at DESC);

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES app_user (id);

CREATE INDEX IF NOT EXISTS idx_chat_message_user_created_at
    ON chat_message (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS long_term_memory (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    conversation_id BIGINT REFERENCES conversation (id),
    memory_type VARCHAR(32) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    importance INTEGER NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    source_type VARCHAR(64),
    source_ref VARCHAR(128),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_long_term_memory_type
        CHECK (memory_type IN (
            'USER_PREFERENCE',
            'PROJECT_STATUS',
            'DESIGN_DECISION',
            'TASK_CONCLUSION',
            'STABLE_FACT'
        )),
    CONSTRAINT ck_long_term_memory_importance
        CHECK (importance BETWEEN 1 AND 10)
);

CREATE INDEX IF NOT EXISTS idx_long_term_memory_user_type_active
    ON long_term_memory (user_id, memory_type, is_active, importance DESC, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_long_term_memory_user_subject
    ON long_term_memory (user_id, subject);

CREATE INDEX IF NOT EXISTS idx_long_term_memory_conversation
    ON long_term_memory (conversation_id);

CREATE TABLE IF NOT EXISTS memory_summary (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    long_term_memory_id BIGINT NOT NULL REFERENCES long_term_memory (id) ON DELETE CASCADE,
    conversation_id BIGINT REFERENCES conversation (id),
    summary_text TEXT NOT NULL,
    embedding VECTOR(${vector_dimensions}),
    importance INTEGER NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    source_type VARCHAR(64),
    source_ref VARCHAR(128),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_memory_summary_importance
        CHECK (importance BETWEEN 1 AND 10)
);

CREATE INDEX IF NOT EXISTS idx_memory_summary_user_active
    ON memory_summary (user_id, is_active, importance DESC, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_memory_summary_long_term_memory
    ON memory_summary (long_term_memory_id);

CREATE INDEX IF NOT EXISTS idx_memory_summary_conversation
    ON memory_summary (conversation_id);

CREATE INDEX IF NOT EXISTS idx_memory_summary_embedding_hnsw
    ON memory_summary USING HNSW (embedding vector_cosine_ops);
