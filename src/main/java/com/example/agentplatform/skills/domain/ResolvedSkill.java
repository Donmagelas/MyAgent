package com.example.agentplatform.skills.domain;

/**
 * 已解析出的 skill 结果。
 * 用于在后续工具选择和提示词拼装阶段传递路由结果。
 */
public record ResolvedSkill(
        SkillDefinition skillDefinition,
        String reason,
        String routeStrategy
) {
}
