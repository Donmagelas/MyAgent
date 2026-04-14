package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 执行可视化与 usage 聚合配置。
 * 控制返回给前端的 usage 明细粒度，避免一次返回过多日志记录。
 */
@ConfigurationProperties(prefix = "app.observability")
public record ObservabilityProperties(
        int usageDetailLimit
) {
}
