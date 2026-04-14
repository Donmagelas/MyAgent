package com.example.agentplatform.skills.dto;

import java.util.List;

/**
 * Skill 路由预览响应。
 */
public record SkillRoutePreviewResponse(
        String skillId,
        String skillName,
        String routeStrategy,
        String reason,
        List<String> allowedTools,
        String promptPreview
) {
}
