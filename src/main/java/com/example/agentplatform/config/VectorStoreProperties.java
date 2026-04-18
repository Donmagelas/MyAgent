package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量存储配置。
 * 当前主链路只需要统一管理向量维度，实际 RAG 数据写入 knowledge_chunk。
 */
@ConfigurationProperties(prefix = "app.vector")
public record VectorStoreProperties(
        int dimensions
) {
}
