package com.example.agentplatform.auth.domain;

/**
 * 系统内置角色常量。
 * 统一约束配置、Spring Security 与 advisor 使用的角色名。
 */
public final class SecurityRole {

    public static final String CHAT_USER = "CHAT_USER";
    public static final String KNOWLEDGE_USER = "KNOWLEDGE_USER";
    public static final String KNOWLEDGE_ADMIN = "KNOWLEDGE_ADMIN";

    private SecurityRole() {
    }

    /** 把角色名转换为 Spring Security 使用的 authority。 */
    public static String authority(String role) {
        return "ROLE_" + role;
    }
}
