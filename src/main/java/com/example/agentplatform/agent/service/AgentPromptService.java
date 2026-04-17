package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.chat.service.DirectPromptService;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.tools.domain.RegisteredTool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 提示词服务。
 * 统一维护 CoT、任务规划、Loop 决策和最终回答所需的核心 prompt，
 * 避免关键提示词分散在多处业务逻辑中。
 */
@Service
public class AgentPromptService {

    private static final String SUBAGENT_TOOL_NAME = "task";

    private final DirectPromptService directPromptService;

    public AgentPromptService(DirectPromptService directPromptService) {
        this.directPromptService = directPromptService;
    }

    /**
     * 构造 CoT 单轮推理的系统提示词。
     */
    public String buildCotSystemPrompt(MemoryContext memoryContext) {
        return directPromptService.buildSystemPrompt(memoryContext) + """

                You are running a CoT reasoning step for an enterprise agent.
                Return a structured result only.
                Requirements:
                1. reasoningSummary must briefly explain the reasoning path and key evidence.
                2. finalAnswer must be the direct user-facing answer.
                3. If evidence is insufficient, say so explicitly instead of inventing facts.
                4. Keep reasoningSummary concise and grounded in the provided context.
                """;
    }

    /**
     * 构造任务规划阶段的系统提示词。
     */
    public String buildTaskPlanningSystemPrompt(
            AgentReasoningMode mode,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools
    ) {
        return buildTaskPlanningSystemPrompt(mode, memoryContext, availableTools, null);
    }

    /**
     * 构造带 skill 上下文的任务规划系统提示词。
     */
    public String buildTaskPlanningSystemPrompt(
            AgentReasoningMode mode,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools,
            ResolvedSkill resolvedSkill
    ) {
        String toolStrategy = hasTool(availableTools, SUBAGENT_TOOL_NAME)
                ? "The task tool is available. Recommend it in suggestedTools only when the user goal clearly contains a separable sub-task that benefits from delegated local investigation."
                : "The task tool is not available in this context. Do not plan around subagent delegation.";
        return directPromptService.buildSystemPrompt(memoryContext) + """

                You are the task planner for an enterprise Agent workflow.
                Produce a compact TaskPlan before execution.
                Requirements:
                1. Break the user goal into the smallest useful number of executable steps.
                2. Prefer 1 to 4 steps unless the request is genuinely complex.
                3. Each step must have a clear title, description, done condition, and suggested tools.
                4. suggestedTools must only come from the available tools list.
                5. Use the plan to support a ReAct / Agent Loop execution, not a human project plan.
                6. Avoid redundant steps, repeated retrieval, and generic filler steps.
                7. If the task is simple enough to answer directly, you may return a one-step plan.
                8. If the request is asking about stable product facts, gameplay systems, configuration, parameters, interfaces, fields, rules, or document-defined behavior, prefer a retrieval-first step before answering.

                Current reasoning mode: %s
                %s

                Selected skill:
                %s

                Available tools:
                %s
                """.formatted(mode.name(), toolStrategy, renderSelectedSkill(resolvedSkill), describeTools(availableTools));
    }

    /**
     * 构造任务规划阶段的用户提示词。
     */
    public String buildTaskPlanningUserPrompt(String message) {
        return """
                User request:
                %s

                Generate a TaskPlan.
                Output rules:
                - goal must restate the user objective.
                - planSummary must summarize the strategy in one sentence.
                - steps must be ordered for execution.
                - stepId should use identifiers like step-1, step-2.
                - suggestedTools must name concrete tools when helpful.
                - dependsOnStepIds should describe actual dependencies only.
                """.formatted(message);
    }

    /**
     * 构造统一 Agent Loop 的系统提示词。
     */
    public String buildLoopPlannerSystemPrompt(
            AgentReasoningMode mode,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools,
            TaskPlan taskPlan
    ) {
        return buildLoopPlannerSystemPrompt(mode, memoryContext, availableTools, taskPlan, null);
    }

    /**
     * 构造带 skill 上下文的统一 Agent Loop 系统提示词。
     */
    public String buildLoopPlannerSystemPrompt(
            AgentReasoningMode mode,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools,
            TaskPlan taskPlan,
            ResolvedSkill resolvedSkill
    ) {
        String planSection = taskPlan == null ? "No explicit task plan is available." : renderTaskPlan(taskPlan);
        return directPromptService.buildSystemPrompt(memoryContext) + """

                You are the planner inside a unified Agent Loop.
                On every step, think first and then choose exactly one action.

                Allowed action types:
                1. FINAL: answer the user directly.
                2. RAG: retrieve knowledge-base evidence first.
                3. TOOL: call one tool.

                Decision rules:
                - Choose FINAL only when the answer is already supported by memory, scratchpad observations, retrieved evidence, or tool results.
                - Choose RAG when the answer depends on stable domain facts that have not yet been verified, especially product features, gameplay systems, configuration, parameters, versions, interfaces, fields, rules, requirements, or document-defined meanings.
                - Do not wait for the user to explicitly say "use the knowledge base" before choosing RAG.
                - If the question asks what / which / how / whether / meaning / difference about a domain entity, prefer one RAG step before FINAL unless the evidence is already explicit in memory or scratchpad.
                - Choose TOOL when an external action is required, such as web search, webpage fetching, PDF generation, or delegated sub-task execution.
                - For knowledge-base style factual questions, prefer RAG before web search.
                - Do not fabricate facts. If evidence is weak, prefer RAG or a cautious final answer.
                - Avoid repeating the same action unless the latest observation clearly justifies it.
                - If the task tool is available, use it only for a separable sub-problem; never recurse blindly.

                Subagent guidance:
                %s

                Current reasoning mode: %s
                Selected skill:
                %s

                Current task plan:
                %s

                Available tools:
                %s
                """.formatted(
                buildSubagentGuidance(availableTools),
                mode.name(),
                renderSelectedSkill(resolvedSkill),
                planSection,
                describeTools(availableTools)
        );
    }

    /**
     * 构造统一 Agent Loop 的用户提示词。
     */
    public String buildLoopPlannerUserPrompt(
            String message,
            int stepIndex,
            int maxSteps,
            List<Map<String, Object>> scratchpad
    ) {
        String scratchpadText = scratchpad == null || scratchpad.isEmpty()
                ? "No previous steps yet."
                : scratchpad.stream()
                .map(step -> """
                        - step=%s
                          thought=%s
                          action=%s
                          observation=%s
                        """.formatted(
                        step.getOrDefault("step", "?"),
                        step.getOrDefault("thought", ""),
                        step.getOrDefault("action", ""),
                        step.getOrDefault("observation", "")
                ))
                .collect(Collectors.joining("\n"));

        return """
                User request:
                %s

                Current loop step: %d / %d

                Scratchpad:
                %s

                Return one structured AgentStepPlan.
                Output rules:
                - If actionType=RAG, ragQuery must be present and focused.
                - If actionType=TOOL, toolName and toolInput must be present.
                - If actionType=FINAL, finalAnswer must be present.
                - thought must explain why this is the best next step.
                - ragQuery must be optimized for retrieval, not a full sentence answer.
                """.formatted(message, stepIndex, maxSteps, scratchpadText);
    }

    /**
     * 构造最终回答阶段的系统提示词。
     */
    public String buildFinalAnswerSystemPrompt(
            MemoryContext memoryContext,
            List<ChatAskResponse.SourceItem> sources
    ) {
        StringBuilder builder = new StringBuilder(directPromptService.buildSystemPrompt(memoryContext));
        builder.append("""

                You are generating the final user-facing answer for an agent workflow.
                You must preserve verified facts from the provided answer draft.
                Do not add new project facts, tool outputs, or document details that are not present in the draft or sources.
                If sources are provided, you may mention the source titles naturally.
                Keep the answer direct, concise, and user-facing.
                """);
        if (sources != null && !sources.isEmpty()) {
            builder.append("\nAvailable source titles:\n")
                    .append(renderSourceTitles(sources))
                    .append('\n');
        }
        return builder.toString();
    }

    /**
     * 构造最终回答阶段的用户提示词。
     */
    public String buildFinalAnswerUserPrompt(
            String message,
            String answerDraft,
            String reasoningSummary,
            List<ChatAskResponse.SourceItem> sources
    ) {
        return """
                User request:
                %s

                Draft answer:
                %s

                Reasoning summary:
                %s

                Source titles:
                %s

                Generate the final answer.
                Requirements:
                - Keep the draft answer grounded.
                - Do not introduce unsupported facts or numbers.
                - If evidence is insufficient, say so plainly.
                - Keep the answer concise and user-facing.
                """.formatted(
                message,
                answerDraft == null ? "" : answerDraft,
                reasoningSummary == null ? "" : reasoningSummary,
                renderSourceTitles(sources)
        );
    }

    /**
     * 生成 subagent 使用约束。
     */
    private String buildSubagentGuidance(List<RegisteredTool> availableTools) {
        if (!hasTool(availableTools, SUBAGENT_TOOL_NAME)) {
            return "The task tool is unavailable. Do not plan any subagent delegation.";
        }
        return """
                - The task tool delegates a separable sub-task to a subagent.
                - Use it only when a local sub-problem benefits from independent investigation.
                - Do not use the task tool for trivial restatements.
                - Do not delegate the entire user request unless the whole task is naturally a sub-task.
                - Assume the subagent has a restricted tool set and limited turns.
                - If direct reasoning or RAG is enough, prefer those options over delegation.
                """;
    }

    /**
     * 把任务计划渲染成 planner 可直接消费的文本块。
     */
    private String renderTaskPlan(TaskPlan taskPlan) {
        String steps = taskPlan.steps() == null || taskPlan.steps().isEmpty()
                ? "- no steps"
                : taskPlan.steps().stream()
                .map(step -> """
                        - %s | %s
                          description: %s
                          dependsOn: %s
                          suggestedTools: %s
                          doneCondition: %s
                        """.formatted(
                        step.stepId(),
                        step.title(),
                        step.description(),
                        step.dependsOnStepIds(),
                        step.suggestedTools(),
                        step.doneCondition()
                ))
                .collect(Collectors.joining("\n"));
        return """
                goal: %s
                summary: %s
                steps:
                %s
                """.formatted(taskPlan.goal(), taskPlan.planSummary(), steps);
    }

    /**
     * 只渲染当前命中的 skill，避免把全部 skill prompt 一次性暴露给模型。
     */
    private String renderSelectedSkill(ResolvedSkill resolvedSkill) {
        if (resolvedSkill == null || resolvedSkill.skillDefinition() == null) {
            return "No skill is selected for this request.";
        }
        SkillDefinition skill = resolvedSkill.skillDefinition();
        String promptContent = skill.promptContent() == null || skill.promptContent().isBlank()
                ? "No extra skill prompt is available."
                : skill.promptContent().trim();
        return """
                - skillId: %s
                - name: %s
                - routeStrategy: %s
                - reason: %s
                - toolChoiceMode: %s
                - allowedTools: %s

                Skill prompt:
                %s
                """.formatted(
                skill.id(),
                skill.name(),
                resolvedSkill.routeStrategy(),
                resolvedSkill.reason(),
                skill.toolChoiceMode(),
                skill.allowedTools(),
                promptContent
        );
    }

    /**
     * 描述当前可见工具，便于 planner 约束 suggestedTools / toolName。
     */
    private String describeTools(List<RegisteredTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return "No tools are available.";
        }
        return tools.stream()
                .map(tool -> """
                        - name: %s
                          description: %s
                          returnDirect: %s
                          readOnly: %s
                          inputSchema: %s
                        """.formatted(
                        tool.definition().name(),
                        tool.definition().description(),
                        tool.definition().returnDirect(),
                        tool.definition().readOnly(),
                        tool.definition().inputSchema()
                ))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 仅提取来源标题，避免把过多原文再次注入最终回答 prompt。
     */
    private String renderSourceTitles(List<ChatAskResponse.SourceItem> sources) {
        if (sources == null || sources.isEmpty()) {
            return "- no sources";
        }
        return sources.stream()
                .map(source -> {
                    if (source.chunkTitle() != null && !source.chunkTitle().isBlank()) {
                        return "- " + source.chunkTitle();
                    }
                    if (source.documentTitle() != null && !source.documentTitle().isBlank()) {
                        return "- " + source.documentTitle();
                    }
                    return "- source-" + source.chunkIndex();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * 判断指定工具当前是否在可见工具集里。
     */
    private boolean hasTool(List<RegisteredTool> tools, String toolName) {
        return tools != null && tools.stream().anyMatch(tool -> toolName.equals(tool.definition().name()));
    }
}
