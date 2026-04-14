package com.example.agentplatform.skills.router;

import com.example.agentplatform.config.SkillRouteProperties;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 默认 skill 路由服务。
 * 优先使用 Spring AI 结构化路由，失败后回退到规则路由。
 */
@Service
public class DefaultSkillRouterService implements SkillRouterService {

    private final SkillRouteProperties skillRouteProperties;
    private final SpringAiSkillRouter springAiSkillRouter;
    private final RuleBasedSkillRouter ruleBasedSkillRouter;

    public DefaultSkillRouterService(
            SkillRouteProperties skillRouteProperties,
            SpringAiSkillRouter springAiSkillRouter,
            RuleBasedSkillRouter ruleBasedSkillRouter
    ) {
        this.skillRouteProperties = skillRouteProperties;
        this.springAiSkillRouter = springAiSkillRouter;
        this.ruleBasedSkillRouter = ruleBasedSkillRouter;
    }

    @Override
    public Optional<ResolvedSkill> route(String message) {
        if (skillRouteProperties.enabled()) {
            try {
                Optional<ResolvedSkill> structuredResult = springAiSkillRouter.route(message);
                if (structuredResult.isPresent()) {
                    return structuredResult;
                }
            }
            catch (RuntimeException ignored) {
                // 结构化路由失败时回退到规则路由，避免影响主对话流程。
            }
        }
        return ruleBasedSkillRouter.route(message);
    }
}
