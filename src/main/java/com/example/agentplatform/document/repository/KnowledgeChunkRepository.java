package com.example.agentplatform.document.repository;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.document.domain.ChunkSearchTextBuilder;
import com.example.agentplatform.document.domain.KnowledgeChunk;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 知识分片仓储。
 * 同时负责持久化以及向量检索、关键词检索 SQL。
 */
@Repository
public class KnowledgeChunkRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeChunkRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 持久化一次文档导入产生的全部 chunk。 */
    public List<KnowledgeChunk> saveAll(List<KnowledgeChunk> chunks) {
        java.util.ArrayList<KnowledgeChunk> persistedChunks = new java.util.ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            String ftsSearchText = ChunkSearchTextBuilder.build(chunk.content(), chunk.metadata());
            if (chunk.embedding() == null) {
                Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO knowledge_chunk (document_id, chunk_index, content, content_tsv, token_count, metadata_json)
                        VALUES (?, ?, ?, to_tsvector('simple', ?), ?, ?::jsonb)
                        RETURNING id
                        """,
                        Long.class,
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        ftsSearchText,
                        chunk.tokenCount(),
                        toJson(chunk.metadata()));
                persistedChunks.add(new KnowledgeChunk(
                        id,
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        null,
                        chunk.tokenCount(),
                        chunk.metadata(),
                        chunk.createdAt()
                ));
            }
            else {
                Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO knowledge_chunk (document_id, chunk_index, content, content_tsv, embedding, token_count, metadata_json)
                        VALUES (?, ?, ?, to_tsvector('simple', ?), ?::vector, ?, ?::jsonb)
                        RETURNING id
                        """,
                        Long.class,
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        ftsSearchText,
                        toVectorLiteral(chunk.embedding()),
                        chunk.tokenCount(),
                        toJson(chunk.metadata()));
                persistedChunks.add(new KnowledgeChunk(
                        id,
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        chunk.embedding(),
                        chunk.tokenCount(),
                        chunk.metadata(),
                        chunk.createdAt()
                ));
            }
        }
        return persistedChunks;
    }

    /** 执行 pgvector 相似度检索并返回最匹配的 chunk。 */
    public List<RetrievedChunk> vectorSearch(float[] embedding, int topK) {
        return jdbcTemplate.query("""
                SELECT kc.id,
                       kc.document_id,
                       kd.title AS document_title,
                       kc.chunk_index,
                       kc.content,
                       kc.metadata_json,
                       1 - (kc.embedding <=> ?::vector) AS score
                FROM knowledge_chunk kc
                JOIN knowledge_document kd ON kd.id = kc.document_id
                ORDER BY kc.embedding <=> ?::vector
                LIMIT ?
                """,
                retrievalRowMapper(),
                toVectorLiteral(embedding),
                toVectorLiteral(embedding),
                topK);
    }

    /** 执行 PostgreSQL 原生 FTS 检索并返回最匹配的 chunk。 */
    public List<RetrievedChunk> keywordSearch(String tsQuery, String tsConfig, double minScore, int topK) {
        String normalizedTsConfig = normalizeTsConfig(tsConfig);
        String sql = """
                SELECT ranked.id,
                       ranked.document_id,
                       ranked.document_title,
                       ranked.chunk_index,
                       ranked.content,
                       ranked.metadata_json,
                       ranked.score
                FROM (
                    SELECT kc.id,
                           kc.document_id,
                           kd.title AS document_title,
                           kc.chunk_index,
                           kc.content,
                           kc.metadata_json,
                           ts_rank_cd(kc.content_tsv, to_tsquery('%s', ?)) AS score
                    FROM knowledge_chunk kc
                    JOIN knowledge_document kd ON kd.id = kc.document_id
                    WHERE kc.content_tsv @@ to_tsquery('%s', ?)
                ) ranked
                WHERE ranked.score >= ?
                ORDER BY ranked.score DESC, ranked.id DESC
                LIMIT ?
                """.formatted(normalizedTsConfig, normalizedTsConfig);
        return jdbcTemplate.query(
                sql,
                retrievalRowMapper(),
                tsQuery,
                tsQuery,
                minScore,
                topK
        );
    }

    /** 返回知识库当前是否已经存在任意 chunk。 */
    public boolean hasKnowledge() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_chunk", Integer.class);
        return count != null && count > 0;
    }

    private RowMapper<RetrievedChunk> retrievalRowMapper() {
        return (resultSet, rowNum) -> new RetrievedChunk(
                resultSet.getLong("id"),
                resultSet.getLong("document_id"),
                resultSet.getString("document_title"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("content"),
                readMetadata(resultSet),
                resultSet.getDouble("score"),
                "unknown"
        );
    }

    private Map<String, Object> readMetadata(ResultSet resultSet) throws SQLException {
        try {
            return objectMapper.readValue(resultSet.getString("metadata_json"), MAP_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("Failed to deserialize chunk metadata", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("Failed to serialize chunk metadata", exception);
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
     * ts_config 来自配置，允许值应当是 PostgreSQL 文本检索配置名。
     * 这里做最小白名单校验，避免把配置直接拼接进 SQL 时带来注入风险。
     */
    private String normalizeTsConfig(String tsConfig) {
        String normalized = tsConfig == null ? "simple" : tsConfig.trim();
        if (normalized.isEmpty()) {
            return "simple";
        }
        if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ApplicationException("Invalid PostgreSQL text search configuration: " + tsConfig);
        }
        return normalized;
    }
}
