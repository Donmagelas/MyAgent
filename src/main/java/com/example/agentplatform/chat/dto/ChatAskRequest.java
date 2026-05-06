package com.example.agentplatform.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 聊天请求对象。
 * sessionId 用于把多条消息归并到同一会话。
 */
public record ChatAskRequest(
        String sessionId,
        @NotBlank String message,
        Integer agentMaxSteps,
        Boolean preferKnowledgeRetrieval,
        String knowledgeDocumentHint
) {

    /**
     * 兼容旧调用方的简化构造器。
     */
    public ChatAskRequest(
            String sessionId,
            String message,
            Integer agentMaxSteps
    ) {
        this(sessionId, message, agentMaxSteps, null, null);
    }
}
