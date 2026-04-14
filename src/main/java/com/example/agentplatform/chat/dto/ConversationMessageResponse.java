package com.example.agentplatform.chat.dto;

import java.time.OffsetDateTime;

/**
 * 会话消息响应。
 * 供前端在切换历史会话时恢复消息列表。
 */
public record ConversationMessageResponse(
        Long messageId,
        String role,
        String content,
        String messageType,
        String modelName,
        OffsetDateTime createdAt
) {
}
