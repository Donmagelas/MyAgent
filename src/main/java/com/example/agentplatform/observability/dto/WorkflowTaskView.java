package com.example.agentplatform.observability.dto;

import com.example.agentplatform.tasks.domain.TaskStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流任务可视化视图。
 */
public record WorkflowTaskView(
        Long taskId,
        Long parentTaskId,
        String clientTaskKey,
        String name,
        String description,
        String taskType,
        TaskStatus status,
        int progress,
        String sourceType,
        String sourceRef,
        Map<String, Object> input,
        Map<String, Object> result,
        String errorMessage,
        Map<String, Object> metadata,
        List<Long> blockedByTaskIds,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
