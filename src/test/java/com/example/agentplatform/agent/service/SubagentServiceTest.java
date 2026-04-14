package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentActionType;
import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.AgentStepPlan;
import com.example.agentplatform.agent.domain.SubagentCompletionType;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.agent.domain.TaskPlanStep;
import com.example.agentplatform.config.AgentProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolContextKeys;
import com.example.agentplatform.tools.domain.ToolRiskLevel;
import com.example.agentplatform.tools.dto.SubagentTaskResult;
import com.example.agentplatform.tools.service.ToolPermissionGuard;
import com.example.agentplatform.tools.service.ToolResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * subagent 服务测试。
 * 验证独立上下文执行、工具白名单过滤、结果回传和循环保护。
 */
class SubagentServiceTest {

    private ToolPermissionGuard toolPermissionGuard;
    private ToolResolverService toolResolverService;
    private TaskPlanningService taskPlanningService;
    private AgentStepPlannerService agentStepPlannerService;
    private AgentToolExecutorService agentToolExecutorService;
    private SubagentWorkflowService subagentWorkflowService;
    private SubagentService subagentService;

    @BeforeEach
    void setUp() {
        toolPermissionGuard = mock(ToolPermissionGuard.class);
        toolResolverService = mock(ToolResolverService.class);
        taskPlanningService = mock(TaskPlanningService.class);
        agentStepPlannerService = mock(AgentStepPlannerService.class);
        agentToolExecutorService = mock(AgentToolExecutorService.class);
        subagentWorkflowService = mock(SubagentWorkflowService.class);

        AgentProperties agentProperties = new AgentProperties(
                true,
                AgentReasoningMode.LOOP,
                new AgentProperties.Cot(0.0, 512),
                new AgentProperties.Planning(true, 0.0, 768),
                new AgentProperties.Loop(6, 0.0, 512),
                new AgentProperties.Subagent(true, 4, true, 2, 2, List.of("search_web", "fetch_webpage"))
        );
        subagentService = new SubagentService(
                agentProperties,
                toolPermissionGuard,
                toolResolverService,
                taskPlanningService,
                agentStepPlannerService,
                agentToolExecutorService,
                subagentWorkflowService
        );
    }

    @Test
    void shouldRunSubagentWithIsolatedLoopAndReturnSummary() {
        PermissionContext permissionContext = new PermissionContext(
                1L,
                "knowledge_user",
                Set.of("KNOWLEDGE_USER"),
                Set.of("task", "search_web", "fetch_webpage"),
                Set.of(),
                Set.of(),
                false
        );
        ToolContext toolContext = new ToolContext(Map.of(
                ToolContextKeys.PERMISSION_CONTEXT, permissionContext,
                ToolContextKeys.CONVERSATION_ID, 10L,
                ToolContextKeys.SESSION_ID, "agent-session",
                ToolContextKeys.WORKFLOW_ID, 100L,
                ToolContextKeys.ROOT_TASK_ID, 200L,
                ToolContextKeys.STEP_INDEX, 1
        ));

        RegisteredTool searchTool = new RegisteredTool(
                new PlatformToolDefinition(
                        "search_web",
                        "search_web",
                        "联网搜索",
                        "搜索互联网资料",
                        "{}",
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        20_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of("KNOWLEDGE_USER"),
                        List.of("search"),
                        List.of("chat")
                ),
                mock(ToolCallback.class)
        );
        RegisteredTool taskTool = new RegisteredTool(
                new PlatformToolDefinition(
                        "task",
                        "task",
                        "子智能体任务",
                        "运行子智能体",
                        "{}",
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        60_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of("KNOWLEDGE_USER"),
                        List.of("task"),
                        List.of("agent")
                ),
                mock(ToolCallback.class)
        );
        TaskPlan taskPlan = new TaskPlan(
                "检查外部资料",
                "先搜索资料，再输出结论。",
                List.of(new TaskPlanStep(
                        "step-1",
                        "搜索资料",
                        "调用搜索工具获取信息",
                        List.of(),
                        List.of("search_web"),
                        false,
                        "找到足够资料"
                ))
        );

        when(toolPermissionGuard.requirePermissionContext(toolContext)).thenReturn(permissionContext);
        when(toolResolverService.resolve(any())).thenReturn(List.of(searchTool, taskTool));
        when(subagentWorkflowService.start(eq(1L), any(), eq("请在独立上下文里调研 PostgreSQL 测试框架"), eq(100L), eq(200L), eq(1)))
                .thenReturn(new SubagentWorkflowService.ExecutionWorkflow(301L, 401L));
        when(taskPlanningService.plan(eq(AgentReasoningMode.LOOP), eq("请在独立上下文里调研 PostgreSQL 测试框架"), any(MemoryContext.class), any()))
                .thenReturn(new TaskPlanningService.StructuredResult<>(taskPlan, null));
        when(agentStepPlannerService.planNextStep(
                eq(AgentReasoningMode.LOOP),
                eq("请在独立上下文里调研 PostgreSQL 测试框架"),
                any(MemoryContext.class),
                any(),
                any(),
                eq(1),
                eq(4),
                eq(taskPlan)
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(
                new AgentStepPlan(
                        "先搜索 PostgreSQL 使用的测试框架",
                        AgentActionType.TOOL,
                        null,
                        "search_web",
                        Map.of("query", "PostgreSQL test framework"),
                        null
                ),
                null
        ));
        when(agentStepPlannerService.planNextStep(
                eq(AgentReasoningMode.LOOP),
                eq("请在独立上下文里调研 PostgreSQL 测试框架"),
                any(MemoryContext.class),
                any(),
                any(),
                eq(2),
                eq(4),
                eq(taskPlan)
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(
                new AgentStepPlan(
                        "已有足够信息，可以汇总结论",
                        AgentActionType.FINAL,
                        null,
                        null,
                        null,
                        "子智能体结论：当前资料显示主要测试框架是 pytest。"
                ),
                null
        ));
        when(agentToolExecutorService.isAvailableTool("search_web", List.of(searchTool))).thenReturn(true);
        when(agentToolExecutorService.execute(
                eq("search_web"),
                eq(Map.of("query", "PostgreSQL test framework")),
                eq(permissionContext),
                any()
        )).thenReturn(new AgentToolExecutorService.ToolExecutionOutcome(
                "search_web",
                "检索结果显示项目主要使用 pytest。",
                false
        ));

        SubagentTaskResult result = subagentService.run("请在独立上下文里调研 PostgreSQL 测试框架", toolContext);

        assertThat(result.workflowId()).isEqualTo(301L);
        assertThat(result.summary()).isEqualTo("子智能体结论：当前资料显示主要测试框架是 pytest。");
        assertThat(result.planSummary()).isEqualTo("先搜索资料，再输出结论。");
        assertThat(result.stepCount()).isEqualTo(2);
        assertThat(result.completedByFallback()).isFalse();
        assertThat(result.completionType()).isEqualTo(SubagentCompletionType.FINAL);
        assertThat(result.completionReason()).isNull();
        assertThat(result.usedTools()).containsExactly("search_web");

        ArgumentCaptor<List<RegisteredTool>> toolCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskPlanningService).plan(eq(AgentReasoningMode.LOOP), eq("请在独立上下文里调研 PostgreSQL 测试框架"), any(MemoryContext.class), toolCaptor.capture());
        assertThat(toolCaptor.getValue()).extracting(tool -> tool.definition().name()).containsExactly("search_web");

        verify(subagentWorkflowService).recordTaskPlan(1L, new SubagentWorkflowService.ExecutionWorkflow(301L, 401L), taskPlan);
        verify(subagentWorkflowService).completeSuccess(
                1L,
                new SubagentWorkflowService.ExecutionWorkflow(301L, 401L),
                "子智能体结论：当前资料显示主要测试框架是 pytest。",
                "先搜索资料，再输出结论。",
                2,
                List.of("search_web"),
                false,
                SubagentCompletionType.FINAL,
                null
        );
    }

    @Test
    void shouldStopEarlyWhenRepeatedActionExceedsThreshold() {
        PermissionContext permissionContext = new PermissionContext(
                1L,
                "knowledge_user",
                Set.of("KNOWLEDGE_USER"),
                Set.of("search_web"),
                Set.of(),
                Set.of(),
                false
        );
        ToolContext toolContext = new ToolContext(Map.of(
                ToolContextKeys.PERMISSION_CONTEXT, permissionContext,
                ToolContextKeys.CONVERSATION_ID, 10L,
                ToolContextKeys.SESSION_ID, "agent-session"
        ));
        RegisteredTool searchTool = new RegisteredTool(
                new PlatformToolDefinition(
                        "search_web",
                        "search_web",
                        "联网搜索",
                        "搜索互联网资料",
                        "{}",
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        20_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of("KNOWLEDGE_USER"),
                        List.of("search"),
                        List.of("chat")
                ),
                mock(ToolCallback.class)
        );
        TaskPlan taskPlan = new TaskPlan(
                "重复搜索测试",
                "尝试搜索并观察是否会触发保护。",
                List.of()
        );

        when(toolPermissionGuard.requirePermissionContext(toolContext)).thenReturn(permissionContext);
        when(toolResolverService.resolve(any())).thenReturn(List.of(searchTool));
        when(subagentWorkflowService.start(eq(1L), any(), eq("重复搜索同一个问题"), eq(null), eq(null), eq(null)))
                .thenReturn(new SubagentWorkflowService.ExecutionWorkflow(501L, 601L));
        when(taskPlanningService.plan(eq(AgentReasoningMode.LOOP), eq("重复搜索同一个问题"), any(MemoryContext.class), any()))
                .thenReturn(new TaskPlanningService.StructuredResult<>(taskPlan, null));

        AgentStepPlan repeatedPlan = new AgentStepPlan(
                "继续搜索同一个问题",
                AgentActionType.TOOL,
                null,
                "search_web",
                Map.of("query", "same query"),
                null
        );
        when(agentStepPlannerService.planNextStep(
                eq(AgentReasoningMode.LOOP),
                eq("重复搜索同一个问题"),
                any(MemoryContext.class),
                any(),
                any(),
                eq(1),
                eq(4),
                eq(taskPlan)
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(repeatedPlan, null));
        when(agentStepPlannerService.planNextStep(
                eq(AgentReasoningMode.LOOP),
                eq("重复搜索同一个问题"),
                any(MemoryContext.class),
                any(),
                any(),
                eq(2),
                eq(4),
                eq(taskPlan)
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(repeatedPlan, null));
        when(agentToolExecutorService.isAvailableTool("search_web", List.of(searchTool))).thenReturn(true);
        when(agentToolExecutorService.execute(eq("search_web"), eq(Map.of("query", "same query")), eq(permissionContext), any()))
                .thenReturn(new AgentToolExecutorService.ToolExecutionOutcome("search_web", "same observation", false));

        SubagentTaskResult result = subagentService.run("重复搜索同一个问题", toolContext);

        assertThat(result.workflowId()).isEqualTo(501L);
        assertThat(result.completedByFallback()).isTrue();
        assertThat(result.completionType()).isEqualTo(SubagentCompletionType.REPEATED_ACTION_GUARD);
        assertThat(result.completionReason()).contains("连续重复相同动作");
        assertThat(result.summary()).isEqualTo("same observation");
        assertThat(result.usedTools()).containsExactly("search_web");
    }
}