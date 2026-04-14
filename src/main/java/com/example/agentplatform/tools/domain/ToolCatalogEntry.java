package com.example.agentplatform.tools.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据库存储的工具定义。
 * 用于工具目录检索、启停控制和权限过滤。
 */
public record ToolCatalogEntry(
        Long id,
        String toolName,
        String implementationKey,
        String displayName,
        String description,
        String inputSchemaJson,
        boolean enabled,
        boolean readOnly,
        boolean mutatesState,
        boolean dangerous,
        boolean returnDirect,
        boolean requiresApproval,
        long timeoutMillis,
        ToolRiskLevel riskLevel,
        Set<String> allowedRoles,
        List<String> tags,
        List<String> scopes,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
