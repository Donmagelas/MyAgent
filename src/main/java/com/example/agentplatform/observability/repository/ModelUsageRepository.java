package com.example.agentplatform.observability.repository;

import com.example.agentplatform.observability.domain.ModelUsageRecord;
import com.example.agentplatform.observability.dto.ModelUsageLogEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 模型 usage 持久化仓储。
 * 负责写入步骤级 usage，并支持按工作流查询明细，供执行可视化聚合使用。
 */
@Repository
public class ModelUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 持久化一条 usage 记录。
     */
    public void save(ModelUsageRecord record) {
        jdbcTemplate.update("""
                INSERT INTO model_usage_log (
                    workflow_id, task_id, conversation_id, message_id, request_id, step_name, model_name, provider,
                    prompt_tokens, completion_tokens, total_tokens, latency_ms, success, error_message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.workflowId(),
                record.taskId(),
                record.conversationId(),
                record.messageId(),
                record.requestId(),
                record.stepName(),
                record.modelName(),
                record.provider(),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                record.latencyMs(),
                record.success(),
                record.errorMessage());
    }

    /**
     * 查询某个工作流下的 usage 明细。
     */
    public List<ModelUsageLogEntry> findByWorkflowId(Long workflowId) {
        return jdbcTemplate.query("""
                        SELECT id,
                               workflow_id,
                               task_id,
                               conversation_id,
                               message_id,
                               request_id,
                               step_name,
                               model_name,
                               provider,
                               prompt_tokens,
                               completion_tokens,
                               total_tokens,
                               latency_ms,
                               success,
                               error_message,
                               created_at
                        FROM model_usage_log
                        WHERE workflow_id = ?
                        ORDER BY created_at ASC, id ASC
                        """,
                (resultSet, rowNum) -> new ModelUsageLogEntry(
                        resultSet.getLong("id"),
                        resultSet.getObject("workflow_id", Long.class),
                        resultSet.getObject("task_id", Long.class),
                        resultSet.getObject("conversation_id", Long.class),
                        resultSet.getObject("message_id", Long.class),
                        resultSet.getString("request_id"),
                        resultSet.getString("step_name"),
                        resultSet.getString("model_name"),
                        resultSet.getString("provider"),
                        resultSet.getObject("prompt_tokens", Integer.class),
                        resultSet.getObject("completion_tokens", Integer.class),
                        resultSet.getObject("total_tokens", Integer.class),
                        resultSet.getObject("latency_ms", Long.class),
                        resultSet.getBoolean("success"),
                        resultSet.getString("error_message"),
                        resultSet.getObject("created_at", OffsetDateTime.class)
                ),
                workflowId);
    }
}
