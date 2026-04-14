CREATE INDEX IF NOT EXISTS idx_long_term_memory_metadata_json
    ON long_term_memory USING GIN (metadata_json);

CREATE INDEX IF NOT EXISTS idx_memory_summary_metadata_json
    ON memory_summary USING GIN (metadata_json);
