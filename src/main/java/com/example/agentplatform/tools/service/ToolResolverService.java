package com.example.agentplatform.tools.service;

import com.example.agentplatform.config.ToolProperties;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolCatalogEntry;
import com.example.agentplatform.tools.domain.ToolResolverRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工具动态解析服务。
 * 根据当前问题、角色和数据库目录，筛选本次可暴露给模型的工具。
 */
@Service
public class ToolResolverService {

    private final ToolProperties toolProperties;
    private final ToolCatalogService toolCatalogService;
    private final ObjectProvider<ToolCallbackRegistry> toolCallbackRegistryProvider;

    public ToolResolverService(
            ToolProperties toolProperties,
            ToolCatalogService toolCatalogService,
            ObjectProvider<ToolCallbackRegistry> toolCallbackRegistryProvider
    ) {
        this.toolProperties = toolProperties;
        this.toolCatalogService = toolCatalogService;
        this.toolCallbackRegistryProvider = toolCallbackRegistryProvider;
    }

    /**
     * 解析本次候选工具。
     * 第一版先按数据库目录检索，再和本地已注册 ToolCallback 取交集。
     */
    public List<RegisteredTool> resolve(ToolResolverRequest request) {
        int limit = request.limit() > 0
                ? request.limit()
                : resolveCandidateLimit();

        List<ToolCatalogEntry> catalogEntries = toolCatalogService.searchEnabledTools(request.message(), limit);
        List<RegisteredTool> resolvedTools = catalogEntries.stream()
                .filter(entry -> isRoleAllowed(entry, request))
                .map(entry -> getToolCallbackRegistry().findRegisteredTool(entry.toolName()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!resolvedTools.isEmpty()) {
            return resolvedTools;
        }
        return toolCatalogService.listEnabledTools(limit).stream()
                .filter(entry -> isRoleAllowed(entry, request))
                .map(entry -> getToolCallbackRegistry().findRegisteredTool(entry.toolName()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private ToolCallbackRegistry getToolCallbackRegistry() {
        return toolCallbackRegistryProvider.getObject();
    }

    private boolean isRoleAllowed(ToolCatalogEntry entry, ToolResolverRequest request) {
        if (entry.allowedRoles() == null || entry.allowedRoles().isEmpty()) {
            return true;
        }
        if (request.roles() == null || request.roles().isEmpty()) {
            return false;
        }
        return request.roles().stream().anyMatch(entry.allowedRoles()::contains);
    }

    private int resolveCandidateLimit() {
        if (toolProperties.resolver() == null || toolProperties.resolver().candidateLimit() <= 0) {
            return 6;
        }
        return toolProperties.resolver().candidateLimit();
    }
}
