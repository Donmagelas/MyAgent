package com.example.agentplatform.tools.dto;

/**
 * 工具调用记录。
 */
public record ToolInvocationRecord(
        String toolCallId,
        String toolName,
        boolean returnDirect
) {
}
