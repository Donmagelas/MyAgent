package com.example.agentplatform.skills.router;

import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.skills.service.SkillCatalogService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * 基于规则的 skill 路由器。
 * 通过关键词、标签和示例对问题做轻量匹配，作为结构化路由失败时的兜底方案。
 */
@Component
public class RuleBasedSkillRouter {

    private static final String FALLBACK_SKILL_ID = "general-tool-agent";

    private final SkillCatalogService skillCatalogService;

    public RuleBasedSkillRouter(SkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    /**
     * 使用规则为当前问题选择 skill。
     */
    public Optional<ResolvedSkill> route(String message) {
        String normalizedMessage = normalize(message);
        if (!StringUtils.hasText(normalizedMessage)) {
            return fallbackSkill("问题为空，回退到通用工具代理");
        }

        return skillCatalogService.listEnabledSkills().stream()
                .filter(skill -> !FALLBACK_SKILL_ID.equals(skill.id()))
                .map(skill -> new ScoredSkill(skill, score(skill, normalizedMessage)))
                .filter(scoredSkill -> scoredSkill.score() > 0)
                .max(Comparator.comparingInt(ScoredSkill::score))
                .map(scoredSkill -> new ResolvedSkill(
                        scoredSkill.skill(),
                        "命中规则关键词，匹配分数为 " + scoredSkill.score(),
                        "rule"
                ))
                .or(() -> fallbackSkill("未命中明确 skill，回退到通用工具代理"));
    }

    private Optional<ResolvedSkill> fallbackSkill(String reason) {
        return skillCatalogService.findEnabledSkill(FALLBACK_SKILL_ID)
                .map(skill -> new ResolvedSkill(skill, reason, "rule-fallback"));
    }

    private int score(SkillDefinition skill, String message) {
        int score = 0;
        score += countMatches(skill.routeKeywords(), message, 8);
        score += countMatches(skill.tags(), message, 5);
        score += countMatches(skill.examples(), message, 3);
        if (contains(skill.name(), message)) {
            score += 4;
        }
        if (contains(skill.description(), message)) {
            score += 2;
        }
        return score;
    }

    private int countMatches(java.util.List<String> candidates, String message, int weight) {
        int score = 0;
        if (candidates == null) {
            return 0;
        }
        for (String candidate : candidates) {
            if (contains(candidate, message)) {
                score += weight;
            }
        }
        return score;
    }

    private boolean contains(String candidate, String message) {
        return StringUtils.hasText(candidate) && message.contains(normalize(candidate));
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private record ScoredSkill(
            SkillDefinition skill,
            int score
    ) {
    }
}
