CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS conversation (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL UNIQUE,
    title VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversation (id),
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    model_name VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_created_at
    ON chat_message (conversation_id, created_at);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    document_code VARCHAR(128) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_uri VARCHAR(1024),
    status VARCHAR(32) NOT NULL DEFAULT 'IMPORTED',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES knowledge_document (id),
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_tsv TSVECTOR,
    embedding VECTOR(${vector_dimensions}),
    token_count INTEGER,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledge_chunk_document_idx UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_document_chunk
    ON knowledge_chunk (document_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_content_tsv
    ON knowledge_chunk USING GIN (content_tsv);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding_hnsw
    ON knowledge_chunk USING HNSW (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS model_usage_log (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversation (id),
    message_id BIGINT REFERENCES chat_message (id),
    request_id VARCHAR(128),
    step_name VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms BIGINT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_model_usage_log_conversation_created_at
    ON model_usage_log (conversation_id, created_at);
