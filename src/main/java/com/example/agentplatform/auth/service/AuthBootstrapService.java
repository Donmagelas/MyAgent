package com.example.agentplatform.auth.service;

import com.example.agentplatform.config.AppSecurityProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 认证数据引导器。
 * 应用启动时把配置里的默认测试账号同步到数据库。
 */
@Component
public class AuthBootstrapService implements ApplicationRunner {

    private final AppSecurityProperties appSecurityProperties;
    private final AuthService authService;

    public AuthBootstrapService(AppSecurityProperties appSecurityProperties, AuthService authService) {
        this.appSecurityProperties = appSecurityProperties;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<AppSecurityProperties.BootstrapUser> bootstrapUsers = appSecurityProperties.bootstrapUsers();
        for (AppSecurityProperties.BootstrapUser bootstrapUser : bootstrapUsers) {
            authService.upsertBootstrapUser(
                    bootstrapUser.username(),
                    bootstrapUser.password(),
                    bootstrapUser.roles()
            );
        }
    }
}
