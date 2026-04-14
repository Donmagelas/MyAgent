package com.example.agentplatform.agent.dto;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.chat.dto.ChatAskResponse;

import java.util.List;

/**
 * Agent 聊天响应。
 * 返回 workflow 跟踪信息、最终答案以及执行过程中实际使用的工具。
 */
public record AgentChatResponse(
        Long workflowId,
        Long conversationId,
        String sessionId,
        AgentReasoningMode mode,
        String answer,
        String reasoningSummary,
        int stepCount,
        List<String> toolNames,
        List<ChatAskResponse.SourceItem> sources
) {
}
