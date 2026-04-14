package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Skill 模块配置。
 * 负责控制 skill 文件目录的加载开关和扫描位置。
 */
@ConfigurationProperties(prefix = "app.skills")
public record SkillProperties(
        boolean enabled,
        String locationPattern
) {
}
