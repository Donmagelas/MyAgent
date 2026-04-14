package com.example.agentplatform.tools.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 工具对话请求。
 */
public record ToolChatRequest(
        String sessionId,
        @NotBlank String message
) {
}
