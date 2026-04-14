package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Advisor 相关配置。
 * 当前主要用于简单违禁词过滤。
 */
@ConfigurationProperties(prefix = "app.advisor")
public record AdvisorProperties(
        List<String> forbiddenWords
) {
}
