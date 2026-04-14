package com.example.agentplatform.advisor.service;

import com.example.agentplatform.advisor.domain.ChatAdvisorContext;
import com.example.agentplatform.auth.domain.SecurityRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 Spring Security 认证结果的权限 advisor。
 * 在聊天链路继续之前校验当前操作所需的权限。
 */
@Service
public class PermissionAdvisor implements ChatAdvisor {

    @Override
    public String getName() {
        return "PermissionAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void before(ChatAdvisorContext context) {
        Authentication authentication = context.authentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("需要先完成登录");
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        switch (context.operation()) {
            case AGENT_STREAM -> {
                if (!authorities.contains(SecurityRole.authority(SecurityRole.CHAT_USER))) {
                    throw new AccessDeniedException("需要 CHAT_USER 权限");
                }
            }
            case KNOWLEDGE_RETRIEVE -> {
                if (!authorities.contains(SecurityRole.authority(SecurityRole.KNOWLEDGE_USER))
                        && !authorities.contains(SecurityRole.authority(SecurityRole.KNOWLEDGE_ADMIN))) {
                    throw new AccessDeniedException("需要 KNOWLEDGE_USER 权限");
                }
            }
        }
    }
}
