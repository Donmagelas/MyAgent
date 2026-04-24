package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis 缓存配置。
 * 控制短期记忆滑动窗口缓存的开关、前缀和过期时间。
 */
@ConfigurationProperties(prefix = "app.cache.redis")
public record RedisCacheProperties(
        Boolean enabled,
        String keyPrefix,
        Duration shortTermMemoryTtl,
        Duration shortTermMemoryVersionTtl
) {

    public RedisCacheProperties {
        enabled = enabled != null && enabled;
        keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "agent-platform";
        shortTermMemoryTtl = shortTermMemoryTtl == null ? Duration.ofMinutes(30) : shortTermMemoryTtl;
        // 版本号 key 的 TTL 需要不短于窗口 key，避免旧窗口尚未过期时版本号先回到 0。
        shortTermMemoryVersionTtl =
                shortTermMemoryVersionTtl == null ? Duration.ofMinutes(60) : shortTermMemoryVersionTtl;
    }
}
