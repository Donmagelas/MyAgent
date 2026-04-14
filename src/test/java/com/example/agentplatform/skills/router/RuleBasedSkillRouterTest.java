package com.example.agentplatform.skills.router;

import com.example.agentplatform.config.SkillProperties;
import com.example.agentplatform.skills.service.SkillCatalogService;
import com.example.agentplatform.skills.loader.SkillFileLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则 skill 路由测试。
 * 验证不同问题能命中预期的 skill。
 */
class RuleBasedSkillRouterTest {

    private static final String TEST_SKILL_LOCATION =
            "file:src/main/resources/skills/*/skill.yaml";

    @Test
    void shouldRouteWebResearchQuestionToWebResearchSkill() {
        RuleBasedSkillRouter router = createRouter();

        String skillId = router.route("帮我搜索一下 PostgreSQL 17 的新特性")
                .orElseThrow()
                .skillDefinition()
                .id();

        assertEquals("web-research", skillId);
    }

    @Test
    void shouldFallbackToCliSkillWhenQuestionDoesNotMatchSpecificSkill() {
        RuleBasedSkillRouter router = createRouter();

        String skillId = router.route("帮我处理一下这个任务")
                .orElseThrow()
                .skillDefinition()
                .id();

        assertEquals("general-tool-agent", skillId);
    }

    @Test
    void shouldRoutePdfQuestionToPdfAssistantSkill() {
        RuleBasedSkillRouter router = createRouter();

        String skillId = router.route("请帮我生成一个 PDF 文件")
                .orElseThrow()
                .skillDefinition()
                .id();

        assertEquals("pdf-assistant", skillId);
    }

    private RuleBasedSkillRouter createRouter() {
        SkillProperties skillProperties = new SkillProperties(true, TEST_SKILL_LOCATION);
        SkillCatalogService skillCatalogService = new SkillCatalogService(new SkillFileLoader(skillProperties));
        assertTrue(skillCatalogService.listEnabledSkills().size() >= 3);
        return new RuleBasedSkillRouter(skillCatalogService);
    }
}
