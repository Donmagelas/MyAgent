package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.SubagentCompletionType;
import com.example.agentplatform.agent.domain.TaskPlan;
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
 * subagent 工作流跟踪服务。
 * 为每次子任务执行创建独立 workflow，并把关键步骤映射为 task。
 */
@Service
public class SubagentWorkflowService {

    private static final String ROOT_TASK_KEY = "subagent-root";

    private final WorkflowService workflowService;
    private final TaskService taskService;

    public SubagentWorkflowService(WorkflowService workflowService, TaskService taskService) {
        this.workflowService = workflowService;
        this.taskService = taskService;
    }

    /**
     * 创建一次 subagent 运行的根工作流。
     */
    public ExecutionWorkflow start(
            Long userId,
            Conversation conversation,
            String prompt,
            Long parentWorkflowId,
            Long parentTaskId,
            Integer parentStep
    ) {
        var workflow = workflowService.create(userId, new WorkflowCreateRequest(
                "Subagent Workflow",
                "子智能体局部任务执行工作流",
                true,
                buildWorkflowInput(conversation, prompt),
                buildWorkflowMetadata(conversation, parentWorkflowId, parentTaskId, parentStep),
                List.of(new WorkflowTaskCreateRequest(
                        ROOT_TASK_KEY,
                        null,
                        List.of(),
                        "子智能体主任务",
                        "跟踪一次独立上下文的子任务执行",
                        "SUBAGENT_RUN",
                        0,
                        "subagent",
                        String.valueOf(conversation.id()),
                        Map.of("prompt", prompt),
                        Map.of("conversationId", conversation.id())
                ))
        ));
        TaskRecord rootTask = workflowService.listTasks(userId, workflow.id()).stream()
                .filter(task -> ROOT_TASK_KEY.equals(task.clientTaskKey()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException("子智能体工作流缺少根任务"));
        TaskRecord runningRootTask = taskService.updateStatus(userId, rootTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.RUNNING,
                0,
                Map.of("conversationId", conversation.id()),
                null
        ));
        return new ExecutionWorkflow(workflow.id(), runningRootTask.id());
    }

    /**
     * 记录 subagent 生成的任务计划。
     */
    public void recordTaskPlan(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            TaskPlan taskPlan
    ) {
        if (taskPlan == null) {
            return;
        }
        TaskRecord task = createStepTask(
                userId,
                executionWorkflow,
                "subagent-plan",
                "子智能体任务规划",
                "记录子智能体在独立上下文中生成的任务计划",
                "SUBAGENT_PLAN",
                Map.of("goal", taskPlan.goal()),
                Map.of()
        );
        taskService.updateStatus(userId, task.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of(
                        "goal", taskPlan.goal(),
                        "planSummary", taskPlan.planSummary(),
                        "stepCount", taskPlan.steps() == null ? 0 : taskPlan.steps().size(),
                        "steps", taskPlan.steps() == null ? List.of() : taskPlan.steps()
                ),
                null
        ));
    }

    /**
     * 记录 subagent 的一次推理步骤。
     */
    public void recordPlanningStep(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            int stepIndex,
            String thought,
            String actionType,
            String toolName,
            String finalAnswer
    ) {
        TaskRecord task = createStepTask(
                userId,
                executionWorkflow,
                "subagent-reason-" + stepIndex,
                "子智能体推理步骤 " + stepIndex,
                "记录子智能体当前步骤的推理结果",
                "SUBAGENT_REASON",
                Map.of("step", stepIndex),
                Map.of()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step", stepIndex);
        result.put("thought", thought);
        result.put("actionType", actionType);
        if (toolName != null && !toolName.isBlank()) {
            result.put("toolName", toolName);
        }
        if (finalAnswer != null && !finalAnswer.isBlank()) {
            result.put("finalAnswer", finalAnswer);
        }
        taskService.updateStatus(userId, task.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                result,
                null
        ));
    }

    /**
     * 记录 subagent 的一次工具执行。
     */
    public void recordToolExecution(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            int stepIndex,
            String toolName,
            Map<String, Object> toolInput,
            String observation
    ) {
        TaskRecord toolTask = createStepTask(
                userId,
                executionWorkflow,
                "subagent-tool-" + stepIndex + "-" + toolName,
                "子智能体工具执行 " + stepIndex,
                "记录子智能体调用的工具",
                "SUBAGENT_TOOL",
                Map.of("step", stepIndex, "toolName", toolName),
                Map.of()
        );
        taskService.updateStatus(userId, toolTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of(
                        "step", stepIndex,
                        "toolName", toolName,
                        "toolInput", toolInput == null ? Map.of() : toolInput
                ),
                null
        ));

        TaskRecord observationTask = createStepTask(
                userId,
                executionWorkflow,
                "subagent-observation-" + stepIndex + "-" + toolName,
                "子智能体观察结果 " + stepIndex,
                "记录子智能体调用工具后的观察结果",
                "SUBAGENT_OBSERVATION",
                Map.of("step", stepIndex, "toolName", toolName),
                Map.of()
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
     * 记录 subagent 的普通观察结果。
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
                "subagent-observation-" + stepIndex,
                "子智能体观察结果 " + stepIndex,
                "记录子智能体在当前步骤产生的观察信息",
                "SUBAGENT_OBSERVATION",
                Map.of("step", stepIndex),
                Map.of()
        );
        taskService.updateStatus(userId, task.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of("step", stepIndex, "observation", observation),
                null
        ));
    }

    /**
     * 标记 subagent 成功完成。
     */
    public void completeSuccess(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            String summary,
            String planSummary,
            int stepCount,
            List<String> usedTools,
            boolean completedByFallback,
            SubagentCompletionType completionType,
            String completionReason
    ) {
        taskService.updateStatus(userId, executionWorkflow.rootTaskId(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of(
                        "summary", summary,
                        "planSummary", planSummary == null ? "" : planSummary,
                        "stepCount", stepCount,
                        "usedTools", usedTools == null ? List.of() : usedTools,
                        "completedByFallback", completedByFallback,
                        "completionType", completionType == null ? null : completionType.name(),
                        "completionReason", completionReason == null ? "" : completionReason
                ),
                null
        ));
    }

    /**
     * 标记 subagent 失败。
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
        TaskRecord task = taskService.create(userId, new TaskCreateRequest(
                executionWorkflow.workflowId(),
                executionWorkflow.rootTaskId(),
                clientTaskKey,
                name,
                description,
                taskType,
                0,
                "subagent",
                String.valueOf(executionWorkflow.workflowId()),
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

    private Map<String, Object> buildWorkflowInput(Conversation conversation, String prompt) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("conversationId", conversation.id());
        input.put("sessionId", conversation.sessionId());
        input.put("prompt", prompt);
        return input;
    }

    private Map<String, Object> buildWorkflowMetadata(
            Conversation conversation,
            Long parentWorkflowId,
            Long parentTaskId,
            Integer parentStep
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("conversationId", conversation.id());
        metadata.put("sessionId", conversation.sessionId());
        if (parentWorkflowId != null) {
            metadata.put("parentWorkflowId", parentWorkflowId);
        }
        if (parentTaskId != null) {
            metadata.put("parentTaskId", parentTaskId);
        }
        if (parentStep != null) {
            metadata.put("parentStep", parentStep);
        }
        return metadata;
    }

    /**
     * subagent 工作流上下文。
     */
    public record ExecutionWorkflow(
            Long workflowId,
            Long rootTaskId
    ) {
    }
}