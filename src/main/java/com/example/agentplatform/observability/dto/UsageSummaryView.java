package com.example.agentplatform.observability.dto;

import java.util.List;

/**
 * 流程级 token 消耗汇总。
 */
public record UsageSummaryView(
        long callCount,
        long successCount,
        long failureCount,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long totalLatencyMs,
        List<UsageStepSummaryView> byStep,
        List<ModelUsageLogEntry> recentLogs
) {
}
