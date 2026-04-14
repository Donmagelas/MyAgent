package com.example.agentplatform.workflow.repository;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.domain.WorkflowStatus;
import com.example.agentplatform.workflow.dto.WorkflowCreateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工作流仓储。
 * 负责工作流实例的创建、查询和状态汇总更新。
 */
@Repository
public class WorkflowRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 创建工作流实例。 */
    public WorkflowRecord save(Long userId, WorkflowCreateRequest request) {
        Long id = namedParameterJdbcTemplate.queryForObject("""
                INSERT INTO workflow_instance (
                    user_id,
                    name,
                    description,
                    status,
                    input_json,
                    fail_fast,
                    metadata_json
                )
                VALUES (
                    :userId,
                    :name,
                    :description,
                    :status,
                    CAST(:inputJson AS jsonb),
                    :failFast,
                    CAST(:metadataJson AS jsonb)
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("name", request.name())
                        .addValue("description", request.description())
                        .addValue("status", WorkflowStatus.PENDING.name())
                        .addValue("inputJson", toJson(request.input()))
                        .addValue("failFast", request.failFast() == null || request.failFast())
                        .addValue("metadataJson", toJson(request.metadata())),
                Long.class);
        if (id == null) {
            throw new ApplicationException("创建工作流失败");
        }
        return findById(userId, id).orElseThrow(() -> new ApplicationException("工作流创建后查询失败"));
    }

    /** 按用户和工作流主键查询工作流。 */
    public Optional<WorkflowRecord> findById(Long userId, Long workflowId) {
        List<WorkflowRecord> workflows = namedParameterJdbcTemplate.query("""
                SELECT id,
                       user_id,
                       name,
                       description,
                       status,
                       input_json,
                       result_json,
                       error_message,
                       fail_fast,
                       metadata_json,
                       created_at,
                       updated_at,
                       started_at,
                       completed_at
                FROM workflow_instance
                WHERE id = :workflowId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("workflowId", workflowId)
                        .addValue("userId", userId),
                rowMapper());
        return workflows.stream().findFirst();
    }

    /** 更新工作流状态与输出。 */
    public WorkflowRecord updateStatus(
            Long userId,
            Long workflowId,
            WorkflowStatus status,
            Map<String, Object> result,
            String errorMessage
    ) {
        namedParameterJdbcTemplate.update("""
                UPDATE workflow_instance
                SET status = :status,
                    result_json = CASE
                        WHEN :resultJson IS NULL THEN result_json
                        ELSE CAST(:resultJson AS jsonb)
                    END,
                    error_message = :errorMessage,
                    updated_at = clock_timestamp(),
                    started_at = CASE
                        WHEN :status = 'RUNNING' AND started_at IS NULL THEN clock_timestamp()
                        ELSE started_at
                    END,
                    completed_at = CASE
                        WHEN :status IN ('COMPLETED', 'FAILED', 'CANCELED') THEN clock_timestamp()
                        ELSE completed_at
                    END
                WHERE id = :workflowId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("workflowId", workflowId)
                        .addValue("userId", userId)
                        .addValue("status", status.name())
                        .addValue("resultJson", result == null ? null : toJson(result))
                        .addValue("errorMessage", errorMessage));
        return findById(userId, workflowId).orElseThrow(() -> new ApplicationException("工作流状态更新后查询失败"));
    }

    private RowMapper<WorkflowRecord> rowMapper() {
        return (resultSet, rowNum) -> new WorkflowRecord(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                WorkflowStatus.valueOf(resultSet.getString("status")),
                readJson(resultSet, "input_json"),
                readJson(resultSet, "result_json"),
                resultSet.getString("error_message"),
                resultSet.getBoolean("fail_fast"),
                readJson(resultSet, "metadata_json"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class)
        );
    }

    private Map<String, Object> readJson(ResultSet resultSet, String columnName) throws SQLException {
        String json = resultSet.getString(columnName);
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("工作流 JSON 字段反序列化失败", exception);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("工作流 JSON 字段序列化失败", exception);
        }
    }
}
