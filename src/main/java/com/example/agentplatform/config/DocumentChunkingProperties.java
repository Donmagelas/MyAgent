package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档切分配置。
 * 按不同文档类型提供独立的 chunk 大小、重叠长度和增强开关。
 */
@ConfigurationProperties(prefix = "app.rag.chunking")
public record DocumentChunkingProperties(
        String strategy,
        Markdown markdown,
        Json json,
        Text text,
        Enhancement enhancement
) {

    public DocumentChunkingProperties {
        strategy = strategy == null || strategy.isBlank() ? "section-aware" : strategy;
        markdown = markdown == null ? new Markdown(true, 560, 60) : markdown;
        json = json == null ? new Json(true, 420, 40) : json;
        text = text == null ? new Text(true, 700, 100) : text;
        enhancement = enhancement == null ? new Enhancement(true, true, 140) : enhancement;
    }

    /**
     * Markdown 切分配置。
     */
    public record Markdown(
            boolean headingAware,
            int chunkSize,
            int overlapChars
    ) {
    }

    /**
     * JSON 切分配置。
     */
    public record Json(
            boolean semanticAware,
            int chunkSize,
            int overlapChars
    ) {
    }

    /**
     * 纯文本切分配置。
     */
    public record Text(
            boolean paragraphAware,
            int chunkSize,
            int overlapChars
    ) {
    }

    /**
     * chunk 增强配置。
     */
    public record Enhancement(
            boolean chunkTitleEnabled,
            boolean chunkSummaryEnabled,
            int summaryMaxLength
    ) {
    }
}
