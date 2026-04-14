package com.example.agentplatform.document.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 知识文档聚合对象。
 * 表示切块前后的源文档信息。
 */
public record KnowledgeDocument(
        Long id,
        String documentCode,
        String title,
        String sourceType,
        String sourceUri,
        String status,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
