package com.example.agentplatform.agent.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 任务计划中的单个步骤。
 * 用于描述子任务、依赖关系和推荐工具。
 */
public record TaskPlanStep(
        @JsonPropertyDescription("稳定的步骤标识，例如 step-1、step-2。")
        String stepId,
        @JsonPropertyDescription("步骤标题，要求简短清晰。")
        String title,
        @JsonPropertyDescription("步骤说明，描述当前步骤要完成的目标。")
        String description,
        @JsonPropertyDescription("当前步骤直接依赖的前置步骤 ID 列表。")
        List<String> dependsOnStepIds,
        @JsonPropertyDescription("推荐使用的工具名称列表，只填写真正有帮助的工具。")
        List<String> suggestedTools,
        @JsonPropertyDescription("该步骤是否允许被跳过。")
        boolean skippable,
        @JsonPropertyDescription("判断该步骤已经完成的条件。")
        String doneCondition
) {
}
