package com.example.agentplatform.tools.service;

import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.skills.domain.SkillToolChoiceMode;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.tasks.dto.TaskCreateRequest;
import com.example.agentplatform.tasks.dto.TaskStatusUpdateRequest;
import com.example.agentplatform.tasks.service.TaskService;
import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolRiskLevel;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.domain.WorkflowStatus;
import com.example.agentplatform.workflow.dto.WorkflowCreateRequest;
import com.example.agentplatform.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具执行工作流服务测试。
 * 重点验证工具对话会创建工作流骨架，并把工具调用映射成任务记录。
 */
class ToolExecutionWorkflowServiceTest {

    @Test
    void startShouldCreateWorkflowAndAvoidNullMetadata() {
        WorkflowService workflowService = mock(WorkflowService.class);
        TaskService taskService = mock(TaskService.class);
        ToolExecutionWorkflowService service = new ToolExecutionWorkflowService(workflowService, taskService);

        Long userId = 1L;
        Conversation conversation = new Conversation(10L, userId, "sess-1", "tool session", "ACTIVE", now(), now());
        RegisteredTool selectedTool = registeredTool("generate_pdf");
        WorkflowRecord workflowRecord = new WorkflowRecord(
                100L,
                userId,
                "Tool Chat Workflow",
                "工具链对话执行工作流",
                WorkflowStatus.PENDING,
                Map.of(),
                Map.of(),
                null,
                true,
                Map.of(),
                now(),
                now(),
                null,
                null
        );
        TaskRecord routeTask = taskRecord(200L, 100L, userId, "skill-route", TaskStatus.READY);
        TaskRecord loopTask = taskRecord(201L, 100L, userId, "tool-loop", TaskStatus.BLOCKED);

        when(workflowService.create(eq(userId), any(WorkflowCreateRequest.class))).thenReturn(workflowRecord);
        when(workflowService.listTasks(userId, 100L)).thenReturn(List.of(routeTask, loopTask));
        when(taskService.updateStatus(eq(userId), eq(200L), any(TaskStatusUpdateRequest.class))).thenReturn(
                taskRecord(200L, 100L, userId, "skill-route", TaskStatus.COMPLETED)
        );
        when(taskService.updateStatus(eq(userId), eq(201L), any(TaskStatusUpdateRequest.class))).thenReturn(
                taskRecord(201L, 100L, userId, "tool-loop", TaskStatus.RUNNING)
        );

        ToolExecutionWorkflowService.ExecutionWorkflow executionWorkflow = service.start(
                userId,
                conversation,
                "create a pdf",
                null,
                List.of(selectedTool)
        );

        ArgumentCaptor<WorkflowCreateRequest> requestCaptor = ArgumentCaptor.forClass(WorkflowCreateRequest.class);
        verify(workflowService).create(eq(userId), requestCaptor.capture());
        WorkflowCreateRequest createRequest = requestCaptor.getValue();

        assertEquals(100L, executionWorkflow.workflowId());
        assertEquals(201L, executionWorkflow.toolLoopTaskId());
        assertEquals("tool-chat", createRequest.metadata().get("mode"));
        assertEquals(List.of("generate_pdf"), createRequest.metadata().get("toolNames"));
        assertFalse(createRequest.metadata().containsKey("skillId"));

        assertEquals(2, createRequest.tasks().size());
        assertEquals("技能路由", createRequest.tasks().get(0).name());
        assertEquals("工具主循环", createRequest.tasks().get(1).name());
        assertEquals(List.of("generate_pdf"), createRequest.tasks().get(0).metadata().get("toolNames"));
        assertFalse(createRequest.tasks().get(0).metadata().containsKey("skillId"));
    }

    @Test
    void recordToolInvocationShouldCreateAndCompleteToolTask() {
        WorkflowService workflowService = mock(WorkflowService.class);
        TaskService taskService = mock(TaskService.class);
        ToolExecutionWorkflowService service = new ToolExecutionWorkflowService(workflowService, taskService);

        Long userId = 1L;
        ToolExecutionWorkflowService.ExecutionWorkflow executionWorkflow =
                new ToolExecutionWorkflowService.ExecutionWorkflow(100L, 201L);

        TaskRecord invocationTask = taskRecord(300L, 100L, userId, "call-1", TaskStatus.READY);
        when(taskService.create(eq(userId), any(TaskCreateRequest.class))).thenReturn(invocationTask);
        when(taskService.updateStatus(eq(userId), eq(300L), any(TaskStatusUpdateRequest.class))).thenReturn(
                taskRecord(300L, 100L, userId, "call-1", TaskStatus.COMPLETED)
        );

        service.recordToolInvocation(userId, executionWorkflow, "call-1", "generate_pdf", true);

        ArgumentCaptor<TaskCreateRequest> createCaptor = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskService).create(eq(userId), createCaptor.capture());
        TaskCreateRequest createRequest = createCaptor.getValue();
        assertEquals(100L, createRequest.workflowId());
        assertEquals(201L, createRequest.parentTaskId());
        assertEquals("call-1", createRequest.clientTaskKey());
        assertEquals("tool", createRequest.sourceType());
        assertEquals("generate_pdf", createRequest.sourceRef());
        assertEquals(true, createRequest.input().get("returnDirect"));

        ArgumentCaptor<TaskStatusUpdateRequest> updateCaptor = ArgumentCaptor.forClass(TaskStatusUpdateRequest.class);
        verify(taskService).updateStatus(eq(userId), eq(300L), updateCaptor.capture());
        TaskStatusUpdateRequest statusUpdateRequest = updateCaptor.getValue();
        assertEquals(TaskStatus.COMPLETED, statusUpdateRequest.status());
        assertNotNull(statusUpdateRequest.result());
        assertEquals("generate_pdf", statusUpdateRequest.result().get("toolName"));
    }

    @Test
    void startShouldCarryResolvedSkillMetadata() {
        WorkflowService workflowService = mock(WorkflowService.class);
        TaskService taskService = mock(TaskService.class);
        ToolExecutionWorkflowService service = new ToolExecutionWorkflowService(workflowService, taskService);

        Long userId = 1L;
        Conversation conversation = new Conversation(10L, userId, "sess-2", "tool session", "ACTIVE", now(), now());
        RegisteredTool selectedTool = registeredTool("search_web");
        ResolvedSkill resolvedSkill = new ResolvedSkill(
                new SkillDefinition(
                        "web-research",
                        "联网研究",
                        "用于联网搜索和抓取网页",
                        true,
                        List.of("search"),
                        List.of("搜索"),
                        List.of("search_web", "fetch_webpage"),
                        SkillToolChoiceMode.ALLOWED,
                        List.of(),
                        "你是联网研究技能。",
                        "classpath:skills/web-research"
                ),
                "问题明显需要联网资料",
                "structured"
        );
        WorkflowRecord workflowRecord = new WorkflowRecord(
                101L,
                userId,
                "Tool Chat Workflow",
                "工具链对话执行工作流",
                WorkflowStatus.PENDING,
                Map.of(),
                Map.of(),
                null,
                true,
                Map.of(),
                now(),
                now(),
                null,
                null
        );
        when(workflowService.create(eq(userId), any(WorkflowCreateRequest.class))).thenReturn(workflowRecord);
        when(workflowService.listTasks(userId, 101L)).thenReturn(List.of(
                taskRecord(210L, 101L, userId, "skill-route", TaskStatus.READY),
                taskRecord(211L, 101L, userId, "tool-loop", TaskStatus.BLOCKED)
        ));
        when(taskService.updateStatus(eq(userId), eq(210L), any(TaskStatusUpdateRequest.class))).thenReturn(
                taskRecord(210L, 101L, userId, "skill-route", TaskStatus.COMPLETED)
        );
        when(taskService.updateStatus(eq(userId), eq(211L), any(TaskStatusUpdateRequest.class))).thenReturn(
                taskRecord(211L, 101L, userId, "tool-loop", TaskStatus.RUNNING)
        );

        service.start(userId, conversation, "search postgres", resolvedSkill, List.of(selectedTool));

        ArgumentCaptor<WorkflowCreateRequest> requestCaptor = ArgumentCaptor.forClass(WorkflowCreateRequest.class);
        verify(workflowService).create(eq(userId), requestCaptor.capture());
        WorkflowCreateRequest createRequest = requestCaptor.getValue();
        assertEquals("web-research", createRequest.metadata().get("skillId"));
        assertEquals("structured", createRequest.metadata().get("skillRouteStrategy"));
        assertTrue(createRequest.tasks().get(0).metadata().containsKey("skillId"));
    }

    private RegisteredTool registeredTool(String toolName) {
        return new RegisteredTool(
                new PlatformToolDefinition(
                        toolName,
                        toolName,
                        toolName,
                        "test tool",
                        "{}",
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        1000L,
                        ToolRiskLevel.LOW,
                        Set.of("CHAT_USER"),
                        List.of(),
                        List.of()
                ),
                mock(ToolCallback.class)
        );
    }

    private TaskRecord taskRecord(Long taskId, Long workflowId, Long userId, String clientTaskKey, TaskStatus status) {
        return new TaskRecord(
                taskId,
                workflowId,
                null,
                userId,
                clientTaskKey,
                clientTaskKey,
                clientTaskKey + " description",
                "TEST",
                status,
                0,
                Map.of(),
                Map.of(),
                null,
                0,
                0,
                false,
                null,
                null,
                Map.of(),
                List.of(),
                now(),
                now(),
                null,
                null
        );
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
