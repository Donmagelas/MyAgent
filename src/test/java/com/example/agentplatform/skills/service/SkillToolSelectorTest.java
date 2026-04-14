package com.example.agentplatform.skills.service;

import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.skills.domain.SkillToolChoiceMode;
import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Skill 工具选择器测试。
 * 验证 skill 元数据能正确收缩工具候选集。
 */
class SkillToolSelectorTest {

    @Test
    void shouldKeepOnlyAllowedToolsWhenSkillUsesAllowedMode() {
        SkillToolSelector selector = new SkillToolSelector();
        List<RegisteredTool> candidateTools = List.of(
                buildRegisteredTool("search_web"),
                buildRegisteredTool("fetch_webpage"),
                buildRegisteredTool("generate_pdf")
        );
        ResolvedSkill resolvedSkill = new ResolvedSkill(
                new SkillDefinition(
                        "web-research",
                        "联网研究",
                        "联网搜索 skill",
                        true,
                        List.of("search"),
                        List.of("搜索"),
                        List.of("search_web", "fetch_webpage"),
                        SkillToolChoiceMode.ALLOWED,
                        List.of(),
                        "",
                        "classpath:skills/web-research/skill.yaml"
                ),
                "测试路由",
                "rule"
        );

        List<RegisteredTool> selectedTools = selector.select(candidateTools, resolvedSkill);

        assertEquals(2, selectedTools.size());
        assertEquals(List.of("search_web", "fetch_webpage"),
                selectedTools.stream().map(tool -> tool.definition().name()).toList());
    }

    private RegisteredTool buildRegisteredTool(String toolName) {
        ToolCallback callback = Mockito.mock(ToolCallback.class);
        return new RegisteredTool(
                new PlatformToolDefinition(
                        toolName,
                        toolName,
                        toolName,
                        toolName + " description",
                        "{}",
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        10_000L,
                        ToolRiskLevel.LOW,
                        Set.of(),
                        List.of(),
                        List.of()
                ),
                callback
        );
    }
}
