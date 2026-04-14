package com.example.agentplatform.document.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 持久化知识分片实体。
 * 保存分片文本、向量以及可检索的元数据。
 */
public record KnowledgeChunk(
        Long id,
        Long documentId,
        int chunkIndex,
        String content,
        float[] embedding,
        Integer tokenCount,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
