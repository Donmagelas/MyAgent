package com.example.agentplatform.memory.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 长期记忆实体。
 * 保存经过提炼后的稳定事实，而不是原始聊天全文。
 */
public record LongTermMemory(
        Long id,
        Long userId,
        Long conversationId,
        MemoryType memoryType,
        String subject,
        String content,
        Integer importance,
        boolean active,
        String sourceType,
        String sourceRef,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
