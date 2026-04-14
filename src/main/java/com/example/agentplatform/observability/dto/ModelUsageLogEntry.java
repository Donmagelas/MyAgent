package com.example.agentplatform.observability.dto;

import java.time.OffsetDateTime;

/**
 * usage 日志查询结果。
 * 供执行可视化接口聚合流程级 token 消耗与调用明细。
 */
public record ModelUsageLogEntry(
        Long id,
        Long workflowId,
        Long taskId,
        Long conversationId,
        Long messageId,
        String requestId,
        String stepName,
        String modelName,
        String provider,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long latencyMs,
        boolean success,
        String errorMessage,
        OffsetDateTime createdAt
) {
}
