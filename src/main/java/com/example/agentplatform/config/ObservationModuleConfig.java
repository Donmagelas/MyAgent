package com.example.agentplatform.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 观测模块配置。
 * 当前仅提供 ObservationRegistry 兜底 Bean，供模型调用观测链路复用。
 */
@Configuration
public class ObservationModuleConfig {

    /**
     * 提供观测注册表兜底 Bean。
     * 如果 Spring Boot 或 Actuator 已经创建 ObservationRegistry，则不会重复创建。
     */
    @Bean
    @ConditionalOnMissingBean(ObservationRegistry.class)
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }
}
