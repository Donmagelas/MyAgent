package com.example.agentplatform.tools.dto;

import com.example.agentplatform.tools.domain.ToolRiskLevel;

import java.util.Set;

/**
 * 工具定义响应。
 */
public record ToolDefinitionResponse(
        String name,
        String description,
        String inputSchema,
        boolean readOnly,
        boolean mutatesState,
        boolean dangerous,
        boolean returnDirect,
        boolean requiresApproval,
        long timeoutMillis,
        ToolRiskLevel riskLevel,
        Set<String> allowedRoles
) {
}
