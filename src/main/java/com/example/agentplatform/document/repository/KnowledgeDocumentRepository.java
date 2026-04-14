package com.example.agentplatform.document.repository;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.document.domain.KnowledgeDocument;
import com.example.agentplatform.document.domain.ParsedDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 知识文档元数据仓储。
 * 在写入 chunk 之前先保存文档级记录。
 */
@Repository
public class KnowledgeDocumentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 持久化一条知识文档记录，并返回创建后的实体视图。 */
    public KnowledgeDocument save(ParsedDocument document) {
        String documentCode = "doc_" + UUID.randomUUID().toString().replace("-", "");
        String metadataJson = toJson(document.metadata());

        Long documentId = jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_document (document_code, title, source_type, source_uri, status, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                RETURNING id
                """,
                Long.class,
                documentCode,
                document.title(),
                document.sourceType(),
                document.sourceUri(),
                "IMPORTED",
                metadataJson);

        if (documentId == null) {
            throw new ApplicationException("Failed to create knowledge document");
        }

        OffsetDateTime now = OffsetDateTime.now();
        return new KnowledgeDocument(
                documentId,
                documentCode,
                document.title(),
                document.sourceType(),
                document.sourceUri(),
                "IMPORTED",
                document.metadata(),
                now,
                now
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("Failed to serialize document metadata", exception);
        }
    }
}
