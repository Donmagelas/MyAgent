package com.example.agentplatform.tasks.repository;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.tasks.dto.TaskCreateRequest;
import com.example.agentplatform.tasks.dto.TaskStatusUpdateRequest;
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
 * 任务仓储。
 * 负责任务实体的持久化、查询与状态更新。
 */
@Repository
public class TaskRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TaskDependencyRepository taskDependencyRepository;

    public TaskRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            ObjectMapper objectMapper,
            TaskDependencyRepository taskDependencyRepository
    ) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
        this.taskDependencyRepository = taskDependencyRepository;
    }

    /**
     * 创建一个任务记录。
     */
    public TaskRecord save(Long userId, TaskCreateRequest request, TaskStatus initialStatus) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("workflowId", request.workflowId())
                .addValue("parentTaskId", request.parentTaskId())
                .addValue("userId", userId)
                .addValue("clientTaskKey", request.clientTaskKey())
                .addValue("name", request.name())
                .addValue("description", request.description())
                .addValue("taskType", request.taskType())
                .addValue("status", initialStatus.name())
                .addValue("inputJson", toJson(request.input()))
                .addValue("maxRetries", request.maxRetries() == null ? 0 : request.maxRetries())
                .addValue("sourceType", request.sourceType())
                .addValue("sourceRef", request.sourceRef())
                .addValue("metadataJson", toJson(request.metadata()));

        Long id = namedParameterJdbcTemplate.queryForObject("""
                INSERT INTO task_record (
                    workflow_id,
                    parent_task_id,
                    user_id,
                    client_task_key,
                    name,
                    description,
                    task_type,
                    status,
                    input_json,
                    max_retries,
                    source_type,
                    source_ref,
                    metadata_json
                )
                VALUES (
                    :workflowId,
                    :parentTaskId,
                    :userId,
                    :clientTaskKey,
                    :name,
                    :description,
                    :taskType,
                    :status,
                    CAST(:inputJson AS jsonb),
                    :maxRetries,
                    :sourceType,
                    :sourceRef,
                    CAST(:metadataJson AS jsonb)
                )
                RETURNING id
                """, parameters, Long.class);

        if (id == null) {
            throw new ApplicationException("创建任务失败");
        }
        taskDependencyRepository.saveAll(id, request.blockedByTaskIds());
        return findById(userId, id).orElseThrow(() -> new ApplicationException("任务创建后查询失败"));
    }

    /**
     * 按用户和任务主键查询任务。
     */
    public Optional<TaskRecord> findById(Long userId, Long taskId) {
        List<TaskRecord> records = namedParameterJdbcTemplate.query("""
                SELECT id,
                       workflow_id,
                       parent_task_id,
                       user_id,
                       client_task_key,
                       name,
                       description,
                       task_type,
                       status,
                       progress,
                       input_json,
                       result_json,
                       error_message,
                       retry_count,
                       max_retries,
                       cancel_requested,
                       source_type,
                       source_ref,
                       metadata_json,
                       created_at,
                       updated_at,
                       started_at,
                       completed_at
                FROM task_record
                WHERE id = :taskId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("taskId", taskId)
                        .addValue("userId", userId),
                rowMapper());
        return records.stream().findFirst().map(this::withDependencies);
    }

    /**
     * 查询某个工作流下的全部任务。
     */
    public List<TaskRecord> findByWorkflowId(Long userId, Long workflowId) {
        List<TaskRecord> tasks = namedParameterJdbcTemplate.query("""
                SELECT id,
                       workflow_id,
                       parent_task_id,
                       user_id,
                       client_task_key,
                       name,
                       description,
                       task_type,
                       status,
                       progress,
                       input_json,
                       result_json,
                       error_message,
                       retry_count,
                       max_retries,
                       cancel_requested,
                       source_type,
                       source_ref,
                       metadata_json,
                       created_at,
                       updated_at,
                       started_at,
                       completed_at
                FROM task_record
                WHERE workflow_id = :workflowId
                  AND user_id = :userId
                ORDER BY created_at ASC, id ASC
                """,
                new MapSqlParameterSource()
                        .addValue("workflowId", workflowId)
                        .addValue("userId", userId),
                rowMapper());
        return tasks.stream().map(this::withDependencies).toList();
    }

    /**
     * 更新任务状态。
     */
    public TaskRecord updateStatus(Long userId, Long taskId, TaskStatusUpdateRequest request) {
        boolean resultJsonProvided = request.result() != null;
        boolean increaseRetry = request.status() == TaskStatus.FAILED;
        boolean markStarted = request.status() == TaskStatus.RUNNING;
        boolean markCompleted = request.status() == TaskStatus.COMPLETED
                || request.status() == TaskStatus.FAILED
                || request.status() == TaskStatus.CANCELED;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("userId", userId)
                .addValue("status", request.status().name())
                .addValue("progress", request.progress() == null ? null : request.progress())
                .addValue("resultJson", request.result() == null ? null : toJson(request.result()))
                .addValue("resultJsonProvided", resultJsonProvided)
                .addValue("errorMessage", request.errorMessage())
                .addValue("increaseRetry", increaseRetry)
                .addValue("markStarted", markStarted)
                .addValue("markCompleted", markCompleted);

        namedParameterJdbcTemplate.update("""
                UPDATE task_record
                SET status = :status,
                    progress = COALESCE(:progress, progress),
                    result_json = CASE
                        WHEN :resultJsonProvided = FALSE THEN result_json
                        ELSE CAST(:resultJson AS jsonb)
                    END,
                    error_message = :errorMessage,
                    retry_count = CASE
                        WHEN :increaseRetry THEN retry_count + 1
                        ELSE retry_count
                    END,
                    updated_at = clock_timestamp(),
                    started_at = CASE
                        WHEN :markStarted AND started_at IS NULL THEN clock_timestamp()
                        ELSE started_at
                    END,
                    completed_at = CASE
                        WHEN :markCompleted THEN clock_timestamp()
                        ELSE completed_at
                    END
                WHERE id = :taskId
                  AND user_id = :userId
                """, parameters);

        return findById(userId, taskId).orElseThrow(() -> new ApplicationException("任务状态更新后查询失败"));
    }

    /**
     * 标记任务为已请求取消。
     */
    public TaskRecord requestCancel(Long userId, Long taskId) {
        namedParameterJdbcTemplate.update("""
                UPDATE task_record
                SET cancel_requested = TRUE,
                    updated_at = clock_timestamp()
                WHERE id = :taskId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("taskId", taskId)
                        .addValue("userId", userId));
        return findById(userId, taskId).orElseThrow(() -> new ApplicationException("任务取消标记后查询失败"));
    }

    /**
     * 把阻塞中的任务切换成可执行状态。
     */
    public void markReady(Long taskId) {
        namedParameterJdbcTemplate.update("""
                UPDATE task_record
                SET status = 'READY',
                    updated_at = clock_timestamp()
                WHERE id = :taskId
                  AND status = 'BLOCKED'
                """,
                new MapSqlParameterSource("taskId", taskId));
    }

    private TaskRecord withDependencies(TaskRecord taskRecord) {
        return new TaskRecord(
                taskRecord.id(),
                taskRecord.workflowId(),
                taskRecord.parentTaskId(),
                taskRecord.userId(),
                taskRecord.clientTaskKey(),
                taskRecord.name(),
                taskRecord.description(),
                taskRecord.taskType(),
                taskRecord.status(),
                taskRecord.progress(),
                taskRecord.input(),
                taskRecord.result(),
                taskRecord.errorMessage(),
                taskRecord.retryCount(),
                taskRecord.maxRetries(),
                taskRecord.cancelRequested(),
                taskRecord.sourceType(),
                taskRecord.sourceRef(),
                taskRecord.metadata(),
                taskDependencyRepository.findBlockedByTaskIds(taskRecord.id()),
                taskRecord.createdAt(),
                taskRecord.updatedAt(),
                taskRecord.startedAt(),
                taskRecord.completedAt()
        );
    }

    private RowMapper<TaskRecord> rowMapper() {
        return (resultSet, rowNum) -> new TaskRecord(
                resultSet.getLong("id"),
                resultSet.getObject("workflow_id", Long.class),
                resultSet.getObject("parent_task_id", Long.class),
                resultSet.getLong("user_id"),
                resultSet.getString("client_task_key"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("task_type"),
                TaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("progress"),
                readJson(resultSet, "input_json"),
                readJson(resultSet, "result_json"),
                resultSet.getString("error_message"),
                resultSet.getInt("retry_count"),
                resultSet.getInt("max_retries"),
                resultSet.getBoolean("cancel_requested"),
                resultSet.getString("source_type"),
                resultSet.getString("source_ref"),
                readJson(resultSet, "metadata_json"),
                List.of(),
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
            throw new ApplicationException("任务 JSON 字段反序列化失败", exception);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("任务 JSON 字段序列化失败", exception);
        }
    }
}
