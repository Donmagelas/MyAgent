package com.example.agentplatform.observability.dto;

/**
 * 单个步骤的 token 消耗汇总。
 */
public record UsageStepSummaryView(
        String stepName,
        String modelName,
        long callCount,
        long successCount,
        long failureCount,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long totalLatencyMs
) {
}
