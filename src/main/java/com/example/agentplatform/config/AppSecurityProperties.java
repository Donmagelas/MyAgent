package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 安全配置属性。
 * 统一维护登录 Token 时效和启动引导账号配置。
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        Long tokenTtlHours,
        List<BootstrapUser> bootstrapUsers
) {

    public AppSecurityProperties {
        tokenTtlHours = tokenTtlHours == null ? 12L : tokenTtlHours;
        bootstrapUsers = bootstrapUsers == null ? List.of() : List.copyOf(bootstrapUsers);
    }

    /** 启动时同步到数据库的默认账号配置。 */
    public record BootstrapUser(
            String username,
            String password,
            List<String> roles
    ) {
        public BootstrapUser {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
