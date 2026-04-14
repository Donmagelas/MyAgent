package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量存储配置。
 * 用于管理 pgvector 相关的维度和表元数据。
 */
@ConfigurationProperties(prefix = "app.vector")
public record VectorStoreProperties(
        int dimensions,
        String schema,
        String table
) {
}
