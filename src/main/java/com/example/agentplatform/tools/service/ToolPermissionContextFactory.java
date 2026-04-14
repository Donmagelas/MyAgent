package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.AuthenticatedUserPrincipal;
import com.example.agentplatform.tools.domain.PermissionContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具权限上下文工厂。
 * 负责把当前认证信息转换为统一的 PermissionContext。
 */
@Component
public class ToolPermissionContextFactory {

    /**
     * 根据当前认证信息创建权限上下文。
     * 当前实现只保留用户身份、用户名和角色集合，不再额外构造历史的显式工具白名单。
     */
    public PermissionContext create(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return new PermissionContext(
                    null,
                    "anonymous",
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    false
            );
        }

        Object principal = authentication.getPrincipal();
        Long userId = null;
        String username = authentication.getName();
        if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
            userId = userPrincipal.userId();
            username = userPrincipal.getUsername();
        }

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::normalizeRole)
                .collect(Collectors.toUnmodifiableSet());

        return new PermissionContext(
                userId,
                username,
                roles,
                Set.of(),
                Set.of(),
                Set.of(),
                false
        );
    }

    /**
     * 统一去掉 Spring Security 的 ROLE_ 前缀，保留项目内部使用的角色名。
     */
    private String normalizeRole(String authority) {
        if (authority != null && authority.startsWith("ROLE_")) {
            return authority.substring("ROLE_".length());
        }
        return authority;
    }
}
