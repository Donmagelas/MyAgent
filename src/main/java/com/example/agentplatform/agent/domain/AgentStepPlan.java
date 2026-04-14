package com.example.agentplatform.agent.domain;

import java.util.Map;

/**
 * Agent 单步规划结果。
 * 用于承接结构化输出后的下一步决策。
 */
public record AgentStepPlan(
        String thought,
        AgentActionType actionType,
        String ragQuery,
        String toolName,
        Map<String, Object> toolInput,
        String finalAnswer
) {
}
