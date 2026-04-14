package com.example.agentplatform.chat.domain;

import java.time.OffsetDateTime;

/**
 * 持久化聊天消息实体。
 * 表示一次会话中的一条用户或助手消息。
 */
public record ChatMessage(
        Long id,
        Long conversationId,
        Long userId,
        String role,
        String content,
        String messageType,
        String modelName,
        OffsetDateTime createdAt
) {
}
