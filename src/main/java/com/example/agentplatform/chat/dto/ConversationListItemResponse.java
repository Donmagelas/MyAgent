package com.example.agentplatform.chat.dto;

import java.time.OffsetDateTime;

/**
 * 会话列表项响应。
 * 用于左侧会话栏展示标题、最后一条消息和最近活动时间。
 */
public record ConversationListItemResponse(
        Long conversationId,
        String sessionId,
        String title,
        String status,
        String lastMessageRole,
        String lastMessagePreview,
        OffsetDateTime lastActivityAt
) {
}
