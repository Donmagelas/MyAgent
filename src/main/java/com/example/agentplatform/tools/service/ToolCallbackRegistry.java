package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolRiskLevel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具回调注册中心。
 * 只负责维护本地已实现的 ToolCallback，不承担数据库工具目录检索职责。
 */
@Component
public class ToolCallbackRegistry {

    private final List<RegisteredTool> registeredTools;
    private final Map<String, RegisteredTool> toolIndex;
    private final StaticToolCallbackResolver toolCallbackResolver;

    public ToolCallbackRegistry(
            SearchToolService searchToolService,
            WebPageFetchToolService webPageFetchToolService,
            PdfGenerateToolService pdfGenerateToolService,
            SubagentTaskToolService subagentTaskToolService,
            MeetupRecommendationToolService meetupRecommendationToolService
    ) {
        ToolCallback[] callbacks = ToolCallbacks.from(
                searchToolService,
                webPageFetchToolService,
                pdfGenerateToolService,
                subagentTaskToolService,
                meetupRecommendationToolService
        );
        this.registeredTools = List.of(
                buildRegisteredTool(
                        callbacks,
                        "search_web",
                        "search_web",
                        "联网搜索",
                        false,
                        false,
                        false,
                        false,
                        20_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                        List.of("search", "web", "internet"),
                        List.of("chat", "research")
                ),
                buildRegisteredTool(
                        callbacks,
                        "fetch_webpage",
                        "fetch_webpage",
                        "网页抓取",
                        true,
                        false,
                        false,
                        false,
                        15_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                        List.of("fetch", "webpage", "html"),
                        List.of("chat", "research")
                ),
                buildRegisteredTool(
                        callbacks,
                        "generate_pdf",
                        "generate_pdf",
                        "PDF 生成",
                        false,
                        true,
                        false,
                        true,
                        30_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                        List.of("pdf", "document", "export"),
                        List.of("chat", "output")
                ),
                buildRegisteredTool(
                        callbacks,
                        "task",
                        "task",
                        "子智能体任务",
                        true,
                        false,
                        false,
                        false,
                        60_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                        List.of("subagent", "task", "delegation"),
                        List.of("agent", "chat")
                ),
                buildRegisteredTool(
                        callbacks,
                        "recommend_meetup_place",
                        "recommend_meetup_place",
                        "聚会地点推荐",
                        true,
                        false,
                        false,
                        false,
                        45_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                        List.of("map", "amap", "meetup", "poi", "route"),
                        List.of("chat", "location", "planning")
                )
        );
        this.toolIndex = registeredTools.stream()
                .collect(Collectors.toUnmodifiableMap(tool -> tool.definition().name(), Function.identity()));
        this.toolCallbackResolver = new StaticToolCallbackResolver(getToolCallbacks());
    }

    /**
     * 返回所有本地已注册工具。
     */
    public List<RegisteredTool> getRegisteredTools() {
        return registeredTools;
    }

    /**
     * 返回所有 ToolCallback。
     */
    public List<ToolCallback> getToolCallbacks() {
        return registeredTools.stream()
                .map(RegisteredTool::callback)
                .toList();
    }

    /**
     * 返回静态工具回调解析器。
     */
    public StaticToolCallbackResolver getToolCallbackResolver() {
        return toolCallbackResolver;
    }

    /**
     * 按工具名查询本地注册工具。
     */
    public Optional<RegisteredTool> findRegisteredTool(String toolName) {
        return Optional.ofNullable(toolIndex.get(toolName));
    }

    /**
     * 按工具名强制获取本地注册工具。
     */
    public RegisteredTool requireRegisteredTool(String toolName) {
        RegisteredTool registeredTool = toolIndex.get(toolName);
        if (registeredTool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        return registeredTool;
    }

    private RegisteredTool buildRegisteredTool(
            ToolCallback[] callbacks,
            String toolName,
            String implementationKey,
            String displayName,
            boolean readOnly,
            boolean mutatesState,
            boolean dangerous,
            boolean requiresApproval,
            long timeoutMillis,
            ToolRiskLevel riskLevel,
            Set<String> allowedRoles,
            List<String> tags,
            List<String> scopes
    ) {
        ToolCallback callback = findCallback(callbacks, toolName);
        return new RegisteredTool(
                new PlatformToolDefinition(
                        callback.getToolDefinition().name(),
                        implementationKey,
                        displayName,
                        callback.getToolDefinition().description(),
                        callback.getToolDefinition().inputSchema(),
                        true,
                        readOnly,
                        mutatesState,
                        dangerous,
                        callback.getToolMetadata().returnDirect(),
                        requiresApproval,
                        timeoutMillis,
                        riskLevel,
                        allowedRoles,
                        tags,
                        scopes
                ),
                callback
        );
    }

    private ToolCallback findCallback(ToolCallback[] callbacks, String toolName) {
        return java.util.Arrays.stream(callbacks)
                .filter(callback -> toolName.equals(callback.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tool callback not found: " + toolName));
    }
}
