package com.example.agentplatform.skills.domain;

/**
 * Skill 路由决策。
 * 表示模型或规则为当前问题选中的 skill 及原因。
 */
public record SkillRouteDecision(
        String skillId,
        String reason
) {
}
