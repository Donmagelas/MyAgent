package com.example.agentplatform.observability.dto;

import com.example.agentplatform.workflow.domain.WorkflowStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行可视化聚合视图。
 * 统一提供流程状态、当前步骤、任务图与 token 消耗汇总。
 */
public record WorkflowExecutionView(
        Long workflowId,
        String name,
        String description,
        WorkflowStatus status,
        Long conversationId,
        String sessionId,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Map<String, Object> input,
        Map<String, Object> result,
        String errorMessage,
        Map<String, Object> metadata,
        Map<String, Long> taskStatusCounts,
        WorkflowCurrentStepView currentStep,
        UsageSummaryView usage,
        List<WorkflowTaskView> tasks
) {
}
