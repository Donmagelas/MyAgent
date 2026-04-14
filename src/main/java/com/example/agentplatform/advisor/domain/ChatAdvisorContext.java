package com.example.agentplatform.advisor.domain;

import org.springframework.security.core.Authentication;

/**
 * 传递给自定义 advisor 的共享上下文。
 * 包含用户输入、当前操作以及认证主体。
 */
public record ChatAdvisorContext(
        AdvisorOperation operation,
        String message,
        Authentication authentication
) {
}
