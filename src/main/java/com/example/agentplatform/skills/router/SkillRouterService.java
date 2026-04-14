package com.example.agentplatform.skills.router;

import com.example.agentplatform.skills.domain.ResolvedSkill;

import java.util.Optional;

/**
 * Skill 路由服务。
 * 根据当前问题选择最合适的 skill。
 */
public interface SkillRouterService {

    /**
     * 为当前问题选择一个 skill。
     */
    Optional<ResolvedSkill> route(String message);
}
