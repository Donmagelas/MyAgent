package com.example.agentplatform.tools.dto;

import com.example.agentplatform.agent.domain.SubagentCompletionType;

import java.util.List;

/**
 * subagent 工具执行结果。
 * 只把必要摘要和标准化结束元数据带回父智能体。
 */
public record SubagentTaskResult(
        Long workflowId,
        String summary,
        String planSummary,
        int stepCount,
        boolean completedByFallback,
        SubagentCompletionType completionType,
        String completionReason,
        List<String> usedTools
) {
}