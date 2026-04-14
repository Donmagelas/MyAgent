package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.SubagentCompletionType;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolRiskLevel;
import com.example.agentplatform.tools.dto.SubagentTaskResult;
import com.example.agentplatform.tools.service.ToolCallbackRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent 工具执行服务测试。
 * 验证父 Agent 对 subagent 结果的 observation 格式化逻辑。
 */
class AgentToolExecutorServiceTest {

    private ToolCallbackRegistry toolCallbackRegistry;
    private ObjectProvider<ToolCallbackRegistry> toolCallbackRegistryProvider;
    private ToolCallback toolCallback;
    private AgentToolExecutorService agentToolExecutorService;
    private PermissionContext permissionContext;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        toolCallbackRegistry = mock(ToolCallbackRegistry.class);
        toolCallbackRegistryProvider = mock(ObjectProvider.class);
        toolCallback = mock(ToolCallback.class);
        when(toolCallbackRegistryProvider.getObject()).thenReturn(toolCallbackRegistry);
        agentToolExecutorService = new AgentToolExecutorService(toolCallbackRegistryProvider, new ObjectMapper());
        permissionContext = new PermissionContext(
                1L,
                "knowledge_user",
                Set.of("KNOWLEDGE_USER"),
                Set.of("task"),
                Set.of(),
                Set.of(),
                false
        );
        conversation = new Conversation(
                10L,
                1L,
                "agent-session",
                "Agent Session",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    @Test
    void shouldFormatSubagentTaskObservation() throws Exception {
        RegisteredTool registeredTool = new RegisteredTool(
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
                toolCallback
        );
        String rawResult = new ObjectMapper().writeValueAsString(new SubagentTaskResult(
                301L,
                "子智能体结论：当前资料显示主要测试框架是 pytest。",
                "先搜索资料，再输出结论。",
                2,
                true,
                SubagentCompletionType.REPEATED_ACTION_GUARD,
                "子智能体连续重复相同动作，已触发保护性提前结束",
                List.of("search_web")
        ));
        when(toolCallbackRegistry.requireRegisteredTool("task")).thenReturn(registeredTool);
        when(toolCallback.call(any(), any())).thenReturn(rawResult);

        AgentToolExecutorService.ToolExecutionOutcome outcome = agentToolExecutorService.execute(
                "task",
                Map.of("prompt", "调研 PostgreSQL 测试框架"),
                permissionContext,
                conversation
        );

        assertThat(outcome.toolName()).isEqualTo("task");
        assertThat(outcome.observation()).contains("完成类型=REPEATED_ACTION_GUARD");
        assertThat(outcome.observation()).contains("步骤数=2");
        assertThat(outcome.observation()).contains("使用工具=search_web");
        assertThat(outcome.observation()).contains("摘要=子智能体结论：当前资料显示主要测试框架是 pytest。");
        assertThat(outcome.observation()).contains("原因=子智能体连续重复相同动作，已触发保护性提前结束");
    }

    @Test
    void shouldKeepRawObservationForNonTaskTool() {
        RegisteredTool registeredTool = new RegisteredTool(
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
                toolCallback
        );
        when(toolCallbackRegistry.requireRegisteredTool("search_web")).thenReturn(registeredTool);
        when(toolCallback.call(any(), any())).thenReturn("plain observation");

        AgentToolExecutorService.ToolExecutionOutcome outcome = agentToolExecutorService.execute(
                "search_web",
                Map.of("query", "postgresql"),
                permissionContext,
                conversation
        );

        assertThat(outcome.observation()).isEqualTo("plain observation");
    }
}
