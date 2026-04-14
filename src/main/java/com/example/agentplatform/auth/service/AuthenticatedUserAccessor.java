package com.example.agentplatform.auth.service;

import com.example.agentplatform.auth.domain.AuthenticatedUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 认证用户访问器。
 * 负责从 Spring Security 上下文安全提取当前登录用户主键。
 */
@Component
public class AuthenticatedUserAccessor {

    /** 提取当前登录用户主键。 */
    public Long requireUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("需要先完成登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
            return userPrincipal.userId();
        }
        throw new AccessDeniedException("当前登录信息不完整，无法识别用户");
    }
}
