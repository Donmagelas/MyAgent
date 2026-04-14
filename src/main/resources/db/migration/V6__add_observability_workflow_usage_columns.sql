ALTER TABLE model_usage_log
    ADD COLUMN IF NOT EXISTS workflow_id BIGINT NULL REFERENCES workflow_instance(id) ON DELETE SET NULL;

ALTER TABLE model_usage_log
    ADD COLUMN IF NOT EXISTS task_id BIGINT NULL REFERENCES task_record(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_model_usage_log_workflow_created_at
    ON model_usage_log (workflow_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_model_usage_log_task_created_at
    ON model_usage_log (task_id, created_at DESC);
