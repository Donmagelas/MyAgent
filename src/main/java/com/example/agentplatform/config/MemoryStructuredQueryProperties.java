package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆结构化查询配置。
 * 控制是否启用结构化输出解析，以及这一步模型调用的生成参数。
 */
@ConfigurationProperties(prefix = "app.memory.structured-query")
public record MemoryStructuredQueryProperties(
        Boolean enabled,
        Double temperature,
        Integer maxTokens
) {

    public MemoryStructuredQueryProperties {
        enabled = enabled == null || enabled;
        temperature = temperature == null ? 0.0d : temperature;
        maxTokens = maxTokens == null ? 256 : maxTokens;
    }
}
