package com.example.agentplatform.tools.domain;

import java.util.Set;

/**
 * 工具动态解析请求。
 * 描述当前问题、角色和候选数量限制。
 */
public record ToolResolverRequest(
        Long userId,
        Long conversationId,
        String message,
        Set<String> roles,
        int limit
) {
}
