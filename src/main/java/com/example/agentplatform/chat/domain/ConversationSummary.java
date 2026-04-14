package com.example.agentplatform.chat.domain;

import java.time.OffsetDateTime;

/**
 * 会话摘要视图。
 * 用于左侧会话列表展示最近活动信息和最后一条消息预览。
 */
public record ConversationSummary(
        Long id,
        String sessionId,
        String title,
        String status,
        String lastMessageRole,
        String lastMessagePreview,
        OffsetDateTime lastActivityAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
