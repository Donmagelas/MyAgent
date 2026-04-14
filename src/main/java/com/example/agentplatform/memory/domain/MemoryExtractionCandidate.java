package com.example.agentplatform.memory.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 自动提炼出的长期记忆候选项。
 * 一条候选项会同时用于长期记忆正文和记忆摘要生成。
 */
public record MemoryExtractionCandidate(
        @JsonPropertyDescription("长期记忆类型，必须是 MemoryType 枚举值")
        MemoryType memoryType,
        @JsonPropertyDescription("记忆主题，要求短、稳定、可检索")
        String subject,
        @JsonPropertyDescription("长期记忆正文，应当是稳定事实、偏好、进度、决策或任务结论")
        String content,
        @JsonPropertyDescription("重要度，范围 1 到 10")
        Integer importance,
        @JsonPropertyDescription("便于向量检索的压缩摘要文本")
        String summaryText
) {
}
