package com.example.agentplatform.skills.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Skill 路由预览请求。
 */
public record SkillRoutePreviewRequest(
        @NotBlank String message
) {
}
