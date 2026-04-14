package com.example.agentplatform.tools.domain;

import org.springframework.ai.tool.ToolCallback;

/**
 * 已注册工具。
 * 把 Spring AI ToolCallback 与平台内部工具定义绑定在一起，便于统一注册和权限治理。
 */
public record RegisteredTool(
        PlatformToolDefinition definition,
        ToolCallback callback
) {
}
