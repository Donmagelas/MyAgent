package com.example.agentplatform.memory.repository;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.memory.domain.LongTermMemory;
import com.example.agentplatform.memory.domain.MemoryType;
import com.example.agentplatform.memory.dto.MemoryMetadataFilter;
import com.example.agentplatform.memory.dto.LongTermMemoryQueryRequest;
import com.example.agentplatform.memory.dto.LongTermMemoryWriteRequest;
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
 * 长期记忆仓储。
 * 负责长期记忆的写入、按类型查询和按主题过滤。
 */
@Repository
public class LongTermMemoryRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    public LongTermMemoryRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 写入一条长期记忆。 */
    public LongTermMemory save(LongTermMemoryWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", request.userId())
                .addValue("conversationId", request.conversationId())
                .addValue("memoryType", request.memoryType().name())
                .addValue("subject", request.subject())
                .addValue("content", request.content())
                .addValue("importance", Optional.ofNullable(request.importance()).orElse(5))
                .addValue("active", Optional.ofNullable(request.active()).orElse(Boolean.TRUE))
                .addValue("sourceType", request.sourceType())
                .addValue("sourceRef", request.sourceRef())
                .addValue("metadataJson", toJson(request.metadata()));

        Long id = namedParameterJdbcTemplate.queryForObject("""
                INSERT INTO long_term_memory (
                    user_id,
                    conversation_id,
                    memory_type,
                    subject,
                    content,
                    importance,
                    is_active,
                    source_type,
                    source_ref,
                    metadata_json
                )
                VALUES (
                    :userId,
                    :conversationId,
                    :memoryType,
                    :subject,
                    :content,
                    :importance,
                    :active,
                    :sourceType,
                    :sourceRef,
                    CAST(:metadataJson AS jsonb)
                )
                RETURNING id
                """, parameters, Long.class);

        if (id == null) {
            throw new ApplicationException("写入长期记忆失败");
        }
        return findById(id).orElseThrow(() -> new ApplicationException("长期记忆写入后查询失败"));
    }

    /** 按查询条件返回生效中的长期记忆。 */
    public List<LongTermMemory> query(LongTermMemoryQueryRequest request) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       user_id,
                       conversation_id,
                       memory_type,
                       subject,
                       content,
                       importance,
                       is_active,
                       source_type,
                       source_ref,
                       metadata_json,
                       created_at,
                       updated_at
                FROM long_term_memory
                WHERE user_id = :userId
                """);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", request.userId())
                .addValue("limit", Optional.ofNullable(request.limit()).orElse(10));

        if (request.active() != null) {
            sql.append(" AND is_active = :active");
            parameters.addValue("active", request.active());
        }
        if (request.minImportance() != null) {
            sql.append(" AND importance >= :minImportance");
            parameters.addValue("minImportance", request.minImportance());
        }
        if (request.subject() != null && !request.subject().isBlank()) {
            sql.append(" AND subject ILIKE :subject");
            parameters.addValue("subject", "%" + request.subject().trim() + "%");
        }
        if (request.memoryTypes() != null && !request.memoryTypes().isEmpty()) {
            sql.append(" AND memory_type IN (:memoryTypes)");
            parameters.addValue("memoryTypes", request.memoryTypes().stream().map(MemoryType::name).toList());
        }
        appendMetadataFilter(sql, parameters, request.metadataFilter());

        sql.append(" ORDER BY importance DESC, updated_at DESC, id DESC LIMIT :limit");
        return namedParameterJdbcTemplate.query(sql.toString(), parameters, rowMapper());
    }

    /** 按主键查询一条长期记忆。 */
    public Optional<LongTermMemory> findById(Long id) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", id);
        List<LongTermMemory> results = namedParameterJdbcTemplate.query("""
                SELECT id,
                       user_id,
                       conversation_id,
                       memory_type,
                       subject,
                       content,
                       importance,
                       is_active,
                       source_type,
                       source_ref,
                       metadata_json,
                       created_at,
                       updated_at
                FROM long_term_memory
                WHERE id = :id
                """, parameters, rowMapper());
        return results.stream().findFirst();
    }

    /**
     * 检查是否已经存在同类型、同主题、同内容的生效长期记忆。
     * 自动提炼场景下先做精确去重，避免重复写入。
     */
    public boolean existsActiveDuplicate(Long userId, MemoryType memoryType, String subject, String content) {
        Integer count = namedParameterJdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM long_term_memory
                WHERE user_id = :userId
                  AND memory_type = :memoryType
                  AND is_active = TRUE
                  AND subject = :subject
                  AND content = :content
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("memoryType", memoryType.name())
                        .addValue("subject", subject)
                        .addValue("content", content),
                Integer.class);
        return count != null && count > 0;
    }

    private RowMapper<LongTermMemory> rowMapper() {
        return (resultSet, rowNum) -> new LongTermMemory(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                resultSet.getObject("conversation_id", Long.class),
                MemoryType.valueOf(resultSet.getString("memory_type")),
                resultSet.getString("subject"),
                resultSet.getString("content"),
                resultSet.getInt("importance"),
                resultSet.getBoolean("is_active"),
                resultSet.getString("source_type"),
                resultSet.getString("source_ref"),
                readMetadata(resultSet),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private Map<String, Object> readMetadata(ResultSet resultSet) throws SQLException {
        try {
            return objectMapper.readValue(resultSet.getString("metadata_json"), MAP_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("反序列化长期记忆元数据失败", exception);
        }
    }

    private String toJson(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("序列化长期记忆元数据失败", exception);
        }
    }

    /**
     * 追加 metadata_json 固定字段过滤。
     * 当前先支持自动提炼相关的三个常用字段。
     */
    private void appendMetadataFilter(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            MemoryMetadataFilter metadataFilter
    ) {
        if (metadataFilter == null) {
            return;
        }
        if (metadataFilter.autoExtracted() != null) {
            sql.append(" AND metadata_json @> CAST(:metadataAutoExtracted AS jsonb)");
            parameters.addValue("metadataAutoExtracted",
                    "{\"autoExtracted\":" + metadataFilter.autoExtracted() + "}");
        }
        if (metadataFilter.triggerType() != null && !metadataFilter.triggerType().isBlank()) {
            sql.append(" AND metadata_json ->> 'triggerType' = :metadataTriggerType");
            parameters.addValue("metadataTriggerType", metadataFilter.triggerType().trim());
        }
        if (metadataFilter.assistantMessageId() != null) {
            sql.append(" AND (metadata_json ->> 'assistantMessageId')::bigint = :metadataAssistantMessageId");
            parameters.addValue("metadataAssistantMessageId", metadataFilter.assistantMessageId());
        }
    }
}
