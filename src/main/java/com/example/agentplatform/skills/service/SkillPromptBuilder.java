package com.example.agentplatform.skills.service;

import com.example.agentplatform.skills.domain.ResolvedSkill;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Skill 提示词构建器。
 * 负责将基础工具提示词与 skill 专属提示词拼装成最终系统提示词。
 */
@Component
public class SkillPromptBuilder {

    /**
     * 构建最终系统提示词。
     */
    public String build(String basePrompt, ResolvedSkill resolvedSkill) {
        if (resolvedSkill == null || !StringUtils.hasText(resolvedSkill.skillDefinition().promptContent())) {
            return basePrompt;
        }
        return """
                %s

                当前已选技能：
                - skillId: %s
                - name: %s
                - routeStrategy: %s
                - reason: %s

                技能提示词：
                %s
                """.formatted(
                basePrompt,
                resolvedSkill.skillDefinition().id(),
                resolvedSkill.skillDefinition().name(),
                resolvedSkill.routeStrategy(),
                resolvedSkill.reason(),
                resolvedSkill.skillDefinition().promptContent().trim()
        );
    }
}
