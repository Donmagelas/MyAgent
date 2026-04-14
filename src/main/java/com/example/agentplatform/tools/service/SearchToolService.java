package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.tools.client.SearchApiClient;
import com.example.agentplatform.tools.dto.SearchToolResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 联网搜索工具。
 */
@Component
public class SearchToolService {

    private static final String TOOL_NAME = "search_web";

    private final SearchApiClient searchApiClient;
    private final ToolPermissionGuard toolPermissionGuard;

    public SearchToolService(SearchApiClient searchApiClient, ToolPermissionGuard toolPermissionGuard) {
        this.searchApiClient = searchApiClient;
        this.toolPermissionGuard = toolPermissionGuard;
    }

    /**
     * 通过外部搜索 API 检索网页结果。
     */
    @Tool(
            name = TOOL_NAME,
            description = "联网搜索最新网页结果，适合查找需要外部资料支持的信息"
    )
    public SearchToolResult searchWeb(
            @ToolParam(description = "搜索关键词") String query,
            @ToolParam(required = false, description = "返回结果条数，建议 1 到 10") Integer limit,
            ToolContext toolContext
    ) {
        toolPermissionGuard.assertAllowed(
                TOOL_NAME,
                Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                false,
                toolContext
        );
        return searchApiClient.search(query, limit);
    }
}
