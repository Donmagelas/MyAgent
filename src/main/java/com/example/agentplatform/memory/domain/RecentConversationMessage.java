package com.example.agentplatform.memory.domain;

import java.time.OffsetDateTime;

/**
 * 短期记忆视图。
 * 表示当前会话窗口内可直接拼接到上下文的最近消息。
 */
public record RecentConversationMessage(
        Long messageId,
        Long conversationId,
        Long userId,
        String role,
        String content,
        String messageType,
        String modelName,
        OffsetDateTime createdAt
) {
}
