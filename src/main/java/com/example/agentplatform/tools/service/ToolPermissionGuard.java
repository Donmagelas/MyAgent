package com.example.agentplatform.tools.service;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.ToolContextKeys;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.Set;

/**
 * 工具权限守卫。
 * 负责从 ToolContext 中提取 PermissionContext，并在工具执行前完成集中权限校验。
 */
@Component
public class ToolPermissionGuard {

    /**
     * 校验当前上下文是否允许执行指定工具。
     */
    public void assertAllowed(
            String toolName,
            Set<String> requiredRoles,
            boolean requiresApproval,
            ToolContext toolContext
    ) {
        PermissionContext permissionContext = requirePermissionContext(toolContext);
        if (permissionContext.isDenied(toolName)) {
            throw new AccessDeniedException("当前上下文禁止执行工具: " + toolName);
        }
        if (!permissionContext.hasAnyRequiredRole(requiredRoles)) {
            throw new AccessDeniedException("当前用户缺少执行工具所需角色: " + toolName);
        }
        if (requiresApproval && permissionContext.requiresApproval(toolName) && !permissionContext.interactiveApprovalAllowed()) {
            throw new AccessDeniedException("当前工具需要审批，但当前上下文不允许直接执行: " + toolName);
        }
    }

    /**
     * 从 ToolContext 中提取权限上下文。
     */
    public PermissionContext requirePermissionContext(ToolContext toolContext) {
        if (toolContext == null) {
            throw new ApplicationException("ToolContext is missing");
        }
        Map<String, Object> context = toolContext.getContext();
        Object permissionContext = context.get(ToolContextKeys.PERMISSION_CONTEXT);
        if (permissionContext instanceof PermissionContext value) {
            return value;
        }
        throw new ApplicationException("PermissionContext is missing from ToolContext");
    }
}
