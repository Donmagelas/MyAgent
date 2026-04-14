package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 查询增强配置。
 * 控制查询改写、压缩、翻译和多查询扩展等预检索能力的开关与参数。
 */
@ConfigurationProperties(prefix = "app.rag.query-enhancement")
public record RagQueryEnhancementProperties(
        RewriteProperties rewrite,
        CompressionProperties compression,
        TranslationProperties translation,
        MultiQueryProperties multiQuery
) {

    /**
     * 查询改写配置。
     */
    public record RewriteProperties(
            boolean enabled,
            String targetSearchSystem
    ) {
    }

    /**
     * 查询压缩配置。
     */
    public record CompressionProperties(
            boolean enabled
    ) {
    }

    /**
     * 查询翻译配置。
     */
    public record TranslationProperties(
            boolean enabled,
            String targetLanguage
    ) {
    }

    /**
     * 多查询扩展配置。
     */
    public record MultiQueryProperties(
            boolean enabled,
            boolean includeOriginal,
            int queryCount
    ) {
    }
}
