package com.example.agentplatform.skills.dto;

import java.util.List;

/**
 * Skill 列表响应。
 */
public record SkillDefinitionResponse(
        String id,
        String name,
        String description,
        boolean enabled,
        List<String> tags,
        List<String> routeKeywords,
        List<String> allowedTools,
        String toolChoiceMode,
        List<String> examples
) {
}
