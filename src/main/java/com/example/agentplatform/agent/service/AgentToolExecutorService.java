package com.example.agentplatform.agent.service;

import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolContextKeys;
import com.example.agentplatform.tools.dto.SubagentTaskResult;
import com.example.agentplatform.tools.service.ToolCallbackRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具执行服务。
 * 在 Agent Loop 中直接调用已注册 ToolCallback，并把结果回填为 observation。
 */
@Service
public class AgentToolExecutorService {

    private static final String SUBAGENT_TASK_TOOL = "task";

    private final ObjectProvider<ToolCallbackRegistry> toolCallbackRegistryProvider;
    private final ObjectMapper objectMapper;

    public AgentToolExecutorService(ObjectProvider<ToolCallbackRegistry> toolCallbackRegistryProvider, ObjectMapper objectMapper) {
        this.toolCallbackRegistryProvider = toolCallbackRegistryProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行指定工具并返回 observation。
     */
    public ToolExecutionOutcome execute(
            String toolName,
            Map<String, Object> toolInput,
            PermissionContext permissionContext,
            Conversation conversation
    ) {
        return execute(toolName, toolInput, permissionContext, conversation, null, null, null);
    }

    /**
     * 执行指定工具并返回 observation，同时可透传父任务上下文。
     */
    public ToolExecutionOutcome execute(
            String toolName,
            Map<String, Object> toolInput,
            PermissionContext permissionContext,
            Conversation conversation,
            Long workflowId,
            Long rootTaskId,
            Integer stepIndex
    ) {
        RegisteredTool registeredTool = getToolCallbackRegistry().requireRegisteredTool(toolName);
        String payload = toJson(toolInput);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(ToolContextKeys.PERMISSION_CONTEXT, permissionContext);
        context.put(ToolContextKeys.USER_ID, permissionContext.userId());
        context.put(ToolContextKeys.USERNAME, permissionContext.username());
        context.put(ToolContextKeys.CONVERSATION_ID, conversation.id());
        context.put(ToolContextKeys.SESSION_ID, conversation.sessionId());
        if (workflowId != null) {
            context.put(ToolContextKeys.WORKFLOW_ID, workflowId);
        }
        if (rootTaskId != null) {
            context.put(ToolContextKeys.ROOT_TASK_ID, rootTaskId);
        }
        if (stepIndex != null) {
            context.put(ToolContextKeys.STEP_INDEX, stepIndex);
        }
        ToolContext toolContext = new ToolContext(context);
        String rawResult = registeredTool.callback().call(payload, toolContext);
        return new ToolExecutionOutcome(
                registeredTool.definition().name(),
                formatObservation(registeredTool.definition().name(), rawResult),
                registeredTool.definition().returnDirect()
        );
    }

    /**
     * 从候选工具中按名称查找某一个工具。
     */
    public boolean isAvailableTool(String toolName, List<RegisteredTool> availableTools) {
        if (toolName == null || availableTools == null) {
            return false;
        }
        return availableTools.stream()
                .anyMatch(tool -> tool.definition().name().equals(toolName));
    }

    private ToolCallbackRegistry getToolCallbackRegistry() {
        return toolCallbackRegistryProvider.getObject();
    }

    private String toJson(Map<String, Object> toolInput) {
        try {
            return objectMapper.writeValueAsString(toolInput == null ? Map.of() : toolInput);
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("工具输入序列化失败", exception);
        }
    }

    /**
     * 将工具原始结果转换成更适合父 Agent 消费的 observation 文本。
     */
    private String formatObservation(String toolName, String rawResult) {
        if (!SUBAGENT_TASK_TOOL.equals(toolName) || rawResult == null || rawResult.isBlank()) {
            return rawResult;
        }
        try {
            SubagentTaskResult result = objectMapper.readValue(rawResult, SubagentTaskResult.class);
            StringBuilder builder = new StringBuilder("子任务执行结果：");
            if (result.completionType() != null) {
                builder.append("完成类型=").append(result.completionType().name());
            }
            builder.append("；步骤数=").append(result.stepCount());
            if (result.usedTools() != null && !result.usedTools().isEmpty()) {
                builder.append("；使用工具=").append(String.join(", ", result.usedTools()));
            }
            if (result.summary() != null && !result.summary().isBlank()) {
                builder.append("；摘要=").append(result.summary());
            }
            if (result.completionReason() != null && !result.completionReason().isBlank()) {
                builder.append("；原因=").append(result.completionReason());
            }
            return builder.toString();
        }
        catch (Exception exception) {
            return rawResult;
        }
    }

    /**
     * 单次工具执行结果。
     */
    public record ToolExecutionOutcome(
            String toolName,
            String observation,
            boolean returnDirect
    ) {
    }
}
