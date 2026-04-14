package com.example.agentplatform.memory.repository;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.memory.domain.MemorySummary;
import com.example.agentplatform.memory.domain.RetrievedMemorySummary;
import com.example.agentplatform.memory.dto.MemoryMetadataFilter;
import com.example.agentplatform.memory.dto.MemorySummaryWriteRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 记忆摘要仓储。
 * 负责摘要写入以及按 user_id 过滤后的 pgvector 语义召回。
 */
@Repository
public class MemorySummaryRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MemorySummaryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 写入一条带向量的记忆摘要。 */
    public MemorySummary save(MemorySummaryWriteRequest request, float[] embedding) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO memory_summary (
                    user_id,
                    long_term_memory_id,
                    conversation_id,
                    summary_text,
                    embedding,
                    importance,
                    is_active,
                    source_type,
                    source_ref,
                    metadata_json
                )
                VALUES (?, ?, ?, ?, ?::vector, ?, ?, ?, ?, ?::jsonb)
                RETURNING id
                """,
                Long.class,
                request.userId(),
                request.longTermMemoryId(),
                request.conversationId(),
                request.summaryText(),
                toVectorLiteral(embedding),
                Optional.ofNullable(request.importance()).orElse(5),
                Optional.ofNullable(request.active()).orElse(Boolean.TRUE),
                request.sourceType(),
                request.sourceRef(),
                toJson(request.metadata()));

        if (id == null) {
            throw new ApplicationException("写入记忆摘要失败");
        }
        return findById(id).orElseThrow(() -> new ApplicationException("记忆摘要写入后查询失败"));
    }

    /** 按用户做向量语义召回。 */
    public List<RetrievedMemorySummary> semanticSearch(
            Long userId,
            float[] embedding,
            int topK,
            MemoryMetadataFilter metadataFilter
    ) {
        List<Object> arguments = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       user_id,
                       long_term_memory_id,
                       conversation_id,
                       summary_text,
                       importance,
                       is_active,
                       source_type,
                       source_ref,
                       metadata_json,
                       created_at,
                       updated_at,
                       1 - (embedding <=> ?::vector) AS score
                FROM memory_summary
                WHERE user_id = ?
                  AND is_active = TRUE
                """);
        arguments.add(toVectorLiteral(embedding));
        arguments.add(userId);
        appendMetadataFilter(sql, arguments, metadataFilter);
        // 显式补空格和换行，避免 metadata 过滤拼接后与 ORDER BY 粘连导致 SQL 语法错误。
        sql.append("\n ORDER BY embedding <=> ?::vector\n LIMIT ?\n");
        arguments.add(toVectorLiteral(embedding));
        arguments.add(topK);
        return jdbcTemplate.query(sql.toString(),
                (resultSet, rowNum) -> new RetrievedMemorySummary(
                        toMemorySummary(resultSet),
                        resultSet.getDouble("score")
                ),
                arguments.toArray());
    }

    /** 按主键查询一条记忆摘要。 */
    public Optional<MemorySummary> findById(Long id) {
        List<MemorySummary> results = jdbcTemplate.query("""
                SELECT id,
                       user_id,
                       long_term_memory_id,
                       conversation_id,
                       summary_text,
                       importance,
                       is_active,
                       source_type,
                       source_ref,
                       metadata_json,
                       created_at,
                       updated_at
                FROM memory_summary
                WHERE id = ?
                """, rowMapper(), id);
        return results.stream().findFirst();
    }

    private RowMapper<MemorySummary> rowMapper() {
        return (resultSet, rowNum) -> toMemorySummary(resultSet);
    }

    private MemorySummary toMemorySummary(ResultSet resultSet) throws SQLException {
        return new MemorySummary(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                resultSet.getLong("long_term_memory_id"),
                resultSet.getObject("conversation_id", Long.class),
                resultSet.getString("summary_text"),
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
            throw new ApplicationException("反序列化记忆摘要元数据失败", exception);
        }
    }

    private String toJson(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("序列化记忆摘要元数据失败", exception);
        }
    }

    private String toVectorLiteral(float[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.append(']').toString();
    }

    /**
     * 追加 metadata_json 固定字段过滤。
     * 当前先支持自动提炼相关的三个常用字段。
     */
    private void appendMetadataFilter(
            StringBuilder sql,
            List<Object> arguments,
            MemoryMetadataFilter metadataFilter
    ) {
        if (metadataFilter == null) {
            return;
        }
        if (metadataFilter.autoExtracted() != null) {
            sql.append(" AND metadata_json @> ?::jsonb");
            arguments.add("{\"autoExtracted\":" + metadataFilter.autoExtracted() + "}");
        }
        if (metadataFilter.triggerType() != null && !metadataFilter.triggerType().isBlank()) {
            sql.append(" AND metadata_json ->> 'triggerType' = ?");
            arguments.add(metadataFilter.triggerType().trim());
        }
        if (metadataFilter.assistantMessageId() != null) {
            sql.append(" AND (metadata_json ->> 'assistantMessageId')::bigint = ?");
            arguments.add(metadataFilter.assistantMessageId());
        }
    }
}
