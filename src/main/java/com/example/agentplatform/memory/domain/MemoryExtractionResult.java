package com.example.agentplatform.memory.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 自动长期记忆提炼结果。
 * 由模型结构化输出，指示当前会话片段是否值得写入长期记忆。
 */
public record MemoryExtractionResult(
        @JsonPropertyDescription("当前会话片段是否需要写入长期记忆")
        Boolean shouldPersist,
        @JsonPropertyDescription("提炼决策的简短原因")
        String reason,
        @JsonPropertyDescription("提炼出的长期记忆候选项列表")
        List<MemoryExtractionCandidate> memories
) {
}
