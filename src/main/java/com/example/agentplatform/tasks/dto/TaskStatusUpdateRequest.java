package com.example.agentplatform.tasks.dto;

import com.example.agentplatform.tasks.domain.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 更新任务状态请求。
 */
public record TaskStatusUpdateRequest(
        @NotNull(message = "任务状态不能为空")
        TaskStatus status,
        @Min(value = 0, message = "进度不能小于 0")
        @Max(value = 100, message = "进度不能大于 100")
        Integer progress,
        Map<String, Object> result,
        String errorMessage
) {
}
