package com.example.agentplatform.config;

import com.example.agentplatform.tools.service.ToolExecutionExceptionTextProcessor;
import com.example.agentplatform.tools.service.ToolRegistryService;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具模块配置。
 * 负责创建 ToolCallingManager，统一管理工具解析、执行和异常处理。
 */
@Configuration
public class ToolModuleConfig {

    @Bean
    @ConditionalOnMissingBean(ObservationRegistry.class)
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    /**
     * 创建 Spring AI ToolCallingManager。
     */
    @Bean
    public ToolCallingManager toolCallingManager(
            ObservationRegistry observationRegistry,
            ToolRegistryService toolRegistryService,
            ToolExecutionExceptionTextProcessor toolExecutionExceptionTextProcessor
    ) {
        return DefaultToolCallingManager.builder()
                .observationRegistry(observationRegistry)
                .toolCallbackResolver(toolRegistryService.getToolCallbackResolver())
                .toolExecutionExceptionProcessor(toolExecutionExceptionTextProcessor)
                .build();
    }
}
