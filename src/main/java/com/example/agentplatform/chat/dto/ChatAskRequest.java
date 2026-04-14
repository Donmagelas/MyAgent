package com.example.agentplatform.chat.dto;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import jakarta.validation.constraints.NotBlank;

/**
 * 聊天请求对象。
 * sessionId 用于把多条消息归并到同一个会话；agent 字段用于显式切换到 Agent 执行模式。
 */
public record ChatAskRequest(
        String sessionId,
        @NotBlank String message,
        AgentReasoningMode agentMode,
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
            AgentReasoningMode agentMode,
            Integer agentMaxSteps
    ) {
        this(sessionId, message, agentMode, agentMaxSteps, null, null);
    }
}
