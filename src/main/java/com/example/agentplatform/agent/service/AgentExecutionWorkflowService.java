package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentActionType;
import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.agent.domain.TaskPlanStep;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.tasks.dto.TaskCreateRequest;
import com.example.agentplatform.tasks.dto.TaskStatusUpdateRequest;
import com.example.agentplatform.tasks.service.TaskService;
import com.example.agentplatform.workflow.dto.WorkflowCreateRequest;
import com.example.agentplatform.workflow.dto.WorkflowTaskCreateRequest;
import com.example.agentplatform.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行工作流跟踪服务。
 * 把一次 CoT、ReAct 或 Agent Loop 执行过程映射成 workflow 和步骤任务，便于复用已有任务体系。
 */
@Service
public class AgentExecutionWorkflowService {

    private static final String ROOT_TASK_KEY = "agent-run-root";

    private final WorkflowService workflowService;
    private final TaskService taskService;

    public AgentExecutionWorkflowService(WorkflowService workflowService, TaskService taskService) {
        this.workflowService = workflowService;
        this.taskService = taskService;
    }

    /**
     * 创建一次 Agent 运行的根工作流，并把根任务切换到运行中。
     */
    public ExecutionWorkflow start(
            Long userId,
            Conversation conversation,
            String message,
            AgentReasoningMode mode
    ) {
        var workflow = workflowService.create(userId, new WorkflowCreateRequest(
                "Agent Execution Workflow",
                "Agent 推理与工具执行工作流",
                true,
                buildWorkflowInput(conversation, message),
                buildWorkflowMetadata(conversation, mode),
                List.of(new WorkflowTaskCreateRequest(
                        ROOT_TASK_KEY,
                        null,
                        List.of(),
                        "Agent 主任务",
                        "跟踪一次 Agent 执行过程",
                        "AGENT_RUN",
                        0,
                        "conversation",
                        String.valueOf(conversation.id()),
                        buildRootInput(message),
                        Map.of("mode", mode.name())
                ))
        ));
        TaskRecord rootTask = workflowService.listTasks(userId, workflow.id()).stream()
                .filter(task -> ROOT_TASK_KEY.equals(task.clientTaskKey()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException("Agent 工作流缺少根任务"));
        TaskRecord runningRootTask = taskService.updateStatus(userId, rootTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.RUNNING,
                0,
                Map.of("mode", mode.name()),
                null
        ));
        return new ExecutionWorkflow(workflow.id(), runningRootTask.id(), mode);
    }

    /**
     * 记录一次推理步骤。
     */
    public void recordPlanningStep(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            int stepIndex,
            String thought,
            AgentActionType actionType,
            String toolName,
            String finalAnswer
    ) {
        TaskRecord task = createStepTask(
                userId,
                executionWorkflow,
                "reason-" + stepIndex,
                "推理步骤 " + stepIndex,
                "记录当前一步的推理与决策",
                "AGENT_REASON",
                Map.of("step", stepIndex),
                Map.of("mode", executionWorkflow.mode().name())
        );
        taskService.updateStatus(userId, task.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                buildPlanningResult(stepIndex, thought, actionType, toolName, finalAnswer),
                null
        ));
    }

    /**
     * 记录一次结构化任务计划。
     */
    public void recordTaskPlan(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            TaskPlan taskPlan
    ) {
        if (taskPlan == null) {
            return;
        }
        TaskRecord planTask = createTask(
                userId,
                executionWorkflow,
                executionWorkflow.rootTaskId(),
                "task-plan",
                "任务计划",
                "记录 Agent 在执行前生成的结构化任务计划",
                "AGENT_PLAN",
                buildTaskPlanInput(taskPlan),
                Map.of("mode", executionWorkflow.mode().name())
        );
        taskService.updateStatus(userId, planTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                buildTaskPlanResult(taskPlan),
                null
        ));
        if (taskPlan.steps() == null || taskPlan.steps().isEmpty()) {
            return;
        }
        for (int index = 0; index < taskPlan.steps().size(); index++) {
            TaskPlanStep step = taskPlan.steps().get(index);
            TaskRecord stepTask = createTask(
                    userId,
                    executionWorkflow,
                    planTask.id(),
                    "task-plan-step-" + (index + 1),
                    "计划步骤 " + (index + 1),
                    "记录任务计划中的单个步骤",
                    "AGENT_PLAN_STEP",
                    buildTaskPlanStepInput(index + 1, step),
                    Map.of("mode", executionWorkflow.mode().name())
            );
            taskService.updateStatus(userId, stepTask.id(), new TaskStatusUpdateRequest(
                    TaskStatus.COMPLETED,
                    100,
                    buildTaskPlanStepResult(index + 1, step),
                    null
            ));
        }
    }

    /**
     * 记录一次工具执行步骤。
     */
    public void recordToolExecution(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            int stepIndex,
            String toolName,
            Map<String, Object> toolInput,
            String observation,
            boolean returnDirect
    ) {
        TaskRecord toolTask = createStepTask(
                userId,
                executionWorkflow,
                "tool-" + stepIndex + "-" + toolName,
                "工具执行 " + stepIndex,
                "记录 Agent 选择的工具调用",
                "AGENT_TOOL",
                buildToolInput(stepIndex, toolName, toolInput),
                Map.of("mode", executionWorkflow.mode().name())
        );
        taskService.updateStatus(userId, toolTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of(
                        "step", stepIndex,
                        "toolName", toolName,
                        "returnDirect", returnDirect
                ),
                null
        ));

        TaskRecord observationTask = createStepTask(
                userId,
                executionWorkflow,
                "observation-" + stepIndex + "-" + toolName,
                "观察结果 " + stepIndex,
                "记录工具调用后的 observation",
                "AGENT_OBSERVATION",
                Map.of("step", stepIndex, "toolName", toolName),
                Map.of("mode", executionWorkflow.mode().name())
        );
        taskService.updateStatus(userId, observationTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of(
                        "step", stepIndex,
                        "toolName", toolName,
                        "observation", observation
                ),
                null
        ));
    }

    /**
     * 记录一次 RAG 检索步骤及其摘要结果。
     */
    public void recordRetrieval(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            int stepIndex,
            String ragQuery,
            int sourceCount,
            String observation
    ) {
        TaskRecord retrievalTask = createStepTask(
                userId,
                executionWorkflow,
                "rag-" + stepIndex,
                "检索执行 " + stepIndex,
                "记录 Agent 在当前步骤触发的知识检索",
                "AGENT_RAG",
                Map.of(
                        "step", stepIndex,
                        "ragQuery", ragQuery
                ),
                Map.of("mode", executionWorkflow.mode().name())
        );
        taskService.updateStatus(userId, retrievalTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of(
                        "step", stepIndex,
                        "ragQuery", ragQuery,
                        "sourceCount", sourceCount
                ),
                null
        ));

        TaskRecord observationTask = createStepTask(
                userId,
                executionWorkflow,
                "rag-observation-" + stepIndex,
                "检索观察 " + stepIndex,
                "记录 RAG 检索返回的观察结果",
                "AGENT_OBSERVATION",
                Map.of("step", stepIndex, "ragQuery", ragQuery),
                Map.of("mode", executionWorkflow.mode().name())
        );
        taskService.updateStatus(userId, observationTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of(
                        "step", stepIndex,
                        "ragQuery", ragQuery,
                        "observation", observation
                ),
                null
        ));
    }

    /**
     * 记录一次无工具 observation，例如工具不可用或执行失败。
     */
    public void recordObservation(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            int stepIndex,
            String observation
    ) {
        TaskRecord task = createStepTask(
                userId,
                executionWorkflow,
                "observation-" + stepIndex,
                "观察结果 " + stepIndex,
                "记录当前一步的 observation",
                "AGENT_OBSERVATION",
                Map.of("step", stepIndex),
                Map.of("mode", executionWorkflow.mode().name())
        );
        taskService.updateStatus(userId, task.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of("step", stepIndex, "observation", observation),
                null
        ));
    }

    /**
     * 标记 Agent 成功结束。
     */
    public void completeSuccess(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            String answer,
            String reasoningSummary,
            int stepCount,
            List<String> toolNames
    ) {
        taskService.updateStatus(userId, executionWorkflow.rootTaskId(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                buildCompletionResult(answer, reasoningSummary, stepCount, toolNames),
                null
        ));
    }

    /**
     * 标记 Agent 失败结束。
     */
    public void completeFailure(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            int stepCount,
            Exception exception
    ) {
        if (executionWorkflow == null) {
            return;
        }
        taskService.updateStatus(userId, executionWorkflow.rootTaskId(), new TaskStatusUpdateRequest(
                TaskStatus.FAILED,
                100,
                Map.of("stepCount", stepCount),
                exception.getMessage()
        ));
    }

    private TaskRecord createStepTask(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            String clientTaskKey,
            String name,
            String description,
            String taskType,
            Map<String, Object> input,
            Map<String, Object> metadata
    ) {
        return createTask(
                userId,
                executionWorkflow,
                executionWorkflow.rootTaskId(),
                clientTaskKey,
                name,
                description,
                taskType,
                input,
                metadata
        );
    }

    private TaskRecord createTask(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            Long parentTaskId,
            String clientTaskKey,
            String name,
            String description,
            String taskType,
            Map<String, Object> input,
            Map<String, Object> metadata
    ) {
        TaskRecord task = taskService.create(userId, new TaskCreateRequest(
                executionWorkflow.workflowId(),
                parentTaskId,
                clientTaskKey,
                name,
                description,
                taskType,
                0,
                "agent",
                executionWorkflow.mode().name(),
                input,
                metadata,
                List.of()
        ));
        return taskService.updateStatus(userId, task.id(), new TaskStatusUpdateRequest(
                TaskStatus.RUNNING,
                0,
                null,
                null
        ));
    }

    private Map<String, Object> buildWorkflowInput(Conversation conversation, String message) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("conversationId", conversation.id());
        input.put("sessionId", conversation.sessionId());
        input.put("message", message);
        return input;
    }

    private Map<String, Object> buildWorkflowMetadata(Conversation conversation, AgentReasoningMode mode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", mode.name());
        metadata.put("conversationId", conversation.id());
        metadata.put("sessionId", conversation.sessionId());
        return metadata;
    }

    private Map<String, Object> buildRootInput(String message) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", message);
        return input;
    }

    private Map<String, Object> buildPlanningResult(
            int stepIndex,
            String thought,
            AgentActionType actionType,
            String toolName,
            String finalAnswer
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step", stepIndex);
        result.put("thought", thought);
        result.put("actionType", actionType == null ? null : actionType.name());
        if (toolName != null && !toolName.isBlank()) {
            result.put("toolName", toolName);
        }
        if (finalAnswer != null && !finalAnswer.isBlank()) {
            result.put("finalAnswer", finalAnswer);
        }
        return result;
    }

    private Map<String, Object> buildToolInput(int stepIndex, String toolName, Map<String, Object> toolInput) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("step", stepIndex);
        input.put("toolName", toolName);
        input.put("toolInput", toolInput == null ? Map.of() : toolInput);
        return input;
    }

    private Map<String, Object> buildCompletionResult(
            String answer,
            String reasoningSummary,
            int stepCount,
            List<String> toolNames
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("reasoningSummary", reasoningSummary);
        result.put("stepCount", stepCount);
        result.put("toolNames", toolNames == null ? List.of() : toolNames);
        return result;
    }

    private Map<String, Object> buildTaskPlanInput(TaskPlan taskPlan) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("goal", taskPlan.goal());
        input.put("planSummary", taskPlan.planSummary());
        input.put("stepCount", taskPlan.steps() == null ? 0 : taskPlan.steps().size());
        return input;
    }

    private Map<String, Object> buildTaskPlanResult(TaskPlan taskPlan) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goal", taskPlan.goal());
        result.put("planSummary", taskPlan.planSummary());
        result.put("stepCount", taskPlan.steps() == null ? 0 : taskPlan.steps().size());
        return result;
    }

    private Map<String, Object> buildTaskPlanStepInput(int planIndex, TaskPlanStep step) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("planIndex", planIndex);
        input.put("stepId", step.stepId());
        input.put("title", step.title());
        input.put("description", step.description());
        input.put("dependsOnStepIds", step.dependsOnStepIds() == null ? List.of() : step.dependsOnStepIds());
        return input;
    }

    private Map<String, Object> buildTaskPlanStepResult(int planIndex, TaskPlanStep step) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planIndex", planIndex);
        result.put("stepId", step.stepId());
        result.put("title", step.title());
        result.put("description", step.description());
        result.put("suggestedTools", step.suggestedTools() == null ? List.of() : step.suggestedTools());
        result.put("doneCondition", step.doneCondition());
        result.put("skippable", step.skippable());
        result.put("dependsOnStepIds", step.dependsOnStepIds() == null ? List.of() : step.dependsOnStepIds());
        return result;
    }

    /**
     * 一次 Agent 执行的工作流上下文。
     */
    public record ExecutionWorkflow(
            Long workflowId,
            Long rootTaskId,
            AgentReasoningMode mode
    ) {
    }
}
