package com.example.agentplatform.tools.dto;

import java.util.List;

/**
 * 工具对话响应。
 */
public record ToolChatResponse(
        Long workflowId,
        Long conversationId,
        String sessionId,
        String answer,
        boolean returnDirect,
        String directToolName,
        String directPayload,
        List<ToolInvocationRecord> toolInvocations
) {
}
