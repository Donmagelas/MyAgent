package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.agent.domain.TaskPlanStep;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.chat.service.DirectPromptService;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.skills.domain.SkillToolChoiceMode;
import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * AgentPromptService 提示词测试。
 * 用于验证关键 prompt 不会再次出现乱码或明显退化。
 */
class AgentPromptServiceTest {

    private static final String CORRUPTED_PLACEHOLDER = "\u003f\u003f\u003f\u003f";

    private final AgentPromptService agentPromptService = new AgentPromptService(new DirectPromptService());

    @Test
    void shouldBuildStableTaskPlanningPrompt() {
        String prompt = agentPromptService.buildTaskPlanningSystemPrompt(
                AgentReasoningMode.LOOP,
                new MemoryContext(List.of(), List.of(), List.of(), ""),
                List.of(buildTaskTool())
        );

        assertThat(prompt)
                .contains("You are the task planner for an enterprise Agent workflow.")
                .contains("The task tool is available.")
                .contains("Available tools:")
                .doesNotContain(CORRUPTED_PLACEHOLDER);
    }

    @Test
    void shouldBuildStableLoopPlannerPrompt() {
        TaskPlan taskPlan = new TaskPlan(
                "Research the document",
                "Retrieve evidence and then answer.",
                List.of(new TaskPlanStep(
                        "step-1",
                        "Retrieve evidence",
                        "Use retrieval first",
                        List.of(),
                        List.of("search_web"),
                        false,
                        "Relevant evidence is found"
                ))
        );

        String prompt = agentPromptService.buildLoopPlannerSystemPrompt(
                AgentReasoningMode.LOOP,
                new MemoryContext(List.of(), List.of(), List.of(), ""),
                List.of(buildTaskTool()),
                taskPlan
        );

        assertThat(prompt)
                .contains("You are the planner inside a unified Agent Loop.")
                .contains("Allowed action types:")
                .contains("Do not wait for the user to explicitly say \"use the knowledge base\" before choosing RAG.")
                .contains("Subagent guidance:")
                .contains("goal: Research the document")
                .doesNotContain(CORRUPTED_PLACEHOLDER);
    }

    @Test
    void shouldInjectOnlySelectedSkillPrompt() {
        ResolvedSkill resolvedSkill = new ResolvedSkill(
                new SkillDefinition(
                        "web-research",
                        "联网研究",
                        "用于搜索和整理公开网页信息",
                        true,
                        List.of("web", "research"),
                        List.of("搜索", "查询"),
                        List.of("search_web", "fetch_webpage"),
                        SkillToolChoiceMode.ALLOWED,
                        List.of("帮我查资料"),
                        "先搜索，再根据必要网页整理结论。",
                        "skills/web-research"
                ),
                "命中搜索类关键词",
                "rule"
        );

        String prompt = agentPromptService.buildLoopPlannerSystemPrompt(
                AgentReasoningMode.LOOP,
                new MemoryContext(List.of(), List.of(), List.of(), ""),
                List.of(buildTaskTool()),
                null,
                resolvedSkill
        );

        assertThat(prompt)
                .contains("Selected skill:")
                .contains("skillId: web-research")
                .contains("allowedTools: [search_web, fetch_webpage]")
                .contains("Skill prompt:")
                .contains("先搜索，再根据必要网页整理结论。")
                .doesNotContain(CORRUPTED_PLACEHOLDER);
    }

    @Test
    void shouldBuildStableFinalAnswerPrompt() {
        String prompt = agentPromptService.buildFinalAnswerSystemPrompt(
                new MemoryContext(List.of(), List.of(), List.of(), ""),
                List.of(new ChatAskResponse.SourceItem(
                        1L,
                        "Document A",
                        "Chunk A",
                        "section/a",
                        null,
                        2L,
                        0,
                        "vector+keyword+rerank",
                        0.91
                ))
        );

        assertThat(prompt)
                .contains("You are generating the final user-facing answer for an agent workflow.")
                .contains("Available source titles:")
                .contains("Chunk A")
                .doesNotContain(CORRUPTED_PLACEHOLDER);
    }

    /**
     * 构造 task 工具，用于验证 prompt 中的 subagent guidance。
     */
    private RegisteredTool buildTaskTool() {
        return new RegisteredTool(
                new PlatformToolDefinition(
                        "task",
                        "task",
                        "任务委托",
                        "把局部任务委托给受控 subagent",
                        "{}",
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        60_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of("CHAT_USER"),
                        List.of("task"),
                        List.of("agent")
                ),
                mock(ToolCallback.class)
        );
    }
}
