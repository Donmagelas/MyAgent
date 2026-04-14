package com.example.agentplatform.skills.domain;

import java.util.List;

/**
 * 文件式 skill 定义。
 * 聚合 skill 元数据和提示词内容，供后续路由与工具筛选使用。
 */
public record SkillDefinition(
        String id,
        String name,
        String description,
        boolean enabled,
        List<String> tags,
        List<String> routeKeywords,
        List<String> allowedTools,
        SkillToolChoiceMode toolChoiceMode,
        List<String> examples,
        String promptContent,
        String sourcePath
) {
}
