package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentActionType;
import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.AgentStepPlan;
import com.example.agentplatform.agent.domain.SubagentCompletionType;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AgentProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolContextKeys;
import com.example.agentplatform.tools.domain.ToolResolverRequest;
import com.example.agentplatform.tools.dto.SubagentTaskResult;
import com.example.agentplatform.tools.service.ToolPermissionGuard;
import com.example.agentplatform.tools.service.ToolResolverService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * subagent 执行服务。
 * 使用独立 scratchpad 和受限工具集完成局部任务，并且只返回摘要给父智能体。
 */
@Service
public class SubagentService {

    private static final String TASK_TOOL_NAME = "task";

    private final AgentProperties agentProperties;
    private final ToolPermissionGuard toolPermissionGuard;
    private final ToolResolverService toolResolverService;
    private final TaskPlanningService taskPlanningService;
    private final AgentStepPlannerService agentStepPlannerService;
    private final AgentToolExecutorService agentToolExecutorService;
    private final SubagentWorkflowService subagentWorkflowService;

    public SubagentService(
            AgentProperties agentProperties,
            ToolPermissionGuard toolPermissionGuard,
            ToolResolverService toolResolverService,
            TaskPlanningService taskPlanningService,
            AgentStepPlannerService agentStepPlannerService,
            AgentToolExecutorService agentToolExecutorService,
            SubagentWorkflowService subagentWorkflowService
    ) {
        this.agentProperties = agentProperties;
        this.toolPermissionGuard = toolPermissionGuard;
        this.toolResolverService = toolResolverService;
        this.taskPlanningService = taskPlanningService;
        this.agentStepPlannerService = agentStepPlannerService;
        this.agentToolExecutorService = agentToolExecutorService;
        this.subagentWorkflowService = subagentWorkflowService;
    }

    /**
     * 在独立上下文中执行一个子任务。
     */
    public SubagentTaskResult run(String prompt, ToolContext toolContext) {
        if (prompt == null || prompt.isBlank()) {
            throw new ApplicationException("子智能体 prompt 不能为空");
        }
        if (agentProperties.subagent() == null || !agentProperties.subagent().enabled()) {
            throw new ApplicationException("Subagent 功能当前未启用");
        }

        PermissionContext permissionContext = toolPermissionGuard.requirePermissionContext(toolContext);
        Conversation conversation = buildConversation(permissionContext, toolContext);
        List<RegisteredTool> availableTools = resolveAvailableTools(permissionContext, conversation, prompt);
        SubagentWorkflowService.ExecutionWorkflow executionWorkflow = subagentWorkflowService.start(
                permissionContext.userId(),
                conversation,
                prompt,
                getLong(toolContext, ToolContextKeys.WORKFLOW_ID),
                getLong(toolContext, ToolContextKeys.ROOT_TASK_ID),
                getInteger(toolContext, ToolContextKeys.STEP_INDEX)
        );

        MemoryContext memoryContext = new MemoryContext(List.of(), List.of(), List.of(), "");
        TaskPlan taskPlan = null;
        if (agentProperties.planning() != null
                && agentProperties.planning().enabled()
                && agentProperties.subagent().planningEnabled()) {
            taskPlan = taskPlanningService.plan(
                    AgentReasoningMode.LOOP,
                    prompt,
                    memoryContext,
                    availableTools
            ).body();
            subagentWorkflowService.recordTaskPlan(permissionContext.userId(), executionWorkflow, taskPlan);
        }

        List<Map<String, Object>> scratchpad = new ArrayList<>();
        Set<String> usedTools = new LinkedHashSet<>();
        int maxTurns = Math.max(agentProperties.subagent().maxTurns(), 1);
        int maxConsecutiveNoProgress = Math.max(agentProperties.subagent().maxConsecutiveNoProgress(), 1);
        int maxRepeatedAction = Math.max(agentProperties.subagent().maxRepeatedAction(), 1);
        int executedSteps = 0;
        int consecutiveNoProgress = 0;
        int repeatedActionCount = 0;
        String lastActionSignature = null;
        String lastObservation = null;
        SubagentCompletionType fallbackType = null;
        String completionReason = null;

        try {
            for (int stepIndex = 1; stepIndex <= maxTurns; stepIndex++) {
                AgentStepPlannerService.StructuredResult<AgentStepPlan> result = agentStepPlannerService.planNextStep(
                        AgentReasoningMode.LOOP,
                        prompt,
                        memoryContext,
                        availableTools,
                        scratchpad,
                        stepIndex,
                        maxTurns,
                        taskPlan
                );
                AgentStepPlan stepPlan = result.body();
                executedSteps = stepIndex;
                validateStepPlan(stepPlan);
                subagentWorkflowService.recordPlanningStep(
                        permissionContext.userId(),
                        executionWorkflow,
                        stepIndex,
                        stepPlan.thought(),
                        stepPlan.actionType().name(),
                        stepPlan.toolName(),
                        stepPlan.finalAnswer()
                );

                String actionSignature = buildActionSignature(stepPlan);
                if (actionSignature.equals(lastActionSignature)) {
                    repeatedActionCount++;
                } else {
                    repeatedActionCount = 1;
                    lastActionSignature = actionSignature;
                }
                if (repeatedActionCount >= maxRepeatedAction && stepIndex > 1) {
                    fallbackType = SubagentCompletionType.REPEATED_ACTION_GUARD;
                    completionReason = "子智能体连续重复相同动作，已触发保护性提前结束";
                    subagentWorkflowService.recordObservation(permissionContext.userId(), executionWorkflow, stepIndex, completionReason);
                    scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), "REPEAT_GUARD", completionReason));
                    break;
                }

                if (stepPlan.actionType() == AgentActionType.FINAL) {
                    return completeSuccess(
                            permissionContext.userId(),
                            executionWorkflow,
                            stepPlan.finalAnswer(),
                            taskPlan,
                            executedSteps,
                            usedTools,
                            false,
                            SubagentCompletionType.FINAL,
                            null
                    );
                }

                if (!agentToolExecutorService.isAvailableTool(stepPlan.toolName(), availableTools)) {
                    String observation = "工具不可用或未在子智能体白名单中暴露: " + stepPlan.toolName();
                    subagentWorkflowService.recordObservation(permissionContext.userId(), executionWorkflow, stepIndex, observation);
                    scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), "UNAVAILABLE:" + stepPlan.toolName(), observation));
                    consecutiveNoProgress++;
                    if (consecutiveNoProgress >= maxConsecutiveNoProgress) {
                        fallbackType = SubagentCompletionType.NO_PROGRESS_GUARD;
                        completionReason = "子智能体连续多步没有获得有效进展，已触发保护性提前结束";
                        subagentWorkflowService.recordObservation(permissionContext.userId(), executionWorkflow, stepIndex, completionReason);
                        scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), "NO_PROGRESS_GUARD", completionReason));
                        break;
                    }
                    continue;
                }

                try {
                    AgentToolExecutorService.ToolExecutionOutcome toolResult = agentToolExecutorService.execute(
                            stepPlan.toolName(),
                            stepPlan.toolInput(),
                            permissionContext,
                            conversation
                    );
                    usedTools.add(toolResult.toolName());
                    subagentWorkflowService.recordToolExecution(
                            permissionContext.userId(),
                            executionWorkflow,
                            stepIndex,
                            toolResult.toolName(),
                            stepPlan.toolInput(),
                            toolResult.observation()
                    );
                    scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), toolResult.toolName(), toolResult.observation()));
                    if (toolResult.returnDirect()) {
                        return completeSuccess(
                                permissionContext.userId(),
                                executionWorkflow,
                                toolResult.observation(),
                                taskPlan,
                                executedSteps,
                                usedTools,
                                false,
                                SubagentCompletionType.RETURN_DIRECT,
                                null
                        );
                    }
                    if (isNoProgressObservation(lastObservation, toolResult.observation())) {
                        consecutiveNoProgress++;
                    } else {
                        consecutiveNoProgress = 0;
                    }
                    lastObservation = toolResult.observation();
                    if (consecutiveNoProgress >= maxConsecutiveNoProgress) {
                        fallbackType = SubagentCompletionType.NO_PROGRESS_GUARD;
                        completionReason = "子智能体连续观测到重复或空结果，已触发保护性提前结束";
                        subagentWorkflowService.recordObservation(permissionContext.userId(), executionWorkflow, stepIndex, completionReason);
                        scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), "NO_PROGRESS_GUARD", completionReason));
                        break;
                    }
                } catch (Exception exception) {
                    String observation = "工具执行失败: " + exception.getMessage();
                    subagentWorkflowService.recordObservation(permissionContext.userId(), executionWorkflow, stepIndex, observation);
                    scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), stepPlan.toolName(), observation));
                    consecutiveNoProgress++;
                    if (consecutiveNoProgress >= maxConsecutiveNoProgress) {
                        fallbackType = SubagentCompletionType.NO_PROGRESS_GUARD;
                        completionReason = "子智能体连续多步没有获得有效进展，已触发保护性提前结束";
                        subagentWorkflowService.recordObservation(permissionContext.userId(), executionWorkflow, stepIndex, completionReason);
                        scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), "NO_PROGRESS_GUARD", completionReason));
                        break;
                    }
                }
            }

            if (fallbackType == null) {
                fallbackType = SubagentCompletionType.MAX_TURNS_FALLBACK;
                completionReason = "子智能体达到最大轮数，按回退策略结束";
            }
            String fallbackSummary = buildFallbackSummary(scratchpad);
            return completeSuccess(
                    permissionContext.userId(),
                    executionWorkflow,
                    fallbackSummary,
                    taskPlan,
                    executedSteps,
                    usedTools,
                    true,
                    fallbackType,
                    completionReason
            );
        } catch (Exception exception) {
            subagentWorkflowService.completeFailure(permissionContext.userId(), executionWorkflow, executedSteps, exception);
            throw exception;
        }
    }

    private Conversation buildConversation(PermissionContext permissionContext, ToolContext toolContext) {
        Long conversationId = getLong(toolContext, ToolContextKeys.CONVERSATION_ID);
        String sessionId = getString(toolContext, ToolContextKeys.SESSION_ID);
        return new Conversation(
                conversationId,
                permissionContext.userId(),
                sessionId == null || sessionId.isBlank() ? "subagent-session" : sessionId,
                "Subagent Session",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private List<RegisteredTool> resolveAvailableTools(
            PermissionContext permissionContext,
            Conversation conversation,
            String prompt
    ) {
        List<String> configuredTools = agentProperties.subagent().allowedTools() == null
                ? List.of()
                : agentProperties.subagent().allowedTools();
        Set<String> allowedTools = new LinkedHashSet<>(configuredTools);
        allowedTools.remove(TASK_TOOL_NAME);
        return toolResolverService.resolve(new ToolResolverRequest(
                        permissionContext.userId(),
                        conversation.id(),
                        prompt,
                        permissionContext.roles(),
                        0
                )).stream()
                .filter(tool -> allowedTools.contains(tool.definition().name()))
                .toList();
    }

    private SubagentTaskResult completeSuccess(
            Long userId,
            SubagentWorkflowService.ExecutionWorkflow executionWorkflow,
            String summary,
            TaskPlan taskPlan,
            int stepCount,
            Set<String> usedTools,
            boolean completedByFallback,
            SubagentCompletionType completionType,
            String completionReason
    ) {
        String finalSummary = summary == null || summary.isBlank() ? "子智能体未生成有效摘要。" : summary;
        String planSummary = taskPlan == null ? null : taskPlan.planSummary();
        List<String> usedToolList = new ArrayList<>(usedTools);
        subagentWorkflowService.completeSuccess(
                userId,
                executionWorkflow,
                finalSummary,
                planSummary,
                stepCount,
                usedToolList,
                completedByFallback,
                completionType,
                completionReason
        );
        return new SubagentTaskResult(
                executionWorkflow.workflowId(),
                finalSummary,
                planSummary,
                stepCount,
                completedByFallback,
                completionType,
                completionReason,
                usedToolList
        );
    }

    private void validateStepPlan(AgentStepPlan stepPlan) {
        if (stepPlan == null || stepPlan.actionType() == null) {
            throw new ApplicationException("子智能体步骤规划结果不完整");
        }
        if (stepPlan.actionType() == AgentActionType.TOOL
                && (stepPlan.toolName() == null || stepPlan.toolName().isBlank())) {
            throw new ApplicationException("子智能体工具步骤缺少 toolName");
        }
        if (stepPlan.actionType() == AgentActionType.FINAL
                && (stepPlan.finalAnswer() == null || stepPlan.finalAnswer().isBlank())) {
            throw new ApplicationException("子智能体最终步骤缺少 finalAnswer");
        }
    }

    private Map<String, Object> buildScratchpadEntry(int stepIndex, String thought, String action, String observation) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("step", stepIndex);
        entry.put("thought", thought == null ? "" : thought);
        entry.put("action", action == null ? "" : action);
        entry.put("observation", observation == null ? "" : observation);
        return entry;
    }

    private String buildActionSignature(AgentStepPlan stepPlan) {
        if (stepPlan.actionType() == AgentActionType.FINAL) {
            return "FINAL:" + String.valueOf(stepPlan.finalAnswer());
        }
        return "TOOL:" + String.valueOf(stepPlan.toolName()) + ":" + String.valueOf(stepPlan.toolInput());
    }

    private boolean isNoProgressObservation(String lastObservation, String currentObservation) {
        if (currentObservation == null || currentObservation.isBlank()) {
            return true;
        }
        return currentObservation.equals(lastObservation);
    }

    private String buildFallbackSummary(List<Map<String, Object>> scratchpad) {
        if (scratchpad == null || scratchpad.isEmpty()) {
            return "子智能体未在限制内形成明确结论。";
        }
        for (int index = scratchpad.size() - 1; index >= 0; index--) {
            Map<String, Object> entry = scratchpad.get(index);
            Object action = entry.get("action");
            if ("REPEAT_GUARD".equals(action) || "NO_PROGRESS_GUARD".equals(action)) {
                continue;
            }
            Object observation = entry.get("observation");
            if (observation instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return "子智能体未在限制内形成明确结论。";
    }

    private Long getLong(ToolContext toolContext, String key) {
        Object value = toolContext == null ? null : toolContext.getContext().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Integer getInteger(ToolContext toolContext, String key) {
        Object value = toolContext == null ? null : toolContext.getContext().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private String getString(ToolContext toolContext, String key) {
        Object value = toolContext == null ? null : toolContext.getContext().get(key);
        return value instanceof String text ? text : null;
    }
}