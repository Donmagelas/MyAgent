package com.example.agentplatform.workflow.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 工作流实例实体。
 * 工作流本身不直接执行，而是作为任务图的编排上下文存在。
 */
public record WorkflowRecord(
        Long id,
        Long userId,
        String name,
        String description,
        WorkflowStatus status,
        Map<String, Object> input,
        Map<String, Object> result,
        String errorMessage,
        boolean failFast,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
