package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 高德地图工具配置。
 * 与通用 ToolProperties 分离，避免新增第三方工具时反复改动旧构造器和旧测试。
 */
@ConfigurationProperties(prefix = "app.tools.amap")
public record AmapProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        Duration timeout,
        int maxRetries,
        Duration retryBackoff,
        Duration minRequestInterval,
        String defaultCity,
        int defaultRadiusMeters,
        int maxRadiusMeters,
        int defaultCandidateLimit,
        int maxCandidateLimit,
        String routeMode,
        int maxParticipants,
        int maxRouteCalculations,
        int routeConcurrency,
        Score score
) {

    public AmapProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        retryBackoff = retryBackoff == null ? Duration.ofMillis(500) : retryBackoff;
        minRequestInterval = minRequestInterval == null ? Duration.ofMillis(400) : minRequestInterval;
        defaultCity = defaultCity == null ? "" : defaultCity.trim();
        defaultRadiusMeters = defaultRadiusMeters <= 0 ? 5_000 : defaultRadiusMeters;
        maxRadiusMeters = maxRadiusMeters <= 0 ? 15_000 : maxRadiusMeters;
        defaultCandidateLimit = defaultCandidateLimit <= 0 ? 5 : defaultCandidateLimit;
        maxCandidateLimit = maxCandidateLimit <= 0 ? 8 : maxCandidateLimit;
        routeMode = StringUtils.hasText(routeMode) ? routeMode.trim().toLowerCase() : "transit";
        maxParticipants = maxParticipants <= 0 ? 8 : maxParticipants;
        maxRouteCalculations = maxRouteCalculations <= 0 ? 80 : maxRouteCalculations;
        routeConcurrency = routeConcurrency <= 0 ? 4 : routeConcurrency;
        score = score == null ? new Score(0.45d, 0.30d, 0.20d, 0.05d) : score;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://restapi.amap.com";
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 聚会地点评分权重。
     * 分数越低越优先，主要衡量总耗时、最慢参与者耗时和公平性。
     */
    public record Score(
            double totalDurationWeight,
            double maxDurationWeight,
            double varianceWeight,
            double centerDistanceWeight
    ) {
    }
}
