package com.example.agentplatform.chat.domain;

import java.time.OffsetDateTime;

/**
 * 会话聚合根。
 * 用于把同一 sessionId 下的多条消息归到同一个会话。
 */
public record Conversation(
        Long id,
        Long userId,
        String sessionId,
        String title,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
