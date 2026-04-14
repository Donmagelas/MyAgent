package com.example.agentplatform.tasks.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务实体。
 * 表示一个可独立跟踪、可重试、可取消的执行单元。
 */
public record TaskRecord(
        Long id,
        Long workflowId,
        Long parentTaskId,
        Long userId,
        String clientTaskKey,
        String name,
        String description,
        String taskType,
        TaskStatus status,
        int progress,
        Map<String, Object> input,
        Map<String, Object> result,
        String errorMessage,
        int retryCount,
        int maxRetries,
        boolean cancelRequested,
        String sourceType,
        String sourceRef,
        Map<String, Object> metadata,
        List<Long> blockedByTaskIds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
