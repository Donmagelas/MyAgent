package com.example.agentplatform.skills.service;

import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.domain.SkillToolChoiceMode;
import com.example.agentplatform.tools.domain.RegisteredTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Skill 工具选择器。
 * 根据 skill 元数据对工具候选集做进一步收缩。
 */
@Component
public class SkillToolSelector {

    /**
     * 按 skill 限制工具候选集。
     */
    public List<RegisteredTool> select(List<RegisteredTool> candidateTools, ResolvedSkill resolvedSkill) {
        if (resolvedSkill == null) {
            return candidateTools;
        }

        SkillToolChoiceMode toolChoiceMode = resolvedSkill.skillDefinition().toolChoiceMode();
        if (toolChoiceMode == SkillToolChoiceMode.NONE) {
            return List.of();
        }
        if (toolChoiceMode == SkillToolChoiceMode.ALL) {
            return candidateTools;
        }

        List<String> allowedTools = resolvedSkill.skillDefinition().allowedTools();
        if (allowedTools == null || allowedTools.isEmpty()) {
            return List.of();
        }
        Set<String> allowedToolNames = Set.copyOf(allowedTools);
        return candidateTools.stream()
                .filter(tool -> allowedToolNames.contains(tool.definition().name()))
                .toList();
    }
}
