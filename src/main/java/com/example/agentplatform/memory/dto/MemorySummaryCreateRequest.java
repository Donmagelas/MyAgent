package com.example.agentplatform.memory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 记忆摘要创建请求。
 * 为某条长期记忆补充可向量召回的摘要层。
 */
public record MemorySummaryCreateRequest(
        @NotNull(message = "长期记忆主键不能为空")
        Long longTermMemoryId,
        Long conversationId,
        @NotBlank(message = "摘要内容不能为空")
        String summaryText,
        @Min(value = 1, message = "重要度最小为 1")
        @Max(value = 10, message = "重要度最大为 10")
        Integer importance,
        Boolean active,
        String sourceType,
        String sourceRef,
        Map<String, Object> metadata
) {
}
