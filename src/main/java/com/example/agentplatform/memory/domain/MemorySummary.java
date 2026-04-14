package com.example.agentplatform.memory.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 记忆摘要实体。
 * 作为长期记忆的可检索摘要层，独立于长期记忆正文。
 */
public record MemorySummary(
        Long id,
        Long userId,
        Long longTermMemoryId,
        Long conversationId,
        String summaryText,
        Integer importance,
        boolean active,
        String sourceType,
        String sourceRef,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
