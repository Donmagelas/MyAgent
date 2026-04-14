package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Skill 路由配置。
 * 控制结构化 skill 路由的开关和模型参数。
 */
@ConfigurationProperties(prefix = "app.skills.route")
public record SkillRouteProperties(
        boolean enabled,
        double temperature,
        int maxTokens
) {
}
