package com.example.agentplatform.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Agent 对话请求。
 * 承载一次 Agent 对话所需的最小输入。
 */
public record AgentChatRequest(
        String sessionId,
        @NotBlank(message = "消息不能为空")
        String message,
        Integer maxSteps,
        Boolean preferKnowledgeRetrieval,
        String knowledgeDocumentHint
) {

    /**
     * 兼容只传基础字段的构造方式。
     */
    public AgentChatRequest(
            String sessionId,
            String message,
            Integer maxSteps
    ) {
        this(sessionId, message, maxSteps, null, null);
    }
}
