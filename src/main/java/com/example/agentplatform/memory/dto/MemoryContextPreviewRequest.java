package com.example.agentplatform.memory.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 记忆上下文预览请求。
 * 用于在 chat/rag 正式调用前查看当前会组装出哪些记忆片段。
 */
public record MemoryContextPreviewRequest(
        Long conversationId,
        @NotBlank(message = "问题不能为空")
        String question
) {
}
