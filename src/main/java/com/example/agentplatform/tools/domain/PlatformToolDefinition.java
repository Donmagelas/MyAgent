package com.example.agentplatform.tools.domain;

import java.util.List;
import java.util.Set;

/**
 * 平台内部工具定义。
 * 参考 Claude Code 的工具元数据思路，为工具治理、权限判断和后续 skill 路由提供统一描述。
 */
public record PlatformToolDefinition(
        String name,
        String implementationKey,
        String displayName,
        String description,
        String inputSchema,
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
        List<String> scopes
) {
}
