package com.example.agentplatform.agent.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 结构化任务计划。
 * 在正式进入 ReAct / Agent Loop 前，用于先把用户目标拆成显式步骤。
 */
public record TaskPlan(
        @JsonPropertyDescription("用户目标的直接复述。")
        String goal,
        @JsonPropertyDescription("对整体执行思路的一句话总结。")
        String planSummary,
        @JsonPropertyDescription("按推荐执行顺序排列的计划步骤列表。")
        List<TaskPlanStep> steps
) {
}
