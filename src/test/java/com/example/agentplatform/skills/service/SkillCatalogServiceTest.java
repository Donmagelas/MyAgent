package com.example.agentplatform.skills.service;

import com.example.agentplatform.config.SkillProperties;
import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.skills.loader.SkillFileLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill 目录服务测试。
 * 验证文件式 skill 能被正确加载并筛选启用状态。
 */
class SkillCatalogServiceTest {

    private static final String TEST_SKILL_LOCATION =
            "file:src/main/resources/skills/*/skill.yaml";

    @Test
    void shouldLoadEnabledSkillsFromClasspath() {
        SkillProperties skillProperties = new SkillProperties(true, TEST_SKILL_LOCATION);
        SkillCatalogService skillCatalogService = new SkillCatalogService(new SkillFileLoader(skillProperties));

        List<SkillDefinition> skills = skillCatalogService.listEnabledSkills();

        assertEquals(4, skills.size());
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("web-research")));
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("pdf-assistant")));
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("general-tool-agent")));
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("knowledge-base-qa")));
        assertFalse(skillCatalogService.findEnabledSkill("missing-skill").isPresent());
    }
}
