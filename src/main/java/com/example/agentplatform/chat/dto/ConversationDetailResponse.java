package com.example.agentplatform.chat.dto;

import java.util.List;

/**
 * 单个会话详情响应。
 * 包含会话基础信息与完整消息历史。
 */
public record ConversationDetailResponse(
        Long conversationId,
        String sessionId,
        String title,
        String status,
        List<ConversationMessageResponse> messages
) {
}
