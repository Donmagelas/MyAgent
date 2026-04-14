package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 混合检索配置。
 * 控制向量检索、关键词检索、融合和 rerank 的开关与参数。
 */
@ConfigurationProperties(prefix = "app.rag.hybrid")
public record RagHybridRetrievalProperties(
        boolean enabled,
        boolean keywordEnabled,
        boolean rerankEnabled,
        String keywordTsConfig,
        int fusionCandidateLimit,
        int rerankTopN,
        double vectorWeight,
        double keywordWeight
) {
}
