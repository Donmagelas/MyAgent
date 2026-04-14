package com.example.agentplatform.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 创建工作流请求。
 */
public record WorkflowCreateRequest(
        @NotBlank(message = "工作流名称不能为空")
        String name,
        String description,
        Boolean failFast,
        @NotNull(message = "工作流输入不能为空")
        Map<String, Object> input,
        Map<String, Object> metadata,
        @Valid
        @NotEmpty(message = "工作流至少需要一个任务")
        List<WorkflowTaskCreateRequest> tasks
) {
}
