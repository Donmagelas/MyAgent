package com.example.agentplatform.tools.service;

import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.tasks.dto.TaskCreateRequest;
import com.example.agentplatform.tasks.dto.TaskStatusUpdateRequest;
import com.example.agentplatform.tasks.service.TaskService;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.dto.WorkflowCreateRequest;
import com.example.agentplatform.workflow.dto.WorkflowTaskCreateRequest;
import com.example.agentplatform.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行工作流跟踪服务。
 * 把一次 tool chat 的执行过程映射成一个 workflow 和若干 task，便于后续查询与观察。
 */
@Service
public class ToolExecutionWorkflowService {

    private static final String ROUTE_TASK_KEY = "skill-route";
    private static final String TOOL_LOOP_TASK_KEY = "tool-loop";

    private final WorkflowService workflowService;
    private final TaskService taskService;

    public ToolExecutionWorkflowService(WorkflowService workflowService, TaskService taskService) {
        this.workflowService = workflowService;
        this.taskService = taskService;
    }

    /**
     * 创建一次工具执行工作流，并返回主循环任务。
     */
    public ExecutionWorkflow start(
            Long userId,
            Conversation conversation,
            String message,
            ResolvedSkill resolvedSkill,
            List<RegisteredTool> selectedTools
    ) {
        WorkflowRecord workflow = workflowService.create(userId, new WorkflowCreateRequest(
                "Tool Chat Workflow",
                "工具链对话执行工作流",
                true,
                buildWorkflowInput(conversation, message),
                buildWorkflowMetadata(resolvedSkill, selectedTools),
                List.of(
                        new WorkflowTaskCreateRequest(
                                ROUTE_TASK_KEY,
                                null,
                                List.of(),
                                "技能路由",
                                "解析 skill 并收缩候选工具",
                                "SKILL_ROUTE",
                                0,
                                "skill",
                                resolvedSkill == null ? null : resolvedSkill.skillDefinition().id(),
                                buildMessageInput(message),
                                buildRouteMetadata(resolvedSkill, selectedTools)
                        ),
                        new WorkflowTaskCreateRequest(
                                TOOL_LOOP_TASK_KEY,
                                null,
                                List.of(ROUTE_TASK_KEY),
                                "工具主循环",
                                "执行模型推理与工具调用主循环",
                                "TOOL_LOOP",
                                0,
                                "conversation",
                                String.valueOf(conversation.id()),
                                buildMessageInput(message),
                                Map.of()
                        )
                )
        ));

        List<TaskRecord> tasks = workflowService.listTasks(userId, workflow.id());
        TaskRecord routeTask = findTask(tasks, ROUTE_TASK_KEY);
        TaskRecord toolLoopTask = findTask(tasks, TOOL_LOOP_TASK_KEY);

        taskService.updateStatus(userId, routeTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                buildRouteResult(resolvedSkill, selectedTools),
                null
        ));
        TaskRecord runningToolLoopTask = taskService.updateStatus(userId, toolLoopTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.RUNNING,
                0,
                null,
                null
        ));
        return new ExecutionWorkflow(workflow.id(), runningToolLoopTask.id());
    }

    /**
     * 记录一条工具调用任务。
     */
    public void recordToolInvocation(
            Long userId,
            ExecutionWorkflow executionWorkflow,
            String toolCallId,
            String toolName,
            boolean returnDirect
    ) {
        TaskRecord invocationTask = taskService.create(userId, new TaskCreateRequest(
                executionWorkflow.workflowId(),
                executionWorkflow.toolLoopTaskId(),
                toolCallId,
                "工具调用:" + toolName,
                "执行一次工具调用",
                "TOOL_CALL",
                0,
                "tool",
                toolName,
                Map.of(
                        "toolCallId", toolCallId,
                        "toolName", toolName,
                        "returnDirect", returnDirect
                ),
                Map.of(),
                List.of()
        ));
        taskService.updateStatus(userId, invocationTask.id(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                Map.of(
                        "toolCallId", toolCallId,
                        "toolName", toolName,
                        "returnDirect", returnDirect
                ),
                null
        ));
    }

    /**
     * 标记工具主循环成功结束。
     */
    public void completeSuccess(Long userId, ExecutionWorkflow executionWorkflow, String answer, String directToolName) {
        taskService.updateStatus(userId, executionWorkflow.toolLoopTaskId(), new TaskStatusUpdateRequest(
                TaskStatus.COMPLETED,
                100,
                buildCompletionResult(answer, directToolName),
                null
        ));
    }

    /**
     * 标记工具主循环失败。
     */
    public void completeFailure(Long userId, ExecutionWorkflow executionWorkflow, Exception exception) {
        if (executionWorkflow == null) {
            return;
        }
        taskService.updateStatus(userId, executionWorkflow.toolLoopTaskId(), new TaskStatusUpdateRequest(
                TaskStatus.FAILED,
                100,
                null,
                exception.getMessage()
        ));
    }

    private TaskRecord findTask(List<TaskRecord> tasks, String clientTaskKey) {
        return tasks.stream()
                .filter(task -> clientTaskKey.equals(task.clientTaskKey()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException("工具执行工作流缺少任务: " + clientTaskKey));
    }

    private Map<String, Object> buildWorkflowInput(Conversation conversation, String message) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("conversationId", conversation.id());
        input.put("sessionId", conversation.sessionId());
        input.put("message", message);
        return input;
    }

    private Map<String, Object> buildWorkflowMetadata(ResolvedSkill resolvedSkill, List<RegisteredTool> selectedTools) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", "tool-chat");
        if (resolvedSkill != null) {
            metadata.put("skillId", resolvedSkill.skillDefinition().id());
            metadata.put("skillRouteStrategy", resolvedSkill.routeStrategy());
        }
        metadata.put("toolNames", selectedTools.stream().map(tool -> tool.definition().name()).toList());
        return metadata;
    }

    private Map<String, Object> buildRouteResult(ResolvedSkill resolvedSkill, List<RegisteredTool> selectedTools) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (resolvedSkill != null) {
            result.put("skillId", resolvedSkill.skillDefinition().id());
            result.put("routeStrategy", resolvedSkill.routeStrategy());
            result.put("reason", resolvedSkill.reason());
        }
        result.put("toolNames", selectedTools.stream().map(tool -> tool.definition().name()).toList());
        return result;
    }

    private Map<String, Object> buildMessageInput(String message) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", message);
        return input;
    }

    private Map<String, Object> buildRouteMetadata(ResolvedSkill resolvedSkill, List<RegisteredTool> selectedTools) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (resolvedSkill != null) {
            metadata.put("skillId", resolvedSkill.skillDefinition().id());
        }
        metadata.put("toolNames", selectedTools.stream().map(tool -> tool.definition().name()).toList());
        return metadata;
    }

    private Map<String, Object> buildCompletionResult(String answer, String directToolName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        if (directToolName != null && !directToolName.isBlank()) {
            result.put("directToolName", directToolName);
        }
        return result;
    }

    /**
     * 一次工具执行的工作流上下文。
     */
    public record ExecutionWorkflow(
            Long workflowId,
            Long toolLoopTaskId
    ) {
    }
}
