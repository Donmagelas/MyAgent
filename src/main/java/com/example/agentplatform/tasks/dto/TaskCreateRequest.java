package com.example.agentplatform.tasks.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 创建任务请求。
 */
public record TaskCreateRequest(
        Long workflowId,
        Long parentTaskId,
        String clientTaskKey,
        @NotBlank(message = "任务名称不能为空")
        String name,
        String description,
        @NotBlank(message = "任务类型不能为空")
        String taskType,
        @Min(value = 0, message = "最大重试次数不能小于 0")
        Integer maxRetries,
        String sourceType,
        String sourceRef,
        @NotNull(message = "任务输入不能为空")
        Map<String, Object> input,
        Map<String, Object> metadata,
        List<Long> blockedByTaskIds
) {
}
