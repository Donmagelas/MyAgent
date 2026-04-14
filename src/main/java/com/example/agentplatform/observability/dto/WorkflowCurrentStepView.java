package com.example.agentplatform.observability.dto;

import com.example.agentplatform.tasks.domain.TaskStatus;

import java.time.OffsetDateTime;

/**
 * 当前执行步骤视图。
 * 供前端快速展示“现在运行到哪一步了”。
 */
public record WorkflowCurrentStepView(
        Long taskId,
        String name,
        String taskType,
        TaskStatus status,
        int progress,
        OffsetDateTime updatedAt
) {
}
