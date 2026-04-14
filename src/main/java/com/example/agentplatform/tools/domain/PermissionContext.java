package com.example.agentplatform.tools.domain;

import java.util.Set;

/**
 * 工具权限上下文。
 * 参考 Claude Code 的权限上下文思路，把当前用户、角色、允许工具和审批要求集中起来，避免权限判断散落在工具实现内部。
 */
public record PermissionContext(
        Long userId,
        String username,
        Set<String> roles,
        Set<String> explicitlyAllowedTools,
        Set<String> explicitlyDeniedTools,
        Set<String> approvalRequiredTools,
        boolean interactiveApprovalAllowed
) {

    /**
     * 判断某个工具是否被显式拒绝。
     */
    public boolean isDenied(String toolName) {
        return toolName != null && explicitlyDeniedTools.contains(toolName);
    }

    /**
     * 判断某个工具是否需要审批。
     */
    public boolean requiresApproval(String toolName) {
        return toolName != null && approvalRequiredTools.contains(toolName);
    }

    /**
     * 判断角色集合是否满足工具要求。
     */
    public boolean hasAnyRequiredRole(Set<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        return roles.stream().anyMatch(requiredRoles::contains);
    }

    /**
     * 判断某个工具是否被显式允许。
     */
    public boolean isExplicitlyAllowed(String toolName) {
        return toolName != null && explicitlyAllowedTools.contains(toolName);
    }
}
