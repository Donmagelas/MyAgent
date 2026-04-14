package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索与切块相关配置。
 * 用于控制向量召回数量、上下文片段数量和默认切块参数。
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        int vectorTopK,
        int keywordTopK,
        int maxContextChunks,
        double similarityThreshold,
        double keywordMinScore,
        int chunkSize,
        int chunkOverlap
) {
}
