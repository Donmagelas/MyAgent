package com.example.agentplatform.memory.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 记忆摘要检索请求。
 * 基于当前问题做按用户过滤的语义召回。
 */
public record MemorySummarySearchRequest(
        @NotBlank(message = "问题不能为空")
        String question,
        Integer topK,
        MemoryMetadataFilter metadataFilter
) {
}
