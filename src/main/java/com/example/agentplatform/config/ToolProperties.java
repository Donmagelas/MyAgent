package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * 工具模块配置。
 * 用于统一管理搜索工具、网页抓取工具、PDF 工具以及工具循环的运行参数。
 */
@ConfigurationProperties(prefix = "app.tools")
public record ToolProperties(
        boolean enabled,
        int maxIterations,
        Resolver resolver,
        SearchApi searchApi,
        WebPage webPage,
        Pdf pdf
) {

    /**
     * 工具动态解析配置。
     */
    public record Resolver(
            int candidateLimit
    ) {
    }

    /**
     * 联网搜索工具配置。
     */
    public record SearchApi(
            String baseUrl,
            String apiKey,
            String engine,
            int defaultResultLimit,
            Duration timeout,
            int maxRetries,
            Duration retryBackoff
    ) {
    }

    /**
     * 网页抓取工具配置。
     */
    public record WebPage(
            Duration timeout,
            int maxContentLength,
            Set<String> allowedSchemes
    ) {
    }

    /**
     * PDF 工具配置。
     */
    public record Pdf(
            Path outputDirectory,
            String defaultAuthor
    ) {
    }
}
