package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI HTTP 客户端配置。
 * 统一约束连接超时和响应超时，避免模型调用长期挂起。
 */
@ConfigurationProperties(prefix = "app.ai.client")
public record AiClientProperties(
        Duration connectTimeout,
        Duration responseTimeout
) {

    public AiClientProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
        responseTimeout = responseTimeout == null ? Duration.ofSeconds(30) : responseTimeout;
    }
}
